package com.maoyou.typingpet.desktop

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import kotlin.math.roundToInt

/** macOS 風の角丸設定ウィンドウ。変更は即座に反映して保存する（モードレス）。 */
class SettingsDialog(
    owner: Frame?,
    config: AppConfig,
    onApply: () -> Unit,
) : JDialog(owner, "猫鱿 设置", false) {

    init {
        styleAppleWindow()
        contentPane = SettingsPanel(config, onApply) { dispose() }
        pack()
        setLocationRelativeTo(owner)
        enableWindowDrag()
        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
    }
}

/** 角丸カードの中身。ダイアログなしでも描画できるよう分離している。 */
class SettingsPanel(
    private val config: AppConfig,
    private val onApply: () -> Unit,
    private val onDone: () -> Unit,
) : RoundedPanel(14, AppleUI.windowBg) {

    init {
        border = BorderFactory.createEmptyBorder(22, 22, 18, 22)
        layout = BorderLayout(0, 18)
        add(buildHeader(), BorderLayout.NORTH)
        add(buildForm(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)
        preferredSize = Dimension(392, preferredSize.height)
    }

    private fun buildHeader(): JComponent {
        val box = JPanel()
        box.isOpaque = false
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)
        box.alignmentX = 0f
        box.add(label("猫鱿 设置", 17, AppleUI.text, bold = true))
        box.add(Box.createVerticalStrut(4))
        box.add(label("外观与窗口", 12, AppleUI.secondary))
        return box
    }

    private fun buildForm(): JComponent {
        val form = JPanel()
        form.isOpaque = false
        form.layout = BoxLayout(form, BoxLayout.Y_AXIS)

        val size = SegmentedControl(
            options = listOf("小", "中", "大"),
            initialIndex = when (config.sizeKey) {
                "small" -> 0
                "medium" -> 1
                else -> 2
            },
        ) { index ->
            config.sizeKey = when (index) {
                0 -> "small"
                1 -> "medium"
                else -> "large"
            }
            applyAndSave()
        }

        val alwaysOnTop = AppleSwitch(config.alwaysOnTop) { checked ->
            config.alwaysOnTop = checked
            applyAndSave()
        }

        val slider = AppleSlider(30, 100, (config.opacity * 100).roundToInt().coerceIn(30, 100))
        val percent = label(percentText(slider.value), 12, AppleUI.secondary).apply {
            horizontalAlignment = SwingConstants.RIGHT
            preferredSize = Dimension(40, 24)
        }
        slider.addChangeListener {
            val value = slider.value
            percent.text = percentText(value)
            config.opacity = value / 100f
            onApply()
            if (!slider.valueIsAdjusting) config.save()
        }
        val sliderRow = JPanel(BorderLayout(10, 0))
        sliderRow.isOpaque = false
        sliderRow.add(slider, BorderLayout.CENTER)
        sliderRow.add(percent, BorderLayout.EAST)
        sliderRow.preferredSize = Dimension(196, 24)

        form.add(row("大小", size))
        form.add(Box.createVerticalStrut(10))
        form.add(row("总在最前", alwaysOnTop))
        form.add(Box.createVerticalStrut(10))
        form.add(row("不透明度", sliderRow))
        return form
    }

    private fun row(text: String, control: JComponent): JComponent {
        val row = JPanel(BorderLayout(12, 0))
        row.isOpaque = false
        row.alignmentX = JComponent.LEFT_ALIGNMENT
        row.maximumSize = Dimension(Int.MAX_VALUE, 30)
        row.add(
            label(text, 13, AppleUI.text).apply { preferredSize = Dimension(84, 30) },
            BorderLayout.WEST,
        )
        row.add(ControlHolder(control), BorderLayout.CENTER)
        return row
    }

    private fun buildFooter(): JComponent {
        val footer = JPanel(BorderLayout(0, 14))
        footer.isOpaque = false

        val separator = JPanel()
        separator.isOpaque = true
        separator.background = AppleUI.separator
        separator.preferredSize = Dimension(1, 1)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        buttons.isOpaque = false
        buttons.add(AppleButton("完成", primary = true) { onDone() })

        footer.add(separator, BorderLayout.NORTH)
        footer.add(buttons, BorderLayout.SOUTH)
        return footer
    }

    private fun applyAndSave() {
        onApply()
        config.save()
    }
}

/** 右寄せ・縦中央で 1 個のコントロールを置く。 */
private class ControlHolder(private val control: JComponent) : JPanel() {
    init {
        isOpaque = false
        layout = null
        preferredSize = control.preferredSize
        add(control)
    }

    override fun doLayout() {
        val width = minOf(control.preferredSize.width, this.width)
        val height = minOf(control.preferredSize.height, this.height)
        control.setBounds(this.width - width, (this.height - height) / 2, width, height)
    }
}
