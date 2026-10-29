package com.lagradost.cloudstream3

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    AnimeMovie,
    OVA,
    Cartoon,
    Documentary,
    LiveStream,
    NSFW,
    Others
}

interface SearchResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType?
    var posterUrl: String?
    var year: Int?
    var id: Int?
}

data class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    var dubStatus: Set<String>? = null,
    var episodes: Map<String, Int>? = null,
    var sub: Int? = null,
    var dub: Int? = null,
) : SearchResponse

data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    var episodes: Int? = null,
) : SearchResponse

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?
    var rating: Int?
    var tags: List<String>?
    var recommendations: List<SearchResponse>?
}

data class Episode(
    val data: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val description: String? = null,
    val date: String? = null,
)

data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var recommendations: List<SearchResponse>? = null,
    var episodes: MutableMap<String, List<Episode>> = mutableMapOf(),
    var japName: String? = null,
    var engName: String? = null,
    var synonyms: List<String>? = null,
    var author: String? = null,
    var status: String? = null,
) : LoadResponse

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var recommendations: List<SearchResponse>? = null,
    var dataUrl: String = url,
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var recommendations: List<SearchResponse>? = null,
    var episodes: List<Episode> = emptyList(),
) : LoadResponse

data class SubtitleFile(
    val lang: String,
    val url: String,
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)

data class SearchResponseList(
    val list: List<SearchResponse>,
)
