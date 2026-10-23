package com.maoyou.typingpet

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerMatcherTest {

    @Test
    fun matchesKeyAtTheEndOfNewInput() {
        assertEquals(0, TriggerMatcher.find("abc!", 3, listOf(listOf("!"))))
    }

    @Test
    fun ignoresKeyThatEndedBeforeNewInput() {
        assertEquals(-1, TriggerMatcher.find("!a", 1, listOf(listOf("!"))))
    }

    @Test
    fun matchesKeySpanningTheBoundary() {
        assertEquals(0, TriggerMatcher.find("ab", 1, listOf(listOf("ab"))))
    }

    @Test
    fun prefersTheLastMatchedPosition() {
        assertEquals(1, TriggerMatcher.find("!a?", 2, listOf(listOf("!"), listOf("?"))))
    }

    @Test
    fun prefersTheLongerKeyAtTheSameEnd() {
        assertEquals(0, TriggerMatcher.find("ab", 0, listOf(listOf("ab"), listOf("b"))))
    }

    @Test
    fun keepsTheFirstItemWhenEndAndLengthTie() {
        assertEquals(0, TriggerMatcher.find("a", 0, listOf(listOf("a"), listOf("a"))))
    }

    @Test
    fun ignoresEmptyKeys() {
        assertEquals(1, TriggerMatcher.find("a", 0, listOf(listOf(""), listOf("a"))))
    }

    @Test
    fun ignoresItemsWithoutKeys() {
        assertEquals(1, TriggerMatcher.find("a", 0, listOf(emptyList(), listOf("a"))))
    }

    @Test
    fun returnsMinusOneWhenThereAreNoKeys() {
        assertEquals(-1, TriggerMatcher.find("a", 0, emptyList()))
    }

    @Test
    fun returnsMinusOneWhenNothingMatches() {
        assertEquals(-1, TriggerMatcher.find("abc", 2, listOf(listOf("!"))))
    }

    @Test
    fun matchesFullWidthKey() {
        assertEquals(0, TriggerMatcher.find("あ！", 1, listOf(listOf("！"))))
    }

    @Test
    fun matchesMultiCharacterKey() {
        assertEquals(0, TriggerMatcher.find("hello!", 5, listOf(listOf("!"))))
    }

    @Test
    fun findsOverlappingKeyWithinNewInput() {
        assertEquals(0, TriggerMatcher.find("aaa", 0, listOf(listOf("aa"))))
    }

    @Test
    fun matchesKeyStartingAtNewInput() {
        assertEquals(0, TriggerMatcher.find("abc", 0, listOf(listOf("abc"))))
    }

    @Test
    fun handlesSurrogatePairs() {
        assertEquals(0, TriggerMatcher.find("x😀", 1, listOf(listOf("😀"))))
    }

    @Test
    fun emptyWindowHasNoMatch() {
        assertEquals(-1, TriggerMatcher.find("", 0, listOf(listOf("!"))))
    }
}
