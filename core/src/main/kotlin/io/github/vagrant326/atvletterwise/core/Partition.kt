package io.github.vagrant326.atvletterwise.core

/**
 * Assignment of letters to the ambiguous keys. Static by design: remapping between
 * keystrokes would destroy muscle memory and make the candidate strip unreadable.
 */
class Partition(val groups: Map<Char, String>) {

    private val keyOf: Map<Char, Char>

    init {
        require(groups.isNotEmpty()) { "a partition needs at least one group" }
        val assignment = HashMap<Char, Char>()
        for ((key, letters) in groups) {
            require(letters.isNotEmpty()) { "group '$key' is empty" }
            for (letter in letters) {
                val clash = assignment.put(letter, key)
                require(clash == null) { "'$letter' is assigned to both '$clash' and '$key'" }
            }
        }
        keyOf = assignment
    }

    val symbols: Set<Char> get() = keyOf.keys

    fun keyFor(symbol: Char): Char? = keyOf[symbol]

    fun symbolsFor(key: Char): String = groups[key] ?: ""

    /** Mean group size, the crude first-order predictor of how much NEXT work there is. */
    val ambiguity: Double get() = keyOf.size.toDouble() / groups.size

    companion object {
        /**
         * The phone keypad. Almost certainly not KSPC-optimal, but most people already
         * know it from feature phones, so it starts from prior familiarity rather than
         * from nothing. Note that a TV remote's number keys carry no letters, so this is
         * recalled knowledge, not an on-device reference - which is a weaker advantage
         * than it would be on a phone, and weakens the case against an optimised
         * partition accordingly.
         */
        val ITU = Partition(
            mapOf(
                '2' to "abc",
                '3' to "def",
                '4' to "ghi",
                '5' to "jkl",
                '6' to "mno",
                '7' to "pqrs",
                '8' to "tuv",
                '9' to "wxyz",
            )
        )

        /**
         * Polish: 35 letters on the same eight keys, each diacritic sitting in the group of
         * the letter it is a variant of and placed directly after it.
         *
         * They are **not** separate groups. Nine extra letters spread over eight keys would
         * wreck a layout whose whole selling point is that the mapping is already familiar,
         * and the base letter is where a user looks for `ó`.
         *
         * The cost is real and lands unevenly: `9` grows from four letters to six because
         * `ź` and `ż` both hang off `z`, while `4` and `8` are untouched. That skew is why
         * the partition search has to run over this alphabet rather than over 26 letters
         * with a correction factor.
         */
        val ITU_PL = Partition(
            mapOf(
                '2' to "aąbcć",
                '3' to "deęf",
                '4' to "ghi",
                '5' to "jklł",
                '6' to "mnńoó",
                '7' to "pqrsś",
                '8' to "tuv",
                '9' to "wxyzźż",
            )
        )

        /**
         * Every printable mark on a QWERTY keyboard, four to a key, for [Layer.SYMBOLS].
         *
         * All thirty-two rather than the twenty-five the `1` cycle leaves out, so there is one
         * rule to learn: this layer is the whole set and `1` is a shortcut to the seven a
         * television query uses. A layer holding "the leftovers" would be a list nobody could
         * predict the contents of.
         *
         * Grouped by kind, and the grouping is the feature. Nothing is printed on the remote, so
         * the legend on screen is the only place these can be found, and a reader scanning eight
         * cells for a bracket does better with brackets kept together than with any frequency
         * order. Within a group the commonest goes first, so `@`, `!`, `-` and `.` — the marks an
         * address or a password actually needs — are the candidate already in flight.
         *
         * Paired with [UniformModel] rather than a trained one. No corpus here records how often
         * somebody types a brace, and a model that ranked these would be ranking them from a
         * number that had been made up; scoring them all equally makes the walk fall through to
         * this order, which is chosen and can be defended.
         */
        val MARKS = Partition(
            mapOf(
                '2' to ".,;:",
                '3' to "!?'\"",
                '4' to "@#/\\",
                '5' to "$%&*",
                '6' to "()<>",
                '7' to "[]{}",
                '8' to "-_+=",
                '9' to "`~^|",
            )
        )
    }
}
