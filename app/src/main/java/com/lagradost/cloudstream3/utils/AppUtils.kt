package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.mapper

object AppUtils {
    var mapper: ObjectMapper = com.lagradost.cloudstream3.mapper

    fun toJson(value: Any?): String = try {
        mapper.writeValueAsString(value)
    } catch (_: Exception) {
        value?.toString() ?: ""
    }

    inline fun <reified T> parseJson(text: String): T {
        return mapper.readValue(text, object : TypeReference<T>() {})
    }

    inline fun <reified T> tryParseJson(text: String): T? {
        return try {
            mapper.readValue(text, object : TypeReference<T>() {})
        } catch (_: Exception) {
            null
        }
    }
}

fun toJson(value: Any?): String = AppUtils.toJson(value)

inline fun <reified T> parseJson(text: String): T = AppUtils.parseJson(text)

inline fun <reified T> tryParseJson(text: String): T? = AppUtils.tryParseJson(text)
