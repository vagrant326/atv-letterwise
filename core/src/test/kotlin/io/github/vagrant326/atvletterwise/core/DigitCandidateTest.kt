package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DigitCandidateTest {

    private fun disambiguator(partition: Partition, model: NgramModel = UniformModel) =
        Disambiguator(partition, model)

    @Test
    fun `every group key offers its own digit`() {
        for (partition in listOf(Partition.ITU, Partition.ITU_PL)) {
            for (key in '2'..'9') {
                val candidates = disambiguator(partition).candidates("", key)
                assertEquals(
                    key,
                    candidates.last(),
                    "key $key must be able to type the digit printed on it",
                )
            }
        }
    }

    @Test
    fun `the digit is last whatever the model thinks`() {
        // The whole reason it is appended after the sort rather than ranked with the letters: no
        // n-gram model will ever predict a digit, and a trained one must not be able to promote
        // it above a letter by accident.
        val model = BackoffNgramModel.train(3, sequenceOf("2222222222abababab"))
        for (key in '2'..'9') {
            val candidates = disambiguator(Partition.ITU, model).candidates("2", key)
            assertEquals(key, candidates.last())
            assertEquals(1, candidates.count { it == key })
        }
    }

    @Test
    fun `letters keep the ranks they had`() {
        // This is what keeps the published KSPC honest. The digit costs a press only to whoever
        // types a digit; every letter is exactly as expensive as before it was added.
        val subject = disambiguator(Partition.ITU_PL)
        for (key in '2'..'9') {
            val candidates = subject.candidates("", key)
            val letters = candidates.dropLast(1)
            assertEquals(
                Partition.ITU_PL.symbolsFor(key).toSet(),
                letters.toSet(),
                "nothing but the digit may be added to key $key",
            )
            for (letter in letters) {
                assertTrue(letter.isLetter(), "$letter should be a letter")
            }
        }
    }

    @Test
    fun `a key that carries no letters offers nothing`() {
        // `0` and `1` are space and punctuation, not groups. Returning a bare digit for them
        // would put a candidate in flight on a key that never starts one.
        for (key in listOf('0', '1', '*', '#')) {
            assertTrue(
                disambiguator(Partition.ITU).candidates("", key).isEmpty(),
                "$key is not a group key",
            )
        }
    }

    @Test
    fun `the digit is one press backwards, not a walk to the end`() {
        // Composer wraps, so last is the cheapest place to put something nobody predicts.
        val composer = Composer(disambiguator(Partition.ITU_PL))
        composer.pressGroup('9', context = "")
        composer.previousCandidate()
        assertEquals('9', composer.pending)
    }
}
