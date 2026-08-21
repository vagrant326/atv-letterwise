package io.github.vagrant326.atvletterwise.ime

import android.view.KeyEvent

sealed interface Action {
    data class Group(val key: Char) : Action
    data class Symbol(val symbol: Char) : Action
    data object NextCandidate : Action
    data object PreviousCandidate : Action
    data object Accept : Action
    data object Backspace : Action
    data object Enter : Action
    data object Punctuation : Action
    data object NextLanguage : Action
    data object ShowLanguages : Action
    data object Dismiss : Action

    /** Consume the event and do nothing. Held keys repeat; only the first repeat counts. */
    data object Ignore : Action
}

object KeyBindings {

    /**
     * Keys the keyboard needs for itself. Offering any of them as a custom binding would
     * trade one unreachable function for a broken one.
     */
    val RESERVED: Set<Int> = buildSet {
        addAll(KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)
        add(KeyEvent.KEYCODE_DPAD_UP)
        add(KeyEvent.KEYCODE_DPAD_DOWN)
        add(KeyEvent.KEYCODE_DPAD_LEFT)
        add(KeyEvent.KEYCODE_DPAD_RIGHT)
        add(KeyEvent.KEYCODE_DPAD_CENTER)
        add(KeyEvent.KEYCODE_ENTER)
        add(KeyEvent.KEYCODE_BACK)
        add(KeyEvent.KEYCODE_HOME)
    }

    const val NO_KEY = 0

    /**
     * @param repeatCount straight from the [KeyEvent]. A held key repeats every few hundred
     *   milliseconds, so acting on every repeat turns one long press into a spin. Only
     *   `repeatCount == 1` is a long press; later repeats are swallowed.
     * @param languageKeyCode the user's chosen key for switching language, or [NO_KEY].
     *   Needed because remotes disagree about what exists: a key printed `TEXT` where a
     *   phone has `*` reports its own keycode, and nothing can guess it from here.
     */
    fun of(keyCode: Int, repeatCount: Int, languageKeyCode: Int): Action? {
        if (repeatCount > 1) {
            return Action.Ignore
        }
        val longPress = repeatCount == 1

        if (languageKeyCode != NO_KEY && keyCode == languageKeyCode) {
            return if (longPress) Action.ShowLanguages else Action.NextLanguage
        }

        return when {
            // Long press on '1' opens the list rather than cycling. Cycling on a held key
            // is unusable, and this is the fallback route for remotes with no spare key.
            keyCode == KeyEvent.KEYCODE_1 ->
                if (longPress) Action.ShowLanguages else Action.Punctuation

            keyCode == KeyEvent.KEYCODE_0 -> Action.Symbol(' ')
            keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 ->
                Action.Group('2' + (keyCode - KeyEvent.KEYCODE_2))

            keyCode == KeyEvent.KEYCODE_DPAD_UP -> Action.NextCandidate
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Action.PreviousCandidate
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Action.Accept
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Action.Backspace
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ->
                Action.Enter

            keyCode == KeyEvent.KEYCODE_BACK -> Action.Dismiss
            else -> null
        }
    }

    /** Cycled by the punctuation key. Order is frequency-ish; measure it later. */
    val PUNCTUATION = ".,-':/".toList()
}
