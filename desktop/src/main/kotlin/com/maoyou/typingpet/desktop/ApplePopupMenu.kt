package com.maoyou.typingpet.desktop

import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JWindow
import javax.swing.SwingUtilities

/** 角丸の macOS 風ポップアップメニュー。 */
class ApplePopupMenu : JPopupMenu() {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Color(0, 0, 0, 0)
    }

    fun addItem(text: String, action: () -> Unit) {
        add(AppleMenuItem(text, action))
    }

    fun addSeparatorLine() {
        add(AppleMenuSeparator())
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            AppleShape.fill(g2, 0.5f, 0.5f, width - 1f, height - 1f, 10f, Color.WHITE)
            AppleShape.stroke(g2, 0.5f, 0.5f, width - 1f, height - 1f, 10f, AppleUI.border, 1f)
        } finally {
            g2.dispose()
        }
    }

    override fun show(invoker: Component?, x: Int, y: Int) {
        super.show(invoker, x, y)
        SwingUtilities.invokeLater {
            val window = SwingUtilities.getWindowAncestor(this) ?: return@invokeLater
            if (window !is JWindow) return@invokeLater
            runCatching {
                window.shape = AppleShape.path(
                    0f,
                    0f,
                    window.width.toFloat(),
                    window.height.toFloat(),
                    10f,
                )
            }
        }
    }
}

private class AppleMenuItem(text: String, action: () -> Unit) : JMenuItem(text) {
    init {
        isOpaque = false
        isBorderPainted = false
        isFocusPainted = false
        border = BorderFactory.createEmptyBorder(7, 12, 7, 24)
        font = AppleUI.font(13)
        foreground = AppleUI.text
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = LEFT_ALIGNMENT
        addActionListener { action() }
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(text) + 38, 30)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            val armed = model.isArmed || model.isSelected
            if (armed) {
                AppleShape.fill(g2, 1f, 1f, width - 2f, height - 2f, 6f, AppleUI.accent)
            }
            g2.font = font
            g2.color = when {
                armed -> Color.WHITE
                isEnabled -> AppleUI.text
                else -> AppleUI.tertiary
            }
            val fm = g2.fontMetrics
            val ty = (height - fm.height) / 2 + fm.ascent
            g2.drawString(text, insets.left, ty)
        } finally {
            g2.dispose()
        }
    }
}

private class AppleMenuSeparator : JComponent() {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        preferredSize = Dimension(1, 9)
        maximumSize = Dimension(Int.MAX_VALUE, 9)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            g2.color = AppleUI.separator
            val y = height / 2
            g2.drawLine(10, y, width - 10, y)
        } finally {
            g2.dispose()
        }
    }
}
