package io.github.vagrant326.atvletterwise.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vagrant326.atvletterwise.ime.KeyBindings

/**
 * Asks the user to press the button they want, and records whatever keycode arrives.
 *
 * This exists because remotes disagree about what physically exists and about what it
 * reports. On this remote the key sitting where a phone has `*` is printed `TEXT` and sends a
 * keycode outside the standard range. Nothing in the app can discover that; only the user
 * pressing the button can, and guessing produced a function that could not be reached at all
 * — worse than an absent one, because it looks implemented.
 *
 * Driven entirely by [Binding], so putting another function on a user-chosen button costs one
 * enum entry and nothing here.
 *
 * **There are deliberately no focusable views.** An earlier version had Clear and Done
 * buttons on the theory that they would only ever consume the d-pad, which is reserved
 * anyway. In practice focus did not move between them and the d-pad arrived here instead, so
 * trying to reach a button looked like it was reassigning the key. A screen whose whole job
 * is to receive raw key events cannot also run focus navigation. Clearing lives in settings,
 * where focus behaves normally.
 */
class KeyCaptureActivity : Activity() {

    private lateinit var preferences: Preferences
    private lateinit var binding: Binding

    private lateinit var stateLabel: TextView
    private lateinit var keyName: TextView
    private lateinit var keyDetail: TextView
    private lateinit var note: TextView
    private lateinit var footer: TextView
    private lateinit var assignedValue: TextView

    /** Captured but not yet confirmed. Confirming needs the same key twice. */
    private var candidate: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)
        binding = Binding.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BINDING) }
            ?: Binding.LANGUAGE

        stateLabel = label("WAITING", MUTED, 12f)
        keyName = label("Press the button", Color.WHITE, 30f)
        keyDetail = label("", SECONDARY, 15f)
        note = label("", WARNING, 14f).apply { visibility = View.GONE }
        footer = label(FOOTER_WAITING, MUTED, 13f)
        assignedValue = label(assignedText(), SECONDARY, 15f)

        val target = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(CARD)
            setPadding(dp(24), dp(22), dp(24), dp(24))
            layoutParams = stack(dp(20))
            addView(stateLabel)
            addView(keyName.apply { setPadding(0, dp(6), 0, 0) })
            addView(keyDetail.apply { setPadding(0, dp(4), 0, 0) })
            addView(note.apply { setPadding(0, dp(12), 0, 0) })
        }

        val assigned = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(SUNKEN)
            setPadding(dp(24), dp(16), dp(24), dp(18))
            layoutParams = stack(dp(12))
            addView(label("ASSIGNED NOW", MUTED, 12f))
            addView(assignedValue.apply { setPadding(0, dp(4), 0, 0) })
            addView(label(binding.fallback, MUTED, 13f).apply { setPadding(0, dp(8), 0, 0) })
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(label(binding.title, Color.WHITE, 28f))
            addView(label(binding.prompt, SECONDARY, 15f).apply { setPadding(0, dp(6), 0, 0) })
            addView(target)
            addView(assigned)
            addView(footer.apply { setPadding(0, dp(18), 0, 0) })
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                isFocusable = false
                addView(
                    LinearLayout(this@KeyCaptureActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(28), dp(28), dp(28), dp(32))
                        addView(content)
                    }
                )
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // With a candidate on screen, BACK throws the candidate away rather than the
            // whole screen: it is the natural undo for pressing the wrong button.
            if (candidate == null) {
                finish()
            } else {
                candidate = null
                showWaiting()
            }
            return true
        }

        if (keyCode in KeyBindings.RESERVED) {
            // Never touch the candidate slot here. Writing a refusal where the chosen key is
            // displayed made a rejection look like a reassignment.
            note.text = "${KeyEvent.keyCodeToString(keyCode)} (code $keyCode) is needed for " +
                "typing and cannot be assigned."
            note.visibility = View.VISIBLE
            return true
        }

        note.visibility = View.GONE

        if (candidate == keyCode) {
            preferences.assign(binding, keyCode)
            candidate = null
            stateLabel.text = "SAVED"
            assignedValue.text = assignedText()
            footer.text = FOOTER_SAVED
            return true
        }

        candidate = keyCode
        stateLabel.text = "PRESS AGAIN TO SAVE"
        keyName.text = KeyEvent.keyCodeToString(keyCode)
        keyDetail.text = "code $keyCode"
        footer.text = FOOTER_CANDIDATE
        return true
    }

    private fun showWaiting() {
        stateLabel.text = "WAITING"
        keyName.text = "Press the button"
        keyDetail.text = ""
        note.visibility = View.GONE
        footer.text = FOOTER_WAITING
    }

    private fun assignedText(): String {
        val code = preferences.keyCodeFor(binding)
        return if (code == KeyBindings.NO_KEY) {
            "Nothing"
        } else {
            "${KeyEvent.keyCodeToString(code)}  ·  code $code"
        }
    }

    private fun label(text: String, colour: Int, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun card(colour: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10).toFloat()
        setColor(colour)
    }

    private fun stack(topMargin: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { this.topMargin = topMargin }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_BINDING = "binding"

        private const val FOOTER_WAITING = "BACK leaves without changing anything."
        private const val FOOTER_CANDIDATE =
            "Press the same button again to save it. BACK discards it. Another button replaces it."
        private const val FOOTER_SAVED = "Saved. BACK returns to settings."

        private const val BACKGROUND = 0xFF08080B.toInt()
        private const val CARD = 0xFF16161C.toInt()
        private const val SUNKEN = 0xFF101014.toInt()
        private const val SECONDARY = 0xFFB0B0BC.toInt()
        private const val MUTED = 0xFF6B6B78.toInt()
        private const val WARNING = 0xFFEF9F27.toInt()
    }
}
