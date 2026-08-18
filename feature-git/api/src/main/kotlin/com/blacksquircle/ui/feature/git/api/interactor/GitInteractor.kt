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

package com.blacksquircle.ui.feature.git.api.interactor

import com.blacksquircle.ui.feature.git.api.model.GitChange
import com.blacksquircle.ui.filesystem.base.model.FileModel
import kotlinx.coroutines.flow.Flow

interface GitInteractor {

    suspend fun checkRepository(repository: String?): String

    suspend fun cloneRepository(fileModel: FileModel, url: String): Flow<String>

    suspend fun currentBranch(repository: String): String

    suspend fun changesList(repository: String): List<GitChange>

    /**
     * Initialize a new git repository at the given path.
     */
    suspend fun initRepository(directory: String)

    /**
     * Stage a single file for commit.
     */
    suspend fun stage(repository: String, change: GitChange)

    /**
     * Unstage a single file.
     */
    suspend fun unstage(repository: String, change: GitChange)

    /**
     * Stage all changes.
     */
    suspend fun stageAll(repository: String)

    /**
     * Unstage all staged changes.
     */
    suspend fun unstageAll(repository: String)

    /**
     * Discard changes for a single file (restore to HEAD).
     */
    suspend fun discard(repository: String, change: GitChange)

    /**
     * Get list of staged changes only.
     */
    suspend fun stagedChanges(repository: String): List<GitChange>

    /**
     * Get list of unstaged changes only.
     */
    suspend fun unstagedChanges(repository: String): List<GitChange>

    /**
     * Commit with a message (all staged + selected unstaged changes).
     */
    suspend fun commit(repository: String, message: String)
}