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

package com.blacksquircle.ui.ds.navigationrail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blacksquircle.ui.ds.SquircleTheme

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.ui.res.painterResource
import com.blacksquircle.ui.ds.modifier.debounceClickable

@Composable
fun NavigationRail(
    modifier: Modifier = Modifier,
    backgroundColor: Color = SquircleTheme.colors.colorBackgroundSecondary,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .background(backgroundColor)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (header != null) {
            header()
            Spacer(Modifier.height(8.dp))
        }
        
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )

        if (footer != null) {
            Spacer(Modifier.height(8.dp))
            footer()
        }
    }
}

@Composable
fun NavigationRailItem(
    iconResId: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = if (selected) {
        SquircleTheme.colors.colorTextAndIconPrimary
    } else {
        SquircleTheme.colors.colorTextAndIconSecondary
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) SquircleTheme.colors.colorBackgroundTertiary else Color.Transparent)
            .debounceClickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // VS Code style vertical bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(24.dp)
                    .background(SquircleTheme.colors.colorPrimary)
            )
        }
        
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
