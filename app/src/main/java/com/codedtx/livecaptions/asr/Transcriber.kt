package com.codedtx.livecaptions.asr

sealed interface Transcript {
    /** The recogniser's current guess. It will usually change. */
    data class Partial(val text: String) : Transcript

    /** A settled utterance. */
    data class Final(val text: String) : Transcript
}

/**
 * Speech to text over a live stream. The only implementation today is Vosk; the
 * interface exists so a stronger engine (sherpa-onnx, whisper.cpp) can replace it
 * without touching capture or display.
 */
interface Transcriber : AutoCloseable {
    /** Feeds 16 kHz mono PCM. Returns what changed, or null if nothing did. */
    fun accept(samples: ShortArray, count: Int): Transcript?
}
