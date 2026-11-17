package com.maoyou.typingpet.desktop

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/** グローバルフックが使えないときの案内カード。 */
class HookPanel(
    reason: String?,
    onAction: (KeyAction) -> Unit,
    onClose: () -> Unit,
) : RoundedPanel(14, AppleUI.windowBg) {

    val testField = AppleTextField(24)

    init {
        border = BorderFactory.createEmptyBorder(22, 22, 18, 22)
        layout = BorderLayout(0, 16)

        val box = JPanel()
        box.isOpaque = false
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)

        val heading = JLabel("无法捕获全局按键")
        heading.font = AppleUI.font(17, bold = true)
        heading.foreground = AppleUI.text
        heading.alignmentX = Component.LEFT_ALIGNMENT

        val reasonText = reason?.takeIf { it.isNotBlank() } ?: "未知原因"
        val reasonArea = JLabel(
            "<html><div style='width:400px'>${escapeHtml(reasonText)}</div></html>",
        )
        reasonArea.font = AppleUI.font(12)
        reasonArea.foreground = AppleUI.secondary
        reasonArea.alignmentX = Component.LEFT_ALIGNMENT
        reasonArea.maximumSize = Dimension(Int.MAX_VALUE, reasonArea.preferredSize.height)

        val hint = JLabel(
            "<html>Linux 提示：全局按键需要 X11 会话。Wayland 会阻止应用监听系统按键；" +
                "<br>请在登录界面选择 “X11” 会话后重新启动猫鱿。</html>",
        )
        hint.font = AppleUI.font(12)
        hint.foreground = AppleUI.warning
        hint.alignmentX = Component.LEFT_ALIGNMENT

        val testLabel = JLabel("在下面的输入框里打字，宠物仍会作出反应：")
        testLabel.font = AppleUI.font(12)
        testLabel.foreground = AppleUI.secondary
        testLabel.alignmentX = Component.LEFT_ALIGNMENT

        testField.alignmentX = Component.LEFT_ALIGNMENT
        testField.maximumSize = Dimension(Int.MAX_VALUE, testField.preferredSize.height)
        testField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val action = KeyClassifier.fromAwt(e.keyCode, e.keyChar)
                if (action == KeyAction.Delete) onAction(action)
            }

            override fun keyTyped(e: KeyEvent) {
                val char = e.keyChar
                if (char >= ' ' && char != '\u007F') onAction(KeyClassifier.classify(char))
            }
        })

        box.add(heading)
        box.add(Box.createVerticalStrut(8))
        box.add(reasonArea)
        box.add(Box.createVerticalStrut(10))
        box.add(hint)
        box.add(Box.createVerticalStrut(16))
        box.add(testLabel)
        box.add(Box.createVerticalStrut(6))
        box.add(testField)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        buttons.isOpaque = false
        buttons.add(AppleButton("关闭", primary = true, onClick = onClose))

        add(box, BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
        preferredSize = Dimension(460, preferredSize.height)
    }
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
