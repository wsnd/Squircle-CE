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

package com.blacksquircle.ui.feature.terminal.ui.terminal

import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksquircle.ui.core.event.AppEvent
import com.blacksquircle.ui.core.event.EventBus
import com.blacksquircle.ui.core.extensions.copyText
import com.blacksquircle.ui.core.extensions.daggerViewModel
import com.blacksquircle.ui.core.extensions.primaryClipText
import com.blacksquircle.ui.core.extensions.showToast
import com.blacksquircle.ui.core.mvi.ViewEvent
import com.blacksquircle.ui.ds.PreviewBackground
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.button.IconButton
import com.blacksquircle.ui.ds.button.IconButtonSizeDefaults
import com.blacksquircle.ui.ds.button.IconButtonStyleDefaults
import com.blacksquircle.ui.ds.divider.HorizontalDivider
import com.blacksquircle.ui.ds.scaffold.ScaffoldSuite
import com.blacksquircle.ui.ds.tabs.TabItem
import com.blacksquircle.ui.ds.tabs.TabLayout
import com.blacksquircle.ui.ds.toolbar.Toolbar
import com.blacksquircle.ui.feature.terminal.R
import com.blacksquircle.ui.feature.terminal.api.navigation.TerminalRoute
import com.blacksquircle.ui.feature.terminal.domain.model.SessionModel
import com.blacksquircle.ui.feature.terminal.internal.TerminalComponent
import com.blacksquircle.ui.feature.terminal.ui.terminal.compose.InstallationScreen
import com.blacksquircle.ui.feature.terminal.ui.terminal.extrakeys.ExtraKeysConstants
import com.blacksquircle.ui.feature.terminal.ui.terminal.extrakeys.ExtraKeysInfo
import com.blacksquircle.ui.feature.terminal.ui.terminal.extrakeys.ExtraKeysView
import com.blacksquircle.ui.feature.terminal.ui.terminal.model.TerminalCommand
import com.blacksquircle.ui.feature.terminal.ui.terminal.view.TerminalViewClientImpl
import com.termux.view.TerminalView
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import com.blacksquircle.ui.ds.R as UiR

/** Height of Termux's single row multiplied by 2 */
private val EXTRA_KEYS_HEIGHT = 75.dp
private const val EXTRA_KEYS_STYLE = "default"
private const val EXTRA_KEYS_PROPERTIES = "[" +
    "['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], " +
    "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']" +
    "]"

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    onCloseClicked: () -> Unit = {}
) {
    val viewModel: TerminalViewModel = daggerViewModel { context ->
        val component = TerminalComponent.buildOrGet(context)
        TerminalViewModel.ParameterizedFactory(null).also(component::inject)
    }
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val tabsState = rememberLazyListState()

    TerminalScreen(
        viewState = viewState,
        tabsState = tabsState,
        onSessionClicked = viewModel::onSessionClicked,
        onCreateSessionClicked = viewModel::onCreateSessionClicked,
        onCloseSessionClicked = viewModel::onCloseSessionClicked,
        onCloseClicked = onCloseClicked,
        isPanel = true,
        headerModifier = headerModifier,
        modifier = modifier,
        viewModel = viewModel
    )
}

