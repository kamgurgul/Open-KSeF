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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.LocalAuthentication.LABiometryTypeOpticID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImageSymbolConfiguration

/**
 * Renders the SF Symbol matching the biometry of the device - `faceid`, `touchid` or `opticid` - so
 * the screen shows the same glyph as the system prompt. Falls back to the Material fingerprint when
 * the symbol cannot be loaded.
 */
@Composable
actual fun BiometricIcon(size: Dp, tint: Color, modifier: Modifier) {
    val symbol = remember(size) { biometrySymbol(pointSize = size.value.toDouble()) }
    if (symbol != null) {
        Icon(
            bitmap = symbol,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}

/** Name of the SF Symbol for the biometry the device is set up with. */
@OptIn(ExperimentalForeignApi::class)
private fun biometrySymbolName(): String {
    val context = LAContext()
    // The biometry type is only populated after the policy has been evaluated.
    context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
    return when (context.biometryType) {
        LABiometryTypeTouchID -> "touchid"
        LABiometryTypeOpticID -> "opticid"
        else -> "faceid"
    }
}

private fun biometrySymbol(pointSize: Double): ImageBitmap? {
    val configuration = UIImageSymbolConfiguration.configurationWithPointSize(pointSize)
    val image =
        UIImage.systemImageNamed(biometrySymbolName(), withConfiguration = configuration)
            ?: return null
    return image.toImageBitmap()
}

/**
 * Rasterises the symbol at the screen scale. Symbols are template images drawn in black, so the
 * alpha channel carries the glyph and [Icon] can tint it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toImageBitmap(): ImageBitmap? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    UIGraphicsBeginImageContextWithOptions(size = size, opaque = false, scale = 0.0)
    drawInRect(CGRectMake(x = 0.0, y = 0.0, width = width, height = height))
    val rendered = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val png = rendered?.let(::UIImagePNGRepresentation) ?: return null
    return Image.makeFromEncoded(png.toByteArray()).toComposeImageBitmap()
}
