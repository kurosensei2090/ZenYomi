package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

open class BasePlugin {
    var filename: String? = null
    var resources: Any? = null
    val registeredAPIs = mutableListOf<MainAPI>()

    open fun load(context: Context) {}
    open fun load() {}

    open fun registerMainAPI(api: MainAPI) {
        registeredAPIs.add(api)
        APIHolder.addPlugin(api)
    }

    open fun registerExtractorAPI(api: ExtractorApi) {}
    open fun registerExtractorAPI(api: Any) {}
}

open class Plugin : BasePlugin()
