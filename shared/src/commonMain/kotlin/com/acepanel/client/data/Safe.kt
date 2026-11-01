package com.acepanel.client.data

import kotlinx.coroutines.CancellationException

/**
 * 与 [runCatching] 行为一致的 Result 包装，但**不会吞掉协程取消**。
 *
 * 页面切换、下拉刷新、重复点击刷新都会取消上一个协程；如果用 [runCatching]，
 * `CancellationException("StandaloneCoroutine was cancelled")` 会被当成普通失败，
 * 于是给用户弹出“StandaloneCoroutine was cancelled”这种无意义报错，
 * 同时破坏结构化并发（取消后代码继续往下跑）。
 * 这里把取消原样抛出，只把真正的异常收进 Result。
 */
inline fun <R> runSafely(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}

/** 协程/线程取消不是错误，任何地方都不该把它提示给用户。 */
fun Throwable.isCancellation(): Boolean =
    this is CancellationException ||
        message?.isCancellationMessage() == true

/** 有些取消异常不是 CancellationException 类型（例如被包装过一层），只能按文案识别。 */
fun String.isCancellationMessage(): Boolean {
    val text = lowercase()
    return text.contains("standalonecoroutine") ||
        text.contains("scopecoroutine") ||
        text.contains("job was cancelled") ||
        text.contains("was cancelled") ||
        text.contains("was canceled") ||
        text.contains("not active") ||
        text == "cancelled" ||
        text == "canceled"
}
