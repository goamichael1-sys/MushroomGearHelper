package com.mushroom.gearhelper

import android.graphics.PointF
import android.graphics.RectF

data class Thresholds(
    val sellRed: Float = 0.40f,
    val equipGreen: Float = 0.40f,
    val ribbonRed: Float = 0.20f,
    val header: Float = 0.06f,
    val statArrow: Float = 0.06f,
    val smoke: Float = 0.10f,
    val dlgRed: Float = 0.35f,
    val dlgGreen: Float = 0.35f
)

data class HelperConfig(
    var conflictMode: String = "stats", // "stats", "power", "pause"
    var scanIdleMs: Long = 1200L,
    var scanPopupMs: Long = 700L,
    var postActionDelayMs: Long = 1300L,
    val weights: Map<String, Int> = mapOf("hp" to 2, "atk" to 3, "def" to 1),
    val thresholds: Thresholds = Thresholds(),
    val points: Map<String, PointF> = mapOf(
        "sell" to PointF(0.327f, 0.826f),
        "equip" to PointF(0.670f, 0.826f),
        "close" to PointF(0.966f, 0.756f),
        "gear" to PointF(0.514f, 0.609f),
        "confirm" to PointF(0.312f, 0.579f),
        "cancel" to PointF(0.685f, 0.577f)
    ),
    val regions: Map<String, RectF> = mapOf(
        "sell" to RectF(0.231f, 0.814f, 0.427f, 0.839f),
        "equip" to RectF(0.573f, 0.814f, 0.770f, 0.839f),
        "ribbon" to RectF(0.137f, 0.525f, 0.277f, 0.547f),
        "hdr" to RectF(0.327f, 0.523f, 0.396f, 0.551f),
        "hp" to RectF(0.763f, 0.600f, 0.813f, 0.621f),
        "atk" to RectF(0.763f, 0.625f, 0.813f, 0.646f),
        "def" to RectF(0.763f, 0.649f, 0.813f, 0.670f),
        "smoke" to RectF(0.246f, 0.579f, 0.776f, 0.649f),
        "dlgConfirm" to RectF(0.215f, 0.566f, 0.405f, 0.592f),
        "dlgCancel" to RectF(0.600f, 0.566f, 0.775f, 0.592f)
    )
)

data class ColorFractions(
    val red: Float = 0f,
    val green: Float = 0f,
    val smoke: Float = 0f
)

data class PopupReading(
    val popup: Boolean,
    val power: Int,
    val stats: Map<String, Int>,
    val score: Int,
    val known: Int
)

data class Decision(
    val action: String, // "sell", "equip", "pause", "none"
    val reason: String
)
