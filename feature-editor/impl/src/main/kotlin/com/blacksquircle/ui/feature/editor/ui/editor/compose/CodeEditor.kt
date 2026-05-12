/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blacksquircle.ui.feature.editor.ui.editor.compose

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksquircle.ui.feature.editor.ui.editor.model.EditorCommand
import com.blacksquircle.ui.feature.editor.ui.editor.model.EditorController
import com.blacksquircle.ui.feature.editor.ui.editor.model.EditorSettings
import com.blacksquircle.ui.feature.editor.ui.editor.view.*
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.github.rosemoe.sora.widget.subscribeAlways
import com.blacksquircle.ui.feature.shortcuts.api.extensions.forAction

import io.github.rosemoe.sora.event.SelectionChangeEvent

@Composable
internal fun CodeEditor(
    content: Content,
    language: String,
    settings: EditorSettings,
    controller: EditorController,
    modifier: Modifier = Modifier,
    onContentChanged: () -> Unit = {},
    onCursorChanged: (Int, Int) -> Unit = { _, _ -> },
    onShortcutPressed: (Boolean, Boolean, Boolean, Int) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    
    // Remember the LATEST callbacks to avoid stale closures in listeners
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)
    val currentOnCursorChanged by rememberUpdatedState(onCursorChanged)
    val currentOnShortcutPressed by rememberUpdatedState(onShortcutPressed)
    val currentSettings by rememberUpdatedState(settings)

    val view = remember {
        CodeEditor(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            subscribeAlways<ContentChangeEvent> { event ->
                if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                    currentOnContentChanged()
                }
            }
            subscribeAlways<SelectionChangeEvent> { event ->
                currentOnCursorChanged(
                    event.left.line + 1,
                    event.left.column + 1
                )
            }
            subscribeAlways<KeyBindingEvent> { event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val ctrl = event.isCtrlPressed
                    val shift = (event.metaState and KeyEvent.META_SHIFT_ON) != 0
                    val alt = (event.metaState and KeyEvent.META_ALT_ON) != 0
                    
                    // ONLY intercept if a shortcut is actually matched
                    val shortcut = currentSettings.keybindings.forAction(ctrl, shift, alt, event.keyCode)
                    if (shortcut != null) {
                        event.intercept()
                        currentOnShortcutPressed(ctrl, shift, alt, event.keyCode)
                    }
                }
            }
            colorScheme = SquircleScheme.create()
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    // Stable tracking of previous state to avoid redundant calls that restart IME
    var lastLanguageName by remember { mutableStateOf("") }
    var lastSettingsRef by remember { mutableStateOf<EditorSettings?>(null) }
    var lastContentRef by remember { mutableStateOf<Content?>(null) }

    AndroidView(
        factory = { view },
        update = { editor ->
            val settingsChanged = lastSettingsRef !== settings
            
            // 1. Update settings ONLY when they actually changed (Identity check)
            if (settingsChanged) {
                editor.setTextSize(settings.fontSize)
                editor.isWordwrap = settings.wordWrap
                editor.props.stickyScroll = settings.stickyScroll
                editor.isScalable = settings.pinchZoom
                editor.isLineNumberEnabled = settings.lineNumbers
                editor.isHighlightCurrentLine = settings.highlightCurrentLine
                editor.isHighlightBracketPair = settings.highlightMatchingDelimiters
                editor.isBlockLineEnabled = settings.highlightCodeBlocks
                
                // Guard isEditable as it restarts IME
                if (editor.isEditable != !settings.readOnly) {
                    editor.isEditable = !settings.readOnly
                }
                
                editor.tabWidth = settings.tabWidth
                editor.typefaceText = settings.fontType
                editor.typefaceLineNumber = settings.fontType
                
                // CRITICAL FIX: Set to false to prevent Android from blocking Enter/Shift keys 
                // when an external keyboard is connected.
                editor.isDisableSoftKbdIfHardKbdAvailable = false 
                
                editor.setShowInvisibleChars(settings.showInvisibleChars)
            }

            // 2. Update language ONLY on change to avoid InputMethod restart
            if (lastLanguageName != language || (settingsChanged && lastSettingsRef?.tabWidth != settings.tabWidth)) {
                val editorLanguage = editor.createFromRegistry(
                    language = language,
                    codeCompletion = settings.codeCompletion,
                    autoIndentation = settings.autoIndentation,
                    autoClosePairs = settings.autoClosePairs,
                    useTab = !settings.useSpacesInsteadOfTabs,
                    tabSize = settings.tabWidth,
                )
                editor.setEditorLanguage(editorLanguage)
                lastLanguageName = language
            }
            
            if (settingsChanged) {
                lastSettingsRef = settings
            }

            // 3. CRITICAL FIX: Only call setText if the Content object INSTANCE has changed (file swap).
            // Calling setText(currentContent) on every keystroke restarts the IME and KILLS 'Enter'.
            if (lastContentRef !== content) {
                editor.setText(content, true, null)
                editor.scroller.startScroll(0, 0, content.scrollX, content.scrollY, 0)
                editor.scroller.abortAnimation()
                lastContentRef = content
            }
            
            if (!editor.isFocused) {
                editor.requestFocus()
            }
        },
        onRelease = CodeEditor::release,
        modifier = modifier,
    )

    LaunchedEffect(Unit) {
        controller.commands.collect { command ->
            when (command) {
                is EditorCommand.Cut -> view.cutText()
                is EditorCommand.Copy -> view.copyText()
                is EditorCommand.Paste -> view.pasteText()

                is EditorCommand.SelectAll -> view.selectAll()
                is EditorCommand.SelectLine -> view.selectLine()
                is EditorCommand.DeleteLine -> view.deleteLine()
                is EditorCommand.DuplicateLine -> view.duplicateLine()
                is EditorCommand.ToggleCase -> view.toggleCase()

                is EditorCommand.PreviousWord -> view.previousWord()
                is EditorCommand.NextWord -> view.nextWord()
                is EditorCommand.StartOfLine -> view.startOfLine()
                is EditorCommand.EndOfLine -> view.endOfLine()

                is EditorCommand.Insert -> {
                    if (view.isFocused) {
                        view.pasteText(command.text)
                    }
                }
                is EditorCommand.IndentOrTab -> {
                    if (view.isFocused) {
                        view.indentOrCommitTab()
                    }
                }

                is EditorCommand.Find -> {
                    try {
                        val type = when {
                            command.searchState.regex -> SearchOptions.TYPE_REGULAR_EXPRESSION
                            command.searchState.wordsOnly -> SearchOptions.TYPE_WHOLE_WORD
                            else -> SearchOptions.TYPE_NORMAL
                        }
                        val searchOptions = SearchOptions(
                            /* type = */ type,
                            /* caseInsensitive = */ !command.searchState.matchCase,
                            /* regexBackrefGrammar = */ RegexBackrefGrammar.DEFAULT,
                        )
                        val findText = command.searchState.findText
                        if (findText.isNotEmpty()) {
                            view.searcher.search(findText, searchOptions)
                        } else {
                            view.searcher.stopSearch()
                        }
                    } catch (e: Exception) {
                        // ignored
                    }
                }
                is EditorCommand.Replace -> {
                    if (view.searcher.hasQuery()) {
                        view.searcher.replaceCurrentMatch(command.replacement)
                    }
                }
                is EditorCommand.ReplaceAll -> {
                    if (view.searcher.hasQuery()) {
                        view.searcher.replaceAll(command.replacement)
                    }
                }
                is EditorCommand.GoToLine -> {
                    val lineNumber = minOf(view.lineCount - 1, maxOf(0, command.line))
                    view.setSelection(lineNumber, 0)
                }
                is EditorCommand.PreviousMatch -> {
                    if (view.searcher.hasQuery()) {
                        view.searcher.gotoPrevious()
                    }
                }
                is EditorCommand.NextMatch -> {
                    if (view.searcher.hasQuery()) {
                        view.searcher.gotoNext()
                    }
                }
                is EditorCommand.StopSearch -> {
                    if (view.searcher.hasQuery()) {
                        view.searcher.stopSearch()
                    }
                }
            }
        }
    }
}
