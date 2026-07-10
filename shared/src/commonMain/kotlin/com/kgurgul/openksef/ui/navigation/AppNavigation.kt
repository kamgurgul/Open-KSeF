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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kgurgul.openksef.common.ObserveAsEvents
import com.kgurgul.openksef.ui.invoicedetail.InvoiceDetailScreen
import com.kgurgul.openksef.ui.invoicedetail.InvoiceDetailViewModel
import com.kgurgul.openksef.ui.invoices.InvoiceListScreen
import com.kgurgul.openksef.ui.invoices.InvoiceListViewModel
import com.kgurgul.openksef.ui.login.LoginScreen
import com.kgurgul.openksef.ui.login.LoginViewModel
import com.kgurgul.openksef.ui.main.MainEvent
import com.kgurgul.openksef.ui.main.MainViewModel
import com.kgurgul.openksef.ui.sendinvoice.SendInvoiceScreen
import com.kgurgul.openksef.ui.sendinvoice.SendInvoiceViewModel
import com.kgurgul.openksef.ui.settings.SellerConfigScreen
import com.kgurgul.openksef.ui.settings.SellerConfigViewModel
import com.kgurgul.openksef.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Navigation keys
/** [autoLogin] is true only for the app-start entry, so remembered credentials sign in
 * automatically once — logout and session-expiry redirects show the form instead. */
@Serializable
data class LoginKey(val autoLogin: Boolean = false) : NavKey

@Serializable data object InvoiceListKey : NavKey

@Serializable data class InvoiceDetailKey(val ksefRef: String) : NavKey

@Serializable data object SendInvoiceKey : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object SellerConfigKey : NavKey

@Composable
fun AppNavigation() {
    val backStack = remember { mutableStateListOf<Any>(LoginKey(autoLogin = true)) }

    val mainViewModel = koinViewModel<MainViewModel>()

    ObserveAsEvents(mainViewModel.events) { event ->
        when (event) {
            MainEvent.SessionExpired -> {
                backStack.clear()
                backStack.add(LoginKey())
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<LoginKey> { key ->
                    val viewModel =
                        koinViewModel<LoginViewModel> { parametersOf(key.autoLogin) }
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            backStack.clear()
                            backStack.add(InvoiceListKey)
                        },
                    )
                }

                entry<InvoiceListKey> {
                    val viewModel = koinViewModel<InvoiceListViewModel>()
                    InvoiceListScreen(
                        viewModel = viewModel,
                        onInvoiceClick = { ksefRef -> backStack.add(InvoiceDetailKey(ksefRef)) },
                        onSendInvoiceClick = { backStack.add(SendInvoiceKey) },
                        onSettingsClick = { backStack.add(SettingsKey) },
                        onLoggedOut = {
                            backStack.clear()
                            backStack.add(LoginKey())
                        },
                    )
                }

                entry<InvoiceDetailKey> { key ->
                    val viewModel =
                        koinViewModel<InvoiceDetailViewModel>(key = key.ksefRef) {
                            parametersOf(key.ksefRef)
                        }
                    InvoiceDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }

                entry<SendInvoiceKey> {
                    val viewModel = koinViewModel<SendInvoiceViewModel>()
                    SendInvoiceScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }

                entry<SettingsKey> {
                    SettingsScreen(
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onSellerConfigClick = { backStack.add(SellerConfigKey) },
                    )
                }

                entry<SellerConfigKey> {
                    val viewModel = koinViewModel<SellerConfigViewModel>()
                    SellerConfigScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
