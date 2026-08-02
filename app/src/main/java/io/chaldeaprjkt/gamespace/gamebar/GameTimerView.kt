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

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import io.chaldeaprjkt.gamespace.data.GameTimer
import io.chaldeaprjkt.gamespace.data.TimerMode
import io.chaldeaprjkt.gamespace.data.TimerState
import java.util.Locale
import kotlin.math.hypot

/** Canvas-rendered 80dp x 96dp timer with click, double-click, long-press and drag support. */
class GameTimerView(
    context: Context,
    timer: GameTimer,
    private val manager: GameTimerManager,
    private val onDrag: (GameTimerView, Float, Float) -> Unit,
    private val onEdit: (GameTimer) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var renderedTimer = timer.copy()
    val timerId: Int
        get() = renderedTimer.id

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var longPressTriggered = false
    private var clickPending = false
    private val touchSlopPx = 10f * density
    private var flashAnimator: ObjectAnimator? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80000000.toInt()
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val ringBounds = RectF()

    private val singleClick = Runnable {
        clickPending = false
        manager.startOrPause(renderedTimer.id)
    }

    private val longPress = Runnable {
        if (!dragging && !longPressTriggered) {
            longPressTriggered = true
            onEdit(renderedTimer.copy())
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
    }

    fun updateTimer(timer: GameTimer) {
        renderedTimer = timer.copy()
        if (timer.state == TimerState.FINISHED && timer.mode == TimerMode.COUNTDOWN) {
            startFinishedAnimation()
        } else {
            stopFinishedAnimation()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val radius = 12f * density
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, backgroundPaint)

        val centerX = width / 2f
        val centerY = 49f * density
        val ringRadius = 28f * density
        ringBounds.set(
            centerX - ringRadius,
            centerY - ringRadius,
            centerX + ringRadius,
            centerY + ringRadius,
        )

        ringPaint.color = renderedTimer.color
        ringPaint.alpha = 90
        canvas.drawArc(ringBounds, -90f, 360f, false, ringPaint)
        ringPaint.alpha = 255
        canvas.drawArc(ringBounds, -90f, 360f * progress(), false, ringPaint)

        val displayMs = renderedTimer.displayMs.coerceAtLeast(0L)
        textPaint.textSize = if (displayMs < 60_000L) 18f * density else 17f * density
        textPaint.color = if (displayMs < 60_000L || renderedTimer.state == TimerState.FINISHED) {
            0xFFFF5252.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
        val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(formatTime(displayMs), centerX, baseline, textPaint)

        val label = renderedTimer.label.take(MAX_LABEL_LENGTH)
        canvas.drawText(label, centerX, height - 10f * density, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                longPressTriggered = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDistance = hypot(
                    (event.rawX - downRawX).toDouble(),
                    (event.rawY - downRawY).toDouble(),
                )
                if (!dragging && totalDistance > touchSlopPx) {
                    dragging = true
                    handler.removeCallbacks(longPress)
                    handler.removeCallbacks(singleClick)
                    clickPending = false
                }
                if (dragging) {
                    onDrag(this, event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (dragging) {
                    dragging = false
                    return true
                }
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (clickPending) {
                        handler.removeCallbacks(singleClick)
                        clickPending = false
                        manager.resetAndStart(renderedTimer.id)
                    } else {
                        clickPending = true
                        handler.postDelayed(singleClick, DOUBLE_TAP_TIMEOUT_MS)
                    }
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        stopFinishedAnimation()
        super.onDetachedFromWindow()
    }

    private fun progress(): Float {
        val duration = renderedTimer.durationSeconds.coerceAtLeast(1) * 1_000L
        return when (renderedTimer.mode) {
            TimerMode.COUNTDOWN -> (renderedTimer.remainingMs.toFloat() / duration).coerceIn(0f, 1f)
            TimerMode.COUNT_UP -> (renderedTimer.elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
        }
    }

    private fun startFinishedAnimation() {
        if (flashAnimator?.isRunning == true) return
        flashAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.25f, 1f).apply {
            duration = 700L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopFinishedAnimation() {
        flashAnimator?.cancel()
        flashAnimator = null
        alpha = 1f
    }

    companion object {
        private const val LONG_PRESS_MS = 500L
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
        private const val MAX_LABEL_LENGTH = 12

        fun formatTime(milliseconds: Long): String {
            val millis = milliseconds.coerceAtLeast(0L)
            return if (millis < 60_000L) {
                val seconds = millis / 1_000L
                val tenths = (millis % 1_000L) / 100L
                String.format(Locale.US, "%02d.%d", seconds, tenths)
            } else {
                val totalSeconds = millis / 1_000L
                String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
            }
        }
    }
}
