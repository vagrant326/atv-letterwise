package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolishPartitionTest {

    private val diacritics = "ąćęłńóśźż"

    @Test
    fun `covers the whole Polish alphabet exactly once`() {
        val expected = (('a'..'z') + diacritics.toList()).toSet()
        assertEquals(expected, Partition.ITU_PL.symbols)
        assertEquals(35, Partition.ITU_PL.symbols.size)
    }

    @Test
    fun `every diacritic shares the key of the letter it varies`() {
        val base = mapOf(
            'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l',
            'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
        )
        for ((accented, plain) in base) {
            assertEquals(
                Partition.ITU_PL.keyFor(plain),
                Partition.ITU_PL.keyFor(accented),
                "$accented should sit with $plain",
            )
        }
    }

    @Test
    fun `keeps the phone keys the user already knows`() {
        for (letter in 'a'..'z') {
            assertEquals(
                Partition.ITU.keyFor(letter),
                Partition.ITU_PL.keyFor(letter),
                "$letter moved between layouts",
            )
        }
    }

    @Test
    fun `the extra letters make the alphabet more ambiguous, unevenly`() {
        assertTrue(Partition.ITU_PL.ambiguity > Partition.ITU.ambiguity)
        // Both z-diacritics hang off the same key, so 9 carries six letters while 4 and 8
        // carry three. The skew is the reason a partition search cannot be a correction
        // factor applied to the 26-letter result.
        assertEquals(6, Partition.ITU_PL.symbolsFor('9').length)
        assertEquals(3, Partition.ITU_PL.symbolsFor('4').length)
    }
}
