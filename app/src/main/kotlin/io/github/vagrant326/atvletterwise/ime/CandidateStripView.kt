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
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.settings.HintMode

/**
 * Deliberately does **not** repeat the text being typed. The buffer is already in the field,
 * and the character in flight is already marked there as composing text, so a copy in the
 * strip was duplicated content taking a row from the search results underneath — the
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

    fun update(state: StripState) {
        candidateRow.text = candidateRow(state)

        if (keypad.childCount == 0) {
            buildKeypad(state.partition)
        }
        keypad.visibility = if (state.hintMode == HintMode.KEYPAD) VISIBLE else GONE
        inlineHint.visibility = if (state.hintMode == HintMode.INLINE) VISIBLE else GONE
        if (state.hintMode == HintMode.INLINE) {
            inlineHint.text = state.partition.groups.entries
                .sortedBy { it.key }
                .joinToString("   ") { "${it.key}:${it.value}" } + "   0:space   *:lang"
        }

        keypadCells['*']?.text = "*\n${state.language.label.lowercase()}"

        // Highlight the group the current alternatives came from, so the keypad reads as
        // part of what is happening rather than as a static wall of text.
        val active = state.composer.alternatives.firstOrNull()?.let { state.partition.keyFor(it) }
        for ((key, cell) in keypadCells) {
            val highlighted = key == active || (key == '*' && state.showLanguageChooser)
            cell.setTextColor(if (highlighted) ACCENT else DIM)
        }
    }

    private fun candidateRow(state: StripState): CharSequence {
        if (state.showLanguageChooser) {
            val text = SpannableStringBuilder()
            for (language in state.enabledLanguages) {
                val start = text.length
                text.append(language.label).append("   ")
                val selected = language == state.language
                text.setSpan(
                    ForegroundColorSpan(if (selected) ACCENT else DIM),
                    start,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            val hint = text.length
            text.append("press * again")
            text.setSpan(ForegroundColorSpan(DIM), hint, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return text
        }

        val tag = if (state.trained) state.language.label else "${state.language.label} no model"
        val text = SpannableStringBuilder(tag).append("   ")
        text.setSpan(ForegroundColorSpan(DIM), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val alternatives = state.composer.alternatives
        if (alternatives.isEmpty()) {
            val start = text.length
            text.append("press 2-9")
            text.setSpan(ForegroundColorSpan(DIM), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return text
        }
        alternatives.forEachIndexed { index, candidate ->
            val start = text.length
            text.append(candidate).append("   ")
            val selected = index == state.composer.alternativeIndex
            text.setSpan(
                ForegroundColorSpan(if (selected) ACCENT else DIM),
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    /** Phone and remote layout: 123 / 456 / 789 / *0# — the shape already in the fingers. */
    private fun buildKeypad(partition: Partition) {
        for (row in listOf("123", "456", "789", "*0#")) {
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (key in row) {
                line.addView(cell(key, partition))
            }
            keypad.addView(line)
        }
    }

    private fun cell(key: Char, partition: Partition): TextView {
        val letters = when (key) {
            '*' -> "lang"
            '#' -> ""
            '0' -> "space"
            '1' -> ".,-"
            else -> partition.symbolsFor(key)
        }
        return TextView(context).apply {
            text = "$key\n$letters"
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
            setBackgroundColor(if (key == '#') BACKGROUND else CELL)
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
