package io.github.vagrant326.atvletterwise.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import io.github.vagrant326.atvletterwise.core.Composer
import io.github.vagrant326.atvletterwise.core.Disambiguator
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.model.Language
import io.github.vagrant326.atvletterwise.model.ModelRepository

class LetterWiseImeService : InputMethodService() {

    private val partition = Partition.ITU
    private lateinit var models: ModelRepository
    private lateinit var composer: Composer
    private var strip: CandidateStripView? = null
    private var language = Language.PL
    private var punctuationIndex = -1

    /**
     * What has actually been committed into the target field. The composer owns the
     * intended state; this tracks what the editor has been told, so the two can be
     * reconciled with the minimum number of InputConnection calls.
     */
    private var mirrored = ""

    override fun onCreate() {
        super.onCreate()
        models = ModelRepository(this)
        composer = Composer(disambiguatorFor(language))
    }

    override fun onCreateInputView(): View =
        CandidateStripView(this).also { strip = it }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.clear()
        mirrored = ""
        punctuationIndex = -1
        render()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = KeyBindings.of(keyCode, longPress = event.repeatCount > 0)
            ?: return super.onKeyDown(keyCode, event)

        if (action !is Action.Punctuation) {
            punctuationIndex = -1
        }

        when (action) {
            is Action.Group -> composer.pressGroup(action.key)
            is Action.Symbol -> composer.pressSymbol(action.symbol)
            Action.NextCandidate -> composer.nextCandidate()
            Action.PreviousCandidate -> composer.previousCandidate()
            Action.Accept -> composer.accept()
            Action.Punctuation -> cyclePunctuation()
            Action.ToggleLanguage -> toggleLanguage()
            Action.ClearAll -> composer.clear()

            Action.Backspace -> {
                if (!composer.backspace()) {
                    // Nothing of ours left to delete, so let the editor handle it.
                    return super.onKeyDown(keyCode, event)
                }
            }

            Action.Enter -> {
                composer.accept()
                render()
                submit()
                return true
            }

            Action.Dismiss -> {
                if (composer.isEmpty) {
                    requestHideSelf(0)
                } else {
                    composer.clear()
                }
            }
        }

        render()
        return true
    }

    /**
     * Repeated presses replace the previous punctuation mark rather than appending, which
     * is what makes a single key usable for six symbols.
     */
    private fun cyclePunctuation() {
        if (punctuationIndex >= 0) {
            composer.backspace()
        }
        punctuationIndex = (punctuationIndex + 1) % KeyBindings.PUNCTUATION.size
        composer.pressSymbol(KeyBindings.PUNCTUATION[punctuationIndex])
    }

    private fun toggleLanguage() {
        language = if (language == Language.PL) Language.EN else Language.PL
        composer.useDisambiguator(disambiguatorFor(language))
    }

    private fun disambiguatorFor(language: Language) =
        Disambiguator(partition, models.modelFor(language))

    private fun render() {
        val connection: InputConnection? = currentInputConnection
        if (connection != null) {
            connection.beginBatchEdit()
            // Clear the composing region first so the committed-text diff below is not
            // competing with an in-flight character for the same span.
            connection.setComposingText("", 1)

            val committed = composer.committedText
            when {
                committed.length > mirrored.length ->
                    connection.commitText(committed.substring(mirrored.length), 1)

                committed.length < mirrored.length ->
                    connection.deleteSurroundingText(mirrored.length - committed.length, 0)
            }
            mirrored = committed

            composer.pending?.let { connection.setComposingText(it.toString(), 1) }
            connection.endBatchEdit()
        }
        strip?.update(composer, partition, language, models.isTrained(language))
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
        composer.clear()
        mirrored = ""
    }
}
