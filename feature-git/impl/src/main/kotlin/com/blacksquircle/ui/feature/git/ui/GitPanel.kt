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

package com.blacksquircle.ui.feature.git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.blacksquircle.ui.ds.PreviewBackground
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.emptyview.EmptyView
import com.blacksquircle.ui.feature.git.R
import com.blacksquircle.ui.feature.git.api.model.ChangeType
import com.blacksquircle.ui.feature.git.api.model.GitChange
import com.blacksquircle.ui.feature.git.api.model.GitCommit
import com.blacksquircle.ui.feature.git.api.model.GitPanelTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.blacksquircle.ui.ds.R as UiR

/**
 * VS Code style Source Control panel showing current branch and uncommitted changes.
 * Includes inline commit input, stage/unstage, and discard actions.
 */
@Composable
fun GitPanel(
    stagedChanges: List<GitChange>,
    unstagedChanges: List<GitChange>,
    currentBranch: String,
    isLoading: Boolean,
    isError: Boolean,
    errorMessage: String,
    hasRepository: Boolean,
    showInitView: Boolean,
    commitMessage: String = "",
    isCommitting: Boolean = false,
    selectedTab: GitPanelTab = GitPanelTab.CHANGES,
    commits: List<GitCommit> = emptyList(),
    isLoadingHistory: Boolean = false,
    expandedCommitSha: String? = null,
    expandedCommitFiles: List<GitChange> = emptyList(),
    hasMoreHistory: Boolean = false,
    onRefreshClicked: () -> Unit,
    onChangeClicked: (GitChange) -> Unit,
    onInitRepositoryClicked: () -> Unit,
    onStageClicked: (GitChange) -> Unit = {},
    onUnstageClicked: (GitChange) -> Unit = {},
    onStageAllClicked: () -> Unit = {},
    onUnstageAllClicked: () -> Unit = {},
    onDiscardClicked: (GitChange) -> Unit = {},
    onCommitMessageChanged: (String) -> Unit = {},
    onCommitClicked: () -> Unit = {},
    onTabSelected: (GitPanelTab) -> Unit = {},
    onHistoryCommitClicked: (GitCommit) -> Unit = {},
    onLoadMoreHistoryClicked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SquircleTheme.colors.colorBackgroundSecondary)
    ) {
        // Header
        GitPanelHeader(
            currentBranch = currentBranch,
            hasRepository = hasRepository,
            selectedTab = selectedTab,
            onRefreshClicked = onRefreshClicked,
            onTabSelected = onTabSelected,
        )

        Divider(color = SquircleTheme.colors.colorOutline)

        // Content
        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        color = SquircleTheme.colors.colorPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            !hasRepository -> {
                if (showInitView) {
                    EmptyView(
                        iconResId = UiR.drawable.ic_git,
                        title = stringResource(R.string.git_panel_no_repository),
                        subtitle = stringResource(R.string.git_panel_initialize_hint),
                        action = stringResource(R.string.git_panel_initialize_button),
                        onClick = onInitRepositoryClicked,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyView(
                        iconResId = UiR.drawable.ic_git,
                        title = stringResource(R.string.git_panel_no_repository),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            isError -> {
                EmptyView(
                    iconResId = UiR.drawable.ic_git,
                    title = errorMessage.ifEmpty {
                        stringResource(R.string.git_panel_error)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            selectedTab == GitPanelTab.HISTORY -> {
                CommitHistory(
                    commits = commits,
                    isLoading = isLoadingHistory,
                    expandedCommitSha = expandedCommitSha,
                    expandedCommitFiles = expandedCommitFiles,
                    hasMore = hasMoreHistory,
                    onCommitClicked = onHistoryCommitClicked,
                    onLoadMoreClicked = onLoadMoreHistoryClicked,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            stagedChanges.isEmpty() && unstagedChanges.isEmpty() -> {
                EmptyView(
                    iconResId = UiR.drawable.ic_git,
                    title = stringResource(R.string.git_commit_dialog_selection_empty),
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (stagedChanges.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.git_panel_staged_changes),
                                count = stagedChanges.size,
                                actionIcon = UiR.drawable.ic_minus,
                                actionDescription = stringResource(R.string.git_panel_unstage_all),
                                onActionClicked = onUnstageAllClicked,
                            )
                        }
                        items(stagedChanges, key = { "staged_${it.name}" }) { change ->
                            ChangeItem(
                                change = change,
                                isStaged = true,
                                onClick = { onChangeClicked(change) },
                                onStageToggle = { onUnstageClicked(change) },
                                onDiscard = null, // Staged changes cannot be discarded
                            )
                        }
                    }

                    if (unstagedChanges.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.git_panel_changes),
                                count = unstagedChanges.size,
                                actionIcon = UiR.drawable.ic_plus,
                                actionDescription = stringResource(R.string.git_panel_stage_all),
                                onActionClicked = onStageAllClicked,
                            )
                        }
                        items(unstagedChanges, key = { "unstaged_${it.name}" }) { change ->
                            ChangeItem(
                                change = change,
                                isStaged = false,
                                onClick = { onChangeClicked(change) },
                                onStageToggle = { onStageClicked(change) },
                                onDiscard = { onDiscardClicked(change) },
                            )
                        }
                    }
                }

                // VS Code-style inline commit area
                Divider(color = SquircleTheme.colors.colorOutline)

                CommitInputArea(
                    commitMessage = commitMessage,
                    isCommitting = isCommitting,
                    hasStagedChanges = stagedChanges.isNotEmpty(),
                    onCommitMessageChanged = onCommitMessageChanged,
                    onCommitClicked = onCommitClicked,
                )
            }
        }
    }
}

@Composable
private fun GitPanelHeader(
    currentBranch: String,
    hasRepository: Boolean,
    selectedTab: GitPanelTab,
    onRefreshClicked: () -> Unit,
    onTabSelected: (GitPanelTab) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.git_panel_title),
            style = SquircleTheme.typography.text14Regular,
            color = SquircleTheme.colors.colorTextAndIconPrimary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.weight(1f))

        // Refresh button
        Icon(
            painter = painterResource(UiR.drawable.ic_refresh),
            contentDescription = stringResource(R.string.git_panel_refresh),
            tint = SquircleTheme.colors.colorTextAndIconSecondary,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onRefreshClicked)
                .padding(2.dp)
        )
    }

    if (hasRepository) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_source_branch),
                contentDescription = null,
                tint = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = currentBranch,
                style = SquircleTheme.typography.text12Regular,
                color = SquircleTheme.colors.colorTextAndIconSecondary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }

        GitPanelTabs(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun GitPanelTabs(
    selectedTab: GitPanelTab,
    onTabSelected: (GitPanelTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GitPanelTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onTabSelected(tab) }
            ) {
                Text(
                    text = stringResource(
                        when (tab) {
                            GitPanelTab.CHANGES -> R.string.git_panel_tab_changes
                            GitPanelTab.HISTORY -> R.string.git_panel_tab_history
                        }
                    ),
                    style = SquircleTheme.typography.text12Regular,
                    color = if (selected) {
                        SquircleTheme.colors.colorTextAndIconPrimary
                    } else {
                        SquircleTheme.colors.colorTextAndIconSecondary
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(SquircleTheme.colors.colorPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    actionIcon: Int? = null,
    actionDescription: String? = null,
    onActionClicked: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SquircleTheme.colors.colorBackgroundTertiary)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = SquircleTheme.typography.text12Regular,
            color = SquircleTheme.colors.colorTextAndIconSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = SquircleTheme.typography.text12Regular,
            color = SquircleTheme.colors.colorTextAndIconSecondary,
        )
        if (actionIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(actionIcon),
                contentDescription = actionDescription.orEmpty(),
                tint = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onActionClicked)
            )
        }
    }
}

