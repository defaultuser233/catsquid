package com.maoyou.typingpet.desktop

import java.awt.event.KeyEvent

/** キー入力を猫鱈の反応に変換する */
object KeyClassifier {
    fun classify(
        char: Char?,
        backspace: Boolean = false,
        delete: Boolean = false,
        modifier: Boolean = false,
    ): KeyAction {
        if (modifier) return KeyAction.Ignore
        if (backspace || delete) return KeyAction.Delete
        return when (char) {
            ' ', '\u3000' -> KeyAction.Space
            '!', '！' -> KeyAction.Exclamation
            null -> KeyAction.Ignore
            else -> KeyAction.Type
        }
    }

    fun fromAwt(keyCode: Int, char: Char?): KeyAction = classify(
        char = char,
        backspace = keyCode == KeyEvent.VK_BACK_SPACE,
        delete = keyCode == KeyEvent.VK_DELETE,
        modifier = keyCode in MODIFIER_KEYS,
    )

    private val MODIFIER_KEYS = setOf(
        KeyEvent.VK_SHIFT,
        KeyEvent.VK_CONTROL,
        KeyEvent.VK_ALT,
        KeyEvent.VK_ALT_GRAPH,
        KeyEvent.VK_META,
        KeyEvent.VK_CAPS_LOCK,
        KeyEvent.VK_WINDOWS,
    )
}
