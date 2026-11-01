package com.acepanel.client.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFBridgingRetain
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionOAEPSHA512

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned {
        NSData.dataWithBytes(it.addressOf(0), size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.base64ToNSData(): NSData =
    NSData.create(base64EncodedString = this, options = 0u) ?: error("公钥 Base64 解析失败")

@OptIn(ExperimentalForeignApi::class)
actual fun rsaEncryptSha512(plain: String, publicKeyPem: String): String = memScoped {
    val base64 = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\\s".toRegex(), "")

    val derData = base64.base64ToNSData()
    val derRef = CFBridgingRetain(derData) as CFDataRef
    val errorRef = alloc<CFErrorRefVar>()
    val attrs = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        4,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )
    CFDictionarySetValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeRSA)
    CFDictionarySetValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPublic)

    val key = SecKeyCreateWithData(derRef, attrs as CFDictionaryRef, errorRef.ptr)
        ?: error("无法解析面板公钥")
    val inputData = plain.encodeToByteArray().toNSData()
    val inputRef = CFBridgingRetain(inputData) as CFDataRef
    val encrypted = SecKeyCreateEncryptedData(
        key,
        kSecKeyAlgorithmRSAEncryptionOAEPSHA512,
        inputRef,
        errorRef.ptr,
    ) ?: error("RSA 加密失败")

    val length = CFDataGetLength(encrypted).toInt()
    val bytes = CFDataGetBytePtr(encrypted)!!.reinterpret<ByteVar>()
    val out = ByteArray(length) { index -> bytes[index].toByte() }
    val result = out.toNSData().base64EncodedStringWithOptions(0u)

    CFRelease(derRef)
    CFRelease(inputRef)
    CFRelease(encrypted)
    CFRelease(key)
    CFRelease(attrs)
    result
}

actual fun createAceHttpClient(): HttpClient = HttpClient(Darwin) {
    configureAceClient()
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val trust = challenge.protectionSpace.serverTrust
            if (trust != null) {
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential,
                    NSURLCredential.credentialForTrust(trust),
                )
            } else {
                completionHandler(NSURLSessionAuthChallengeUseCredential, null)
            }
        }
    }
}

actual fun platformName(): String = "iOS"

actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate.date().timeIntervalSince1970 * 1000.0).toLong()


actual fun debugAutoLogin(): Triple<String, String, String>? = null

actual fun debugStartRoute(): String? = null

actual fun debugForceMobile(): Boolean = false

actual fun readDebugRouteFile(): String? = null
