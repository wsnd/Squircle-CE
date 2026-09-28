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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class GitCredentials(
    val username: String,
    val password: String,
)

data class GitIdentity(
    val name: String,
    val email: String,
)

data class GitResult(
    val exitCode: Int,
    /** Combined stdout and stderr — git reports progress on stderr. */
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

/**
 * Runs the bundled native git binary.
 *
 * The Git panel and the terminal share this runner so that both operate on the
 * same git version, the same configuration (~/.gitconfig, ~/.ssh, credential
 * helpers) and the same set of helpers (LFS, remote helpers, ...).
 */
class GitCommandRunner(private val context: Context) {

    /**
     * @param directory working directory (the repository).
     * @param args git arguments, without the leading `git`.
     * @param credentials supplied to git through GIT_ASKPASS when non-null.
     * @param identity injected as `-c user.name=... -c user.email=...` so that
     * the app's settings win over whatever ~/.gitconfig says.
     * @param onProgress invoked for every output line, for long operations.
     */
    suspend fun run(
        directory: File,
        args: List<String>,
        credentials: GitCredentials? = null,
        identity: GitIdentity? = null,
        onProgress: ((String) -> Unit)? = null,
    ): GitResult = withContext(Dispatchers.IO) {
        val git = GitBinary.install(context)
            ?: return@withContext GitResult(EXIT_UNAVAILABLE, "Git executable is not available")

        val command = buildList {
            add(git.absolutePath)
            if (identity != null) {
                add("-c")
                add("user.name=${identity.name}")
                add("-c")
                add("user.email=${identity.email}")
            }
            addAll(args)
        }

        val process = runCatching {
            ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
                .apply { environment().putAll(GitBinary.environment(context, credentials)) }
                .start()
        }.getOrElse { e ->
            Timber.e(e, "Failed to start git: ${command.joinToString(" ")}")
            return@withContext GitResult(EXIT_UNAVAILABLE, e.message.orEmpty())
        }

        val output = StringBuilder()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.appendLine(line)
                    onProgress?.invoke(line)
                }
            }
            val exitCode = process.waitFor()
            Timber.d("git ${args.firstOrNull().orEmpty()} -> $exitCode")
            GitResult(exitCode, output.toString())
        } catch (e: Exception) {
            Timber.e(e, "Failed to read git output")
            process.destroy()
            GitResult(EXIT_UNAVAILABLE, output.toString())
        }
    }

    companion object {
        const val EXIT_UNAVAILABLE = -1
    }
}