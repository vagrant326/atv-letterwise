package io.github.vagrant326.atvletterwise.model

import android.content.Context
import io.github.vagrant326.atvletterwise.core.NgramModel
import io.github.vagrant326.atvletterwise.core.UniformModel

enum class Language(val code: String, val label: String, val title: String) {
    PL("pl", "PL", "Polski"),
    EN("en", "EN", "English"),
}

/**
 * Supplies the language model for a language.
 *
 * Right now it always returns [UniformModel], which means no prediction at all: the
 * keyboard behaves like a phone keypad and every letter costs its position in the group.
 * That is honest rather than convenient - the trained trigram tables do not exist until
 * the corpus tooling does, and a build on this model measures the remote and the
 * interaction design, not LetterWise's KSPC.
 */
class ModelRepository(private val context: Context) {

    fun modelFor(language: Language): NgramModel {
        val asset = "trigrams-${language.code}.bin"
        if (!hasAsset(asset)) {
            return UniformModel
        }
        // Loading the memory-mapped table lands here once the format is defined in P1.
        return UniformModel
    }

    fun isTrained(language: Language): Boolean = hasAsset("trigrams-${language.code}.bin")

    private fun hasAsset(name: String): Boolean =
        runCatching { context.assets.open(name).close() }.isSuccess
}
