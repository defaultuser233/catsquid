package com.maoyou.typingpet

import kotlin.math.roundToInt

/** 位置の比率(0..1)と画面ピクセルの変換。Android に依存しない純粋関数 */
object PositionMath {
    fun pxForAnchor(anchor: Double, max: Int): Int {
        if (max <= 0) return 0
        val safe = if (anchor.isNaN()) 0.0 else anchor.coerceIn(0.0, 1.0)
        return (safe * max).roundToInt().coerceIn(0, max)
    }

    fun anchorForPx(px: Int, max: Int): Double {
        if (max <= 0) return 0.0
        return (px.toDouble() / max).coerceIn(0.0, 1.0)
    }
}
