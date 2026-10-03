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
import com.blacksquircle.ui.feature.python.model.PythonPackage
import com.blacksquircle.ui.feature.python.model.PythonPackageDetails

/**
 * State holder for the Python packages panel.
 */
@Immutable
internal data class PythonPanelState(
    /** Whether the panel is the one currently shown in the sidebar. */
    val isOpen: Boolean = false,
    val query: String = "",
    val packages: List<PythonPackage> = emptyList(),
    /** Normalized package name to installed version. */
    val installed: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    /** Package whose wheels are expanded, if any. */
    val expandedPackage: String? = null,
    val details: PythonPackageDetails? = null,
    val isLoadingDetails: Boolean = false,
    /** Packages with a pip command still running in the terminal. */
    val pending: Map<String, PythonPendingTask> = emptyMap(),
)