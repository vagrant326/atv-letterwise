package io.github.vagrant326.atvletterwise.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Disambiguator
import io.github.vagrant326.atvletterwise.core.LetterCase
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language
import io.github.vagrant326.atvletterwise.model.ModelRepository
import io.github.vagrant326.atvletterwise.settings.Preferences

class LetterWiseImeService : InputMethodService() {

    /** Polish carries nine more letters on the same eight keys, so this follows the language. */
    private val partition: Partition get() = language.partition

    private lateinit var models: ModelRepository
    private lateinit var preferences: Preferences
    private lateinit var composer: Composer
    private var strip: CandidateStripView? = null
    private var language = Language.PL
    private var punctuationIndex = -1
    private var showLanguageChooser = false
    private var composing = false

    /** The numeric row types digits rather than letter groups. Per field, never remembered. */
    private var digits = false

    /**
     * A key that is down and whose character is still waiting for the release, or
     * `KEYCODE_UNKNOWN` for none. Cleared the moment a hold claims the press, and per field.
     */
    private var deferredKey = KeyEvent.KEYCODE_UNKNOWN

    /**
     * Applied where characters are written and nowhere else. The disambiguator, the model and
     * the candidate walk all stay in lower case — `a` and `A` are the same candidate in the same
     * position, so nothing about prediction changes.
     */
    private var letterCase = LetterCase.LOWER

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
        digits = wantsDigits(info)
        deferredKey = KeyEvent.KEYCODE_UNKNOWN

