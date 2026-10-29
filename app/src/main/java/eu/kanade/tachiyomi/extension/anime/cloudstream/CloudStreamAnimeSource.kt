package eu.kanade.tachiyomi.extension.anime.cloudstream

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import logcat.LogPriority
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

@Suppress("OVERRIDE_DEPRECATION")
class CloudStreamAnimeSource(
    val api: MainAPI,
    val pluginName: String = api.name,
    val pluginVersion: Int = 1,
) : AnimeHttpSource() {

    override val id: Long = abs(api.name.hashCode().toLong() xor 0x43534C00L)
    override val name: String = "${api.name} [CS]"
    override val lang: String = api.lang
    override val baseUrl: String = api.mainUrl
    override val supportsLatest: Boolean = api.hasMainPage

    override val client: OkHttpClient = api.app.baseClient

    override fun popularAnimeRequest(page: Int): Request {
        return Request.Builder().url(baseUrl).build()
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        throw UnsupportedOperationException("Using suspend getPopularAnime")
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val home = if (api.hasMainPage) {
            try {
                api.getMainPage(page, MainPageRequest("Home", baseUrl))
            } catch (e: Throwable) {
                null
            }
        } else null

        val list = home?.items?.flatMap { it.list } ?: try {
            api.search("") ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }

        val animes = list.map { it.toSAnime() }
        return AnimesPage(animes, hasNextPage = home?.hasNext ?: false)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = popularAnimeRequest(page)
    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val results = try {
            api.search(query) ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
        return AnimesPage(results.map { it.toSAnime() }, hasNextPage = false)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        throw UnsupportedOperationException("Using suspend getAnimeDetails")
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val details = try {
            api.load(anime.url)
        } catch (e: Throwable) {
            null
        } ?: return anime

        anime.title = details.name
        details.posterUrl?.let { anime.thumbnail_url = it }
        anime.description = details.plot
        anime.genre = details.tags?.joinToString(", ")

        if (details is AnimeLoadResponse) {
            details.author?.let { anime.author = it }
            anime.status = when (details.status?.lowercase()) {
                "ongoing", "releasing" -> SAnime.ONGOING
                "completed", "finished" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
        }
        anime.initialized = true
        return anime
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        throw UnsupportedOperationException("Using suspend getEpisodeList")
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val details = try {
            api.load(anime.url)
        } catch (e: Throwable) {
            null
        } ?: return emptyList()

        val episodes = mutableListOf<SEpisode>()

        when (details) {
            is AnimeLoadResponse -> {
                var order = 1f
                details.episodes.values.flatten().forEach { ep ->
                    episodes.add(ep.toSEpisode(order++))
                }
            }
            is TvSeriesLoadResponse -> {
                var order = 1f
                details.episodes.forEach { ep ->
                    episodes.add(ep.toSEpisode(order++))
                }
            }
            is MovieLoadResponse -> {
                val ep = SEpisode.create().apply {
                    name = "Full Movie"
                    url = details.dataUrl
                    episode_number = 1f
                }
                episodes.add(ep)
            }
            else -> {}
        }

        return episodes.reversed()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val videos = CopyOnWriteArrayList<Video>()
        val subtitles = CopyOnWriteArrayList<Track>()

        val onSubtitle: (SubtitleFile) -> Unit = { sub ->
            subtitles.add(Track(url = sub.url, lang = sub.lang))
        }

        val onLink: (ExtractorLink) -> Unit = { link ->
            val qualityName = "${link.name} - ${Qualities.getStringByInt(link.quality)}"
            val okhttpHeaders = link.headers.toMutableMap()
            if (link.referer.isNotEmpty()) {
                okhttpHeaders["Referer"] = link.referer
            }

            val video = Video(
                videoUrl = link.url,
                videoTitle = qualityName,
                headers = okhttpHeaders.toHeaders(),
                subtitleTracks = subtitles.toList(),
            )
            videos.add(video)
        }

        try {
            api.loadLinks(
                data = episode.url,
                isCasting = false,
                subtitleCallback = onSubtitle,
                callback = onLink,
            )
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load links for ${episode.name}" }
        }

        return videos.toList()
    }

    private fun SearchResponse.toSAnime(): SAnime {
        val sAnime = SAnime.create()
        sAnime.title = name
        sAnime.url = url
        posterUrl?.let { sAnime.thumbnail_url = it }
        return sAnime
    }

    private fun Episode.toSEpisode(index: Float): SEpisode {
        val sEpisode = SEpisode.create()
        sEpisode.name = this.name ?: "Episode ${this.episode ?: index.toInt()}"
        sEpisode.url = this.data
        sEpisode.episode_number = (this.episode ?: index.toInt()).toFloat()
        return sEpisode
    }
}
