package com.codedtx.livecaptions.caption

import java.text.BreakIterator

/** One word on screen. [confirmed] is false while the recogniser may still change it. */
data class CaptionWord(val text: String, val confirmed: Boolean)

/** What the overlay draws: at most a few lines, oldest first. Empty means hide the overlay. */
data class CaptionFrame(val lines: List<List<CaptionWord>>) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        val EMPTY = CaptionFrame(emptyList())
    }
}

/**
 * Turns a stream of recogniser results into roll-up captions, the way live
 * television does it: the bottom line fills, then the block scrolls up a line.
 *
 * Two rules keep the text from jumping around while someone reads it.
 *
 * - Lines are wrapped greedily from the start of the buffer, so appending words
 *   never moves an earlier line break.
 * - Confirmed words are only ever dropped a whole line at a time, so the buffer
 *   always starts on a line boundary and the wrap stays where it was.
 *
 * Pure Kotlin with an injected clock value, so it is tested on the JVM.
 */
class CaptionTrack(
    private val maxCharsPerLine: Int = 37,
    private val maxLines: Int = 2,
    private val holdMillis: Long = 4_000,
) {
    private val confirmed = ArrayList<String>()
    private var partial: List<String> = emptyList()
    private var lastChange = 0L

    /** The recogniser's current guess for the utterance in progress. Returns true if the frame changed. */
    fun onPartial(text: String, now: Long): Boolean {
        val words = words(text)
        if (words == partial) return false
        partial = words
        lastChange = now
        return true
    }

    /** The recogniser has settled an utterance. Returns true if the frame changed. */
    fun onFinal(text: String, now: Long): Boolean {
        val words = words(text)
        val hadPartial = partial.isNotEmpty()
        partial = emptyList()
        if (words.isEmpty()) return hadPartial
        confirmed += words
        dropScrolledLines()
        lastChange = now
        return true
    }

    /** Clears the captions once nothing new has arrived for [holdMillis]. Returns true if it cleared. */
    fun expire(now: Long): Boolean {
        if (confirmed.isEmpty() && partial.isEmpty()) return false
        if (now - lastChange < holdMillis) return false
        confirmed.clear()
        partial = emptyList()
        return true
    }

    /** Drops everything on screen, for a change of language. */
    fun clear() {
        confirmed.clear()
        partial = emptyList()
    }

    fun frame(): CaptionFrame {
        val all = confirmed.map { CaptionWord(it, confirmed = true) } +
            partial.map { CaptionWord(it, confirmed = false) }
        if (all.isEmpty()) return CaptionFrame.EMPTY
        return CaptionFrame(wrap(all, maxCharsPerLine).takeLast(maxLines))
    }

    private fun dropScrolledLines() {
        val lines = wrap(confirmed.map { CaptionWord(it, confirmed = true) }, maxCharsPerLine)
        val excess = lines.size - maxLines
        if (excess <= 0) return
        val dropCount = lines.take(excess).sumOf { it.size }
        confirmed.subList(0, dropCount).clear()
    }

    private fun words(text: String): List<String> =
        tidy(text).split(' ').filter { it.isNotEmpty() }
}

/**
 * Greedy line wrap. A word longer than [maxChars] gets a line of its own rather
 * than being split, because a broken word is harder to read than a long line.
 */
internal fun wrap(words: List<CaptionWord>, maxChars: Int): List<List<CaptionWord>> {
    val lines = ArrayList<MutableList<CaptionWord>>()
    var length = 0
    for (word in words) {
        val line = lines.lastOrNull()
        val size = displayLength(word.text)
        if (line == null || length + 1 + size > maxChars) {
            lines += mutableListOf(word)
            length = size
        } else {
            line += word
            length += 1 + size
        }
    }
    return lines
}

/**
 * Length in letters a reader sees, not in UTF-16 units. In Devanagari a single
 * letter is often a consonant plus vowel signs, several units that draw as one.
 * Counting units would wrap Hindi lines far too early.
 */
internal fun displayLength(text: String): Int {
    if (text.all { it.code < 0x300 }) return text.length
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(text)
    var count = 0
    while (breaks.next() != BreakIterator.DONE) count++
    return count
}
