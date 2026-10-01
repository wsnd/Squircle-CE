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

package com.blacksquircle.ui.feature.git.data.repository

import com.blacksquircle.ui.core.git.GitCommandRunner
import com.blacksquircle.ui.core.git.GitCredentials
import com.blacksquircle.ui.core.git.GitIdentity
import com.blacksquircle.ui.core.git.GitLogParser
import com.blacksquircle.ui.core.git.GitStatusEntry
import com.blacksquircle.ui.core.git.GitStatusParser
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.core.settings.SettingsManager
import com.blacksquircle.ui.feature.git.api.model.ChangeType
import com.blacksquircle.ui.feature.git.api.model.GitChange
import com.blacksquircle.ui.feature.git.api.model.GitCommit
import com.blacksquircle.ui.feature.git.domain.exception.GitException
import com.blacksquircle.ui.feature.git.domain.exception.GitPullException
import com.blacksquircle.ui.feature.git.domain.exception.GitPushException
import com.blacksquircle.ui.feature.git.domain.repository.GitRepository
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Git operations implemented on top of the bundled native git binary.
 *
 * The Git panel and the terminal share [GitCommandRunner], so both go through
 * the same git version, the same configuration ( ~/.gitconfig, ~/.ssh,
 * credential helpers) and the same helpers — no more divergence between what
 * the sidebar reports and what `git` in the terminal does.
 */
