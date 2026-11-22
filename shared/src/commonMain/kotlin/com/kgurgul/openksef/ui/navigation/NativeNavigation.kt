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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect

enum class NativeNavigationButtonType {
    BACK,
    CLOSE,
}

/**
 * Where the platform placed its system navigation bar. It usually runs along the top, but it can be
 * moved down a side of the window, e.g. on iPhone Duo.
 */
@Immutable
data class NativeNavigationInsets(
    /**
     * Whether the platform lays the bars out down a side of the window, e.g. on iPhone Duo. The
     * back button is then shown in the system navigation bar there, see
     * [NativeNavigation.setNavigationBarButton], and the screens keep clear of the safe area at the
     * sides, so nothing of them is drawn behind the bar.
     */
    val areBarsVertical: Boolean = false,
    /**
     * Distance from the top of the window to the bottom of the system navigation bar while it runs
     * along the top, `null` otherwise. A top app bar shown in the system bar reserves this space.
     */
    val navigationBarBottom: Dp? = null,
)

/**
 * Navigation elements which are rendered by the platform above the Compose content instead of being
 * drawn by Compose (e.g. Liquid Glass buttons and the system navigation bar on iOS 26+).
 */
interface NativeNavigation {

    /** Where the system navigation bar is. */
    val insets: NativeNavigationInsets

    /** Adds or updates a native button placed at [boundsInWindow]. */
    fun setButton(
        id: String,
        type: NativeNavigationButtonType,
        contentDescription: String,
        boundsInWindow: DpRect,
        alpha: Float = 1f,
        onClick: () -> Unit,
    )

    /**
     * Adds or updates a button shown in the system navigation bar, which the platform places on its
     * own, e.g. down the side of the window on iPhone Duo. The bar shows the button of the screen
     * which most recently became the top one, [isTop] is false once the screen isn't, see
     * [isTopEntry].
     */
    fun setNavigationBarButton(
        id: String,
        type: NativeNavigationButtonType,
        contentDescription: String,
        isTop: Boolean,
        onClick: () -> Unit,
    )

    /** Removes the button added with [setButton] or [setNavigationBarButton]. */
    fun removeButton(id: String)

    /**
     * Adds or updates a title shown in [titleColor] in the system navigation bar, picked like the
     * button of [setNavigationBarButton].
     */
    fun setNavigationBarTitle(id: String, title: String, titleColor: Color, isTop: Boolean)

    /** Removes the title added with [setNavigationBarTitle]. */
    fun removeNavigationBarTitle(id: String)
}

/**
 * Set by a top app bar shown in the system navigation bar, the navigation icon placed in it is
 * shown there as well.
 */
internal val LocalIsInSystemNavigationBar = compositionLocalOf { false }

/** Not null when the platform renders navigation elements on its own. */
val LocalNativeNavigation = staticCompositionLocalOf<NativeNavigation?> { null }
