@file:JvmName("MainAPIKt")
package com.lagradost.cloudstream3

import android.util.Base64
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.serialization.json.Json

val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

val mapper: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .build()

fun MainAPI.fixUrl(url: String): String {
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$mainUrl$url"
        else -> "$mainUrl/$url"
    }
}

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url == null) return null
    return fixUrl(url)
}

fun AnimeSearchResponse.addDub(episodes: Int?) {
    this.dub = episodes
}

fun AnimeSearchResponse.addSub(episodes: Int?) {
    this.sub = episodes
}

fun AnimeSearchResponse.addDubStatus(
    isDub: Boolean,
    isSub: Boolean,
    dubEpisodes: Int? = null,
    subEpisodes: Int? = null,
) {
    val set = mutableSetOf<String>()
    if (isDub) set.add("dub")
    if (isSub) set.add("sub")
    this.dubStatus = set
    if (dubEpisodes != null) this.dub = dubEpisodes
    if (subEpisodes != null) this.sub = subEpisodes
}

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>) {
    val key = status.name.lowercase()
    val list = this.episodes.getOrPut(key) { mutableListOf() }.toMutableList()
    list.addAll(episodes)
    this.episodes[key] = list
}

fun base64Decode(str: String): String {
    return try {
        String(Base64.decode(str, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Throwable) {
        str
    }
}

fun base64DecodeArray(str: String): ByteArray {
    return try {
        Base64.decode(str, Base64.DEFAULT)
    } catch (_: Throwable) {
        ByteArray(0)
    }
}

fun base64Encode(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun fixTitle(title: String): String = title.trim()

@JvmOverloads
fun Episode.addDate(date: String?, format: String? = null) {
    this.date = date
}

fun getQualityFromString(string: String?): SearchQuality? {
    val s = string?.lowercase() ?: return null
    return when {
        "4k" in s || "2160" in s -> SearchQuality.FourK
        "1080" in s || "fhd" in s -> SearchQuality.FullHd
        "720" in s || "hd" in s -> SearchQuality.Hd
        "480" in s || "sd" in s -> SearchQuality.Sd
        "cam" in s -> SearchQuality.Cam
        "dvd" in s -> SearchQuality.Dvd
        "bluray" in s || "blue-ray" in s -> SearchQuality.BlueRay
        else -> SearchQuality.Hd
    }
}

fun mainPageOf(vararg pairs: Pair<String, String>): List<MainPageData> {
    return pairs.map { MainPageData(name = it.second, data = it.first) }
}

fun MainAPI.newEpisode(data: Any?, builder: Episode.() -> Unit = {}): Episode {
    val ep = Episode(data = data?.toString() ?: "")
    builder(ep)
    return ep
}

fun newHomePageResponse(
    name: String,
    items: List<SearchResponse>,
    hasNext: Boolean = false,
): HomePageResponse {
    return HomePageResponse(items = listOf(HomePageList(name = name, list = items)), hasNext = hasNext)
}

fun newHomePageResponse(
    items: List<HomePageList>,
    hasNext: Boolean = false,
): HomePageResponse {
    return HomePageResponse(items = items, hasNext = hasNext)
}

fun newHomePageResponse(
    item: HomePageList,
    hasNext: Boolean = false,
): HomePageResponse {
    return HomePageResponse(items = listOf(item), hasNext = hasNext)
}

suspend fun newSubtitleFile(
    lang: String,
    url: String,
    builder: (suspend SubtitleFile.() -> Unit)? = null,
): SubtitleFile {
    val sub = SubtitleFile(lang = lang, url = url)
    builder?.invoke(sub)
    return sub
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    builder: suspend MovieLoadResponse.() -> Unit = {},
): MovieLoadResponse {
    val res = MovieLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        dataUrl = fixUrl(dataUrl),
    )
    builder(res)
    return res
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    builder: suspend TvSeriesLoadResponse.() -> Unit = {},
): TvSeriesLoadResponse {
    val res = TvSeriesLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        episodes = episodes,
    )
    builder(res)
    return res
}

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = false,
    builder: MovieSearchResponse.() -> Unit = {},
): MovieSearchResponse {
    val res = MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder(res)
    return res
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = false,
    builder: AnimeSearchResponse.() -> Unit = {},
): AnimeSearchResponse {
    val res = AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder(res)
    return res
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = false,
    builder: TvSeriesSearchResponse.() -> Unit = {},
): TvSeriesSearchResponse {
    val res = TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder(res)
    return res
}

fun List<SearchResponse>.toNewSearchResponseList(hasNext: Boolean = false): SearchResponseList {
    return SearchResponseList(this)
}
