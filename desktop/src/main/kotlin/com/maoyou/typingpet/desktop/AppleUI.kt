package com.maoyou.typingpet.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.max

/** macOS 風の配色と角丸コンポーネント */
object AppleUI {
    val windowBg = Color(0xFF, 0xFF, 0xFF)
    val text = Color(0x1D, 0x1D, 0x1F)
    val secondary = Color(0x6E, 0x6E, 0x73)
    val tertiary = Color(0x8E, 0x8E, 0x93)
    val separator = Color(0xE5, 0xE5, 0xEA)
    val border = Color(0xD2, 0xD2, 0xD7)
    val accent = Color(0x00, 0x7A, 0xFF)
    val accentHover = Color(0x14, 0x85, 0xFF)
    val accentPressed = Color(0x00, 0x62, 0xCC)
    val controlBg = Color(0xE8, 0xE8, 0xED)
    val controlHover = Color(0xDE, 0xDE, 0xE3)
    val controlPressed = Color(0xD2, 0xD2, 0xD7)
    val warning = Color(0xB2, 0x50, 0x00)

    private val family: String by lazy {
        val available = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrDefault(emptySet())
        listOf(
            "SF Pro Text",
            "SF Pro Display",
            "Helvetica Neue",
            "Segoe UI",
            "Noto Sans CJK SC",
            "Noto Sans",
            "DejaVu Sans",
            "Dialog",
        ).firstOrNull { it in available } ?: Font.SANS_SERIF
    }

    fun font(size: Int, bold: Boolean = false): Font =
        Font(family, if (bold) Font.BOLD else Font.PLAIN, size)

    fun antialias(g: Graphics): Graphics2D {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        )
        return g2
    }
}

/** 角丸の背景を描くパネル。子コンポーネントは透明のまま重ねる。 */
open class RoundedPanel(
    private val radius: Int = 14,
    private val fill: Color = AppleUI.windowBg,
) : JPanel() {
    var strokeColor: Color? = null
    var strokeWidth: Float = 1f

    init {
        isOpaque = false
        background = fill
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            AppleShape.fill(g2, 0.5f, 0.5f, width - 1f, height - 1f, radius.toFloat(), fill)
            strokeColor?.let {
                AppleShape.stroke(g2, 0f, 0f, width.toFloat(), height.toFloat(), radius.toFloat(), it, strokeWidth)
            }
        } finally {
            g2.dispose()
        }
    }
}

/** macOS 風の角丸ボタン。primary は青色の塗りつぶし。 */
class AppleButton(text: String, private val primary: Boolean = false) : JButton(text) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder()
        font = AppleUI.font(13, bold = primary)
        foreground = if (primary) Color.WHITE else AppleUI.text
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(preferredWidth(), 30)
    }

    constructor(text: String, primary: Boolean, onClick: () -> Unit) : this(text, primary) {
        addActionListener { onClick() }
    }

    override fun getPreferredSize(): Dimension {
        val base = super.getPreferredSize()
        return Dimension(max(preferredWidth(), base.width), max(30, base.height))
    }

    private fun preferredWidth(): Int {
        val fm = getFontMetrics(font)
        return fm.stringWidth(text) + if (primary) 34 else 30
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            val bg = when {
                !isEnabled -> if (primary) Color(0xB8, 0xD9, 0xFF) else AppleUI.controlBg
                model.isPressed -> if (primary) AppleUI.accentPressed else AppleUI.controlPressed
                model.isRollover -> if (primary) AppleUI.accentHover else AppleUI.controlHover
                else -> if (primary) AppleUI.accent else AppleUI.controlBg
            }
            AppleShape.fill(g2, 0f, 0f, width.toFloat(), height.toFloat(), 8f, bg)
            if (!primary) {
                AppleShape.stroke(g2, 0f, 0f, width.toFloat(), height.toFloat(), 8f, AppleUI.border, 1f)
            }
            g2.font = font
            g2.color = if (primary) Color.WHITE else if (isEnabled) AppleUI.text else AppleUI.tertiary
            val fm = g2.fontMetrics
            val tx = (width - fm.stringWidth(text)) / 2
            val ty = (height - fm.height) / 2 + fm.ascent
            g2.drawString(text, tx, ty)
        } finally {
            g2.dispose()
        }
    }
}

