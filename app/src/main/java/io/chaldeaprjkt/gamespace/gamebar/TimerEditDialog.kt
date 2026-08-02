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

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.data.GameTimer
import io.chaldeaprjkt.gamespace.data.TimerMode

/** Material 3 editor hosted in an application-overlay Dialog window. */
class TimerEditDialog(
    context: Context,
    private val timer: GameTimer,
    private val onSave: (label: String, durationSeconds: Int, color: Int, mode: TimerMode) -> Unit,
) : Dialog(context) {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (320 * context.resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }

        val composeView = ComposeView(context).apply {
            setContent {
                TimerEditContent(
                    timer = timer,
                    onCancel = { dismiss() },
                    onSave = { label, duration, color, mode ->
                        onSave(label, duration, color, mode)
                        dismiss()
                    },
                )
            }
        }
        setContentView(composeView)
        setCanceledOnTouchOutside(true)
    }

    companion object {
        fun show(
            context: Context,
            timer: GameTimer,
            onSave: (String, Int, Int, TimerMode) -> Unit,
        ): TimerEditDialog {
            return TimerEditDialog(context, timer, onSave).also { it.show() }
        }
    }
}

@Composable
private fun TimerEditContent(
    timer: GameTimer,
    onCancel: () -> Unit,
    onSave: (String, Int, Int, TimerMode) -> Unit,
) {
    var label by remember { mutableStateOf(timer.label) }
    var duration by remember { mutableStateOf(timer.durationSeconds.toString()) }
    var color by remember { mutableIntStateOf(timer.color) }
    var mode by remember { mutableStateOf(timer.mode) }
    val colors = remember {
        listOf(
            0xFFFFFFFF.toInt(),
            0xFF64B5F6.toInt(),
            0xFF81C784.toInt(),
            0xFFFFD54F.toInt(),
            0xFFFF8A65.toInt(),
            0xFFE57373.toInt(),
        )
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit timer ${timer.id}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(20) },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { value ->
                    if (value.all(Char::isDigit)) duration = value.take(6)
                },
                label = { Text("Duration (seconds)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mode == TimerMode.COUNTDOWN) {
                    Button(onClick = { mode = TimerMode.COUNTDOWN }) { Text("Countdown") }
                } else {
                    OutlinedButton(onClick = { mode = TimerMode.COUNTDOWN }) { Text("Countdown") }
                }
                if (mode == TimerMode.COUNT_UP) {
                    Button(onClick = { mode = TimerMode.COUNT_UP }) { Text("Stopwatch") }
                } else {
                    OutlinedButton(onClick = { mode = TimerMode.COUNT_UP }) { Text("Stopwatch") }
                }
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                colors.forEach { candidate ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(candidate))
                            .then(
                                if (candidate == color) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        CircleShape,
                                    )
                                } else Modifier
                            )
                            .clickable { color = candidate },
                        contentAlignment = Alignment.Center,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    onSave(
                        label.trim().ifBlank { "Timer ${timer.id}" },
                        duration.toIntOrNull()?.coerceIn(1, 86_400) ?: timer.durationSeconds,
                        color,
                        mode,
                    )
                }) { Text("Save") }
            }
        }
    }
}
