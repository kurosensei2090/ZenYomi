package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.Requests


abstract class MainAPI {
    open var mainUrl: String = ""
    open var name: String = ""
    open var supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)
    open var lang: String = "en"
    open var hasMainPage: Boolean = false
    open var hasQuickSearch: Boolean = false
    open var hasDownloadSupport: Boolean = true

    val app: Requests get() = com.lagradost.cloudstream3.app

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return null
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        return null
    }

    open suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }

    open suspend fun load(url: String): LoadResponse? {
        return null
    }

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        return false
    }

    fun fixUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$mainUrl$url"
            else -> "$mainUrl/$url"
        }
    }

    fun newAnimeSearchResponse(
        name: String,
        url: String,
        type: TvType = TvType.Anime,
        builder: AnimeSearchResponse.() -> Unit = {},
    ): AnimeSearchResponse {
        val res = AnimeSearchResponse(
            name = name,
            url = fixUrl(url),
            apiName = this.name,
            type = type,
        )
        builder(res)
        return res
    }

    fun newMovieSearchResponse(
        name: String,
        url: String,
        type: TvType = TvType.Movie,
        builder: MovieSearchResponse.() -> Unit = {},
    ): MovieSearchResponse {
        val res = MovieSearchResponse(
            name = name,
            url = fixUrl(url),
            apiName = this.name,
            type = type,
        )
        builder(res)
        return res
    }

    fun newTvSeriesSearchResponse(
        name: String,
        url: String,
        type: TvType = TvType.TvSeries,
        builder: TvSeriesSearchResponse.() -> Unit = {},
    ): TvSeriesSearchResponse {
        val res = TvSeriesSearchResponse(
            name = name,
            url = fixUrl(url),
            apiName = this.name,
            type = type,
        )
        builder(res)
        return res
    }

    fun newEpisode(
        data: String,
        builder: Episode.() -> Unit = {},
    ): Episode {
        val ep = Episode(data = fixUrl(data))
        return ep
    }
}
