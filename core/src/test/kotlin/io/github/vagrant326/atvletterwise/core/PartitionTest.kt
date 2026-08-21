package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PartitionTest {

    @Test
    fun `ITU partition covers the latin alphabet exactly once`() {
        assertEquals(('a'..'z').toSet(), Partition.ITU.symbols)
        assertEquals(26, Partition.ITU.symbols.size)
    }

    @Test
    fun `ITU partition maps letters to the keys printed on a phone`() {
        assertEquals('2', Partition.ITU.keyFor('c'))
        assertEquals('7', Partition.ITU.keyFor('s'))
        assertEquals('9', Partition.ITU.keyFor('z'))
    }

    @Test
    fun `symbols outside the partition have no key`() {
        assertNull(Partition.ITU.keyFor(' '))
        assertNull(Partition.ITU.keyFor('ż'))
    }

    @Test
    fun `assigning a letter to two groups is rejected`() {
        val clash = assertThrows<IllegalArgumentException> {
            Partition(mapOf('2' to "abc", '3' to "cde"))
        }
        assertEquals("'c' is assigned to both '2' and '3'", clash.message)
    }

    @Test
    fun `mean group size is reported`() {
        assertEquals(26.0 / 8, Partition.ITU.ambiguity, 1e-9)
    }
}
