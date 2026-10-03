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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.button.IconButton
import com.blacksquircle.ui.ds.button.IconButtonSizeDefaults
import com.blacksquircle.ui.ds.button.IconButtonStyleDefaults
import com.blacksquircle.ui.ds.progress.CircularProgress
import com.blacksquircle.ui.ds.progress.CircularProgressSizeDefaults
import com.blacksquircle.ui.ds.textfield.TextField
import com.blacksquircle.ui.feature.editor.R
import com.blacksquircle.ui.feature.editor.ui.editor.model.PythonPanelState
import com.blacksquircle.ui.feature.editor.ui.editor.model.PythonPendingAction
import com.blacksquircle.ui.feature.editor.ui.editor.model.PythonPendingTask
import com.blacksquircle.ui.feature.python.model.PythonPackage
import com.blacksquircle.ui.ds.R as UiR

private const val WHEEL_PREVIEW_LIMIT = 6

// Keys for the two section headers, so they keep their identity while the
// rows below them are reordered by an install.
private const val GROUP_INSTALLED = "group-installed"
private const val GROUP_AVAILABLE = "group-available"

/**
 * Sidebar panel listing the distributions that have an Android wheel, with
 * install, upgrade and uninstall actions. Every action is a pip invocation
 * handed to the terminal, so its output stays visible.
 */
