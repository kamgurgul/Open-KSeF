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

package com.kgurgul.openksef.data.remote

/**
 * Platform-specific cryptographic primitives required by the KSeF v2 API.
 *
 * The API requires sensitive payloads (the KSeF token, the AES symmetric key for online sessions)
 * to be encrypted with the Ministry of Finance public key fetched from `GET
 * /security/public-key-certificates`. The chosen padding is RSA-OAEP with SHA-256 hash and
 * MGF1-SHA-256 mask generation.
 */
interface KsefCrypto {
    /**
     * Encrypts [data] with RSA-OAEP-SHA256 (MGF1 also SHA-256) using the public key extracted from
     * the DER-encoded X.509 [certificateDer]. Returns the raw ciphertext bytes (caller is
     * responsible for any further encoding such as Base64).
     */
    fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray): ByteArray
}

/** Returns the platform-default [KsefCrypto] implementation. */
expect fun defaultKsefCrypto(): KsefCrypto
