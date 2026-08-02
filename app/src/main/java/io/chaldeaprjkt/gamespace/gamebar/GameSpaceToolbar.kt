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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.data.GameSpaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Window-independent toolbar state.  GameSideBar owns the actual WindowManager window while
 * this class lets the mode state machine update the existing Compose toolbar safely.
 */
class GameSpaceToolbar {
    private val _mode = MutableStateFlow<GameSpaceMode>(GameSpaceMode.Idle)
    val mode: StateFlow<GameSpaceMode> = _mode.asStateFlow()

    fun showGameButtons(packageName: String) {
        _mode.value = GameSpaceMode.GameMode(packageName)
    }

    fun showVideoButtons(packageName: String) {
        _mode.value = GameSpaceMode.VideoMode(packageName)
    }

    fun hide() {
        _mode.value = GameSpaceMode.Idle
    }
}

@Composable
fun GameModeFeatureButtons(
    timerVisible: Boolean,
    comboAvailable: Boolean,
    onToggleTimer: () -> Unit,
    onToggleCombo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Game tools",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (timerVisible) {
                Button(
                    onClick = onToggleTimer,
                    modifier = Modifier.weight(1f),
                ) { Text("Timers") }
            } else {
                OutlinedButton(
                    onClick = onToggleTimer,
                    modifier = Modifier.weight(1f),
                ) { Text("Timers") }
            }
            if (comboAvailable) {
                Button(
                    onClick = onToggleCombo,
                    modifier = Modifier.weight(1f),
                ) { Text("Combo") }
            } else {
                OutlinedButton(
                    onClick = onToggleCombo,
                    modifier = Modifier.weight(1f),
                ) { Text("Combo") }
            }
        }
    }
}
