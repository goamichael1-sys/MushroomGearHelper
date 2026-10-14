package com.mushroom.gearhelper

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

object DecisionLogic {

    private fun sym(s: Int): String = when {
        s > 0 -> "▲"
        s < 0 -> "▼"
        else -> "–"
    }

    fun rgbToHsv(rInt: Int, gInt: Int, bInt: Int): FloatArray {
        val r = rInt / 255f
        val g = gInt / 255f
        val b = bInt / 255f
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val d = maxVal - minVal

        var h = 0f
        if (d != 0f) {
            h = when (maxVal) {
                r -> ((g - b) / d) % 6f
                g -> ((b - r) / d) + 2f
                else -> ((r - g) / d) + 4f
            }
            h *= 60f
            if (h < 0f) h += 360f
        }
        val s = if (maxVal != 0f) d / maxVal else 0f
        val v = maxVal
        return floatArrayOf(h, s, v)
    }

    /**
     * Samples a specific rectangle in a bitmap and returns the color fractions.
     */
    fun analyzeRegion(bitmap: Bitmap, rect: Rect): ColorFractions {
        val left = max(0, rect.left)
        val top = max(0, rect.top)
        val right = min(bitmap.width, rect.right)
        val bottom = min(bitmap.height, rect.bottom)
        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return ColorFractions()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        var redCount = 0
        var greenCount = 0
        var smokeCount = 0

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val hsv = rgbToHsv(r, g, b)
            val hh = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            // Button and Arrow detection
            if ((hh <= 14f || hh >= 346f) && s >= 0.55f && v >= 0.55f) redCount++
            if (hh in 70f..160f && s >= 0.5f && v >= 0.45f) greenCount++

            // Cloud detection: original extension tested cyan smoke formula
            // On idle screen: 0.12% (idle, timer completely stops!)
            // On real genie smoke screen: 44.3% (active, timer runs!)
            val isCloud = (v >= 0.85f && s in 0.08f..0.55f && hh in 165f..225f)

            if (isCloud) smokeCount++
        }

        val total = (width * height).toFloat()
        return ColorFractions(
            red = redCount / total,
            green = greenCount / total,
            smoke = smokeCount / total
        )
    }

    fun arrowSign(f: ColorFractions, min: Float, greenWins: Boolean): Int {
        if (greenWins) {
            if (f.green >= min) return 1
            if (f.red >= min) return -1
            return 0
        }
        if (f.green >= min && f.green > f.red) return 1
        if (f.red >= min && f.red > f.green) return -1
        return 0
    }

    fun readPopup(m: Map<String, ColorFractions>, cfg: HelperConfig): PopupReading {
        val t = cfg.thresholds
        val sellFrac = m["sell"] ?: ColorFractions()
        val equipFrac = m["equip"] ?: ColorFractions()
        val ribbonFrac = m["ribbon"] ?: ColorFractions()

        val popup = sellFrac.red >= t.sellRed &&
                equipFrac.green >= t.equipGreen &&
                ribbonFrac.red >= t.ribbonRed

        val power = arrowSign(m["hdr"] ?: ColorFractions(), t.header, true)
        val hpSign = arrowSign(m["hp"] ?: ColorFractions(), t.statArrow, false)
        val atkSign = arrowSign(m["atk"] ?: ColorFractions(), t.statArrow, false)
        val defSign = arrowSign(m["def"] ?: ColorFractions(), t.statArrow, false)

        val stats = mapOf("hp" to hpSign, "atk" to atkSign, "def" to defSign)
        val w = cfg.weights
        val score = (w["hp"] ?: 2) * hpSign + (w["atk"] ?: 3) * atkSign + (w["def"] ?: 1) * defSign
        val known = listOf(hpSign, atkSign, defSign).count { it != 0 }

        return PopupReading(popup, power, stats, score, known)
    }

    fun isDialog(m: Map<String, ColorFractions>, cfg: HelperConfig): Boolean {
        val t = cfg.thresholds
        val confirmFrac = m["dlgConfirm"] ?: ColorFractions()
        val cancelFrac = m["dlgCancel"] ?: ColorFractions()
        return confirmFrac.red >= t.dlgRed && cancelFrac.green >= t.dlgGreen
    }

    fun decide(r: PopupReading, cfg: HelperConfig): Decision {
        val hpSym = sym(r.stats["hp"] ?: 0)
        val atkSym = sym(r.stats["atk"] ?: 0)
        val defSym = sym(r.stats["def"] ?: 0)
        val desc = "power ${sym(r.power)} | HP $hpSym ATK $atkSym DEF $defSym (score ${r.score})"
        val statSign = r.score.sign

        var action: String
        var note = ""

        if (r.power != 0) {
            if (r.known == 0 || statSign == 0 || statSign == r.power) {
                action = if (r.power > 0) "equip" else "sell"
            } else {
                val mode = cfg.conflictMode
                note = " [power/stats disagree → $mode]"
                action = when (mode) {
                    "power" -> if (r.power > 0) "equip" else "sell"
                    "stats" -> if (statSign > 0) "equip" else "sell"
                    else -> "pause"
                }
            }
        } else if (r.known >= 2 && statSign != 0) {
            action = if (statSign > 0) "equip" else "sell"
            note = " [power unreadable, used stats]"
        } else {
            action = "pause"
            note = " [unreadable]"
        }

        return Decision(action, desc + note)
    }

    /**
     * Checks if the Auto-Use dialog is currently open by verifying the bright green Start button.
     * When closed: green fraction is 0.00%.
     * When open: green fraction is ~73.7%.
     */
    fun isAutoUseDialog(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val x0 = (0.40f * w).toInt()
        val x1 = (0.60f * w).toInt()
        val y0 = (0.63f * h).toInt()
        val y1 = (0.67f * h).toInt()

        if (x0 < 0 || y0 < 0 || x1 > w || y1 > h) return false
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0 || height <= 0) return false

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, x0, y0, width, height)

        var greenCount = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (g > 130 && g > r * 1.2f && g > b * 1.2f) {
                greenCount++
            }
        }
        val frac = greenCount.toFloat() / (width * height)
        return frac >= 0.35f
    }
}
