package com.codedtx.livecaptions.asr

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The chosen spoken language, saved across restarts. A state flow, so the
 * running capture service can swap models the moment the choice changes,
 * without asking for a new screen-share grant.
 */
object LanguageStore {
    private const val PREFS = "settings"
    private const val KEY = "language"

    private val state = MutableStateFlow(Language.ENGLISH)
    private var loaded = false

    val current: StateFlow<Language> get() = state

    fun load(context: Context): Language {
        if (!loaded) {
            state.value = Language.fromId(prefs(context).getString(KEY, null))
            loaded = true
        }
        return state.value
    }

    fun set(context: Context, language: Language) {
        prefs(context).edit { putString(KEY, language.id) }
        loaded = true
        state.value = language
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
