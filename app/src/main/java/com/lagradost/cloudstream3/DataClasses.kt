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
    Others,
}

enum class SearchQuality {
    Cam,
    CamRip,
    HdCam,
    Telesync,
    Telecine,
    Workprint,
    Sd,
    Hdtv,
    Dvd,
    Hd,
    FullHd,
    Uhd,
    FourK,
    BlueRay,
}

enum class DubStatus {
    Dubbed,
    Subbed,
    Dub,
    Sub,
    Both,
    None,
}

enum class ShowStatus {
    Completed,
    Ongoing,
    Upcoming,
    None,
}

data class Score(
    val score: Double = 0.0,
    val maxScore: Double = 10.0,
) {
    companion object {
        fun from10(score: String?): Score? {
            val d = score?.toDoubleOrNull() ?: return null
            return Score(d, 10.0)
        }

        fun from10(score: Double?): Score? {
            val d = score ?: return null
            return Score(d, 10.0)
        }
    }
}

data class Actor(
    val name: String = "",
    val image: String? = null,
)

enum class ActorRole {
    Main,
    Supporting,
    Background,
}

data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)

interface SearchResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType?
    var posterUrl: String?
    var year: Int?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}

data class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
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
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var episodes: Int? = null,
) : SearchResponse

data class LiveSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.LiveStream,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var lang: String? = null,
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
    var score: Score?
    var actors: List<ActorData>?
    var backgroundPosterUrl: String?
    var duration: Int?
    var logoUrl: String?

    companion object {
        fun addTMDbId(response: LoadResponse, tmdbId: String?) {}
    }
}

data class NextAiring(
    val episode: Int,
    val unixTime: Long,
)

data class SeasonData(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null,
)

interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
    fun getLatestEpisodes(): Map<DubStatus, Int?> = emptyMap()
    fun getTotalEpisodeIndex(episode: Int, season: Int): Int? = episode
}

data class Episode(
    var data: String = "",
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var date: String? = null,
    var score: Score? = null,
    var runTime: Int? = null,
) {
    var url: String
        get() = data
        set(value) { data = value }

    constructor(data: String) : this(data = data, name = null)
}

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
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var duration: Int? = null,
    override var logoUrl: String? = null,
    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    var japName: String? = null,
    var engName: String? = null,
    var synonyms: List<String>? = null,
    var author: String? = null,
    var status: String? = null,
    override var showStatus: ShowStatus? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
) : LoadResponse, EpisodeResponse {
    fun addEpisodes(status: DubStatus, episodes: List<Episode>) {
        val list = this.episodes.getOrPut(status) { mutableListOf() }.toMutableList()
        list.addAll(episodes)
        this.episodes[status] = list
    }
}

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
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var duration: Int? = null,
    override var logoUrl: String? = null,
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
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var duration: Int? = null,
    override var logoUrl: String? = null,
    var episodes: List<Episode> = emptyList(),
    override var showStatus: ShowStatus? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
) : LoadResponse, EpisodeResponse

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String = url,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var type: TvType = TvType.LiveStream,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var rating: Int? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
) : LoadResponse

data class SubtitleFile(
    val lang: String,
    val url: String,
)

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
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
    val hasNext: Boolean = false,
)
