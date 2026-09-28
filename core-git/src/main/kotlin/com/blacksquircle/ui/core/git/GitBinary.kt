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

package com.blacksquircle.ui.core.git

import android.content.Context
import android.system.Os
import timber.log.Timber
import java.io.File

/**
 * Installs the bundled Termux git binaries so that they can be executed both
 * from the terminal and from the Git panel.
 *
 * AGP only ships files named `lib*.so` inside nativeLibraryDir, so the git
 * executables arrive as `libgit.so`, `libgit-upload-pack.so`, ... and have to
 * be exposed under their canonical short names (`git`, `git-upload-pack`, ...)
 * through symlinks in a directory that is on PATH.
 *
 * Everything matching `git`, `scalar`, `curl` or `wcurl` (with an optional
 * `-suffix`) is linked automatically, so helpers added by newer Termux git
 * releases flow through without code changes.
 */
object GitBinary {

    private const val TAG = "GitBinary"
    private const val BIN_DIR_NAME = "bin"
    private const val ASKPASS_NAME = "git-askpass.sh"
    private const val GIT_NAME = "git"

    private val HELPER_PATTERN = Regex("^lib(git|scalar|curl|wcurl)(-.+)?\\.so$")

    @Volatile
    private var installedGit: File? = null

    /**
     * @return the `git` executable, or null when no git binary is bundled or
     * the symlink directory could not be prepared.
     */
    fun install(context: Context): File? {
        // Creating ~180 symlinks on every command would be wasteful, so the
        // resolved executable is cached for the lifetime of the process.
        installedGit?.let { cached ->
            if (cached.canExecute()) return cached
        }

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binDir = File(context.filesDir, BIN_DIR_NAME)
        if (!binDir.exists() && !binDir.mkdirs()) {
            Timber.e("Failed to create $binDir")
            return null
        }

        val helpers = try {
            File(nativeLibDir).listFiles { file ->
                file.isFile && HELPER_PATTERN.matches(file.name)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate native libs")
            null
        } ?: return null

        var gitExecutable: File? = null
        for (source in helpers) {
            if (!source.canExecute()) {
                source.setExecutable(true, false)
            }
            val shortName = source.name.removePrefix("lib").removeSuffix(".so")
            val link = File(binDir, shortName)
            link.delete()
            try {
                Os.symlink(source.absolutePath, link.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create git symlink: ${source.name}")
                continue
            }
            if (shortName == GIT_NAME) {
                gitExecutable = link
            }
        }

        writeAskPass(binDir)
        installedGit = gitExecutable
        return gitExecutable
    }

    /**
     * Environment required to run the bundled git: PATH resolves the helper
     * symlinks, LD_LIBRARY_PATH resolves libcurl/libssl/libiconv/... that git
     * links against, and HOME is where git looks for ~/.gitconfig, ~/.ssh and
     * the credential store.
     */
    fun environment(context: Context, credentials: GitCredentials? = null): Map<String, String> {
        val binDir = File(context.filesDir, BIN_DIR_NAME).absolutePath
        val nativeLibDir = context.applicationInfo.nativeLibraryDir.orEmpty()
        return buildMap {
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("PATH", "$binDir:/system/bin:/system/xbin")
            put("LD_LIBRARY_PATH", "$nativeLibDir:/system/lib64:/system/lib")
            // Never block on an interactive prompt: credentials are supplied
            // through GIT_ASKPASS instead.
            put("GIT_TERMINAL_PROMPT", "0")
            if (credentials != null) {
                put("GIT_ASKPASS", File(binDir, ASKPASS_NAME).absolutePath)
                put(ENV_ASKPASS_USERNAME, credentials.username)
                put(ENV_ASKPASS_PASSWORD, credentials.password)
            }
        }
    }

    /**
     * GIT_ASKPASS helper: git executes it once per credential it needs and
     * reads the answer from stdout. The secrets are injected through the
     * process environment instead of the script itself because filesDir is
     * readable by anything running as this app.
     */
    private fun writeAskPass(binDir: File) {
        val script = File(binDir, ASKPASS_NAME)
        val dollar = '$'
        val content = """
            #!/system/bin/sh
            case "$dollar{1:-}" in
                *Username*) echo "$$ENV_ASKPASS_USERNAME" ;;
                *Password*) echo "$$ENV_ASKPASS_PASSWORD" ;;
                *) echo "$$ENV_ASKPASS_PASSWORD" ;;
            esac
        """.trimIndent()
        try {
            script.writeText(content + "\n")
            script.setExecutable(true, false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write GIT_ASKPASS helper")
        }
    }

    const val ENV_ASKPASS_USERNAME = "SQ_GIT_USERNAME"
    const val ENV_ASKPASS_PASSWORD = "SQ_GIT_PASSWORD"
}