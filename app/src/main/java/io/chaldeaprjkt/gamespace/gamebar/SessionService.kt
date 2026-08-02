/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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

import android.annotation.SuppressLint
import android.app.GameManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.WindowManager
import com.android.axion.platform.AxPlatformClient
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.GameSession
import io.chaldeaprjkt.gamespace.data.GameSpaceMode
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.mapper.MapperController
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.utils.isServiceRunning
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class SessionService : Hilt_SessionService() {
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var settings: SystemSettings
    @Inject lateinit var session: GameSession
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var gameModeUtils: GameModeUtils
    @Inject lateinit var callListener: CallListener
    @Inject lateinit var danmakuService: DanmakuService
    @Inject lateinit var brightnessInteractor: BrightnessInteractor
    @Inject lateinit var fpsInteractor: FpsInteractor
    @Inject lateinit var tileRepository: TileRepository
    @Inject lateinit var gson: Gson

    private var currentPackage: String? = null
    private lateinit var gameManager: GameManager
    private lateinit var sidebar: GameSideBar
    private lateinit var mapperController: MapperController
    private lateinit var platform: AxPlatformClient
    private lateinit var toolbar: GameSpaceToolbar
    private lateinit var timerModule: GameTimerModule
    private lateinit var comboModule: AutoComboModule
    private lateinit var videoModule: VideoToolboxModule
    private lateinit var modeManager: GameSpaceModeManager

    private var dndEnabledByUs = false
    private var previousDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SessionService created")

        platform = AxPlatformClient.getInstance()
        platform.init(this)

        gameManager = getSystemService(Context.GAME_SERVICE) as GameManager
        gameModeUtils.bind(gameManager)
        tileRepository.init(platform)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val mainHandler = Handler(Looper.getMainLooper())

        mapperController = MapperController(
            context = this,
            wm = windowManager,
            handler = mainHandler,
            gson = gson,
        )
        toolbar = GameSpaceToolbar()
        timerModule = GameTimerModule(this, windowManager)
        comboModule = AutoComboModule(this, windowManager)
        videoModule = VideoToolboxModule(this, windowManager)

        sidebar = GameSideBar(
            context = this,
            wm = windowManager,
            handler = mainHandler,
            appSettings = appSettings,
            screenUtils = screenUtils,
            danmakuService = danmakuService,
            brightnessInteractor = brightnessInteractor,
            fpsInteractor = fpsInteractor,
            gameModeUtils = gameModeUtils,
            settings = settings,
            tileRepository = tileRepository,
            platform = platform,
            mapperController = mapperController,
            toolbar = toolbar,
            timerModule = timerModule,
            comboModule = comboModule,
            videoModule = videoModule,
        )
        sidebar.onCreate()
        modeManager = GameSpaceModeManager(
            context = this,
            timerModule = timerModule,
            comboModule = comboModule,
            videoModule = videoModule,
            toolbar = toolbar,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_PACKAGE_NAME)?.let {
                switchTo(GameSpaceMode.GameMode(it))
            }
            ACTION_START_VIDEO -> intent.getStringExtra(EXTRA_PACKAGE_NAME)?.let {
                switchTo(GameSpaceMode.VideoMode(it))
            }
            ACTION_SWITCH_MODE -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                when (intent.getStringExtra(EXTRA_MODE)) {
                    MODE_GAME -> packageName?.let { switchTo(GameSpaceMode.GameMode(it)) }
                    MODE_VIDEO -> packageName?.let { switchTo(GameSpaceMode.VideoMode(it)) }
                    MODE_IDLE -> stopSession()
                }
            }
            ACTION_STOP -> {
                stopSession()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::sidebar.isInitialized) sidebar.onConfigurationChanged(newConfig)
    }

    private fun switchTo(newMode: GameSpaceMode) {
        val newPackage = when (newMode) {
            is GameSpaceMode.GameMode -> newMode.packageName
            is GameSpaceMode.VideoMode -> newMode.packageName
            GameSpaceMode.Idle -> null
        }
        if (newPackage == null) {
            stopSession()
            return
        }

        var currentMode = if (::modeManager.isInitialized) modeManager.mode.value else GameSpaceMode.Idle
        if (currentPackage != newPackage) {
            if (currentPackage != null) stopSession()
            currentPackage = newPackage
            currentMode = GameSpaceMode.Idle
        }

        if (newMode is GameSpaceMode.GameMode && currentMode !is GameSpaceMode.GameMode) {
            session.unregister()
            session.register(newPackage)
            applyGameModeConfig(newPackage)
            applyAutoDnd()
            sidebar.onGameStart(newPackage)
            callListener.init()
        } else if (newMode is GameSpaceMode.VideoMode && currentMode !is GameSpaceMode.VideoMode) {
            if (currentMode is GameSpaceMode.GameMode) {
                sidebar.onGameModeExit()
                session.unregister()
                callListener.destroy()
                restoreAutoDnd()
            }
            sidebar.onVideoStart(newPackage)
        }
        modeManager.switchMode(newMode)
    }

    private fun stopSession() {
        if (!::modeManager.isInitialized || currentPackage == null) return
        Log.i(TAG, "Stopping GameSpace session for $currentPackage")
        val wasGame = modeManager.mode.value is GameSpaceMode.GameMode
        modeManager.switchMode(GameSpaceMode.Idle)
        if (wasGame) timerModule.onGameExit()
        sidebar.onGameLeave()
        session.unregister()
        callListener.destroy()
        restoreAutoDnd()
        currentPackage = null
    }

    private fun applyAutoDnd() {
        if (!appSettings.autoDnd) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        ) {
            previousDndFilter = currentFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            dndEnabledByUs = true
        }
    }

    private fun restoreAutoDnd() {
        if (!dndEnabledByUs) return
        dndEnabledByUs = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(previousDndFilter)
    }

    private fun applyGameModeConfig(app: String) {
        val userGame = settings.userGames.firstOrNull { it.packageName == app }
        val preferred = userGame?.mode ?: GameModeUtils.defaultPreferredMode
        gameModeUtils.activeGame = userGame
        val availableModes = gameManager.getAvailableGameModes(app)
        if (availableModes.contains(preferred)) {
            gameManager.setGameMode(app, preferred)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "SessionService destroyed")
        stopSession()
        if (::modeManager.isInitialized) modeManager.close()
        tileRepository.dispose()
        gameModeUtils.unbind()
        danmakuService.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SessionService"
        const val ACTION_START = "game_start"
        const val ACTION_START_VIDEO = "video_start"
        const val ACTION_SWITCH_MODE = "switch_mode"
        const val ACTION_STOP = "game_stop"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_MODE = "mode"
        const val MODE_GAME = "game"
        const val MODE_VIDEO = "video"
        const val MODE_IDLE = "idle"

        fun start(context: Context, app: String) {
            startService(context, ACTION_START, app, MODE_GAME)
        }

        fun startVideo(context: Context, app: String) {
            startService(context, ACTION_START_VIDEO, app, MODE_VIDEO)
        }

        fun switchMode(context: Context, mode: GameSpaceMode) {
            val modeName = when (mode) {
                is GameSpaceMode.GameMode -> MODE_GAME
                is GameSpaceMode.VideoMode -> MODE_VIDEO
                GameSpaceMode.Idle -> MODE_IDLE
            }
            val packageName = when (mode) {
                is GameSpaceMode.GameMode -> mode.packageName
                is GameSpaceMode.VideoMode -> mode.packageName
                GameSpaceMode.Idle -> null
            }
            startService(context, ACTION_SWITCH_MODE, packageName, modeName)
        }

        fun stop(context: Context) {
            if (!context.isServiceRunning(SessionService::class.java)) return
            Intent(context, SessionService::class.java).apply {
                action = ACTION_STOP
            }.let { context.startServiceAsUser(it, UserHandle.CURRENT) }
        }

        private fun startService(
            context: Context,
            action: String,
            packageName: String?,
            mode: String,
        ) {
            Intent(context, SessionService::class.java).apply {
                this.action = action
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_MODE, mode)
            }.let { context.startServiceAsUser(it, UserHandle.CURRENT) }
        }
    }
}
