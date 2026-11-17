package com.maoyou.typingpet.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Apple 系の連続曲率（squircle）パス。
 * 円弧ではなく、スーパー楕円の 1/4 を角に使うので、角から直線へなめらかに繋がる。
 */
object AppleShape {
    private const val EXPONENT = 5.0
    private const val SEGMENTS = 18

    fun path(x: Float, y: Float, width: Float, height: Float, radius: Float): Path2D.Float {
        val w = width.coerceAtLeast(0f)
        val h = height.coerceAtLeast(0f)
        val r = radius.coerceIn(0f, min(w, h) / 2f)
        val p = Path2D.Float()
        if (w <= 0f || h <= 0f) return p
        if (r <= 0.1f) {
            p.append(Rectangle2D.Float(x, y, w, h), false)
            return p
        }

        p.moveTo(x + r, y)
        p.lineTo(x + w - r, y)
        corner(p, x + w - r, y + r, r, -PI / 2, 0.0)
        p.lineTo(x + w, y + h - r)
        corner(p, x + w - r, y + h - r, r, 0.0, PI / 2)
        p.lineTo(x + w - r, y + h)
        corner(p, x + r, y + h - r, r, PI / 2, PI)
        p.lineTo(x, y + r)
        corner(p, x + r, y + r, r, PI, PI * 3 / 2)
        p.closePath()
        return p
    }

    fun fill(g: Graphics2D, x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        g.color = color
        g.fill(path(x, y, width, height, radius))
    }

    fun stroke(
        g: Graphics2D,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Color,
        strokeWidth: Float = 1f,
    ) {
        if (width <= strokeWidth || height <= strokeWidth) return
        val inset = strokeWidth / 2f
        g.color = color
        g.stroke = BasicStroke(strokeWidth)
        g.draw(path(x + inset, y + inset, width - strokeWidth, height - strokeWidth, radius - inset))
    }

    private fun corner(
        p: Path2D.Float,
        cx: Float,
        cy: Float,
        radius: Float,
        start: Double,
        end: Double,
    ) {
        val r = radius.toDouble()
        val centerX = cx.toDouble()
        val centerY = cy.toDouble()
        val power = 2.0 / EXPONENT
        for (i in 1..SEGMENTS) {
            val t = start + (end - start) * i / SEGMENTS
            val c = cos(t)
            val s = sin(t)
            val px = centerX + r * sign(c) * abs(c).pow(power)
            val py = centerY + r * sign(s) * abs(s).pow(power)
            p.lineTo(px.toFloat(), py.toFloat())
        }
    }
}
