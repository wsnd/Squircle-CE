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

package com.blacksquircle.ui.feature.settings.ui.about

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.blacksquircle.ui.core.extensions.daggerViewModel
import com.blacksquircle.ui.ds.PreviewBackground
import com.blacksquircle.ui.ds.SquircleTheme
import com.blacksquircle.ui.ds.divider.HorizontalDivider
import com.blacksquircle.ui.ds.preference.Preference
import com.blacksquircle.ui.ds.preference.PreferenceGroup
import com.blacksquircle.ui.ds.scaffold.ScaffoldSuite
import com.blacksquircle.ui.ds.toolbar.Toolbar
import com.blacksquircle.ui.feature.settings.R
import com.blacksquircle.ui.feature.settings.internal.SettingsComponent
import com.blacksquircle.ui.ds.R as UiR

@Composable
internal fun OpenSourceLicensesScreen(
    viewModel: OpenSourceLicensesViewModel = daggerViewModel { context ->
        val component = SettingsComponent.buildOrGet(context)
        OpenSourceLicensesViewModel.Factory().also(component::inject)
    }
) {
    val context = LocalContext.current
    OpenSourceLicensesScreen(
        onBackClicked = viewModel::onBackClicked,
        onViewFullLicensesClicked = {
            // Open GitHub repository with full license information
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://github.com/massivemadness/Squircle-CE/blob/master/THIRD-PARTY-LICENSES".toUri()
            }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun OpenSourceLicensesScreen(
    onBackClicked: () -> Unit = {},
    onViewFullLicensesClicked: () -> Unit = {},
) {
    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.settings_open_source_licenses_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            // App Information
            Text(
                text = stringResource(R.string.licenses_app_title),
                style = SquircleTheme.typography.text16Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.licenses_app_description),
                style = SquircleTheme.typography.text14Regular,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            // Core Components
            PreferenceGroup(
                title = stringResource(R.string.licenses_core_components)
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_sora_editor),
                license = "LGPL v2.1",
                copyright = "Rosemoe",
                url = "https://github.com/Rosemoe/sora-editor"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_python_runtime),
                license = "PSF License",
                copyright = "Python Software Foundation",
                url = "https://www.python.org/"
            )
            
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            // Major Dependencies
            PreferenceGroup(
                title = stringResource(R.string.licenses_major_dependencies)
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_dagger),
                license = "Apache 2.0",
                copyright = "Google",
                url = "https://github.com/google/dagger"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_retrofit),
                license = "Apache 2.0",
                copyright = "Square",
                url = "https://github.com/square/retrofit"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_room),
                license = "Apache 2.0",
                copyright = "Android Open Source Project",
                url = "https://developer.android.com/jetpack/androidx/releases/room"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_kotlin_coroutines),
                license = "Apache 2.0",
                copyright = "JetBrains",
                url = "https://github.com/Kotlin/kotlinx.coroutines"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_jgit),
                license = "BSD",
                copyright = "Eclipse Foundation",
                url = "https://github.com/eclipse-jgit/jgit"
            )
            
            LicenseItem(
                name = stringResource(R.string.licenses_timber),
                license = "Apache 2.0",
                copyright = "Jake Wharton",
                url = "https://github.com/JakeWharton/timber"
            )
            
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            // LGPL Notice
            Text(
                text = stringResource(R.string.licenses_important_notice),
                style = SquircleTheme.typography.text16Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.licenses_lgl_notice),
                style = SquircleTheme.typography.text14Regular,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = stringResource(R.string.licenses_apache_notice),
                style = SquircleTheme.typography.text14Regular,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            // View Full Licenses
            Text(
                text = stringResource(R.string.licenses_complete_info),
                style = SquircleTheme.typography.text16Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.licenses_complete_info_desc),
                style = SquircleTheme.typography.text14Regular,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Preference(
                title = stringResource(R.string.licenses_view_full),
                onClick = onViewFullLicensesClicked,
            )
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LicenseItem(
    name: String,
    license: String,
    copyright: String,
    url: String,
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = url.toUri()
                }
                context.startActivity(intent)
            }
    ) {
        Text(
            text = name,
            style = SquircleTheme.typography.text16Regular,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        
        Text(
            text = "License: $license",
            style = SquircleTheme.typography.text14Regular,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        
        Text(
            text = "© $copyright",
            style = SquircleTheme.typography.text14Regular,
        )
        
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@PreviewLightDark
@Composable
private fun OpenSourceLicensesScreenPreview() {
    PreviewBackground {
        OpenSourceLicensesScreen()
    }
}
