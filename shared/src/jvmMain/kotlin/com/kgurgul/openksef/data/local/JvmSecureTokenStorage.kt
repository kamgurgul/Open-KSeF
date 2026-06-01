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

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.kgurgul.openksef.common.KsefLogger
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JvmSecureTokenStorage(private val storageDir: File) : SecureTokenStorage {

    private val keysetFile = File(storageDir, KEYSET_FILE_NAME)
    private val ciphertextFile = File(storageDir, CIPHERTEXT_FILE_NAME)

    private val aead: Aead by lazy {
        AeadConfig.register()
        loadOrCreateKeysetHandle().getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun loadOrCreateKeysetHandle(): KeysetHandle {
        if (!storageDir.exists()) storageDir.mkdirs()
        return if (keysetFile.exists()) {
            TinkJsonProtoKeysetFormat.parseKeyset(
                keysetFile.readText(),
                InsecureSecretKeyAccess.get(),
            )
        } else {
            KsefLogger.w {
                "Tink keyset stored without OS-level protection at ${keysetFile.absolutePath}; " +
                    "JVM target has no portable secure store."
            }
            val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            keysetFile.writeText(
                TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())
            )
            handle
        }
    }

    override suspend fun saveToken(token: String) =
        withContext(Dispatchers.IO) {
            val ciphertext = aead.encrypt(token.encodeToByteArray(), ASSOCIATED_DATA)
            ciphertextFile.writeText(Base64.getEncoder().encodeToString(ciphertext))
        }

    override suspend fun readToken(): String? =
        withContext(Dispatchers.IO) {
            if (!ciphertextFile.exists()) return@withContext null
            runCatching {
                    val ciphertext = Base64.getDecoder().decode(ciphertextFile.readText())
                    aead.decrypt(ciphertext, ASSOCIATED_DATA).decodeToString()
                }
                .getOrNull()
        }

    override suspend fun clearToken() =
        withContext(Dispatchers.IO) {
            if (ciphertextFile.exists()) ciphertextFile.delete()
            Unit
        }

    private companion object {
        const val KEYSET_FILE_NAME = "token_keyset.json"
        const val CIPHERTEXT_FILE_NAME = "token.bin"
        val ASSOCIATED_DATA = "openksef.token".encodeToByteArray()
    }
}

actual fun defaultSecureTokenStorage(): SecureTokenStorage {
    val userHome = System.getProperty("user.home")
    return JvmSecureTokenStorage(File("$userHome/.openksef"))
}
