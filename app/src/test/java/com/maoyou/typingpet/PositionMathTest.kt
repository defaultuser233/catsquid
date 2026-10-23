package com.maoyou.typingpet

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionMathTest {

    @Test
    fun anchorMapsToPixel() {
        assertEquals(0, PositionMath.pxForAnchor(0.0, 100))
        assertEquals(50, PositionMath.pxForAnchor(0.5, 100))
        assertEquals(100, PositionMath.pxForAnchor(1.0, 100))
    }

    @Test
    fun anchorIsClamped() {
        assertEquals(0, PositionMath.pxForAnchor(-1.0, 100))
        assertEquals(100, PositionMath.pxForAnchor(2.0, 100))
    }

    @Test
    fun pixelMapsToAnchor() {
        assertEquals(0.0, PositionMath.anchorForPx(0, 100), 0.0)
        assertEquals(0.5, PositionMath.anchorForPx(50, 100), 0.0)
        assertEquals(1.0, PositionMath.anchorForPx(100, 100), 0.0)
    }

    @Test
    fun pixelIsClamped() {
        assertEquals(0.0, PositionMath.anchorForPx(-10, 100), 0.0)
        assertEquals(1.0, PositionMath.anchorForPx(200, 100), 0.0)
    }

    @Test
    fun zeroMaximumKeepsEverythingAtZero() {
        assertEquals(0, PositionMath.pxForAnchor(0.7, 0))
        assertEquals(0.0, PositionMath.anchorForPx(70, 0), 0.0)
    }

    @Test
    fun nonFiniteAnchorsAreHandled() {
        assertEquals(0, PositionMath.pxForAnchor(Double.NaN, 100))
        assertEquals(100, PositionMath.pxForAnchor(Double.POSITIVE_INFINITY, 100))
        assertEquals(0, PositionMath.pxForAnchor(Double.NEGATIVE_INFINITY, 100))
    }

    @Test
    fun anchorAndPixelRoundTrip() {
        val max = 101
        for (px in 0..max) {
            assertEquals(px, PositionMath.pxForAnchor(PositionMath.anchorForPx(px, max), max))
        }
    }
}
