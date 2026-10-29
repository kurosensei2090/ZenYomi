package com.lagradost.cloudstream3.utils

enum class Qualities(val value: Int) {
    Unknown(400),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160);

    companion object {
        fun getStringByInt(qual: Int?): String {
            return when (qual) {
                null -> "Auto"
                Unknown.value -> "Auto"
                P144.value -> "144p"
                P240.value -> "240p"
                P360.value -> "360p"
                P480.value -> "480p"
                P720.value -> "720p"
                P1080.value -> "1080p"
                P1440.value -> "1440p"
                P2160.value -> "4K"
                else -> "${qual}p"
            }
        }
    }
}

enum class ExtractorLinkType {
    M3U8,
    DASH,
    VIDEO,
    TORRENT,
    MAGNET,
    OTHER
}

data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractorData: String? = null,
    val type: ExtractorLinkType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
)
