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

package com.blacksquircle.ui.feature.editor.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksquircle.ui.core.contract.ContractResult
import com.blacksquircle.ui.core.contract.MimeType
import com.blacksquircle.ui.core.contract.rememberCreateFileContract
import com.blacksquircle.ui.core.contract.rememberOpenFileContract
import com.blacksquircle.ui.core.effect.CleanupEffect
import com.blacksquircle.ui.core.effect.ResultEffect
import com.blacksquircle.ui.core.extensions.daggerViewModel
import com.blacksquircle.ui.core.extensions.showToast
import com.blacksquircle.ui.core.mvi.ViewEvent
import com.blacksquircle.ui.ds.PreviewBackground
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.divider.HorizontalDivider
import com.blacksquircle.ui.ds.divider.VerticalDraggableDivider
import com.blacksquircle.ui.ds.divider.VerticalDivider
import com.blacksquircle.ui.ds.drawer.DrawerState
import com.blacksquircle.ui.ds.drawer.rememberDrawerState
import com.blacksquircle.ui.ds.emptyview.EmptyView
import com.blacksquircle.ui.ds.layout.SquircleLayout
import com.blacksquircle.ui.ds.layout.WindowSize
import com.blacksquircle.ui.ds.navigationrail.NavigationRail
import com.blacksquircle.ui.ds.navigationrail.NavigationRailItem
import com.blacksquircle.ui.ds.progress.CircularProgress
import com.blacksquircle.ui.ds.scaffold.ScaffoldSuite
import com.blacksquircle.ui.ds.statusbar.StatusBar
import com.blacksquircle.ui.ds.statusbar.StatusBarItem
import com.blacksquircle.ui.feature.editor.R
import com.blacksquircle.ui.feature.editor.domain.model.DocumentModel
import com.blacksquircle.ui.feature.editor.internal.EditorComponent
import com.blacksquircle.ui.feature.editor.ui.editor.compose.*
import com.blacksquircle.ui.feature.editor.ui.editor.model.*
import com.blacksquircle.ui.feature.explorer.ui.explorer.DrawerExplorer
import com.blacksquircle.ui.feature.git.api.navigation.CheckoutRoute.Companion.KEY_CHECKOUT
import com.blacksquircle.ui.feature.git.api.navigation.PullRoute.Companion.KEY_PULL
import com.blacksquircle.ui.feature.terminal.ui.terminal.TerminalPanel
import kotlinx.coroutines.launch
import com.blacksquircle.ui.ds.R as UiR

internal const val KEY_CLOSE_FILE = "KEY_CLOSE_FILE"
internal const val KEY_SELECT_LANGUAGE = "KEY_SELECT_LANGUAGE"
internal const val KEY_GOTO_LINE = "KEY_GOTO_LINE"
internal const val KEY_INSERT_COLOR = "KEY_INSERT_COLOR"

