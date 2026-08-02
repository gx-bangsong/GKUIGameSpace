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
import io.chaldeaprjkt.gamespace.data.GameSpaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates feature lifecycles when the foreground package changes. */
class GameSpaceModeManager(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val timerModule: GameTimerModule,
    private val comboModule: AutoComboModule,
    private val videoModule: VideoToolboxModule,
    private val toolbar: GameSpaceToolbar,
) {
    private val _mode = MutableStateFlow<GameSpaceMode>(GameSpaceMode.Idle)
    val mode: StateFlow<GameSpaceMode> = _mode.asStateFlow()

    @Synchronized
    fun switchMode(newMode: GameSpaceMode) {
        val oldMode = _mode.value
        if (oldMode == newMode) return

        when (newMode) {
            is GameSpaceMode.GameMode -> {
                videoModule.hide()
                timerModule.restore(newMode.packageName)
                comboModule.stopAll()
                comboModule.loadCombos(newMode.packageName)
                comboModule.show()
                timerModule.show(newMode.packageName)
                toolbar.showGameButtons(newMode.packageName)
            }
            is GameSpaceMode.VideoMode -> {
                timerModule.save()
                timerModule.hide()
                comboModule.stopAll()
                comboModule.hide()
                videoModule.show(newMode.packageName)
                toolbar.showVideoButtons(newMode.packageName)
            }
            GameSpaceMode.Idle -> {
                videoModule.hide()
                timerModule.save()
                timerModule.hide()
                comboModule.stopAll()
                comboModule.hide()
                toolbar.hide()
            }
        }
        _mode.value = newMode
    }

    fun close() {
        switchMode(GameSpaceMode.Idle)
        timerModule.close()
        comboModule.close()
        videoModule.close()
    }
}
