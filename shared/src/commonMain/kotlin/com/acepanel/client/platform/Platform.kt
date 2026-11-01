package com.acepanel.client.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay

expect fun rsaEncryptSha512(plain: String, publicKeyPem: String): String

expect fun createAceHttpClient(): HttpClient

expect fun platformName(): String

expect fun currentTimeMillis(): Long

/** JVM-only development helpers, driven by -Dacepanel.* system properties. */
expect fun debugAutoLogin(): Triple<String, String, String>?

expect fun debugStartRoute(): String?

expect fun debugForceMobile(): Boolean

expect fun readDebugRouteFile(): String?

internal fun HttpClientConfig<*>.configureAceClient() {
    expectSuccess = false
    // 安全入口校验依赖 302 响应上的 Set-Cookie，因此不让客户端自动跟随重定向
    followRedirects = false
    install(WebSockets) {
        pingIntervalMillis = 20_000
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 25_000
    }
}

/**
 * 面板经常使用自签证书，这里在客户端侧接受本地/自签名证书。
 * 仅用于用户明确添加的面板地址，不会用于任意第三方导航。
 */
internal fun normalizeBaseUrl(input: String): String {
    var value = input.trim().trimEnd('/')
    if (value.isEmpty()) return value
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "https://$value"
    }
    return value
}

internal suspend fun <T> retry(times: Int = 2, delayMs: Long = 350, block: suspend () -> T): T {
    var last: Throwable? = null
    repeat(times + 1) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            last = t
            if (attempt < times) delay(delayMs * (attempt + 1))
        }
    }
    throw last ?: IllegalStateException("unknown error")
}
