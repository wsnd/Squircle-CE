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

package com.blacksquircle.ui.feature.python.model

import androidx.compose.runtime.Immutable

/**
 * A distribution one of the Android wheel indexes publishes a prebuilt binary
 * for. PyPI itself is not usable as a catalog here: most of it has no wheel
 * matching the Android platform tags, so installing from it builds from source
 * on a device that has no toolchain.
 */
@Immutable
data class PythonPackage(
    val name: String,
    /** PEP 503 project page of this distribution in the index it was found in. */
    val pageUrl: String,
    /** Version currently present in the bundled runtime, if any. */
    val installedVersion: String? = null,
) {

    /** PEP 503 normalized name, the form pip reports installs under. */
    val normalizedName: String = normalizePackageName(name)
}

@Immutable
data class PythonWheel(
    val version: String,
    val pythonTag: String,
    val platformTag: String,
    /** Wheel matches the interpreter version and ABI of this device. */
    val isCompatible: Boolean,
)

@Immutable
data class PythonPackageDetails(
    val name: String,
    val wheels: List<PythonWheel>,
) {
    val hasCompatibleWheel: Boolean
        get() = wheels.any(PythonWheel::isCompatible)
}

private val SEPARATORS = Regex("[-_.]+")

internal fun normalizePackageName(name: String): String =
    SEPARATORS.replace(name.trim().lowercase(), "-")