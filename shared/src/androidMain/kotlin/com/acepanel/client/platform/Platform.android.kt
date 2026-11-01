package com.acepanel.client.platform

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun rsaEncryptSha512(plain: String, publicKeyPem: String): String {
    val body = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\\s".toRegex(), "")
    val der = Base64.decode(body, Base64.DEFAULT)
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
    val oaep = OAEPParameterSpec(
        "SHA-512",
        "MGF1",
        MGF1ParameterSpec.SHA512,
        PSource.PSpecified.DEFAULT,
    )
    cipher.init(Cipher.ENCRYPT_MODE, key, oaep, SecureRandom())
    return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
}

private val trustAllManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

actual fun createAceHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureAceClient()
    engine {
        config {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAllManager), SecureRandom())
            }
            sslSocketFactory(sslContext.socketFactory, trustAllManager)
            hostnameVerifier { _, _ -> true }
            retryOnConnectionFailure(true)
        }
    }
}

actual fun platformName(): String = "Android"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()


actual fun debugAutoLogin(): Triple<String, String, String>? = null

actual fun debugStartRoute(): String? = null

actual fun debugForceMobile(): Boolean = false

actual fun readDebugRouteFile(): String? = null
