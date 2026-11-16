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

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpRect

enum class NativeNavigationButtonType {
    BACK,
    CLOSE,
}

/**
 * Navigation elements which are rendered by the platform above the Compose content instead of being
 * drawn by Compose (e.g. Liquid Glass buttons on iOS 26+).
 */
interface NativeNavigation {

    /** Adds or updates a native button placed at [boundsInWindow]. */
    fun setButton(
        id: String,
        type: NativeNavigationButtonType,
        contentDescription: String,
        boundsInWindow: DpRect,
        alpha: Float = 1f,
        onClick: () -> Unit,
    )

    fun removeButton(id: String)
}

/** Not null when the platform renders navigation elements on its own. */
val LocalNativeNavigation = staticCompositionLocalOf<NativeNavigation?> { null }
