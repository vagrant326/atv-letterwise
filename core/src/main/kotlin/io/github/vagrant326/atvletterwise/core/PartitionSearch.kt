package io.github.vagrant326.atvletterwise.core

/**
 * Searches for the letter-to-key assignment that costs the fewest NEXT presses on a corpus.
 *
 * The reduction that makes this tractable: the number of NEXT presses at a position is the
 * number of letters **in the same group** that the model scores above the letter actually
 * typed. Nothing else about the partition matters. So the whole corpus collapses, once, into
 * a pair matrix — `weights[t][o]` counts the positions where `t` was typed and `o` outranked
 * it — and the cost of any partition is the sum of `weights[t][o]` over pairs that share a
 * group.
 *
 * That turns a fresh simulation per candidate layout, which is thousands of model lookups,
 * into a few hundred integer additions. It is also the honest statement of the problem: this
 * is a graph partition that minimises intra-group weight, and grouping letters that compete
 * in the same contexts is exactly what it penalises.
 *
 * Ties in model score are broken alphabetically here, while the shipped [Disambiguator] breaks
 * them by position within the group. Those agree exactly whenever a group lists its letters in
 * alphabetical order, which covers the ITU layout and every contiguous candidate — so for
 * everything that could ship, this is not an approximation but the same number the simulator
 * produces. It diverges only for scattered layouts, which are measured as a yardstick and are
 * not shipping candidates.
 */
class PartitionSearch(
    private val alphabet: String,
    model: NgramModel,
    corpus: List<String>,
    private val deterministic: Set<Char> = Simulator.DEFAULT_DETERMINISTIC_KEYS.keys,
) {

    private val indexOf: Map<Char, Int> =
        alphabet.withIndex().associate { (position, symbol) -> symbol to position }

    /** `weights[typed][other]`: positions where `other` outranks the letter actually typed. */
    private val weights: Array<IntArray> = Array(alphabet.length) { IntArray(alphabet.length) }

    /** Positions the corpus contributes, so a cost can be reported per character. */
    var positions: Int = 0
        private set

    init {
        val scores = DoubleArray(alphabet.length)
        for (line in corpus) {
            val resolved = StringBuilder()
            for (character in line) {
                if (character in deterministic) {
                    resolved.append(character)
                    continue
                }
                val typed = indexOf[character]
                if (typed == null) {
                    // Not in this alphabet: the caller decides whether that is an error. Here
                    // it simply contributes nothing, since no layout can change its cost.
                    resolved.append(character)
                    continue
                }
                val context = resolved.toString()
                for (other in alphabet.indices) {
                    scores[other] = model.score(context, alphabet[other])
                }
                val typedScore = scores[typed]
                for (other in alphabet.indices) {
                    if (other == typed) {
                        continue
                    }
                    val outranks = scores[other] > typedScore ||
                        (scores[other] == typedScore && other < typed)
                    if (outranks) {
                        weights[typed][other]++
                    }
                }
                positions++
                resolved.append(character)
            }
        }
    }

    /** Total NEXT presses this layout would cost on the corpus. */
    fun cost(groups: List<String>): Long {
        var total = 0L
        for (group in groups) {
            for (typed in group) {
                val row = weights[indexOf.getValue(typed)]
                for (other in group) {
                    if (other != typed) {
                        total += row[indexOf.getValue(other)]
                    }
                }
            }
        }
        return total
    }

    fun cost(partition: Partition): Long =
        cost(partition.groups.entries.sortedBy { it.key }.map { it.value })

    /**
     * Best split of the alphabet into `groupCount` **alphabetically contiguous** blocks,
     * searched exhaustively.
     *
     * Contiguity is not a shortcut, it is the point: a layout where `a` to `d` sit on one key
     * can be recalled from the alphabet, and one that scatters letters cannot be recalled from
     * anything. It also shrinks the space enough to enumerate — choosing 7 split points among
     * 34 gaps is about five million layouts, and each costs a few hundred additions.
     */
    fun bestContiguous(groupCount: Int): List<String> {
        require(groupCount in 2..alphabet.length) { "groupCount out of range" }
        val cuts = IntArray(groupCount - 1)
        var best: List<String>? = null
        var bestCost = Long.MAX_VALUE

        fun walk(depth: Int, from: Int) {
            if (depth == cuts.size) {
                val groups = slice(cuts)
                val candidate = cost(groups)
                if (candidate < bestCost) {
                    bestCost = candidate
                    best = groups
                }
                return
            }
            // Leave room for one letter per remaining group.
            val remaining = cuts.size - depth
            for (cut in from..alphabet.length - remaining - 1) {
                cuts[depth] = cut
                walk(depth + 1, cut + 1)
            }
        }
        walk(0, 1)
        return best ?: error("no partition found")
    }

    private fun slice(cuts: IntArray): List<String> {
        val groups = ArrayList<String>(cuts.size + 1)
        var start = 0
        for (cut in cuts) {
            groups += alphabet.substring(start, cut)
            start = cut
        }
        groups += alphabet.substring(start)
        return groups
    }

    /**
     * Local search with no contiguity constraint: the floor of what any layout could achieve,
     * useful as a yardstick for how much the alphabetical ordering costs. Not a shipping
     * candidate — a scattered layout has to be read off the screen forever.
     */
    fun bestUnconstrained(groupCount: Int, restarts: Int = 8, seed: Long = 1): List<String> {
        val random = java.util.Random(seed)
        var best: List<String>? = null
        var bestCost = Long.MAX_VALUE

        repeat(restarts) {
            val assignment = IntArray(alphabet.length) { it % groupCount }
            for (at in assignment.indices.reversed()) {
                val swap = random.nextInt(at + 1)
                val held = assignment[at]
                assignment[at] = assignment[swap]
                assignment[swap] = held
            }
            var improved = true
            while (improved) {
                improved = false
                for (letter in alphabet.indices) {
                    val current = assignment[letter]
                    var bestGroup = current
                    var bestLocal = cost(groupsOf(assignment))
                    for (group in 0 until groupCount) {
                        if (group == current) {
                            continue
                        }
                        assignment[letter] = group
                        val candidate = cost(groupsOf(assignment))
                        if (candidate < bestLocal) {
                            bestLocal = candidate
                            bestGroup = group
                        }
                    }
                    assignment[letter] = bestGroup
                    if (bestGroup != current) {
                        improved = true
                    }
                }
            }
            val groups = groupsOf(assignment)
            val candidate = cost(groups)
            if (candidate < bestCost) {
                bestCost = candidate
                best = groups
            }
        }
        return best ?: error("no partition found")
    }

    private fun groupsOf(assignment: IntArray): List<String> {
        val groups = Array(assignment.max() + 1) { StringBuilder() }
        for (letter in alphabet.indices) {
            groups[assignment[letter]].append(alphabet[letter])
        }
        return groups.map { it.toString() }.filter { it.isNotEmpty() }
    }
}
