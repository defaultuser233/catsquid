package com.maoyou.typingpet.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.ImageObserver
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * 透明・枠なしのペットウィンドウ。すべての Swing 操作は EDT で行う。
 * グローバルフックのスレッドから postAction() が呼ばれても安全。
 */
class PetWindow(private val config: AppConfig) : AutoCloseable {
    private val stateMachine = PetStateMachine()
    private val disposed = AtomicBoolean(false)
    private val rng = java.util.Random()

    private lateinit var frame: JFrame
    private lateinit var panel: PetPanel
    private lateinit var idleTimer: Timer

    private var currentKey: String? = null
    private var gifImage: Image? = null
    private var idleIndex = 1
    private var aspect = DEFAULT_ASPECT
    private var dragOffset: Point? = null
    private var dragged = false
    private var settingsDialog: JDialog? = null
    private var hookDialog: JDialog? = null

    init {
        onEdtAndWait { buildUi() }
    }

    fun show() {
        onEdt {
            if (disposed.get()) return@onEdt
            if (frame.isVisible) {
                frame.toFront()
                return@onEdt
            }
            applyAlwaysOnTop(config.alwaysOnTop)
            resizeFrame()
            frame.setLocation(initialLocation(frame.size))
            frame.isVisible = true
            applyAlwaysOnTop(config.alwaysOnTop)
            applyOpacity(config.opacity)
            idleTimer.start()
        }
    }

    /** フックのスレッドから呼ばれる。表示前・close() 後でも安全。 */
    fun postAction(action: KeyAction) {
        if (action == KeyAction.Ignore || disposed.get()) return
        SwingUtilities.invokeLater {
            if (disposed.get()) return@invokeLater
            stateMachine.onAction(action)
            updateGif()
        }
    }

    fun showSettings() {
        onEdt {
            if (disposed.get()) return@onEdt
            val existing = settingsDialog
            if (existing != null && existing.isDisplayable) {
                existing.isVisible = true
                existing.toFront()
                return@onEdt
            }
            settingsDialog = SettingsDialog(frame, config) { applySettings() }.also {
                it.isVisible = true
            }
        }
    }

    fun showHookUnavailable(reason: String?) {
        onEdt {
            if (disposed.get()) return@onEdt
            val existing = hookDialog
            if (existing != null && existing.isDisplayable) {
                existing.isVisible = true
                existing.toFront()
                return@onEdt
            }
            hookDialog = HookUnavailableDialog(frame, reason) { postAction(it) }.also {
                it.isVisible = true
            }
        }
    }

    override fun close() {
        if (!disposed.compareAndSet(false, true)) return
        val cleanup = {
            idleTimer.stop()
            settingsDialog?.dispose()
            hookDialog?.dispose()
            settingsDialog = null
            hookDialog = null
            panel.frameImage = null
            frame.isVisible = false
            frame.dispose()
        }
        if (SwingUtilities.isEventDispatchThread()) cleanup() else SwingUtilities.invokeLater(cleanup)
    }

    private fun buildUi() {
        idleIndex = randomIdle()

        frame = JFrame("猫鱿")
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.isUndecorated = true
        frame.isAutoRequestFocus = false
        runCatching { frame.background = Color(0, 0, 0, 0) }
        applyAlwaysOnTop(config.alwaysOnTop)
        applyOpacity(config.opacity)

        panel = PetPanel()
        panel.isOpaque = false
        panel.background = Color(0, 0, 0, 0)
        frame.contentPane = panel
        installMouseHandlers()

        idleTimer = Timer(100) { onTick() }
        idleTimer.isCoalesce = true

        updateGif(force = true)
        resizeFrame()
    }

    private fun onTick() {
        if (disposed.get()) return
        val previous = stateMachine.state
        val next = stateMachine.tick()
        if (next != previous) {
            if (next == PetState.IDLE) idleIndex = randomIdle()
            updateGif(force = true)
        }
    }

    private fun updateGif(force: Boolean = false) {
        val key = gifKey()
        if (!force && key == currentKey) return
        val gif = loadWithFallback(key)
        currentKey = key
        if (gif != null) {
            aspect = gif.width.toDouble() / gif.height.toDouble()
            gifImage = gif.newIcon().image
        } else {
            gifImage = null
        }
        panel.frameImage = gifImage
        panel.repaint()
        resizeFrame()
    }

    private fun gifKey(): String = when (stateMachine.state) {
        PetState.IDLE -> "idle_$idleIndex"
        PetState.TYPING -> if (stateMachine.typingStep % 2 == 1) "typing_1" else "typing_2"
        PetState.DELETE -> "delete"
        PetState.SPACE -> "space"
        PetState.EXCLAMATION -> "exclamation"
    }

