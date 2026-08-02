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

val VIDEO_APPS: Set<String> = setOf(
    "com.bilibili.app.in",
    "com.bilibili.app.blue",
    "com.qiyi.video",
    "com.youku.phone",
    "com.tencent.qqlive",
    "com.ss.android.ugc.aweme",
    "tv.danmaku.bili",
    "com.mgtv.tv",
    "com.hunantv.imgo.activity",
    "com.iqiyi.i18n",
)

/** Identifies video applications for the system-wide video mode. */
class VideoAppDetector(
    private val videoPackages: Set<String> = VIDEO_APPS,
) {
    fun isVideoApp(packageName: String?): Boolean {
        return packageName?.trim()?.let(videoPackages::contains) == true
    }

    fun packages(): Set<String> = videoPackages

    companion object {
        @JvmField
        val VIDEO_APPS: Set<String> = io.chaldeaprjkt.gamespace.gamebar.VIDEO_APPS
    }
}
