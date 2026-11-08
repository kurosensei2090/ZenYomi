package eu.kanade.tachiyomi.extension.anime.cloudstream

import android.app.Activity
import android.content.Context
import android.webkit.CookieManager
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
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
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import logcat.LogPriority
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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
    var iconUrl: String? = null,
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

    private fun bindActivityContext(targetApi: MainAPI = api) {
        val activity = com.lagradost.cloudstream3.CommonActivity.activity ?: return
        try {
            var clazz: Class<*>? = targetApi.javaClass
            while (clazz != null && clazz != Any::class.java) {
                for (field in clazz.declaredFields) {
                    if (Context::class.java.isAssignableFrom(field.type)) {
                        field.isAccessible = true
                        val current = field.get(targetApi)
                        if (current !is Activity) {
                            field.set(targetApi, activity)
                        }
                    }
                }
                clazz = clazz.superclass
            }
        } catch (t: Throwable) {
            logcat(LogPriority.DEBUG, t) { "Failed to bind activity context to ${targetApi.name}" }
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
        bindActivityContext()
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
        bindActivityContext()
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
        bindActivityContext()
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
        val details = loadDetailsSafely(anime.url) ?: return anime

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
        val details = loadDetailsSafely(anime.url) ?: return emptyList()

        val episodes = mutableListOf<SEpisode>()

        when (details) {
            is AnimeLoadResponse -> {
                var order = 1f
                val hasMultipleStatuses = details.episodes.size > 1
                for ((status, epList) in details.episodes) {
                    val statusSuffix = if (hasMultipleStatuses) " (${status?.name ?: status.toString()})" else ""
                    epList?.forEach { ep ->
                        if (ep != null) {
                            val baseName = ep.name ?: "Episode ${ep.episode ?: order.toInt()}"
                            val sEpisode = ep.toSEpisode(order++)
                            if (hasMultipleStatuses && !baseName.contains(status?.name ?: "", ignoreCase = true)) {
                                sEpisode.name = "$baseName$statusSuffix"
                            }
                            episodes.add(sEpisode)
                        }
                    }
                }
            }
            is TvSeriesLoadResponse -> {
                var order = 1f
                details.episodes?.forEach { ep ->
                    if (ep != null) {
                        episodes.add(ep.toSEpisode(order++))
                    }
                }
            }
            is MovieLoadResponse -> {
                val ep = SEpisode.create().apply {
                    name = "Full Movie"
                    url = details.dataUrl.ifBlank { details.url }
                    episode_number = 1f
                }
                episodes.add(ep)
            }
            else -> {}
        }

        if (episodes.isEmpty()) {
            try {
                for (m in details.javaClass.methods) {
                    if (m.name.equals("getEpisodes", ignoreCase = true) && m.parameterTypes.isEmpty()) {
                        val result = m.invoke(details)
                        if (result is Map<*, *>) {
                            var order = 1f
                            result.values.filterIsInstance<Collection<*>>().flatten().forEach { item ->
                                toSEpisodeFromAny(item, order++)?.let { episodes.add(it) }
                            }
                        } else if (result is Collection<*>) {
                            var order = 1f
                            result.forEach { item ->
                                toSEpisodeFromAny(item, order++)?.let { episodes.add(it) }
                            }
                        }
                        if (episodes.isNotEmpty()) break
                    }
                }

                if (episodes.isEmpty()) {
                    var c: Class<*>? = details.javaClass
                    var f: java.lang.reflect.Field? = null
                    while (c != null && c != Any::class.java) {
                        try {
                            f = c.getDeclaredField("episodes")
                            break
                        } catch (_: NoSuchFieldException) {
                            c = c.superclass
                        }
                    }
                    f?.isAccessible = true
                    val result = f?.get(details)
                    if (result is Map<*, *>) {
                        var order = 1f
                        result.values.filterIsInstance<Collection<*>>().flatten().forEach { item ->
                            toSEpisodeFromAny(item, order++)?.let { episodes.add(it) }
                        }
                    } else if (result is Collection<*>) {
                        var order = 1f
                        result.forEach { item ->
                            toSEpisodeFromAny(item, order++)?.let { episodes.add(it) }
                        }
                    }
                }

                if (episodes.isEmpty()) {
                    val dataUrl = runCatching {
                        details.javaClass.methods.firstOrNull { it.name.equals("getDataUrl", ignoreCase = true) }?.invoke(details) as? String
                    }.getOrNull() ?: details.url
                    if (dataUrl.isNotBlank()) {
                        val ep = SEpisode.create().apply {
                            name = "Full Movie / Stream"
                            url = dataUrl
                            episode_number = 1f
                        }
                        episodes.add(ep)
                    }
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Reflection fallback failed for ${details.javaClass.name}" }
            }
        }

        logcat(LogPriority.INFO) { "Loaded ${episodes.size} episodes for ${anime.title} from ${api.name}" }
        return episodes.reversed()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        bindActivityContext()
        try {
            CookieManager.getInstance()?.flush()
        } catch (_: Exception) {}

        val videos = CopyOnWriteArrayList<Video>()
        val subtitles = CopyOnWriteArrayList<Track>()

        val onSubtitle: (SubtitleFile) -> Unit = { sub ->
            subtitles.add(Track(url = sub.url, lang = sub.lang))
        }

        val onLink: (ExtractorLink) -> Unit = { link ->
            if (link.url.isNotBlank()) {
                val qualityStr = Qualities.getStringByInt(link.quality)
                val cleanQuality = if (qualityStr.equals("Unknown", ignoreCase = true)) "" else qualityStr.trim()
                val serverName = when {
                    link.name.isNotBlank() && !link.name.equals(cleanQuality, ignoreCase = true) -> link.name.trim()
                    link.source.isNotBlank() -> link.source.trim()
                    else -> "Server"
                }
                val qualityName = if (cleanQuality.isNotBlank() && !serverName.contains(cleanQuality, ignoreCase = true)) {
                    "$serverName - $cleanQuality"
                } else if (serverName.contains(" - ") || serverName.startsWith("[")) {
                    serverName
                } else {
                    "$serverName - Default"
                }

                val okhttpHeaders = link.headers.toMutableMap()
                if (link.referer.isNotEmpty()) {
                    okhttpHeaders["Referer"] = link.referer
                }
                if (!okhttpHeaders.containsKey("User-Agent")) {
                    okhttpHeaders["User-Agent"] = com.lagradost.cloudstream3.USER_AGENT
                }
                val cm = CookieManager.getInstance()
                val cookieForUrl = try {
                    cm?.getCookie(link.url) ?: if (link.referer.isNotEmpty()) cm?.getCookie(link.referer) else null
                } catch (_: Exception) {
                    null
                }
                if (!cookieForUrl.isNullOrBlank() && !okhttpHeaders.containsKey("Cookie")) {
                    okhttpHeaders["Cookie"] = cookieForUrl
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
            withTimeoutOrNull(30_000L) {
                api.loadLinks(
                    data = episode.url,
                    isCasting = false,
                    subtitleCallback = onSubtitle,
                    callback = onLink,
                )
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load links for ${episode.name}" }
        }

        val isDirectMedia: (String) -> Boolean = { u ->
            val clean = u.lowercase().substringBefore("?")
            clean.endsWith(".m3u8") || clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".ts")
        }

        // Check if any video was added as an embed URL that needs second-pass extraction
        val embedsToExtract = videos.filter { v ->
            !isDirectMedia(v.videoUrl) && (
                v.videoUrl.contains("pixeldrain.com/u/", ignoreCase = true) ||
                v.videoUrl.contains("uqload", ignoreCase = true) ||
                v.videoUrl.contains("streamwish", ignoreCase = true) ||
                v.videoUrl.contains("wish", ignoreCase = true) ||
                v.videoUrl.contains("vidhide", ignoreCase = true) ||
                v.videoUrl.contains("streamhide", ignoreCase = true) ||
                v.videoUrl.contains("ahvsh", ignoreCase = true) ||
                v.videoUrl.contains("filelions", ignoreCase = true) ||
                v.videoUrl.contains("alions", ignoreCase = true) ||
                v.videoUrl.contains("voe.sx", ignoreCase = true) ||
                v.videoUrl.contains("mp4upload", ignoreCase = true) ||
                v.videoUrl.contains("dood", ignoreCase = true) ||
                v.videoUrl.contains("megamax", ignoreCase = true) ||
                v.videoUrl.contains("savefiles", ignoreCase = true) ||
                v.videoUrl.contains("abyss", ignoreCase = true) ||
                v.videoUrl.contains("vidtube", ignoreCase = true) ||
                v.videoUrl.contains("playmogo", ignoreCase = true) ||
                v.videoUrl.contains("embed", ignoreCase = true)
            )
        }
        if (embedsToExtract.isNotEmpty()) {
            videos.removeAll(embedsToExtract.toSet())
            coroutineScope {
                embedsToExtract.map { embed ->
                    async {
                        try {
                            com.lagradost.cloudstream3.utils.loadExtractor(
                                url = embed.videoUrl,
                                referer = baseUrl,
                                subtitleCallback = onSubtitle,
                                callback = onLink,
                            )
                        } catch (t: Throwable) {
                            logcat(LogPriority.DEBUG, t) { "Failed deep extract for ${embed.videoUrl}" }
                        }
                    }
                }.awaitAll()
            }
        }

        if (videos.isEmpty() && episode.url.isNotBlank() && (episode.url.startsWith("http://", ignoreCase = true) || episode.url.startsWith("https://", ignoreCase = true))) {
            try {
                withTimeoutOrNull(20_000L) {
                    com.lagradost.cloudstream3.utils.loadExtractor(
                        url = episode.url,
                        referer = baseUrl,
                        subtitleCallback = onSubtitle,
                        callback = onLink,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "Direct loadExtractor fallback failed for ${episode.name}" }
            }
        }

        logcat(LogPriority.INFO) { "Loaded ${videos.size} videos for ${episode.name} from ${api.name}" }
        return videos.toList()
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return getVideoList(episode).toHosterList()
    }

    private suspend fun loadDetailsSafely(url: String): LoadResponse? {
        bindActivityContext()
        val candidates = linkedSetOf<String>()
        candidates.add(url)
        val fixed = api.fixUrl(url)
        candidates.add(fixed)
        if (url.startsWith(baseUrl, ignoreCase = true)) {
            val rel = url.removePrefix(baseUrl).trimStart('/')
            if (rel.isNotBlank()) candidates.add(rel)
        }
        for (candidate in candidates) {
            try {
                val resp = api.load(candidate)
                if (resp != null) return resp
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "api.load failed for candidate '$candidate' in ${api.name}" }
            }
        }
        return null
    }

    private fun toSEpisodeFromAny(obj: Any?, order: Float): SEpisode? {
        if (obj == null) return null
        if (obj is Episode) {
            return obj.toSEpisode(order)
        }
        return try {
            val sEpisode = SEpisode.create()
            val name = obj.javaClass.methods.firstOrNull { it.name == "getName" }?.invoke(obj) as? String
            val data = (obj.javaClass.methods.firstOrNull { it.name == "getData" }?.invoke(obj) as? String)
                ?: (obj.javaClass.methods.firstOrNull { it.name == "getUrl" }?.invoke(obj) as? String)
                ?: ""
            val epNum = (obj.javaClass.methods.firstOrNull { it.name == "getEpisode" }?.invoke(obj) as? Int)?.toFloat()
                ?: order
            sEpisode.name = name ?: "Episode ${epNum.toInt()}"
            sEpisode.url = data
            sEpisode.episode_number = epNum
            sEpisode
        } catch (_: Throwable) {
            null
        }
    }

    private fun SearchResponse.toSAnime(): SAnime {
        val sAnime = SAnime.create()
        sAnime.title = name
        sAnime.url = url
        posterUrl?.let { sAnime.thumbnail_url = api.fixUrl(it) }
        return sAnime
    }

    private fun Episode.toSEpisode(index: Float): SEpisode {
        val sEpisode = SEpisode.create()
        sEpisode.name = this.name ?: "Episode ${this.episode ?: index.toInt()}"
        sEpisode.url = this.data.ifBlank { this.url }
        sEpisode.episode_number = (this.episode ?: index.toInt()).toFloat()
        return sEpisode
    }
}
