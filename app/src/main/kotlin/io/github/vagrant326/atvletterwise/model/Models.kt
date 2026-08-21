package io.github.vagrant326.atvletterwise.model

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import io.github.vagrant326.atvletterwise.R
import io.github.vagrant326.atvletterwise.core.BinaryNgramModel
import io.github.vagrant326.atvletterwise.core.NgramModel
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.core.UniformModel

/**
 * [label] is the two-letter tag shown on the strip, where space is scarce; [titleRes] is the
 * language's own name, for the settings list. Names of languages are not translated — Polski
 * is Polski in every locale.
 *
 * [partition] differs between the two: Polish carries nine more letters on the same eight
 * keys. They share a keypad, not an alphabet.
 */
enum class Language(
    val code: String,
    val label: String,
    @StringRes val titleRes: Int,
    val partition: Partition,
) {
    PL("pl", "PL", R.string.language_pl, Partition.ITU_PL),
    EN("en", "EN", R.string.language_en, Partition.ITU),
}

/**
 * Loads the trained table for a language, once, and keeps it.
 *
 * Falls back to [UniformModel] — no prediction at all, alphabetical order within the group —
 * if the asset is missing or unreadable. The fallback is reported on the strip rather than
 * hidden, because a keyboard running on it is a phone keypad and any KSPC measured from it
 * says nothing about LetterWise.
 */
class ModelRepository(private val context: Context) {

    private val loaded = HashMap<Language, NgramModel>()

    fun modelFor(language: Language): NgramModel = loaded.getOrPut(language) {
        val name = assetName(language)
        runCatching {
            context.assets.open(name).use { BinaryNgramModel.read(it) }
        }.getOrElse { failure ->
            Log.w(TAG, "no usable model in $name, falling back to uniform", failure)
            UniformModel
        }
    }

    fun isTrained(language: Language): Boolean = modelFor(language) !== UniformModel

    private fun assetName(language: Language) = "trigrams-${language.code}.bin"

    private companion object {
        const val TAG = "LetterWise"
    }
}
