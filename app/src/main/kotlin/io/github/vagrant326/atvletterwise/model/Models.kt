package io.github.vagrant326.atvletterwise.model

import android.content.Context
import androidx.annotation.StringRes
import io.github.vagrant326.atvletterwise.R
import io.github.vagrant326.atvletterwise.core.NgramModel
import io.github.vagrant326.atvletterwise.core.UniformModel

/**
 * [label] is the two-letter tag shown on the strip, where space is scarce; [titleRes] is the
 * language's own name, for the settings list. Names of languages are not translated — Polski
 * is Polski in every locale.
 */
enum class Language(
    val code: String,
    val label: String,
    @StringRes val titleRes: Int,
) {
    PL("pl", "PL", R.string.language_pl),
    EN("en", "EN", R.string.language_en),
}

/**
 * Supplies the language model for a language.
 *
 * Right now it always returns [UniformModel], which means no prediction at all: the keyboard
 * behaves like a phone keypad and every letter costs its position in the group. That is honest
 * rather than convenient - the trained trigram tables do not exist until the corpus tooling
 * does, and a build on this model measures the remote and the interaction design, not
 * LetterWise's KSPC.
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
