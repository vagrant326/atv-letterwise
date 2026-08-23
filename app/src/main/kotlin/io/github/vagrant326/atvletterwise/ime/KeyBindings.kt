package io.github.vagrant326.atvletterwise.ime

import android.view.KeyEvent
import io.github.vagrant326.atvletterwise.core.Simulator

sealed interface Action {
    data class Group(val key: Char) : Action
    data class Symbol(val symbol: Char) : Action

    /**
     * The digit printed on a numeric key, from holding it.
     *
     * @param discardsPending whether the letter in flight came from this same key's short press,
     *   in which case the hold replaces it. Otherwise it came from an earlier key and is
     *   committed as usual.
     */
    data class Digit(val digit: Char, val discardsPending: Boolean) : Action
    data object NextCandidate : Action
    data object PreviousCandidate : Action
    data object CaretLeft : Action
    data object CaretRight : Action
    data object Delete : Action
    data object Enter : Action
    data object NextLanguage : Action
    data object ShowLanguages : Action
    data object Dismiss : Action
    data object ToggleDigits : Action

    /**
     * A key went down whose meaning is not settled yet: released it is a character, held it is
     * [Digit]. Resolved in `LetterWiseImeService.onKeyUp`.
     */
    data object DeferToRelease : Action

    /** Consume the event and do nothing. Held keys repeat; only the first repeat counts. */
    data object Ignore : Action
}

/**
 * Custom bindings, because remotes disagree about which keys exist and about what they
 * report. The user's `TEXT` key sits where a phone has `*` and reports keycode 300, well
 * outside the standard range — nothing in the app could have guessed that.
 */
data class CustomKeys(val language: Int, val delete: Int, val trigger: Int, val digits: Int)

object KeyBindings {

    const val NO_KEY = 0

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

    /**
     * @param repeatCount straight from the [KeyEvent]. A held key repeats every few hundred
     *   milliseconds, so acting on every repeat turns one long press into a spin. Only
     *   `repeatCount == 1` is a long press; later repeats are swallowed.
     * @param digits whether the numeric row is currently typing digits instead of letters.
     */
    fun of(keyCode: Int, repeatCount: Int, custom: CustomKeys, digits: Boolean): Action? {
        if (repeatCount > 1) {
            return Action.Ignore
        }
        val longPress = repeatCount == 1

        if (custom.language != NO_KEY && keyCode == custom.language) {
            return if (longPress) Action.ShowLanguages else Action.NextLanguage
        }
        if (custom.delete != NO_KEY && keyCode == custom.delete) {
            return Action.Delete
        }

        if (custom.digits != NO_KEY && keyCode == custom.digits) {
            return Action.ToggleDigits
        }

        // In digit mode the row is only digits, so holding a key means nothing extra. Swallowed
        // rather than repeated: every other key here does one thing per press.
        if (digits && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return if (longPress) {
                Action.Ignore
            } else {
                Action.Symbol('0' + (keyCode - KeyEvent.KEYCODE_0))
            }
        }

        // Held, a numeric key gives the digit printed on it. That gesture was previously going
        // to waste on all ten - accepting the letter in flight and starting a second one from
        // the same group, two letters from one press - except on `1`, where it opened the
        // language list a second time. Typing a `1` matters more than a duplicate of a key the
        // user has already assigned.
        //
        // `2`-`9` discard the letter in flight, because it came from this same key's short press
        // and the hold says it was never the point. `0` and `1` commit nothing until released,
        // so anything in flight there came from an earlier key and is kept.
        if (longPress && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = '0' + (keyCode - KeyEvent.KEYCODE_0)
            return Action.Digit(digit, discardsPending = digit in '2'..'9')
        }

        // `0` and `1` are the two keys whose short press commits text outright, and a hold
        // arrives as a *second* key-down - so their character has to wait for the release or
        // there is something to un-type. `2`-`9` only set composing text, which [Action.Digit]
        // can drop for free, so they act on the way down and the letter appears as you type.
        if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_1) {
            return Action.DeferToRelease
        }

        return when {
            keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 ->
                Action.Group('2' + (keyCode - KeyEvent.KEYCODE_2))

            // The candidate walk is the hot path - it happens on most characters - so it
            // keeps the d-pad. CHANNEL_UP/DOWN sit next to the numpad on remotes that have
            // one, and are offered as a second way in rather than as the only one.
            keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP ->
                Action.NextCandidate

            keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ->
                Action.PreviousCandidate

            // Left and right move the caret, because that is what a d-pad means everywhere
            // else on the device. Moving right past the character in flight accepts it, so
            // there is no separate accept key to find.
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ->
                if (longPress) Action.Delete else Action.CaretLeft

            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Action.CaretRight

            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ->
                Action.Enter

            keyCode == KeyEvent.KEYCODE_DEL -> Action.Delete
            keyCode == KeyEvent.KEYCODE_BACK -> Action.Dismiss
            else -> null
        }
    }

    /**
     * Cycled by the punctuation key, and shared with the simulator so a measured cost matches
     * the shipped one. `&` earns its place from the real query set — "Bohren & der Club of
     * Gore" is unreachable without it. The order itself is still a guess worth measuring.
     */
    val PUNCTUATION = Simulator.PUNCTUATION
}
