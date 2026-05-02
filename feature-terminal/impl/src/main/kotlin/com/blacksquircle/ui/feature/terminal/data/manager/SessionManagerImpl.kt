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
        val candidates = listOf(
            File(nativeLibDir, "libpython_exe.so"),
            File(nativeLibDir, "python3"), // Some systems might extract it differently
            File(nativeLibDir, "python")
        )
        
        val pythonExe = candidates.find { it.exists() }

        try {
            if (pythonExe != null) {
                // Ensure executable permission
                if (!pythonExe.canExecute()) {
                    pythonExe.setExecutable(true)
                }
                
                val links = listOf("python", "python3", "python3.14")
                links.forEach { name ->
                    val link = File(binDir, name)
                    if (link.exists()) link.delete()
                    android.system.Os.symlink(pythonExe.absolutePath, link.absolutePath)
                }
                Timber.d("Setup bin directory: python linked to ${pythonExe.absolutePath}")
            } else {
                Timber.e("Python executable not found in native library directory: $nativeLibDir")
                // Check if we can find it in the architecture-specific subfolder (sometimes happens)
                val abi = android.os.Build.SUPPORTED_ABIS[0]
                val abiDir = File(nativeLibDir, abi)
                if (abiDir.exists()) {
                    val abiPython = File(abiDir, "libpython_exe.so")
                    if (abiPython.exists()) {
                        abiPython.setExecutable(true)
                        listOf("python", "python3", "python3.14").forEach { name ->
                            val link = File(binDir, name)
                            if (link.exists()) link.delete()
                            android.system.Os.symlink(abiPython.absolutePath, link.absolutePath)
                        }
                        Timber.d("Setup bin directory: python linked to ${abiPython.absolutePath} (ABI subfolder)")
                    }
                }
            }
            
            // Create shims for common build tools to provide better error messages
            setupBuildShims(binDir)
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup bin directory")
        }
    }

    private fun setupBuildShims(binDir: File) {
        val shims = listOf("make", "gcc", "g++", "cc", "c++", "cmake", "ninja")
        val shimContent = """
            #!/system/bin/sh
            echo "Error: Build tool '${'$'}(basename ${'$'}0)' is not available in the mobile environment."
            echo "Native compilation is not supported on-device in Squircle-CE."
            echo "Please use pre-compiled wheels: pip install <package> --only-binary=:all:"
            exit 1
        """.trimIndent()

        shims.forEach { shim ->
            val shimFile = File(binDir, shim)
            try {
                if (shimFile.exists()) {
                    shimFile.delete()
                }
                shimFile.writeText(shimContent)
                shimFile.setExecutable(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create shim: ${'$'}shim")
            }
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
        // Always put binDir at the front to prioritize our shims/symlinks
        env[ENV_PATH] = if (currentPath.isEmpty()) binDir else "$binDir:$currentPath"

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
            val dollarSign = "\$"
            val initContent = """
                # Silent initialization
                export sitePackages="$pythonPath/lib/python3.14/site-packages"
                
                # Check if python works, set alias if PATH lookup fails (extra safety)
                if ! command -v python > /dev/null 2>&1; then
                    if [ -f "$binDir/python" ]; then
                         alias python="$binDir/python"
                    elif [ -f "$nativeLibDir/libpython_exe.so" ]; then
                         alias python="$nativeLibDir/libpython_exe.so"
                    fi
                fi

                if [ ! -d "$dollarSign{sitePackages}/pip" ]; then
                    echo "Initializing pip (one-time setup)..."
                    python -m ensurepip --default-pip > /dev/null 2>&1
                    if [ $dollarSign? -ne 0 ]; then
                        echo "❌ Failed to initialize pip."
                    fi
                fi
                echo "Python 3.14 environment initialized."
                
                # Define pip as a shell function to avoid noexec issues
                pip() {
                    python -m pip "$dollarSign@" \
                        --index-url https://anshdadwal.is-a.dev/p4a-wheels/p4a/ \
                        --extra-index-url https://pypi.org/simple \
                        --only-binary=:all:
                    local exit_code=$dollarSign?
                    
                    # Fix platform tag mismatch (.linux-gnu.so -> .linux-android.so)
                    if [ $dollarSign{exit_code} -eq 0 ]; then
                        echo "Checking for platform tag mismatches in $dollarSign{sitePackages}..."
                        find "$dollarSign{sitePackages}" -name "*.linux-gnu.so" | while read -r file; do
                            new_file=$dollarSign(echo "$dollarSign{file}" | sed 's/\.linux-gnu\.so$/\.linux-android\.so/')
                            mv "$dollarSign{file}" "$dollarSign{new_file}"
                        done
                    else
                        echo ""
                        echo "Error: pip command failed (exit code: $dollarSign{exit_code})"
                        echo "Note: Native compilation is NOT supported. Only pre-compiled wheels can be installed."
                    fi
                    return $dollarSign{exit_code}
                }
                
                # Show current folder name in prompt
                export PS1='${dollarSign}{PWD##*/} ${dollarSign}'
            """.trimIndent()
            initScript.writeText(initContent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write init script")
        }
        
        val environment = HashMap<String, String>()
        setupCommonEnvironment(environment, runtime)
        
        // Setup Python specific environment
        val sitePackages = "$pythonPath/lib/python3.14/site-packages"
        environment["PYTHONHOME"] = pythonPath ?: ""
        environment["PYTHONPATH"] = "$pythonPath/lib/python3.14:$pythonPath/lib/python3.14/lib-dynload:$sitePackages"
        environment["LD_LIBRARY_PATH"] = "$nativeLibDir:$pythonPath/lib/python3.14/lib-dynload:$sitePackages/numpy/_core"
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