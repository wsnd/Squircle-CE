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

package com.blacksquircle.ui.feature.editor.ui.editor.model

import androidx.compose.runtime.Immutable
import com.blacksquircle.ui.feature.git.api.model.GitChange

/**
 * State holder for the Git (Source Control) panel.
 */
@Immutable
internal data class GitPanelState(
    val stagedChanges: List<GitChange> = emptyList(),
    val unstagedChanges: List<GitChange> = emptyList(),
    val currentBranch: String = "",
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String = "",
    val repositoryPath: String = "",
    /**
     * When true, the panel shows an "Initialize Repository" button
     * because the current directory does not have a .git folder.
     */
    val showInitView: Boolean = false,
    /**
     * Inline commit message text.
     */
    val commitMessage: String = "",
    /**
     * Whether a commit is in progress.
     */
    val isCommitting: Boolean = false,
) {
    /**
     * Combined list of all changes for backward compatibility.
     */
    val changesList: List<GitChange>
        get() = stagedChanges + unstagedChanges
}
