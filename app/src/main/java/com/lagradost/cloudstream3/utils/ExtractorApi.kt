package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

val extractorApis = mutableListOf<ExtractorApi>()

open class ExtractorApi {
    open var name: String = ""
    open var mainUrl: String = ""
    open val requiresReferer: Boolean = false

    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = null

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit = {},
    ): List<ExtractorLink>? {
        val links = getUrl(url, referer)
        links?.forEach(callback)
        return links
    }
}

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    initializer: (suspend ExtractorLink.() -> Unit)? = null,
): ExtractorLink {
    val link = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        type = type,
        isM3u8 = type == ExtractorLinkType.M3U8,
    )
    if (initializer != null) {
        link.initializer()
    }
    return link
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit = {},
    callback: (ExtractorLink) -> Unit = {},
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

suspend fun loadExtractor(
    url: String,
    callback: (ExtractorLink) -> Unit,
): Boolean = loadExtractor(url, null, {}, callback)

suspend fun loadExtractor(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit = {},
    callback: (ExtractorLink) -> Unit = {},
): Boolean {
    if (url.isBlank()) return false

    // 1. Check registered extractors
    for (extractor in extractorApis.toList()) {
        val matchesDomain = extractor.mainUrl.isNotBlank() && url.contains(extractor.mainUrl, ignoreCase = true)
        val matchesName = extractor.name.isNotBlank() && url.contains(extractor.name, ignoreCase = true)
        if (matchesDomain || matchesName) {
            try {
                val links = extractor.getUrl(url, referer, subtitleCallback, callback)
                if (!links.isNullOrEmpty()) return true
            } catch (t: Throwable) {
                Log.w("Extractor", "Extractor ${extractor.name} failed for $url", t)
            }
        }
    }

    // 2. Direct media & cloud drive links
    val cleanUrl = url.trim()

    if (cleanUrl.contains("drive.google.com", ignoreCase = true) || cleanUrl.contains("drive.usercontent.google.com", ignoreCase = true)) {
        val fileIdRegex = Regex("""(?:/file/d/|[?&]id=)([0-9A-Za-z_-]{10,})""")
        val fileId = fileIdRegex.find(cleanUrl)?.groupValues?.get(1)
        if (!fileId.isNullOrBlank()) {
            val directUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
            callback(
                ExtractorLink(
                    source = "GoogleDrive",
                    name = "Google Drive (Direct)",
                    url = directUrl,
                    referer = "https://drive.google.com/",
                    quality = Qualities.P1080.value,
                    isM3u8 = false,
                ),
            )
            return true
        }
    }

    val isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
    val isMp4 = cleanUrl.contains(".mp4", ignoreCase = true) || cleanUrl.contains(".mkv", ignoreCase = true)
    if (isM3u8 || isMp4) {
        callback(
            ExtractorLink(
                source = "Direct",
                name = if (isM3u8) "HLS Stream" else "Direct Video",
                url = cleanUrl,
                referer = referer ?: "",
                quality = Qualities.Unknown.value,
                isM3u8 = isM3u8,
            ),
        )
        return true
    }

    // 3. Web embed inspection & unpacker
    try {
        val headers = mutableMapOf<String, String>()
        if (!referer.isNullOrBlank()) headers["Referer"] = referer
        val response = app.get(cleanUrl, headers = headers)
        val html = response.text
        val unpacked = JsUnpacker.unpack(html)
        val combined = "$html\n$unpacked"

        var emitted = false

        // StreamTape robotlink pattern
        val robotLinkRegex = Regex("""id=["']robotlink["'][^>]*>([^<]+)""")
        val robotMatch = robotLinkRegex.find(html)
        if (robotMatch != null) {
            val tapePath = robotMatch.groupValues[1].trim()
            val tapeUrl = if (tapePath.startsWith("//")) "https:$tapePath" else if (!tapePath.startsWith("http")) "https://$tapePath" else tapePath
            callback(
                ExtractorLink(
                    source = "StreamTape",
                    name = "StreamTape",
                    url = tapeUrl,
                    referer = cleanUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = tapeUrl.contains(".m3u8", ignoreCase = true),
                ),
            )
            emitted = true
        }

        // Generic m3u8 pattern
        val m3u8Regex = Regex("""https?://[^"'<>\s\\]+?\.m3u8[^"'<>\s\\]*""")
        val foundM3u8 = m3u8Regex.findAll(combined).map { it.value.replace(Regex("""\\/"""), "/") }.distinct().toList()
        for (stream in foundM3u8) {
            callback(
                ExtractorLink(
                    source = "Stream",
                    name = "Auto Quality (HLS)",
                    url = stream,
                    referer = cleanUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true,
                ),
            )
            emitted = true
        }

        // Generic mp4 pattern
        val mp4Regex = Regex("""https?://[^"'<>\s\\]+?\.mp4[^"'<>\s\\]*""")
        val foundMp4 = mp4Regex.findAll(combined).map { it.value.replace(Regex("""\\/"""), "/") }.distinct().toList()
        for (stream in foundMp4) {
            callback(
                ExtractorLink(
                    source = "Stream",
                    name = "MP4 Video",
                    url = stream,
                    referer = cleanUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false,
                ),
            )
            emitted = true
        }

        return emitted
    } catch (e: Throwable) {
        Log.w("Extractor", "Failed to resolve embed in loadExtractor for $url", e)
    }

    return false
}
