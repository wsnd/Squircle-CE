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
 * A single entry of `git status --porcelain=v1 -z`.
 *
 * [indexStatus] is the status in the index (staged) and [worktreeStatus] the
 * status in the working tree; a space means "unchanged in that area".
 */
data class GitStatusEntry(
    val path: String,
    val indexStatus: Char,
    val worktreeStatus: Char,
) {

    val isRenamedOrCopied: Boolean
        get() = indexStatus == GitStatusEntry.RENAMED || indexStatus == GitStatusEntry.COPIED ||
            worktreeStatus == GitStatusEntry.RENAMED || worktreeStatus == GitStatusEntry.COPIED

    companion object {
        const val UNCHANGED = ' '
        const val UNTRACKED = '?'
        const val RENAMED = 'R'
        const val COPIED = 'C'
    }
}

object GitStatusParser {

    /**
     * Parses the NUL-separated porcelain v1 format. Renames and copies emit
     * two NUL-terminated records (old path, new path); the new path is the one
     * callers act on.
     */
    fun parse(output: String): List<GitStatusEntry> {
        if (output.isEmpty()) return emptyList()

        val fields = output.split('\u0000')
        val entries = mutableListOf<GitStatusEntry>()

        var index = 0
        while (index < fields.size) {
            val record = fields[index]
            if (record.length < 4) {
                index++
                continue
            }
            val indexStatus = record[0]
            val worktreeStatus = record[1]
            val path = record.substring(3)

            val entry = GitStatusEntry(path, indexStatus, worktreeStatus)
            if (entry.isRenamedOrCopied && index + 1 < fields.size) {
                // Use the destination path for renames/copies.
                entries.add(entry.copy(path = fields[index + 1]))
                index += 2
            } else {
                entries.add(entry)
                index++
            }
        }
        return entries
    }
}