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

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

class AndroidSecureTokenStorage(context: Context) : SecureTokenStorage {

    private val appContext = context.applicationContext

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private val ciphertextPrefs by lazy {
        appContext.getSharedPreferences(CIPHERTEXT_PREF_FILE, Context.MODE_PRIVATE)
    }

    override suspend fun saveToken(token: String) =
        withContext(Dispatchers.IO) {
            val ciphertext = aead.encrypt(token.encodeToByteArray(), ASSOCIATED_DATA)
            ciphertextPrefs.edit {
                putString(TOKEN_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }
        }

    override suspend fun readToken(): String? =
        withContext(Dispatchers.IO) {
            val stored = ciphertextPrefs.getString(TOKEN_KEY, null) ?: return@withContext null
            runCatching {
                val ciphertext = Base64.decode(stored, Base64.NO_WRAP)
                aead.decrypt(ciphertext, ASSOCIATED_DATA).decodeToString()
            }
                .getOrNull()
        }

    override suspend fun clearToken() =
        withContext(Dispatchers.IO) { ciphertextPrefs.edit { remove(TOKEN_KEY) } }

    private companion object {
        const val KEYSET_NAME = "openksef_token_keyset"
        const val KEYSET_PREF_FILE = "openksef_secure_keyset"
        const val CIPHERTEXT_PREF_FILE = "openksef_secure_token"
        const val TOKEN_KEY = "token"
        const val MASTER_KEY_URI = "android-keystore://openksef_token_master_key"
        val ASSOCIATED_DATA = "openksef.token".encodeToByteArray()
    }
}

actual fun defaultSecureTokenStorage(): SecureTokenStorage =
    AndroidSecureTokenStorage(KoinPlatform.getKoin().get<Context>())
