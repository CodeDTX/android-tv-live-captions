package com.codedtx.livecaptions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.codedtx.livecaptions.caption.CaptionBus
import com.codedtx.livecaptions.caption.CaptionStatus
import com.codedtx.livecaptions.overlay.CaptionOverlayService
import kotlinx.coroutines.launch

/**
 * The only screen: three steps a person does once with the remote, then a
 * start/stop button. Every state is re-read in onResume because two of the
 * steps are completed in a system screen this activity cannot observe.
 */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var overlayNumber: TextView
    private lateinit var overlayDetail: TextView
    private lateinit var overlayAction: Button
    private lateinit var audioNumber: TextView
    private lateinit var audioDetail: TextView
    private lateinit var audioAction: Button
    private lateinit var startNumber: TextView
    private lateinit var startDetail: TextView
    private lateinit var startAction: Button
    private lateinit var testDetail: TextView
    private lateinit var testAction: Button

    private var overlaySettingsMissing = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val askAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val askCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val grant = result.data
        if (result.resultCode == RESULT_OK && grant != null) {
            startForegroundService(CaptionService.startIntent(this, result.resultCode, grant))
        } else {
            CaptionBus.status.value = CaptionStatus.Failed(getString(R.string.status_capture_declined))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.status_dot)
        overlayNumber = findViewById(R.id.overlay_number)
        overlayDetail = findViewById(R.id.overlay_detail)
        overlayAction = findViewById(R.id.overlay_action)
        audioNumber = findViewById(R.id.audio_number)
        audioDetail = findViewById(R.id.audio_detail)
        audioAction = findViewById(R.id.audio_action)
        startNumber = findViewById(R.id.start_number)
        startDetail = findViewById(R.id.start_detail)
        startAction = findViewById(R.id.start_action)
        testDetail = findViewById(R.id.test_detail)
        testAction = findViewById(R.id.test_action)

        overlayAction.setOnClickListener { openAccessibilitySettings() }
        audioAction.setOnClickListener { askAudio.launch(Manifest.permission.RECORD_AUDIO) }
        startAction.setOnClickListener { if (isRunning()) stopCaptions() else startCaptions() }
        testAction.setOnClickListener { speakTestSentence() }

        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    // Media usage, so playback capture picks it up like any show.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            if (!ttsReady) testDetail.setText(R.string.test_no_voice)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CaptionBus.status.collect { refresh() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        firstOpenAction()?.requestFocus()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val overlayOn = isOverlayEnabled()
        val audioOn = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val running = isRunning()

        showStep(
            number = overlayNumber, detail = overlayDetail, action = overlayAction, done = overlayOn,
            todo = if (overlaySettingsMissing) R.string.step_overlay_missing else R.string.step_overlay_todo,
            doneText = R.string.step_overlay_done,
        )
        showStep(
            number = audioNumber, detail = audioDetail, action = audioAction, done = audioOn,
            todo = R.string.step_audio_todo, doneText = R.string.step_audio_done,
        )

        val ready = overlayOn && audioOn
        startAction.isEnabled = ready || running
        startAction.setText(if (running) R.string.action_stop else R.string.action_start)
        startDetail.setText(
            when {
                running -> R.string.step_start_running
                ready -> R.string.step_start_todo
                else -> R.string.step_start_blocked
            },
        )
        startNumber.setTextColor(getColor(if (running) R.color.done else if (ready) R.color.amber else R.color.muted))

        showStatus(CaptionBus.status.value)
    }

    private fun showStep(number: TextView, detail: TextView, action: Button, done: Boolean, todo: Int, doneText: Int) {
        detail.setText(if (done) doneText else todo)
        detail.setTextColor(getColor(if (done) R.color.done else R.color.muted))
        number.setTextColor(getColor(if (done) R.color.done else R.color.amber))
        action.visibility = if (done) View.GONE else View.VISIBLE
    }

    private fun showStatus(value: CaptionStatus) {
        val (text, color) = when (value) {
            CaptionStatus.Stopped -> getString(R.string.status_stopped) to R.color.muted
            CaptionStatus.Starting -> getString(R.string.status_starting) to R.color.amber
            is CaptionStatus.Listening ->
                if (value.hearingSound) getString(R.string.status_listening) to R.color.done
                else getString(R.string.status_silent) to R.color.amber
            is CaptionStatus.Failed -> value.reason to R.color.amber
        }
        status.text = text
        (statusDot.background.mutate() as GradientDrawable).setColor(getColor(color))
    }

    private fun firstOpenAction(): View? =
        listOf(overlayAction, audioAction, startAction, testAction).firstOrNull { it.isShown && it.isEnabled }

    private fun isRunning(): Boolean = when (CaptionBus.status.value) {
        CaptionStatus.Starting, is CaptionStatus.Listening -> true
        else -> false
    }

    private fun isOverlayEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val mine = ComponentName(this, CaptionOverlayService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            overlaySettingsMissing = true
            refresh()
        }
    }

    private fun startCaptions() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        // Android 14 offers "share one app" by default, which would caption only
        // that app. Asking for the whole display removes the choice.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        try {
            askCapture.launch(intent)
        } catch (e: ActivityNotFoundException) {
            CaptionBus.status.value = CaptionStatus.Failed(getString(R.string.status_no_capture_prompt))
        }
    }

    private fun stopCaptions() {
        startService(CaptionService.stopIntent(this))
    }

    private fun speakTestSentence() {
        if (!ttsReady) {
            testDetail.setText(R.string.test_no_voice)
            return
        }
        tts?.speak(getString(R.string.test_sentence), TextToSpeech.QUEUE_FLUSH, null, "test")
    }
}
