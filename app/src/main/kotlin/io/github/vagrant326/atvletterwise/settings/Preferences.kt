package io.github.vagrant326.atvletterwise.settings

import android.content.Context
import android.view.KeyEvent
import io.github.vagrant326.atvletterwise.ime.KeyBindings
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

    /**
     * Which languages the `*` key cycles through, stored per language rather than as a set
     * of allowed combinations — the combinations grow exponentially with the number of
     * languages supported, and each one would need naming.
     *
     * Order follows [Language] declaration order so two presses always land in the same
     * place. Never empty: a keyboard with no language cannot predict anything.
     */
    var enabledLanguages: List<Language>
        get() {
            val stored = store.getStringSet(KEY_ENABLED_LANGUAGES, null)
                ?: return listOf(Language.PL, Language.EN)
            val enabled = Language.entries.filter { it.name in stored }
            return enabled.ifEmpty { listOf(Language.entries.first()) }
        }
        set(value) {
            val kept = value.ifEmpty { listOf(Language.entries.first()) }
            store.edit().putStringSet(KEY_ENABLED_LANGUAGES, kept.map { it.name }.toSet()).apply()
            if (activeLanguage !in kept) {
                activeLanguage = kept.first()
            }
        }

    fun isEnabled(language: Language): Boolean = language in enabledLanguages

    /**
     * Toggles one language. Refuses to remove the last one and reports whether it did
     * anything, so the caller can say why nothing happened rather than looking broken.
     */
    fun toggle(language: Language): Boolean {
        val current = enabledLanguages
        if (language in current) {
            if (current.size == 1) {
                return false
            }
            enabledLanguages = current - language
        } else {
            enabledLanguages = current + language
        }
        return true
    }

    /**
     * The button the user picked for switching language, captured by [KeyCaptureActivity].
     * Defaults to `*`, which is where a phone keypad has its spare key - but plenty of TV
     * remotes put something else there and report a different keycode, so the default is a
     * guess and the capture screen is the answer.
     */
    var languageKeyCode: Int
        get() = store.getInt(KEY_LANGUAGE_KEYCODE, KeyEvent.KEYCODE_STAR)
        set(value) = store.edit().putInt(KEY_LANGUAGE_KEYCODE, value).apply()

    fun clearLanguageKey() {
        store.edit().putInt(KEY_LANGUAGE_KEYCODE, KeyBindings.NO_KEY).apply()
    }

    /** Survives restarts: the language is a mode, and a mode that silently resets is a trap. */
    var activeLanguage: Language
        get() = store.getString(KEY_ACTIVE_LANGUAGE, null)
            ?.let { stored -> Language.entries.firstOrNull { it.name == stored } }
            ?.takeIf { it in enabledLanguages }
            ?: enabledLanguages.first()
        set(value) = store.edit().putString(KEY_ACTIVE_LANGUAGE, value.name).apply()

    private companion object {
        const val NAME = "letterwise"
        const val KEY_HINT_MODE = "hint_mode"
        const val KEY_ENABLED_LANGUAGES = "enabled_languages"
        const val KEY_ACTIVE_LANGUAGE = "active_language"
        const val KEY_LANGUAGE_KEYCODE = "language_keycode"
    }
}
