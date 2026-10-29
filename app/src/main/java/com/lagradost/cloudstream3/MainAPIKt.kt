package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

val mapper: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .build()

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
