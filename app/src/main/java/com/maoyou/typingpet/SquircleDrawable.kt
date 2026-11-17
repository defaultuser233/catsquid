package com.maoyou.typingpet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * 円弧ではなくスーパー楕円の角を使う、Apple 風の連続曲率（squircle）背景。
 * radius は角の大きさ（px）。
 */
class SquircleDrawable(
    private val fillColor: Int,
    private val radiusPx: Float,
    private val strokeWidthPx: Int = 0,
    private val strokeColor: Int = Color.TRANSPARENT,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        buildPath(
            path,
            b.left.toFloat(),
            b.top.toFloat(),
            b.right.toFloat(),
            b.bottom.toFloat(),
            radiusPx,
        )
        if (Color.alpha(fillColor) != 0) {
            fillPaint.color = fillColor
            canvas.drawPath(path, fillPaint)
        }
        if (strokeWidthPx > 0 && Color.alpha(strokeColor) != 0) {
            strokePaint.color = strokeColor
            strokePaint.strokeWidth = strokeWidthPx.toFloat()
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        if (Build.VERSION.SDK_INT >= 30) {
            buildPath(
                path,
                b.left.toFloat(),
                b.top.toFloat(),
                b.right.toFloat(),
                b.bottom.toFloat(),
                radiusPx,
            )
            outline.setPath(path)
        } else {
            outline.setRoundRect(Rect(b), radiusPx)
        }
    }
}

private const val SQUIRCLE_EXPONENT = 5.0
private const val SQUIRCLE_SEGMENTS = 18

/** 連続曲率の角を持つ矩形パスを作る。 */
internal fun buildPath(
    path: Path,
    x: Float,
    y: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    val w = right - x
    val h = bottom - y
    path.reset()
    if (w <= 0f || h <= 0f) return
    val r = radius.coerceIn(0f, min(w, h) / 2f)
    if (r <= 0.1f) {
        path.addRect(x, y, right, bottom, Path.Direction.CW)
        return
    }

    path.moveTo(x + r, y)
    path.lineTo(right - r, y)
    squircleCorner(path, right - r, y + r, r, -PI / 2, 0.0)
    path.lineTo(right, bottom - r)
    squircleCorner(path, right - r, bottom - r, r, 0.0, PI / 2)
    path.lineTo(right - r, bottom)
    squircleCorner(path, x + r, bottom - r, r, PI / 2, PI)
    path.lineTo(x, y + r)
    squircleCorner(path, x + r, y + r, r, PI, PI * 3 / 2)
    path.close()
}

private fun squircleCorner(
    path: Path,
    cx: Float,
    cy: Float,
    radius: Float,
    start: Double,
    end: Double,
) {
    val r = radius.toDouble()
    val centerX = cx.toDouble()
    val centerY = cy.toDouble()
    val power = 2.0 / SQUIRCLE_EXPONENT
    for (i in 1..SQUIRCLE_SEGMENTS) {
        val t = start + (end - start) * i / SQUIRCLE_SEGMENTS
        val c = cos(t)
        val s = sin(t)
        val px = centerX + r * sign(c) * abs(c).pow(power)
        val py = centerY + r * sign(s) * abs(s).pow(power)
        path.lineTo(px.toFloat(), py.toFloat())
    }
}
