package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    return false
}