        // Like the digit mode, the case belongs to the field: a lock left on in one box must not
        // follow the user into the next one.
        letterCase = LetterCase.LOWER
        if (language !in preferences.enabledLanguages) {
            language = preferences.activeLanguage
            composer.useDisambiguator(disambiguatorFor(language), context())
        }
        moveCaretToEnd(info)
        render()
    }

    /**
     * A field that declares itself numeric has no use for letter prediction, so it starts in
     * digit mode. Recomputed for every field rather than remembered: a manual switch made in a
     * PIN box must not follow the user into the next search query.
     *
     * Plenty of fields that mostly hold digits still declare themselves plain text — the
     * pairing-code box this was found in is one — which is why the manual toggle exists and
     * this is only the shortcut for fields that are honest.
     */
    private fun wantsDigits(info: EditorInfo?): Boolean {
        val variant = (info?.inputType ?: return false) and InputType.TYPE_MASK_CLASS
        return variant == InputType.TYPE_CLASS_NUMBER ||
            variant == InputType.TYPE_CLASS_PHONE ||
            variant == InputType.TYPE_CLASS_DATETIME
    }

    /**
     * A field that opens with the caret at the front is almost never what was wanted: coming
     * back to a search box means adding to the query, not prefixing it.
     *
     * Only nudged when the selection is collapsed at zero and there is text after it, so an
     * editor that placed the caret deliberately, or gave the user a selection, is left alone.
     */
    private fun moveCaretToEnd(info: EditorInfo?) {
        if (info == null || info.initialSelStart != 0 || info.initialSelEnd != 0) {
            return
        }
        val connection = currentInputConnection ?: return
        val tail = connection.getTextAfterCursor(MAX_TAIL, 0)?.length ?: 0
        if (tail > 0) {
            connection.setSelection(tail, tail)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // An IME receives hardware key events even while its window is hidden. Consuming d-pad
        // events in that state takes over navigation for the whole device - it left a TV
        // unnavigable, recoverable only via HOME or a USB mouse.
        //
        // So while hidden exactly one key is honoured: the trigger the user assigned, which is
        // unassigned by default and can never be a reserved key. One key, chosen deliberately,
        // is a blast radius worth having; the d-pad is not.
        if (!isInputViewShown) {
            val trigger = preferences.triggerKeyCode
            if (trigger != KeyBindings.NO_KEY && keyCode == trigger && event.repeatCount == 0) {
                raiseSelf()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        val action = KeyBindings.of(keyCode, event.repeatCount, preferences.customKeys, digits)
            ?: return super.onKeyDown(keyCode, event)

        if (action == Action.Ignore) {
            return true
        }

        if (showLanguageChooser && handleChooser(action)) {
            render()
            return true
        }

        // Only another short `1` continues the punctuation cycle. Anything else breaks it —
        // including holding `1` for the digit — so the next press starts fresh instead of
        // deleting a character it did not put there.
        if (action != Action.DeferToRelease || keyCode != KeyEvent.KEYCODE_1) {
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

            // Nothing happens yet. See onKeyUp.
            Action.DeferToRelease -> deferredKey = keyCode

            /**
             * The character in flight is still the composing region, so the switch applies to it
             * as well as to what follows. Pressing the case key after seeing the wrong case is
             * the order people actually press them in.
             */
            Action.ToggleCase -> {
                // The hold has claimed the press, so the release must not also write a space.
                deferredKey = KeyEvent.KEYCODE_UNKNOWN
                letterCase = letterCase.next()
            }

            Action.ToggleDigits -> {
                resolvePending()
                digits = !digits
            }

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
     * `0` and `1` commit on release rather than on press, because each carries a character *and*
     * a digit, and a hold arrives as a *second* key-down.
     *
     * Committing on the way down and retracting on the hold was the obvious version and does not
     * work: `commitText` is one-way, so the `getTextBeforeCursor` that would confirm what to
     * retract is answered from before the commit has landed. The retraction then either misses
     * the character or, unguarded, deletes whatever the user typed before it. Deciding on the way
     * up needs neither guess — nothing was committed, so nothing has to be un-typed.
     *
     * `2`-`9` are not deferred. Their short press only sets composing text, which the digit can
     * drop for free, and deferring them would hold back the letter that makes the keyboard feel
     * like it is keeping up.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != deferredKey) {
            return super.onKeyUp(keyCode, event)
        }
        deferredKey = KeyEvent.KEYCODE_UNKNOWN
        if (keyCode == KeyEvent.KEYCODE_1) {
            cyclePunctuation()
        } else {
            resolvePending()
            currentInputConnection?.commitText(" ", 1)
        }
        render()
        return true
    }

    /**
     * Raises the keyboard without an app having asked for it, which is the whole of what the
     * trigger key does.
     *
     * `requestShowSelf` puts the window on screen, but an IME writes through an
     * `InputConnection` and a view that never requested input does not provide one - so over an
     * app that renders its own keyboard, the keys arrive and there is nowhere to send them. The
     * strip says which of the two happened rather than leaving it to guesswork; see
     * docs/30-global-key-capture.md for why the accessibility route is the only one with both
     * halves.
     */
    private fun raiseSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestShowSelf(0)
        }
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

    /** Turns the character in flight into real text in the field, in the case now in force. */
    private fun resolvePending() {
        val pending = composer.pending ?: return
        composer.clearPending()
        currentInputConnection?.commitText(letterCase.apply(pending).toString(), 1)

        // Spent by a letter reaching the field and by nothing else. The digit that now sits at
        // the end of every group is not a letter, so walking down to it does not silently
        // swallow a capital the user had asked for.
        if (pending.isLetter()) {
            letterCase = letterCase.afterLetter()
        }
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
     *
     * Folded to lower case on the way out, and that is load-bearing rather than tidiness. The
     * corpus is lowercased before training — `corpus/alphabet.py` — so the model's alphabet is
     * 27 symbols for English and 33 for Polish with no capital among them. A `J` reaching
     * [Disambiguator] would be a symbol the table has never seen, and the order-3 model would
     * back off for the next three characters: every proper noun would predict badly from its
     * own first letter onwards. Folding here also keeps the keyboard doing exactly what
     * `Simulator` measures, which runs over the same lowercased text.
     */
    private fun context(): String {
        val connection = currentInputConnection ?: return ""
        val extra = if (composer.hasPending) 1 else 0
        val text = connection.getTextBeforeCursor(CONTEXT_LENGTH + extra, 0)?.toString()
            ?: return ""
        return (if (extra > 0) text.dropLast(1) else text).lowercase()
    }

    private fun disambiguatorFor(language: Language) =
        Disambiguator(language.partition, models.modelFor(language))

    private fun render() {
        val connection = currentInputConnection
        val pending = composer.pending
        if (connection != null) {
            // Only touch the composing region when there is something to say about it. Setting
            // an empty composing text unconditionally creates and clears a region on every
            // keystroke, which editors are entitled to interpret as a selection change.
            if (pending != null) {
                connection.setComposingText(letterCase.apply(pending).toString(), 1)
                composing = true
            } else if (composing) {
                connection.setComposingText("", 1)
                connection.finishComposingText()
                composing = false
            }
        }
        strip?.update(
            StripState(
                composer = composer,
                partition = partition,
                language = language,
                enabledLanguages = preferences.enabledLanguages,
                trained = models.isTrained(language),
                hintMode = preferences.hintMode,
                letterCase = letterCase,
                showLanguageChooser = showLanguageChooser,
                customKeys = preferences.customKeys,
                hasEditor = connection != null,
                digits = digits,
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

        /** Only used to find the end of an existing value, so a generous bound is plenty. */
        const val MAX_TAIL = 2000
    }
}
