package com.kgurgul.openksef.data.di

import com.kgurgul.openksef.data.local.DATA_STORE_FILE_NAME
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidModule = module {
    factory(named(DATA_STORE_PATH_QUALIFIER)) {
        androidContext().filesDir.absolutePath + "/$DATA_STORE_FILE_NAME"
    }
}

const val DATA_STORE_PATH_QUALIFIER = "dataStorePath"