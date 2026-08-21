package io.github.vagrant326.atvletterwise.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import io.github.vagrant326.atvletterwise.ime.KeyBindings

/**
 * Asks the user to press the button they want, and records whatever keycode arrives.
 *
 * This exists because remotes disagree about what physically exists and what it reports.
 * A key sitting where a phone has `*` can be printed `TEXT` and send an entirely different
 * keycode, and there is no way to find that out from inside the app — only the user pressing
 * it can settle it. Guessing produced a language switch that could not be reached at all.
 *
 * Deliberately holds no focusable views, so every key reaches [onKeyDown] instead of being
 * eaten by focus navigation.
 */
class KeyCaptureActivity : Activity() {

    private lateinit var prompt: TextView
    private lateinit var detail: TextView
    private lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)

        prompt = TextView(this).apply {
            text = "Press the button you want for switching language"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        detail = TextView(this).apply {
            text = current()
            setTextColor(SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(18), 0, 0)
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(BACKGROUND)
                setPadding(dp(32), dp(32), dp(32), dp(32))
                addView(prompt)
                addView(detail)
            }
        )
    }

    private fun current(): String {
        val code = preferences.languageKeyCode
        return if (code == KeyBindings.NO_KEY) {
            "Nothing assigned. Long press on 1 opens the language list either way.\n\n" +
                "Press BACK to leave without changing it."
        } else {
            "Currently ${KeyEvent.keyCodeToString(code)} (code $code).\n\n" +
                "Press BACK to leave without changing it."
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        if (keyCode in KeyBindings.RESERVED) {
            prompt.text = "That key is needed for typing"
            detail.text = "${KeyEvent.keyCodeToString(keyCode)} (code $keyCode) is already " +
                "used by the keyboard. Assigning it would trade one unreachable function " +
                "for a broken one.\n\nPress another button, or BACK to leave."
            return true
        }

        preferences.languageKeyCode = keyCode
        prompt.text = "Assigned"
        detail.text = "${KeyEvent.keyCodeToString(keyCode)} (code $keyCode) now switches " +
            "language. Long press it for the list.\n\nPress BACK to go back."
        return true
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF08080B.toInt()
        const val SECONDARY = 0xFFB0B0BC.toInt()
    }
}
