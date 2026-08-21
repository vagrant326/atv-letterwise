package io.github.vagrant326.atvletterwise.settings

import android.content.Context
import io.github.vagrant326.atvletterwise.model.Language

/** How much of the key mapping the strip spells out. */
enum class HintMode(val label: String, val description: String) {
    KEYPAD(
        "Keypad",
        "Phone layout with letters on every key. Clearest, and the tallest.",
    ),
    INLINE(
        "One line",
        "Compact 2:abc list on a single row.",
    ),
    OFF(
        "Off",
        "Nothing. The remote's keys have no letters on them, so nothing will remind you.",
    ),
    ;

    fun next(): HintMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Which languages the `*` key cycles through.
 *
 * Cycling only works while the list is short — the user has to be able to predict where two
 * presses land. At eighteen languages a cycling key is useless and the long-press list
 * becomes the only usable route, so the set is the user's choice rather than everything the
 * app happens to support.
 */
enum class LanguageSet(val label: String, val languages: List<Language>) {
    PL_EN("PL + EN", listOf(Language.PL, Language.EN)),
    PL_ONLY("PL only", listOf(Language.PL)),
    EN_ONLY("EN only", listOf(Language.EN)),
    ;

    fun next(): LanguageSet = entries[(ordinal + 1) % entries.size]
}

class Preferences(context: Context) {

    private val store = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Defaults to the keypad. The remote's number keys carry no letters, so a new user has
     * no reference anywhere else.
     */
    var hintMode: HintMode
        get() = store.getString(KEY_HINT_MODE, null)
            ?.let { stored -> HintMode.entries.firstOrNull { it.name == stored } }
            ?: HintMode.KEYPAD
        set(value) = store.edit().putString(KEY_HINT_MODE, value.name).apply()

    var languageSet: LanguageSet
        get() = store.getString(KEY_LANGUAGE_SET, null)
            ?.let { stored -> LanguageSet.entries.firstOrNull { it.name == stored } }
            ?: LanguageSet.PL_EN
        set(value) {
            store.edit().putString(KEY_LANGUAGE_SET, value.name).apply()
            if (activeLanguage !in value.languages) {
                activeLanguage = value.languages.first()
            }
        }

    /** Survives restarts: the language is a mode, and a mode that silently resets is a trap. */
    var activeLanguage: Language
        get() = store.getString(KEY_ACTIVE_LANGUAGE, null)
            ?.let { stored -> Language.entries.firstOrNull { it.name == stored } }
            ?.takeIf { it in languageSet.languages }
            ?: languageSet.languages.first()
        set(value) = store.edit().putString(KEY_ACTIVE_LANGUAGE, value.name).apply()

    private companion object {
        const val NAME = "letterwise"
        const val KEY_HINT_MODE = "hint_mode"
        const val KEY_LANGUAGE_SET = "language_set"
        const val KEY_ACTIVE_LANGUAGE = "active_language"
    }
}
