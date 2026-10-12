package com.codedtx.livecaptions.asr

import androidx.annotation.StringRes
import com.codedtx.livecaptions.R
import java.util.Locale

/**
 * A language the app can caption. Each one is a Vosk model bundled under
 * [assetDir], which must match a key of `speechModels` in app/build.gradle.kts.
 */
enum class Language(
    val id: String,
    val assetDir: String,
    @StringRes val label: Int,
    @StringRes val testSentence: Int,
    val locale: Locale,
    /**
     * Line length in visible letters. Devanagari letters are wider than Latin
     * ones at the same text size, so Hindi lines are shorter.
     */
    val maxCharsPerLine: Int,
) {
    ENGLISH("en", "model-en-us", R.string.language_english, R.string.test_sentence_en, Locale.US, 37),
    HINDI("hi", "model-hi", R.string.language_hindi, R.string.test_sentence_hi, Locale("hi", "IN"), 32),
    ;

    companion object {
        fun fromId(id: String?): Language = entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}