    private fun loadWithFallback(key: String): GifFrames? {
        loadGifFrames(key)?.let { return it }
        for (i in idleCandidates()) {
            val fallback = "idle_$i"
            if (fallback != key) loadGifFrames(fallback)?.let { return it }
        }
        return null
    }

    private fun idleCandidates(): List<Int> {
        val start = randomIdle()
        return listOf(start, start % 3 + 1, (start + 1) % 3 + 1)
    }

    private fun randomIdle(): Int = rng.nextInt(3) + 1

    private fun applySettings() {
        if (disposed.get()) return
        applyAlwaysOnTop(config.alwaysOnTop)
        applyOpacity(config.opacity)
        resizeFrame()
        if (frame.isVisible) clampToScreen()
        panel.repaint()
    }

    private fun applyAlwaysOnTop(value: Boolean) {
        runCatching { frame.isAlwaysOnTop = value }
    }

    private fun applyOpacity(value: Float) {
        runCatching { frame.opacity = value.coerceIn(0.3f, 1f) }
    }

    private fun resizeFrame() {
        val height = config.sizeHeight()
        val width = (height * aspect).roundToInt().coerceAtLeast(1)
        if (frame.width != width || frame.height != height) {
            frame.setSize(width, height)
        }
    }

    private fun initialLocation(size: Dimension): Point {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val primary = ge.defaultScreenDevice.defaultConfiguration.bounds
        val desired = if (config.posX >= 0 && config.posY >= 0) {
            Point(config.posX, config.posY)
        } else {
            Point(
                primary.x + primary.width - size.width - 48,
                primary.y + primary.height - size.height - 96,
            )
        }
        val screen = ge.screenDevices
            .map { it.defaultConfiguration.bounds }
            .firstOrNull { it.contains(desired) }
            ?: primary
        return clamp(desired, size, screen)
    }

    private fun clampToScreen() {
        val size = frame.size
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val screens = ge.screenDevices.map { it.defaultConfiguration.bounds }
        val center = Point(frame.x + size.width / 2, frame.y + size.height / 2)
        val screen = screens.firstOrNull { it.contains(center) }
            ?: ge.defaultScreenDevice.defaultConfiguration.bounds
        val clamped = clamp(Point(frame.x, frame.y), size, screen)
        if (clamped.x != frame.x || clamped.y != frame.y) frame.setLocation(clamped)
    }

    private fun clamp(point: Point, size: Dimension, bounds: Rectangle): Point {
        val maxX = (bounds.x + bounds.width - size.width).coerceAtLeast(bounds.x)
        val maxY = (bounds.y + bounds.height - size.height).coerceAtLeast(bounds.y)
        return Point(
            point.x.coerceIn(bounds.x, maxX),
            point.y.coerceIn(bounds.y, maxY),
        )
    }

    private fun installMouseHandlers() {
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopup(e)
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e) && !config.locked) {
                    dragOffset = e.point
                    dragged = false
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopup(e)
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e) && dragOffset != null) {
                    if (dragged) {
                        config.posX = frame.x
                        config.posY = frame.y
                        config.save()
                    }
                    dragOffset = null
                    dragged = false
                }
            }
        })
        panel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val offset = dragOffset ?: return
                if (config.locked) return
                dragged = true
                frame.setLocation(e.xOnScreen - offset.x, e.yOnScreen - offset.y)
            }
        })
    }

    private fun showPopup(e: MouseEvent) {
        val menu = JPopupMenu()
        val settings = JMenuItem("设置")
        settings.addActionListener { showSettings() }
        val quit = JMenuItem("退出")
        quit.addActionListener {
            close()
            System.exit(0)
        }
        menu.add(settings)
        menu.addSeparator()
        menu.add(quit)
        menu.show(e.component, e.x, e.y)
    }

    private fun onEdtAndWait(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait { block() }
        }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater { block() }
        }
    }

    private inner class PetPanel : JPanel() {
        @Volatile
        var frameImage: Image? = null

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON,
                )
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                val image = frameImage
                if (image != null) {
                    g2.drawImage(image, 0, 0, width, height, this)
                } else {
                    drawFallback(g2)
                }
            } finally {
                g2.dispose()
            }
        }

        override fun imageUpdate(
            img: Image?,
            infoflags: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
        ): Boolean {
            if (img !== frameImage) return false
            if (infoflags and (ImageObserver.FRAMEBITS or ImageObserver.ALLBITS or ImageObserver.SOMEBITS) != 0) {
                repaint()
            }
            return true
        }

        private fun drawFallback(g: Graphics2D) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 1f || h <= 1f) return
            val s = minOf(w, h)
            val cx = w / 2f
            val cy = h / 2f

            g.color = Color(0xE8, 0x9B, 0x5C)
            val leftEar = Path2D.Float().apply {
                moveTo(cx - s * 0.30f, cy - s * 0.10f)
                lineTo(cx - s * 0.28f, cy - s * 0.42f)
                lineTo(cx - s * 0.06f, cy - s * 0.22f)
                closePath()
            }
            val rightEar = Path2D.Float().apply {
                moveTo(cx + s * 0.30f, cy - s * 0.10f)
                lineTo(cx + s * 0.28f, cy - s * 0.42f)
                lineTo(cx + s * 0.06f, cy - s * 0.22f)
                closePath()
            }
            g.fill(leftEar)
            g.fill(rightEar)
            g.fill(Ellipse2D.Float(cx - s * 0.34f, cy - s * 0.18f, s * 0.68f, s * 0.58f))

            g.color = Color(0x33, 0x2B, 0x25)
            val eye = s * 0.07f
            g.fill(Ellipse2D.Float(cx - s * 0.16f, cy - s * 0.02f, eye, eye * 1.25f))
            g.fill(Ellipse2D.Float(cx + s * 0.09f, cy - s * 0.02f, eye, eye * 1.25f))

            g.stroke = BasicStroke(maxOf(1f, s * 0.018f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawArc((cx - s * 0.05f).toInt(), (cy + s * 0.08f).toInt(), (s * 0.10f).toInt(), (s * 0.08f).toInt(), 200, 140)
        }
    }

    private companion object {
        const val DEFAULT_ASPECT = 192.0 / 138.0
    }
}

