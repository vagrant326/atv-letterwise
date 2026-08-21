package io.github.vagrant326.atvletterwise.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language

/**
 * Three rows, in the order that matters when the underlying search results are competing
 * for the same screen: the buffer, the alternatives for the position in flight, and the
 * group legend so the mapping never has to be remembered.
 *
 * Kept short deliberately. A TV search screen is cramped and the results underneath are
 * the reason the user is typing at all.
 */
@SuppressLint("ViewConstructor")
class CandidateStripView(context: Context) : LinearLayout(context) {

    private val bufferRow = row(22f, bold = true)
    private val candidateRow = row(18f)
    private val legendRow = row(13f)

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        val padding = dp(10)
        setPadding(padding, dp(6), padding, dp(6))
        addView(bufferRow)
        addView(candidateRow)
        addView(legendRow)
    }

    fun update(composer: Composer, partition: Partition, language: Language, trained: Boolean) {
        bufferRow.text = buffer(composer, language, trained)
        candidateRow.text = candidates(composer)
        legendRow.text = legend(partition)
    }

    private fun buffer(composer: Composer, language: Language, trained: Boolean): CharSequence {
        val prefix = if (trained) language.label else "${language.label} (no model)"
        val text = SpannableStringBuilder("$prefix  ")
        text.setSpan(ForegroundColorSpan(DIM), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append(composer.committedText)
        composer.pending?.let { pending ->
            val start = text.length
            text.append(pending)
            // The character in flight is the one thing the user must be able to find
            // without hunting, since deciding whether to press NEXT depends on seeing it.
            text.setSpan(BackgroundColorSpan(HIGHLIGHT), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(Color.BLACK), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun candidates(composer: Composer): CharSequence {
        val alternatives = composer.alternatives
        if (alternatives.isEmpty()) {
            return ""
        }
        val text = SpannableStringBuilder()
        alternatives.forEachIndexed { index, candidate ->
            val start = text.length
            text.append(candidate).append("  ")
            val colour = if (index == composer.alternativeIndex) ACCENT else DIM
            text.setSpan(ForegroundColorSpan(colour), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun legend(partition: Partition): CharSequence =
        partition.groups.entries
            .sortedBy { it.key }
            .joinToString("   ") { "${it.key}:${it.value}" } + "   0:space"

    private fun row(sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        setTextColor(FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.START
        if (bold) {
            setTypeface(typeface, Typeface.BOLD)
        }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xF0101014.toInt()
        const val FOREGROUND = 0xFFE8E8EC.toInt()
        const val DIM = 0xFF80808C.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
        const val HIGHLIGHT = 0xFF7FD1FF.toInt()
    }
}
