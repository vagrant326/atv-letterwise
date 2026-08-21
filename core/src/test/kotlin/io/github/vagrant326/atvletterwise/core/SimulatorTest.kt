package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ranks by a fixed preference order, ignoring context. Makes press counts hand-checkable. */
private class PreferenceModel(private val preference: String) : NgramModel {
    override val order = 1

    override fun score(context: String, candidate: Char): Double {
        val position = preference.indexOf(candidate)
        return if (position < 0) Double.NEGATIVE_INFINITY else -position.toDouble()
    }
}

private const val ALPHABETICAL = "abcdefghijklmnopqrstuvwxyz"
private val REVERSED = ALPHABETICAL.reversed()

private fun simulator(model: NgramModel, requireAccept: Boolean = false) = Simulator(
    partition = Partition.ITU,
    disambiguator = Disambiguator(Partition.ITU, model),
    requireAccept = requireAccept,
)

class SimulatorTest {

    @Test
    fun `press counts match a hand-computed example`() {
        // Group '2' holds "abc" and the model prefers a, then b, then c.
        // c costs two NEXT presses, a costs none, b costs one.
        val result = simulator(PreferenceModel(ALPHABETICAL)).run("cab")

        assertEquals(3, result.characters)
        assertEquals(3, result.groupPresses)
        assertEquals(3, result.nextPresses)
        assertEquals(0, result.deterministicPresses)
        assertEquals(6, result.totalPresses)
        assertEquals(2.0, result.kspc, 1e-9)
    }

    @Test
    fun `space costs a single press and never disambiguates`() {
        val result = simulator(PreferenceModel(ALPHABETICAL)).run("a b")

        assertEquals(1, result.deterministicPresses)
        assertEquals(2, result.groupPresses)
        assertEquals(1, result.nextPresses)
        assertEquals(2, result.visualChecks)
        assertEquals(4.0 / 3, result.kspc, 1e-9)
    }

    @Test
    fun `a perfect ranking costs one press per character`() {
        val result = simulator(PreferenceModel(ALPHABETICAL)).run("adgj")

        assertEquals(0, result.nextPresses)
        assertEquals(1.0, result.kspc, 1e-9)
    }

    @Test
    fun `an adversarial ranking costs the full group walk`() {
        val result = simulator(PreferenceModel(REVERSED)).run("aaa")

        // Group "abc" reversed ranks c, b, a, so every 'a' walks two candidates.
        assertEquals(6, result.nextPresses)
        assertEquals(3.0, result.kspc, 1e-9)
    }

    @Test
    fun `mandatory accept costs one extra press per ambiguous character`() {
        val optional = simulator(PreferenceModel(ALPHABETICAL)).run("adgj")
        val mandatory = simulator(PreferenceModel(ALPHABETICAL), requireAccept = true).run("adgj")

        assertEquals(0, optional.acceptPresses)
        assertEquals(4, mandatory.acceptPresses)
        // The reason accept is optional by default: it is worth a full point of KSPC.
        assertEquals(1.0, mandatory.kspc - optional.kspc, 1e-9)
    }

    @Test
    fun `context uses resolved characters so a trained model pays off mid-word`() {
        val model = BackoffNgramModel.train(3, sequenceOf("abababab"))
        val disambiguator = Disambiguator(Partition.ITU, model)

        // 'a' and 'b' share group '2'. Having resolved "a", the model must rank 'b' first.
        assertEquals('b', disambiguator.candidates("a", '2').first())

        val result = Simulator(Partition.ITU, disambiguator).run("ab")
        assertEquals(0, result.nextPresses)
        assertEquals(1.0, result.kspc, 1e-9)
    }

    @Test
    fun `a trained model beats an adversarial ranking on its own corpus`() {
        val corpus = List(50) { "the quick brown fox jumps over the lazy dog" }
        val trained = BackoffNgramModel.train(3, corpus.asSequence())

        val trainedResult = simulator(trained).run("the quick brown fox")
        val adversarialResult = simulator(PreferenceModel(REVERSED)).run("the quick brown fox")

        assertTrue(
            trainedResult.kspc < adversarialResult.kspc,
            "trained ${trainedResult.kspc} should beat adversarial ${adversarialResult.kspc}",
        )
        assertTrue(trainedResult.kspc < 1.5, "trained KSPC was ${trainedResult.kspc}")
    }

    @Test
    fun `aggregating trials sums presses and characters`() {
        val subject = simulator(PreferenceModel(ALPHABETICAL))
        val combined = subject.run(listOf("cab", "cab"))
        val single = subject.run("cab")

        assertEquals(single.totalPresses * 2, combined.totalPresses)
        assertEquals(single.kspc, combined.kspc, 1e-9)
    }

    @Test
    fun `an untypable character is rejected rather than silently skipped`() {
        val failure = assertThrows<IllegalArgumentException> {
            simulator(PreferenceModel(ALPHABETICAL)).run("żółw")
        }
        assertTrue(failure.message!!.contains("not typable"))
    }
}
