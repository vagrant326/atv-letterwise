package io.github.vagrant326.atvletterwise.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Disambiguator
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language
import io.github.vagrant326.atvletterwise.model.ModelRepository
import io.github.vagrant326.atvletterwise.settings.Preferences

class LetterWiseImeService : InputMethodService() {

    private val partition = Partition.ITU
    private lateinit var models: ModelRepository
    private lateinit var preferences: Preferences
    private lateinit var composer: Composer
    private var strip: CandidateStripView? = null
    private var language = Language.PL
    private var punctuationIndex = -1
    private var showLanguageChooser = false

    override fun onCreate() {
        super.onCreate()
        models = ModelRepository(this)
        preferences = Preferences(this)
        language = preferences.activeLanguage
        composer = Composer(disambiguatorFor(language))
    }

    override fun onCreateInputView(): View =
        CandidateStripView(this).also { strip = it }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.clearPending()
        punctuationIndex = -1
        showLanguageChooser = false
        if (language !in preferences.enabledLanguages) {
            language = preferences.activeLanguage
            composer.useDisambiguator(disambiguatorFor(language), context())
        }
        render()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // An IME receives hardware key events even while its window is hidden. Consuming
        // d-pad events in that state takes over navigation for the whole device - it left a
        // TV unnavigable, recoverable only via HOME or a USB mouse. Never consume anything
        // unless the keyboard is actually on screen with somewhere to type.
        if (!isInputViewShown || currentInputConnection == null) {
            return super.onKeyDown(keyCode, event)
        }

        val action = KeyBindings.of(keyCode, event.repeatCount, preferences.customKeys)
            ?: return super.onKeyDown(keyCode, event)

        if (action == Action.Ignore) {
            return true
        }

        if (showLanguageChooser && handleChooser(action)) {
            render()
            return true
        }

        if (action !is Action.Punctuation) {
            punctuationIndex = -1
        }
        showLanguageChooser = false

        when (action) {
            is Action.Group -> {
                resolvePending()
                composer.pressGroup(action.key, context())
            }

            is Action.Symbol -> {
                resolvePending()
                currentInputConnection?.commitText(action.symbol.toString(), 1)
            }

            Action.NextCandidate -> composer.nextCandidate()
            Action.PreviousCandidate -> composer.previousCandidate()
            Action.Punctuation -> cyclePunctuation()
            Action.NextLanguage -> stepLanguage(1)
            Action.ShowLanguages -> showLanguageChooser = true
            Action.Ignore -> Unit

            // Moving the caret resolves whatever was in flight, so moving right is also how a
            // character gets accepted and there is no separate accept key to find. The arrow
            // is forwarded to the editor rather than computed here: the editor owns the text
            // and the selection, and asking it to move is the only version that stays correct
            // in a field this keyboard did not fill.
            Action.CaretLeft -> {
                resolvePending()
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }

            Action.CaretRight -> {
                resolvePending()
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }

            Action.Delete -> {
                if (composer.hasPending) {
                    composer.clearPending()
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }

            Action.Enter -> {
                resolvePending()
                submit()
                return true
            }

            // One press escapes once nothing is in flight. Losing a half-typed letter matters
            // far less than having a reliable way out of the keyboard.
            Action.Dismiss -> {
                if (composer.hasPending) {
                    composer.clearPending()
                } else {
                    requestHideSelf(0)
                    return true
                }
            }
        }

        render()
        return true
    }

    /**
     * While the list is open the language changes live, so up and down are the whole
     * interaction and there is nothing to confirm. Returns false for keys that should close
     * the list and then be handled normally.
     */
    private fun handleChooser(action: Action): Boolean = when (action) {
        Action.NextCandidate -> { stepLanguage(-1); true }
        Action.PreviousCandidate -> { stepLanguage(1); true }
        Action.NextLanguage, Action.ShowLanguages -> { stepLanguage(1); true }
        Action.CaretRight, Action.Enter, Action.Dismiss -> { showLanguageChooser = false; true }
        else -> false
    }

    /**
     * Steps through the languages the user enabled, not through everything the app knows.
     * Cycling is only usable while that list is short, which is what the list view is for.
     */
    private fun stepLanguage(delta: Int) {
        val enabled = preferences.enabledLanguages
        if (enabled.size < 2) {
            return
        }
        val index = enabled.indexOf(language).coerceAtLeast(0)
        language = enabled[(index + delta + enabled.size) % enabled.size]
        preferences.activeLanguage = language
        composer.useDisambiguator(disambiguatorFor(language), context())
    }

    /**
     * Repeated presses replace the previous punctuation mark rather than appending, which is
     * what makes a single key usable for six symbols.
     */
    private fun cyclePunctuation() {
        resolvePending()
        val connection = currentInputConnection ?: return
        if (punctuationIndex >= 0) {
            connection.deleteSurroundingText(1, 0)
        }
        punctuationIndex = (punctuationIndex + 1) % KeyBindings.PUNCTUATION.size
        connection.commitText(KeyBindings.PUNCTUATION[punctuationIndex].toString(), 1)
    }

    /** Turns the character in flight into real text in the field. */
    private fun resolvePending() {
        val pending = composer.pending ?: return
        composer.clearPending()
        currentInputConnection?.commitText(pending.toString(), 1)
    }

    /**
     * The resolved text immediately before the caret, read from the editor rather than kept
     * here.
     *
     * A local buffer would be a second source of truth that goes stale the moment the caret
     * moves or anything else edits the field. Reading the real thing also gives *better*
     * context, not worse: it works when the caret sits inside text this keyboard never typed,
     * where a buffer would have nothing to offer. When the editor declines to answer the
     * context is empty and the model backs off to unigram — the same outcome an emptied
     * buffer would give, so the worst case here equals the best case there.
     *
     * The character in flight is the composing text, so it comes back in this query and has
     * to be dropped: it is not resolved yet and must not be predicted from.
     */
    private fun context(): String {
        val connection = currentInputConnection ?: return ""
        val extra = if (composer.hasPending) 1 else 0
        val text = connection.getTextBeforeCursor(CONTEXT_LENGTH + extra, 0)?.toString()
            ?: return ""
        return if (extra > 0) text.dropLast(1) else text
    }

    private fun disambiguatorFor(language: Language) =
        Disambiguator(partition, models.modelFor(language))

    private fun render() {
        currentInputConnection?.setComposingText(composer.pending?.toString() ?: "", 1)
        strip?.update(
            StripState(
                composer = composer,
                partition = partition,
                language = language,
                enabledLanguages = preferences.enabledLanguages,
                trained = models.isTrained(language),
                hintMode = preferences.hintMode,
                showLanguageChooser = showLanguageChooser,
                customKeys = preferences.customKeys,
            )
        )
    }

    private fun submit() {
        val connection = currentInputConnection ?: return
        connection.finishComposingText()
        val editorAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (editorAction != null && editorAction != EditorInfo.IME_ACTION_NONE) {
            connection.performEditorAction(editorAction)
        } else {
            connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }

    private companion object {
        /** Enough for an order-5 model; the table in use needs two. */
        const val CONTEXT_LENGTH = 4
    }
}
