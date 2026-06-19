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
import com.blacksquircle.ui.feature.editor.domain.GlobalSearchUseCase.FileSearchResult

/**
 * State holder for the global search panel.
 */
@Immutable
internal data class GlobalSearchState(
    val query: String = "",
    val replaceShown: Boolean = false,
    val replaceText: String = "",
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wordsOnly: Boolean = false,
    val isSearching: Boolean = false,
    val results: List<FileSearchResult> = emptyList(),
    val totalMatchCount: Int = 0,
    val totalFileCount: Int = 0,
)
