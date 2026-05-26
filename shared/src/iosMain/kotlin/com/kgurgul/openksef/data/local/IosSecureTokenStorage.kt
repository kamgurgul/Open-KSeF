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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureTokenStorage : SecureTokenStorage {

    override suspend fun saveToken(token: String) =
        withContext(Dispatchers.Default) {
            deleteItem()
            val nsData = token.encodeToByteArray().toNSData()
            val cfData = CFBridgingRetain(nsData)
            try {
                withKeychainQuery { query ->
                    CFDictionaryAddValue(query, kSecValueData, cfData)
                    CFDictionaryAddValue(
                        query,
                        kSecAttrAccessible,
                        kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )
                    val status = SecItemAdd(query, null)
                    check(status == errSecSuccess) {
                        "Keychain SecItemAdd failed with status $status"
                    }
                }
            } finally {
                if (cfData != null) CFRelease(cfData)
            }
        }

    override suspend fun readToken(): String? =
        withContext(Dispatchers.Default) {
            withKeychainQuery { query ->
                CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
                memScoped {
                    val resultVar = alloc<CFTypeRefVar>()
                    val status: OSStatus = SecItemCopyMatching(query, resultVar.ptr)
                    when (status) {
                        errSecSuccess -> {
                            val nsData =
                                resultVar.value?.let { CFBridgingRelease(it) as? NSData }
                            nsData?.toByteArray()?.decodeToString()
                        }
                        errSecItemNotFound -> null
                        else ->
                            error("Keychain SecItemCopyMatching failed with status $status")
                    }
                }
            }
        }

    override suspend fun clearToken() = withContext(Dispatchers.Default) { deleteItem() }

    private fun deleteItem() {
        withKeychainQuery { query ->
            val status = SecItemDelete(query)
            check(status == errSecSuccess || status == errSecItemNotFound) {
                "Keychain SecItemDelete failed with status $status"
            }
        }
    }

    private inline fun <T> withKeychainQuery(block: (CFMutableDictionaryRef) -> T): T {
        val query =
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )!!
        val service = SERVICE.toCFString()
        val account = ACCOUNT.toCFString()
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            return block(query)
        } finally {
            CFRelease(service)
            CFRelease(account)
            CFRelease(query)
        }
    }

    private companion object {
        const val SERVICE = "com.kgurgul.openksef"
        const val ACCOUNT = "ksef_token"
    }
}

actual fun defaultSecureTokenStorage(): SecureTokenStorage = IosSecureTokenStorage()

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFString(): CFStringRef =
    CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)!!

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
