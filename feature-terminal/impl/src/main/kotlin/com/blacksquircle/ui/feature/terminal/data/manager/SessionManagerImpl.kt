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

package com.blacksquircle.ui.feature.terminal.data.manager

import android.content.Context
import com.blacksquircle.ui.feature.python.PythonReplNative
import com.blacksquircle.ui.feature.python.PythonStdlibExtractor
import com.blacksquircle.ui.feature.terminal.api.model.RuntimeType
import com.blacksquircle.ui.feature.terminal.api.model.ShellArgs
import com.blacksquircle.ui.feature.terminal.domain.manager.SessionManager
import com.blacksquircle.ui.feature.terminal.domain.model.SessionModel
import com.blacksquircle.ui.feature.terminal.domain.runtime.TerminalRuntime
import com.blacksquircle.ui.feature.terminal.ui.terminal.model.TerminalCommand
import com.blacksquircle.ui.feature.terminal.ui.terminal.view.TerminalSessionClientImpl
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.convertEnvironmentToEnviron
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.putToEnvIfInSystemEnv
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_COLORTERM
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_HOME
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_LANG
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_PATH
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_TERM
import com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_TMPDIR
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class SessionManagerImpl(
    private val context: Context
) : SessionManager {

    private val sessions = ConcurrentHashMap<String, SessionModel>()
    private val counter = AtomicInteger(0)
    private val pythonRepls = ConcurrentHashMap<String, PythonReplNative>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun sessions(): List<SessionModel> {
        return sessions.values.sortedBy(SessionModel::ordinal)
    }

    override fun createSession(runtime: TerminalRuntime, args: ShellArgs?): String {
        Timber.d("createSession called: ${runtime.name}")
        
        val sessionId = UUID.randomUUID().toString()
        val commands = MutableSharedFlow<TerminalCommand>(extraBufferCapacity = 64)
        val client = TerminalSessionClientImpl(
            onUpdate = { commands.tryEmit(TerminalCommand.Update) },
            onCopy = { text -> commands.tryEmit(TerminalCommand.Copy(text)) },
            onPaste = { commands.tryEmit(TerminalCommand.Paste) }
        )
        
        // Ensure bin directory and python symlinks exist for all session types
        setupBinDirectory()

        // Check if this is a Python runtime
        if (runtime.type == RuntimeType.PYTHON || args?.isPythonRepl == true) {
            return createPythonReplSession(sessionId, runtime, client, commands)
        }
        
        // Standard shell session
        val environment = HashMap<String, String>()
        setupCommonEnvironment(environment, runtime)
        
        val (shellPath, shellArgs) = Pair("/system/bin/sh", arrayOf("-i"))

        sessions[sessionId] = SessionModel(
            id = sessionId,
            name = runtime.name,
            ordinal = counter.getAndIncrement(),
            session = TerminalSession(
                shellPath,
                args?.workingDir ?: runtime.homeDir,
                shellArgs,
                convertEnvironmentToEnviron(environment).toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                client
            ),
            commands = commands.asSharedFlow(),
        )
        
        return sessionId
    }

    private fun setupBinDirectory() {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val pythonExe = File(nativeLibDir, "libpython_exe.so")
        val pythonLink = File(binDir, "python")
        val pipLink = File(binDir, "pip")

        try {
            if (pythonExe.exists()) {
                pythonLink.delete()
                android.system.Os.symlink(pythonExe.absolutePath, pythonLink.absolutePath)
            }
            
            if (!pipLink.exists()) {
                pipLink.writeText("#!/system/bin/sh\npython -m pip \"$@\"\n")
                pipLink.setExecutable(true, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup bin directory")
        }
    }

    private fun setupCommonEnvironment(env: HashMap<String, String>, runtime: TerminalRuntime) {
        env[ENV_HOME] = runtime.homeDir
        env[ENV_LANG] = DEFAULT_LANG
        env[ENV_TMPDIR] = runtime.tmpDir
        env[ENV_COLORTERM] = DEFAULT_COLOR
        env[ENV_TERM] = DEFAULT_TERM

        val binDir = File(context.filesDir, "bin").absolutePath
        val currentPath = System.getenv(ENV_PATH).orEmpty()
        env[ENV_PATH] = if (currentPath.contains(binDir)) currentPath else "$binDir:$currentPath"

        putToEnvIfInSystemEnv(env, "ANDROID_ASSETS")
        putToEnvIfInSystemEnv(env, "ANDROID_DATA")
        putToEnvIfInSystemEnv(env, "ANDROID_ROOT")
        putToEnvIfInSystemEnv(env, "ANDROID_STORAGE")
        putToEnvIfInSystemEnv(env, "EXTERNAL_STORAGE")
    }

    override fun closeSession(sessionId: String) {
        val terminalSession = sessions[sessionId]?.session ?: return
        if (terminalSession.pid > 0) {
            terminalSession.finishIfRunning()
        }
        
        // Clean up Python REPL if exists
        pythonRepls.remove(sessionId)?.stopRepl()
        
        sessions.remove(sessionId)

        if (sessions.isEmpty()) {
            counter.set(0)
        }
    }
    
    private fun createPythonReplSession(
        sessionId: String,
        runtime: TerminalRuntime,
        client: TerminalSessionClientImpl,
        commands: MutableSharedFlow<TerminalCommand>
    ): String {
        Timber.d("Creating Python REPL session")
        
        val pythonPath = PythonStdlibExtractor.extractIfNeeded(context)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        
        val binDir = File(context.filesDir, "bin")
        val initScript = File(binDir, "init.sh")
        
        try {
            // Setup the 'init.sh' script with pip check and prompt
            val sitePackages = "$pythonPath/lib/python3.14/site-packages"
            val initContent = """
                # Silent initialization
                if [ ! -d "$sitePackages/pip" ]; then
                    echo "Initializing pip (one-time setup)..."
                    python -m ensurepip --default-pip > /dev/null 2>&1
                fi
                echo "Python 3.14 environment initialized."
                
                # Show current folder name in prompt
                export PS1='${"$"}{PWD##*/} ${"$"}'
            """.trimIndent()
            initScript.writeText(initContent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write init script")
        }
        
        val environment = HashMap<String, String>()
        setupCommonEnvironment(environment, runtime)
        
        // Setup Python specific environment
        environment["PYTHONHOME"] = pythonPath ?: ""
        environment["PYTHONPATH"] = "$pythonPath/lib/python3.14:$pythonPath/lib/python3.14/lib-dynload:$pythonPath/lib/python3.14/site-packages"
        environment["LD_LIBRARY_PATH"] = "$nativeLibDir:$pythonPath/lib/python3.14/lib-dynload"
        environment["PYTHONUNBUFFERED"] = "1"
        environment["ENV"] = initScript.absolutePath
        
        // Extra vars for pip
        val pipCache = File(context.cacheDir, "pip")
        if (!pipCache.exists()) pipCache.mkdirs()
        environment["PIP_CACHE_DIR"] = pipCache.absolutePath
        
        val shellPath = "/system/bin/sh"
        val shellArgs = arrayOf("-i")

        val terminalSession = TerminalSession(
            shellPath,
            runtime.homeDir,
            shellArgs,
            convertEnvironmentToEnviron(environment).toTypedArray(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            client
        )
        
        sessions[sessionId] = SessionModel(
            id = sessionId,
            name = runtime.name,
            ordinal = counter.getAndIncrement(),
            session = terminalSession,
            commands = commands.asSharedFlow(),
        )
        
        return sessionId
    }
    
    /**
     * Fallback session when Python REPL fails to start
     */
    private fun createFallbackPythonSession(
        sessionId: String,
        runtime: TerminalRuntime,
        client: TerminalSessionClientImpl,
        commands: MutableSharedFlow<TerminalCommand>,
        showPythonError: Boolean = false
    ): String {
        val environment = HashMap<String, String>()
        environment[ENV_HOME] = runtime.homeDir
        environment[ENV_LANG] = DEFAULT_LANG
        environment[ENV_PATH] = System.getenv(ENV_PATH).orEmpty()
        environment[ENV_TMPDIR] = runtime.tmpDir
        environment[ENV_COLORTERM] = DEFAULT_COLOR
        environment[ENV_TERM] = DEFAULT_TERM
        
        val shellPath = "/system/bin/sh"
        val shellArgs = arrayOf("-i")
        
        val terminalSession = TerminalSession(
            shellPath,
            runtime.homeDir,
            shellArgs,
            convertEnvironmentToEnviron(environment).toTypedArray(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            client
        )
        
        sessions[sessionId] = SessionModel(
            id = sessionId,
            name = runtime.name,
            ordinal = counter.getAndIncrement(),
            session = terminalSession,
            commands = commands.asSharedFlow(),
        )
        
        scope.launch {
            if (showPythonError) {
                showMissingPythonBinaryError(terminalSession)
            } else {
                terminalSession.write("\n⚠️ Python stdlib not available\n")
                terminalSession.write("Please ensure Python assets are properly configured\n\n")
            }
        }
        
        return sessionId
    }

    private fun showMissingPythonBinaryError(terminalSession: TerminalSession) {
        terminalSession.write("\n" + "=".repeat(60) + "\n")
        terminalSession.write("⚠️  Python Interpreter Not Available\n")
        terminalSession.write("=".repeat(60) + "\n\n")
        terminalSession.write("The Python standard library is extracted, but the Python\n")
        terminalSession.write("interpreter binary (python3) is not bundled with this app.\n\n")
        terminalSession.write("To use Python in Terminal:\n")
        terminalSession.write("  1. Install Termux and run: pkg install python\n")
        terminalSession.write("  2. Or build CPython for Android and package it\n\n")
        terminalSession.write("Falling back to standard shell...\n\n")
    }

    companion object {
        private const val DEFAULT_LANG = "en_US.UTF-8"
        private const val DEFAULT_COLOR = "truecolor"
        private const val DEFAULT_TERM = "xterm-256color"
    }
}