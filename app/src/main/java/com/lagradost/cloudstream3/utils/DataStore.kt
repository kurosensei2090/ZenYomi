package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences

object DataStore {
    val mapper = com.lagradost.cloudstream3.mapper

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getSharedPrefs(): SharedPreferences {
        return getSharedPreferences("cloudstream_datastore", Context.MODE_PRIVATE)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return getSharedPreferences("cloudstream_datastore", Context.MODE_PRIVATE)
    }

    fun <T> setKey(context: Context, key: String, value: T) {
        try {
            val json = com.lagradost.cloudstream3.utils.AppUtils.toJson(value)
            context.getSharedPreferences("cloudstream_datastore", Context.MODE_PRIVATE)
                .edit()
                .putString(key, json)
                .apply()
        } catch (_: Throwable) {}
    }

    inline fun <reified T> getKey(context: Context, key: String, default: T? = null): T? {
        val raw = context.getSharedPreferences("cloudstream_datastore", Context.MODE_PRIVATE)
            .getString(key, null) ?: return default
        return try {
            com.lagradost.cloudstream3.utils.AppUtils.parseJson(raw)
        } catch (_: Throwable) {
            default
        }
    }

    fun <T : Any> getKey(context: Context, key: String, kClass: kotlin.reflect.KClass<T>, default: T? = null): T? {
        val raw = context.getSharedPreferences("cloudstream_datastore", Context.MODE_PRIVATE)
            .getString(key, null) ?: return default
        return try {
            com.lagradost.cloudstream3.utils.AppUtils.tryParseJson(raw, kClass) ?: default
        } catch (_: Throwable) {
            default
        }
    }
}
