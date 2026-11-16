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

package com.kgurgul.openksef.ui.navigation

import androidx.compose.ui.unit.DpRect

/**
 * Implemented by the iOS app. Renders navigation elements using UIKit components (Liquid Glass)
 * above the Compose content. All sizes and positions are in points.
 */
interface IosNativeNavigationRenderer {

    fun setDarkTheme(isDarkTheme: Boolean)

    fun setButton(
        id: String,
        systemImageName: String,
        accessibilityLabel: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        alpha: Double,
        onClick: () -> Unit,
    )

    fun removeButton(id: String)
}

/** Bridge between Compose and [IosNativeNavigationRenderer]. */
class IosNativeNavigation(private val renderer: IosNativeNavigationRenderer) : NativeNavigation {

    fun setDarkTheme(isDarkTheme: Boolean) {
        renderer.setDarkTheme(isDarkTheme)
    }

    override fun setButton(
        id: String,
        type: NativeNavigationButtonType,
        contentDescription: String,
        boundsInWindow: DpRect,
        alpha: Float,
        onClick: () -> Unit,
    ) {
        renderer.setButton(
            id = id,
            // SF Symbols
            systemImageName =
                when (type) {
                    NativeNavigationButtonType.BACK -> "chevron.backward"
                    NativeNavigationButtonType.CLOSE -> "xmark"
                },
            accessibilityLabel = contentDescription,
            x = boundsInWindow.left.value.toDouble(),
            y = boundsInWindow.top.value.toDouble(),
            width = (boundsInWindow.right - boundsInWindow.left).value.toDouble(),
            height = (boundsInWindow.bottom - boundsInWindow.top).value.toDouble(),
            alpha = alpha.toDouble(),
            onClick = onClick,
        )
    }

    override fun removeButton(id: String) {
        renderer.removeButton(id)
    }
}
