package com.codedtx.livecaptions.asr

import android.content.Context
import com.codedtx.livecaptions.audio.PlaybackCapture
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Offline recognition with Vosk. Audio never leaves the device. */
class VoskTranscriber private constructor(private val model: Model) : Transcriber {
    private val recognizer = Recognizer(model, PlaybackCapture.SAMPLE_RATE.toFloat())
    private var lastPartial = ""

    override fun accept(samples: ShortArray, count: Int): Transcript? {
        if (recognizer.acceptWaveForm(samples, count)) {
            lastPartial = ""
            return Transcript.Final(JSONObject(recognizer.result).optString("text"))
        }
        val partial = JSONObject(recognizer.partialResult).optString("partial")
        if (partial == lastPartial) return null
        lastPartial = partial
        return Transcript.Partial(partial)
    }

    private var closed = false

    /** Safe to call twice: a failed language swap closes the old model before the finally block does. */
    override fun close() {
        if (closed) return
        closed = true
        recognizer.close()
        model.close()
    }

    companion object {
        /**
         * Copies the bundled model for [language] out of the APK the first time
         * it is used (Vosk needs real files), then loads it. Later runs skip the
         * copy unless the model changed. Unused languages are never copied.
         */
        suspend fun load(context: Context, language: Language): VoskTranscriber = suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context.applicationContext,
                language.assetDir,
                "model",
                { model -> cont.resume(VoskTranscriber(model)) },
                { error -> cont.resumeWithException(error) },
            )
        }
    }
}
