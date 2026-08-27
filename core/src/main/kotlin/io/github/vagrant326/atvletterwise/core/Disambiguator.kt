package io.github.vagrant326.atvletterwise.core

class Disambiguator(
    private val partition: Partition,
    private val model: NgramModel,
    /**
     * Whether the digit printed on a key is offered as its last candidate.
     *
     * True for the letter partitions, where it is the only route to a digit on a remote with no
     * digit button assigned. False for [Partition.MARKS]: digits are their own layer, and
     * offering them here as well would put the same character in two places and turn the symbol
     * legend into a list of two unrelated kinds.
     */
    private val offersDigit: Boolean = true,
) {

    /**
     * Candidates for [key], most likely first. [context] must be the **resolved** text so
     * far rather than the raw key sequence, so that correcting an early character with
     * NEXT improves every prediction after it.
     *
     * Ties break on position within the group, so a run is reproducible and a measured
     * KSPC can be trusted to be the same number tomorrow.
     *
     * The digit printed on the key goes last, after the ranking rather than into it, because
     * no n-gram model will ever predict it: a digit is what the user wants precisely when the
     * letters are wrong. Last is also cheap rather than expensive — the candidate walk wraps,
     * so one press *backwards* reaches it. That is what lets the key's hold be spent on
     * something else, and it is the route that works on a remote where no digit key has been
     * assigned. Letters keep the ranks they had, so nothing measured moves.
     */
    fun candidates(context: String, key: Char): List<Char> {
        val symbols = partition.symbolsFor(key)
        if (symbols.isEmpty()) {
            return emptyList()
        }
        val ranked = symbols.toList().sortedWith(
            compareByDescending<Char> { model.score(context, it) }
                .thenBy { symbols.indexOf(it) }
        )
        return if (offersDigit) ranked + key else ranked
    }
}
