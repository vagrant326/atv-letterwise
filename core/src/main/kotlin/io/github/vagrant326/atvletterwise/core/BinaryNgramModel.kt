package io.github.vagrant326.atvletterwise.core

import java.io.DataInputStream
import java.io.InputStream

/**
 * The trained table, as built by `corpus/train.py`.
 *
 * Dense: one flat count array per order, indexed by symbol arithmetic. The alphabet is 27
 * symbols for English and 36 for Polish, so an order-3 table is a couple of hundred
 * kilobytes — small enough that sparse storage would trade a real cost, hashing on every
 * keystroke, for a saving that does not matter.
 *
 * Format, big-endian:
 *
 *     magic       4 bytes  "LWM1"
 *     order       u8
 *     symbols     u8       S
 *     reserved    u16
 *     alphabet    S x u16  UTF-16 code units, index order
 *     counts      for k in 1..order: S^k x u32
 */
class BinaryNgramModel private constructor(
    override val order: Int,
    private val alphabet: String,
    private val tables: List<IntArray>,
    private val totals: List<IntArray>,
) : NgramModel {

    private val size = alphabet.length

    /** Indexed by char code, so lookup avoids boxing on the keystroke path. */
    private val indexByCode: IntArray = run {
        val highest = alphabet.maxOf { it.code }
        IntArray(highest + 1) { -1 }.also { table ->
            alphabet.forEachIndexed { position, symbol -> table[symbol.code] = position }
        }
    }

    private fun indexOf(symbol: Char): Int =
        if (symbol.code < indexByCode.size) indexByCode[symbol.code] else -1

    override fun score(context: String, candidate: Char): Double {
        val candidateIndex = indexOf(candidate)
        if (candidateIndex < 0) {
            return 0.0
        }
        for (used in order downTo 1) {
            val contextLength = used - 1
            if (contextLength > context.length) {
                continue
            }
            var contextIndex = 0
            var stride = 1
            var usable = true
            for (back in 1..contextLength) {
                val symbol = indexOf(context[context.length - back])
                if (symbol < 0) {
                    usable = false
                    break
                }
                contextIndex += symbol * stride
                stride *= size
            }
            if (!usable) {
                continue
            }
            val table = tables[used - 1]
            val count = table[contextIndex * size + candidateIndex]
            if (count > 0) {
                val total = totals[used - 1][contextIndex]
                return FALLBACK_PENALTY.pow(order - used) * count / total
            }
        }
        return 0.0
    }

    companion object {
        /**
         * Matches `BackoffNgramModel`, so the shipped table and the in-memory model trained
         * during a test rank candidates the same way.
         */
        private const val FALLBACK_PENALTY = 0.05

        private fun Double.pow(n: Int): Double {
            var result = 1.0
            repeat(n) { result *= this }
            return result
        }

        fun read(input: InputStream): BinaryNgramModel {
            val stream = DataInputStream(input.buffered())
            val magic = ByteArray(4)
            stream.readFully(magic)
            require(magic.decodeToString() == "LWM1") {
                "not an n-gram table: magic was ${magic.decodeToString()}"
            }
            val order = stream.readUnsignedByte()
            val size = stream.readUnsignedByte()
            stream.readUnsignedShort()
            require(order in 1..4) { "unsupported order $order" }
            require(size > 1) { "alphabet of $size symbols" }

            val alphabet = StringBuilder(size)
            repeat(size) { alphabet.append(stream.readUnsignedShort().toChar()) }

            val tables = ArrayList<IntArray>(order)
            val totals = ArrayList<IntArray>(order)
            var entries = size
            for (used in 1..order) {
                val table = IntArray(entries)
                for (at in table.indices) {
                    table[at] = stream.readInt()
                }
                tables += table

                // Summed here rather than on every lookup: the divisor depends only on the
                // context, and there are at most S^(order-1) of those.
                val contexts = entries / size
                val sums = IntArray(contexts)
                for (contextIndex in 0 until contexts) {
                    var sum = 0
                    for (symbol in 0 until size) {
                        sum += table[contextIndex * size + symbol]
                    }
                    sums[contextIndex] = if (sum == 0) 1 else sum
                }
                totals += sums
                entries *= size
            }
            return BinaryNgramModel(order, alphabet.toString(), tables, totals)
        }
    }
}
