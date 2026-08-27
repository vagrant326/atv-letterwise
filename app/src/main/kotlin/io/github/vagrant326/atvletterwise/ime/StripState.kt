package io.github.vagrant326.atvletterwise.ime

import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Layer
import io.github.vagrant326.atvletterwise.core.LetterCase
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language
import io.github.vagrant326.atvletterwise.settings.HintMode

/** Everything the strip draws, so adding a mode does not mean adding another parameter. */
data class StripState(
    val composer: Composer,
    val partition: Partition,
    val language: Language,
    val enabledLanguages: List<Language>,
    val trained: Boolean,
    val hintMode: HintMode,
    val letterCase: LetterCase,
    val showLanguageChooser: Boolean,
    val customKeys: CustomKeys,
    /**
     * Whether there is anywhere to send characters. True whenever a field opened the keyboard;
     * false when the trigger key raised it over an app that never asked for input, where the
     * keys arrive but there is no connection to write through.
     */
    val hasEditor: Boolean,
    /**
     * What the numeric row is for. The letter legend is hidden in the digit layer, because
     * a legend that promises `abc` on a key now producing `2` is worse than no legend at all.
     */
    val layer: Layer,
) {
    /**
     * Which partition the keypad legend should spell out, or null in the digit layer where every
     * key is simply the digit printed on it and the legend has nothing to add.
     */
    val legend: Partition?
        get() = when (layer) {
            Layer.LETTERS -> partition
            Layer.SYMBOLS -> Partition.MARKS
            Layer.DIGITS -> null
        }
}
