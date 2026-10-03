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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blacksquircle.ui.core.extensions.indexOf
import com.blacksquircle.ui.core.mvi.ViewEvent
import com.blacksquircle.ui.core.settings.SettingsManager
import com.blacksquircle.ui.feature.terminal.api.model.ShellArgs
import com.blacksquircle.ui.feature.terminal.domain.manager.RuntimeManager
import com.blacksquircle.ui.feature.terminal.domain.manager.SessionManager
import com.blacksquircle.ui.feature.terminal.domain.model.RuntimeState
import com.blacksquircle.ui.feature.terminal.domain.model.SessionModel
import com.blacksquircle.ui.feature.terminal.domain.runtime.TerminalRuntime
import com.blacksquircle.ui.navigation.api.Navigator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

internal class TerminalViewModel @AssistedInject constructor(
    private val settingsManager: SettingsManager,
    private val sessionManager: SessionManager,
    private val runtimeManager: RuntimeManager,
    private val navigator: Navigator,
    @Assisted private val pendingCommand: ShellArgs?,
) : ViewModel() {

    private val _viewState = MutableStateFlow(initialViewState())
    val viewState: StateFlow<TerminalViewState> = _viewState.asStateFlow()

    private val _viewEvent = Channel<ViewEvent>(Channel.BUFFERED)
    val viewEvent: Flow<ViewEvent> = _viewEvent.receiveAsFlow()

    private var sessions = emptyList<SessionModel>()
    private var selectedSession: String? = null
    private var internalPendingCommand: ShellArgs? = null
    private var isInitializing = false

    init {
        loadSessions()
    }

    fun onBackClicked() {
        navigator.goBack()
    }

    fun executeCommandInTerminal(command: String) {
        viewModelScope.launch {
            Timber.d("TERMINAL_EXEC: Received command -> $command")

            val currentSession = sessions.find { it.id == selectedSession }
            if (currentSession != null && !isInitializing) {
                Timber.d("TERMINAL_EXEC: Session exists and ready, writing directly")
                writeCommand(currentSession.id, command)
            } else {
                Timber.d("TERMINAL_EXEC: No session ready, queuing for new session...")
                // Capture command for the session that's about to be created
                internalPendingCommand = ShellArgs(command = command)
                if (!isInitializing && sessions.isEmpty()) {
                    onCreateSessionClicked()
                }
            }
        }
    }

    /**
     * Type [command] into [sessionId] once its shell is actually running.
     *
     * A session exists as soon as it is created, but its process only starts
     * when the terminal view attaches and sizes it, which happens a frame
     * later — and creating the first session also extracts the stdlib, so
     * that can be seconds. TerminalSession.write() is a no-op until then, so
     * a command sent right after the panel opened was silently dropped and
     * the terminal came up with nothing to run.
     */
    private suspend fun writeCommand(sessionId: String, command: String) {
        val session = sessions.find { it.id == sessionId }?.session ?: return
        repeat(SESSION_STARTUP_ATTEMPTS) {
            if (session.isRunning) {
                Timber.d("TERMINAL_EXEC: Writing command -> $command")
                session.write("$command\n")
                return
            }
            delay(SESSION_STARTUP_POLL_MS)
        }
        Timber.w("TERMINAL_EXEC: Dropped command, session never started -> $command")
    }

    fun onSessionClicked(sessionModel: SessionModel) {
        selectedSession = sessionModel.id
        _viewState.update {
            it.copy(selectedSession = selectedSession)
        }
    }

    fun onCreateSessionClicked() {
        if (isInitializing) return
        isInitializing = true

        viewModelScope.launch {
            try {
                createRuntime { runtime ->
                    val queued = internalPendingCommand
                    internalPendingCommand = null
                    val sessionId = sessionManager.createSession(runtime, queued ?: pendingCommand)
                    sessions = sessionManager.sessions()
                    selectedSession = sessionId

                    _viewState.update {
                        it.copy(
                            sessions = sessions,
                            selectedSession = selectedSession,
                        )
                    }
                    runCommandQueuedWhileCreating(sessionId)

                    viewModelScope.launch {
                        _viewEvent.send(TerminalViewEvent.ScrollToEnd)
                    }
                }
            } finally {
                isInitializing = false
            }
        }
    }

    @Suppress("KotlinConstantConditions")
    fun onCloseSessionClicked(sessionModel: SessionModel) {
        val selectedPosition = sessions.indexOf { it.id == selectedSession }
        val removedPosition = sessions.indexOf { it.id == sessionModel.id }
        val currentPosition = when {
            removedPosition == selectedPosition -> when {
                removedPosition - 1 > -1 -> removedPosition - 1
                removedPosition + 1 < sessions.size -> removedPosition
                else -> -1
            }
            removedPosition < selectedPosition -> selectedPosition - 1
            removedPosition > selectedPosition -> selectedPosition
            else -> -1
        }

        sessions = sessions.filter { it.id != sessionModel.id }
        selectedSession = sessions.getOrNull(currentPosition)?.id

        sessionManager.closeSession(sessionModel.id)

        if (sessions.isEmpty()) {
            viewModelScope.launch {
                _viewEvent.send(TerminalViewEvent.NotifyEmpty)
                // REMOVED loadSessions() - No pre-warming to ensure clean state
            }
        } else {
            _viewState.update {
                it.copy(
                    sessions = sessions,
                    selectedSession = selectedSession,
                )
            }
        }
    }

    /**
     * Creating a session blocks on the runtime — the first one also extracts
     * the stdlib — so a command can easily arrive in the middle of it. That
     * command missed the arguments handed to createSession, and used to be
     * discarded when the queue was cleared afterwards.
     */
    private fun runCommandQueuedWhileCreating(sessionId: String) {
        val lateCommand = internalPendingCommand?.command
        internalPendingCommand = null
        if (lateCommand != null) {
            viewModelScope.launch {
                writeCommand(sessionId, lateCommand)
            }
        }
    }

    private suspend fun createRuntime(onReady: (TerminalRuntime) -> Unit) {
        runtimeManager.createRuntime().collect { state ->
            when (state) {
                is RuntimeState.Installing -> {
                    _viewState.update {
                        it.copy(
                            isInstalling = true,
                            installProgress = state.progress,
                            installError = null,
                        )
                    }
                }
                is RuntimeState.Ready -> {
                    _viewState.update {
                        it.copy(
                            isInstalling = false,
                            installProgress = 1f,
                            installError = null,
                        )
                    }
                    onReady(state.runtime)
                }
                is RuntimeState.Failed -> {
                    _viewState.update {
                        it.copy(
                            isInstalling = true,
                            installProgress = 0f,
                            installError = state.error,
                        )
                    }
                }
            }
        }
    }

    private fun loadSessions() {
        if (isInitializing) return
        isInitializing = true

        viewModelScope.launch {
            try {
                sessions = sessionManager.sessions()
                selectedSession = sessions.lastOrNull()?.id

                if (sessions.isEmpty() || pendingCommand != null) {
                    createRuntime { runtime ->
                        val queued = internalPendingCommand
                        internalPendingCommand = null
                        val sessionId = sessionManager.createSession(runtime, queued ?: pendingCommand)

                        sessions = sessionManager.sessions()
                        selectedSession = sessionId

                        _viewState.update {
                            it.copy(
                                sessions = sessions,
                                selectedSession = selectedSession,
                            )
                        }
                        runCommandQueuedWhileCreating(sessionId)

                        viewModelScope.launch {
                            _viewEvent.send(TerminalViewEvent.ScrollToEnd)
                        }
                    }
                } else {
                    _viewState.update {
                        it.copy(
                            sessions = sessions,
                            selectedSession = selectedSession,
                        )
                    }
                }
            } finally {
                isInitializing = false
            }
        }
    }

    private fun initialViewState(): TerminalViewState {
        return TerminalViewState(
            cursorBlinking = settingsManager.cursorBlinking,
            keepScreenOn = settingsManager.keepScreenOn,
        )
    }

    class ParameterizedFactory(private val pendingCommand: ShellArgs?) : ViewModelProvider.Factory {

        @Inject
        lateinit var viewModelFactory: Factory

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return viewModelFactory.create(pendingCommand) as T
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted pendingCommand: ShellArgs?): TerminalViewModel
    }

    companion object {

        /** How long to wait for a session's shell to start before giving up. */
        private const val SESSION_STARTUP_ATTEMPTS = 200
        private const val SESSION_STARTUP_POLL_MS = 100L
    }
}