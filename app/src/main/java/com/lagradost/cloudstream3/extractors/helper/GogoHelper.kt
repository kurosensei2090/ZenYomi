package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document

object GogoHelper {
    suspend fun extractVidstream(
        url: String,
        name: String,
        callback: (ExtractorLink) -> Unit,
        prefix: String = "",
        suffix: String = "",
        mainUrl: String = "",
        isM3u8: Boolean = false,
        isDash: Boolean = false,
        document: Document? = null,
    ): List<ExtractorLink> {
        return emptyList()
    }
}
