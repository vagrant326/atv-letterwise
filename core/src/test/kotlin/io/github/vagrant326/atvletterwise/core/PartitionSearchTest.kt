package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PartitionSearchTest {

    private val corpus = listOf(
        "the quick brown fox jumps over the lazy dog",
        "kung fu panda",
        "bruno mars",
        "daft punk",
        "jackie chan",
        "twoset violin",
    )

    private val model = BackoffNgramModel.train(3, corpus.asSequence())

    private fun search(alphabet: String = "abcdefghijklmnopqrstuvwxyz") =
        PartitionSearch(alphabet, model, corpus)

    /**
     * The whole reduction rests on this: the pair matrix has to produce exactly the NEXT
     * presses the simulator counts. If it drifts, the optimiser cheerfully optimises the wrong
     * thing and the result looks plausible.
     */
    @Test
    fun `cost equals the NEXT presses the simulator counts`() {
        val subject = search()
        val simulator = Simulator(Partition.ITU, Disambiguator(Partition.ITU, model))
        val simulated = corpus.fold(TrialResult.EMPTY) { total, line -> total + simulator.run(line) }

        assertEquals(simulated.nextPresses.toLong(), subject.cost(Partition.ITU))
    }

    @Test
    fun `cost matches the simulator for a different contiguous layout too`() {
        val groups = listOf("abcd", "efg", "hijk", "lmn", "opq", "rst", "uvw", "xyz")
        val partition = Partition(groups.withIndex().associate { (at, letters) ->
            ('2' + at) to letters
        })
        val simulator = Simulator(partition, Disambiguator(partition, model))
        val simulated = corpus.fold(TrialResult.EMPTY) { total, line -> total + simulator.run(line) }

        assertEquals(simulated.nextPresses.toLong(), search().cost(groups))
    }

    @Test
    fun `one letter per key costs nothing`() {
        val singles = "abcdefghijklmnopqrstuvwxyz".map { it.toString() }
        assertEquals(0L, search().cost(singles))
    }

    @Test
    fun `putting the whole alphabet on one key is the worst case`() {
        val subject = search()
        val single = listOf("abcdefghijklmnopqrstuvwxyz")
        assertTrue(subject.cost(single) > subject.cost(Partition.ITU))
    }

    @Test
    fun `the best contiguous split is no worse than ITU`() {
        val subject = search()
        val best = subject.bestContiguous(8)

        assertEquals(8, best.size)
        assertEquals("abcdefghijklmnopqrstuvwxyz", best.joinToString(""))
        assertTrue(
            subject.cost(best) <= subject.cost(Partition.ITU),
            "contiguous best ${subject.cost(best)} vs ITU ${subject.cost(Partition.ITU)}",
        )
    }

    @Test
    fun `scattering letters beats keeping them in alphabetical order`() {
        val subject = search()
        val scattered = subject.bestUnconstrained(8, restarts = 3)

        assertEquals(26, scattered.joinToString("").length)
        assertTrue(
            subject.cost(scattered) <= subject.cost(subject.bestContiguous(8)),
            "unconstrained should not lose to contiguous",
        )
    }

    @Test
    fun `the Polish alphabet is searchable too`() {
        val polish = "abcdefghijklmnopqrstuvwxyząćęłńóśźż"
        val subject = PartitionSearch(polish, model, listOf("piątek prestiż żółw"))

        assertTrue(subject.positions > 0)
        assertEquals(polish.length, subject.bestContiguous(8).joinToString("").length)
    }
}
