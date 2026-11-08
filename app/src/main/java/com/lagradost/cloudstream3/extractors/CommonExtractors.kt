package com.lagradost.cloudstream3.extractors

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities

open class Filesim : ExtractorApi() {
    override var name = "Filesim"
    override var mainUrl = "files.im"
}

open class GMPlayer : ExtractorApi() {
    override var name = "GMPlayer"
    override var mainUrl = "gmplayer"
}

open class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "jeniusplay"
}

open class PixelDrain : ExtractorApi() {
    override var name = "PixelDrain"
    override var mainUrl = "pixeldrain.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        val id = Regex("""(?:/u/|/api/file/)([a-zA-Z0-9_-]+)""").find(url)?.groupValues?.get(1) ?: return null
        val directUrl = "https://pixeldrain.com/api/file/$id?download"
        val link = ExtractorLink(
            source = name,
            name = "$name - 1080p",
            url = directUrl,
            referer = "https://pixeldrain.com/",
            quality = Qualities.P1080.value,
            isM3u8 = false,
        )
        callback(link)
        return listOf(link)
    }
}

open class Uqload : ExtractorApi() {
    override var name = "Uqload"
    override var mainUrl = "uqload"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val headers = mapOf("Referer" to (referer ?: "https://uqload.com/"))
            val response = app.get(url, headers = headers).text
            val videoUrl = Regex("""sources:\s*\[\s*["'](https?://[^"']+)["']""").find(response)?.groupValues?.get(1)
                ?: Regex("""file:\s*["'](https?://[^"']+)["']""").find(response)?.groupValues?.get(1)
                ?: return null
            val isM3u8 = videoUrl.contains(".m3u8", ignoreCase = true)
            val link = ExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = isM3u8,
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class StreamWish : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "streamwish"
    override val mainUrls = listOf(
        "streamwish", "wishfast", "embedwish", "strwish", "flaswish",
        "awish", "dwish", "mwish", "ajmid", "sfastwish", "wish",
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val headers = mutableMapOf<String, String>()
            if (!referer.isNullOrBlank()) headers["Referer"] = referer
            val response = app.get(url, headers = headers).text
            val unpacked = JsUnpacker.unpack(response)
            val combined = "$response\n$unpacked"

            val m3u8Regex = Regex("""https?://[^"'<>\s\\]+?\.m3u8[^"'<>\s\\]*""")
            val streams = m3u8Regex.findAll(combined).map { it.value.replace(Regex("""\\/"""), "/") }.distinct().toList()
            if (streams.isEmpty()) return null

            val list = mutableListOf<ExtractorLink>()
            for (stream in streams) {
                val link = ExtractorLink(
                    source = name,
                    name = "$name (HLS)",
                    url = stream,
                    referer = url,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true,
                )
                callback(link)
                list.add(link)
            }
            list
        } catch (_: Throwable) {
            null
        }
    }
}

open class VidHide : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "vidhide"
    override val mainUrls = listOf(
        "vidhide", "streamhide", "ahvsh", "kinokz", "vidhidepro", "vidhidepre",
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return StreamWish().getUrl(url, referer, subtitleCallback, callback)
    }
}

open class FileLions : ExtractorApi() {
    override var name = "FileLions"
    override var mainUrl = "filelions"
    override val mainUrls = listOf(
        "filelions", "fdewsdc", "alions", "azipcdn",
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return StreamWish().getUrl(url, referer, subtitleCallback, callback)
    }
}

open class StreamSB : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "streamsb"
}

