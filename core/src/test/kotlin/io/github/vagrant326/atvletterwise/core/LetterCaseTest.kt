package io.github.vagrant326.atvletterwise.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LetterCaseTest {

    @Test
    fun `one gesture reaches every state and comes back`() {
        assertEquals(LetterCase.ONCE, LetterCase.LOWER.next())
        assertEquals(LetterCase.LOCKED, LetterCase.ONCE.next())
        assertEquals(
            LetterCase.LOWER,
            LetterCase.LOCKED.next(),
            "a cycle that cannot be left strands the user in a mode the remote does not show",
        )
    }

    @Test
    fun `every Polish letter has a capital and keeps it`() {
        val pairs = mapOf(
            'ą' to 'Ą', 'ć' to 'Ć', 'ę' to 'Ę', 'ł' to 'Ł', 'ń' to 'Ń',
            'ó' to 'Ó', 'ś' to 'Ś', 'ź' to 'Ź', 'ż' to 'Ż',
        )
        for ((lower, upper) in pairs) {
            assertEquals(upper, LetterCase.ONCE.apply(lower), "$lower must reach $upper")
            assertEquals(upper, LetterCase.LOCKED.apply(lower))
        }
    }

    @Test
    fun `the whole partition is reachable in capitals`() {
        val letters = Partition.ITU_PL.symbols
        val capitals = letters.map(LetterCase.LOCKED::apply)
        assertEquals(
            letters.size,
            capitals.toSet().size,
            "two letters sharing one capital would make a password unreachable, not merely odd",
        )
    }

    @Test
    fun `a letter spends the one-off and the lock survives it`() {
        assertEquals(LetterCase.LOWER, LetterCase.ONCE.afterLetter())
        assertEquals(LetterCase.LOCKED, LetterCase.LOCKED.afterLetter())
        assertEquals(LetterCase.LOWER, LetterCase.LOWER.afterLetter())
    }

    @Test
    fun `the digit on a key is not a letter and so cannot spend the one-off`() {
        // Walking down to the digit must not swallow a capital the user has asked for. The
        // service asks `isLetter` rather than comparing cases, which is why this holds.
        for (digit in '2'..'9') {
            assertEquals(digit, LetterCase.LOCKED.apply(digit))
            assertEquals(false, digit.isLetter())
        }
    }

    @Test
    fun `the model never sees a capital`() {
        // The corpus is lowercased before training - see corpus/alphabet.py - so the table's
        // alphabet has no capital in it. `LetterWiseImeService.context` folds the case off for
        // this reason; if it stopped, prediction would back off for three characters after
        // every proper noun's first letter and nothing would fail loudly.
        val partition = Partition.ITU_PL
        for (symbol in partition.symbols) {
            assertEquals(
                symbol,
                symbol.lowercaseChar(),
                "the partition is lower case, so the context fed to the model has to be too",
            )
        }
    }

    @Test
    fun `case does not move a candidate`() {
        // `a` and `A` are the same candidate in the same position: the case is applied where the
        // editor is written to, never inside disambiguation, so no ranking changes.
        val disambiguator = Disambiguator(Partition.ITU, UniformModel)
        val before = disambiguator.candidates("", '2')
        val after = disambiguator.candidates("A", '2')
        assertEquals(before, after)
    }
}
