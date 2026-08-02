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
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.MotionEvent
import android.view.WindowManager
import io.chaldeaprjkt.gamespace.data.ActionType
import io.chaldeaprjkt.gamespace.data.ComboSequence
import io.chaldeaprjkt.gamespace.data.LoopMode
import io.chaldeaprjkt.gamespace.data.TouchAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.hypot

/**
 * Records touch events through an InputMonitor instead of a touchable full-screen overlay.
 * A touchable overlay would become the InputDispatcher target and would not reliably pass
 * events through to the game when its listener returns false.
 */
class ComboRecorder(
    private val inputManager: InputManager,
    private val windowManager: WindowManager,
) {
    private var monitor: InputMonitor? = null
    private var receiver: InputEventReceiver? = null
    private var recordingPackage: String? = null
    private var lastRecordedPackage: String? = null
    private var recording = false
    private var gestureActive = false
    private var multiTouchGesture = false
    private var downTime = 0L
    private var lastActionEndTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var gestureDisplayWidth = 1
    private var gestureDisplayHeight = 1
    private var armedAtUptime = 0L

    private val recordedActions = mutableListOf<TouchAction>()
    private val _actions = MutableStateFlow<List<TouchAction>>(emptyList())
    val actions: StateFlow<List<TouchAction>> = _actions.asStateFlow()

    val isRecording: Boolean
        get() = recording

    fun start(packageName: String): Boolean {
        if (recording) return false
        recordingPackage = packageName.takeIf(String::isNotBlank) ?: return false
        val bounds = windowManager.maximumWindowMetrics.bounds
        gestureDisplayWidth = bounds.width().coerceAtLeast(1)
        gestureDisplayHeight = bounds.height().coerceAtLeast(1)
        recordedActions.clear()
        _actions.value = emptyList()
        gestureActive = false
        multiTouchGesture = false
        lastActionEndTime = 0L
        armedAtUptime = SystemClock.uptimeMillis() + ARM_DELAY_MS

        val newMonitor = runCatching {
            inputManager.monitorGestureInput(MONITOR_NAME, Display.DEFAULT_DISPLAY)
        }.getOrNull() ?: run {
            recordingPackage = null
            return false
        }

        monitor = newMonitor
        receiver = object : InputEventReceiver(newMonitor.inputChannel, Looper.getMainLooper()) {
            override fun onInputEvent(event: InputEvent) {
                try {
                    if (recording && SystemClock.uptimeMillis() >= armedAtUptime &&
                        event is MotionEvent &&
                        event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
                    ) {
                        record(event)
                    }
                } finally {
                    finishInputEvent(event, false)
                }
            }
        }
        recording = true
        return true
    }

    fun stop(): List<TouchAction> {
        recording = false
        finishGesture()
        receiver?.dispose()
        receiver = null
        monitor?.dispose()
        monitor = null
        lastRecordedPackage = recordingPackage
        recordingPackage = null
        return recordedActions.toList()
    }

    fun cancel() {
        stop()
        recordedActions.clear()
        _actions.value = emptyList()
    }

    fun buildSequence(
        name: String,
        loopMode: LoopMode = LoopMode.ONCE,
        loopCount: Int = 1,
    ): ComboSequence? {
        val packageName = recordingPackage ?: lastRecordedPackage ?: return null
        return ComboSequence(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Combo" },
            gamePackage = packageName,
            actions = actions.value,
            loopMode = loopMode,
            loopCount = loopCount.coerceAtLeast(1),
        )
    }

    private fun record(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (gestureActive) finishGesture()
                gestureActive = true
                multiTouchGesture = event.pointerCount > 1
                downTime = event.eventTime
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gestureActive) multiTouchGesture = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureActive && !multiTouchGesture) {
                    endX = event.x
                    endY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gestureActive) multiTouchGesture = true
            }
            MotionEvent.ACTION_UP -> {
                if (gestureActive) {
                    endX = event.x
                    endY = event.y
                    finishGesture(event.eventTime)
                }
            }
            MotionEvent.ACTION_CANCEL -> finishGesture()
        }
    }

    private fun finishGesture(eventTime: Long? = null) {
        if (!gestureActive) return
        val upTime = eventTime ?: downTime
        if (!multiTouchGesture) {
            val duration = (upTime - downTime).coerceAtLeast(0L)
            val distance = hypot((endX - startX).toDouble(), (endY - startY).toDouble())
            val type = when {
                distance >= SWIPE_DISTANCE_PX -> ActionType.SWIPE
                duration >= LONG_PRESS_MS -> ActionType.LONG_PRESS
                else -> ActionType.TAP
            }
            val delay = if (lastActionEndTime == 0L) {
                0L
            } else {
                (downTime - lastActionEndTime).coerceAtLeast(0L)
            }
            val action = TouchAction(
                type = type,
                xRatio = ratioX(startX),
                yRatio = ratioY(startY),
                x2Ratio = ratioX(endX),
                y2Ratio = ratioY(endY),
                durationMs = when (type) {
                    ActionType.TAP -> TAP_DURATION_MS
                    else -> duration
                },
                delayBeforeMs = delay,
            )
            recordedActions += action
            _actions.value = recordedActions.toList()
            lastActionEndTime = upTime
        }
        gestureActive = false
        multiTouchGesture = false
    }

    private fun ratioX(value: Float): Float =
        (value / gestureDisplayWidth.toFloat()).coerceIn(0f, 1f)

    private fun ratioY(value: Float): Float =
        (value / gestureDisplayHeight.toFloat()).coerceIn(0f, 1f)

    companion object {
        private const val MONITOR_NAME = "GameSpaceComboRecorder"
        private const val LONG_PRESS_MS = 500L
        private const val TAP_DURATION_MS = 50L
        private const val SWIPE_DISTANCE_PX = 10f
        private const val ARM_DELAY_MS = 150L
    }
}
