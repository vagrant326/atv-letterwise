package io.github.vagrant326.atvletterwise.core

/**
 * The editing state machine, kept out of the IME service so it can be tested without a
 * device. Holds resolved text plus at most one unresolved position.
 *
 * Accept is optional: pressing the next group key resolves the previous character, as on
 * a phone keypad. Requiring an explicit accept would cost a full point of KSPC.
 */
class Composer(
    private var disambiguator: Disambiguator,
    private val deterministicKeys: Map<Char, Char> = Simulator.DEFAULT_DETERMINISTIC_KEYS,
) {

    private val resolved = StringBuilder()
    private var candidates: List<Char> = emptyList()
    private var cursor = 0
    private var pendingKey: Char? = null

    val committedText: String get() = resolved.toString()

    /** The unresolved character, or null when there is nothing in flight. */
    val pending: Char? get() = candidates.getOrNull(cursor)

    val alternatives: List<Char> get() = candidates

    val alternativeIndex: Int get() = cursor

    val text: String get() = committedText + (pending?.toString() ?: "")

    val isEmpty: Boolean get() = resolved.isEmpty() && candidates.isEmpty()

    fun pressGroup(key: Char) {
        resolvePending()
        candidates = disambiguator.candidates(resolved.toString(), key)
        cursor = 0
        pendingKey = if (candidates.isEmpty()) null else key
    }

    fun pressSymbol(symbol: Char) {
        resolvePending()
        resolved.append(symbol)
    }

    fun nextCandidate() {
        if (candidates.isNotEmpty()) {
            cursor = (cursor + 1) % candidates.size
        }
    }

    fun previousCandidate() {
        if (candidates.isNotEmpty()) {
            cursor = (cursor - 1 + candidates.size) % candidates.size
        }
    }

    fun accept() {
        resolvePending()
    }

    /**
     * Deletes the unresolved character if there is one, otherwise the last resolved
     * character. Returns false when there was nothing left to delete, which is the
     * signal for the caller to dismiss instead.
     */
    fun backspace(): Boolean {
        if (candidates.isNotEmpty()) {
            candidates = emptyList()
            cursor = 0
            pendingKey = null
            return true
        }
        if (resolved.isNotEmpty()) {
            resolved.deleteCharAt(resolved.length - 1)
            return true
        }
        return false
    }

    fun clear() {
        resolved.setLength(0)
        candidates = emptyList()
        cursor = 0
        pendingKey = null
    }

    /**
     * Swaps the language model. Keeps the buffer and re-ranks anything in flight, holding
     * on to the letter the user had selected: switching language mid-word states intent
     * about what comes next, it is not a request to start over.
     */
    fun useDisambiguator(replacement: Disambiguator) {
        disambiguator = replacement
        val key = pendingKey ?: return
        val selected = pending
        candidates = disambiguator.candidates(resolved.toString(), key)
        cursor = candidates.indexOf(selected).coerceAtLeast(0)
    }

    fun isDeterministic(symbol: Char): Boolean = deterministicKeys.containsKey(symbol)

    private fun resolvePending() {
        pending?.let { resolved.append(it) }
        candidates = emptyList()
        cursor = 0
        pendingKey = null
    }
}
