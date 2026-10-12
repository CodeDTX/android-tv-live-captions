package com.codedtx.livecaptions

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.codedtx.livecaptions.asr.Transcript
import com.codedtx.livecaptions.asr.VoskTranscriber
import com.codedtx.livecaptions.audio.PlaybackCapture
import com.codedtx.livecaptions.audio.Pcm
import com.codedtx.livecaptions.caption.CaptionBus
import com.codedtx.livecaptions.caption.CaptionFrame
import com.codedtx.livecaptions.caption.CaptionStatus
import com.codedtx.livecaptions.caption.CaptionTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens to what the TV plays and publishes captions to [CaptionBus].
 *
 * A foreground service of type mediaProjection, because that is the only kind
 * Android lets hold a screen-capture grant. The grant arrives from the setup
 * screen as an activity result, and is single-use: stopping means asking again.
 */
class CaptionService : LifecycleService() {

    private var projection: MediaProjection? = null
    private var listening: Job? = null

    /** Set when the service is going away on purpose, so a read that fails because of it is not reported. */
    @Volatile
    private var stopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (listening != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val grant = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_GRANT, Intent::class.java) }
        if (resultCode != Activity.RESULT_OK || grant == null) {
            fail("Screen capture was not allowed")
            return START_NOT_STICKY
        }

        // Android 14 and later require the foreground state before the projection exists.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        CaptionBus.status.value = CaptionStatus.Starting

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, grant) ?: run {
            fail("Android did not hand over the capture grant")
            return START_NOT_STICKY
        }
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = stopSelf()
            },
            Handler(Looper.getMainLooper()),
        )
        this.projection = projection

        listening = lifecycleScope.launch { listen(projection) }
        return START_NOT_STICKY
    }

    private suspend fun listen(projection: MediaProjection) {
        val transcriber = try {
            VoskTranscriber.load(this)
        } catch (e: Exception) {
            Log.e(TAG, "Speech model failed to load", e)
            fail("The speech model did not load")
            return
        }

        val capture = try {
            PlaybackCapture.open(projection)
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed to open", e)
            transcriber.close()
            fail("This TV would not share its sound")
            return
        }

        val track = CaptionTrack()
        // A quarter second per read: short enough that words appear as they are spoken.
        val buffer = ShortArray(PlaybackCapture.SAMPLE_RATE / 4)
        var lastSound = SystemClock.elapsedRealtime()
        var hearing = true
        CaptionBus.status.value = CaptionStatus.Listening(hearingSound = true)

        try {
            withContext(Dispatchers.IO) {
                capture.start()
                while (isActive) {
                    val count = capture.read(buffer)
                    if (count < 0) error("AudioRecord read failed: $count")
                    if (count == 0) continue
                    val now = SystemClock.elapsedRealtime()

                    if (Pcm.rms(buffer, count) > SILENCE_RMS) lastSound = now
                    val nowHearing = now - lastSound < SILENCE_MILLIS
                    if (nowHearing != hearing) {
                        hearing = nowHearing
                        CaptionBus.status.value = CaptionStatus.Listening(hearing)
                    }

                    val changed = when (val t = transcriber.accept(buffer, count)) {
                        is Transcript.Partial -> track.onPartial(t.text, now)
                        is Transcript.Final -> track.onFinal(t.text, now)
                        null -> false
                    }
                    if (changed or track.expire(now)) CaptionBus.frame.value = track.frame()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stopping) return
            Log.e(TAG, "Listening stopped", e)
            fail("Listening stopped unexpectedly")
        } finally {
            // Released here, on the reading side, never from under a blocked read.
            capture.release()
            transcriber.close()
        }
    }

    private fun fail(reason: String) {
        CaptionBus.status.value = CaptionStatus.Failed(reason)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        listening?.cancel()
        projection?.stop()
        CaptionBus.frame.value = CaptionFrame.EMPTY
        if (CaptionBus.status.value !is CaptionStatus.Failed) CaptionBus.status.value = CaptionStatus.Stopped
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_captions)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stop).build())
            .build()
    }

    companion object {
        private const val TAG = "CaptionService"
        private const val CHANNEL_ID = "captions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.codedtx.livecaptions.STOP"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_GRANT = "grant"

        /** Below this level a quarter second counts as silence. Full scale is 32768. */
        private const val SILENCE_RMS = 30.0
        private const val SILENCE_MILLIS = 8_000L

        fun startIntent(context: Context, resultCode: Int, grant: Intent): Intent =
            Intent(context, CaptionService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_GRANT, grant)

        fun stopIntent(context: Context): Intent =
            Intent(context, CaptionService::class.java).setAction(ACTION_STOP)
    }
}
