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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.view.Gravity
import android.view.WindowManager
import io.chaldeaprjkt.gamespace.data.ActionType
import io.chaldeaprjkt.gamespace.data.ComboSequence
import io.chaldeaprjkt.gamespace.data.LoopMode
import io.chaldeaprjkt.gamespace.data.TouchAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Coordinates combo recording, SQLite persistence, replay and the edge trigger window. */
class AutoComboModule(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
    inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ComboStore(context)
    private val recorder = ComboRecorder(inputManager, windowManager)
    private val displayMetrics = context.resources.displayMetrics
    private val executor = ComboExecutor(inputManager, displayMetrics)
    private val triggerParams = WindowManager.LayoutParams(
        dp(56),
        dp(56),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(4)
        y = dp(220)
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private var triggerView: ComboTriggerView? = null
    private var currentPackage: String? = null
    private var selectedIndex = 0
    private var recording = false

    private val _combos = MutableStateFlow<List<ComboSequence>>(emptyList())
    val combos: StateFlow<List<ComboSequence>> = _combos.asStateFlow()
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun loadCombos(packageName: String) {
        if (packageName.isBlank()) return
        currentPackage = packageName
        _combos.value = store.load(packageName)
        selectedIndex = selectedIndex.coerceIn(0, (_combos.value.size - 1).coerceAtLeast(0))
        updateTrigger()
    }

    fun show() {
        if (currentPackage == null || triggerView != null) return
        val view = ComboTriggerView(
            context = context,
            onClick = ::toggleSelected,
            onLongPress = ::selectNext,
            onDrag = ::moveTrigger,
        )
        view.setComboName(selectedCombo()?.name)
        runCatching {
            windowManager.addView(view, triggerParams)
            triggerView = view
        }
    }

    fun hide() {
        triggerView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        triggerView = null
        stopRecording()
    }

    fun toggleSelected() {
        val combo = selectedCombo() ?: return
        if (executor.isRunning) {
            executor.stop()
            triggerView?.setRunning(false)
        } else {
            executor.execute(combo, scope)
            triggerView?.setRunning(true)
            scope.launch {
                executor.awaitStopped()
                triggerView?.setRunning(false)
            }
        }
    }

    fun stopAll() {
        executor.stop()
        triggerView?.setRunning(false)
    }

    fun selectNext() {
        if (_combos.value.isEmpty()) return
        selectedIndex = (selectedIndex + 1) % _combos.value.size
        updateTrigger()
    }

    fun startRecording(): Boolean {
        val packageName = currentPackage ?: return false
        if (recording) return false
        recording = recorder.start(packageName)
        _isRecording.value = recording
        triggerView?.setRecording(recording)
        return recording
    }

    fun stopRecording(name: String = "Combo"): ComboSequence? {
        if (!recording) return null
        val actions = recorder.stop()
        recording = false
        _isRecording.value = false
        triggerView?.setRecording(false)
        val packageName = currentPackage ?: return null
        if (actions.isEmpty()) return null
        val combo = ComboSequence(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Combo" },
            gamePackage = packageName,
            actions = actions,
            loopMode = LoopMode.ONCE,
            loopCount = 1,
        )
        store.save(combo)
        loadCombos(packageName)
        return combo
    }

    fun saveCombo(combo: ComboSequence) {
        store.save(combo)
        if (combo.gamePackage == currentPackage) loadCombos(combo.gamePackage)
    }

    fun deleteCombo(id: String) {
        store.delete(id)
        currentPackage?.let(::loadCombos)
    }

    fun onConfigurationChanged() {
        val bounds = windowManager.maximumWindowMetrics.bounds
        displayMetrics.widthPixels = bounds.width()
        displayMetrics.heightPixels = bounds.height()
        if (triggerView != null) {
            triggerParams.x = triggerParams.x.coerceIn(0, dp(24))
            triggerParams.y = triggerParams.y.coerceAtLeast(0)
            runCatching { windowManager.updateViewLayout(triggerView, triggerParams) }
        }
    }

    fun close() {
        stopAll()
        if (recording) recorder.cancel()
        hide()
        store.close()
        scope.cancel()
    }

    private fun selectedCombo(): ComboSequence? =
        _combos.value.getOrNull(selectedIndex)

    private fun updateTrigger() {
        triggerView?.setComboName(selectedCombo()?.name)
    }

    private fun moveTrigger(dx: Float, dy: Float) {
        val view = triggerView ?: return
        val bounds = windowManager.maximumWindowMetrics.bounds
        val maxY = (bounds.height() - view.height).coerceAtLeast(0)
        triggerParams.y = (triggerParams.y + dy.roundToInt()).coerceIn(0, maxY)
        triggerParams.x = (triggerParams.x - dx.roundToInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, triggerParams) }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    /** SQLite persistence kept local so the ROM build does not need a Gradle-only Room runtime. */
    private class ComboStore(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $SEQUENCES (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    game_package TEXT NOT NULL,
                    loop_mode TEXT NOT NULL,
                    loop_count INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $ACTIONS (
                    sequence_id TEXT NOT NULL,
                    action_index INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    x_ratio REAL NOT NULL,
                    y_ratio REAL NOT NULL,
                    x2_ratio REAL NOT NULL,
                    y2_ratio REAL NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    delay_before_ms INTEGER NOT NULL,
                    PRIMARY KEY(sequence_id, action_index),
                    FOREIGN KEY(sequence_id) REFERENCES $SEQUENCES(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $ACTIONS")
            db.execSQL("DROP TABLE IF EXISTS $SEQUENCES")
            onCreate(db)
        }

        fun load(packageName: String): List<ComboSequence> {
            val result = mutableListOf<ComboSequence>()
            readableDatabase.query(
                SEQUENCES,
                null,
                "game_package = ?",
                arrayOf(packageName),
                null,
                null,
                "name COLLATE NOCASE ASC",
            ).use { sequenceCursor ->
                val idIndex = sequenceCursor.getColumnIndexOrThrow("id")
                val nameIndex = sequenceCursor.getColumnIndexOrThrow("name")
                val packageIndex = sequenceCursor.getColumnIndexOrThrow("game_package")
                val modeIndex = sequenceCursor.getColumnIndexOrThrow("loop_mode")
                val countIndex = sequenceCursor.getColumnIndexOrThrow("loop_count")
                while (sequenceCursor.moveToNext()) {
                    val id = sequenceCursor.getString(idIndex)
                    val actions = loadActions(id)
                    result += ComboSequence(
                        id = id,
                        name = sequenceCursor.getString(nameIndex),
                        gamePackage = sequenceCursor.getString(packageIndex),
                        actions = actions,
                        loopMode = runCatching {
                            LoopMode.valueOf(sequenceCursor.getString(modeIndex))
                        }.getOrDefault(LoopMode.ONCE),
                        loopCount = sequenceCursor.getInt(countIndex).coerceAtLeast(1),
                    )
                }
            }
            return result
        }

        fun save(combo: ComboSequence) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(SEQUENCES, "id = ?", arrayOf(combo.id))
                db.delete(ACTIONS, "sequence_id = ?", arrayOf(combo.id))
                db.insertOrThrow(SEQUENCES, null, ContentValues().apply {
                    put("id", combo.id)
                    put("name", combo.name)
                    put("game_package", combo.gamePackage)
                    put("loop_mode", combo.loopMode.name)
                    put("loop_count", combo.loopCount.coerceAtLeast(1))
                })
                combo.actions.forEachIndexed { index, action ->
                    db.insertOrThrow(ACTIONS, null, ContentValues().apply {
                        put("sequence_id", combo.id)
                        put("action_index", index)
                        put("type", action.type.name)
                        put("x_ratio", action.xRatio)
                        put("y_ratio", action.yRatio)
                        put("x2_ratio", action.x2Ratio)
                        put("y2_ratio", action.y2Ratio)
                        put("duration_ms", action.durationMs)
                        put("delay_before_ms", action.delayBeforeMs)
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun delete(id: String) {
            writableDatabase.delete(SEQUENCES, "id = ?", arrayOf(id))
        }

        private fun loadActions(sequenceId: String): List<TouchAction> {
            val result = mutableListOf<TouchAction>()
            readableDatabase.query(
                ACTIONS,
                null,
                "sequence_id = ?",
                arrayOf(sequenceId),
                null,
                null,
                "action_index ASC",
            ).use { cursor ->
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val xIndex = cursor.getColumnIndexOrThrow("x_ratio")
                val yIndex = cursor.getColumnIndexOrThrow("y_ratio")
                val x2Index = cursor.getColumnIndexOrThrow("x2_ratio")
                val y2Index = cursor.getColumnIndexOrThrow("y2_ratio")
                val durationIndex = cursor.getColumnIndexOrThrow("duration_ms")
                val delayIndex = cursor.getColumnIndexOrThrow("delay_before_ms")
                while (cursor.moveToNext()) {
                    result += TouchAction(
                        type = runCatching { ActionType.valueOf(cursor.getString(typeIndex)) }
                            .getOrDefault(ActionType.TAP),
                        xRatio = cursor.getFloat(xIndex),
                        yRatio = cursor.getFloat(yIndex),
                        x2Ratio = cursor.getFloat(x2Index),
                        y2Ratio = cursor.getFloat(y2Index),
                        durationMs = cursor.getLong(durationIndex),
                        delayBeforeMs = cursor.getLong(delayIndex),
                    )
                }
            }
            return result
        }

        companion object {
            private const val DATABASE_NAME = "gamespace_combos.db"
            private const val DATABASE_VERSION = 1
            private const val SEQUENCES = "combo_sequences"
            private const val ACTIONS = "touch_actions"
        }
    }
}

private suspend fun ComboExecutor.awaitStopped() {
    while (isRunning) kotlinx.coroutines.delay(100L)
}
