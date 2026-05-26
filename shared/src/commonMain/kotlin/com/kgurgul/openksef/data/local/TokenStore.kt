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

package com.kgurgul.openksef.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kgurgul.openksef.domain.model.KsefEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenStore(
    private val dataStore: DataStore<Preferences>,
    private val secureTokenStorage: SecureTokenStorage,
) {
    private companion object {
        val NIP_KEY = stringPreferencesKey("ksef_nip")
        val ENVIRONMENT_KEY = stringPreferencesKey("ksef_environment")
    }

    suspend fun saveToken(token: String) {
        secureTokenStorage.saveToken(token)
    }

    suspend fun getToken(): String? = secureTokenStorage.readToken()

    suspend fun saveNip(nip: String) {
        dataStore.edit { prefs -> prefs[NIP_KEY] = nip }
    }

    fun getNip(): Flow<String?> = dataStore.data.map { prefs -> prefs[NIP_KEY] }

    suspend fun saveEnvironment(env: KsefEnvironment) {
        dataStore.edit { prefs -> prefs[ENVIRONMENT_KEY] = env.name }
    }

    fun getEnvironment(): Flow<KsefEnvironment> =
        dataStore.data.map { prefs ->
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
        secureTokenStorage.clearToken()
        dataStore.edit { prefs -> prefs.clear() }
    }
}
