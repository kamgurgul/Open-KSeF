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

package com.kgurgul.openksef.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kgurgul.openksef.common.BiometricIcon
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.biometric_lock_description
import openksef.shared.generated.resources.biometric_lock_title
import openksef.shared.generated.resources.biometric_unlock
import openksef.shared.generated.resources.biometric_use_credentials
import org.jetbrains.compose.resources.stringResource

/**
 * Shown on app start instead of the automatic sign-in when the remembered credentials are guarded
 * by biometrics. The system prompt is triggered by the caller; this screen only offers a retry and
 * a way back to the regular login form.
 */
@Composable
fun BiometricLockContent(
    isLoading: Boolean,
    onUnlockClick: () -> Unit,
    onUseCredentialsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BiometricIcon(size = 96.dp, tint = MaterialTheme.colorScheme.primary)

        Text(
            text = stringResource(Res.string.biometric_lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )

        Text(
            text = stringResource(Res.string.biometric_lock_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = onUnlockClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp).height(50.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(Res.string.biometric_unlock))
            }
        }

        TextButton(onClick = onUseCredentialsClick, enabled = !isLoading) {
            Text(stringResource(Res.string.biometric_use_credentials))
        }
    }
}
