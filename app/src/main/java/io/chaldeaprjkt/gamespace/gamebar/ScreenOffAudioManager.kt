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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the CPU awake while asking the system to turn the display off. */
class ScreenOffAudioManager(
    private val context: Context,
    private val inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "GameSpace:VideoAudio",
    )
    private var receiverRegistered = false

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) disable()
        }
    }

    @Synchronized
    fun enable(): Boolean {
        if (_enabled.value) return true
        runCatching {
            wakeLock.acquire(MAX_WAKE_LOCK_MS)
            registerScreenReceiver()
            injectPowerKey()
            _enabled.value = true
        }.onFailure {
            unregisterScreenReceiver()
            if (wakeLock.isHeld) wakeLock.release()
            _enabled.value = false
        }
        return _enabled.value
    }

    @Synchronized
    fun disable() {
        unregisterScreenReceiver()
        if (wakeLock.isHeld) wakeLock.release()
        _enabled.value = false
    }

    fun toggle(): Boolean {
        if (_enabled.value) disable() else enable()
        return _enabled.value
    }

    fun close() {
        disable()
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        context.registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            Context.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(screenReceiver) }
        receiverRegistered = false
    }

    private fun injectPowerKey() {
        val downTime = SystemClock.uptimeMillis()
        injectKey(KeyEvent.ACTION_DOWN, downTime, downTime)
        injectKey(KeyEvent.ACTION_UP, downTime, SystemClock.uptimeMillis())
    }

    private fun injectKey(action: Int, downTime: Long, eventTime: Long) {
        val event = KeyEvent.obtain(
            downTime,
            eventTime,
            action,
            KeyEvent.KEYCODE_POWER,
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

    companion object {
        private const val MAX_WAKE_LOCK_MS = 30 * 60 * 1_000L
    }
}
