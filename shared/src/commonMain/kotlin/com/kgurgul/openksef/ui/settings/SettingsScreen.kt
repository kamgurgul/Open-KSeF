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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.ui.components.BackNavigationIconButton
import com.kgurgul.openksef.ui.components.SectionHeader
import com.kgurgul.openksef.ui.theme.jetBrainsMonoFamily
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.login_environment_label
import openksef.shared.generated.resources.login_nip_label
import openksef.shared.generated.resources.settings_account
import openksef.shared.generated.resources.settings_seller_info
import openksef.shared.generated.resources.settings_seller_info_description
import openksef.shared.generated.resources.settings_title
import openksef.shared.generated.resources.settings_value_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onSellerConfigClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSellerConfigClick = onSellerConfigClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onSellerConfigClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = { BackNavigationIconButton(onClick = onNavigateBack) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            val unknown = stringResource(Res.string.settings_value_unknown)
            SectionHeader(
                title = stringResource(Res.string.settings_account),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.login_nip_label)) },
                supportingContent = {
                    Text(text = uiState.nip.ifBlank { unknown }, fontFamily = jetBrainsMonoFamily())
                },
                leadingContent = { Icon(Icons.Default.Badge, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.login_environment_label)) },
                supportingContent = { Text(uiState.environment?.name ?: unknown) },
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_seller_info)) },
                supportingContent = {
                    Text(stringResource(Res.string.settings_seller_info_description))
                },
                leadingContent = { Icon(Icons.Default.Store, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onSellerConfigClick),
            )
        }
    }
}
