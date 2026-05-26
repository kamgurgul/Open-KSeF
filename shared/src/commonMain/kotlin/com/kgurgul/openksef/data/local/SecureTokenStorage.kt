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

/**
 * Persists the KSeF authentication token in platform-secure storage so the value never lives in
 * plain text on disk. Android uses the AndroidKeyStore-backed AES/GCM cipher; iOS uses the
 * Keychain; desktop falls back to the regular DataStore (no OS-level keychain integration).
 */
interface SecureTokenStorage {

    suspend fun saveToken(token: String)

    suspend fun readToken(): String?

    suspend fun clearToken()
}

/** Returns the platform-default [SecureTokenStorage]. */
expect fun defaultSecureTokenStorage(): SecureTokenStorage
