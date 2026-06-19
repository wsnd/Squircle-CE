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

package com.blacksquircle.ui.feature.editor.domain

import com.blacksquircle.ui.filesystem.base.Filesystem
import com.blacksquircle.ui.filesystem.base.model.FileModel
import com.blacksquircle.ui.filesystem.base.model.FileParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * Global search use case - recursively searches file contents in the workspace directory.
 */
internal class GlobalSearchUseCase(
    private val filesystemFactory: com.blacksquircle.ui.feature.explorer.api.factory.FilesystemFactory,
) {

    /**
     * Result of replacing in a single file.
     */
    data class ReplaceResult(
        val file: FileModel,
        val replacedCount: Int,
        val error: String? = null,
    )

    /**
     * A single search result entry (line/column match).
     */
    data class SearchResult(
        val file: FileModel,
        val matchLine: Int,
        val matchColumn: Int,
        val lineContent: String,
    )

    /**
     * Search results grouped by file.
     */
    data class FileSearchResult(
        val file: FileModel,
        val matches: List<SearchResult>,
    ) {
        val matchCount: Int get() = matches.size
        val previewLines: List<String> get() = matches.take(5).map { it.lineContent }
    }

    /**
     * Search parameters.
     */
    data class SearchParams(
        val query: String,
        val matchCase: Boolean = false,
        val useRegex: Boolean = false,
        val wordsOnly: Boolean = false,
    )

    /**
     * Recursively searches file contents under the given root directory.
     *
     * @param rootDir The starting directory for the search.
     * @param params Search parameters (query, matchCase, useRegex, wordsOnly).
     * @return List of search results grouped by file.
     */
    suspend fun search(
        rootDir: FileModel,
        params: SearchParams,
    ): List<FileSearchResult> = withContext(Dispatchers.IO) {
        if (params.query.isBlank()) return@withContext emptyList()

        val filesystem = filesystemFactory.create(rootDir.filesystemUuid)
        val fileParams = FileParams()

        // Build search regex
        val searchRegex = buildSearchRegex(params.query, params.matchCase, params.useRegex, params.wordsOnly)
            ?: return@withContext emptyList()

        val results = mutableListOf<FileSearchResult>()

        // Recursive search
        searchRecursive(
            filesystem = filesystem,
            dir = rootDir,
            searchRegex = searchRegex,
            fileParams = fileParams,
            results = results,
        )

        results
    }

    private suspend fun searchRecursive(
        filesystem: Filesystem,
        dir: FileModel,
        searchRegex: Regex,
        fileParams: FileParams,
        results: MutableList<FileSearchResult>,
    ) {
        coroutineContext.ensureActive()

        val children = try {
            filesystem.listFiles(dir)
        } catch (e: Exception) {
            Timber.w(e, "Failed to list files in ${dir.path}")
            return
        }

        for (child in children) {
            coroutineContext.ensureActive()

            if (child.isDirectory) {
                // Skip hidden directories
                if (child.name.startsWith(".") && child.name != ".") continue

                searchRecursive(
                    filesystem = filesystem,
                    dir = child,
                    searchRegex = searchRegex,
                    fileParams = fileParams,
                    results = results,
                )
            } else {
                // Skip binary files and oversized files
                if (isBinaryExtension(child.extension)) continue
                if (child.size > MAX_FILE_SIZE) continue

                try {
                    val content = filesystem.loadFile(child, fileParams)
                    val matches = findMatches(content, searchRegex).map { match ->
                        match.copy(file = child)
                    }
                    if (matches.isNotEmpty()) {
                        results.add(
                            FileSearchResult(
                                file = child,
                                matches = matches,
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to read file ${child.path}")
                }
            }
        }
    }

    private fun findMatches(content: String, regex: Regex): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val lines = content.lines()
        for ((index, line) in lines.withIndex()) {
            for (match in regex.findAll(line)) {
                results.add(
                    SearchResult(
                        file = FileModel("", ""), // placeholder, replaced by caller
                        matchLine = index + 1,
                        matchColumn = match.range.first + 1,
                        lineContent = line.trim(),
                    )
                )
            }
        }
        return results
    }

    // ---- Helpers ----

    private fun isBinaryExtension(ext: String): Boolean {
        return ext.lowercase() in BINARY_EXTENSIONS
    }

    private fun buildSearchRegex(
        query: String,
        matchCase: Boolean,
        useRegex: Boolean,
        wordsOnly: Boolean,
    ): Regex? {
        return try {
            val pattern = when {
                useRegex -> query
                wordsOnly -> "\\b${Regex.escape(query)}\\b"
                else -> Regex.escape(query)
            }
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, options)
        } catch (e: Exception) {
            Timber.w(e, "Invalid search regex: $query")
            null
        }
    }

    /**
     * Replace all matches across the given search results.
     * Reads each file, replaces all occurrences, and writes back.
     *
     * @param fileResults Search results grouped by file.
     * @param replaceText The replacement text.
     * @param params Search parameters used to build the replacement regex.
     * @return List of replace results per file.
     */
    suspend fun replaceAll(
        fileResults: List<FileSearchResult>,
        replaceText: String,
        params: SearchParams,
    ): List<ReplaceResult> = withContext(Dispatchers.IO) {
        replaceFiles(fileResults, replaceText, params)
    }

    /**
     * Replace all matches in a single file.
     *
     * @param fileResult The file to replace in.
     * @param replaceText The replacement text.
     * @param params Search parameters used to build the replacement regex.
     * @return The replace result for this file.
     */
    suspend fun replaceSingle(
        fileResult: FileSearchResult,
        replaceText: String,
        params: SearchParams,
    ): ReplaceResult = withContext(Dispatchers.IO) {
        val results = replaceFiles(listOf(fileResult), replaceText, params)
        results.firstOrNull() ?: ReplaceResult(fileResult.file, 0, "Unknown error")
    }

    /**
     * Internal shared file replacement logic.
     */
    private suspend fun replaceFiles(
        fileResults: List<FileSearchResult>,
        replaceText: String,
        params: SearchParams,
    ): List<ReplaceResult> {
        val searchRegex = buildSearchRegex(params.query, params.matchCase, params.useRegex, params.wordsOnly)
            ?: return fileResults.map {
                ReplaceResult(it.file, 0, "Invalid search pattern")
            }

        val filesystem = filesystemFactory.create(fileResults.firstOrNull()?.file?.filesystemUuid ?: "")
        val fileParams = FileParams()

        return fileResults.map { fileResult ->
            try {
                val content = filesystem.loadFile(fileResult.file, fileParams)
                var replacedCount = 0

                val newContent = searchRegex.replace(content) { match ->
                    replacedCount++
                    replaceText
                }

                if (replacedCount > 0) {
                    filesystem.saveFile(fileResult.file, newContent, fileParams)
                }

                ReplaceResult(fileResult.file, replacedCount)
            } catch (e: Exception) {
                Timber.w(e, "Failed to replace in file: ${fileResult.file.path}")
                ReplaceResult(fileResult.file, 0, e.message)
            }
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 1024 * 1024L // 1MB

        private val BINARY_EXTENSIONS = setOf(
            ".apk", ".aab", ".zip", ".jar", ".rar", ".7z", ".tar", ".gz",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico",
            ".mp3", ".ogg", ".wav", ".flac", ".mp4", ".avi", ".mkv",
            ".ttf", ".otf", ".woff", ".woff2",
            ".dex", ".so", ".o", ".a",
            ".class", ".pyc", ".pyo",
            ".db", ".sqlite", ".sqlite3",
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx",
        )
    }
}
