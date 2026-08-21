package io.github.vagrant326.atvletterwise.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vagrant326.atvletterwise.BuildConfig
import io.github.vagrant326.atvletterwise.update.UpdateActivity

/**
 * The app's only screen. It exists because an Android TV app with no launcher activity is
 * invisible on the home screen, and because `method.xml` has to point `settingsActivity`
 * somewhere.
 *
 * This app is installed on its own and knows nothing about the other keyboards in the
 * programme. Comparing methods happens by switching IME in the system settings, not here.
 */
class SettingsActivity : Activity() {

    private lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)

        // Rows are capped rather than stretched across the panel. A full-width control on a
        // TV is a metre of switch for two words of label.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        content.addView(heading("LetterWise"))
        content.addView(caption("Version ${BuildConfig.VERSION_NAME}"))

        content.addView(sectionLabel("Try it"))
        content.addView(scratchField())
        content.addView(
            caption("Focus this field to bring the keyboard up. Nothing here is saved.")
        )

        content.addView(sectionLabel("Keyboard"))
        content.addView(hintModeRow())
        content.addView(
            row("Switch or enable keyboard", "System") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        content.addView(sectionLabel("Updates"))
        content.addView(
            row("Check for updates", "") {
                startActivity(Intent(this, UpdateActivity::class.java))
            }
        )
        content.addView(
            caption(
                "The only thing here that uses the network. Runs in a separate process, " +
                    "only when you press it, and sends nothing. What you type never leaves " +
                    "the device."
            )
        )

        content.addView(sectionLabel("How to type"))
        content.addView(
            caption(
                "2-9 pick a letter group. 0 is space. 1 is punctuation, long press switches " +
                    "language. Up and down walk the alternatives, right accepts, left " +
                    "deletes, centre submits, back closes.\n\n" +
                    "Accept is optional: pressing the next group key accepts the previous " +
                    "letter on its own."
            )
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(28), dp(24), dp(28), dp(32))
                        addView(content)
                    }
                )
            }
        )
    }

    /**
     * Somewhere to try the keyboard without leaving the app and without editing anything
     * real. Not persisted and not read by anything.
     *
     * The explicit focus flags and `showSoftInput` are not decoration: on a TV there is no
     * touch, so a field that does not take d-pad focus cannot be reached at all, and
     * focusing one does not always raise the IME on its own.
     */
    private fun scratchField() = EditText(this).apply {
        hint = "Type here"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(Color.WHITE)
        setHintTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(FIELD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }

        setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundColor(if (hasFocus) FIELD_FOCUSED else FIELD)
            if (hasFocus) {
                showKeyboardFor(view)
            }
        }
        setOnClickListener { showKeyboardFor(it) }
    }

    private fun showKeyboardFor(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hintModeRow(): View {
        lateinit var control: LinearLayout
        lateinit var value: TextView
        lateinit var explain: TextView

        fun apply() {
            value.text = preferences.hintMode.label
            explain.text = preferences.hintMode.description
        }

        control = row("Key hint", preferences.hintMode.label) {
            preferences.hintMode = preferences.hintMode.next()
            apply()
        }
        value = control.getChildAt(1) as TextView
        explain = caption(preferences.hintMode.description)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(control)
            addView(explain)
        }
    }

    /** Label on the left, current value on the right, focus visible. Standard TV list row. */
    private fun row(label: String, value: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = true
        isClickable = true
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundColor(ROW)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }

        addView(
            TextView(this@SettingsActivity).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        addView(
            TextView(this@SettingsActivity).apply {
                text = value
                setTextColor(ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        )

        setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundColor(if (hasFocus) ROW_FOCUSED else ROW)
        }
        setOnClickListener { onClick() }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(22), 0, dp(2))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(SECONDARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(6), 0, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF08080B.toInt()
        const val ROW = 0xFF16161C.toInt()
        const val ROW_FOCUSED = 0xFF2A3A46.toInt()
        const val FIELD = 0xFF16161C.toInt()
        const val FIELD_FOCUSED = 0xFF22303A.toInt()
        const val SECONDARY = 0xFFB0B0BC.toInt()
        const val MUTED = 0xFF6B6B78.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
    }
}
