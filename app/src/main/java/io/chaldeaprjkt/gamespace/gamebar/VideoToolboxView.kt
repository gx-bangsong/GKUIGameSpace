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

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.data.VideoOrientationLock
import io.chaldeaprjkt.gamespace.data.VideoQualityPreset
import io.chaldeaprjkt.gamespace.data.VideoToolboxState
import kotlinx.coroutines.delay

@Composable
fun VideoToolboxView(
    state: VideoToolboxState,
    onToggleScreenOffAudio: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onScheduleClose: (Int) -> Unit,
    onCancelClose: () -> Unit,
    onCast: () -> Unit,
    onCycleQuality: () -> Unit,
    onDanmaku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.closeAtElapsedRealtime) {
        while (state.closeAtElapsedRealtime != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    Surface(
        modifier = modifier.width(220.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) },
            label = "video_toolbox_content",
        ) { contentState ->
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Video toolbox", style = MaterialTheme.typography.titleMedium)
                Text(
                    contentState.currentPackage ?: "Video mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeatureButton(
                        text = if (contentState.screenOffAudioEnabled) "Listening…" else "Screen off",
                        active = contentState.screenOffAudioEnabled,
                        onClick = onToggleScreenOffAudio,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureButton(
                        text = if (contentState.orientationLock == VideoOrientationLock.UNLOCKED) "Unlock" else "Lock",
                        active = contentState.orientationLock != VideoOrientationLock.UNLOCKED,
                        onClick = onToggleOrientationLock,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeatureButton(
                        text = "Timer",
                        active = contentState.closeAtElapsedRealtime != null,
                        onClick = { onScheduleClose(DEFAULT_CLOSE_MINUTES) },
                        modifier = Modifier.weight(1f),
                    )
                    FeatureButton(
                        text = "Cast",
                        active = false,
                        onClick = onCast,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeatureButton(
                        text = contentState.qualityPreset.displayName(),
                        active = contentState.qualityPreset != VideoQualityPreset.STANDARD,
                        onClick = onCycleQuality,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureButton(
                        text = "Danmaku",
                        active = contentState.danmakuControlAvailable,
                        onClick = onDanmaku,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Close after", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        OutlinedButton(
                            onClick = { onScheduleClose(minutes) },
                            modifier = Modifier.weight(1f),
                        ) { Text("${minutes}m") }
                    }
                }
                contentState.closeAtElapsedRealtime?.let { closeAt ->
                    val remaining = ((closeAt - now).coerceAtLeast(0L) / 60_000L)
                    Text(
                        text = if (remaining > 0) "Closing in ${remaining}m" else "Closing soon",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    OutlinedButton(
                        onClick = onCancelClose,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel timer") }
                }
            }
        }
    }
}

@Composable
private fun FeatureButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (active) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

private fun VideoQualityPreset.displayName(): String = when (this) {
    VideoQualityPreset.STANDARD -> "Standard"
    VideoQualityPreset.VIVID -> "Vivid"
    VideoQualityPreset.EYE_COMFORT -> "Eye care"
}

private const val DEFAULT_CLOSE_MINUTES = 30
