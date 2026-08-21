package io.github.vagrant326.atvletterwise.core

data class TrialResult(
    val characters: Int,
    val groupPresses: Int,
    val nextPresses: Int,
    val acceptPresses: Int,
    val deterministicPresses: Int,
    val visualChecks: Int,
) {
    val totalPresses: Int
        get() = groupPresses + nextPresses + acceptPresses + deterministicPresses

    val kspc: Double
        get() = if (characters == 0) 0.0 else totalPresses.toDouble() / characters

    /**
     * Fraction of characters that force the user to read the strip before deciding the
     * next press. For prefix disambiguation this does not decay with practice: however
     * expert the user, a prediction still has to be checked because the model can be
     * wrong. That is the metric on which this method is most likely to lose.
     */
    val visualCheckRate: Double
        get() = if (characters == 0) 0.0 else visualChecks.toDouble() / characters

    operator fun plus(other: TrialResult) = TrialResult(
        characters = characters + other.characters,
        groupPresses = groupPresses + other.groupPresses,
        nextPresses = nextPresses + other.nextPresses,
        acceptPresses = acceptPresses + other.acceptPresses,
        deterministicPresses = deterministicPresses + other.deterministicPresses,
        visualChecks = visualChecks + other.visualChecks,
    )

    companion object {
        val EMPTY = TrialResult(0, 0, 0, 0, 0, 0)
    }
}

/**
 * Exact keystroke count for entering a target string, NEXT presses included.
 *
 * This is the same disambiguation path the IME uses, which is the whole reason [core] has
 * no Android dependencies: a KSPC measured here has to be the KSPC that ships.
 */
class Simulator(
    private val partition: Partition,
    private val disambiguator: Disambiguator,
    private val deterministicKeys: Map<Char, Char> = DEFAULT_DETERMINISTIC_KEYS,
    private val requireAccept: Boolean = false,
) {

    fun run(target: String): TrialResult {
        var groupPresses = 0
        var nextPresses = 0
        var acceptPresses = 0
        var deterministicPresses = 0
        var visualChecks = 0
        val resolved = StringBuilder()

        for (character in target) {
            if (deterministicKeys.containsKey(character)) {
                deterministicPresses++
                resolved.append(character)
                continue
            }

            val key = partition.keyFor(character)
                ?: throw IllegalArgumentException("'$character' is not typable under this partition")
            groupPresses++

            val ranked = disambiguator.candidates(resolved.toString(), key)
            val rank = ranked.indexOf(character)
            check(rank >= 0) { "'$character' missing from its own group's candidates" }
            nextPresses += rank

            if (ranked.size > 1) {
                visualChecks++
                if (requireAccept) {
                    acceptPresses++
                }
            }

            resolved.append(character)
        }

        return TrialResult(
            characters = target.length,
            groupPresses = groupPresses,
            nextPresses = nextPresses,
            acceptPresses = acceptPresses,
            deterministicPresses = deterministicPresses,
            visualChecks = visualChecks,
        )
    }

    fun run(targets: Iterable<String>): TrialResult =
        targets.fold(TrialResult.EMPTY) { total, target -> total + run(target) }

    companion object {
        /**
         * Space gets its own key. Word boundaries are where the n-gram model is weakest,
         * so keeping them out of the ambiguous set helps accuracy as well as KSPC.
         */
        val DEFAULT_DETERMINISTIC_KEYS = mapOf(' ' to '0')
    }
}
