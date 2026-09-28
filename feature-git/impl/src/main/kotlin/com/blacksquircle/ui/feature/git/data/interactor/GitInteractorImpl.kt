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

package com.blacksquircle.ui.feature.git.data.interactor

import com.blacksquircle.ui.core.git.GitCommandRunner
import com.blacksquircle.ui.core.git.GitCredentials
import com.blacksquircle.ui.core.settings.SettingsManager
import com.blacksquircle.ui.feature.git.api.exception.InvalidCredentialsException
import com.blacksquircle.ui.feature.git.api.exception.RepositoryNotFoundException
import com.blacksquircle.ui.feature.git.api.exception.UnsupportedFilesystemException
import com.blacksquircle.ui.feature.git.api.interactor.GitInteractor
import com.blacksquircle.ui.feature.git.api.model.GitChange
import com.blacksquircle.ui.feature.git.domain.exception.GitException
import com.blacksquircle.ui.feature.git.domain.repository.GitRepository
import com.blacksquircle.ui.filesystem.base.exception.FileNotFoundException
import com.blacksquircle.ui.filesystem.base.model.FileModel
import com.blacksquircle.ui.filesystem.local.LocalFilesystem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

internal class GitInteractorImpl(
    private val settingsManager: SettingsManager,
    private val gitRepository: GitRepository,
    private val gitCommandRunner: GitCommandRunner,
) : GitInteractor {

    override suspend fun checkRepository(repository: String?): String {
        if (repository == null || !File(repository, ".git").exists()) {
            throw RepositoryNotFoundException()
        }
        return repository
    }

    override suspend fun cloneRepository(fileModel: FileModel, url: String): Flow<String> {
        return callbackFlow {
            if (fileModel.filesystemUuid != LocalFilesystem.LOCAL_UUID) {
                throw UnsupportedFilesystemException()
            }

            if (settingsManager.gitCredentialsUsername.isBlank() ||
                settingsManager.gitCredentialsPassword.isBlank() ||
                settingsManager.gitUserEmail.isBlank() ||
                settingsManager.gitUserName.isBlank()
            ) {
                throw InvalidCredentialsException()
            }

            val file = File(fileModel.path)
            if (!file.exists() || !file.isDirectory) {
                throw FileNotFoundException(fileModel.path)
            }

            val args = buildList {
                add("clone")
                // Progress is reported on stderr; the runner merges it into
                // the stream we forward to the UI.
                add("--progress")
                if (settingsManager.gitSubmodules) {
                    add("--recurse-submodules")
                }
                add(url)
                add(file.absolutePath)
            }

            val result = gitCommandRunner.run(
                directory = file,
                args = args,
                credentials = GitCredentials(
                    username = settingsManager.gitCredentialsUsername,
                    password = settingsManager.gitCredentialsPassword,
                ),
                onProgress = { line -> trySend(line) },
            )
            if (!result.isSuccess) {
                throw GitException(result.output.trim().ifEmpty { "git clone failed" })
            }

            awaitClose()
        }
    }

    override suspend fun currentBranch(repository: String): String {
        return gitRepository.currentBranch(repository)
    }

    override suspend fun changesList(repository: String): List<GitChange> {
        return gitRepository.changesList(repository)
    }

    override suspend fun initRepository(directory: String) {
        gitRepository.init(directory)
    }

    override suspend fun stage(repository: String, change: GitChange) {
        gitRepository.stage(repository, change)
    }

    override suspend fun unstage(repository: String, change: GitChange) {
        gitRepository.unstage(repository, change)
    }

    override suspend fun stageAll(repository: String) {
        gitRepository.stageAll(repository)
    }

    override suspend fun unstageAll(repository: String) {
        gitRepository.unstageAll(repository)
    }

    override suspend fun discard(repository: String, change: GitChange) {
        gitRepository.discard(repository, change)
    }

    override suspend fun stagedChanges(repository: String): List<GitChange> {
        return gitRepository.stagedChanges(repository)
    }

    override suspend fun unstagedChanges(repository: String): List<GitChange> {
        return gitRepository.unstagedChanges(repository)
    }

    override suspend fun commit(repository: String, message: String) {
        // Inline commit: stage all and commit
        val allChanges = gitRepository.changesList(repository)
        gitRepository.stageAll(repository)
        gitRepository.commit(repository, allChanges, message, false)
    }

    companion object {
        private const val GIT_FOLDER = ".git"
    }
}