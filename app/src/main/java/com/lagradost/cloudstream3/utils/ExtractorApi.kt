package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

open class ExtractorApi {
    open var name: String = ""
    open var mainUrl: String = ""
    open val requiresReferer: Boolean = false

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit = {},
    ): List<ExtractorLink>? = null
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    return false
}
