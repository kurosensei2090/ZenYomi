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

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val cookie = try {
            CookieManager.getInstance()?.getCookie(url)
        } catch (_: Exception) {
            null
        }

        val newReq = if (!cookie.isNullOrBlank()) {
            val cookieMap = parseCookieMap(cookie)
            request.url.host.let { host ->
                savedCookies[host] = cookieMap
            }
            request.newBuilder()
                .header("Cookie", cookie)
                .build()
        } else {
            request
        }
        return chain.proceed(newReq)
    }
}
