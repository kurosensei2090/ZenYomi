package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

val jsonResponseParser: ResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return AppUtils.parseJson(text, kClass)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return AppUtils.tryParseJson(text, kClass)
    }

    override fun writeValueAsString(obj: Any): String {
        return AppUtils.toJson(obj)
    }
}

var app: Requests = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

@UnsafeSSL
var insecureApp: Requests = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}
