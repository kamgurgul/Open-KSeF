/*
 * Copyright KG Soft
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

package com.kgurgul.openksef.common

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Scrollbar style tinted from the active Material theme so the thumb stays visible against the
 * surface, unlike the very faint platform default.
 */
@Composable
private fun themedScrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return LocalScrollbarStyle.current.copy(
        unhoverColor = onSurface.copy(alpha = 0.38f),
        hoverColor = onSurface.copy(alpha = 0.6f),
    )
}

@Composable
actual fun BoxScope.PlatformVerticalScrollbar(listState: LazyListState) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(listState),
        style = themedScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.PlatformVerticalScrollbar(scrollState: ScrollState) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState),
        style = themedScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.PlatformHorizontalScrollbar(scrollState: ScrollState) {
    HorizontalScrollbar(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        adapter = rememberScrollbarAdapter(scrollState),
        style = themedScrollbarStyle(),
    )
}
