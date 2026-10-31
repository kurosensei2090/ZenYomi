package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.Requests

abstract class MainAPI {
    open var mainUrl: String = ""
    open var name: String = ""
    open var supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)
    open var lang: String = "en"
    open var mainPage: List<MainPageData> = emptyList()
    open var hasMainPage: Boolean = false
        get() = field || mainPage.isNotEmpty()
    open var hasQuickSearch: Boolean = false
    open var hasDownloadSupport: Boolean = true

    open val app: Requests get() = com.lagradost.cloudstream3.app

    @UnsafeSSL
    open val insecureApp: Requests get() = com.lagradost.cloudstream3.insecureApp

    open fun fixUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$mainUrl$url"
            else -> "$mainUrl/$url"
        }
    }

    open fun fixUrlNull(url: String?): String? {
        if (url == null) return null
        return fixUrl(url)
    }

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

    fun newMovieLoadResponse(
        name: String,
        url: String,
        type: TvType = TvType.Movie,
        dataUrl: String = url,
        builder: MovieLoadResponse.() -> Unit = {},
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

    suspend fun newAnimeLoadResponse(
        name: String,
        url: String,
        type: TvType = TvType.Anime,
        comingSoonIfNone: Boolean = true,
        builder: suspend AnimeLoadResponse.() -> Unit = {},
    ): AnimeLoadResponse {
        val res = AnimeLoadResponse(
            name = name,
            url = fixUrl(url),
            apiName = this.name,
            type = type,
        )
        builder(res)
        return res
    }

    suspend fun newAnimeLoadResponse(
        name: String,
        url: String,
        type: TvType,
        builder: suspend AnimeLoadResponse.() -> Unit,
    ): AnimeLoadResponse = newAnimeLoadResponse(name, url, type, true, builder)

    suspend fun newAnimeLoadResponse(
        name: String,
        url: String,
        builder: suspend AnimeLoadResponse.() -> Unit,
    ): AnimeLoadResponse = newAnimeLoadResponse(name, url, TvType.Anime, true, builder)

    fun newTvSeriesLoadResponse(
        name: String,
        url: String,
        type: TvType = TvType.TvSeries,
        episodes: List<Episode> = emptyList(),
        builder: TvSeriesLoadResponse.() -> Unit = {},
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

    fun newHomePageResponse(
        name: String,
        items: List<SearchResponse>,
        hasNext: Boolean = false,
    ): HomePageResponse {
        return HomePageResponse(items = listOf(HomePageList(name = name, list = items)), hasNext = hasNext)
    }

    fun newHomePageResponse(
        data: MainPageRequest,
        list: List<SearchResponse>,
        hasNext: Boolean = false,
    ): HomePageResponse {
        return HomePageResponse(
            items = listOf(HomePageList(name = data.name, list = list, isHorizontalImages = data.horizontalImages)),
            hasNext = hasNext,
        )
    }

    fun newHomePageResponse(
        data: MainPageData,
        list: List<SearchResponse>,
        hasNext: Boolean = false,
    ): HomePageResponse {
        return HomePageResponse(
            items = listOf(HomePageList(name = data.name, list = list, isHorizontalImages = data.horizontalImages)),
            hasNext = hasNext,
        )
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

    fun newEpisode(
        data: String,
        builder: Episode.() -> Unit = {},
    ): Episode {
        val ep = Episode(data = fixUrl(data))
        builder(ep)
        return ep
    }

    fun newEpisode(
        data: Any?,
        builder: Episode.() -> Unit = {},
    ): Episode {
        val ep = Episode(data = fixUrl(data?.toString() ?: ""))
        builder(ep)
        return ep
    }
}
