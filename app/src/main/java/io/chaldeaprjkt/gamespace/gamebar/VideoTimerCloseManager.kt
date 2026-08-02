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

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Schedules pause + HOME without leaving an uncancellable Handler callback behind. */
class VideoTimerCloseManager(
    private val context: Context,
    private val inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager,
    private val onTimeout: (() -> Unit)? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closeRunnable: Runnable? = null
    private var closeScreen = false

    private val _closeAt = MutableStateFlow<Long?>(null)
    val closeAt: StateFlow<Long?> = _closeAt.asStateFlow()

    fun scheduleClose(minutes: Int, closeScreen: Boolean = false) {
        cancel()
        if (minutes <= 0) return
        this.closeScreen = closeScreen
        val closeAt = SystemClock.elapsedRealtime() + minutes.toLong() * 60_000L
        _closeAt.value = closeAt
        closeRunnable = Runnable {
            closeRunnable = null
            _closeAt.value = null
            injectMediaPause()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivityAsUser(homeIntent, UserHandle.CURRENT) }
            if (this.closeScreen) onTimeout?.invoke()
        }.also { handler.postDelayed(it, minutes.toLong() * 60_000L) }
    }

    fun cancel() {
        closeRunnable?.let(handler::removeCallbacks)
        closeRunnable = null
        _closeAt.value = null
        closeScreen = false
    }

    fun close() {
        cancel()
    }

    private fun injectMediaPause() {
        val downTime = SystemClock.uptimeMillis()
        injectKey(KeyEvent.ACTION_DOWN, downTime, downTime)
        injectKey(KeyEvent.ACTION_UP, downTime, SystemClock.uptimeMillis())
    }

    private fun injectKey(action: Int, downTime: Long, eventTime: Long) {
        val event = KeyEvent.obtain(
            downTime,
            eventTime,
            action,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            0,
            0,
            -1,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD,
        )
        try {
            inputManager.injectInputEvent(
                event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC,
            )
        } finally {
            event.recycle()
        }
    }
}
