package io.github.vagrant326.atvletterwise.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language
import io.github.vagrant326.atvletterwise.settings.HintMode

/**
 * Deliberately does **not** repeat the text being typed. The buffer is already in the field,
 * and the character in flight is already marked there as composing text, so a copy in the
 * strip was duplicated content competing with the search results underneath for the
 * scarcest thing on a TV screen.
 *
 * What is left is what the field cannot show: the alternatives for the position in flight,
 * and as much of the key mapping as the user asked for.
 */
@SuppressLint("ViewConstructor")
class CandidateStripView(context: Context) : LinearLayout(context) {

    private val candidateRow = TextView(context).apply {
        setTextColor(FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private val inlineHint = TextView(context).apply {
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private val keypad = LinearLayout(context).apply { orientation = VERTICAL }

    private val keypadCells = mutableMapOf<Char, TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        setPadding(dp(12), dp(6), dp(12), dp(8))
        addView(candidateRow)
        addView(inlineHint)
        addView(keypad)
    }

    fun update(
        composer: Composer,
        partition: Partition,
        language: Language,
        trained: Boolean,
        hintMode: HintMode,
    ) {
        candidateRow.text = candidates(composer, language, trained)

        if (keypad.childCount == 0) {
            buildKeypad(partition)
        }
        keypad.visibility = if (hintMode == HintMode.KEYPAD) VISIBLE else GONE
        inlineHint.visibility = if (hintMode == HintMode.INLINE) VISIBLE else GONE
        if (hintMode == HintMode.INLINE) {
            inlineHint.text = partition.groups.entries
                .sortedBy { it.key }
                .joinToString("   ") { "${it.key}:${it.value}" } + "   0:space"
        }

        // Highlight the group the current alternatives came from, so the keypad reads as
        // part of what is happening rather than as a static wall of text.
        val active = composer.alternatives.firstOrNull()?.let { partition.keyFor(it) }
        for ((key, cell) in keypadCells) {
            cell.setTextColor(if (key == active) ACCENT else DIM)
        }
    }

    private fun candidates(composer: Composer, language: Language, trained: Boolean): CharSequence {
        val tag = if (trained) language.label else "${language.label} no model"
        val text = SpannableStringBuilder(tag).append("   ")
        text.setSpan(ForegroundColorSpan(DIM), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val alternatives = composer.alternatives
        if (alternatives.isEmpty()) {
            val start = text.length
            text.append("press 2-9")
            text.setSpan(ForegroundColorSpan(DIM), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return text
        }
        alternatives.forEachIndexed { index, candidate ->
            val start = text.length
            text.append(candidate).append("   ")
            val selected = index == composer.alternativeIndex
            text.setSpan(
                ForegroundColorSpan(if (selected) ACCENT else DIM),
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    /** Phone and remote layout: 123 / 456 / 789 / _0_ — the shape already in the fingers. */
    private fun buildKeypad(partition: Partition) {
        for (row in listOf("123", "456", "789", " 0 ")) {
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (key in row) {
                line.addView(cell(key, partition))
            }
            keypad.addView(line)
        }
    }

    private fun cell(key: Char, partition: Partition): TextView {
        val letters = when (key) {
            ' ' -> ""
            '0' -> "space"
            '1' -> ".,-"
            else -> partition.symbolsFor(key)
        }
        return TextView(context).apply {
            text = if (key == ' ') "" else "$key\n$letters"
            setTextColor(DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 0.95f)
            setPadding(dp(2), dp(3), dp(2), dp(3))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
                topMargin = dp(2)
            }
            if (key != ' ') {
                setBackgroundColor(CELL)
            }
            keypadCells[key] = this
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xF0101014.toInt()
        const val FOREGROUND = 0xFFE8E8EC.toInt()
        const val DIM = 0xFF80808C.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
        const val CELL = 0xFF1A1A22.toInt()
    }
}
