package io.github.vagrant326.atvletterwise.core

/**
 * Ranks candidate characters against the preceding resolved text. Scores are comparable
 * only within a single context, never across contexts.
 */
interface NgramModel {
    val order: Int

    fun score(context: String, candidate: Char): Double
}

/**
 * Character n-gram model with stupid back-off: try the longest context available, fall
 * back one character at a time, penalising each fallback. Katz or Kneser-Ney would be
 * more principled, but back-off is likely enough at this scale and that is a measurement
 * question rather than a matter of taste.
 */
class BackoffNgramModel private constructor(
    override val order: Int,
    private val rows: Map<String, Row>,
) : NgramModel {

    private class Row(val counts: Map<Char, Int>, val total: Int)

    override fun score(context: String, candidate: Char): Double {
        for (length in (order - 1) downTo 0) {
            if (length > context.length) {
                continue
            }
            val row = rows[context.takeLast(length)] ?: continue
            val count = row.counts[candidate] ?: 0
            if (count > 0) {
                val penalty = FALLBACK_PENALTY.pow(order - 1 - length)
                return penalty * count / row.total
            }
        }
        return 0.0
    }

    companion object {
        private const val FALLBACK_PENALTY = 0.05

        private fun Double.pow(n: Int): Double {
            var result = 1.0
            repeat(n) { result *= this }
            return result
        }

        fun train(order: Int, lines: Sequence<String>): BackoffNgramModel {
            require(order >= 1) { "order must be at least 1" }
            val counts = HashMap<String, HashMap<Char, Int>>()
            for (line in lines) {
                for (i in line.indices) {
                    for (contextLength in 0 until order) {
                        if (i - contextLength < 0) {
                            break
                        }
                        val context = line.substring(i - contextLength, i)
                        counts.getOrPut(context) { HashMap() }.merge(line[i], 1, Int::plus)
                    }
                }
            }
            val rows = counts.mapValues { (_, row) -> Row(row, row.values.sum()) }
            return BackoffNgramModel(order, rows)
        }
    }
}
