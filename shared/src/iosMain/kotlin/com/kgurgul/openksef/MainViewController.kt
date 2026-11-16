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

package com.kgurgul.openksef

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.kgurgul.openksef.di.appModule
import com.kgurgul.openksef.ui.navigation.IosNativeNavigation
import com.kgurgul.openksef.ui.navigation.IosNativeNavigationRenderer
import com.kgurgul.openksef.ui.navigation.LocalNativeNavigation
import org.koin.core.context.startKoin

/**
 * @param nativeNavigationRenderer renderer of the native navigation elements (Liquid Glass) or null
 *   when all navigation elements should be drawn by Compose
 */
fun MainViewController(
    nativeNavigationRenderer: IosNativeNavigationRenderer?
): platform.UIKit.UIViewController {
    startKoin { modules(appModule) }
    return ComposeUIViewController {
        val nativeNavigation =
            remember(nativeNavigationRenderer) {
                nativeNavigationRenderer?.let(::IosNativeNavigation)
            }
        val darkTheme = isSystemInDarkTheme()
        LaunchedEffect(nativeNavigation, darkTheme) { nativeNavigation?.setDarkTheme(darkTheme) }
        CompositionLocalProvider(LocalNativeNavigation provides nativeNavigation) { App() }
    }
}
