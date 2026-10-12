package com.codedtx.livecaptions.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection

/**
 * Records what the TV is playing, as 16 kHz mono, which is what the recogniser wants.
 *
 * Android only hands over audio from apps that allow it. Most paid streaming
 * apps opt out, and for those this records silence. Nothing in the API reports
 * the opt-out, so the service watches the level instead.
 */
class PlaybackCapture private constructor(
    private val record: AudioRecord,
    private val channels: Int,
    private val factor: Int,
) {
    private var scratch = ShortArray(0)

    fun start() = record.startRecording()

    /** Fills [out] with 16 kHz mono samples. Returns the count, or a negative AudioRecord error. */
    fun read(out: ShortArray): Int {
        if (channels == 1 && factor == 1) return record.read(out, 0, out.size)
        val needed = out.size * channels * factor
        if (scratch.size != needed) scratch = ShortArray(needed)
        val read = record.read(scratch, 0, needed)
        if (read <= 0) return read
        return Pcm.downmix(scratch, read, channels, factor, out)
    }

    fun release() {
        runCatching { record.stop() }
        record.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** Caller must hold RECORD_AUDIO. */
        @SuppressLint("MissingPermission")
        fun open(projection: MediaProjection): PlaybackCapture {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            // Ask for 16 kHz mono first. If a device's capture path will not
            // convert, fall back to the 48 kHz stereo every device plays at.
            build(config, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO)?.let { return PlaybackCapture(it, 1, 1) }
            build(config, 48_000, AudioFormat.CHANNEL_IN_STEREO)?.let { return PlaybackCapture(it, 2, 3) }
            error("This TV would not open an audio capture stream")
        }

        @SuppressLint("MissingPermission")
        private fun build(config: AudioPlaybackCaptureConfiguration, rate: Int, channelMask: Int): AudioRecord? {
            val minBuffer = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return null
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuffer * 4)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            }.getOrNull() ?: return null
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return null
            }
            return record
        }
    }
}
