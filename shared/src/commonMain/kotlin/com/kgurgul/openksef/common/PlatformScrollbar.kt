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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

/**
 * Shows a vertical scrollbar overlay for the given [listState] on desktop, aligned to the trailing
 * edge of the enclosing [BoxScope]. No-op on touch platforms (Android, iOS).
 */
@Composable
expect fun BoxScope.PlatformVerticalScrollbar(listState: LazyListState)

/**
 * Shows a vertical scrollbar overlay for the given [scrollState] on desktop, aligned to the
 * trailing edge of the enclosing [BoxScope]. No-op on touch platforms (Android, iOS).
 */
@Composable
expect fun BoxScope.PlatformVerticalScrollbar(scrollState: ScrollState)

/**
 * Shows a horizontal scrollbar overlay for the given [scrollState] on desktop, aligned to the
 * bottom edge of the enclosing [BoxScope]. No-op on touch platforms (Android, iOS).
 */
@Composable
expect fun BoxScope.PlatformHorizontalScrollbar(scrollState: ScrollState)
