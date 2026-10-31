package eu.kanade.tachiyomi.extension.anime.cloudstream

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val pluginName: String = runCatching { api.name }.getOrDefault("CS-Plugin"),
    val pluginVersion: Int = 1,
) : AnimeHttpSource() {

    override val id: Long = abs(runCatching { api.name }.getOrDefault(pluginName).hashCode().toLong() xor 0x43534C00L)
    override val name: String = "${runCatching { api.name }.getOrDefault(pluginName)} [CS]"
    override val lang: String = runCatching { api.lang }.getOrDefault("en")
    override val baseUrl: String = (runCatching { api.mainUrl }.getOrDefault("https://localhost")).ifBlank { "https://localhost" }
    override val supportsLatest: Boolean = runCatching { api.hasMainPage }.getOrDefault(false)

    override val client: OkHttpClient by lazy {
        try {
            api.app.baseClient
        } catch (_: Throwable) {
            network.client
        }
    }

    override fun popularAnimeRequest(page: Int): Request {
        val target = if (baseUrl.startsWith("http://", ignoreCase = true) || baseUrl.startsWith("https://", ignoreCase = true)) baseUrl else "https://$baseUrl"
        return Request.Builder().url(target).build()
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        throw UnsupportedOperationException("Using suspend getPopularAnime")
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val list = mutableListOf<SearchResponse>()
        var hasNext = false

        val mainPages = api.mainPage
        if (mainPages.isNotEmpty()) {
            val pagesToFetch = if (page == 1) mainPages.take(4) else mainPages.take(2)
            coroutineScope {
                val deferredList = pagesToFetch.map { mp ->
                    async {
                        try {
                            api.getMainPage(page, MainPageRequest(mp.name, mp.data, mp.horizontalImages))
                        } catch (e: Throwable) {
                            logcat(LogPriority.WARN, e) { "Failed to load main page row ${mp.name} for ${api.name}" }
                            null
                        }
                    }
                }
                deferredList.awaitAll().forEach { resp ->
                    resp?.items?.forEach { hpList ->
                        list.addAll(hpList.list)
                    }
                    if (resp?.hasNext == true) {
                        hasNext = true
                    }
                }
            }
        } else if (api.hasMainPage) {
            try {
                val resp = api.getMainPage(page, MainPageRequest("Home", baseUrl))
                resp?.items?.forEach { hpList ->
                    list.addAll(hpList.list)
                }
                hasNext = resp?.hasNext ?: false
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to load Home main page for ${api.name}" }
            }
        }

        if (list.isEmpty()) {
            val fallbackQueries = listOf("", "a")
            for (q in fallbackQueries) {
                try {
                    val searchResults = api.search(q) ?: api.quickSearch(q)
                    if (!searchResults.isNullOrEmpty()) {
                        list.addAll(searchResults)
                        break
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.DEBUG, e) { "Fallback search '$q' failed for ${api.name}" }
                }
            }
        }

        val animes = list.distinctBy { it.url }.map { it.toSAnime() }
        return AnimesPage(animes, hasNextPage = hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val list = mutableListOf<SearchResponse>()
        var hasNext = false

        val mainPages = api.mainPage
        val latestRow = mainPages.firstOrNull {
            it.name.contains("latest", ignoreCase = true) ||
                it.name.contains("أحدث", ignoreCase = true) ||
                it.name.contains("جديد", ignoreCase = true) ||
                it.name.contains("recent", ignoreCase = true) ||
                it.data.contains("latest", ignoreCase = true) ||
                it.data.contains("recent", ignoreCase = true)
        }
        if (latestRow != null) {
            try {
                val resp = api.getMainPage(page, MainPageRequest(latestRow.name, latestRow.data, latestRow.horizontalImages))
                resp?.items?.forEach { hpList ->
                    list.addAll(hpList.list)
                }
                hasNext = resp?.hasNext ?: false
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to load latest row for ${api.name}" }
            }
        }
        if (list.isEmpty()) {
            return getPopularAnime(page)
        }
        val animes = list.distinctBy { it.url }.map { it.toSAnime() }
        return AnimesPage(animes, hasNextPage = hasNext)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = popularAnimeRequest(page)
    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isBlank()) {
            return getPopularAnime(page)
        }
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
            logcat(LogPriority.ERROR, e) { "Failed to load anime details for ${anime.title} from ${anime.url}" }
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
                else -> when (details.showStatus) {
                    ShowStatus.Ongoing -> SAnime.ONGOING
                    ShowStatus.Completed -> SAnime.COMPLETED
                    else -> SAnime.UNKNOWN
                }
            }
        } else if (details is EpisodeResponse) {
            anime.status = when (details.showStatus) {
                ShowStatus.Ongoing -> SAnime.ONGOING
                ShowStatus.Completed -> SAnime.COMPLETED
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
            logcat(LogPriority.ERROR, e) { "Failed to load episodes for ${anime.title} from ${anime.url}" }
            null
        } ?: return emptyList()

        val episodes = mutableListOf<SEpisode>()

        when (details) {
            is AnimeLoadResponse -> {
                var order = 1f
                val hasMultipleStatuses = details.episodes.size > 1
                for ((status, epList) in details.episodes) {
                    val statusSuffix = if (hasMultipleStatuses) " (${status.name})" else ""
                    epList.forEach { ep ->
                        val baseName = ep.name ?: "Episode ${ep.episode ?: order.toInt()}"
                        val sEpisode = ep.toSEpisode(order++)
                        if (hasMultipleStatuses && !baseName.contains(status.name, ignoreCase = true)) {
                            sEpisode.name = "$baseName$statusSuffix"
                        }
                        episodes.add(sEpisode)
                    }
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
            else -> {
                try {
                    for (m in details.javaClass.methods) {
                        if (m.name.equals("getEpisodes", ignoreCase = true) && m.parameterTypes.isEmpty()) {
                            val result = m.invoke(details)
                            if (result is Map<*, *>) {
                                var order = 1f
                                result.values.filterIsInstance<List<*>>().flatten().filterIsInstance<Episode>().forEach { ep ->
                                    episodes.add(ep.toSEpisode(order++))
                                }
                            } else if (result is List<*>) {
                                var order = 1f
                                result.filterIsInstance<Episode>().forEach { ep ->
                                    episodes.add(ep.toSEpisode(order++))
                                }
                            }
                            break
                        }
                    }
                } catch (t: Throwable) {
                    logcat(LogPriority.WARN, t) { "Reflection fallback failed for ${details.javaClass.name}" }
                }
            }
        }

        logcat(LogPriority.INFO) { "Loaded ${episodes.size} episodes for ${anime.title} from ${api.name}" }
        return episodes.reversed()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val videos = CopyOnWriteArrayList<Video>()
        val subtitles = CopyOnWriteArrayList<Track>()

        val onSubtitle: (SubtitleFile) -> Unit = { sub ->
            subtitles.add(Track(url = sub.url, lang = sub.lang))
        }

        val onLink: (ExtractorLink) -> Unit = { link ->
            if (link.url.isNotBlank()) {
                val qualityName = "${link.name} - ${Qualities.getStringByInt(link.quality)}"
                val okhttpHeaders = link.headers.toMutableMap()
                if (link.referer.isNotEmpty()) {
                    okhttpHeaders["Referer"] = link.referer
                }
                if (!okhttpHeaders.containsKey("User-Agent")) {
                    okhttpHeaders["User-Agent"] = com.lagradost.cloudstream3.USER_AGENT
                }

                val video = Video(
                    videoUrl = link.url,
                    videoTitle = qualityName,
                    headers = okhttpHeaders.toHeaders(),
                    subtitleTracks = subtitles.toList(),
                    initialized = true,
                )
                videos.add(video)
            }
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

        if (videos.isEmpty() && episode.url.isNotBlank()) {
            try {
                com.lagradost.cloudstream3.utils.loadExtractor(
                    url = episode.url,
                    referer = baseUrl,
                    subtitleCallback = onSubtitle,
                    callback = onLink,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "Direct loadExtractor fallback failed for ${episode.name}" }
            }
        }

        logcat(LogPriority.INFO) { "Loaded ${videos.size} videos for ${episode.name} from ${api.name}" }
        return videos.toList()
    }

    private fun SearchResponse.toSAnime(): SAnime {
        val sAnime = SAnime.create()
        sAnime.title = name
        sAnime.url = api.fixUrl(url)
        posterUrl?.let { sAnime.thumbnail_url = api.fixUrl(it) }
        return sAnime
    }

    private fun Episode.toSEpisode(index: Float): SEpisode {
        val sEpisode = SEpisode.create()
        sEpisode.name = this.name ?: "Episode ${this.episode ?: index.toInt()}"
        sEpisode.url = api.fixUrl(this.data)
        sEpisode.episode_number = (this.episode ?: index.toInt()).toFloat()
        return sEpisode
    }
}
