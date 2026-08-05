package io.chaldeaprjkt.gamespace.gamebar

import org.junit.Assert.assertEquals
import org.junit.Test

class GameTimerViewTest {
    @Test
    fun compactReferenceFormatUsesMinutesAndSecondsBelowOneMinute() {
        assertEquals("00:03", GameTimerView.formatCompactTime(3_000L))
        assertEquals("00:00", GameTimerView.formatCompactTime(0L))
        assertEquals("01:05", GameTimerView.formatCompactTime(65_000L))
    }

    @Test
    fun legacyCardFormatKeepsTenthsForTheLargeCardRenderer() {
        assertEquals("03.0", GameTimerView.formatCardTime(3_000L))
        assertEquals("01:05", GameTimerView.formatCardTime(65_000L))
    }
}
