package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Retries idempotent GET requests once when encountering transient gateway errors
 * (HTTP 502, 503, 504) or transient socket timeouts/connect errors.
 */
class TransientErrorRetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        var response: Response? = null
        try {
            response = chain.proceed(request)
            if (response.code !in TRANSIENT_HTTP_CODES) {
                return response
            }
            response.close()
        } catch (e: IOException) {
            if (e !is SocketTimeoutException && e !is ConnectException) {
                throw e
            }
        }

        try {
            Thread.sleep(RETRY_DELAY_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Retry interrupted")
        }

        return chain.proceed(request)
    }

    companion object {
        private val TRANSIENT_HTTP_CODES = setOf(502, 503, 504)
        private const val RETRY_DELAY_MS = 800L
    }
}