open class Voe : ExtractorApi() {
    override var name = "Voe"
    override var mainUrl = "voe.sx"
    override val mainUrls = listOf("voe.sx", "voe", "repackrodeo", "audaciousdefaulthouse")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val response = app.get(url).text
            val hlsUrl = Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(response)?.groupValues?.get(1)
                ?: Regex("""prompt\(\s*['"]node['"]\s*,\s*['"]([^'"]+)['"]""").find(response)?.groupValues?.get(1)
                ?: return null
            val decodedUrl = if (hlsUrl.startsWith("http")) hlsUrl else try {
                String(Base64.decode(hlsUrl, Base64.DEFAULT))
            } catch (_: Throwable) {
                hlsUrl
            }
            val link = ExtractorLink(
                source = name,
                name = "$name (HLS)",
                url = decodedUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = true,
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class Mp4Upload : ExtractorApi() {
    override var name = "Mp4Upload"
    override var mainUrl = "mp4upload.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val response = app.get(url).text
            val videoUrl = Regex("""player\.src\(\s*\{\s*type:\s*["'][^"']+["'],\s*src:\s*["'](https?://[^"']+)["']""").find(response)?.groupValues?.get(1)
                ?: Regex("""src:\s*["'](https?://[^"']+\.mp4[^"']*)["']""").find(response)?.groupValues?.get(1)
                ?: return null
            val link = ExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = false,
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "dood"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val response = app.get(url).text
            val passMd5 = Regex("""/pass_md5/([^'"]+)""").find(response)?.groupValues?.get(1) ?: return null
            val token = passMd5.substringAfterLast("/")
            val domain = Regex("""https?://([^/]+)""").find(url)?.groupValues?.get(0) ?: "https://dood.to"
            val md5Url = "$domain/pass_md5/$passMd5"
            val trueUrl = app.get(md5Url, headers = mapOf("Referer" to url)).text
            val randomString = (1..10).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
            val videoUrl = "$trueUrl$randomString?token=$token&expiry=${System.currentTimeMillis()}"
            val link = ExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                referer = domain,
                quality = Qualities.Unknown.value,
                isM3u8 = false,
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class StreamTape : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "streamtape"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val response = app.get(url).text
            val match = Regex("""id=["']robotlink["'][^>]*>([^<]+)""").find(response)
                ?: Regex("""["'](https?://[^"']*tapecontent\.net/[^"']*)["']""").find(response)
                ?: return null
            val tapePath = match.groupValues[1].trim()
            val tapeUrl = if (tapePath.startsWith("//")) "https:$tapePath" else if (!tapePath.startsWith("http")) "https://$tapePath" else tapePath
            val link = ExtractorLink(
                source = name,
                name = name,
                url = tapeUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = tapeUrl.contains(".m3u8", ignoreCase = true),
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class OkRu : ExtractorApi() {
    override var name = "OkRu"
    override var mainUrl = "ok.ru"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return try {
            val response = app.get(url).text
            val m3u8 = Regex("""data-options=["']([^"']+)["']""").find(response)?.groupValues?.get(1)
                ?.replace("&quot;", "\"")
                ?: response
            val videoUrl = Regex("""https?://[^"'<>\s\\]+?\.m3u8[^"'<>\s\\]*""").find(m3u8)?.value
                ?: Regex("""https?://[^"'<>\s\\]+?\.mp4[^"'<>\s\\]*""").find(m3u8)?.value
                ?: return null
            val link = ExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = videoUrl.contains(".m3u8", ignoreCase = true),
            )
            callback(link)
            listOf(link)
        } catch (_: Throwable) {
            null
        }
    }
}

open class MegaMax : ExtractorApi() {
    override var name = "MegaMax"
    override var mainUrl = "megamax"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return StreamWish().getUrl(url, referer, subtitleCallback, callback)
    }
}

open class VidTube : ExtractorApi() {
    override var name = "VidTube"
    override var mainUrl = "vidtube"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return StreamWish().getUrl(url, referer, subtitleCallback, callback)
    }
}

open class AbyssPlayer : ExtractorApi() {
    override var name = "Abyss"
    override var mainUrl = "abyssplayer"
    override val mainUrls = listOf("abyssplayer", "abysscdn", "abyss.to")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        return StreamWish().getUrl(url, referer, subtitleCallback, callback)
    }
}

open class SaveFiles : ExtractorApi() {
    override var name = "SaveFiles"
    override var mainUrl = "savefiles"
    override val mainUrls = listOf("savefiles.com", "savefiles.org", "savefiles")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): List<ExtractorLink>? {
        val embedUrl = url.trim().trimEnd('/')
        if (embedUrl.isBlank()) return null

        var fileCode = embedUrl.substringAfterLast("/").substringBefore('?').substringBefore('#')
        if (fileCode.contains("-")) fileCode = fileCode.substringAfterLast('-')
        fileCode = fileCode.substringBefore('.').trim()
        if (fileCode.isBlank()) return null

        val host = if (embedUrl.contains("://")) {
            embedUrl.substringAfter("://").substringBefore("/")
        } else {
            "savefiles.com"
        }
        val origin = "https://$host"

        val dlText = try {
            app.post(
                "$origin/dl",
                data = mapOf(
                    "op" to "embed",
                    "file_code" to fileCode,
                    "auto" to "0",
                    "referer" to "",
                ),
                headers = mapOf(
                    "User-Agent" to com.lagradost.cloudstream3.USER_AGENT,
                    "Referer" to "$origin/",
                    "Origin" to origin,
                ),
                referer = "$origin/",
            ).text
        } catch (_: Exception) {
            return null
        }
        if (dlText.isBlank()) return null

        val streamUrl = Regex("""sources:\s*\[(?:\{\s*file\s*:)?\s*['"]([^'"]+)['"]""")
            .find(dlText)?.groupValues?.get(1)
            ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""")
                .find(dlText)?.groupValues?.get(1)
            ?: Regex("""file:\s*["']([^"']+\.mp4[^"']*)["']""")
                .find(dlText)?.groupValues?.get(1)
        if (streamUrl.isNullOrBlank()) return null

        val link = ExtractorLink(
            source = name,
            name = name,
            url = streamUrl,
            referer = "$origin/",
            quality = Qualities.Unknown.value,
            isM3u8 = streamUrl.contains(".m3u8"),
        )
        callback(link)
        return listOf(link)
    }
}

open class Gdriveplayer : ExtractorApi() {
    override var name = "Gdriveplayer"
    override var mainUrl = "gdriveplayer.to"
}

fun registerCommonExtractors() {
    val common = listOf(
        PixelDrain(),
        Uqload(),
        StreamWish(),
        FileLions(),
        VidHide(),
        Mp4Upload(),
        Voe(),
        DoodLaExtractor(),
        StreamTape(),
        OkRu(),
        MegaMax(),
        VidTube(),
        AbyssPlayer(),
        SaveFiles(),
        Filesim(),
        GMPlayer(),
        Jeniusplay(),
        Gdriveplayer(),
    )
    for (extractor in common) {
        if (com.lagradost.cloudstream3.utils.extractorApis.none {
                it.name.equals(extractor.name, ignoreCase = true) ||
                (it.mainUrl.isNotBlank() && it.mainUrl.equals(extractor.mainUrl, ignoreCase = true))
            }
        ) {
            com.lagradost.cloudstream3.utils.extractorApis.add(extractor)
        }
    }
}
