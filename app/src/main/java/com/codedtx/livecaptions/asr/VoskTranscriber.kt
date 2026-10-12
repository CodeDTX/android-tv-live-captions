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

    override fun close() {
        recognizer.close()
        model.close()
    }

    companion object {
        /**
         * Copies the bundled model out of the APK on first run (Vosk needs real
         * files), then loads it. Later runs skip the copy unless the model changed.
         */
        suspend fun load(context: Context): VoskTranscriber = suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context.applicationContext,
                "model-en-us",
                "model",
                { model -> cont.resume(VoskTranscriber(model)) },
                { error -> cont.resumeWithException(error) },
            )
        }
    }
}
