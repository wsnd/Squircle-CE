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

package com.blacksquircle.ui.feature.git.api.model

/**
 * A commit from the repository history, as shown by the Git panel.
 */
data class GitCommit(
    val sha: String,
    val shortSha: String,
    val message: String,
    val authorName: String,
    val authorEmail: String,
    /** Author time in seconds since the epoch. */
    val timestamp: Long,
    /** Branch and tag names pointing at this commit. */
    val refs: List<String>,
    val isMerge: Boolean,
)