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

import android.app.AlertDialog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import io.chaldeaprjkt.gamespace.data.GameTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Adds/removes up to six timer overlay windows and connects them to GameTimerManager. */
class GameTimerModule(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
    val manager: GameTimerManager = GameTimerManager(context),
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val views = LinkedHashMap<Int, GameTimerView>()
    private val params = LinkedHashMap<Int, WindowManager.LayoutParams>()
    private var packageName: String? = null
    private var windowVisible = false

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    init {
        scope.launch {
            manager.updates.collect { timers ->
                timers.forEach { timer -> updateOrRemove(timer) }
            }
        }
    }

    fun show(packageName: String) {
        this.packageName = packageName
        manager.load(packageName)
        windowVisible = true
        _isVisible.value = true
        handler.post { syncViews(manager.timers.value) }
    }

    fun restore(packageName: String? = this.packageName) {
        packageName?.let {
            this.packageName = it
            manager.restore(it)
            if (windowVisible) handler.post { syncViews(manager.timers.value) }
        }
    }

    fun hide() {
        windowVisible = false
        _isVisible.value = false
        handler.post { removeAllViews() }
    }

    fun toggle() {
        if (windowVisible) hide() else packageName?.let(::show)
    }

    fun save() {
        manager.save()
    }

    fun onGameExit() {
        val shouldAsk = manager.hasRunningOrPausedTimers()
        manager.save()
        hide()
        if (!shouldAsk) return

        handler.post {
            val dialog = AlertDialog.Builder(context)
                .setTitle("Keep timer state?")
                .setMessage("Save the current timer values for the next session?")
                .setPositiveButton("Keep") { _, _ -> manager.save() }
                .setNegativeButton("Reset") { _, _ ->
                    manager.clearRuntimeState()
                    manager.save()
                }
                .create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            runCatching { dialog.show() }
        }
    }

    fun onConfigurationChanged() {
        handler.post { syncViews(manager.timers.value) }
    }

    fun close() {
        handler.post { removeAllViews() }
        manager.close()
        scope.cancel()
    }

    private fun syncViews(timers: List<GameTimer>) {
        if (!windowVisible) return
        timers.forEach { timer -> updateOrRemove(timer) }
    }

    private fun updateOrRemove(timer: GameTimer) {
        if (!windowVisible || !timer.isVisible) {
            removeView(timer.id)
            return
        }

        val view = views[timer.id]
        if (view == null) {
            addView(timer)
        } else {
            view.updateTimer(timer)
            params[timer.id]?.let { lp ->
                val corrected = positionFor(timer, view)
                if (lp.x != corrected.x || lp.y != corrected.y) {
                    lp.x = corrected.x
                    lp.y = corrected.y
                    runCatching { windowManager.updateViewLayout(view, lp) }
                }
            }
        }
    }

    private fun addView(timer: GameTimer) {
        if (views.containsKey(timer.id)) return
        val view = GameTimerView(
            context = context,
            timer = timer,
            manager = manager,
            onDrag = ::onDrag,
            onEdit = { editTimer(it) },
        )
        val lp = WindowManager.LayoutParams(
            dp(80),
            dp(96),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            preferMinimalPostProcessing = true
        }
        val position = positionFor(timer, view)
        lp.x = position.x
        lp.y = position.y
        runCatching {
            windowManager.addView(view, lp)
            views[timer.id] = view
            params[timer.id] = lp
        }
    }

    private fun removeView(id: Int) {
        val view = views.remove(id) ?: return
        params.remove(id)
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun removeAllViews() {
        views.values.toList().forEach { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        views.clear()
        params.clear()
    }

    private fun onDrag(view: GameTimerView, dx: Float, dy: Float) {
        val lp = params[view.timerId] ?: return
        val bounds = windowManager.maximumWindowMetrics.bounds
        val maxX = (bounds.width() - view.width).coerceAtLeast(0)
        val maxY = (bounds.height() - view.height).coerceAtLeast(0)
        lp.x = (lp.x + dx.roundToInt()).coerceIn(0, maxX)
        lp.y = (lp.y + dy.roundToInt()).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(view, lp) }
        manager.move(
            view.timerId,
            if (maxX == 0) 0f else lp.x.toFloat() / maxX,
            if (maxY == 0) 0f else lp.y.toFloat() / maxY,
        )
    }

    private fun editTimer(timer: GameTimer) {
        TimerEditDialog.show(context, timer) { label, duration, color, mode ->
            manager.updateConfiguration(timer.id, label, duration, color, mode)
        }
    }

    private fun positionFor(timer: GameTimer, view: View): Position {
        val bounds = windowManager.maximumWindowMetrics.bounds
        val width = if (view.width > 0) view.width else dp(80)
        val height = if (view.height > 0) view.height else dp(96)
        val maxX = (bounds.width() - width).coerceAtLeast(0)
        val maxY = (bounds.height() - height).coerceAtLeast(0)
        return Position(
            (timer.posX.coerceIn(0f, 1f) * maxX).roundToInt(),
            (timer.posY.coerceIn(0f, 1f) * maxY).roundToInt(),
        )
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private data class Position(val x: Int, val y: Int)
}
