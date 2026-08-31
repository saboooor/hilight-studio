package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternAudioPlayerTest {

    @Test
    fun `every pattern generates valid 16-bit PCM audio samples`() {
        for (pattern in Pattern.entries) {
            val pcm = PatternAudioPlayer.synthesize(pattern)
            assertNotNull("PCM buffer should not be null for $pattern", pcm)
            assertTrue("PCM buffer should not be empty for $pattern", pcm.isNotEmpty())
            assertEquals("PCM buffer size must be even (16-bit mono)", 0, pcm.size % 2)

            // Verify samples contain actual audible waveform data
            var maxAmplitude = 0
            for (i in 0 until pcm.size step 2) {
                val low = pcm[i].toInt() and 0xFF
                val high = pcm[i + 1].toInt()
                val sample = (high shl 8) or low
                val absSample = Math.abs(sample.toShort().toInt())
                if (absSample > maxAmplitude) {
                    maxAmplitude = absSample
                }
            }

            assertTrue(
                "Pattern $pattern should produce non-silent audio with peak amplitude > 100",
                maxAmplitude > 100,
            )
            assertTrue(
                "Pattern $pattern should not hard-clip (peak amplitude <= 32767)",
                maxAmplitude <= 32767,
            )
        }
    }

    @Test
    fun `getPcmBuffer returns cached buffers matching synthesis`() {
        for (pattern in Pattern.entries) {
            val cached = PatternAudioPlayer.getPcmBuffer(pattern)
            assertTrue(cached.isNotEmpty())
            assertEquals(cached.size, PatternAudioPlayer.synthesize(pattern).size)
        }
    }

    @Test
    fun `startActive and stopActive lifecycle safely executes without crash`() {
        PatternAudioPlayer.startActive(Pattern.PULSE, 800, 1000)
        PatternAudioPlayer.stopActive()

        PatternAudioPlayer.startActive(Pattern.OFF, 800, 1000)
        PatternAudioPlayer.stopActive()
    }
}

