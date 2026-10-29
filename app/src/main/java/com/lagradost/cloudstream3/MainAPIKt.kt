@file:JvmName("MainAPIKt")
package com.lagradost.cloudstream3

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

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url == null) return null
    return fixUrl(url)
}

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