@Composable
private fun CommitHistory(
    commits: List<GitCommit>,
    isLoading: Boolean,
    expandedCommitSha: String?,
    expandedCommitFiles: List<GitChange>,
    hasMore: Boolean,
    onCommitClicked: (GitCommit) -> Unit,
    onLoadMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading && commits.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize()
        ) {
            CircularProgressIndicator(
                color = SquircleTheme.colors.colorPrimary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        return
    }

    if (commits.isEmpty()) {
        EmptyView(
            iconResId = UiR.drawable.ic_source_commit,
            title = stringResource(R.string.git_panel_history_empty),
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(commits, key = { it.sha }) { commit ->
            val expanded = commit.sha == expandedCommitSha
            CommitItem(
                commit = commit,
                expanded = expanded,
                files = if (expanded) expandedCommitFiles else emptyList(),
                onClick = { onCommitClicked(commit) },
            )
        }
        if (hasMore) {
            item {
                LoadMoreButton(
                    isLoading = isLoading,
                    onClick = onLoadMoreClicked,
                )
            }
        }
    }
}

@Composable
private fun CommitItem(
    commit: GitCommit,
    expanded: Boolean,
    files: List<GitChange>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Commit graph: a lane with a dot, merge commits get a distinct color.
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(SquircleTheme.colors.colorOutline)
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (commit.isMerge) {
                                SquircleTheme.colors.colorTextAndIconAdditional
                            } else {
                                SquircleTheme.colors.colorPrimary
                            }
                        )
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commit.message,
                    style = SquircleTheme.typography.text14Regular,
                    color = SquircleTheme.colors.colorTextAndIconPrimary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_person),
                        contentDescription = null,
                        tint = SquircleTheme.colors.colorTextAndIconSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = commit.authorName,
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = commit.shortSha,
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRelativeTime(commit.timestamp),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                        maxLines = 1,
                    )
                }

                if (commit.refs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        commit.refs.forEach { ref ->
                            RefChip(name = ref)
                        }
                    }
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                Text(
                    text = stringResource(R.string.git_panel_history_no_files),
                    style = SquircleTheme.typography.text12Regular,
                    color = SquircleTheme.colors.colorTextAndIconSecondary,
                )
            } else {
                files.forEach { change ->
                    CommitFileItem(change = change)
                }
            }
        }
    }
}

