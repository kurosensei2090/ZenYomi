@file:JvmName("ArchComponentExtKt")
package com.lagradost.cloudstream3.mvvm

import com.lagradost.api.Log

fun logError(throwable: Throwable) {
    Log.e("CloudStream", throwable.message ?: "CloudStream error", throwable)
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall()
    } catch (e: Throwable) {
        logError(e)
        null
    }
}