/** 角丸の入力欄。フォーカス時はアクセント色の枠。 */
class AppleTextField(columns: Int = 20) : JTextField(columns) {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
        font = AppleUI.font(13)
        foreground = AppleUI.text
        caretColor = AppleUI.accent
        selectionColor = Color(0xB3, 0xD7, 0xFF)
        selectedTextColor = AppleUI.text
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    override fun getPreferredSize(): Dimension {
        val base = super.getPreferredSize()
        return Dimension(base.width, max(32, base.height))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            AppleShape.fill(g2, 0f, 0f, width.toFloat(), height.toFloat(), 8f, Color.WHITE)
            AppleShape.stroke(
                g2,
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                8f,
                if (hasFocus()) AppleUI.accent else AppleUI.border,
                if (hasFocus()) 1.6f else 1f,
            )
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

/** 角丸のセグメントコントロール（小 / 中 / 大 など）。 */
class SegmentedControl(
    private val options: List<String>,
    initialIndex: Int = 0,
    private val onChange: (Int) -> Unit,
) : JPanel() {
    var selectedIndex: Int = initialIndex.coerceIn(0, max(0, options.size - 1))
        private set

    private var hoverIndex = -1
    private var pressIndex = -1

    init {
        isOpaque = false
        font = AppleUI.font(12)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(segmentWidth() * options.size, 30)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                pressIndex = indexAt(e.x)
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                val index = indexAt(e.x)
                if (index >= 0 && index == pressIndex && index != selectedIndex) {
                    selectedIndex = index
                    onChange(index)
                }
                pressIndex = -1
                hoverIndex = index
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                hoverIndex = -1
                pressIndex = -1
                repaint()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = indexAt(e.x)
                if (index != hoverIndex) {
                    hoverIndex = index
                    repaint()
                }
            }
        })
    }

    fun select(index: Int, notify: Boolean = false) {
        val next = index.coerceIn(0, max(0, options.size - 1))
        if (next == selectedIndex) return
        selectedIndex = next
        if (notify) onChange(next)
        repaint()
    }

    private fun segmentWidth(): Int {
        val fm = getFontMetrics(AppleUI.font(12))
        return max(58, options.maxOfOrNull { fm.stringWidth(it) } ?: 0) + 22
    }

    private fun indexAt(x: Int): Int {
        if (options.isEmpty() || width <= 0) return -1
        val index = x * options.size / width
        return index.coerceIn(0, options.size - 1)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            AppleShape.fill(g2, 0f, 0f, width.toFloat(), height.toFloat(), 8f, AppleUI.controlBg)

            val segWidth = width.toFloat() / max(1, options.size)
            val selected = selectedIndex.coerceIn(0, max(0, options.size - 1))
            val pillX = selected * segWidth + 2f
            val pillY = 2f
            val pillW = segWidth - 4f
            val pillH = height - 4f
            AppleShape.fill(g2, pillX, pillY, pillW, pillH, 6f, Color.WHITE)
            AppleShape.stroke(g2, pillX, pillY, pillW, pillH, 6f, Color(0x00, 0x00, 0x00, 0x14), 1f)

            options.forEachIndexed { index, option ->
                if (index == hoverIndex && index != selected && pressIndex < 0) {
                    AppleShape.fill(
                        g2,
                        index * segWidth + 2f,
                        2f,
                        segWidth - 4f,
                        height - 4f,
                        6f,
                        Color(0x00, 0x00, 0x00, 0x08),
                    )
                }
                g2.font = AppleUI.font(12, bold = index == selected)
                g2.color = if (index == selected) AppleUI.text else AppleUI.secondary
                val fm: FontMetrics = g2.fontMetrics
                val tx = (index * segWidth + (segWidth - fm.stringWidth(option)) / 2).toInt()
                val ty = (height - fm.height) / 2 + fm.ascent
                g2.drawString(option, tx, ty)
            }
        } finally {
            g2.dispose()
        }
    }
}

/** macOS 風のチェックボックス（角丸の四角 + チェック）。 */
class AppleCheckBox(text: String = "") : JCheckBox(text) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        border = BorderFactory.createEmptyBorder()
        font = AppleUI.font(13)
        foreground = AppleUI.text
        icon = BoxIcon(false)
        selectedIcon = BoxIcon(true)
        iconTextGap = 8
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private class BoxIcon(private val selected: Boolean) : Icon {
        override fun getIconWidth(): Int = 18
        override fun getIconHeight(): Int = 18

        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val g2 = AppleUI.antialias(g)
            try {
                if (selected) {
                    AppleShape.fill(g2, x + 0.5f, y + 0.5f, 17f, 17f, 5f, AppleUI.accent)
                    g2.color = Color.WHITE
                    g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    val check = Path2D.Float().apply {
                        moveTo(x + 4.5f, y + 9f)
                        lineTo(x + 7.8f, y + 12.3f)
                        lineTo(x + 13.5f, y + 5.8f)
                    }
                    g2.draw(check)
                } else {
                    AppleShape.fill(g2, x + 0.5f, y + 0.5f, 17f, 17f, 5f, Color.WHITE)
                    AppleShape.stroke(g2, x + 0.5f, y + 0.5f, 17f, 17f, 5f, AppleUI.border, 1.2f)
                }
            } finally {
                g2.dispose()
            }
        }
    }
}

/** macOS 風のトグルスイッチ。 */
class AppleSwitch(initial: Boolean = false, private val onChange: (Boolean) -> Unit) : JComponent() {
    var selected: Boolean = initial
        private set