@Composable
internal fun EditorScreen(
    viewModel: EditorViewModel = daggerViewModel { context ->
        val component = EditorComponent.buildOrGet(context)
        EditorViewModel.Factory().also(component::inject)
    },
) {
    val scope = rememberCoroutineScope()
    val editorController = rememberEditorController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val tabsState = rememberLazyListState()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    var line by remember { mutableIntStateOf(1) }
    var column by remember { mutableIntStateOf(1) }
    
    var sidePaneWidth by rememberSaveable { mutableStateOf(300f) }
    var bottomPanelHeight by rememberSaveable { mutableStateOf(300f) }

    EditorScreen(
        viewState = viewState,
        drawerState = drawerState,
        tabsState = tabsState,
        editorController = editorController,
        line = line,
        column = column,
        sidePaneWidth = sidePaneWidth.dp,
        bottomPanelHeight = bottomPanelHeight.dp,
        onSidePaneWidthChanged = { sidePaneWidth = it.value },
        onBottomPanelHeightChanged = { bottomPanelHeight = it.value },
        onCursorChanged = { l, c ->
            line = l
            column = c
        },
        onDrawerClicked = {
            scope.launch {
                if (drawerState.isOpen) drawerState.close() else drawerState.open()
            }
        },
        onNewFileClicked = viewModel::onNewFileClicked,
        onOpenFileClicked = viewModel::onOpenFileClicked,
        onSaveFileClicked = viewModel::onSaveFileClicked,
        onSaveFileAsClicked = viewModel::onSaveFileAsClicked,
        onReloadFileClicked = viewModel::onReloadFileClicked,
        onRunPythonClicked = viewModel::onRunPythonClicked,
        onReadOnlyClicked = viewModel::onReadOnlyClicked,
        onContentChanged = viewModel::onContentChanged,
        onShortcutPressed = viewModel::onShortcutPressed,
        onCutClicked = viewModel::onCutClicked,
        onCopyClicked = viewModel::onCopyClicked,
        onPasteClicked = viewModel::onPasteClicked,
        onSelectAllClicked = viewModel::onSelectAllClicked,
        onSelectLineClicked = viewModel::onSelectLineClicked,
        onDeleteLineClicked = viewModel::onDeleteLineClicked,
        onDuplicateLineClicked = viewModel::onDuplicateLineClicked,
        onUndoClicked = viewModel::onUndoClicked,
        onRedoClicked = viewModel::onRedoClicked,
        onToggleFindClicked = viewModel::onToggleFindClicked,
        onToggleReplaceClicked = viewModel::onToggleReplaceClicked,
        onFindTextChanged = viewModel::onFindTextChanged,
        onReplaceTextChanged = viewModel::onReplaceTextChanged,
        onRegexClicked = viewModel::onRegexClicked,
        onMatchCaseClicked = viewModel::onMatchCaseClicked,
        onWordsOnlyClicked = viewModel::onWordsOnlyClicked,
        onPreviousMatchClicked = viewModel::onPreviousMatchClicked,
        onNextMatchClicked = viewModel::onNextMatchClicked,
        onReplaceMatchClicked = viewModel::onReplaceMatchClicked,
        onReplaceAllClicked = viewModel::onReplaceAllClicked,
        onForceSyntaxClicked = viewModel::onForceSyntaxClicked,
        onInsertColorClicked = viewModel::onInsertColorClicked,
        onFetchClicked = viewModel::onFetchClicked,
        onPullClicked = viewModel::onPullClicked,
        onCommitClicked = viewModel::onCommitClicked,
        onPushClicked = viewModel::onPushClicked,
        onCheckoutClicked = viewModel::onCheckoutClicked,
        onTerminalClicked = viewModel::onTerminalClicked,
        onSettingsClicked = viewModel::onSettingsClicked,
        onDocumentClicked = viewModel::onDocumentClicked,
        onDocumentMoved = viewModel::onDocumentMoved,
        onCloseClicked = viewModel::onCloseClicked,
        onCloseOthersClicked = viewModel::onCloseOthersClicked,
        onCloseAllClicked = viewModel::onCloseAllClicked,
        onErrorActionClicked = viewModel::onErrorActionClicked,
        onExtraKeyClicked = viewModel::onExtraKeyClicked,
        onExtraOptionsClicked = viewModel::onExtraOptionsClicked,
        onToggleBottomPanel = viewModel::onToggleBottomPanel,
    )

    val defaultFileName = stringResource(UiR.string.common_untitled)
    val newFileContract = rememberCreateFileContract(MimeType.TEXT) { result ->
        if (result is ContractResult.Success) viewModel.onFileOpened(result.uri)
    }
    val openFileContract = rememberOpenFileContract { result ->
        if (result is ContractResult.Success) viewModel.onFileOpened(result.uri)
    }
    val saveFileContract = rememberCreateFileContract(MimeType.TEXT) { result ->
        if (result is ContractResult.Success) viewModel.onSaveFileSelected(result.uri)
    }

    val activity = LocalActivity.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> context.showToast(text = event.message)
                is EditorViewEvent.Finish -> activity?.finish()
                is EditorViewEvent.ScrollToEnd -> tabsState.animateScrollToItem(viewState.documents.size)
                is EditorViewEvent.CreateFileContract -> newFileContract.launch(defaultFileName)
                is EditorViewEvent.OpenFileContract -> openFileContract.launch(arrayOf(MimeType.ANY))
                is EditorViewEvent.SaveAsFileContract -> saveFileContract.launch(event.fileName)
                is EditorViewEvent.Command -> scope.launch { editorController.send(event.command) }
            }
        }
    }

    ResultEffect<String>(KEY_CLOSE_FILE) { viewModel.onCloseModifiedClicked(it) }
    ResultEffect<String>(KEY_SELECT_LANGUAGE) { viewModel.onLanguageChanged(it) }
    ResultEffect<Int>(KEY_GOTO_LINE) { viewModel.onLineSelected(it) }
    ResultEffect<Int>(KEY_INSERT_COLOR) { viewModel.onColorSelected(it) }
    ResultEffect<Unit>(KEY_PULL) { viewModel.onReloadFileClicked() }
    ResultEffect<Unit>(KEY_CHECKOUT) { viewModel.onReloadFileClicked() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPaused() }

    BackHandler {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else viewModel.onBackClicked()
    }

    CleanupEffect { EditorComponent.release() }
    ReportDrawnWhen { !viewState.isLoading }
}

