package com.maoyou.typingpet.desktop

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyClassifierTest {
    @Test
    fun regularCharacterIsType() {
        assertEquals(KeyAction.Type, KeyClassifier.classify('a'))
    }

    @Test
    fun spaceAndIdeographicSpaceAreSpace() {
        assertEquals(KeyAction.Space, KeyClassifier.classify(' '))
        assertEquals(KeyAction.Space, KeyClassifier.classify('\u3000'))
    }

    @Test
    fun exclamationMarksAreExclamation() {
        assertEquals(KeyAction.Exclamation, KeyClassifier.classify('!'))
        assertEquals(KeyAction.Exclamation, KeyClassifier.classify('！'))
    }

    @Test
    fun newlineIsType() {
        assertEquals(KeyAction.Type, KeyClassifier.classify('\n'))
    }

    @Test
    fun nullCharacterIsIgnore() {
        assertEquals(KeyAction.Ignore, KeyClassifier.classify(null))
    }

    @Test
    fun backspaceAndDeleteFlagsAreDelete() {
        assertEquals(KeyAction.Delete, KeyClassifier.classify('a', backspace = true))
        assertEquals(KeyAction.Delete, KeyClassifier.classify('a', delete = true))
    }

    @Test
    fun modifierFlagWinsOverCharacterAndBackspace() {
        assertEquals(KeyAction.Ignore, KeyClassifier.classify('a', modifier = true))
        assertEquals(KeyAction.Ignore, KeyClassifier.classify(' ', backspace = true, modifier = true))
    }

    @Test
    fun fromAwtRegularKeyIsType() {
        assertEquals(KeyAction.Type, KeyClassifier.fromAwt(KeyEvent.VK_A, 'a'))
    }

    @Test
    fun fromAwtSpaceIsSpace() {
        assertEquals(KeyAction.Space, KeyClassifier.fromAwt(KeyEvent.VK_SPACE, ' '))
    }

    @Test
    fun fromAwtBackspaceIsDelete() {
        assertEquals(KeyAction.Delete, KeyClassifier.fromAwt(KeyEvent.VK_BACK_SPACE, '\b'))
    }

    @Test
    fun fromAwtDeleteIsDelete() {
        assertEquals(KeyAction.Delete, KeyClassifier.fromAwt(KeyEvent.VK_DELETE, null))
    }

    @Test
    fun fromAwtShiftIsIgnore() {
        assertEquals(KeyAction.Ignore, KeyClassifier.fromAwt(KeyEvent.VK_SHIFT, null))
    }
}
