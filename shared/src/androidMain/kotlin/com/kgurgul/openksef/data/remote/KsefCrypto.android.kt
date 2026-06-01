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

import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class JvmKsefCrypto : KsefCrypto {

    private val secureRandom = SecureRandom()

    override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray): ByteArray {
        val factory = CertificateFactory.getInstance("X.509")
        val cert =
            factory.generateCertificate(ByteArrayInputStream(certificateDer)) as X509Certificate
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val params =
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            )
        cipher.init(Cipher.ENCRYPT_MODE, cert.publicKey, params)
        return cipher.doFinal(data)
    }

    override fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size).also { secureRandom.nextBytes(it) }

    override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}

actual fun defaultKsefCrypto(): KsefCrypto = JvmKsefCrypto()