@Composable
private fun RefChip(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(SquircleTheme.colors.colorBackgroundTertiary)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_source_branch),
            contentDescription = null,
            tint = SquircleTheme.colors.colorTextAndIconSecondary,
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = name,
            style = SquircleTheme.typography.text12Regular,
            color = SquircleTheme.colors.colorTextAndIconSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun CommitFileItem(change: GitChange) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 0.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = when (change.changeType) {
                ChangeType.ADDED -> "A"
                ChangeType.MODIFIED -> "M"
                ChangeType.DELETED -> "D"
            },
            style = SquircleTheme.typography.text12Regular,
            color = when (change.changeType) {
                ChangeType.ADDED -> SquircleTheme.colors.colorTextAndIconSuccess
                ChangeType.MODIFIED -> SquircleTheme.colors.colorTextAndIconAdditional
                ChangeType.DELETED -> SquircleTheme.colors.colorTextAndIconError
            },
            modifier = Modifier.width(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = change.name,
            style = SquircleTheme.typography.text12Regular,
            color = SquircleTheme.colors.colorTextAndIconSecondary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

@Composable
private fun LoadMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = SquircleTheme.colors.colorPrimary,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.git_panel_history_load_more),
                style = SquircleTheme.typography.text12Regular,
                color = SquircleTheme.colors.colorPrimary,
            )
        }
    }
}

/**
 * `git log` reports author time in seconds since the epoch; anything older
 * than a few weeks is shown as a date instead of a coarse "N weeks ago".
 */
@Composable
private fun formatRelativeTime(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return ""

    val diffSeconds = System.currentTimeMillis() / 1000 - timestampSeconds
    return when {
        diffSeconds < 60 -> stringResource(R.string.git_panel_history_just_now)
        diffSeconds < 3600 -> stringResource(R.string.git_panel_history_minutes_ago, diffSeconds / 60)
        diffSeconds < 86_400 -> stringResource(R.string.git_panel_history_hours_ago, diffSeconds / 3600)
        diffSeconds < 604_800 -> stringResource(R.string.git_panel_history_days_ago, diffSeconds / 86_400)
        diffSeconds < 2_629_800 -> stringResource(R.string.git_panel_history_weeks_ago, diffSeconds / 604_800)
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(timestampSeconds * 1000))
    }
}