@Composable
private fun EditorScreen(
    viewState: EditorViewState,
    editorController: EditorController,
    drawerState: DrawerState,
    tabsState: LazyListState,
    onDrawerClicked: () -> Unit = {},
    onNewFileClicked: () -> Unit = {},
    onOpenFileClicked: () -> Unit = {},
    onSaveFileClicked: () -> Unit = {},
    onSaveFileAsClicked: () -> Unit = {},
    onReloadFileClicked: () -> Unit = {},
    onRunPythonClicked: () -> Unit = {},
    onReadOnlyClicked: () -> Unit = {},
    onContentChanged: () -> Unit = {},
    onShortcutPressed: (Boolean, Boolean, Boolean, Int) -> Unit = { _, _, _, _ -> },
    onCutClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onPasteClicked: () -> Unit = {},
    onSelectAllClicked: () -> Unit = {},
    onSelectLineClicked: () -> Unit = {},
    onDeleteLineClicked: () -> Unit = {},
    onDuplicateLineClicked: () -> Unit = {},
    onUndoClicked: () -> Unit = {},
    onRedoClicked: () -> Unit = {},
    onToggleFindClicked: () -> Unit = {},
    onToggleReplaceClicked: () -> Unit = {},
    onFindTextChanged: (String) -> Unit = {},
    onReplaceTextChanged: (String) -> Unit = {},
    onRegexClicked: () -> Unit = {},
    onMatchCaseClicked: () -> Unit = {},
    onWordsOnlyClicked: () -> Unit = {},
    onPreviousMatchClicked: () -> Unit = {},
    onNextMatchClicked: () -> Unit = {},
    onReplaceMatchClicked: () -> Unit = {},
    onReplaceAllClicked: () -> Unit = {},
    onForceSyntaxClicked: () -> Unit = {},
    onInsertColorClicked: () -> Unit = {},
    onFetchClicked: () -> Unit = {},
    onPullClicked: () -> Unit = {},
    onCommitClicked: () -> Unit = {},
    onPushClicked: () -> Unit = {},
    onCheckoutClicked: () -> Unit = {},
    onTerminalClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {},
    onDocumentClicked: (DocumentModel) -> Unit = {},
    onDocumentMoved: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCloseClicked: (DocumentModel) -> Unit = {},
    onCloseOthersClicked: (DocumentModel) -> Unit = {},
    onCloseAllClicked: () -> Unit = {},
    onErrorActionClicked: (ErrorAction) -> Unit = {},
    onExtraKeyClicked: (Char) -> Unit = {},
    onExtraOptionsClicked: () -> Unit = {},
    onToggleBottomPanel: () -> Unit = {},
    line: Int = 1,
    column: Int = 1,
    sidePaneWidth: Dp = 300.dp,
    bottomPanelHeight: Dp = 300.dp,
    onSidePaneWidthChanged: (Dp) -> Unit = {},
    onBottomPanelHeightChanged: (Dp) -> Unit = {},
    onCursorChanged: (Int, Int) -> Unit = { _, _ -> },
) {
    val windowSize = SquircleLayout.windowSize
    val isTablet = windowSize != WindowSize.Compact
    
    val currentBottomHeight by rememberUpdatedState(bottomPanelHeight)
    val currentOnBottomHeightChanged by rememberUpdatedState(onBottomPanelHeightChanged)

    Row(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            NavigationRail {
                NavigationRailItem(
                    iconResId = UiR.drawable.ic_folder,
                    selected = drawerState.isOpen,
                    onClick = onDrawerClicked,
                )
                NavigationRailItem(
                    iconResId = UiR.drawable.ic_console,
                    selected = viewState.bottomPanelVisible,
                    onClick = onToggleBottomPanel,
                )
                NavigationRailItem(
                    iconResId = UiR.drawable.ic_settings,
                    selected = false,
                    onClick = onSettingsClicked,
                )
            }
            VerticalDivider()
        }

        if (isTablet && drawerState.isOpen) {
            Surface(
                modifier = Modifier.width(sidePaneWidth).fillMaxHeight(),
                shape = RectangleShape,
                color = SquircleTheme.colors.colorBackgroundSecondary
            ) {
                DrawerExplorer(onDrawerClicked)
            }
            VerticalDraggableDivider(
                onDrag = { onSidePaneWidthChanged((sidePaneWidth + it).coerceIn(200.dp, 600.dp)) }
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ScaffoldSuite(
                topBar = {
                    EditorToolbar(
                        currentDocument = viewState.currentDocument,
                        onDrawerClicked = onDrawerClicked,
                        onNewFileClicked = onNewFileClicked,
                        onOpenFileClicked = onOpenFileClicked,
                        onSaveFileClicked = onSaveFileClicked,
                        onSaveFileAsClicked = onSaveFileAsClicked,
                        onReloadFileClicked = onReloadFileClicked,
                        onRunPythonClicked = onRunPythonClicked,
                        onCutClicked = onCutClicked,
                        onCopyClicked = onCopyClicked,
                        onPasteClicked = onPasteClicked,
                        onSelectAllClicked = onSelectAllClicked,
                        onSelectLineClicked = onSelectLineClicked,
                        onDeleteLineClicked = onDeleteLineClicked,
                        onDuplicateLineClicked = onDuplicateLineClicked,
                        onUndoClicked = onUndoClicked,
                        onRedoClicked = onRedoClicked,
                        onFindClicked = onToggleFindClicked,
                        onForceSyntaxClicked = onForceSyntaxClicked,
                        onInsertColorClicked = onInsertColorClicked,
                        onFetchClicked = onFetchClicked,
                        onPullClicked = onPullClicked,
                        onCommitClicked = onCommitClicked,
                        onPushClicked = onPushClicked,
                        onCheckoutClicked = onCheckoutClicked,
                        onTerminalClicked = if (isTablet) onToggleBottomPanel else onTerminalClicked,
                        onSettingsClicked = onSettingsClicked,
                    )
                },
                bottomBar = {
                    if (viewState.showExtendedKeyboard) {
                        ExtendedKeyboard(
                            currentDocument = viewState.currentDocument,
                            preset = viewState.settings.keyboardPreset,
                            showExtraKeys = viewState.showExtraKeys,
                            readOnly = viewState.settings.readOnly,
                            onExtraKeyClicked = onExtraKeyClicked,
                            onExtraOptionsClicked = onExtraOptionsClicked,
                            onSaveFileClicked = onSaveFileClicked,
                            onReadOnlyClicked = onReadOnlyClicked,
                            onUndoClicked = onUndoClicked,
                            onRedoClicked = onRedoClicked,
                        )
                    }
                },
                drawerState = drawerState,
                drawerGesturesEnabled = !isTablet && drawerState.isOpen,
                drawerContent = if (!isTablet) { { DrawerExplorer(onDrawerClicked) } } else null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { contentPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    DocumentTabLayout(
                        tabs = viewState.documents,
                        selectedIndex = viewState.selectedDocument,
                        state = tabsState,
                        onDocumentClicked = { onDocumentClicked(it.document) },
                        onDocumentMoved = onDocumentMoved,
                        onCloseClicked = { onCloseClicked(it.document) },
                        onCloseOthersClicked = { onCloseOthersClicked(it.document) },
                        onCloseAllClicked = onCloseAllClicked,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    )

                    val currentDocument = viewState.currentDocument
                    val searchState = currentDocument?.searchState

                    if (currentDocument != null && searchState != null) {
                        SearchPanel(
                            searchState = searchState,
                            onFindTextChanged = onFindTextChanged,
                            onReplaceTextChanged = onReplaceTextChanged,
                            onToggleReplaceClicked = onToggleReplaceClicked,
                            onRegexClicked = onRegexClicked,
                            onMatchCaseClicked = onMatchCaseClicked,
                            onWordsOnlyClicked = onWordsOnlyClicked,
                            onCloseSearchClicked = onToggleFindClicked,
                            onPreviousMatchClicked = onPreviousMatchClicked,
                            onNextMatchClicked = onNextMatchClicked,
                            onReplaceMatchClicked = onReplaceMatchClicked,
                            onReplaceAllClicked = onReplaceAllClicked,
                        )
                        HorizontalDivider()
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (currentDocument?.content != null) {
                            key(currentDocument.document.uuid) {
                                CodeEditor(
                                    content = currentDocument.content,
                                    language = currentDocument.document.language,
                                    settings = viewState.settings,
                                    controller = editorController,
                                    onContentChanged = onContentChanged,
                                    onCursorChanged = onCursorChanged,
                                    onShortcutPressed = onShortcutPressed,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        if (viewState.isError && !viewState.isLoading) {
                            ErrorStatus(
                                errorState = currentDocument?.errorState,
                                onActionClicked = onErrorActionClicked,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        if (viewState.isEmpty && !viewState.isLoading) {
                            EmptyView(
                                iconResId = UiR.drawable.ic_file_find,
                                title = stringResource(R.string.editor_empty_view_title),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        if (viewState.isLoading) {
                            CircularProgress(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    if (isTablet && viewState.bottomPanelVisible) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(currentBottomHeight)
                        ) {
                            TerminalPanel(
                                modifier = Modifier.fillMaxSize(),
                                headerModifier = Modifier.pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        currentOnBottomHeightChanged(
                                            (currentBottomHeight - dragAmount.y.toDp()).coerceIn(100.dp, 600.dp)
                                        )
                                    }
                                },
                                onCloseClicked = onToggleBottomPanel
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(SquircleTheme.colors.colorOutline)
                                    .align(Alignment.TopCenter)
                                    .zIndex(100f)
                            )
                        }
                    }
                }
            }

            if (isTablet) {
                StatusBar(
                    startContent = {
                        StatusBarItem(text = "UTF-8")
                        StatusBarItem(text = "LF")
                    },
                    endContent = {
                        StatusBarItem(text = "Ln $line, Col $column")
                        StatusBarItem(text = viewState.currentDocument?.document?.language ?: "plaintext")
                    }
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun EditorScreenPreview() {
    PreviewBackground {
        EditorScreen(
            viewState = EditorViewState(
                documents = listOf(
                    DocumentState(
                        document = DocumentModel(
                            uuid = "123",
                            fileUri = "file://storage/emulated/0/Downloads/untitled.txt",
                            filesystemUuid = "local",
                            displayName = "untitled.txt",
                            language = "plaintext",
                            modified = false,
                            position = 0,
                            scrollX = 0,
                            scrollY = 0,
                            selectionStart = 0,
                            selectionEnd = 0,
                            gitRepository = null,
                        ),
                    )
                ),
                selectedDocument = 0,
                isLoading = true,
            ),
            editorController = rememberEditorController(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            tabsState = rememberLazyListState(),
        )
    }
}
