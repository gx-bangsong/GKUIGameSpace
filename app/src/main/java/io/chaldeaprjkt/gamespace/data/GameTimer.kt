/*
 * Copyright (C) 2026 GameSpace contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.data

/** A timer can be a countdown (skill cooldown/BOSS refresh) or a stopwatch. */
enum class TimerMode {
    COUNTDOWN,
    COUNT_UP,
}

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
}

data class GameTimer(
    val id: Int,
    var label: String = "Timer $id",
    var durationSeconds: Int = DEFAULT_DURATION_SECONDS,
    var remainingMs: Long = DEFAULT_DURATION_SECONDS * 1_000L,
    var state: TimerState = TimerState.IDLE,
    var color: Int = DEFAULT_COLOR,
    var posX: Float = DEFAULT_X,
    var posY: Float = DEFAULT_Y,
    var isVisible: Boolean = true,
    var mode: TimerMode = TimerMode.COUNTDOWN,
    var elapsedMs: Long = 0L,
) {
    /** The value shown by the overlay for the current timer mode. */
    val displayMs: Long
        get() = if (mode == TimerMode.COUNTDOWN) remainingMs else elapsedMs

    companion object {
        const val MAX_TIMERS = 6
        const val DEFAULT_DURATION_SECONDS = 60
        const val DEFAULT_COLOR: Int = 0xFFFFFFFF.toInt()
        const val DEFAULT_X = 0.5f
        const val DEFAULT_Y = 0.25f
    }
}
