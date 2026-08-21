package io.github.vagrant326.atvletterwise.settings

import android.content.Context
import androidx.annotation.StringRes
import io.github.vagrant326.atvletterwise.R
import io.github.vagrant326.atvletterwise.ime.CustomKeys
import io.github.vagrant326.atvletterwise.ime.KeyBindings
import io.github.vagrant326.atvletterwise.model.Language

/** How much of the key mapping the strip spells out. */
enum class HintMode(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    KEYPAD(R.string.hint_keypad, R.string.hint_keypad_description),
    INLINE(R.string.hint_inline, R.string.hint_inline_description),
    OFF(R.string.hint_off, R.string.hint_off_description),
    ;

    fun next(): HintMode = entries[(ordinal + 1) % entries.size]
}

/** A function the user can put on a button of their choosing. */
enum class Binding(
    @StringRes val titleRes: Int,
    @StringRes val promptRes: Int,
    @StringRes val fallbackRes: Int,
) {
    LANGUAGE(
        R.string.binding_language,
        R.string.binding_language_prompt,
        R.string.binding_language_fallback,
    ),
    DELETE(
        R.string.binding_delete,
        R.string.binding_delete_prompt,
        R.string.binding_delete_fallback,
    ),

    /**
     * Experimental. This is the only binding the keyboard listens for while it is hidden, which
     * is the mechanism that once left a TV unnavigable — so it is one key, chosen by the user,
     * and unassigned by default. Reserved keys cannot be picked, so the d-pad is never at risk.
     */
    TRIGGER(
        R.string.binding_trigger,
        R.string.binding_trigger_prompt,
        R.string.binding_trigger_fallback,
    ),
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
     * Which languages the language key cycles through, stored per language rather than as a
     * set of allowed combinations — the combinations grow exponentially with the number of
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
     * The button the user picked for switching language. There is no sensible default: the key
     * where a phone has `*` may not exist, and where it does it may report anything.
     */
    var languageKeyCode: Int
        get() = store.getInt(KEY_LANGUAGE_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_LANGUAGE_KEYCODE, value).apply()

    /**
     * Delete needs a key of its own now that left and right move the caret. There is no
     * default that exists on every remote, so `DEL` is wired in unconditionally and holding
     * left works as the fallback until the user assigns something reachable.
     */
    var deleteKeyCode: Int
        get() = store.getInt(KEY_DELETE_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_DELETE_KEYCODE, value).apply()

    /** Unassigned by default: nothing is consumed while the keyboard is hidden until asked. */
    var triggerKeyCode: Int
        get() = store.getInt(KEY_TRIGGER_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_TRIGGER_KEYCODE, value).apply()

    val customKeys: CustomKeys
        get() = CustomKeys(languageKeyCode, deleteKeyCode, triggerKeyCode)

    fun keyCodeFor(binding: Binding): Int = when (binding) {
        Binding.LANGUAGE -> languageKeyCode
        Binding.DELETE -> deleteKeyCode
        Binding.TRIGGER -> triggerKeyCode
    }

    fun assign(binding: Binding, keyCode: Int) {
        when (binding) {
            Binding.LANGUAGE -> languageKeyCode = keyCode
            Binding.DELETE -> deleteKeyCode = keyCode
            Binding.TRIGGER -> triggerKeyCode = keyCode
        }
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
        const val KEY_DELETE_KEYCODE = "delete_keycode"
        const val KEY_TRIGGER_KEYCODE = "trigger_keycode"
    }
}
