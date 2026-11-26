package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import java.net.URI

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
            val list = mutableListOf<ExtractorLink>()
            list.add(
                ExtractorLink(
                    source = source,
                    name = "$name - Auto",
                    url = m3u8Url,
                    referer = referer,
                    quality = quality ?: Qualities.Unknown.value,
                    isM3u8 = true,
                    headers = headers,
                ),
            )
            try {
                val fullHeaders = headers.toMutableMap()
                if (referer.isNotBlank()) fullHeaders["Referer"] = referer
                if (!fullHeaders.containsKey("User-Agent")) fullHeaders["User-Agent"] = USER_AGENT
                val res = app.get(m3u8Url, headers = fullHeaders, timeout = 5)
                if (res.isSuccessful) {
                    val text = res.text
                    if (text.contains("#EXT-X-STREAM-INF")) {
                        var currentQuality: Int = Qualities.Unknown.value
                        var currentLabel: String? = null
                        for (line in text.lines()) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                                val resMatch = Regex("""RESOLUTION=(\d+x\d+)""").find(trimmed)
                                if (resMatch != null) {
                                    val h = resMatch.groupValues[1].substringAfter("x").toIntOrNull() ?: 0
                                    currentQuality = when {
                                        h >= 2160 -> Qualities.P2160.value
                                        h >= 1440 -> Qualities.P1440.value
                                        h >= 1080 -> Qualities.P1080.value
                                        h >= 720 -> Qualities.P720.value
                                        h >= 480 -> Qualities.P480.value
                                        h >= 360 -> Qualities.P360.value
                                        else -> Qualities.P240.value
                                    }
                                    currentLabel = "${h}p"
                                }
                            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                val resolvedUrl = try {
                                    URI(m3u8Url).resolve(trimmed).toString()
                                } catch (_: Exception) {
                                    if (trimmed.startsWith("http")) trimmed else "${m3u8Url.substringBeforeLast('/')}/$trimmed"
                                }
                                val qLabel = currentLabel ?: Qualities.getStringByInt(currentQuality)
                                list.add(
                                    ExtractorLink(
                                        source = source,
                                        name = "$name - $qLabel",
                                        url = resolvedUrl,
                                        referer = referer,
                                        quality = currentQuality,
                                        isM3u8 = true,
                                        headers = headers,
                                    ),
                                )
                                currentQuality = Qualities.Unknown.value
                                currentLabel = null
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
            return if (list.size > 1) list else listOf(
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
