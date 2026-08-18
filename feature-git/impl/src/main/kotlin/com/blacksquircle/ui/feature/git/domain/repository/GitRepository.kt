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

package com.blacksquircle.ui.feature.git.domain.repository

import com.blacksquircle.ui.feature.git.api.model.GitChange

internal interface GitRepository {

    suspend fun currentBranch(repository: String): String
    suspend fun branchList(repository: String): List<String>
    suspend fun changesList(repository: String): List<GitChange>
    suspend fun commitCount(repository: String): Int

    suspend fun fetch(repository: String)
    suspend fun pull(repository: String)
    suspend fun commit(
        repository: String,
        changes: List<GitChange>,
        message: String,
        isAmend: Boolean
    )
    suspend fun push(repository: String, force: Boolean)
    suspend fun checkout(repository: String, branchName: String)
    suspend fun checkoutNew(repository: String, branchName: String, branchBase: String)

    /**
     * Initialize a new git repository at the given path.
     */
    suspend fun init(directory: String)

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
}