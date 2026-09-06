package com.parental.shared.api

/**
 * Extension functions for [ApiResult] to enable ergonomic chaining
 * of success/failure handlers (similar to Kotlin's Result<T> API).
 */
fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

fun <T> ApiResult<T>.onFailure(action: (Throwable) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) {
        action(RuntimeException("HTTP ${code}: $message"))
    }
    return this
}