@Composable
internal fun PythonPackagePanel(
    state: PythonPanelState,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit = {},
    onPackageClicked: (PythonPackage) -> Unit = {},
    onInstallClicked: (PythonPackage) -> Unit = {},
    onUninstallClicked: (PythonPackage) -> Unit = {},
    onUpgradeClicked: (PythonPackage) -> Unit = {},
    onRefreshClicked: () -> Unit = {},
) {
    val installedPackages = remember(state.packages) {
        state.packages.filter { it.installedVersion != null }
    }
    val availablePackages = remember(state.packages) {
        state.packages.filter { it.installedVersion == null }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SquircleTheme.colors.colorBackgroundSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_python_panel_title),
                style = SquircleTheme.typography.text12Regular,
                fontWeight = FontWeight.Bold,
                color = SquircleTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                iconResId = UiR.drawable.ic_refresh,
                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                iconButtonSize = IconButtonSizeDefaults.XS,
                onClick = onRefreshClicked,
                contentDescription = stringResource(R.string.editor_python_panel_refresh),
            )
        }

        TextField(
            inputText = state.query,
            onInputChanged = onQueryChanged,
            placeholderText = stringResource(R.string.editor_python_panel_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> {
                    CircularProgress(
                        circularProgressSize = CircularProgressSizeDefaults.S,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.errorMessage.isNotEmpty() -> {
                    Text(
                        text = state.errorMessage,
                        style = SquircleTheme.typography.text14Regular,
                        color = SquircleTheme.colors.colorTextAndIconError,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp),
                    )
                }
                state.packages.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.editor_python_panel_empty),
                        style = SquircleTheme.typography.text14Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (installedPackages.isNotEmpty()) {
                            item(key = GROUP_INSTALLED) {
                                GroupHeader(text = stringResource(R.string.editor_python_panel_group_installed))
                            }
                            items(items = installedPackages, key = { it.normalizedName }) { pkg ->
                                PackageItem(
                                    pkg = pkg,
                                    pending = state.pending,
                                    expanded = pkg.normalizedName == state.expandedPackage,
                                    isLoadingDetails = state.isLoadingDetails,
                                    details = state.details?.takeIf { it.name == pkg.name },
                                    onPackageClicked = onPackageClicked,
                                    onInstallClicked = onInstallClicked,
                                    onUninstallClicked = onUninstallClicked,
                                    onUpgradeClicked = onUpgradeClicked,
                                )
                            }
                        }
                        if (availablePackages.isNotEmpty()) {
                            item(key = GROUP_AVAILABLE) {
                                GroupHeader(text = stringResource(R.string.editor_python_panel_group_available))
                            }
                            items(items = availablePackages, key = { it.normalizedName }) { pkg ->
                                PackageItem(
                                    pkg = pkg,
                                    pending = state.pending,
                                    expanded = pkg.normalizedName == state.expandedPackage,
                                    isLoadingDetails = state.isLoadingDetails,
                                    details = state.details?.takeIf { it.name == pkg.name },
                                    onPackageClicked = onPackageClicked,
                                    onInstallClicked = onInstallClicked,
                                    onUninstallClicked = onUninstallClicked,
                                    onUpgradeClicked = onUpgradeClicked,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = SquircleTheme.typography.text12Regular,
        fontWeight = FontWeight.Bold,
        color = SquircleTheme.colors.colorTextAndIconSecondary,
        modifier = modifier
            .fillMaxWidth()
            .background(SquircleTheme.colors.colorBackgroundSecondary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PackageItem(
    pkg: PythonPackage,
    pending: Map<String, PythonPendingTask>,
    expanded: Boolean,
    isLoadingDetails: Boolean,
    details: com.blacksquircle.ui.feature.python.model.PythonPackageDetails?,
    modifier: Modifier = Modifier,
    onPackageClicked: (PythonPackage) -> Unit = {},
    onInstallClicked: (PythonPackage) -> Unit = {},
    onUninstallClicked: (PythonPackage) -> Unit = {},
    onUpgradeClicked: (PythonPackage) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPackageClicked(pkg) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pkg.name,
                    style = SquircleTheme.typography.text14Regular,
                    color = SquircleTheme.colors.colorTextAndIconPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pkg.installedVersion != null) {
                        stringResource(
                            R.string.editor_python_panel_installed,
                            pkg.installedVersion.orEmpty(),
                        )
                    } else {
                        stringResource(R.string.editor_python_panel_not_installed)
                    },
                    style = SquircleTheme.typography.text12Regular,
                    color = SquircleTheme.colors.colorTextAndIconSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val task = pending[pkg.normalizedName]
            when {
                task != null -> {
                    CircularProgress(circularProgressSize = CircularProgressSizeDefaults.S)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            when (task.action) {
                                PythonPendingAction.INSTALL -> R.string.editor_python_panel_installing
                                PythonPendingAction.UPGRADE -> R.string.editor_python_panel_upgrading
                                PythonPendingAction.UNINSTALL -> R.string.editor_python_panel_removing
                            }
                        ),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconAdditional,
                        maxLines = 1,
                    )
                }
                pkg.installedVersion != null -> {
                    IconButton(
                        iconResId = UiR.drawable.ic_autorenew,
                        iconButtonStyle = IconButtonStyleDefaults.Secondary,
                        iconButtonSize = IconButtonSizeDefaults.XS,
                        onClick = { onUpgradeClicked(pkg) },
                        contentDescription = stringResource(R.string.editor_python_panel_upgrade),
                    )
                    IconButton(
                        iconResId = UiR.drawable.ic_delete,
                        iconButtonStyle = IconButtonStyleDefaults.Secondary,
                        iconButtonSize = IconButtonSizeDefaults.XS,
                        onClick = { onUninstallClicked(pkg) },
                        contentDescription = stringResource(R.string.editor_python_panel_uninstall),
                    )
                }
                else -> {
                    IconButton(
                        iconResId = UiR.drawable.ic_tray_arrow_down,
                        iconButtonStyle = IconButtonStyleDefaults.Primary,
                        iconButtonSize = IconButtonSizeDefaults.XS,
                        onClick = { onInstallClicked(pkg) },
                        contentDescription = stringResource(R.string.editor_python_panel_install),
                    )
                }
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    isLoadingDetails -> CircularProgress(
                        circularProgressSize = CircularProgressSizeDefaults.S,
                    )
                    details == null -> Unit
                    details.wheels.isEmpty() -> Text(
                        text = stringResource(R.string.editor_python_panel_no_wheels),
                        style = SquircleTheme.typography.text12Regular,
                        color = SquircleTheme.colors.colorTextAndIconSecondary,
                    )
                    else -> {
                        if (!details.hasCompatibleWheel) {
                            Text(
                                text = stringResource(
                                    R.string.editor_python_panel_no_compatible_wheel,
                                    com.blacksquircle.ui.feature.python.PythonStdlibExtractor
                                        .PYTHON_VERSION,
                                ),
                                style = SquircleTheme.typography.text12Regular,
                                color = SquircleTheme.colors.colorTextAndIconError,
                            )
                        }
                        details.wheels.take(WHEEL_PREVIEW_LIMIT).forEach { wheel ->
                            Text(
                                text = "${wheel.version} · ${wheel.pythonTag} · ${wheel.platformTag}",
                                style = SquircleTheme.typography.text12Regular,
                                color = if (wheel.isCompatible) {
                                    SquircleTheme.colors.colorTextAndIconSuccess
                                } else {
                                    SquircleTheme.colors.colorTextAndIconAdditional
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}