internal class GitRepositoryImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val settingsManager: SettingsManager,
    private val gitCommandRunner: GitCommandRunner,
) : GitRepository {

    override suspend fun currentBranch(repository: String): String {
        return withContext(dispatcherProvider.io()) {
            val branch = git(repository, "rev-parse", "--abbrev-ref", "HEAD")
            val name = branch.output.trim()
            if (!branch.isSuccess || name == DETACHED_HEAD) {
                // Detached HEAD or a fresh repository without commits yet.
                val commit = git(repository, "rev-parse", "--short", "HEAD")
                if (commit.isSuccess) commit.output.trim() else DEFAULT_BRANCH
            } else {
                name
            }
        }
    }

    override suspend fun branchList(repository: String): List<String> {
        return withContext(dispatcherProvider.io()) {
            val result = git(repository, "branch", "--all", "--format=%(refname:short)")
            if (!result.isSuccess) return@withContext emptyList()

            val branches = result.output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.endsWith("/HEAD") }
                .toMutableList()

            val current = currentBranch(repository)
            if (current !in branches) {
                branches.add(0, current)
            }
            branches
        }
    }

    override suspend fun changesList(repository: String): List<GitChange> {
        return withContext(dispatcherProvider.io()) {
            status(repository).map { it.toChange() }
        }
    }

    override suspend fun commitCount(repository: String): Int {
        return withContext(dispatcherProvider.io()) {
            val upstream = git(repository, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
            if (!upstream.isSuccess) {
                // No remote tracking branch configured.
                return@withContext NO_UPSTREAM
            }
            val count = git(repository, "rev-list", "--count", "${upstream.output.trim()}..HEAD")
            count.output.trim().toIntOrNull() ?: NO_UPSTREAM
        }
    }

    override suspend fun log(repository: String, limit: Int): List<GitCommit> {
        return withContext(dispatcherProvider.io()) {
            val result = git(
                repository,
                "log",
                "--max-count=$limit",
                "--date-order",
                "--pretty=format:${GitLogParser.LOG_FORMAT}",
            )
            if (!result.isSuccess) return@withContext emptyList()

            GitLogParser.parse(result.output).map { entry ->
                GitCommit(
                    sha = entry.sha,
                    shortSha = entry.shortSha,
                    message = entry.subject,
                    authorName = entry.authorName,
                    authorEmail = entry.authorEmail,
                    timestamp = entry.timestamp,
                    refs = entry.refs,
                    isMerge = entry.parents.size > 1,
                )
            }
        }
    }

    override suspend fun commitFiles(repository: String, sha: String): List<GitChange> {
        return withContext(dispatcherProvider.io()) {
            // `-m --first-parent` makes a merge commit report what it brought
            // in relative to its first parent — without it, merge commits come
            // back with an empty file list.
            val result = git(
                repository,
                "show",
                "-m",
                "--first-parent",
                "--name-status",
                "--format=",
                sha,
            )
            if (!result.isSuccess) return@withContext emptyList()

            result.output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@mapNotNull null
                    val status = parts[0].firstOrNull() ?: return@mapNotNull null
                    // Renames and copies report "R100<old><new>".
                    val path = if (parts.size >= 3) parts[2] else parts[1]
                    if (path.isEmpty()) return@mapNotNull null
                    val changeType = when (status) {
                        'A' -> ChangeType.ADDED
                        'D' -> ChangeType.DELETED
                        else -> ChangeType.MODIFIED
                    }
                    GitChange(name = path, changeType = changeType)
                }
                .toList()
        }
    }

    override suspend fun fetch(repository: String) {
        withContext(dispatcherProvider.io()) {
            val args = buildList {
                add("fetch")
                add(GIT_ORIGIN)
                add("--prune")
                add("--recurse-submodules=" + if (settingsManager.gitRecursiveSubmodules) "yes" else "on-demand")
            }
            val result = gitCommandRunner.run(
                directory = File(repository),
                args = args,
                credentials = credentials(),
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git fetch failed" })
            }
        }
    }

    override suspend fun pull(repository: String) {
        withContext(dispatcherProvider.io()) {
            val result = gitCommandRunner.run(
                directory = File(repository),
                args = listOf("pull", GIT_ORIGIN),
                credentials = credentials(),
            )
            if (!result.isSuccess) {
                throw GitPullException(result.output.trim().ifEmpty { "git pull failed" })
            }
        }
    }

    override suspend fun commit(
        repository: String,
        changes: List<GitChange>,
        message: String,
        isAmend: Boolean
    ) {
        withContext(dispatcherProvider.io()) {
            changes.forEach { change ->
                when (change.changeType) {
                    ChangeType.ADDED -> git(repository, "add", "--", change.name)
                    ChangeType.MODIFIED -> git(repository, "add", "--", change.name)
                    ChangeType.DELETED -> git(repository, "rm", "--", change.name)
                }
            }

            val args = buildList {
                add("commit")
                add("--message")
                add(message)
                if (isAmend) add("--amend")
            }
            val result = gitCommandRunner.run(
                directory = File(repository),
                args = args,
                identity = identity(),
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git commit failed" })
            }
        }
    }

    override suspend fun push(repository: String, force: Boolean) {
        withContext(dispatcherProvider.io()) {
            val refspec = pushRefspec(repository)
            val args = buildList {
                add("push")
                if (force) add("--force")
                add(GIT_ORIGIN)
                add(refspec)
            }
            val result = gitCommandRunner.run(
                directory = File(repository),
                args = args,
                credentials = credentials(),
            )
            if (!result.isSuccess) {
                throw GitPushException(result.output.trim().ifEmpty { "git push failed" })
            }
        }
    }

    override suspend fun checkout(repository: String, branchName: String) {
        withContext(dispatcherProvider.io()) {
            val args = if (branchName.startsWith("$GIT_ORIGIN/")) {
                val localBranchName = branchName.removePrefix("$GIT_ORIGIN/")
                if (localBranchExists(repository, localBranchName)) {
                    listOf("checkout", localBranchName)
                } else {
                    listOf("checkout", "-b", localBranchName, branchName)
                }
            } else {
                listOf("checkout", branchName)
            }

            val result = gitCommandRunner.run(
                directory = File(repository),
                args = args,
                credentials = credentials(),
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git checkout failed" })
            }
        }
    }

    override suspend fun checkoutNew(repository: String, branchName: String, branchBase: String) {
        withContext(dispatcherProvider.io()) {
            val startPoint = if (branchBase.startsWith("$GIT_ORIGIN/")) {
                branchBase
            } else {
                "refs/heads/$branchBase"
            }
            val result = gitCommandRunner.run(
                directory = File(repository),
                args = listOf("checkout", "-b", branchName, startPoint),
                credentials = credentials(),
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git checkout failed" })
            }
        }
    }

    override suspend fun init(directory: String) {
        withContext(dispatcherProvider.io()) {
            val result = gitCommandRunner.run(
                directory = File(directory),
                args = listOf("-c", "init.defaultBranch=$DEFAULT_BRANCH", "init"),
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git init failed" })
            }
        }
    }

    override suspend fun stage(repository: String, change: GitChange) {
        withContext(dispatcherProvider.io()) {
            git(repository, "add", "--", change.name)
        }
    }

    override suspend fun unstage(repository: String, change: GitChange) {
        withContext(dispatcherProvider.io()) {
            git(repository, "reset", "--", change.name)
        }
    }

    override suspend fun stageAll(repository: String) {
        withContext(dispatcherProvider.io()) {
            git(repository, "add", "--all")
        }
    }

    override suspend fun unstageAll(repository: String) {
        withContext(dispatcherProvider.io()) {
            git(repository, "reset")
        }
    }

    override suspend fun discard(repository: String, change: GitChange) {
        withContext(dispatcherProvider.io()) {
            if (change.changeType == ChangeType.ADDED) {
                // Untracked files are not in the index — remove them directly.
                val file = File(repository, change.name)
                if (file.exists()) {
                    file.deleteRecursively()
                }
            } else {
                git(repository, "checkout", "--", change.name)
            }
        }
    }

    override suspend fun stagedChanges(repository: String): List<GitChange> {
        return withContext(dispatcherProvider.io()) {
            status(repository)
                .filter { it.indexStatus != GitStatusEntry.UNCHANGED && it.indexStatus != GitStatusEntry.UNTRACKED }
                .map { it.toChange() }
        }
    }

    override suspend fun unstagedChanges(repository: String): List<GitChange> {
        return withContext(dispatcherProvider.io()) {
            status(repository)
                .filter { it.worktreeStatus != GitStatusEntry.UNCHANGED }
                .map { it.toChange() }
        }
    }

    private suspend fun status(repository: String): List<GitStatusEntry> {
        val result = git(repository, "status", "--porcelain=v1", "-z")
        if (!result.isSuccess) return emptyList()
        return GitStatusParser.parse(result.output)
    }

    private suspend fun localBranchExists(repository: String, branchName: String): Boolean {
        val result = git(repository, "branch", "--list", "--format=%(refname:short)", branchName)
        return result.output.lineSequence().any { it.trim() == branchName }
    }

    /**
     * `HEAD:refs/heads/<branch>` keeps the push target explicit even when no
     * upstream is configured. In a detached HEAD there is no branch to push, so
     * plain HEAD is used and git reports the situation itself.
     */
    private suspend fun pushRefspec(repository: String): String {
        val symbolic = git(repository, "symbolic-ref", "--short", "HEAD")
        val branch = symbolic.output.trim()
        return if (symbolic.isSuccess && branch.isNotEmpty()) {
            "HEAD:refs/heads/$branch"
        } else {
            "HEAD"
        }
    }

    private suspend fun git(
        repository: String,
        vararg args: String
    ) = gitCommandRunner.run(
        directory = File(repository),
        args = args.toList(),
        credentials = credentials(),
    )

    private fun credentials(): GitCredentials? {
        val username = settingsManager.gitCredentialsUsername
        val password = settingsManager.gitCredentialsPassword
        return if (username.isNotBlank() && password.isNotBlank()) {
            GitCredentials(username, password)
        } else {
            // Fall back to ~/.gitconfig, credential helpers or SSH keys.
            null
        }
    }

    private fun identity(): GitIdentity {
        return GitIdentity(
            name = settingsManager.gitUserName,
            email = settingsManager.gitUserEmail,
        )
    }

    private fun GitStatusEntry.toChange(): GitChange {
        val changeType = when {
            indexStatus == 'D' || worktreeStatus == 'D' -> ChangeType.DELETED
            indexStatus == 'A' || indexStatus == '?' || worktreeStatus == '?' -> ChangeType.ADDED
            else -> ChangeType.MODIFIED
        }
        return GitChange(name = path, changeType = changeType)
    }

    companion object {
        private const val GIT_ORIGIN = "origin"
        private const val DETACHED_HEAD = "HEAD"
        private const val DEFAULT_BRANCH = "main"
        private const val NO_UPSTREAM = -1
    }
}