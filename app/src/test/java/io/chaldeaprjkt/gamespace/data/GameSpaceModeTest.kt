package io.chaldeaprjkt.gamespace.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameSpaceModeTest {
    @Test
    fun gameAndVideoModesCarryTheirPackage() {
        val game = GameSpaceMode.GameMode("com.example.game")
        val video = GameSpaceMode.VideoMode("com.example.video")

        assertEquals("com.example.game", game.packageName)
        assertEquals("com.example.video", video.packageName)
        assertNotEquals(game, video)
    }

    @Test
    fun idleIsAStableSingleton() {
        assertEquals(GameSpaceMode.Idle, GameSpaceMode.Idle)
    }
}
