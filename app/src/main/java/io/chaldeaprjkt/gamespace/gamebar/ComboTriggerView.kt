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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/** Edge trigger button for starting/stopping and cycling saved combos. */
class ComboTriggerView(
    context: Context,
    private val onClick: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC202124.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF64B5F6.toInt()
    }
    private val ringBounds = RectF()
    private var label = "CB"
    private var running = false
    private var recording = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var spinAnimator: ObjectAnimator? = null

    private val longPress = Runnable {
        if (!moved) onLongPress()
    }

    init {
        isClickable = true
        setWillNotDraw(false)
    }

    fun setComboName(name: String?) {
        label = name?.trim()?.take(3)?.ifBlank { "CB" } ?: "CB"
        invalidate()
    }

    fun setRunning(value: Boolean) {
        running = value
        if (running) {
            if (spinAnimator == null) {
                spinAnimator = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
                    duration = 1_200L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        } else {
            spinAnimator?.cancel()
            spinAnimator = null
            rotation = 0f
        }
        invalidate()
    }

    fun setRecording(value: Boolean) {
        recording = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = (width.coerceAtMost(height) / 2f) - 2f * density
        canvas.drawCircle(width / 2f, height / 2f, radius, backgroundPaint)
        ringPaint.color = when {
            recording -> 0xFFFF5252.toInt()
            running -> 0xFF64B5F6.toInt()
            else -> 0x99FFFFFF.toInt()
        }
        ringBounds.set(
            width / 2f - radius + density,
            height / 2f - radius + density,
            width / 2f + radius - density,
            height / 2f + radius - density,
        )
        canvas.drawArc(ringBounds, -90f, if (running) 300f else 360f, false, ringPaint)
        canvas.drawText(
            if (recording) "REC" else label,
            width / 2f,
            height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                moved = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val total = kotlin.math.hypot(
                    (event.rawX - downX).toDouble(),
                    (event.rawY - downY).toDouble(),
                )
                if (!moved && total > TOUCH_SLOP_DP * density) {
                    moved = true
                    handler.removeCallbacks(longPress)
                }
                if (moved) {
                    onDrag(event.rawX - lastX, event.rawY - lastY)
                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                    onClick()
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
        spinAnimator?.cancel()
        spinAnimator = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LONG_PRESS_MS = 600L
        private const val TOUCH_SLOP_DP = 10f
    }
}
