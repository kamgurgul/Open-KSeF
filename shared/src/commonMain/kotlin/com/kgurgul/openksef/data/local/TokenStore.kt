package com.kgurgul.openksef.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kgurgul.openksef.domain.model.KsefEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenStore(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val TOKEN_KEY = stringPreferencesKey("ksef_token")
        val NIP_KEY = stringPreferencesKey("ksef_nip")
        val ENVIRONMENT_KEY = stringPreferencesKey("ksef_environment")
    }

    suspend fun saveToken(token: String) {
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }

    fun getToken(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }

    suspend fun saveNip(nip: String) {
        dataStore.edit { prefs ->
            prefs[NIP_KEY] = nip
        }
    }

    fun getNip(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[NIP_KEY]
    }

    suspend fun saveEnvironment(env: KsefEnvironment) {
        dataStore.edit { prefs ->
            prefs[ENVIRONMENT_KEY] = env.name
        }
    }

    fun getEnvironment(): Flow<KsefEnvironment> = dataStore.data.map { prefs ->
        val name = prefs[ENVIRONMENT_KEY]
        if (name != null) {
            try {
                KsefEnvironment.valueOf(name)
            } catch (_: Exception) {
                KsefEnvironment.TEST
            }
        } else {
            KsefEnvironment.TEST
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
