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

            // Cloud detection: checks Cyan (rare/normal), Purple (epic), and Gold sparkles (legendary)
            // On idle screen: ~3.5% (below 10% threshold -> timer completely stopped when idle!)
            // On purple cloud: ~48.9% - 53.7% (confirms gear!)
            // On cyan cloud: ~42.8% - 44.3% (confirms gear!)
            val isCyan = (v >= 0.82f && s in 0.08f..0.55f && hh in 165f..225f)
            val isPurple = (v >= 0.70f && s in 0.15f..0.70f && hh in 245f..320f)
            val isGold = (v >= 0.90f && s in 0.18f..0.65f && hh in 38f..65f)
            val isCloud = isCyan || isPurple || isGold

            if (isCloud) smokeCount++
        }

        val total = (width * height).toFloat()
        return ColorFractions(
            red = redCount / total,
            green = greenCount / total,
            smoke = smokeCount / total
        )
    }

    /**
     * Calculates the centroid (center of mass) of the cloud/smoke in normalized coordinates (0..1).
     * Returns null if not enough cloud pixels are present (< 8% of the region).
     */
    fun getCloudCentroid(bitmap: Bitmap, smokeRegion: android.graphics.RectF = android.graphics.RectF(0.246f, 0.530f, 0.776f, 0.670f)): android.graphics.PointF? {
        val w = bitmap.width
        val h = bitmap.height
        val x0 = (smokeRegion.left * w).toInt().coerceIn(0, w)
        val x1 = (smokeRegion.right * w).toInt().coerceIn(0, w)
        val y0 = (smokeRegion.top * h).toInt().coerceIn(0, h)
        val y1 = (smokeRegion.bottom * h).toInt().coerceIn(0, h)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, x0, y0, width, height)

        var totalX = 0L
        var totalY = 0L
        var count = 0

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val pixel = pixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val hsv = rgbToHsv(r, g, b)
                val hh = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                val isCyan = (v >= 0.82f && s in 0.08f..0.55f && hh in 165f..225f)
                val isPurple = (v >= 0.70f && s in 0.15f..0.70f && hh in 245f..320f)
                val isGold = (v >= 0.90f && s in 0.18f..0.65f && hh in 38f..65f)
                if (isCyan || isPurple || isGold) {
                    totalX += (x0 + x)
                    totalY += (y0 + y)
                    count++
                }
            }
        }

        val totalArea = width * height
        if (count < totalArea * 0.08f) return null

        val avgX = (totalX.toFloat() / count) / w
        val avgY = (totalY.toFloat() / count) / h
        return android.graphics.PointF(avgX, avgY)
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
        val x0 = (0.38f * w).toInt()
        val x1 = (0.62f * w).toInt()
        val y0 = (0.62f * h).toInt()
        val y1 = (0.68f * h).toInt()

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
        return frac >= 0.25f
    }
}
