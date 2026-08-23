package io.github.vagrant326.atvletterwise.ime

import io.github.vagrant326.atvletterwise.core.Composer
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
    val showLanguageChooser: Boolean,
    val customKeys: CustomKeys,
    /**
     * Whether there is anywhere to send characters. Normally always true; false when the
     * keyboard was raised by the experimental trigger over an app that never asked for input,
     * which is the answer that experiment exists to produce.
     */
    val hasEditor: Boolean,
    /**
     * Whether the numeric row is typing digits. The letter legend is hidden while it is, because
     * a legend that promises `abc` on a key now producing `2` is worse than no legend at all.
     */
    val digits: Boolean,
)
