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
}

object KeyBindings {

    fun of(keyCode: Int, longPress: Boolean): Action? = when {
        // Language lives on '*', the phone keypad's spare key. Long press on '1' keeps
        // working as a fallback: plenty of TV remotes have a numpad but no '*' at all, and
        // there is no way to find out from here which kind this is.
        keyCode == KeyEvent.KEYCODE_STAR ->
            if (longPress) Action.ShowLanguages else Action.NextLanguage

        keyCode == KeyEvent.KEYCODE_1 ->
            if (longPress) Action.NextLanguage else Action.Punctuation

        keyCode == KeyEvent.KEYCODE_0 -> Action.Symbol(' ')
        keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 ->
            Action.Group('2' + (keyCode - KeyEvent.KEYCODE_2))

        keyCode == KeyEvent.KEYCODE_DPAD_UP -> Action.NextCandidate
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Action.PreviousCandidate
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Action.Accept
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Action.Backspace
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER -> Action.Enter
        keyCode == KeyEvent.KEYCODE_BACK -> Action.Dismiss
        else -> null
    }

    /** Cycled by the punctuation key. Order is frequency-ish; measure it later. */
    val PUNCTUATION = ".,-':/".toList()
}
