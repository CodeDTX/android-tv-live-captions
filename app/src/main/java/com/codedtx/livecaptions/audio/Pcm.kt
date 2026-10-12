package com.codedtx.livecaptions.audio

import kotlin.math.sqrt

/** Plain functions over 16-bit PCM, kept free of Android types so they are tested on the JVM. */
object Pcm {
    /**
     * Averages interleaved [channels] down to mono and keeps one sample in every
     * [factor], averaging the ones it drops. Crude next to a proper resampler,
     * and fine for speech recognition, which only needs the band under 8 kHz.
     *
     * Returns the number of samples written to [out]. Trailing frames that do not
     * fill a whole group of [factor] are dropped.
     */
    fun downmix(src: ShortArray, length: Int, channels: Int, factor: Int, out: ShortArray): Int {
        require(channels >= 1 && factor >= 1)
        val frames = length / channels
        val count = minOf(frames / factor, out.size)
        val divisor = channels * factor
        for (i in 0 until count) {
            var sum = 0L
            val start = i * factor * channels
            for (j in 0 until divisor) sum += src[start + j]
            out[i] = (sum / divisor).toInt().toShort()
        }
        return count
    }

    /** Root-mean-square level of the first [length] samples, in sample units (0 to 32768). */
    fun rms(samples: ShortArray, length: Int): Double {
        if (length <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until length) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / length)
    }
}
