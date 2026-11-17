package com.maoyou.typingpet.desktop

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.KeyStroke
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/** 表示設定。変更は即座にペットへ反映して保存する（モードレス）。 */
class SettingsDialog(
    owner: Frame?,
    private val config: AppConfig,
    private val onApply: () -> Unit,
) : JDialog(owner, "🐾 猫鱿 设置", false) {

    private val sizeBox = JComboBox(arrayOf("小", "中", "大"))
    private val alwaysOnTopBox = JCheckBox("总在最前")
    private val opacitySlider = JSlider(30, 100)
    private val opacityLabel = JLabel("100%")

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        isResizable = false
        isAlwaysOnTop = true

        val root = JPanel(BorderLayout(0, 14))
        root.border = BorderFactory.createEmptyBorder(16, 18, 14, 18)

        val title = JLabel("显示")
        title.font = title.font.deriveFont(Font.BOLD, title.font.size2D + 1f)

        val form = JPanel(GridBagLayout())
        addRow(form, 0, "大小", sizeBox)

        val topRow = GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            gridwidth = 2
            anchor = GridBagConstraints.LINE_START
            insets = Insets(8, 0, 4, 0)
        }
        form.add(alwaysOnTopBox, topRow)

        val sliderRow = JPanel()
        sliderRow.layout = BoxLayout(sliderRow, BoxLayout.X_AXIS)
        sliderRow.add(opacitySlider)
        sliderRow.add(Box.createHorizontalStrut(8))
        sliderRow.add(opacityLabel)
        addRow(form, 2, "不透明度", sliderRow)

        val hint = JLabel("设置会立即生效。")
        hint.foreground = java.awt.Color(0x77, 0x77, 0x77)

        val content = JPanel(BorderLayout(0, 12))
        content.add(title, BorderLayout.NORTH)
        content.add(form, BorderLayout.CENTER)
        content.add(hint, BorderLayout.SOUTH)
        root.add(content, BorderLayout.CENTER)

        val closeButton = JButton("关闭")
        closeButton.addActionListener { dispose() }
        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        bottom.add(closeButton)
        root.add(bottom, BorderLayout.SOUTH)

        contentPane = root
        syncFromConfig()
        installListeners()

        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        pack()
        setLocationRelativeTo(owner)
    }

    private fun addRow(form: JPanel, row: Int, label: String, comp: JComponent) {
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.LINE_START
            insets = Insets(4, 0, 4, 12)
        }
        form.add(JLabel(label), labelConstraints)

        val compConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 0, 4, 0)
        }
        form.add(comp, compConstraints)
    }

    private fun syncFromConfig() {
        sizeBox.selectedIndex = when (config.sizeKey) {
            "small" -> 0
            "medium" -> 1
            else -> 2
        }
        alwaysOnTopBox.isSelected = config.alwaysOnTop
        val percent = (config.opacity * 100).roundToInt().coerceIn(30, 100)
        opacitySlider.value = percent
        opacityLabel.text = "$percent%"
    }

    private fun installListeners() {
        sizeBox.addActionListener {
            config.sizeKey = when (sizeBox.selectedIndex) {
                0 -> "small"
                1 -> "medium"
                else -> "large"
            }
            apply()
        }
        alwaysOnTopBox.addItemListener {
            config.alwaysOnTop = alwaysOnTopBox.isSelected
            apply()
        }
        opacitySlider.addChangeListener {
            val percent = opacitySlider.value
            opacityLabel.text = "$percent%"
            config.opacity = percent / 100f
            onApply()
            if (!opacitySlider.valueIsAdjusting) config.save()
        }
    }

    private fun apply() {
        onApply()
        config.save()
    }
}
