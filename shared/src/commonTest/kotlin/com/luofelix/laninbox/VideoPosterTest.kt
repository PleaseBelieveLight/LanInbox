package com.luofelix.laninbox

import com.luofelix.laninbox.feature.inbox.canDecodeLegacyVideoPoster
import com.luofelix.laninbox.feature.inbox.videoPosterSize
import com.luofelix.laninbox.feature.inbox.videoPosterTimeUs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPosterTest {
    @Test
    fun boundsLandscapeAndPortraitCoversWithoutUpscalingSmallVideos() {
        val landscape = videoPosterSize(3_840, 2_160)
        assertEquals(512, landscape.width)
        assertEquals(288, landscape.height)
        val portrait = videoPosterSize(1_080, 1_920)
        assertEquals(288, portrait.width)
        assertEquals(512, portrait.height)
        assertEquals(160, videoPosterSize(160, 90).width)
        assertEquals(1, videoPosterSize(1, Int.MAX_VALUE).width)
        assertFailsWith<IllegalArgumentException> { videoPosterSize(0, 100) }
    }

    @Test
    fun preventsUnboundedLegacyFrameAllocations() {
        assertTrue(canDecodeLegacyVideoPoster(3_840, 2_160))
        assertFalse(canDecodeLegacyVideoPoster(7_680, 4_320))
        assertFalse(canDecodeLegacyVideoPoster(null, 1_080))
        assertFalse(canDecodeLegacyVideoPoster(-1, 1_080))
        assertFalse(canDecodeLegacyVideoPoster(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun selectsAnExistingPositionInShortClipsAndAvoidsDurationOverflow() {
        listOf(1L, 100L, 999L, 1_500L).forEach { duration ->
            assertTrue(videoPosterTimeUs(duration) in 0 until duration * 1_000)
        }
        assertEquals(-1L, videoPosterTimeUs(null))
        assertEquals(-1L, videoPosterTimeUs(0))
        assertEquals(1_000_000L, videoPosterTimeUs(Long.MAX_VALUE))
    }
}
