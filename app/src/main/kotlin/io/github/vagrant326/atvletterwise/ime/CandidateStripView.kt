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
import android.view.KeyEvent
import android.view.ViewGroup
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

    private val keypad = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(dp(230), LayoutParams.WRAP_CONTENT)
    }

    /**
     * The assigned language and delete keys, named rather than drawn into the grid, and set
     * beside it rather than under it.
     *
     * Named because the grid mirrors the physical numpad and a key printed `TEXT` does not sit
     * where a phone has `*` — putting it in that cell would lie about where to reach for it.
     * Beside because the grid is three cells wide and the space to its right is already going
     * spare, while vertical space is what the search results underneath are short of.
     */
    private val assignedKeys = TextView(context).apply {
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setLineSpacing(0f, 1.25f)
        setPadding(dp(16), dp(4), 0, 0)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.TOP
        }
    }

    private val hintRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(keypad)
        addView(assignedKeys)
    }

    private val keypadCells = mutableMapOf<Char, TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        setPadding(dp(12), dp(6), dp(12), dp(8))
        addView(candidateRow)
        addView(inlineHint)
        addView(hintRow)
    }

    fun update(state: StripState) {
        candidateRow.text = candidateRow(state)

        if (keypad.childCount == 0) {
            buildKeypad(state.partition)
        }
        val keypadVisible = state.hintMode == HintMode.KEYPAD
        hintRow.visibility = if (keypadVisible) VISIBLE else GONE
        inlineHint.visibility = if (state.hintMode == HintMode.INLINE) VISIBLE else GONE

        if (state.hintMode == HintMode.INLINE) {
            inlineHint.text = state.partition.groups.entries
                .sortedBy { it.key }
                .joinToString("   ") { "${it.key}:${it.value}" } + "   0:space"
        }
        if (keypadVisible) {
            assignedKeys.text = buildString {
                append("language   ").append(keyLabel(state.customKeys.language, "hold 1"))
                append('\n')
                append("delete     ").append(keyLabel(state.customKeys.delete, "hold left"))
                append('\n')
                append("caret      left / right")
                append('\n')
                append("submit     centre")
            }
        }

        // Highlight the group the current alternatives came from, so the keypad reads as
        // part of what is happening rather than as a static wall of text.
        val active = state.composer.alternatives.firstOrNull()?.let { state.partition.keyFor(it) }
        for ((key, cell) in keypadCells) {
            cell.setTextColor(if (key == active) ACCENT else DIM)
        }
    }

    private fun keyLabel(keyCode: Int, fallback: String): String =
        if (keyCode == KeyBindings.NO_KEY) {
            fallback
        } else {
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
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
            text.append("up / down")
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

    /**
     * The physical numpad: 123 / 456 / 789 / 0. No `*` or `#` row — this is not a phone and
     * those keys are not on every remote, so drawing them promises buttons that may not exist.
     */
    private fun buildKeypad(partition: Partition) {
        for (row in listOf("123", "456", "789", " 0 ")) {
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
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
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
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
