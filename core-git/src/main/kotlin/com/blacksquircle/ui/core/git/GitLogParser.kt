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

package com.blacksquircle.ui.core.git

/**
 * A single entry of `git log --pretty=format:$LOG_FORMAT`.
 *
 * [timestamp] is the author time in seconds since the epoch, matching what
 * `git log --format=%at` reports.
 */
data class GitLogEntry(
    val sha: String,
    val shortSha: String,
    val parents: List<String>,
    val authorName: String,
    val authorEmail: String,
    val timestamp: Long,
    val subject: String,
    /** Branch/tag decorations, e.g. `main`, `v1.0.0`, `origin/main`. */
    val refs: List<String>,
)

object GitLogParser {

    /**
     * Field and record separators of the pretty format below. Both are ASCII
     * control characters that cannot appear inside a commit subject, so the
     * output stays parseable without any quoting.
     */
    const val LOG_FORMAT =
        "%H%x1f%h%x1f%P%x1f%an%x1f%ae%x1f%at%x1f%D%x1f%s%x1e"

    private const val FIELD_SEPARATOR = '\u001F'
    private const val RECORD_SEPARATOR = '\u001E'
    private const val HEAD_PREFIX = "HEAD -> "
    private const val TAG_PREFIX = "tag: "
    private const val FIELD_COUNT = 8

    /**
     * Parses the output of
     * `git log --pretty=format:%H%x1f%h%x1f%P%x1f%an%x1f%ae%x1f%at%x1f%D%x1f%s%x1e`.
     */
    fun parse(output: String): List<GitLogEntry> {
        if (output.isBlank()) return emptyList()

        return output.split(RECORD_SEPARATOR)
            .mapNotNull { record ->
                val fields = record.trim('\n', '\r').split(FIELD_SEPARATOR)
                if (fields.size < FIELD_COUNT) return@mapNotNull null

                GitLogEntry(
                    sha = fields[0],
                    shortSha = fields[1],
                    parents = fields[2].split(' ').filter { it.isNotEmpty() },
                    authorName = fields[3],
                    authorEmail = fields[4],
                    timestamp = fields[5].toLongOrNull() ?: 0L,
                    subject = fields[7],
                    refs = parseRefs(fields[6]),
                )
            }
    }

    /**
     * `%D` renders decorations such as `HEAD -> main, tag: v1.0.0, origin/main`.
     * The prefixes are noise for display purposes, so only the ref name is kept.
     */
    private fun parseRefs(decorations: String): List<String> {
        if (decorations.isBlank()) return emptyList()
        return decorations
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ref ->
                when {
                    ref.startsWith(HEAD_PREFIX) -> ref.removePrefix(HEAD_PREFIX)
                    ref.startsWith(TAG_PREFIX) -> ref.removePrefix(TAG_PREFIX)
                    else -> ref
                }
            }
    }
}