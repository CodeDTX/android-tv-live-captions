package com.codedtx.livecaptions.caption

import kotlinx.coroutines.flow.MutableStateFlow

/** Where the capture service is up to, shown on the setup screen. */
sealed interface CaptionStatus {
    data object Stopped : CaptionStatus
    data object Starting : CaptionStatus

    /** [hearingSound] goes false after several seconds of digital silence. */
    data class Listening(val hearingSound: Boolean) : CaptionStatus
    data class Failed(val reason: String) : CaptionStatus
}

/**
 * Hands captions from the capture service to the overlay service. Both run in
 * the app's single process, so a pair of state flows is all the plumbing needed.
 */
object CaptionBus {
    val frame = MutableStateFlow(CaptionFrame.EMPTY)
    val status = MutableStateFlow<CaptionStatus>(CaptionStatus.Stopped)
}
