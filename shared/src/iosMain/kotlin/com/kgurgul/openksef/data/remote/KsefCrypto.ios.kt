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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.kSecKeyAlgorithmRSAEncryptionOAEPSHA256
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKsefCrypto : KsefCrypto {
    override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray): ByteArray {
        @Suppress("UNCHECKED_CAST")
        val certCfData = CFBridgingRetain(certificateDer.toNSData()) as CFDataRef
        try {
            val certificate =
                SecCertificateCreateWithData(null, certCfData)
                    ?: error("Invalid X.509 certificate provided to KsefCrypto")
            try {
                val publicKey =
                    SecCertificateCopyKey(certificate)
                        ?: error("Could not extract public key from certificate")
                try {
                    @Suppress("UNCHECKED_CAST")
                    val plainCfData = CFBridgingRetain(data.toNSData()) as CFDataRef
                    try {
                        return memScoped {
                            val errorVar = alloc<CFErrorRefVar>()
                            val cipherCfData =
                                SecKeyCreateEncryptedData(
                                    publicKey,
                                    kSecKeyAlgorithmRSAEncryptionOAEPSHA256,
                                    plainCfData,
                                    errorVar.ptr,
                                )
                                    ?: run {
                                        val nsError =
                                            errorVar.value?.let {
                                                CFBridgingRelease(it) as? NSError
                                            }
                                        error(
                                            "RSA-OAEP-SHA256 encryption failed: " +
                                                (nsError?.localizedDescription ?: "unknown error")
                                        )
                                    }

                            @Suppress("UNCHECKED_CAST")
                            val cipherNs = CFBridgingRelease(cipherCfData) as NSData
                            cipherNs.toByteArray()
                        }
                    } finally {
                        CFRelease(plainCfData)
                    }
                } finally {
                    CFRelease(publicKey)
                }
            } finally {
                CFRelease(certificate)
            }
        } finally {
            CFRelease(certCfData)
        }
    }
}

actual fun defaultKsefCrypto(): KsefCrypto = IosKsefCrypto()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out ->
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
