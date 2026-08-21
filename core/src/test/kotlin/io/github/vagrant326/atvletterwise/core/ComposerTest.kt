package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun composer(model: NgramModel = UniformModel) =
    Composer(Disambiguator(Partition.ITU, model))

class ComposerTest {

    @Test
    fun `a group press puts the top candidate in flight`() {
        val subject = composer()
        subject.pressGroup('2', context = "")

        assertEquals('a', subject.pending)
        assertTrue(subject.hasPending)
        assertEquals(listOf('a', 'b', 'c'), subject.alternatives)
    }

    @Test
    fun `walking candidates wraps in both directions`() {
        val subject = composer()
        subject.pressGroup('2', context = "")

        subject.nextCandidate()
        assertEquals('b', subject.pending)
        subject.previousCandidate()
        assertEquals('a', subject.pending)
        subject.previousCandidate()
        assertEquals('c', subject.pending)
        subject.nextCandidate()
        assertEquals('a', subject.pending)
    }

    @Test
    fun `nothing is in flight before a group press or after clearing`() {
        val subject = composer()

        assertNull(subject.pending)
        assertFalse(subject.hasPending)

        subject.pressGroup('5', context = "")
        assertTrue(subject.hasPending)

        subject.clearPending()
        assertFalse(subject.hasPending)
        assertNull(subject.pending)
    }

    @Test
    fun `context supplied by the caller drives the ranking`() {
        val model = BackoffNgramModel.train(3, sequenceOf("abababab"))
        val subject = composer(model)

        // Group '2' holds a, b and c. With nothing before the caret, 'a' and 'b' tie and the
        // group order decides. Having resolved 'a', the model must rank 'b' first.
        subject.pressGroup('2', context = "")
        assertEquals('a', subject.pending)

        subject.pressGroup('2', context = "a")
        assertEquals('b', subject.pending)
    }

    @Test
    fun `a group press replaces whatever was in flight`() {
        val subject = composer()
        subject.pressGroup('2', context = "")
        subject.nextCandidate()
        assertEquals('b', subject.pending)

        subject.pressGroup('7', context = "b")
        assertEquals(listOf('p', 'q', 'r', 's'), subject.alternatives)
        assertEquals('p', subject.pending)
    }

    @Test
    fun `switching model keeps the chosen letter`() {
        val subject = composer()
        subject.pressGroup('7', context = "")
        subject.nextCandidate()
        val chosen = subject.pending

        subject.useDisambiguator(
            Disambiguator(Partition.ITU, BackoffNgramModel.train(3, sequenceOf("qrs qrs"))),
            context = "",
        )

        assertEquals(chosen, subject.pending)
    }

    @Test
    fun `switching model with nothing in flight is harmless`() {
        val subject = composer()

        subject.useDisambiguator(Disambiguator(Partition.ITU, UniformModel), context = "")

        assertFalse(subject.hasPending)
    }
}
