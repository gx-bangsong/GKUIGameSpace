package io.chaldeaprjkt.gamespace.gamebar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAppDetectorTest {
    private val detector = VideoAppDetector()

    @Test
    fun knownVideoPackagesAreDetected() {
        assertTrue(detector.isVideoApp("tv.danmaku.bili"))
        assertTrue(detector.isVideoApp("com.tencent.qqlive"))
    }

    @Test
    fun unknownAndBlankPackagesAreRejected() {
        assertFalse(detector.isVideoApp("com.example.game"))
        assertFalse(detector.isVideoApp(null))
        assertFalse(detector.isVideoApp("  "))
    }
}
