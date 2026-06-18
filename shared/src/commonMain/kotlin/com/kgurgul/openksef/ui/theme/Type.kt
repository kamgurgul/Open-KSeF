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

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.jetbrains_mono_medium
import openksef.shared.generated.resources.jetbrains_mono_regular
import openksef.shared.generated.resources.jetbrains_mono_semibold
import openksef.shared.generated.resources.manrope_bold
import openksef.shared.generated.resources.manrope_medium
import openksef.shared.generated.resources.manrope_regular
import openksef.shared.generated.resources.manrope_semibold
import openksef.shared.generated.resources.space_grotesk_bold
import openksef.shared.generated.resources.space_grotesk_medium
import org.jetbrains.compose.resources.Font

/** Space Grotesk — display & headlines. */
@Composable
fun spaceGroteskFamily() =
    FontFamily(
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )

/** Manrope — UI & body. */
@Composable
fun manropeFamily() =
    FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
    )

/** JetBrains Mono — amounts, NIP & codes. */
@Composable
fun jetBrainsMonoFamily() =
    FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(Res.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )

@Composable
fun ksefTypography(): Typography {
    val display = spaceGroteskFamily()
    val body = manropeFamily()
    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.8).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.6).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.6).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.4).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    )
}
