package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkLayerTest {

    /** Every printable mark on a US QWERTY keyboard, which is the promise this layer makes. */
    private val qwerty = "`~!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?"

    private val marks = Disambiguator(Partition.MARKS, UniformModel, offersDigit = false)

    @Test
    fun `every QWERTY mark is on some key`() {
        for (mark in qwerty) {
            assertTrue(
                mark in Partition.MARKS.symbols,
                "$mark is unreachable, which is the whole bug this layer fixes",
            )
        }
    }

    @Test
    fun `the layer carries the whole set and nothing else`() {
        assertEquals(qwerty.toSet(), Partition.MARKS.symbols)
    }

    @Test
    fun `nothing costs more than four presses`() {
        for (key in '2'..'9') {
            val group = Partition.MARKS.symbolsFor(key)
            assertTrue(group.length <= 4, "key $key carries ${group.length} marks")
        }
    }

    @Test
    fun `the marks a password and an address need are already in flight`() {
        // Commonest first inside each group, so these cost the group press alone.
        assertEquals('@', marks.candidates("", '4').first())
        assertEquals('!', marks.candidates("", '3').first())
        assertEquals('-', marks.candidates("", '8').first())
        assertEquals('.', marks.candidates("", '2').first())
    }

    @Test
    fun `the mark layer offers no digits`() {
        // Digits are their own layer. Offering them here as well would put the same character in
        // two places and make the legend a list of two unrelated kinds.
        for (key in '2'..'9') {
            val candidates = marks.candidates("", key)
            assertTrue(
                candidates.none { it.isDigit() },
                "key $key offered a digit: $candidates",
            )
            assertEquals(Partition.MARKS.symbolsFor(key).length, candidates.size)
        }
    }

    @Test
    fun `the letter layers still offer theirs`() {
        val letters = Disambiguator(Partition.ITU_PL, UniformModel)
        for (key in '2'..'9') {
            assertEquals(key, letters.candidates("", key).last())
        }
    }

    @Test
    fun `the walk order is fixed rather than predicted`() {
        // UniformModel scores everything equally, so ranking falls through to group order. That
        // is the point: no corpus records how often anybody types a brace, so a ranking here
        // would be invented.
        for (key in '2'..'9') {
            assertEquals(
                Partition.MARKS.symbolsFor(key).toList(),
                marks.candidates("anything at all", key),
            )
        }
    }

    @Test
    fun `the layers cycle and come back`() {
        assertEquals(Layer.SYMBOLS, Layer.LETTERS.next())
        assertEquals(Layer.DIGITS, Layer.SYMBOLS.next())
        assertEquals(Layer.LETTERS, Layer.DIGITS.next())
    }

    @Test
    fun `swapping the partition finishes what was in flight`() {
        // A position in a group that outlived the group changing would come back as a different
        // character than the one the field is showing.
        val composer = Composer(Disambiguator(Partition.ITU_PL, UniformModel))
        composer.pressGroup('2', context = "")
        assertEquals('a', composer.pending)

        composer.clearPending()
        composer.useDisambiguator(marks, context = "")
        assertFalse(composer.hasPending)

        composer.pressGroup('2', context = "")
        assertEquals('.', composer.pending)
    }

    @Test
    fun `no mark shares a key with a letter it could be confused for`() {
        // The two partitions are disjoint, so nothing typed in one layer is reachable in the
        // other and the legend never has to show a character twice.
        assertTrue(Partition.MARKS.symbols.none { it in Partition.ITU_PL.symbols })
    }
}
