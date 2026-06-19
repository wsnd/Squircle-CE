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

package com.blacksquircle.ui.feature.editor.ui.editor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.blacksquircle.ui.feature.editor.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.button.IconButton
import com.blacksquircle.ui.ds.button.IconButtonSizeDefaults
import com.blacksquircle.ui.ds.button.IconButtonStyleDefaults
import com.blacksquircle.ui.ds.button.TextButton
import com.blacksquircle.ui.ds.progress.CircularProgress
import com.blacksquircle.ui.ds.textfield.TextField
import com.blacksquircle.ui.feature.editor.domain.GlobalSearchUseCase.FileSearchResult
import com.blacksquircle.ui.feature.editor.domain.GlobalSearchUseCase.SearchResult
import com.blacksquircle.ui.feature.editor.ui.editor.compose.menu.FindMenu
import com.blacksquircle.ui.feature.editor.ui.editor.model.GlobalSearchState
import com.blacksquircle.ui.ds.R as UiR

/**
 * Global search panel - similar to VSCode sidebar search.
 * Searches file contents across the entire workspace directory.
 */
@Composable
internal fun GlobalSearchPanel(
    searchState: GlobalSearchState,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit = {},
    onReplaceTextChanged: (String) -> Unit = {},
    onToggleReplaceClicked: () -> Unit = {},
    onRegexClicked: () -> Unit = {},
    onMatchCaseClicked: () -> Unit = {},
    onWordsOnlyClicked: () -> Unit = {},
    onCloseSearchClicked: () -> Unit = {},
    onSearchSubmitted: () -> Unit = {},
    onClearClicked: () -> Unit = {},
    onResultClicked: (FileSearchResult) -> Unit = {},
    onReplaceAllClicked: () -> Unit = {},
    onReplaceResultClicked: (FileSearchResult) -> Unit = {},
) {
    val findFocusRequester = remember { FocusRequester() }
    val replaceFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SquircleTheme.colors.colorBackgroundSecondary)
    ) {
        // Title header (matching ExplorerToolbar style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.editor_global_search_title),
                style = SquircleTheme.typography.text12Regular,
                fontWeight = FontWeight.Bold,
                color = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
        // Search input area
        Row(Modifier.padding(horizontal = 4.dp)) {
            IconButton(
                iconResId = if (searchState.replaceShown) {
                    UiR.drawable.ic_menu_down
                } else {
                    UiR.drawable.ic_menu_right
                },
                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                iconButtonSize = IconButtonSizeDefaults.XS,
                onClick = onToggleReplaceClicked,
            )

            Column(Modifier.weight(1f)) {
                var menuExpanded by rememberSaveable { mutableStateOf(false) }

                TextField(
                    inputText = searchState.query,
                    onInputChanged = onQueryChanged,
                    placeholderText = stringResource(R.string.editor_global_search_placeholder),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchState.replaceShown) {
                                replaceFocusRequester.requestFocus()
                            } else {
                                onSearchSubmitted()
                            }
                        },
                    ),
                    endContent = {
                        Row {
                            IconButton(
                                iconResId = UiR.drawable.ic_dots_vertical,
                                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                                iconButtonSize = IconButtonSizeDefaults.XS,
                                onClick = { menuExpanded = !menuExpanded },
                                anchor = {
                                    FindMenu(
                                        expanded = menuExpanded,
                                        onDismiss = { menuExpanded = false },
                                        regex = searchState.regex,
                                        matchCase = searchState.matchCase,
                                        wordsOnly = searchState.wordsOnly,
                                        onRegexClicked = { menuExpanded = false; onRegexClicked() },
                                        onMatchCaseClicked = { menuExpanded = false; onMatchCaseClicked() },
                                        onWordsOnlyClicked = { menuExpanded = false; onWordsOnlyClicked() },
                                    )
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .focusRequester(findFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
                                if (searchState.replaceShown) {
                                    replaceFocusRequester.requestFocus()
                                } else {
                                    onSearchSubmitted()
                                }
                                true
                            } else {
                                false
                            }
                        }
                )

                LaunchedEffect(Unit) {
                    findFocusRequester.requestFocus()
                }

                if (searchState.replaceShown) {
                    Spacer(Modifier.height(8.dp))

                    TextField(
                        inputText = searchState.replaceText,
                        onInputChanged = onReplaceTextChanged,
                        placeholderText = stringResource(R.string.editor_global_search_replace_placeholder),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSearchSubmitted() },
                        ),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .focusRequester(replaceFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
                                    onSearchSubmitted()
                                    true
                                } else {
                                    false
                                }
                            }
                    )

                    LaunchedEffect(searchState.replaceShown) {
                        replaceFocusRequester.requestFocus()
                    }
                }
            }

            IconButton(
                iconResId = UiR.drawable.ic_close,
                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                iconButtonSize = IconButtonSizeDefaults.XS,
                onClick = onCloseSearchClicked,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
        ) {
            TextButton(
                text = stringResource(R.string.editor_global_search_button_search),
                onClick = onSearchSubmitted,
                debounce = false,
                modifier = Modifier.weight(1f)
            )
            if (searchState.replaceShown && searchState.results.isNotEmpty()) {
                TextButton(
                    text = stringResource(R.string.editor_global_search_button_replace_all),
                    onClick = onReplaceAllClicked,
                    debounce = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Divider(color = SquircleTheme.colors.colorOutline.copy(alpha = 0.3f))

        // Search results area
        when {
            searchState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgress()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.editor_global_search_searching),
                            style = SquircleTheme.typography.text14Regular,
                            color = SquircleTheme.colors.colorTextAndIconSecondary
                        )
                    }
                }
            }

            searchState.results.isNotEmpty() -> {
                // Results summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.editor_global_search_results_summary, searchState.totalMatchCount, searchState.totalFileCount),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary
                    )
                    TextButton(
                        text = stringResource(R.string.editor_global_search_button_clear),
                        onClick = onClearClicked,
                        debounce = false,
                    )
                }

                Divider(color = SquircleTheme.colors.colorOutline.copy(alpha = 0.3f))

                // File results list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(searchState.results) { fileResult ->
                        FileSearchResultItem(
                            result = fileResult,
                            searchQuery = searchState.query,
                            showReplaceAction = searchState.replaceShown,
                            onClick = { onResultClicked(fileResult) },
                            onReplace = { onReplaceResultClicked(fileResult) },
                        )
                    }
                }
            }

            searchState.query.isNotEmpty() && !searchState.isSearching -> {
                // No results
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = UiR.drawable.ic_file_find),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = SquircleTheme.colors.colorTextAndIconSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.editor_global_search_no_results),
                        style = SquircleTheme.typography.text14Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.editor_global_search_no_results_detail, searchState.query),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.6f)
                    )
                }
            }

            else -> {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Icon(
                        painter = painterResource(id = UiR.drawable.ic_file_find),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = SquircleTheme.colors.colorTextAndIconSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.editor_global_search_empty_hint),
                        style = SquircleTheme.typography.text14Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.editor_global_search_empty_subtitle),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.6f)
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun FileSearchResultItem(
    result: FileSearchResult,
    searchQuery: String,
    showReplaceAction: Boolean,
    onClick: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // File header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = UiR.drawable.ic_file_find),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = SquircleTheme.colors.colorPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = result.file.name,
                style = SquircleTheme.typography.text14Regular,
                color = SquircleTheme.colors.colorTextAndIconPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.editor_global_search_file_matches, result.matchCount),
                style = SquircleTheme.typography.text12Regular,
                color = SquircleTheme.colors.colorTextAndIconSecondary
            )
        }

        // File path secondary
        Text(
            text = result.file.path,
            style = SquircleTheme.typography.text12Regular,
            color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Match previews
        result.matches.take(3).forEach { match ->
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.padding(start = 20.dp)
            ) {
                // Line number
                Text(
                    text = "${match.matchLine}",
                    style = SquircleTheme.typography.text12Regular,
                    color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp)
                )
                // Line content with highlighted match
                Text(
                    text = buildHighlightedText(match.lineContent, searchQuery),
                    style = SquircleTheme.typography.text12Regular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Replace button (only shown when replace mode is active)
        if (showReplaceAction) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    text = stringResource(R.string.editor_global_search_button_replace),
                    onClick = onReplace,
                    debounce = true,
                )
            }
        }
    }

    Divider(color = SquircleTheme.colors.colorOutline.copy(alpha = 0.15f))
}

@Composable
private fun buildHighlightedText(line: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(line)
        return@buildAnnotatedString
    }

    val highlightColor = SquircleTheme.colors.colorPrimary

    var startIndex = 0
    val lowerLine = line.lowercase()
    val lowerQuery = query.lowercase()

    while (startIndex < line.length) {
        val matchIndex = lowerLine.indexOf(lowerQuery, startIndex)
        if (matchIndex == -1) {
            append(line.substring(startIndex))
            break
        }

        // Text before match
        if (matchIndex > startIndex) {
            append(line.substring(startIndex, matchIndex))
        }

        // Highlighted match
        withStyle(SpanStyle(color = highlightColor, background = highlightColor.copy(alpha = 0.2f))) {
            append(line.substring(matchIndex, matchIndex + query.length))
        }

        startIndex = matchIndex + query.length
    }
}