@Composable
private fun ChangeItem(
    change: GitChange,
    isStaged: Boolean,
    onClick: () -> Unit,
    onStageToggle: () -> Unit,
    onDiscard: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Stage/unstage toggle button (+/- icon)
        Icon(
            painter = painterResource(
                if (isStaged) UiR.drawable.ic_minus else UiR.drawable.ic_plus
            ),
            contentDescription = if (isStaged) {
                stringResource(R.string.git_panel_unstage_file)
            } else {
                stringResource(R.string.git_panel_stage_file)
            },
            tint = if (isStaged) {
                SquircleTheme.colors.colorTextAndIconSecondary
            } else {
                SquircleTheme.colors.colorTextAndIconSuccess
            },
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .clickable(onClick = onStageToggle)
        )

        Spacer(Modifier.width(8.dp))

        // File info (clickable to open)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (change.changeType) {
                            ChangeType.ADDED -> SquircleTheme.colors.colorTextAndIconSuccess
                            ChangeType.MODIFIED -> SquircleTheme.colors.colorTextAndIconAdditional
                            ChangeType.DELETED -> SquircleTheme.colors.colorTextAndIconError
                        }
                    )
            )

            Spacer(Modifier.width(8.dp))

            Column {
                // File name (last segment)
                val fileName = change.name.substringAfterLast('/')
                val filePath = change.name.substringBeforeLast('/', "")

                Text(
                    text = fileName,
                    style = SquircleTheme.typography.text14Regular,
                    color = SquircleTheme.colors.colorTextAndIconPrimary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )

                if (filePath.isNotEmpty()) {
                    Text(
                        text = filePath,
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }

        // Change type label
        Text(
            text = when (change.changeType) {
                ChangeType.ADDED -> "A"
                ChangeType.MODIFIED -> "M"
                ChangeType.DELETED -> "D"
            },
            style = SquircleTheme.typography.text12Regular,
            color = when (change.changeType) {
                ChangeType.ADDED -> SquircleTheme.colors.colorTextAndIconSuccess
                ChangeType.MODIFIED -> SquircleTheme.colors.colorTextAndIconAdditional
                ChangeType.DELETED -> SquircleTheme.colors.colorTextAndIconError
            },
            modifier = Modifier
                .background(
                    when (change.changeType) {
                        ChangeType.ADDED -> SquircleTheme.colors.colorTextAndIconSuccess.copy(alpha = 0.1f)
                        ChangeType.MODIFIED -> SquircleTheme.colors.colorTextAndIconAdditional.copy(alpha = 0.1f)
                        ChangeType.DELETED -> SquircleTheme.colors.colorTextAndIconError.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )

        // Discard button (only for unstaged changes)
        if (onDiscard != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.git_panel_discard_file),
                tint = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onDiscard)
            )
        }
    }
}

@Composable
private fun CommitInputArea(
    commitMessage: String,
    isCommitting: Boolean,
    hasStagedChanges: Boolean,
    onCommitMessageChanged: (String) -> Unit,
    onCommitClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SquircleTheme.colors.colorBackgroundSecondary)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (isCommitting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.git_commit_dialog_message),
                    style = SquircleTheme.typography.text12Regular,
                    color = SquircleTheme.colors.colorTextAndIconSecondary,
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp),
                    color = SquircleTheme.colors.colorPrimary,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = onCommitMessageChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.git_panel_commit_placeholder),
                            style = SquircleTheme.typography.text14Regular,
                            color = SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.5f),
                        )
                    },
                    textStyle = SquircleTheme.typography.text14Regular.copy(
                        color = SquircleTheme.colors.colorTextAndIconPrimary
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = SquircleTheme.colors.colorOutline,
                        unfocusedBorderColor = SquircleTheme.colors.colorOutline.copy(alpha = 0.5f),
                        cursorColor = SquircleTheme.colors.colorPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )

                Spacer(Modifier.width(8.dp))

                // Commit check button
                val canCommit = commitMessage.isNotBlank()
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = stringResource(R.string.git_commit_dialog_button_commit),
                    tint = if (canCommit) {
                        SquircleTheme.colors.colorPrimary
                    } else {
                        SquircleTheme.colors.colorTextAndIconSecondary.copy(alpha = 0.3f)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (canCommit) {
                                Modifier.clickable(onClick = onCommitClicked)
                            } else {
                                Modifier
                            }
                        )
                        .background(
                            if (canCommit) {
                                SquircleTheme.colors.colorPrimary.copy(alpha = 0.1f)
                            } else {
                                SquircleTheme.colors.colorBackgroundSecondary
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(6.dp)
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun GitPanelPreview() {
    PreviewBackground {
        GitPanel(
            stagedChanges = listOf(
                GitChange("src/main/MainActivity.kt", ChangeType.MODIFIED),
            ),
            unstagedChanges = listOf(
                GitChange("src/main/Utils.kt", ChangeType.ADDED),
                GitChange("build.gradle.kts", ChangeType.MODIFIED),
                GitChange("README.md", ChangeType.DELETED),
            ),
            currentBranch = "main",
            isLoading = false,
            isError = false,
            errorMessage = "",
            hasRepository = true,
            showInitView = false,
            onRefreshClicked = {},
            onChangeClicked = {},
            onInitRepositoryClicked = {},
        )
    }
}