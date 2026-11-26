package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeDownload(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    val changeDownloader: Boolean = false,
    var video: Video? = null,
) : ProgressListener {

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val progressStateFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = progressStateFlow.asStateFlow()
    var progress: Int
        get() = progressStateFlow.value
        set(value) {
            progressStateFlow.value = value
        }

    @Transient
    private val statsStateFlow = MutableStateFlow(0L)

    @Transient
    val statsFlow = statsStateFlow.asStateFlow()

    @Transient
    @Volatile
    var downloadedBytes: Long = 0L

    @Transient
    @Volatile
    var estimatedTotalBytes: Long = 0L

    @Transient
    @Volatile
    var downloadSpeedBytesPerSec: Long = 0L

    @Transient
    @Volatile
    var etaSeconds: Long = -1L

    fun notifyStatsUpdated() {
        statsStateFlow.value = System.nanoTime()
    }

    fun resetStats() {
        downloadedBytes = 0L
        estimatedTotalBytes = 0L
        downloadSpeedBytesPerSec = 0L
        etaSeconds = -1L
        notifyStatsUpdated()
    }

    fun formatStatsSummary(): String? {
        if (downloadedBytes <= 0L && downloadSpeedBytesPerSec <= 0L) return null
        val parts = mutableListOf<String>()
        if (downloadedBytes > 0L) {
            val sizeText = if (estimatedTotalBytes > downloadedBytes) {
                "${formatBytes(downloadedBytes)} / ~${formatBytes(estimatedTotalBytes)}"
            } else {
                formatBytes(downloadedBytes)
            }
            parts.add(sizeText)
        }
        if (downloadSpeedBytesPerSec > 0L) {
            parts.add("${formatBytes(downloadSpeedBytesPerSec)}/s")
        }
        if (etaSeconds > 0L) {
            parts.add(formatEta(etaSeconds))
        }
        return parts.joinToString(" • ").takeIf { it.isNotEmpty() }
    }

    /**
     * Updates the status of the download
     *
     * @param bytesRead the updated TOTAL number of bytes read (not a partial increment)
     * @param contentLength the updated content length
     * @param done whether progress has completed or not
     */
    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        val newProgress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
        if (progress != newProgress) progress = newProgress
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromEpisodeId(
            episodeId: Long,
            getEpisode: GetEpisode = Injekt.get(),
            getAnimeById: GetAnime = Injekt.get(),
            sourceManager: AnimeSourceManager = Injekt.get(),
        ): AnimeDownload? {
            val episode = getEpisode.await(episodeId) ?: return null
            val anime = getAnimeById.await(episode.animeId) ?: return null
            val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return null

            return AnimeDownload(source, anime, episode)
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024L -> "$bytes B"
                bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
                bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
                else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }

        fun formatEta(seconds: Long): String {
            return when {
                seconds < 60L -> "${seconds}s"
                seconds < 3600L -> "${seconds / 60L}m ${seconds % 60L}s"
                else -> "${seconds / 3600L}h ${(seconds % 3600L) / 60L}m"
            }
        }
    }
}
