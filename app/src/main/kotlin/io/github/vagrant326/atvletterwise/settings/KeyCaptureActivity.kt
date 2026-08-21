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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vagrant326.atvletterwise.ime.KeyBindings

/**
 * Asks the user to press the button they want, and records whatever keycode arrives.
 *
 * This exists because remotes disagree about what physically exists and about what it
 * reports. The key sitting where a phone has `*` is printed `TEXT` on this remote and sends
 * keycode 300 — outside the standard range entirely. Nothing in the app could have guessed
 * that, and guessing `KEYCODE_STAR` produced a function that could not be reached at all,
 * which is worse than an absent one because it looks implemented.
 *
 * Driven entirely by [Binding], so putting another function on a user-chosen button costs one
 * enum entry and nothing here.
 *
 * The focusable buttons are safe despite the screen needing raw key events: they only ever
 * consume the d-pad and centre, and those are exactly the keys that can never be assigned.
 * Everything else falls through to [onKeyDown].
 */
class KeyCaptureActivity : Activity() {

    private lateinit var preferences: Preferences
    private lateinit var binding: Binding

    private lateinit var stateLabel: TextView
    private lateinit var keyName: TextView
    private lateinit var keyCode: TextView
    private lateinit var reason: TextView
    private lateinit var assignedValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)
        binding = Binding.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BINDING) }
            ?: Binding.LANGUAGE

        stateLabel = label("WAITING", MUTED, 12f)
        keyName = label("Press a button on the remote", Color.WHITE, 30f)
        keyCode = label("", SECONDARY, 15f)
        reason = label("", WARNING, 14f).apply { visibility = View.GONE }
        assignedValue = label(assignedText(), SECONDARY, 15f)

        val target = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(CARD)
            setPadding(dp(24), dp(22), dp(24), dp(24))
            layoutParams = stack(topMargin = dp(20))
            addView(stateLabel)
            addView(keyName.apply { setPadding(0, dp(6), 0, 0) })
            addView(keyCode.apply { setPadding(0, dp(4), 0, 0) })
            addView(reason.apply { setPadding(0, dp(10), 0, 0) })
        }

        val assigned = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card(SUNKEN)
            setPadding(dp(24), dp(16), dp(24), dp(18))
            layoutParams = stack(topMargin = dp(12))
            addView(label("ASSIGNED NOW", MUTED, 12f))
            addView(assignedValue.apply { setPadding(0, dp(4), 0, 0) })
            addView(label(binding.fallback, MUTED, 13f).apply { setPadding(0, dp(8), 0, 0) })
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = stack(topMargin = dp(18))
            addView(action("Clear") { clear() })
            addView(action("Done") { finish() })
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(label(binding.title, Color.WHITE, 28f))
            addView(label(binding.prompt, SECONDARY, 15f).apply { setPadding(0, dp(6), 0, 0) })
            addView(target)
            addView(assigned)
            addView(actions)
            addView(
                label("BACK leaves without changing anything.", MUTED, 13f)
                    .apply { setPadding(0, dp(16), 0, 0) }
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
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
            finish()
            return true
        }
        if (keyCode in KeyBindings.RESERVED) {
            showRejected(keyCode)
            return true
        }
        preferences.assign(binding, keyCode)
        showAssigned(keyCode)
        return true
    }

    private fun showRejected(code: Int) {
        stateLabel.text = "NOT AVAILABLE"
        keyName.text = KeyEvent.keyCodeToString(code)
        this.keyCode.text = "code $code"
        reason.text = "The keyboard needs this key for typing. Assigning it would trade one " +
            "unreachable function for a broken one."
        reason.visibility = View.VISIBLE
    }

    private fun showAssigned(code: Int) {
        stateLabel.text = "ASSIGNED"
        keyName.text = KeyEvent.keyCodeToString(code)
        this.keyCode.text = "code $code"
        reason.visibility = View.GONE
        assignedValue.text = assignedText()
    }

    private fun clear() {
        preferences.assign(binding, KeyBindings.NO_KEY)
        stateLabel.text = "CLEARED"
        keyName.text = "Press a button on the remote"
        keyCode.text = ""
        reason.visibility = View.GONE
        assignedValue.text = assignedText()
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

    private fun action(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        background = card(SUNKEN)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginEnd = dp(10) }
        setOnFocusChangeListener { view, hasFocus ->
            view.background = card(if (hasFocus) FOCUSED else SUNKEN)
        }
        setOnClickListener { onClick() }
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

        private const val BACKGROUND = 0xFF08080B.toInt()
        private const val CARD = 0xFF16161C.toInt()
        private const val SUNKEN = 0xFF101014.toInt()
        private const val FOCUSED = 0xFF2A3A46.toInt()
        private const val SECONDARY = 0xFFB0B0BC.toInt()
        private const val MUTED = 0xFF6B6B78.toInt()
        private const val WARNING = 0xFFEF9F27.toInt()
    }
}
