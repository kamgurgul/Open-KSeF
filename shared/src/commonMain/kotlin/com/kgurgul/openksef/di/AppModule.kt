package com.kgurgul.openksef.di

import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.local.TokenStore
import com.kgurgul.openksef.data.local.createDataStore
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefApiClient
import com.kgurgul.openksef.data.remote.defaultKsefCrypto
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.ui.invoicedetail.InvoiceDetailViewModel
import com.kgurgul.openksef.ui.invoices.InvoiceListViewModel
import com.kgurgul.openksef.ui.login.LoginViewModel
import com.kgurgul.openksef.ui.sendinvoice.SendInvoiceViewModel
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
    singleOf(::TokenStore)
    singleOf(::SessionHolder)
    single { KsefApiClient.create(get(), get()) }
    singleOf(::KsefApi)
    single { defaultKsefCrypto() }
    singleOf(::KsefRepository)

    // ViewModels
    viewModelOf(::LoginViewModel)
    viewModelOf(::InvoiceListViewModel)
    viewModel { params -> InvoiceDetailViewModel(params.get(), get()) }
    viewModelOf(::SendInvoiceViewModel)
}
