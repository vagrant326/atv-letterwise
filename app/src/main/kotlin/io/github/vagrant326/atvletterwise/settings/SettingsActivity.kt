package io.github.vagrant326.atvletterwise.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)

                addView(heading("LetterWise"))
                addView(
                    body(
                        "Version ${BuildConfig.VERSION_NAME}\n\n" +
                            "Digits 2-9 pick a letter group, 0 is space, 1 is punctuation " +
                            "and switches language on a long press. Up and down walk the " +
                            "alternatives, right accepts, left deletes, centre submits.\n\n" +
                            "You do not have to press accept: the next group key accepts " +
                            "the previous letter on its own."
                    )
                )
                addView(
                    button("Keyboard settings - enable or switch keyboard") {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                )
                addView(
                    button("Check for updates") {
                        startActivity(Intent(this@SettingsActivity, UpdateActivity::class.java))
                    }
                )
                addView(
                    body(
                        "The update check is the only thing here that uses the network. It " +
                            "runs in a separate process, only when you press that button, " +
                            "and sends nothing. What you type never leaves the device."
                    )
                )
            }
        )
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFB0B0BC.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }
}
