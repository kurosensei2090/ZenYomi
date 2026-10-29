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

    open fun registerExtractorAPI(api: ExtractorApi) {
        if (!com.lagradost.cloudstream3.utils.extractorApis.contains(api)) {
            com.lagradost.cloudstream3.utils.extractorApis.add(api)
        }
    }

    open fun registerExtractorAPI(api: Any) {
        if (api is ExtractorApi) {
            registerExtractorAPI(api)
        }
    }
}

open class Plugin : BasePlugin()
