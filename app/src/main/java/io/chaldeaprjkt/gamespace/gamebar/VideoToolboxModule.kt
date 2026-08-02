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
import android.os.UserHandle
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import io.chaldeaprjkt.gamespace.data.VideoOrientationLock
import io.chaldeaprjkt.gamespace.data.VideoQualityPreset
import io.chaldeaprjkt.gamespace.data.VideoToolboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Integrates screen-off audio, close timer, orientation, cast and video controls. */
class VideoToolboxModule(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
) {
    private val screenOffAudio = ScreenOffAudioManager(context)
    private val closeManager = VideoTimerCloseManager(context) {
        updateState { it.copy(closeAtElapsedRealtime = null) }
    }
    private val resolver = context.contentResolver
    private var savedAccelerometerRotation: Int? = null
    private var savedUserRotation: Int? = null

    private val _state = MutableStateFlow(VideoToolboxState())
    val state: StateFlow<VideoToolboxState> = _state.asStateFlow()

    fun show(packageName: String? = _state.value.currentPackage) {
        val pkg = packageName?.takeIf(String::isNotBlank) ?: return
        updateState {
            it.copy(isVisible = true, currentPackage = pkg, closeAtElapsedRealtime = closeManager.closeAt.value)
        }
    }

    fun hide() {
        closeManager.cancel()
        screenOffAudio.disable()
        restoreOrientation()
        _state.value = VideoToolboxState()
    }

    fun toggleScreenOffAudio() {
        val enabled = screenOffAudio.toggle()
        updateState { it.copy(screenOffAudioEnabled = enabled) }
        if (enabled) {
            Toast.makeText(context, "Audio will continue while the screen is off", Toast.LENGTH_SHORT).show()
        }
    }

    fun scheduleClose(minutes: Int) {
        closeManager.scheduleClose(minutes)
        updateState { it.copy(closeAtElapsedRealtime = closeManager.closeAt.value) }
    }

    fun cancelClose() {
        closeManager.cancel()
        updateState { it.copy(closeAtElapsedRealtime = null) }
    }

    fun toggleOrientationLock() {
        if (_state.value.orientationLock == VideoOrientationLock.UNLOCKED) {
            lockCurrentOrientation()
        } else {
            restoreOrientation()
            updateState { it.copy(orientationLock = VideoOrientationLock.UNLOCKED) }
        }
    }

    fun cycleQuality() {
        val next = when (_state.value.qualityPreset) {
            VideoQualityPreset.STANDARD -> VideoQualityPreset.VIVID
            VideoQualityPreset.VIVID -> VideoQualityPreset.EYE_COMFORT
            VideoQualityPreset.EYE_COMFORT -> VideoQualityPreset.STANDARD
        }
        updateState { it.copy(qualityPreset = next) }
        Toast.makeText(
            context,
            "${next.displayName()} display preset selected; device support may vary",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun openCastSettings() {
        val intent = Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivityAsUser(intent, UserHandle.CURRENT) }
            .onFailure {
                Toast.makeText(context, "Cast settings are unavailable", Toast.LENGTH_SHORT).show()
            }
    }

    fun showDanmakuGuide() {
        Toast.makeText(
            context,
            "Danmaku control requires an enabled AccessibilityService",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun close() {
        hide()
        screenOffAudio.close()
        closeManager.close()
    }

    private fun lockCurrentOrientation() {
        if (savedAccelerometerRotation == null) {
            savedAccelerometerRotation = Settings.System.getIntForUser(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                1,
                UserHandle.USER_CURRENT,
            )
            savedUserRotation = Settings.System.getIntForUser(
                resolver,
                Settings.System.USER_ROTATION,
                Surface.ROTATION_0,
                UserHandle.USER_CURRENT,
            )
        }
        val rotation = runCatching { windowManager.defaultDisplay.rotation }
            .getOrDefault(Surface.ROTATION_0)
        Settings.System.putIntForUser(
            resolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
            UserHandle.USER_CURRENT,
        )
        Settings.System.putIntForUser(
            resolver,
            Settings.System.USER_ROTATION,
            rotation,
            UserHandle.USER_CURRENT,
        )
        updateState {
            it.copy(
                orientationLock = if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                    VideoOrientationLock.PORTRAIT
                } else {
                    VideoOrientationLock.LANDSCAPE
                },
            )
        }
    }

    private fun restoreOrientation() {
        savedAccelerometerRotation?.let {
            Settings.System.putIntForUser(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                it,
                UserHandle.USER_CURRENT,
            )
        }
        savedUserRotation?.let {
            Settings.System.putIntForUser(
                resolver,
                Settings.System.USER_ROTATION,
                it,
                UserHandle.USER_CURRENT,
            )
        }
        savedAccelerometerRotation = null
        savedUserRotation = null
    }

    private fun updateState(transform: (VideoToolboxState) -> VideoToolboxState) {
        _state.value = transform(_state.value)
    }

    private fun VideoQualityPreset.displayName(): String = when (this) {
        VideoQualityPreset.STANDARD -> "Standard"
        VideoQualityPreset.VIVID -> "Vivid"
        VideoQualityPreset.EYE_COMFORT -> "Eye care"
    }
}
