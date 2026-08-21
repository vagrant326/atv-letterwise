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
    data object ToggleLanguage : Action
    data object Dismiss : Action
}

object KeyBindings {

    fun of(keyCode: Int, longPress: Boolean): Action? = when {
        keyCode == KeyEvent.KEYCODE_1 -> if (longPress) Action.ToggleLanguage else Action.Punctuation
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
