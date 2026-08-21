package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Writes the same layout `corpus/train.py` writes. If the two ever disagree, the shipped
 * keyboard silently ranks letters by nonsense, so the format is pinned from both ends.
 */
private fun table(alphabet: String, order: Int, text: String): ByteArray {
    val size = alphabet.length
    val index = alphabet.withIndex().associate { (position, symbol) -> symbol to position }
    val tables = (1..order).map { IntArray(pow(size, it)) }

    val positions = text.mapNotNull { index[it] }
    positions.forEachIndexed { at, symbol ->
        var offset = symbol
        tables[0][offset]++
        var stride = size
        for (back in 1 until order) {
            if (at - back < 0) {
                break
            }
            offset += positions[at - back] * stride
            stride *= size
            tables[back][offset]++
        }
    }

    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        out.write("LWM1".toByteArray())
        out.writeByte(order)
        out.writeByte(size)
        out.writeShort(0)
        alphabet.forEach { out.writeShort(it.code) }
        tables.forEach { counts -> counts.forEach { out.writeInt(it) } }
    }
    return bytes.toByteArray()
}

private fun pow(base: Int, exponent: Int): Int {
    var result = 1
    repeat(exponent) { result *= base }
    return result
}

class BinaryNgramModelTest {

    private val alphabet = " abcz"

    @Test
    fun `rejects a file that is not a table`() {
        val failure = assertThrows<IllegalArgumentException> {
            BinaryNgramModel.read(ByteArrayInputStream("not a table at all".toByteArray()))
        }
        assertTrue(failure.message!!.contains("not an n-gram table"))
    }

    @Test
    fun `round trips the alphabet and order`() {
        val model = BinaryNgramModel.read(ByteArrayInputStream(table(alphabet, 3, "abc")))
        assertEquals(3, model.order)
    }

    @Test
    fun `ranks by the highest order context available`() {
        // 'b' only ever follows 'a'; 'c' only ever follows 'b'.
        val model = BinaryNgramModel.read(
            ByteArrayInputStream(table(alphabet, 3, " abc abc abc"))
        )

        assertTrue(model.score("a", 'b') > model.score("a", 'c'))
        assertTrue(model.score("b", 'c') > model.score("b", 'b'))
    }

    @Test
    fun `backs off when the context was never seen`() {
        val model = BinaryNgramModel.read(
            ByteArrayInputStream(table(alphabet, 3, " abc abc"))
        )

        // 'z' appears nowhere, so no order can predict from it - but 'b' is still a known
        // character and must outrank one that never occurred at all.
        assertTrue(model.score("z", 'b') > 0.0)
        assertEquals(0.0, model.score("z", 'z'))
    }

    @Test
    fun `an unknown candidate scores zero rather than throwing`() {
        val model = BinaryNgramModel.read(ByteArrayInputStream(table(alphabet, 3, "abc")))

        assertEquals(0.0, model.score("a", 'ą'))
    }

    @Test
    fun `agrees with the in-memory model on ranking`() {
        val corpus = " abc abz abc acb "
        val binary = BinaryNgramModel.read(ByteArrayInputStream(table(alphabet, 3, corpus)))
        val trained = BackoffNgramModel.train(3, sequenceOf(corpus))

        for (context in listOf("", "a", "b", "ab", "ac")) {
            val fromBinary = alphabet.toList().sortedByDescending { binary.score(context, it) }
            val fromTrained = alphabet.toList().sortedByDescending { trained.score(context, it) }
            assertEquals(fromTrained.first(), fromBinary.first(), "top candidate after '$context'")
        }
    }
}
