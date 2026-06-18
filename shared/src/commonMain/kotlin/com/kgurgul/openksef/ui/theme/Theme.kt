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

package com.kgurgul.openksef.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Blue600,
        onPrimary = Neutral0,
        primaryContainer = Blue100,
        onPrimaryContainer = BlueOnContainer,
        secondary = Neutral600,
        onSecondary = Neutral0,
        secondaryContainer = Neutral100,
        onSecondaryContainer = Neutral800,
        tertiary = Success,
        onTertiary = Neutral0,
        tertiaryContainer = SuccessContainer,
        onTertiaryContainer = OnSuccessContainer,
        background = Neutral50,
        onBackground = Neutral900,
        surface = Neutral0,
        onSurface = Neutral900,
        surfaceVariant = Neutral100,
        onSurfaceVariant = Neutral500,
        outline = Neutral300,
        outlineVariant = Neutral200,
        error = ErrorRed,
        onError = Neutral0,
        errorContainer = ErrorContainerRed,
        onErrorContainer = OnErrorContainerRed,
        scrim = Neutral900,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Blue300,
        onPrimary = Blue900,
        primaryContainer = Blue700,
        onPrimaryContainer = Blue100,
        secondary = Neutral300,
        onSecondary = Neutral900,
        secondaryContainer = Neutral800,
        onSecondaryContainer = Neutral100,
        tertiary = Color(0xFF5BD0A0),
        onTertiary = OnSuccessContainer,
        tertiaryContainer = Color(0xFF105C3E),
        onTertiaryContainer = SuccessContainer,
        background = Neutral900,
        onBackground = Neutral100,
        surface = Neutral800,
        onSurface = Neutral100,
        surfaceVariant = Neutral800,
        onSurfaceVariant = Neutral400,
        outline = Neutral600,
        outlineVariant = Neutral800,
        error = Color(0xFFFFB3B5),
        onError = Color(0xFF5C1115),
        errorContainer = Color(0xFF8A2229),
        onErrorContainer = ErrorContainerRed,
        scrim = NavyDeep,
    )

@Composable
fun OpenKsefTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ksefTypography(),
        shapes = KsefShapes,
        content = content,
    )
}