@Composable
internal fun TerminalScreen(
    navArgs: TerminalRoute,
    viewModel: TerminalViewModel = daggerViewModel { context ->
        val component = TerminalComponent.buildOrGet(context)
        TerminalViewModel.ParameterizedFactory(navArgs.args).also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val tabsState = rememberLazyListState()

    TerminalScreen(
        viewState = viewState,
        tabsState = tabsState,
        onSessionClicked = viewModel::onSessionClicked,
        onCreateSessionClicked = viewModel::onCreateSessionClicked,
        onCloseSessionClicked = viewModel::onCloseSessionClicked,
        onBackClicked = viewModel::onBackClicked,
        viewModel = viewModel
    )

    val activity = LocalActivity.current
    DisposableEffect(viewState.keepScreenOn) {
        if (viewState.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun TerminalScreen(
    viewState: TerminalViewState,
    tabsState: LazyListState,
    viewModel: TerminalViewModel,
    onSessionClicked: (SessionModel) -> Unit = {},
    onCreateSessionClicked: () -> Unit = {},
    onCloseSessionClicked: (SessionModel) -> Unit = {},
    onBackClicked: () -> Unit = {},
    onCloseClicked: () -> Unit = {},
    isPanel: Boolean = false,
    headerModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textSize = with(LocalDensity.current) { 10.sp.toPx() }
    val backgroundColor = SquircleTheme.colors.colorBackgroundPrimary.toArgb()
    val foregroundColor = SquircleTheme.colors.colorTextAndIconPrimary.toArgb()
    val activeBackgroundColor = SquircleTheme.colors.colorBackgroundTertiary.toArgb()
    val activeForegroundColor = SquircleTheme.colors.colorPrimary.toArgb()

    val extraKeysView = remember {
        ExtraKeysView(context, null).apply {
            val extraKeysInfo = ExtraKeysInfo(
                EXTRA_KEYS_PROPERTIES,
                EXTRA_KEYS_STYLE,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES
            )
            buttonBackgroundColor = backgroundColor
            buttonTextColor = foregroundColor
            buttonActiveBackgroundColor = activeBackgroundColor
            buttonActiveTextColor = activeForegroundColor
            reload(extraKeysInfo, EXTRA_KEYS_HEIGHT.value)
        }
    }
    val terminalView = remember {
        TerminalView(context, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setTextSize(textSize.toInt())
            setTypeface(Typeface.MONOSPACE)

            val viewClient = TerminalViewClientImpl(
                terminalView = this,
                extraKeysView = extraKeysView,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                cursorBlinking = viewState.cursorBlinking,
            )
            setTerminalViewClient(viewClient)
        }
    }

    // SHARED LISTENER: Works for both Panel and Screen
    LaunchedEffect(Unit) {
        try {
            EventBus.events
                .onStart { EventBus.setTerminalReady(true) }
                .collect { event ->
                    Timber.d("TERMINAL_BUS: Received event: $event")
                    if (event is AppEvent.ExecutePythonCommand) {
                        viewModel.executeCommandInTerminal(event.command)
                        terminalView.post {
                            terminalView.requestFocus()
                        }
                    }
                }
        } finally {
            EventBus.setTerminalReady(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is TerminalViewEvent.ScrollToEnd -> {
                    tabsState.animateScrollToItem(viewState.sessions.size)
                }
                is TerminalViewEvent.NotifyEmpty -> {
                    if (isPanel) {
                        onCloseClicked()
                    } else {
                        viewModel.onBackClicked()
                    }
                }
            }
        }
    }

    val content = @Composable { paddingValues: PaddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (viewState.isInstalling) {
                InstallationScreen(
                    installProgress = viewState.installProgress,
                    installError = viewState.installError,
                )
            } else {
                val currentSession = viewState.currentSession
                if (currentSession != null) {
                    TabLayout(
                        state = tabsState,
                        divider = false,
                        modifier = Modifier.height(28.dp).then(headerModifier),
                        leadingContent = {
                            if (isPanel) {
                                Text(
                                    text = stringResource(R.string.terminal_toolbar_title).uppercase(),
                                    style = SquircleTheme.typography.text12Regular,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SquircleTheme.colors.colorTextAndIconSecondary,
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    iconResId = UiR.drawable.ic_plus,
                                    onClick = onCreateSessionClicked,
                                    contentDescription = stringResource(R.string.terminal_menu_session_new),
                                    iconButtonSize = IconButtonSizeDefaults.XS,
                                )
                                if (isPanel) {
                                    IconButton(
                                        iconResId = UiR.drawable.ic_close,
                                        onClick = onCloseClicked,
                                        iconButtonSize = IconButtonSizeDefaults.XS,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    ) {
                        items(items = viewState.sessions, key = SessionModel::id) { sessionModel ->
                            TabItem(
                                title = if (sessionModel.ordinal > 0) sessionModel.name + " (${sessionModel.ordinal})" else sessionModel.name,
                                selected = sessionModel.id == currentSession.id,
                                height = 28.dp,
                                textStyle = SquircleTheme.typography.text12Regular.copy(
                                    fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                ),
                                paddingValues = PaddingValues(start = 12.dp),
                                onClick = { onSessionClicked(sessionModel) },
                                trailingContent = {
                                    IconButton(
                                        iconResId = UiR.drawable.ic_close,
                                        iconButtonStyle = IconButtonStyleDefaults.Secondary,
                                        onClick = { onCloseSessionClicked(sessionModel) },
                                        contentDescription = stringResource(R.string.terminal_menu_session_close),
                                        iconButtonSize = IconButtonSizeDefaults.XXS,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                },
                            )
                        }
                    }

                    AndroidView(
                        factory = { terminalView },
                        update = { terminalView.onScreenUpdated() },
                        modifier = Modifier.weight(1f)
                    )

                    LaunchedEffect(currentSession.id) {
                        terminalView.attachSession(currentSession.session)
                        terminalView.post {
                            terminalView.requestFocus()
                        }
                        currentSession.commands.collect { command ->
                            when (command) {
                                is TerminalCommand.Update -> terminalView.onScreenUpdated()
                                is TerminalCommand.Copy -> context.copyText(command.text)
                                is TerminalCommand.Paste -> terminalView.mEmulator?.paste(context.primaryClipText())
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(SquircleTheme.colors.colorBackgroundPrimary))
                }
            }
        }
    }

    if (isPanel) {
        content(PaddingValues())
    } else {
        ScaffoldSuite(
            topBar = {
                Toolbar(
                    title = stringResource(R.string.terminal_toolbar_title),
                    navigationIcon = UiR.drawable.ic_back,
                    onNavigationClicked = onBackClicked,
                )
            },
            bottomBar = {
                if (!viewState.isInstalling) {
                    AndroidView(
                        factory = { extraKeysView },
                        modifier = Modifier.fillMaxWidth().height(EXTRA_KEYS_HEIGHT).navigationBarsPadding()
                    )
                }
            },
            modifier = Modifier.imePadding()
        ) { contentPadding ->
            content(contentPadding)
        }
    }
}

@PreviewLightDark
@Composable
private fun TerminalScreenPreview() {
    PreviewBackground {
        TerminalScreen(
            viewState = TerminalViewState(sessions = emptyList(), selectedSession = null),
            tabsState = rememberLazyListState(),
            viewModel = daggerViewModel { context ->
                val component = TerminalComponent.buildOrGet(context)
                TerminalViewModel.ParameterizedFactory(null).also(component::inject)
            }
        )
    }
}
