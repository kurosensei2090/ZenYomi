package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

open class Plugin {
    val registeredAPIs = mutableListOf<MainAPI>()

    open fun load(context: Context) {}

    fun registerMainAPI(api: MainAPI) {
        registeredAPIs.add(api)
    }

    fun registerExtractorAPI(api: Any) {}
}
