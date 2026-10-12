package com.codedtx.livecaptions.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionTrackTest {

    private fun CaptionFrame.text(): List<String> = lines.map { line -> line.joinToString(" ") { it.text } }

    private fun CaptionFrame.unconfirmed(): List<String> =
        lines.flatten().filterNot { it.confirmed }.map { it.text }

    @Test
    fun `partial words show unconfirmed and become confirmed on final`() {
        val track = CaptionTrack()
        track.onPartial("hello there", now = 0)
        assertEquals(listOf("Hello", "there"), track.frame().unconfirmed())

        track.onFinal("hello there", now = 10)
        assertEquals(listOf("Hello there"), track.frame().text())
        assertEquals(emptyList<String>(), track.frame().unconfirmed())
    }

    @Test
    fun `a repeated partial is not a change`() {
        val track = CaptionTrack()
        assertTrue(track.onPartial("same words", now = 0))
        assertFalse(track.onPartial("same words", now = 5))
    }

    @Test
    fun `an empty final clears the guess without adding anything`() {
        val track = CaptionTrack()
        track.onPartial("um", now = 0)
        assertTrue(track.onFinal("", now = 1))
        assertTrue(track.frame().isEmpty)
        assertFalse(track.onFinal("", now = 2))
    }

    @Test
    fun `wraps to the line width and keeps only the last lines`() {
        val track = CaptionTrack(maxCharsPerLine = 10, maxLines = 2)
        track.onFinal("one two three four five six", now = 0)
        // "One two" / "three four" / "five six" -> the first line has scrolled off.
        assertEquals(listOf("three four", "five six"), track.frame().text())
    }

    @Test
    fun `earlier line breaks do not move when words are appended`() {
        val track = CaptionTrack(maxCharsPerLine = 10, maxLines = 3)
        track.onFinal("alpha beta gamma", now = 0)
        val before = track.frame().text()
        track.onPartial("delta", now = 1)
        val after = track.frame().text()
        assertEquals(before, after.take(before.size))
    }

    @Test
    fun `a partial scrolls the block up a line`() {
        val track = CaptionTrack(maxCharsPerLine = 12, maxLines = 2)
        track.onFinal("alpha beta gamma", now = 0) // "Alpha beta" / "gamma"
        track.onPartial("delta epsilon", now = 1)
        assertEquals(listOf("gamma Delta", "epsilon"), track.frame().text())
    }

    @Test
    fun `clears after the hold time and not before`() {
        val track = CaptionTrack(holdMillis = 4_000)
        track.onFinal("still here", now = 1_000)
        assertFalse(track.expire(now = 4_999))
        assertTrue(track.expire(now = 5_000))
        assertTrue(track.frame().isEmpty)
        assertFalse(track.expire(now = 9_000))
    }

    @Test
    fun `a word longer than the line gets a line of its own`() {
        val lines = wrap(listOf(CaptionWord("a", true), CaptionWord("supercalifragilistic", true)), maxChars = 10)
        assertEquals(2, lines.size)
    }
}
