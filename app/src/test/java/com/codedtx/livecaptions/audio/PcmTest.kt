package com.codedtx.livecaptions.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmTest {
    @Test
    fun `48 kHz stereo becomes 16 kHz mono by averaging`() {
        // Three stereo frames per output sample.
        val src = shortArrayOf(
            10, 20, 30, 40, 50, 60,
            -6, -6, -6, -6, -6, -6,
        )
        val out = ShortArray(4)
        val n = Pcm.downmix(src, src.size, channels = 2, factor = 3, out = out)
        assertEquals(2, n)
        assertEquals(35.toShort(), out[0])
        assertEquals((-6).toShort(), out[1])
    }

    @Test
    fun `mono at factor 1 is a copy`() {
        val src = shortArrayOf(1, -2, 3)
        val out = ShortArray(3)
        assertEquals(3, Pcm.downmix(src, 3, channels = 1, factor = 1, out = out))
        assertEquals(src.toList(), out.toList())
    }

    @Test
    fun `incomplete trailing frames are dropped and out is never overrun`() {
        val src = ShortArray(13) { 100 }
        val out = ShortArray(1)
        assertEquals(1, Pcm.downmix(src, src.size, channels = 2, factor = 3, out = out))
    }

    @Test
    fun `rms of silence is zero and of a constant is that constant`() {
        assertEquals(0.0, Pcm.rms(ShortArray(8), 8), 0.0)
        assertEquals(1000.0, Pcm.rms(ShortArray(8) { 1000 }, 8), 1e-9)
    }
}
