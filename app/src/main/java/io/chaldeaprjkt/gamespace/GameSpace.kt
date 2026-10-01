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
package io.chaldeaprjkt.gamespace

import android.app.Application
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.chaldeaprjkt.gamespace.gamebar.GameSpaceForegroundService

@HiltAndroidApp(Application::class)
class GameSpace : Hilt_GameSpace() {
    private val tag = "GameSpace"

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Application created")
        runCatching {
            startServiceAsUser(
                Intent(this, GameSpaceForegroundService::class.java),
                UserHandle.CURRENT,
            )
        }.onFailure { Log.e(tag, "Unable to start foreground mode observer", it) }
    }
}
