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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.common.asString
import com.kgurgul.openksef.domain.model.KsefEnvironment
import com.kgurgul.openksef.ui.components.LoadingOverlay
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.app_name
import openksef.shared.generated.resources.app_subtitle
import openksef.shared.generated.resources.login_environment_label
import openksef.shared.generated.resources.login_nip_label
import openksef.shared.generated.resources.login_nip_placeholder
import openksef.shared.generated.resources.login_remember_credentials
import openksef.shared.generated.resources.login_sign_in
import openksef.shared.generated.resources.login_token_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    val errorMessage = uiState.error?.asString()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LoginScreen(
        uiState = uiState,
        onNipChanged = viewModel::onNipChanged,
        onTokenChanged = viewModel::onTokenChanged,
        onEnvironmentChanged = viewModel::onEnvironmentChanged,
        onRememberChanged = viewModel::onRememberChanged,
        onLoginClick = viewModel::login,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onNipChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onEnvironmentChanged: (KsefEnvironment) -> Unit,
    onRememberChanged: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = stringResource(Res.string.app_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(48.dp))

                OutlinedTextField(
                    value = uiState.nip,
                    onValueChange = {
                        val trimmed = it.filter { char -> char.isDigit() }
                        if (trimmed.length <= 10) onNipChanged(it)
                    },
                    label = { Text(stringResource(Res.string.login_nip_label)) },
                    placeholder = { Text(stringResource(Res.string.login_nip_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.token,
                    onValueChange = onTokenChanged,
                    label = { Text(stringResource(Res.string.login_token_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.login_environment_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start),
                )

                Spacer(modifier = Modifier.height(4.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    KsefEnvironment.entries.forEachIndexed { index, env ->
                        SegmentedButton(
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = KsefEnvironment.entries.size,
                                ),
                            onClick = { onEnvironmentChanged(env) },
                            selected = uiState.environment == env,
                            label = {
                                Text(
                                    text = env.name,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = uiState.rememberCredentials,
                        onCheckedChange = onRememberChanged,
                    )
                    Text(
                        text = stringResource(Res.string.login_remember_credentials),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onLoginClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(Res.string.login_sign_in))
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            LoadingOverlay(isLoading = uiState.isLoading)
        }
    }
}
