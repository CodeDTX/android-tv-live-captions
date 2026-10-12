package com.codedtx.livecaptions.caption

import org.junit.Assert.assertEquals
import org.junit.Test

class TidyTest {
    @Test
    fun `capitalises the first letter and a lone i`() {
        assertEquals("I think I'm fine", tidy("i think i'm fine"))
        assertEquals("So I said I'll go", tidy("so i said i'll go"))
    }

    @Test
    fun `leaves i inside words alone`() {
        assertEquals("It is in it", tidy("it is in it"))
    }

    @Test
    fun `collapses whitespace and handles empty input`() {
        assertEquals("Two words", tidy("  two   words "))
        assertEquals("", tidy("   "))
    }
}
