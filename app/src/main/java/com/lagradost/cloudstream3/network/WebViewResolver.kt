package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Response

class WebViewResolver(val interceptor: (Any) -> Boolean = { true }) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
