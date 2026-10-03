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
import android.os.Build
import android.util.Log
import com.blacksquircle.ui.feature.python.model.PythonPackage
import com.blacksquircle.ui.feature.python.model.PythonPackageDetails
import com.blacksquircle.ui.feature.python.model.PythonWheel
import com.blacksquircle.ui.feature.python.model.normalizePackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catalog of what can actually be installed in the bundled runtime.
 *
 * It is built from the PEP 503 indexes of the repositories that publish
 * Android wheels, which is a few hundred packages and under 10 KB in total,
 * instead of PyPI's own index: that one lists every distribution ever
 * published, and the overwhelming majority of it has no wheel matching
 * `android_*` platform tags.
 */
object AndroidWheelRepository {

    private const val TAG = "AndroidWheelRepository"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val INDEX_FILE = "android-wheel-index.txt"
    private const val RESULT_LIMIT = 100
    private const val USER_AGENT = "Squircle-CE"

    // Interpreter shipped with the app, see PythonStdlibExtractor.
    private val PYTHON_TAG = "cp" + PythonStdlibExtractor.PYTHON_VERSION.replace(".", "")
    private val ABI_TAG = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().replace('-', '_')

    @Volatile
    private var index: List<PythonPackage>? = null

    /**
     * Catalog, from memory first, then from the cache on disk, then from the
     * network. A failed refresh leaves the previous catalog in place, so the
     * panel keeps working offline.
     */
    suspend fun loadIndex(
        context: Context,
        forceRefresh: Boolean = false,
    ): List<PythonPackage> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            index?.let { return@withContext it }
            readCache(context)?.let { cached ->
                index = cached
                return@withContext cached
            }
        }
        val fetched = fetchIndex()
        if (fetched.isNotEmpty()) {
            writeCache(context, fetched)
            index = fetched
        }
        fetched
    }

    fun isLoaded(): Boolean = index != null

    /**
     * Filter the catalog, prefix matches first because they are what the user
     * is usually typing. [installed] is keyed by normalized name.
     */
    fun search(query: String, installed: Map<String, String>): List<PythonPackage> {
        val catalog = index ?: return emptyList()
        val needle = normalizePackageName(query)

        // Browsing shows the whole catalog; it is small enough to scroll and
        // truncating it would hide everything past the first few letters.
        if (needle.isEmpty()) {
            return catalog.map { pkg ->
                pkg.copy(installedVersion = installed[pkg.normalizedName])
            }
        }

        val prefix = ArrayList<PythonPackage>()
        val contains = ArrayList<PythonPackage>()
        for (pkg in catalog) {
            when {
                pkg.normalizedName.startsWith(needle) -> if (prefix.size < RESULT_LIMIT) prefix.add(pkg)
                pkg.normalizedName.contains(needle) -> if (contains.size < RESULT_LIMIT) contains.add(pkg)
            }
            if (prefix.size >= RESULT_LIMIT && contains.size >= RESULT_LIMIT) break
        }
        return (prefix + contains).map { pkg ->
            pkg.copy(installedVersion = installed[pkg.normalizedName])
        }
    }

    /** Wheel files published for one distribution, newest first. */
    suspend fun fetchDetails(packageName: String): PythonPackageDetails = withContext(Dispatchers.IO) {
        val normalized = normalizePackageName(packageName)
        val pageUrl = index?.firstOrNull { it.normalizedName == normalized }?.pageUrl
        val wheels = if (pageUrl == null) {
            emptyList()
        } else {
            parseWheels(get(pageUrl).orEmpty())
        }
        PythonPackageDetails(name = packageName, wheels = wheels)
    }

    private fun fetchIndex(): List<PythonPackage> {
        val packages = LinkedHashMap<String, PythonPackage>()
        for (indexUrl in PythonStdlibExtractor.WHEEL_INDEXES) {
            val html = get(indexUrl) ?: continue
            for (pkg in parseIndex(html, indexUrl)) {
                packages.putIfAbsent(pkg.normalizedName, pkg)
            }
        }
        return packages.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /**
     * PEP 503 index entries are `<a href="...">name</a>`; the href is kept
     * because the per-project pages have no uniform shape across the indexes
     * (`numpy/index.html` in one, `brotli/` in another).
     */
    private fun parseIndex(html: String, baseUrl: String): List<PythonPackage> {
        val packages = ArrayList<PythonPackage>()
        var cursor = 0
        while (cursor < html.length) {
            val tagStart = html.indexOf("<a ", cursor)
            if (tagStart < 0) break
            val hrefStart = html.indexOf("href=\"", tagStart)
            val textStart = html.indexOf("\">", tagStart)
            val textEnd = html.indexOf("</a>", textStart)
            if (hrefStart < 0 || textStart < 0 || textEnd < 0) break

            val hrefEnd = html.indexOf('"', hrefStart + "href=\"".length)
            val href = html.substring(hrefStart + "href=\"".length, hrefEnd)
            val name = html.substring(textStart + 2, textEnd).trim()
            cursor = textEnd + "</a>".length

            if (name.isEmpty() || name == "..") continue
            val pageUrl = if (href.startsWith("http")) href else baseUrl + href
            packages.add(PythonPackage(name = name, pageUrl = pageUrl))
        }
        return packages
    }

    private fun parseWheels(html: String): List<PythonWheel> {
        val wheels = ArrayList<PythonWheel>()
        var cursor = 0
        while (cursor < html.length) {
            val hrefStart = html.indexOf("href=\"", cursor)
            if (hrefStart < 0) break
            val hrefEnd = html.indexOf('"', hrefStart + "href=\"".length)
            if (hrefEnd < 0) break

            val href = html.substring(hrefStart + "href=\"".length, hrefEnd)
            cursor = hrefEnd + 1

            val fileName = href.substringAfterLast('/')
            if (!fileName.endsWith(".whl")) continue
            val wheel = parseWheelFileName(fileName) ?: continue
            if (wheels.none { it.version == wheel.version && it.pythonTag == wheel.pythonTag }) {
                wheels.add(wheel)
            }
        }
        return wheels
    }

    /** `name-version-python-abi-platform.whl`, read from the end because names carry dashes. */
    private fun parseWheelFileName(fileName: String): PythonWheel? {
        val parts = fileName.removeSuffix(".whl").split("-")
        if (parts.size < 5) return null
        val platformTag = parts[parts.size - 1]
        val abiTag = parts[parts.size - 2]
        val pythonTag = parts[parts.size - 3]
        val version = parts[parts.size - 4]
        val compatible = isCompatible(pythonTag, abiTag, platformTag)
        return PythonWheel(version, pythonTag, platformTag, compatible)
    }

    private fun isCompatible(pythonTag: String, abiTag: String, platformTag: String): Boolean {
        val interpreterMatches = pythonTag.equals(PYTHON_TAG, ignoreCase = true) ||
            pythonTag == "py3" ||
            pythonTag == "py2.py3"
        if (!interpreterMatches) return false
        if (platformTag == "any") return true
        // Termux wheels for older interpreters are tagged linux_*; pip rejects
        // those for the Android platform tags this interpreter reports.
        return platformTag.startsWith("android") && platformTag.endsWith(ABI_TAG)
    }

    private fun get(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> {
                    Log.w(TAG, "$url -> ${connection.responseCode}")
                    null
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to fetch $url: ${e.message}")
        null
    }

    private fun readCache(context: Context): List<PythonPackage>? = try {
        val file = File(context.filesDir, INDEX_FILE)
        if (!file.exists()) return null
        val packages = file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            PythonPackage(name = parts[0], pageUrl = parts[1])
        }
        packages.ifEmpty { null }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read index cache: ${e.message}")
        null
    }

    private fun writeCache(context: Context, packages: List<PythonPackage>) {
        try {
            val file = File(context.filesDir, INDEX_FILE)
            val temp = File(context.filesDir, "$INDEX_FILE.tmp")
            temp.bufferedWriter().use { writer ->
                packages.forEach { pkg ->
                    writer.write("${pkg.name}\t${pkg.pageUrl}\n")
                }
            }
            if (temp.renameTo(file)) return
            Log.w(TAG, "Failed to replace index cache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write index cache: ${e.message}")
        }
    }
}