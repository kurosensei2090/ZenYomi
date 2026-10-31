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

    open fun load(context: Context) {
        load()
    }
    open fun load() {}

    open fun beforeUnload() {}

    open fun registerMainAPI(api: MainAPI) {
        if (!registeredAPIs.contains(api)) {
            registeredAPIs.add(api)
        }
        APIHolder.addPlugin(api)
    }

    open fun registerMainAPI(api: Any) {
        if (api is MainAPI) {
            registerMainAPI(api)
        }
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

open class Plugin : BasePlugin() {
    var openSettings: ((context: Context) -> Unit)? = null

    override fun load(context: Context) {
        load()
    }

    override fun registerMainAPI(api: MainAPI) {
        super.registerMainAPI(api)
    }

    override fun registerExtractorAPI(api: ExtractorApi) {
        super.registerExtractorAPI(api)
    }
}

