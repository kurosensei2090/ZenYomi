package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Interceptor that ensures required browser security headers (Sec-Fetch-*, Origin, Referer)
 * and session state are correctly supplied for protected video playback APIs (e.g. AnimeZid).
 */
class SpecificHostSecurityInterceptor : Interceptor {

    private val cachedSessionCookies = ConcurrentHashMap<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val host = originalRequest.url.host.lowercase()

        if (isProtectedHost(host)) {
            val path = originalRequest.url.encodedPath
            val builder = originalRequest.newBuilder()

            // 1. Inject appropriate Sec-Fetch-* browser navigation/fetch headers
            if (path.contains("/web-playback/launch/") || path.contains("/launch/")) {
                builder.header("Sec-Fetch-Dest", "iframe")
                builder.header("Sec-Fetch-Mode", "navigate")
                builder.header("Sec-Fetch-Site", "same-origin")
            } else {
                if (originalRequest.header("Sec-Fetch-Dest").isNullOrEmpty()) {
                    builder.header("Sec-Fetch-Dest", "empty")
                }
                if (originalRequest.header("Sec-Fetch-Mode").isNullOrEmpty()) {
                    builder.header("Sec-Fetch-Mode", "cors")
                }
                if (originalRequest.header("Sec-Fetch-Site").isNullOrEmpty()) {
                    builder.header("Sec-Fetch-Site", "same-origin")
                }
                if (originalRequest.header("Origin").isNullOrEmpty()) {
                    builder.header("Origin", "https://${originalRequest.url.host}")
                }
            }

            // 2. Cookie persistence fallback (in case caller didn't pass PHPSESSID)
            val currentCookie = originalRequest.header("Cookie")
            if (currentCookie.isNullOrEmpty() || !currentCookie.contains("PHPSESSID")) {
                val cached = cachedSessionCookies[host]
                if (!cached.isNullOrEmpty()) {
                    val merged = if (currentCookie.isNullOrEmpty()) cached else "$currentCookie; $cached"
                    builder.header("Cookie", merged)
                }
            } else {
                val match = PHPSESSID_REGEX.find(currentCookie)
                if (match != null) {
                    cachedSessionCookies[host] = match.value
                }
            }

            val response = chain.proceed(builder.build())

            // 3. Cache observed PHPSESSID from Set-Cookie headers
            val setCookies = response.headers("Set-Cookie")
            for (sc in setCookies) {
                val match = PHPSESSID_REGEX.find(sc)
                if (match != null) {
                    cachedSessionCookies[host] = match.value
                }
            }

            return response
        }

        return chain.proceed(originalRequest)
    }

    private fun isProtectedHost(host: String): Boolean {
        return host.endsWith("animezid.org") ||
            host.contains("animezid.")
    }

    companion object {
        private val PHPSESSID_REGEX = Regex("""PHPSESSID=[a-zA-Z0-9]+""")
    }
}
