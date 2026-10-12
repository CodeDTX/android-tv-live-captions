package com.codedtx.livecaptions.caption

private val WHITESPACE = Regex("\\s+")
private val LONE_I = Regex("\\bi\\b")

/**
 * The small-model recogniser returns lower case with no punctuation. This does
 * the two fixes that read as wrong when missing: a capital at the start of an
 * utterance, and a capital "I". Anything cleverer belongs in a punctuation model.
 */
fun tidy(text: String): String {
    val collapsed = text.trim().replace(WHITESPACE, " ")
    if (collapsed.isEmpty()) return collapsed
    val withI = LONE_I.replace(collapsed, "I")
    return withI.replaceFirstChar { it.uppercaseChar() }
}
