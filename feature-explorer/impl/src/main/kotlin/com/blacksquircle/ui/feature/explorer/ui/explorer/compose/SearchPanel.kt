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

package com.blacksquircle.ui.feature.explorer.ui.explorer.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.button.IconButton
import com.blacksquircle.ui.ds.button.IconButtonSizeDefaults
import com.blacksquircle.ui.ds.textfield.TextField
import com.blacksquircle.ui.feature.explorer.R
import com.blacksquircle.ui.filesystem.base.model.FileModel
import com.blacksquircle.ui.ds.R as UiR

/**
 * Global search panel similar to VSCode's search functionality
 */
@Composable
internal fun SearchPanel(
    searchQuery: String,
    searchResults: List<FileSearchResult>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClearClicked: () -> Unit,
    onResultClicked: (FileModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var includePattern by remember { mutableStateOf("") }
    var excludePattern by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Title header (matching ExplorerToolbar style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material.Text(
                text = stringResource(R.string.explorer_search_title),
                style = SquircleTheme.typography.text12Regular,
                fontWeight = FontWeight.Bold,
                color = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        // Search input field
        TextField(
            inputText = searchQuery,
            onInputChanged = onQueryChanged,
            placeholderText = stringResource(R.string.explorer_search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            endContent = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        iconResId = UiR.drawable.ic_close,
                        iconButtonSize = IconButtonSizeDefaults.XS,
                        onClick = onClearClicked
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Search options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Include pattern
            TextField(
                inputText = includePattern,
                onInputChanged = { includePattern = it },
                placeholderText = stringResource(R.string.explorer_search_include_placeholder),
                modifier = Modifier.weight(1f)
            )

            // Exclude pattern
            TextField(
                inputText = excludePattern,
                onInputChanged = { excludePattern = it },
                placeholderText = stringResource(R.string.explorer_search_exclude_placeholder),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Options row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Match case toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { matchCase = !matchCase }
            ) {
                androidx.compose.material.Text(
                    text = if (matchCase) "[✓]" else "[ ]",
                    style = SquircleTheme.typography.text14Regular,
                    color = if (matchCase) SquircleTheme.colors.colorPrimary else Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                androidx.compose.material.Text(
                    text = stringResource(R.string.explorer_search_match_case_label),
                    style = SquircleTheme.typography.text14Regular,
                    color = if (matchCase) SquircleTheme.colors.colorTextAndIconPrimary else Color.Gray
                )
            }

            // Regex toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { useRegex = !useRegex }
            ) {
                androidx.compose.material.Text(
                    text = if (useRegex) "[✓]" else "[ ]",
                    style = SquircleTheme.typography.text14Regular,
                    color = if (useRegex) SquircleTheme.colors.colorPrimary else Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                androidx.compose.material.Text(
                    text = stringResource(R.string.explorer_search_regex_label),
                    style = SquircleTheme.typography.text14Regular,
                    color = if (useRegex) SquircleTheme.colors.colorTextAndIconPrimary else Color.Gray
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Search results
        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.blacksquircle.ui.ds.progress.CircularProgress()
            }
        } else if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material.Text(
                    text = stringResource(R.string.explorer_search_no_results),
                    style = SquircleTheme.typography.text16Regular,
                    color = Color.Gray
                )
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(searchResults) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = { onResultClicked(result.file) }
                    )
                }
            }
        } else {
            // Empty state - show instructions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material.Text(
                    text = stringResource(R.string.explorer_search_instructions),
                    style = SquircleTheme.typography.text16Regular,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: FileSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // File path
        androidx.compose.material.Text(
            text = result.file.path,
            style = SquircleTheme.typography.text14Regular,
            color = SquircleTheme.colors.colorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Preview lines with matches
        result.previewLines.take(3).forEach { line ->
            Spacer(Modifier.height(2.dp))
            androidx.compose.material.Text(
                text = line,
                style = SquircleTheme.typography.text12Regular,
                color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Match count
        if (result.matchCount > 0) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material.Text(
                text = stringResource(R.string.explorer_search_match_count, result.matchCount),
                style = SquircleTheme.typography.text12Regular,
                color = Color.Gray
            )
        }
    }
}

/**
 * Data class representing a search result
 */
data class FileSearchResult(
    val file: FileModel,
    val previewLines: List<String>,
    val matchCount: Int
)
