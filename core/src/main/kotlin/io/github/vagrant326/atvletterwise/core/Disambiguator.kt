package io.github.vagrant326.atvletterwise.core

class Disambiguator(
    private val partition: Partition,
    private val model: NgramModel,
) {

    /**
     * Candidates for [key], most likely first. [context] must be the **resolved** text so
     * far rather than the raw key sequence, so that correcting an early character with
     * NEXT improves every prediction after it.
     *
     * Ties break on position within the group, so a run is reproducible and a measured
     * KSPC can be trusted to be the same number tomorrow.
     */
    fun candidates(context: String, key: Char): List<Char> {
        val symbols = partition.symbolsFor(key)
        return symbols.toList().sortedWith(
            compareByDescending<Char> { model.score(context, it) }
                .thenBy { symbols.indexOf(it) }
        )
    }
}
