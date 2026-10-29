package com.lagradost.cloudstream3.utils

import kotlinx.serialization.json.Json

val csJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

object AppUtils {
    var mapper: Any? = null

    fun toJson(value: Any?): String = value?.toString() ?: ""
}

fun toJson(value: Any?): String = value?.toString() ?: ""

inline fun <reified T> parseJson(text: String): T {
    return csJson.decodeFromString(text)
}

inline fun <reified T> tryParseJson(text: String): T? {
    return try {
        csJson.decodeFromString<T>(text)
    } catch (_: Exception) {
        null
    }
}
