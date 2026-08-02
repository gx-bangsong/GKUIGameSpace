/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2025 AxionOS
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
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.internal.app.IGameSpaceCallback
import com.android.internal.app.IGameSpaceService
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.data.GameSpaceMode
import io.chaldeaprjkt.gamespace.data.GameOptimizationManager
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class GameSpaceService : Hilt_GameSpaceService() {
    private val tag = "GameSpaceService"
    private val handler = Handler(Looper.getMainLooper())
    private val videoAppDetector = VideoAppDetector()
    private var gameSpaceService: IGameSpaceService? = null
    private var activeGamePackage: String? = null
    private var lastForegroundPackage: String? = null

    @Inject
    lateinit var gameOptimization: GameOptimizationManager

    private val callback = object : IGameSpaceCallback.Stub() {
        override fun onGameStart(packageName: String) {
            Log.d(tag, "Game started: $packageName")
            activeGamePackage = packageName
            SessionService.start(applicationContext, packageName)
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            Process.setThreadGroupAndCpuset(Process.myTid(), Process.THREAD_GROUP_SYSTEM)
            Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_SYSTEM)
            gameOptimization.optimizeGameLaunch(packageName)
            handler.post { dispatchForegroundPackage(packageName) }
        }

        override fun onGameLeave() {
            Log.d(tag, "Game left")
            activeGamePackage = null
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            Process.setThreadGroupAndCpuset(Process.myTid(), Process.THREAD_GROUP_BACKGROUND)
            Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_BACKGROUND)
            handler.postDelayed({ refreshForegroundMode(force = true) }, MODE_TRANSITION_DEBOUNCE_MS)
        }
    }

    private val taskStackListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            handler.post { refreshForegroundMode(force = false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created, registering callbacks")
        registerCallback()
        registerTaskStackListener()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        Process.setThreadGroupAndCpuset(Process.myTid(), Process.THREAD_GROUP_BACKGROUND)
        Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_BACKGROUND)
        handler.post { refreshForegroundMode(force = true) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_PACKAGE_NAME)?.let {
                activeGamePackage = it
                SessionService.start(applicationContext, it)
            }
            ACTION_START_VIDEO -> intent.getStringExtra(EXTRA_PACKAGE_NAME)?.let {
                SessionService.startVideo(applicationContext, it)
            }
            ACTION_STOP -> SessionService.stop(applicationContext)
        }
        return START_STICKY
    }

    /** Entry point used by the task-stack listener and by tests/future system callbacks. */
    fun onForegroundAppChanged(packageName: String) {
        dispatchForegroundPackage(packageName)
    }

    private fun refreshForegroundMode(force: Boolean) {
        val packageName = getForegroundPackage() ?: return
        if (!force && packageName == lastForegroundPackage) return
        lastForegroundPackage = packageName
        dispatchForegroundPackage(packageName)
    }

    private fun dispatchForegroundPackage(packageName: String) {
        val newMode = when {
            activeGamePackage == packageName -> GameSpaceMode.GameMode(packageName)
            videoAppDetector.isVideoApp(packageName) -> GameSpaceMode.VideoMode(packageName)
            else -> GameSpaceMode.Idle
        }
        when (newMode) {
            is GameSpaceMode.GameMode -> SessionService.start(applicationContext, newMode.packageName)
            is GameSpaceMode.VideoMode -> SessionService.switchMode(applicationContext, newMode)
            GameSpaceMode.Idle -> SessionService.stop(applicationContext)
        }
    }

    private fun getForegroundPackage(): String? = runCatching {
        ActivityTaskManager.getService().focusedRootTaskInfo?.topActivity?.packageName
    }.getOrNull()

    private fun registerTaskStackListener() {
        runCatching {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener)
        }.onFailure { Log.w(tag, "Unable to register TaskStackListener", it) }
    }

    private fun unregisterTaskStackListener() {
        runCatching {
            ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener)
        }
    }

    private fun registerCallback() {
        gameSpaceService = IGameSpaceService.Stub.asInterface(
            ServiceManager.getService("game_space")
        )
        if (gameSpaceService != null) {
            try {
                gameSpaceService?.registerCallback(callback)
                Log.i(tag, "Game-space callback registered")
            } catch (e: RemoteException) {
                Log.e(tag, "Failed to register game-space callback", e)
            }
        } else {
            Log.e(tag, "game_space service is not available")
        }
    }

    private fun unregisterCallback() {
        try {
            gameSpaceService?.unregisterCallback(callback)
        } catch (e: RemoteException) {
            Log.w(tag, "Failed to unregister game-space callback", e)
        }
        gameSpaceService = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterTaskStackListener()
        unregisterCallback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val MODE_TRANSITION_DEBOUNCE_MS = 150L
        const val ACTION_START = "game_start"
        const val ACTION_START_VIDEO = "video_start"
        const val ACTION_STOP = "game_stop"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
