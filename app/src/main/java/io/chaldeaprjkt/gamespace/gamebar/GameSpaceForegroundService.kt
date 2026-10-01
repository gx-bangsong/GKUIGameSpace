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

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.data.GameSpaceMode
import io.chaldeaprjkt.gamespace.data.SystemSettings
import javax.inject.Inject

/**
 * Android 17-compatible foreground/task observer.  Upstream 17.0 starts SessionService
 * directly for games, so this service is only responsible for the additional video mode and
 * for handing a configured game back to SessionService after a video app is closed.
 */
@AndroidEntryPoint(Service::class)
class GameSpaceForegroundService : Hilt_GameSpaceForegroundService() {
    private val handler = Handler(Looper.getMainLooper())
    private val videoAppDetector = VideoAppDetector()
    private var activeVideoPackage: String? = null

    @Inject
    lateinit var systemSettings: SystemSettings

    private val taskStackListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            handler.post { refreshForegroundPackage(false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerTaskStackListener()
        handler.post { refreshForegroundPackage(true) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun refreshForegroundPackage(force: Boolean) {
        val packageName = runCatching {
            ActivityTaskManager.getService().focusedRootTaskInfo?.topActivity?.packageName
        }.getOrNull() ?: return
        if (!force && packageName == activeVideoPackage && videoAppDetector.isVideoApp(packageName)) return

        if (videoAppDetector.isVideoApp(packageName)) {
            if (activeVideoPackage != packageName) {
                activeVideoPackage = packageName
                SessionService.switchMode(
                    applicationContext,
                    GameSpaceMode.VideoMode(packageName),
                )
            }
            return
        }

        if (activeVideoPackage != null) {
            activeVideoPackage = null
            val isConfiguredGame = systemSettings.userGames.any { it.packageName == packageName }
            if (isConfiguredGame) {
                SessionService.start(applicationContext, packageName)
            } else {
                SessionService.stop(applicationContext)
            }
        }
    }

    private fun registerTaskStackListener() {
        runCatching {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener)
        }.onFailure { Log.w(TAG, "Unable to register TaskStackListener", it) }
    }

    private fun unregisterTaskStackListener() {
        runCatching {
            ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterTaskStackListener()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GameSpaceForegroundService"
    }
}
