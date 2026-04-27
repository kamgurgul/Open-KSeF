package com.kgurgul.openksef.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

expect fun dataStorePath(): String

fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStorePath().toPath() }
    )
}

internal const val DATA_STORE_FILE_NAME = "openksef_prefs.preferences_pb"
