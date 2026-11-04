package com.lagradost.cloudstream3

@Suppress("UNUSED_PARAMETER")
enum class TvType(val value: Int? = null) {
    Movie(1),
    AnimeMovie(2),
    TvSeries(3),
    Cartoon(4),
    Anime(5),
    OVA(6),
    Torrent(7),
    Documentary(8),
    AsianDrama(9),
    Live(10),
    NSFW(11),
    Others(12),
    Music(13),
    AudioBook(14),

    /** Won't load the built in player, make your own interaction */
    CustomMedia(15),

    Audio(16),
    Podcast(17),
    Video(18),

    // Backward-compatible alias
    LiveStream(10);

    companion object {
        infix fun getEnum(value: Int): TvType? =
            values().firstOrNull { it.value == value }
    }
}

fun TvType.isMovieType(): Boolean {
    return when (this) {
        TvType.AnimeMovie,
        TvType.Live,
        TvType.Movie,
        TvType.Torrent,
        TvType.Video -> true

        else -> false
    }
}

fun TvType.isAudioType(): Boolean {
    return when (this) {
        TvType.Audio,
        TvType.AudioBook,
        TvType.Music,
        TvType.Podcast -> true

        else -> false
    }
}

fun TvType.isLiveStream(): Boolean {
    return this == TvType.Live || this == TvType.LiveStream
}

fun TvType.isAnimeOp(): Boolean {
    return this == TvType.Anime || this == TvType.OVA
}

@Suppress("UNUSED_PARAMETER")
enum class SearchQuality(val value: Int? = null) {
    Cam(1),
    CamRip(2),
    HdCam(3),
    Telesync(4), // TS
    WorkPrint(5),
    Telecine(6), // TC
    HQ(7),
    HD(8),
    HDR(9), // high dynamic range
    BlueRay(10),
    DVD(11),
    SD(12),
    FourK(13),
    UHD(14),
    SDR(15), // standard dynamic range
    WebRip(16),

    // Aliases to support older/variant plugins:
    Workprint(5),
    Sd(12),
    Hdtv(8),
    Dvd(11),
    Hd(8),
    FullHd(8),
    Uhd(14);
}

enum class DubStatus(val id: Int = -1) {
    None(-1),
    Dubbed(1),
    Subbed(0),

    // Aliases
    Dub(1),
    Sub(0),
    Both(2);
}

enum class ShowStatus {
    Completed,
    Ongoing,
    Upcoming,
    None,
}

enum class ProviderType {
    MetaProvider,
    DirectProvider,
}

enum class VPNStatus {
    None,
    MightBeNeeded,
    Torrent,
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

        fun from10(score: Float?): Score? = score?.let { Score(it.toDouble(), 10.0) }
        fun from10(score: Int?): Score? = score?.let { Score(it.toDouble(), 10.0) }
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
    var posterHeaders: Map<String, String>?
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
    override var posterHeaders: Map<String, String>? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var dubStatus: Set<String>? = null,
    var episodes: Map<String, Int>? = null,
    var sub: Int? = null,
    var dub: Int? = null,
    var otherName: String? = null,
) : SearchResponse

data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
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
    override var posterHeaders: Map<String, String>? = null,
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
    override var posterHeaders: Map<String, String>? = null,
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
    var posterHeaders: Map<String, String>?
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
    var contentRating: String?
    var comingSoon: Boolean
        get() = false
        set(value) {}

    companion object {
        fun addActorNames(response: LoadResponse, actors: List<String>?) {}
        fun addTMDbId(response: LoadResponse, tmdbId: String?) {}
        fun addImdbId(response: LoadResponse, imdbId: String?) {}
        fun addAniListId(response: LoadResponse, id: Int?) {}
        fun addMalId(response: LoadResponse, id: Int?) {}
        fun addActorsOnly(response: LoadResponse, actors: List<String>?) {}
        fun addActorsRole(response: LoadResponse, actors: List<Any>?) {}
        @Suppress("RedundantSuspendModifier")
        suspend fun addTrailer(
            response: LoadResponse,
            trailerUrl: String?,
            referer: String? = null,
            addRaw: Boolean = false,
        ) {}
        @Suppress("RedundantSuspendModifier")
        suspend fun addTrailer(
            response: LoadResponse,
            trailers: List<Any>?,
            referer: String? = null,
            addRaw: Boolean = false,
        ) {}
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

    constructor(
        data: String,
        name: String? = null,
        season: Int? = null,
        episode: Int? = null,
        posterUrl: String? = null,
        rating: Int? = null,
        description: String? = null,
        date: Long? = null,
    ) : this(
        data = data,
        name = name,
        season = season,
        episode = episode,
        posterUrl = posterUrl,
        rating = rating,
        description = description,
        date = date?.toString(),
    )
}

data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
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
    override var contentRating: String? = null,
    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    var japName: String? = null,
    var engName: String? = null,
    var synonyms: List<String>? = null,
    var author: String? = null,
    var status: String? = null,
    override var showStatus: ShowStatus? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var comingSoon: Boolean = false,
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
    override var posterHeaders: Map<String, String>? = null,
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
    override var contentRating: String? = null,
    var dataUrl: String = url,
    override var comingSoon: Boolean = false,
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
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
    override var contentRating: String? = null,
    var episodes: List<Episode> = emptyList(),
    override var showStatus: ShowStatus? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var comingSoon: Boolean = false,
) : LoadResponse, EpisodeResponse

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String = url,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
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
    override var contentRating: String? = null,
    override var comingSoon: Boolean = false,
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
) {
    val items: List<SearchResponse> get() = list
}
