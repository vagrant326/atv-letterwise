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
    fun `a group press puts the top candidate in flight without resolving it`() {
        val subject = composer()
        subject.pressGroup('2')

        assertEquals("", subject.committedText)
        assertEquals('a', subject.pending)
        assertEquals("a", subject.text)
        assertEquals(listOf('a', 'b', 'c'), subject.alternatives)
    }

    @Test
    fun `the next group press resolves the previous character, so accept is optional`() {
        val subject = composer()
        subject.pressGroup('2')
        subject.pressGroup('3')

        assertEquals("a", subject.committedText)
        assertEquals('d', subject.pending)
        assertEquals("ad", subject.text)
    }

    @Test
    fun `walking candidates wraps in both directions`() {
        val subject = composer()
        subject.pressGroup('2')

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
    fun `space resolves whatever is in flight and never disambiguates`() {
        val subject = composer()
        subject.pressGroup('2')
        subject.nextCandidate()
        subject.pressSymbol(' ')

        assertEquals("b ", subject.committedText)
        assertNull(subject.pending)
    }

    @Test
    fun `backspace drops the character in flight before touching resolved text`() {
        val subject = composer()
        subject.pressGroup('2')
        subject.pressGroup('3')

        assertTrue(subject.backspace())
        assertEquals("a", subject.committedText)
        assertNull(subject.pending)

        assertTrue(subject.backspace())
        assertEquals("", subject.committedText)
    }

    @Test
    fun `backspace on an empty buffer reports nothing to delete`() {
        val subject = composer()

        assertTrue(subject.isEmpty)
        assertFalse(subject.backspace())
    }

    @Test
    fun `resolved characters feed the next prediction`() {
        val model = BackoffNgramModel.train(3, sequenceOf("abababab"))
        val subject = composer(model)

        subject.pressGroup('2')
        assertEquals('a', subject.pending)
        // Having resolved 'a', the same group must now rank 'b' first.
        subject.pressGroup('2')
        assertEquals("a", subject.committedText)
        assertEquals('b', subject.pending)
    }

    @Test
    fun `switching model mid-word keeps the buffer and the chosen letter`() {
        val subject = composer()
        subject.pressGroup('2')
        subject.pressGroup('7')
        subject.nextCandidate()
        val chosen = subject.pending

        subject.useDisambiguator(
            Disambiguator(Partition.ITU, BackoffNgramModel.train(3, sequenceOf("qrs qrs")))
        )

        assertEquals("a", subject.committedText)
        assertEquals(chosen, subject.pending)
    }

    @Test
    fun `clear empties everything`() {
        val subject = composer()
        subject.pressGroup('2')
        subject.pressSymbol(' ')
        subject.pressGroup('9')

        subject.clear()

        assertTrue(subject.isEmpty)
        assertEquals("", subject.text)
    }
}
