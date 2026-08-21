package io.github.vagrant326.atvletterwise.settings

import android.content.Context

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

    private companion object {
        const val NAME = "letterwise"
        const val KEY_HINT_MODE = "hint_mode"
    }
}
