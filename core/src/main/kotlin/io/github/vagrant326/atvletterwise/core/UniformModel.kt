package io.github.vagrant326.atvletterwise.core

/**
 * Scores every candidate equally, so ranking falls through to position within the group.
 * That makes the keyboard behave like a phone keypad with no prediction at all.
 *
 * This is the fallback when no trained table is bundled, and it is deliberately not
 * hidden: a build running on this model is measuring the remote, not measuring LetterWise.
 * Anything reporting KSPC has to say which model produced it.
 */
object UniformModel : NgramModel {
    override val order = 1

    override fun score(context: String, candidate: Char): Double = 0.0
}
