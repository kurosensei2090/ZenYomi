package com.lagradost.cloudstream3.utils

class M3u8Helper {
    companion object {
        suspend fun generateM3u8(
            source: String,
            m3u8Url: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String = source,
        ): List<ExtractorLink> {
            return listOf(
                ExtractorLink(
                    source = source,
                    name = name,
                    url = m3u8Url,
                    referer = referer,
                    quality = quality ?: Qualities.P1080.value,
                    isM3u8 = true,
                    headers = headers,
                ),
            )
        }
    }
}