    init {
        isOpaque = false
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(42, 24)
        minimumSize = preferredSize
        addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && contains(e.point)) toggle()
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) = repaint()
        })
        getInputMap().put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "toggle")
        getActionMap().put(
            "toggle",
            object : javax.swing.AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent) = toggle()
            },
        )
    }

    fun setSelected(value: Boolean, notify: Boolean = false) {
        if (selected == value) return
        selected = value
        if (notify) onChange(value)
        repaint()
    }

    private fun toggle() {
        selected = !selected
        onChange(selected)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = AppleUI.antialias(g)
        try {
            val h = height.toFloat()
            val capsule = RoundRectangle2D.Float(0f, 0f, width.toFloat(), h, h, h)
            g2.color = if (selected) AppleUI.accent else AppleUI.controlPressed
            g2.fill(capsule)

            val knob = (h - 6f).coerceAtLeast(14f)
            val x = if (selected) width - knob - 3f else 3f
            g2.color = Color(0x00, 0x00, 0x00, 0x22)
            g2.fill(Ellipse2D.Float(x, 4f, knob, knob))
            g2.color = Color.WHITE
            g2.fill(Ellipse2D.Float(x, 3f, knob, knob))

            if (hasFocus()) {
                g2.color = AppleUI.accent
                g2.stroke = BasicStroke(1.5f)
                g2.draw(
                    RoundRectangle2D.Float(
                        -1.5f,
                        -1.5f,
                        width + 3f,
                        h + 3f,
                        h + 6f,
                        h + 6f,
                    ),
                )
            }
        } finally {
            g2.dispose()
        }
    }
}

/** macOS 風のスライダー。細いトラック + 白い丸ノブ。 */
class AppleSlider(minimum: Int, maximum: Int, value: Int) : JSlider(minimum, maximum, value) {
    init {
        isOpaque = false
        isFocusable = false
        font = AppleUI.font(12)
        preferredSize = Dimension(140, 24)
        setUI(AppleSliderUi(this))
    }

    private class AppleSliderUi(slider: JSlider) : BasicSliderUI(slider) {
        override fun getThumbSize(): Dimension = Dimension(16, 16)

        override fun paintFocus(g: Graphics) = Unit

        override fun paintTrack(g: Graphics) {
            val g2 = AppleUI.antialias(g)
            try {
                val cy = trackRect.y + trackRect.height / 2f
                val x = trackRect.x.toFloat()
                val w = trackRect.width.toFloat()
                g2.color = AppleUI.controlPressed
                g2.fill(RoundRectangle2D.Float(x, cy - 2f, w, 4f, 4f, 4f))

                val ratio = if (slider.maximum == slider.minimum) {
                    0f
                } else {
                    (slider.value - slider.minimum).toFloat() / (slider.maximum - slider.minimum)
                }
                g2.color = AppleUI.accent
                g2.fill(RoundRectangle2D.Float(x, cy - 2f, w * ratio, 4f, 4f, 4f))
            } finally {
                g2.dispose()
            }
        }

        override fun paintThumb(g: Graphics) {
            val g2 = AppleUI.antialias(g)
            try {
                val cx = thumbRect.x + thumbRect.width / 2f
                val cy = thumbRect.y + thumbRect.height / 2f
                g2.color = Color(0x00, 0x00, 0x00, 0x1F)
                g2.fill(Ellipse2D.Float(cx - 8f, cy - 7f, 16f, 16f))
                g2.color = Color.WHITE
                g2.fill(Ellipse2D.Float(cx - 8f, cy - 8f, 16f, 16f))
                g2.color = AppleUI.border
                g2.stroke = BasicStroke(1f)
                g2.draw(Ellipse2D.Float(cx - 7.5f, cy - 7.5f, 15f, 15f))
            } finally {
                g2.dispose()
            }
        }
    }
}

/** ダイアログに角丸の窓形状・透明背景・ドラッグ移動を付ける。 */
fun JDialog.styleAppleWindow(cornerRadius: Int = 14) {
    isUndecorated = true
    isResizable = false
    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    isAlwaysOnTop = true
    runCatching { background = Color(0, 0, 0, 0) }
    addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            applyAppleShape(cornerRadius)
        }
    })
    SwingUtilities.invokeLater { applyAppleShape(cornerRadius) }
}

fun JDialog.enableWindowDrag() {
    val handler = object : MouseAdapter() {
        private var origin: Point? = null

        override fun mousePressed(e: MouseEvent) {
            origin = e.point
        }

        override fun mouseReleased(e: MouseEvent) {
            origin = null
        }

        override fun mouseDragged(e: MouseEvent) {
            val start = origin ?: return
            setLocation(e.xOnScreen - start.x, e.yOnScreen - start.y)
        }
    }
    contentPane.addMouseListener(handler)
    contentPane.addMouseMotionListener(handler)
}

private fun Window.applyAppleShape(radius: Int) {
    if (width <= 0 || height <= 0) return
    runCatching {
        shape = AppleShape.path(0f, 0f, width.toFloat(), height.toFloat(), radius.toFloat())
    }
}

/** 中央揃えのラベルを作る小さなヘルパー。 */
fun label(text: String, size: Int = 13, color: Color = AppleUI.text, bold: Boolean = false) =
    javax.swing.JLabel(text).apply {
        font = AppleUI.font(size, bold)
        foreground = color
        horizontalAlignment = SwingConstants.LEFT
        isOpaque = false
    }

/** 数値を丸めて表示するための小さなヘルパー。 */
fun percentText(value: Int): String = "${value.coerceIn(0, 100)}%"
