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
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.chaldeaprjkt.gamespace.data.GameTimer
import io.chaldeaprjkt.gamespace.data.TimerMode
import io.chaldeaprjkt.gamespace.data.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the six timers and publishes immutable snapshots to overlay views.
 *
 * The ticker is deliberately based on elapsed realtime.  A 16 ms scheduling delay is only
 * a refresh cadence; it is never used as the elapsed time, so a busy system cannot make a
 * cooldown drift by several seconds.
 */
class GameTimerManager(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val tickerStarted = AtomicBoolean(false)
    private var tickerJob: Job? = null
    private var lastTick = SystemClock.elapsedRealtime()
    private var currentPackage: String? = null

    private val _timers = MutableStateFlow(defaultTimers())
    val timers: StateFlow<List<GameTimer>> = _timers.asStateFlow()

    private val _updates = MutableSharedFlow<List<GameTimer>>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<List<GameTimer>> = _updates.asSharedFlow()

    init {
        emitSnapshot()
        startTicker()
    }

    fun load(packageName: String) {
        if (packageName.isBlank()) return
        synchronized(lock) {
            if (currentPackage != packageName) {
                saveLocked()
                currentPackage = packageName
                _timers.value = readTimers(packageName)
                lastTick = SystemClock.elapsedRealtime()
                emitSnapshotLocked()
            }
        }
    }

    fun restore(packageName: String? = currentPackage) {
        packageName?.takeIf { it.isNotBlank() }?.let(::load)
    }

    fun save() {
        synchronized(lock) {
            saveLocked()
        }
    }

    fun startOrPause(id: Int) {
        updateTimer(id) { timer ->
            if (timer.state == TimerState.RUNNING) {
                timer.state = TimerState.PAUSED
            } else {
                if (timer.state == TimerState.FINISHED ||
                    (timer.mode == TimerMode.COUNTDOWN && timer.remainingMs <= 0L)
                ) {
                    resetValues(timer)
                }
                timer.state = TimerState.RUNNING
            }
        }
    }

    fun resetAndStart(id: Int) {
        updateTimer(id) { timer ->
            resetValues(timer)
            timer.state = TimerState.RUNNING
        }
    }

    fun reset(id: Int) {
        updateTimer(id) { timer ->
            resetValues(timer)
            timer.state = TimerState.IDLE
        }
    }

    fun updateConfiguration(
        id: Int,
        label: String,
        durationSeconds: Int,
        color: Int,
        mode: TimerMode,
    ) {
        updateTimer(id) { timer ->
            val normalizedDuration = durationSeconds.coerceIn(1, MAX_DURATION_SECONDS)
            val modeChanged = timer.mode != mode
            timer.label = label.trim().ifBlank { "Timer ${timer.id}" }
            timer.durationSeconds = normalizedDuration
            timer.color = color
            timer.mode = mode
            if (modeChanged || timer.state != TimerState.RUNNING) {
                resetValues(timer)
                timer.state = TimerState.IDLE
            }
        }
    }

    fun move(id: Int, xRatio: Float, yRatio: Float) {
        updateTimer(id) { timer ->
            timer.posX = xRatio.coerceIn(0f, 1f)
            timer.posY = yRatio.coerceIn(0f, 1f)
        }
    }

    fun setVisible(id: Int, visible: Boolean) {
        updateTimer(id) { it.isVisible = visible }
    }

    fun clearRuntimeState() {
        synchronized(lock) {
            _timers.value = _timers.value.map { timer ->
                timer.copy(
                    remainingMs = timer.durationSeconds.coerceAtLeast(1) * 1_000L,
                    elapsedMs = 0L,
                    state = TimerState.IDLE,
                )
            }
            emitSnapshotLocked()
        }
    }

    fun hasRunningOrPausedTimers(): Boolean =
        _timers.value.any { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }

    fun close() {
        save()
        tickerJob?.cancel()
        scope.cancel()
    }

    private fun updateTimer(id: Int, mutation: (GameTimer) -> Unit) {
        synchronized(lock) {
            val updated = _timers.value.map { timer ->
                if (timer.id == id) timer.copy().also(mutation) else timer
            }
            if (updated.any { it.id == id }) {
                _timers.value = updated
                emitSnapshotLocked()
            }
        }
    }

    private fun startTicker() {
        if (!tickerStarted.compareAndSet(false, true)) return
        tickerJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        if (delta == 0L) return

        synchronized(lock) {
            var changed = false
            val updated = _timers.value.map { timer ->
                if (timer.state != TimerState.RUNNING) return@map timer
                changed = true
                timer.copy().also { running ->
                    when (running.mode) {
                        TimerMode.COUNTDOWN -> {
                            running.remainingMs = (running.remainingMs - delta).coerceAtLeast(0L)
                            if (running.remainingMs == 0L) {
                                running.state = TimerState.FINISHED
                            }
                        }
                        TimerMode.COUNT_UP -> {
                            running.elapsedMs += delta
                        }
                    }
                }
            }
            if (changed) {
                _timers.value = updated
                emitSnapshotLocked()
            }
        }
    }

    private fun resetValues(timer: GameTimer) {
        timer.remainingMs = timer.durationSeconds.coerceIn(1, MAX_DURATION_SECONDS) * 1_000L
        timer.elapsedMs = 0L
    }

    private fun saveLocked() {
        val packageName = currentPackage ?: return
        runCatching {
            preferences.edit()
                .putString(keyFor(packageName), gson.toJson(_timers.value))
                .apply()
        }
    }

    private fun readTimers(packageName: String): List<GameTimer> {
        val json = preferences.getString(keyFor(packageName), null)
        val type = object : TypeToken<List<GameTimer>>() {}.type
        val saved = runCatching { json?.let { gson.fromJson<List<GameTimer>>(it, type) } }
            .getOrNull()
            .orEmpty()
            .associateBy { it.id }

        return (1..GameTimer.MAX_TIMERS).map { id ->
            val timer = saved[id]?.copy() ?: GameTimer(id = id, isVisible = id == 1)
            timer.durationSeconds = timer.durationSeconds.coerceIn(1, MAX_DURATION_SECONDS)
            timer.remainingMs = timer.remainingMs.coerceIn(0L, timer.durationSeconds * 1_000L)
            timer.elapsedMs = timer.elapsedMs.coerceAtLeast(0L)
            timer.posX = timer.posX.coerceIn(0f, 1f)
            timer.posY = timer.posY.coerceIn(0f, 1f)
            timer
        }
    }

    private fun emitSnapshot() {
        synchronized(lock) {
            emitSnapshotLocked()
        }
    }

    private fun emitSnapshotLocked() {
        val snapshot = _timers.value.map { it.copy() }
        _updates.tryEmit(snapshot)
    }

    private fun defaultTimers(): List<GameTimer> =
        (1..GameTimer.MAX_TIMERS).map { GameTimer(id = it, isVisible = it == 1) }

    private fun keyFor(packageName: String) = "$KEY_PREFIX$packageName"

    companion object {
        private const val PREFERENCES = "game_timer_state"
        private const val KEY_PREFIX = "timers_"
        private const val TICK_MS = 16L
        private const val MAX_DURATION_SECONDS = 24 * 60 * 60
    }
}
