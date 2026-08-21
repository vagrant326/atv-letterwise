package io.github.vagrant326.atvletterwise.core

/**
 * Disambiguation state for the one unresolved position, kept out of the IME service so it
 * can be tested without a device.
 *
 * It deliberately owns **no text buffer**. Once the caret can move, the editor is the only
 * thing that knows where the text is and what is around it; a buffer here would be a second
 * copy that goes stale the moment anything else touches the field. The caller supplies the
 * context preceding the caret with each press.
 *
 * Accept is optional: pressing the next group key resolves the previous character, as on a
 * phone keypad. Requiring an explicit accept would cost a full point of KSPC.
 */
class Composer(private var disambiguator: Disambiguator) {

    private var candidates: List<Char> = emptyList()
    private var cursor = 0
    private var pendingKey: Char? = null

    /** The unresolved character, or null when there is nothing in flight. */
    val pending: Char? get() = candidates.getOrNull(cursor)

    val hasPending: Boolean get() = candidates.isNotEmpty()

    val alternatives: List<Char> get() = candidates

    val alternativeIndex: Int get() = cursor

    /**
     * @param context the resolved text immediately before the caret. Using resolved
     *   characters rather than the raw key sequence is what makes an early NEXT correction
     *   improve every prediction after it.
     */
    fun pressGroup(key: Char, context: String) {
        candidates = disambiguator.candidates(context, key)
        cursor = 0
        pendingKey = if (candidates.isEmpty()) null else key
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

    fun clearPending() {
        candidates = emptyList()
        cursor = 0
        pendingKey = null
    }

    /**
     * Swaps the language model, re-ranking anything in flight while holding on to the letter
     * the user had selected: switching language mid-word states intent about what comes
     * next, it is not a request to start over.
     */
    fun useDisambiguator(replacement: Disambiguator, context: String) {
        disambiguator = replacement
        val key = pendingKey ?: return
        val selected = pending
        candidates = disambiguator.candidates(context, key)
        cursor = candidates.indexOf(selected).coerceAtLeast(0)
    }
}
