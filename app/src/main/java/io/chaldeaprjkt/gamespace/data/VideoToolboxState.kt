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
package io.chaldeaprjkt.gamespace.data

enum class VideoOrientationLock {
    UNLOCKED,
    PORTRAIT,
    LANDSCAPE,
}

enum class VideoQualityPreset {
    STANDARD,
    VIVID,
    EYE_COMFORT,
}

/** UI and operation state owned by [io.chaldeaprjkt.gamespace.gamebar.VideoToolboxModule]. */
data class VideoToolboxState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = true,
    val currentPackage: String? = null,
    val screenOffAudioEnabled: Boolean = false,
    val closeAtElapsedRealtime: Long? = null,
    val orientationLock: VideoOrientationLock = VideoOrientationLock.UNLOCKED,
    val qualityPreset: VideoQualityPreset = VideoQualityPreset.STANDARD,
    val danmakuControlAvailable: Boolean = false,
)
