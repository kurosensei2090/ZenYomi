package com.lagradost.cloudstream3.network

import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

class CloudflareKiller : Interceptor {
    companion object {
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun getCookieHeaders(url: String): okhttp3.Headers {
        val cookie = try {
            CookieManager.getInstance()?.getCookie(url)
        } catch (_: Exception) {
            null
        }
        val builder = okhttp3.Headers.Builder()
        if (!cookie.isNullOrBlank()) {
            builder.add("Cookie", cookie)
        }
        return builder.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val cookie = try {
            CookieManager.getInstance()?.getCookie(url)
        } catch (_: Exception) {
            null
        }

        val reqBuilder = request.newBuilder()
        if (!request.headers.names().any { it.equals("User-Agent", ignoreCase = true) }) {
            reqBuilder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        }

        val existingCookie = request.header("Cookie")
        val mergedCookie = when {
            cookie.isNullOrBlank() -> existingCookie
            existingCookie.isNullOrBlank() -> cookie
            else -> {
                val existingPairs = existingCookie.split(";").map { it.trim() }
                val cmPairs = cookie.split(";").map { it.trim() }
                val combined = (existingPairs + cmPairs).distinctBy { it.substringBefore("=") }
                combined.joinToString("; ")
            }
        }

        if (!mergedCookie.isNullOrBlank()) {
            val cookieMap = parseCookieMap(mergedCookie)
            request.url.host.let { host ->
                savedCookies[host] = cookieMap
            }
            reqBuilder.header("Cookie", mergedCookie)
        }

        val response = chain.proceed(reqBuilder.build())
        if (response.headers("Set-Cookie").isNotEmpty()) {
            try {
                CookieManager.getInstance()?.flush()
            } catch (_: Exception) {}
        }
        return response
    }
}
