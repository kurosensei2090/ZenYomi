package com.lagradost.cloudstream3.utils

object StringUtils {
    fun fixUrl(url: String, domain: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$domain$url"
            else -> "$domain/$url"
        }
    }

    fun encodeUri(str: String): String {
        return try {
            java.net.URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }
}
