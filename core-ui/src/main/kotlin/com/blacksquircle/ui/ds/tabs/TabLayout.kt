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

package com.blacksquircle.ui.ds.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.blacksquircle.ui.ds.divider.HorizontalDivider

@Composable
fun TabLayout(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    divider: Boolean = true,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        Row(
            modifier = Modifier.zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            LazyRow(
                state = state,
                content = content,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }
        if (divider) {
            HorizontalDivider(Modifier.align(Alignment.BottomCenter))
        }
    }
}