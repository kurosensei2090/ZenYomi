package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebViewResolver : Interceptor {
    var interceptUrl: Regex? = null
    var additionalUrls: List<Regex> = emptyList()
    var userAgent: String = USER_AGENT
    var timeout: Long = 30_000L

    constructor(interceptor: (Any) -> Boolean = { true }) {
        // Default constructor
    }

    constructor(
        interceptUrl: Regex,
        additionalUrls: List<Regex> = emptyList(),
        userAgent: String? = null,
        useOkhttp: Boolean = true,
        script: String? = null,
        scriptCallback: ((String) -> Unit)? = null,
        timeout: Long = 30_000L,
    ) {
        this.interceptUrl = interceptUrl
        this.additionalUrls = additionalUrls
        this.userAgent = userAgent ?: USER_AGENT
        this.timeout = timeout
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val targetRegex = interceptUrl ?: return chain.proceed(request)
        val act = CommonActivity.activity ?: return chain.proceed(request)

        val latch = CountDownLatch(1)
        var interceptedRequest: Request? = null
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            try {
                val wv = WebView(act).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = userAgent
                }

                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(wv, true)

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = req?.url?.toString() ?: return null
                        if (targetRegex.containsMatchIn(reqUrl) || additionalUrls.any { it.containsMatchIn(reqUrl) }) {
                            interceptedRequest = request.newBuilder().url(reqUrl).build()
                            latch.countDown()
                            handler.post {
                                try {
                                    cm.flush()
                                    wv.stopLoading()
                                    wv.destroy()
                                } catch (_: Exception) {}
                            }
                        }
                        return super.shouldInterceptRequest(view, req)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        cm.flush()
                    }
                }

                wv.loadUrl(request.url.toString())
            } catch (_: Throwable) {
                latch.countDown()
            }
        }

        try {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {}

        val finalReq = interceptedRequest ?: request
        return chain.proceed(finalReq)
    }
}

