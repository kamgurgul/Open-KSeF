package com.kgurgul.openksef.data.local

import com.kgurgul.openksef.data.di.DATA_STORE_PATH_QUALIFIER
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

actual fun dataStorePath(): String {
    return KoinPlatform.getKoin().get<String>(named(DATA_STORE_PATH_QUALIFIER))
}
