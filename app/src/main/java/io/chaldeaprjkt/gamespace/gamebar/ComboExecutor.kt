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
package io.chaldeaprjkt.gamespace.gamebar

import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.MotionEvent
import io.chaldeaprjkt.gamespace.data.ActionType
import io.chaldeaprjkt.gamespace.data.ComboSequence
import io.chaldeaprjkt.gamespace.data.LoopMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Replays recorded actions using the platform InputManager injection API. */
class ComboExecutor(
    private val inputManager: InputManager,
    private val displayMetrics: DisplayMetrics,
) {
    private var comboJob: Job? = null

    @Synchronized
    fun execute(combo: ComboSequence, scope: CoroutineScope) {
        comboJob?.cancel()
        comboJob = scope.launch(Dispatchers.Default) {
            if (combo.actions.isEmpty()) return@launch
            val repeatCount = when (combo.loopMode) {
                LoopMode.ONCE -> 1
                LoopMode.COUNT -> combo.loopCount.coerceAtLeast(1)
                LoopMode.INFINITE -> Int.MAX_VALUE
            }

            var iteration = 0
            while (isActive && iteration < repeatCount) {
                for (action in combo.actions) {
                    ensureActive()
                    delay(action.delayBeforeMs.coerceAtLeast(0L))
                    executeAction(action)
                }
                iteration++
            }
        }
    }

    fun stop() {
        comboJob?.cancel()
        comboJob = null
    }

    val isRunning: Boolean
        get() = comboJob?.isActive == true

    private suspend fun executeAction(action: io.chaldeaprjkt.gamespace.data.TouchAction) {
        val width = displayMetrics.widthPixels.coerceAtLeast(1)
        val height = displayMetrics.heightPixels.coerceAtLeast(1)
        val x = (action.xRatio.coerceIn(0f, 1f) * width).coerceIn(0f, width.toFloat())
        val y = (action.yRatio.coerceIn(0f, 1f) * height).coerceIn(0f, height.toFloat())
        when (action.type) {
            ActionType.TAP -> press(x, y, TAP_DURATION_MS)
            ActionType.LONG_PRESS -> press(x, y, action.durationMs.coerceAtLeast(1L))
            ActionType.SWIPE -> swipe(
                x,
                y,
                (action.x2Ratio.coerceIn(0f, 1f) * width).coerceIn(0f, width.toFloat()),
                (action.y2Ratio.coerceIn(0f, 1f) * height).coerceIn(0f, height.toFloat()),
                action.durationMs.coerceAtLeast(1L),
            )
        }
    }

    private suspend fun press(x: Float, y: Float, holdMs: Long) {
        val downTime = SystemClock.uptimeMillis()
        var released = false
        inject(MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
        try {
            delay(holdMs)
            val upTime = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_UP, x, y, downTime, upTime)
            released = true
        } finally {
            if (!released) {
                inject(MotionEvent.ACTION_UP, x, y, downTime, SystemClock.uptimeMillis())
            }
        }
    }

    private suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ) {
        val downTime = SystemClock.uptimeMillis()
        var released = false
        inject(MotionEvent.ACTION_DOWN, startX, startY, downTime, downTime)
        try {
            val steps = max(1, (durationMs / FRAME_MS).toInt())
            val startedAt = SystemClock.uptimeMillis()
            for (step in 1..steps) {
                coroutineContext.ensureActive()
                val fraction = step.toFloat() / steps.toFloat()
                val targetElapsed = (durationMs * step / steps)
                val wait = targetElapsed - (SystemClock.uptimeMillis() - startedAt)
                if (wait > 0) delay(wait)
                inject(
                    MotionEvent.ACTION_MOVE,
                    startX + (endX - startX) * fraction,
                    startY + (endY - startY) * fraction,
                    downTime,
                    SystemClock.uptimeMillis(),
                )
            }
            inject(MotionEvent.ACTION_UP, endX, endY, downTime, SystemClock.uptimeMillis())
            released = true
        } finally {
            if (!released) {
                inject(MotionEvent.ACTION_UP, endX, endY, downTime, SystemClock.uptimeMillis())
            }
        }
    }

    private fun inject(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            eventTime.coerceAtLeast(downTime),
            action,
            x,
            y,
            1f,
            1f,
            0,
            1f,
            1f,
            0,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            inputManager.injectInputEvent(
                event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC,
            )
        } finally {
            event.recycle()
        }
    }

    companion object {
        private const val TAP_DURATION_MS = 50L
        private const val FRAME_MS = 16L
    }
}
