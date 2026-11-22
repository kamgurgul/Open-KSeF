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

package com.kgurgul.openksef.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.kgurgul.openksef.ui.navigation.LocalIsInSystemNavigationBar
import com.kgurgul.openksef.ui.navigation.LocalNativeNavigation
import com.kgurgul.openksef.ui.navigation.NativeNavigation
import com.kgurgul.openksef.ui.navigation.isTopEntry

/**
 * Top app bar in the primary color. When the platform provides a system navigation bar (iOS 26+)
 * and the bar has no [actions], the title and the navigation icon (see [NavigationIconButton]) are
 * shown in the system bar, so they look and are placed like in native apps, e.g. next to the status
 * bar on the unfolded iPhone Duo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val nativeNavigation = LocalNativeNavigation.current
    val navigationBarBottom = nativeNavigation?.insets?.navigationBarBottom
    if (nativeNavigation != null && navigationBarBottom != null && actions == null) {
        SystemNavigationTopAppBar(
            nativeNavigation = nativeNavigation,
            navigationBarBottom = navigationBarBottom,
            title = title,
            navigationIcon = navigationIcon,
            modifier = modifier,
        )
        return
    }
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions ?: {},
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    )
}

/**
 * Shows the title in the system navigation bar, which the platform draws above the Compose content,
 * and takes the place of the bar. The bar shows the content of the top screen, so it changes once a
 * back gesture completes, not when it starts.
 */
@Composable
private fun SystemNavigationTopAppBar(
    nativeNavigation: NativeNavigation,
    navigationBarBottom: Dp,
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    val id = remember { "navigationBarTitle${nextNavigationBarTitleId++}" }
    val isTop = isTopEntry()
    val titleColor = MaterialTheme.colorScheme.onPrimary
    DisposableEffect(nativeNavigation, id) {
        onDispose { nativeNavigation.removeNavigationBarTitle(id) }
    }
    LaunchedEffect(nativeNavigation, id, title, titleColor, isTop) {
        nativeNavigation.setNavigationBarTitle(
            id = id,
            title = title,
            titleColor = titleColor,
            isTop = isTop,
        )
    }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(navigationBarBottom)
                .background(MaterialTheme.colorScheme.primary)
    ) {
        CompositionLocalProvider(LocalIsInSystemNavigationBar provides true) {
            navigationIcon?.invoke()
        }
    }
}

private var nextNavigationBarTitleId = 0
