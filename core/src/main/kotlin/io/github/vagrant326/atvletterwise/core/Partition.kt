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
         * The phone keypad. Almost certainly not KSPC-optimal, but it is printed on the
         * remote and most people already know it, so its learning cost is near zero.
         * That is worth real KSPC and makes it the partition to beat, not a placeholder.
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
    }
}
