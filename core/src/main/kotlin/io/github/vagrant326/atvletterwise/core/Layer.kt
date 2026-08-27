package io.github.vagrant326.atvletterwise.core

/**
 * What the numeric row is currently for.
 *
 * Three peers rather than a mode bolted onto a mode. [LETTERS] and [SYMBOLS] are the same
 * machinery with a different partition — a key narrows to a group, the walk picks from it — so
 * the strip, the keypad legend and every gesture work unchanged on either. [DIGITS] is the odd
 * one and always was: nothing is ambiguous, so there is no group, no walk and no composing text.
 *
 * Digits are their own layer rather than passengers in [SYMBOLS]. A mark and a digit are not the
 * same kind of thing, and mixing them would make the symbol legend a list nobody could predict.
 * It is also the only route to `0` and `1`, which are space and punctuation on their own keys
 * and so have no group to hide their digit at the end of.
 *
 * Stickiness differs per layer, and matches how each is used rather than a rule imposed on both:
 * a password wants one `!`, so [SYMBOLS] is spent by one mark; a PIN is a run, so [DIGITS] stays
 * until it is switched off.
 */
enum class Layer {
    LETTERS,
    SYMBOLS,
    DIGITS,
    ;

    fun next(): Layer = entries[(ordinal + 1) % entries.size]
}