/** グローバルフックが使えないときの案内。閉じてもペットは動き続ける。 */
private class HookUnavailableDialog(
    owner: Frame?,
    reason: String?,
    onAction: (KeyAction) -> Unit,
) : JDialog(owner, "⚠ 无法捕获全局按键", false) {

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        isResizable = false
        isAlwaysOnTop = true

        val root = JPanel(java.awt.BorderLayout(0, 12))
        root.border = BorderFactory.createEmptyBorder(16, 18, 14, 18)

        val heading = JLabel("全局按键捕获启动失败")
        heading.font = heading.font.deriveFont(Font.BOLD, heading.font.size2D + 1f)
        heading.alignmentX = Component.LEFT_ALIGNMENT

        val reasonArea = JTextArea(reason?.takeIf { it.isNotBlank() } ?: "未知原因")
        reasonArea.isEditable = false
        reasonArea.isOpaque = false
        reasonArea.lineWrap = true
        reasonArea.wrapStyleWord = true
        reasonArea.rows = 2
        reasonArea.columns = 38
        reasonArea.alignmentX = Component.LEFT_ALIGNMENT
        reasonArea.maximumSize = Dimension(520, reasonArea.preferredSize.height)

        val hint = JLabel(
            "<html>Linux 提示：全局按键需要 X11 会话。Wayland 会阻止应用监听系统按键；" +
                "<br>请在登录界面选择 “X11” 会话后重新启动猫鱿。</html>",
        )
        hint.foreground = Color(0x8A, 0x6D, 0x3B)
        hint.alignmentX = Component.LEFT_ALIGNMENT

        val testLabel = JLabel("在下面的输入框里打字，宠物仍会作出反应：")
        testLabel.alignmentX = Component.LEFT_ALIGNMENT

        val testField = JTextField(24)
        testField.alignmentX = Component.LEFT_ALIGNMENT
        testField.maximumSize = Dimension(520, testField.preferredSize.height)
        testField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // 文字キーは keyTyped 側で扱い、二重反応を避ける
                val action = KeyClassifier.fromAwt(e.keyCode, e.keyChar)
                if (action == KeyAction.Delete) onAction(action)
            }

            override fun keyTyped(e: KeyEvent) {
                val char = e.keyChar
                if (char >= ' ' && char != '\u007F') onAction(KeyClassifier.classify(char))
            }
        })
        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                testField.requestFocusInWindow()
            }
        })

        val box = JPanel()
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)
        box.add(heading)
        box.add(Box.createVerticalStrut(8))
        box.add(reasonArea)
        box.add(Box.createVerticalStrut(10))
        box.add(hint)
        box.add(Box.createVerticalStrut(14))
        box.add(testLabel)
        box.add(Box.createVerticalStrut(6))
        box.add(testField)

        val closeButton = JButton("关闭")
        closeButton.addActionListener { dispose() }
        val bottom = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT))
        bottom.add(closeButton)

        root.add(box, java.awt.BorderLayout.CENTER)
        root.add(bottom, java.awt.BorderLayout.SOUTH)
        contentPane = root

        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        pack()
        setLocationRelativeTo(owner)
    }
}
