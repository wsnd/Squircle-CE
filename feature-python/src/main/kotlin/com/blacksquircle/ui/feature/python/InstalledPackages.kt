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

package com.blacksquircle.ui.feature.python

import android.content.Context
import android.util.Log
import com.blacksquircle.ui.feature.python.model.normalizePackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What is installed in the bundled runtime, read from the `*.dist-info`
 * directories pip leaves in site-packages.
 *
 * Scanning the directory instead of running `pip list` keeps the panel
 * responsive: starting the interpreter costs a couple of seconds, and this
 * runs every time the panel opens.
 */
object InstalledPackages {

    private const val TAG = "InstalledPackages"

    /** Name and version live in the first block of METADATA, well inside this. */
    private const val METADATA_LINES = 80

    private val DIR_NAME = Regex("^(.+)-([0-9][^-]*)\\.(?:dist|egg)-info$")

    /** Normalized package name to installed version. */
    suspend fun scan(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val sitePackages = PythonStdlibExtractor.sitePackages(context)
        val entries = sitePackages.listFiles() ?: return@withContext emptyMap()

        val installed = HashMap<String, String>()
        for (entry in entries) {
            if (!entry.isDirectory) continue
            if (!entry.name.endsWith(".dist-info") && !entry.name.endsWith(".egg-info")) continue
            val (name, version) = readMetadata(entry) ?: parseDirectoryName(entry.name) ?: continue
            installed[normalizePackageName(name)] = version
        }
        installed
    }

    private fun readMetadata(directory: File): Pair<String, String>? = try {
        val metadata = File(directory, "METADATA")
        if (!metadata.exists()) return null

        var name: String? = null
        var version: String? = null
        metadata.bufferedReader().useLines { lines ->
            for (line in lines.take(METADATA_LINES)) {
                if (line.startsWith("Name:", ignoreCase = true)) {
                    name = line.substringAfter(':').trim()
                } else if (line.startsWith("Version:", ignoreCase = true)) {
                    version = line.substringAfter(':').trim()
                }
                if (name != null && version != null) break
            }
        }
        if (name == null || version == null) null else name!! to version!!
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read metadata of ${directory.name}: ${e.message}")
        null
    }

    private fun parseDirectoryName(directoryName: String): Pair<String, String>? {
        val match = DIR_NAME.matchEntire(directoryName) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }
}