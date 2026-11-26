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

package com.kgurgul.openksef.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.common.ObserveAsEvents
import com.kgurgul.openksef.common.asString
import com.kgurgul.openksef.ui.components.BackNavigationIconButton
import com.kgurgul.openksef.ui.components.PrimaryTopAppBar
import com.kgurgul.openksef.ui.components.SectionHeader
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.bank_account_number
import openksef.shared.generated.resources.bank_account_section
import openksef.shared.generated.resources.bank_name
import openksef.shared.generated.resources.bank_swift
import openksef.shared.generated.resources.seller_config_address
import openksef.shared.generated.resources.seller_config_name
import openksef.shared.generated.resources.seller_config_save
import openksef.shared.generated.resources.seller_config_saved
import openksef.shared.generated.resources.settings_seller_info
import org.jetbrains.compose.resources.stringResource

@Composable
fun SellerConfigScreen(viewModel: SellerConfigViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(Res.string.seller_config_saved)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SellerConfigEvent.Saved -> snackbarHostState.showSnackbar(savedMessage)
        }
    }

    SellerConfigScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNameChanged = viewModel::onNameChanged,
        onAddressChanged = viewModel::onAddressChanged,
        onBankAccountChanged = viewModel::onBankAccountChanged,
        onBankSwiftChanged = viewModel::onBankSwiftChanged,
        onBankNameChanged = viewModel::onBankNameChanged,
        onSaveClicked = viewModel::onSaveClicked,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun SellerConfigScreen(
    uiState: SellerConfigUiState,
    onNavigateBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onBankAccountChanged: (String) -> Unit,
    onBankSwiftChanged: (String) -> Unit,
    onBankNameChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            PrimaryTopAppBar(
                title = stringResource(Res.string.settings_seller_info),
                navigationIcon = { BackNavigationIconButton(onClick = onNavigateBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(Res.string.seller_config_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.address,
                onValueChange = onAddressChanged,
                label = { Text(stringResource(Res.string.seller_config_address)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.bank_account_section))
            OutlinedTextField(
                value = uiState.bankAccount,
                onValueChange = onBankAccountChanged,
                label = { Text(stringResource(Res.string.bank_account_number)) },
                isError = uiState.bankAccountError != null,
                supportingText = uiState.bankAccountError?.let { { Text(it.asString()) } },
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.bankSwift,
                onValueChange = onBankSwiftChanged,
                label = { Text(stringResource(Res.string.bank_swift)) },
                isError = uiState.bankSwiftError != null,
                supportingText = uiState.bankSwiftError?.let { { Text(it.asString()) } },
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.bankName,
                onValueChange = onBankNameChanged,
                label = { Text(stringResource(Res.string.bank_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onSaveClicked, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.seller_config_save))
            }
        }
    }
}
