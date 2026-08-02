package io.chaldeaprjkt.gamespace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ComboSequenceTest {
    @Test
    fun actionsKeepRatioCoordinatesAndLoopConfiguration() {
        val sequence = ComboSequence(
            id = "test",
            name = "Skill",
            gamePackage = "com.example.game",
            actions = listOf(
                TouchAction(ActionType.TAP, 0.25f, 0.75f, durationMs = 50L),
            ),
            loopMode = LoopMode.COUNT,
            loopCount = 3,
        )

        assertEquals(0.25f, sequence.actions.single().xRatio, 0.0001f)
        assertEquals(0.75f, sequence.actions.single().yRatio, 0.0001f)
        assertEquals(LoopMode.COUNT, sequence.loopMode)
        assertEquals(3, sequence.loopCount)
    }
}
