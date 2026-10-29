package com.lagradost.nicehttp

import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ResponseParser

/**
 * CloudStream NiceResponse compatibility wrapper for ZenYomi.
 */
class NiceResponse(
    val okhttpResponse: Response,
    private val responseBytes: ByteArray,
) {
    val code: Int = okhttpResponse.code
    val isSuccessful: Boolean = okhttpResponse.isSuccessful
    val headers: Headers = okhttpResponse.headers
    val url: String = okhttpResponse.request.url.toString()

    val text: String by lazy {
        String(responseBytes, Charsets.UTF_8)
    }

    val body: ByteArray
        get() = responseBytes

    val document: Document by lazy {
        Jsoup.parse(text, url)
    }

    inline fun <reified T> parsed(): T {
        return com.lagradost.cloudstream3.utils.AppUtils.parseJson<T>(text)
    }

    inline fun <reified T> parsedSafe(): T? {
        return com.lagradost.cloudstream3.utils.AppUtils.tryParseJson<T>(text)
    }
}

/**
 * CloudStream Requests / NiceHttp client backed by ZenYomi's OkHttpClient.
 */
class Requests(
    val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        cache: Boolean = false,
        cacheTime: Int = 0,
        timeUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 0,
        interceptor: Interceptor? = null,
        allowRedirects: Boolean = true,
        responseParser: ResponseParser? = null,
    ): NiceResponse {
        val duration = if (timeout > 0) timeout.seconds else 30.seconds
        return request(
            url = url,
            method = "GET",
            headers = headers,
            referer = referer,
            params = params,
            cookies = cookies,
            timeout = duration,
            allowRedirects = allowRedirects,
        )
    }

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        json: String? = null,
        requestBody: okhttp3.RequestBody? = null,
        cache: Boolean = false,
        cacheTime: Int = 0,
        timeUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 0,
        interceptor: Interceptor? = null,
        allowRedirects: Boolean = true,
        responseParser: ResponseParser? = null,
    ): NiceResponse {
        val duration = if (timeout > 0) timeout.seconds else 30.seconds
        val body = when {
            requestBody != null -> requestBody
            json != null -> json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            data != null -> {
                val formBody = okhttp3.FormBody.Builder()
                data.forEach { (k, v) -> formBody.add(k, v) }
                formBody.build()
            }
            else -> "".toRequestBody(null)
        }

        return request(
            url = url,
            method = "POST",
            headers = headers,
            referer = referer,
            params = params,
            cookies = cookies,
            requestBody = body,
            timeout = duration,
            allowRedirects = allowRedirects,
        )
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): NiceResponse {
        return request(url = url, method = "HEAD", headers = headers, referer = referer)
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        requestBody: okhttp3.RequestBody? = null,
        timeout: Duration = 30.seconds,
        allowRedirects: Boolean = true,
    ): NiceResponse {
        val cleanUrl = when {
            url.startsWith("//") -> "https:$url"
            !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true) -> "https://$url"
            else -> url
        }
        var httpUrl = cleanUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $url")

        if (params.isNotEmpty()) {
            val builder = httpUrl.newBuilder()
            params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            httpUrl = builder.build()
        }

        val requestHeaders = headers.toMutableMap()
        if (referer != null) {
            requestHeaders["Referer"] = referer
        }
        if (cookies.isNotEmpty()) {
            val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            requestHeaders["Cookie"] = cookieString
        }
        if (!requestHeaders.containsKey("User-Agent")) {
            requestHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        val request = Request.Builder()
            .url(httpUrl)
            .method(method, requestBody)
            .headers(requestHeaders.toHeaders())
            .build()

        val client = if (baseClient.connectTimeoutMillis.toLong() != timeout.inWholeMilliseconds ||
            baseClient.followRedirects != allowRedirects
        ) {
            baseClient.newBuilder()
                .connectTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .readTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .followRedirects(allowRedirects)
                .build()
        } else {
            baseClient
        }

        val response = client.newCall(request).execute()
        val bytes = response.body?.bytes() ?: ByteArray(0)
        return NiceResponse(response, bytes)
    }
}
