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

package com.kgurgul.openksef.di

import com.kgurgul.openksef.data.SessionEventBus
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.local.TokenStore
import com.kgurgul.openksef.data.local.createDataStore
import com.kgurgul.openksef.data.local.db.AppDatabase
import com.kgurgul.openksef.data.local.db.getDatabaseBuilder
import com.kgurgul.openksef.data.local.db.getRoomDatabase
import com.kgurgul.openksef.data.local.defaultSecureTokenStorage
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefApiClient
import com.kgurgul.openksef.data.remote.defaultKsefCrypto
import com.kgurgul.openksef.data.repository.InvoiceTemplateRepository
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.data.repository.RoomInvoiceTemplateRepository
import com.kgurgul.openksef.data.repository.RoomSellerConfigRepository
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.pdf.InvoicePdfExporter
import com.kgurgul.openksef.domain.pdf.InvoicePdfSharer
import com.kgurgul.openksef.domain.pdf.KsefWebPdfRenderer
import com.kgurgul.openksef.domain.pdf.defaultInvoicePdfExporter
import com.kgurgul.openksef.domain.pdf.defaultInvoicePdfSharer
import com.kgurgul.openksef.domain.pdf.defaultKsefWebPdfRenderer
import com.kgurgul.openksef.ui.invoicedetail.InvoiceDetailViewModel
import com.kgurgul.openksef.ui.invoices.InvoiceListViewModel
import com.kgurgul.openksef.ui.login.LoginViewModel
import com.kgurgul.openksef.ui.main.MainViewModel
import com.kgurgul.openksef.ui.sendinvoice.SendInvoiceViewModel
import com.kgurgul.openksef.ui.settings.SellerConfigViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Data
    single { createDataStore() }
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }
    }
    single { defaultSecureTokenStorage() }
    singleOf(::TokenStore)
    singleOf(::SessionHolder)
    singleOf(::SessionEventBus)
    single { KsefApiClient.create(get(), get(), get()) }
    singleOf(::KsefApi)
    single { defaultKsefCrypto() }
    singleOf(::KsefRepository)
    single { getRoomDatabase(getDatabaseBuilder()) }
    single { get<AppDatabase>().invoiceTemplateDao() }
    single<InvoiceTemplateRepository> { RoomInvoiceTemplateRepository(get()) }
    single { get<AppDatabase>().sellerConfigDao() }
    single<SellerConfigRepository> { RoomSellerConfigRepository(get()) }

    // Domain
    single<InvoicePdfExporter> { defaultInvoicePdfExporter() }
    single<KsefWebPdfRenderer> { defaultKsefWebPdfRenderer() }
    single<InvoicePdfSharer> { defaultInvoicePdfSharer() }

    // ViewModels
    viewModelOf(::MainViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::InvoiceListViewModel)
    viewModel { params -> InvoiceDetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModelOf(::SendInvoiceViewModel)
    viewModelOf(::SellerConfigViewModel)
}
