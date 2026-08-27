package io.github.vagrant326.atvletterwise.core

/**
 * Whether the next letter is a capital, and for how long.
 *
 * One gesture and three states rather than a key for the one-off and another for the lock:
 * `KeyBindings.RESERVED` is the whole numeric row and the whole d-pad, and there is no second
 * button to spend. The order is a measurement rather than a taste — isolated capitals, being
 * sentence openings and proper nouns, outnumber runs of them in both alphabets — so the first
 * press buys the common case and the lock costs one more.
 *
 * [ONCE] cannot be forgotten, because it spends itself on the letter it capitalised. Nothing is
 * printed on this remote, so a mode the user cannot see and did not mean to be in produces text
 * that reads as a typo rather than as a mode.
 *
 * **This is presentation and nothing else.** The disambiguator, the model and the candidate walk
 * all stay in lower case: `a` and `A` are the same candidate in the same position, and the case
 * is only what the editor is told. The corpus is lowercased at training time — see
 * `corpus/alphabet.py` — so a capital reaching the model would be a symbol outside its alphabet.
 * `LetterWiseImeService.context` folds the case back off for exactly that reason.
 */
enum class LetterCase {

    LOWER,

    /** The next letter, and then back to [LOWER] on its own. */
    ONCE,

    /** Every letter until switched off. */
    LOCKED,
    ;

    fun next(): LetterCase = entries[(ordinal + 1) % entries.size]

    /**
     * Digits and marks come back unchanged, which is why this is applied to everything the
     * keyboard writes rather than only to letters: a caller that has to ask whether a character
     * is a letter first is a caller that will eventually forget to. That matters more here than
     * in a keypad keyboard, because the digit printed on a key is one of its candidates.
     *
     * `uppercaseChar` rather than `uppercase`: the locale-aware version returns a *string*, to
     * cover the languages where one letter becomes two — none of which occur in either alphabet
     * here — and on a Turkish device it would make `İ` out of `i`. The whole Polish set maps one
     * to one, `ł` to `Ł` included.
     */
    fun apply(character: Char): Char = if (this == LOWER) character else character.uppercaseChar()

    /**
     * What the state becomes once a capital has actually reached the field.
     *
     * Only a letter spends [ONCE]. A space, a mark or a digit cannot be capitalised, so consuming
     * the state on one would quietly take back the capital the user asked for.
     */
    fun afterLetter(): LetterCase = if (this == ONCE) LOWER else this
}
