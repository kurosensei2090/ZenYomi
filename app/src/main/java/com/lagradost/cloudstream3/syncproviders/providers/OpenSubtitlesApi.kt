package com.lagradost.cloudstream3.syncproviders.providers

open class OpenSubtitlesApi {
    companion object {
        const val API_KEY = "uyBLgFD17MgrYmA0gSXoKllMJBelOYj2"
        const val HOST = "https://api.opensubtitles.com/api/v1"
        const val userAgent = "Cloudstream3 v0.2"
        val headers = mapOf("user-agent" to userAgent, "Api-Key" to API_KEY)
    }
}
