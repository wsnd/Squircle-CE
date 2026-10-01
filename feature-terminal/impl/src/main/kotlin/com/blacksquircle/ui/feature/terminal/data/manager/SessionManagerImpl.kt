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
import com.blacksquircle.ui.core.git.GitBinary
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

        setupBinDirectory()

        if (runtime.type == RuntimeType.PYTHON || args?.isPythonRepl == true) {
            return createPythonReplSession(sessionId, runtime, client, commands, args)
        }

        val environment = HashMap<String, String>()
        setupCommonEnvironment(environment, runtime)

        val shellPath = "/system/bin/sh"
        val shellArgs = arrayOf("-i")

        val terminalSession = TerminalSession(
            shellPath,
            args?.workingDir ?: runtime.homeDir,
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

    private fun setupBinDirectory() {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val candidates = listOf(
            File(nativeLibDir, "libpython_exe.so"),
            File(nativeLibDir, "python3"),
            File(nativeLibDir, "python")
        )

        val pythonExe = candidates.find { it.exists() }

        try {
            if (pythonExe != null) {
                if (!pythonExe.canExecute()) {
                    pythonExe.setExecutable(true)
                }

                val links = listOf("python", "python3", "python3.14")
                links.forEach { name ->
                    val link = File(binDir, name)
                    // Use standard Java delete to handle broken symlinks
                    link.delete()
                    try {
                        android.system.Os.symlink(pythonExe.absolutePath, link.absolutePath)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create symlink: $name")
                    }
                }
            }
            // Git binaries live in :core-git now, so the shell and the Git
            // panel share exactly the same executables and helpers.
            GitBinary.install(context)
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
            exit 1
        """.trimIndent()

        shims.forEach { shim ->
            val shimFile = File(binDir, shim)
            if (!shimFile.exists()) {
                try {
                    shimFile.writeText(shimContent)
                    shimFile.setExecutable(true)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create shim")
                }
            }
        }
    }

    private fun setupCommonEnvironment(env: HashMap<String, String>, runtime: TerminalRuntime) {
        env[ENV_HOME] = runtime.homeDir
        env[ENV_LANG] = DEFAULT_LANG
        env[ENV_TMPDIR] = runtime.tmpDir
        env[ENV_COLORTERM] = DEFAULT_COLOR
        env[ENV_TERM] = DEFAULT_TERM
        // No pager binary is bundled, and the inherited environment may point
        // PAGER at a program that does not exist ("pager"), which makes tools
        // like git fail with "unable to execute pager". Output is scrolled by
        // the terminal itself, so plain cat is the right behaviour here.
        // GIT_PAGER is set as well because it takes precedence over a
        // core.pager entry in the user's gitconfig.
        env["PAGER"] = DEFAULT_PAGER
        env["GIT_PAGER"] = DEFAULT_PAGER

        val binDir = File(context.filesDir, "bin").absolutePath
        val systemPath = "/system/bin:/system/xbin"
        val currentPath = System.getenv(ENV_PATH).orEmpty()

        // Ensure binDir is first, but also include essential system paths
        env[ENV_PATH] = if (currentPath.isEmpty()) {
            "$binDir:$systemPath"
        } else {
            "$binDir:$systemPath:$currentPath"
        }

        // Make native libs (libssl, libcrypto, libpcre2, libcurl, ...) discoverable
        // to dynamically-linked executables bundled in nativeLibraryDir — primarily
        // the Termux-built git binaries whose RUNPATH points at the Termux prefix.
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val currentLdPath = System.getenv("LD_LIBRARY_PATH").orEmpty()
        env["LD_LIBRARY_PATH"] = if (currentLdPath.isEmpty()) {
            "$nativeLibDir:/system/lib64:/system/lib"
        } else {
            "$nativeLibDir:$currentLdPath"
        }

        putToEnvIfInSystemEnv(env, "ANDROID_ASSETS")
        putToEnvIfInSystemEnv(env, "ANDROID_DATA")
        putToEnvIfInSystemEnv(env, "ANDROID_ROOT")
        putToEnvIfInSystemEnv(env, "ANDROID_STORAGE")
        putToEnvIfInSystemEnv(env, "EXTERNAL_STORAGE")

        setupPythonEnvironment(env)
    }

    /**
     * Expose the bundled CPython runtime to every shell session, not just the
     * Python REPL — scripts can be launched by invoking libpython_exe.so
     * directly from the terminal, and pip must resolve its site-level config
     * (pip.conf) from sys.prefix in that case too.
     *
     * Note: this only sets the variables when the standard library has already
     * been extracted on this device; forcing extraction here would block
     * terminal startup with ~260MB of I/O.
     */
    private fun setupPythonEnvironment(env: HashMap<String, String>) {
        val pythonHome = File(context.filesDir, "python")
        if (!pythonHome.exists()) return

        val pythonHomePath = pythonHome.absolutePath
        val stdlib = "$pythonHomePath/lib/python3.14"
        val sitePackages = "$stdlib/site-packages"

        env["PYTHONHOME"] = pythonHomePath
        env["PYTHONPATH"] = "$stdlib:$stdlib/lib-dynload:$sitePackages"
        // pip reads its config from $PIP_CONFIG_DIR/pip.conf before looking at
        // sys.prefix — set explicitly so index configuration applies no matter
        // how the interpreter is launched.
        env["PIP_CONFIG_DIR"] = pythonHomePath
        // pip installs console scripts (f2py, numpy-config, ...) into
        // $PYTHONHOME/bin — expose them and silence pip's script-location warning.
        val currentPath = env[ENV_PATH].orEmpty()
        env[ENV_PATH] = if (currentPath.isEmpty()) {
            "$pythonHomePath/bin"
        } else {
            "$pythonHomePath/bin:$currentPath"
        }
        // Native dependencies with version-suffixed file names (libjpeg.so.8,
        // libz.so.1, libxml2.so.16, ...) cannot be packaged into nativeLibraryDir
        // because AGP keeps only *.so from jniLibs, so the extractor unpacks
        // them into $PYTHONHOME/lib — add it to the linker search path.
        val depsLibDir = "$pythonHomePath/lib"
        val currentLdPath = env["LD_LIBRARY_PATH"].orEmpty()
        env["LD_LIBRARY_PATH"] = if (currentLdPath.isEmpty()) {
            "$depsLibDir:/system/lib64:/system/lib"
        } else {
            "$depsLibDir:$currentLdPath"
        }
    }

    override fun closeSession(sessionId: String) {
        val terminalSession = sessions[sessionId]?.session ?: return
        if (terminalSession.pid > 0) {
            terminalSession.finishIfRunning()
        }
        sessions.remove(sessionId)
        if (sessions.isEmpty()) {
            counter.set(0)
        }
    }

    private fun createPythonReplSession(
        sessionId: String,
        runtime: TerminalRuntime,
        client: TerminalSessionClientImpl,
        commands: MutableSharedFlow<TerminalCommand>,
        args: ShellArgs?
    ): String {
        Timber.d("Creating Python REPL session")

        val pythonPath = PythonStdlibExtractor.extractIfNeeded(context)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binDir = File(context.filesDir, "bin")
        val initScript = File(binDir, "init.sh")

        try {
            val dollarSign = "\$"
            val initContent = """
                # Silent initialization
                export sitePackages="$pythonPath/lib/python3.14/site-packages"
                
                # Setup python alias if binary is not in path or as a priority
                if [ -f "$binDir/python" ]; then
                    alias python="$binDir/python"
                    alias python3="$binDir/python"
                fi
                
                if [ ! -d "$dollarSign{sitePackages}/pip" ]; then
                    python -m ensurepip --default-pip > /dev/null 2>&1
                fi
                
                pip() {
                    python -m pip "$dollarSign@" --extra-index-url https://anshdadwal.is-a.dev/p4a-wheels/p4a/
                }
                
                export PS1='$dollarSign{PWD##*/} $dollarSign'
                
                # EXECUTE INJECTED COMMAND
                if [ -n "$dollarSign{STARTUP_COMMAND}" ]; then
                    _cmd="$dollarSign{STARTUP_COMMAND}"
                    unset STARTUP_COMMAND
                    echo "$dollarSign{PWD##*/} $dollarSign $dollarSign{_cmd}"
                    eval "$dollarSign{_cmd}"
                fi
            """.trimIndent()
            initScript.writeText(initContent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write init script")
        }

        val environment = HashMap<String, String>()
        setupCommonEnvironment(environment, runtime)

        val sitePackages = "$pythonPath/lib/python3.14/site-packages"
        environment["PYTHONHOME"] = pythonPath ?: ""
        environment["PYTHONPATH"] = "$pythonPath/lib/python3.14:$pythonPath/lib/python3.14/lib-dynload:$sitePackages"
        environment["PIP_CONFIG_DIR"] = pythonPath ?: ""

        // CRITICAL: Include system library paths to ensure libc and other system libs are found.
        // $pythonPath/lib holds the version-suffixed native dependencies unpacked
        // from assets (AGP drops them from jniLibs because they are not *.so).
        environment["LD_LIBRARY_PATH"] =
            "$nativeLibDir:$pythonPath/lib:$pythonPath/lib/python3.14/lib-dynload:/system/lib64:/system/lib"

        environment["PYTHONUNBUFFERED"] = "1"
        environment["ENV"] = initScript.absolutePath

        val command = args?.command
        if (command != null) {
            environment["STARTUP_COMMAND"] = command
        }

        val shellPath = "/system/bin/sh"
        val shellArgs = arrayOf("-i")

        val terminalSession = TerminalSession(
            shellPath,
            args?.workingDir ?: runtime.homeDir,
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

    companion object {
        private const val DEFAULT_LANG = "en_US.UTF-8"
        private const val DEFAULT_COLOR = "truecolor"
        private const val DEFAULT_TERM = "xterm-256color"
        private const val DEFAULT_PAGER = "cat"
    }
}