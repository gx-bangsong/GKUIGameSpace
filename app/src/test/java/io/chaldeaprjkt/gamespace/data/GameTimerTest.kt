package io.chaldeaprjkt.gamespace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameTimerTest {
    @Test
    fun countdownUsesRemainingMilliseconds() {
        val timer = GameTimer(
            id = 1,
            durationSeconds = 30,
            remainingMs = 12_345L,
            mode = TimerMode.COUNTDOWN,
            elapsedMs = 99L,
        )

        assertEquals(12_345L, timer.displayMs)
    }

    @Test
    fun stopwatchUsesElapsedMilliseconds() {
        val timer = GameTimer(
            id = 2,
            durationSeconds = 30,
            remainingMs = 1L,
            mode = TimerMode.COUNT_UP,
            elapsedMs = 12_345L,
        )

        assertEquals(12_345L, timer.displayMs)
    }
}
