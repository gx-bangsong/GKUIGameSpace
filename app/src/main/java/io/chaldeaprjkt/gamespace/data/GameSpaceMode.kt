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

/**
 * The surface managed by GameSpace at the moment.
 *
 * GameMode and VideoMode carry the package which caused the mode transition.  This is
 * intentional: timer and combo data is scoped to a package and a mode without a package
 * cannot be restored safely after a task-stack transition.
 */
sealed class GameSpaceMode {
    data class GameMode(val packageName: String) : GameSpaceMode() {
        init {
            require(packageName.isNotBlank()) { "A game mode must have a package name" }
        }
    }

    data class VideoMode(val packageName: String) : GameSpaceMode() {
        init {
            require(packageName.isNotBlank()) { "A video mode must have a package name" }
        }
    }

    object Idle : GameSpaceMode()
}
