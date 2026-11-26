package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.util.TypedValue
import android.view.WindowManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadJob
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamAnimeSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.getEpisodeSort
import java.io.File
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Track representation for ExoPlayer audio and subtitle stream tracks.
 */
data class ExoTrackInfo(
    val group: Tracks.Group,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean,
)

/**
 * Track representation for ExoPlayer video quality stream tracks.
 */
data class ExoVideoQualityInfo(
    val group: Tracks.Group,
    val trackIndex: Int,
    val height: Int,
    val width: Int,
    val bitrate: Int,
    val isSelected: Boolean,
) {
    val resolutionLabel: String
        get() = "${height}p" + (if (width > 0) " (${width}x${height})" else "")
}

/**
 * Native Android ExoPlayer engine with full CloudStream player UI overlay and episode switching.
 */
class ExoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var animeId: Long = -1L
    private var episodeId: Long = -1L

    private var currentAnime: Anime? = null
    private var currentEpisode: Episode? = null
    private var allEpisodes: List<Episode> = emptyList()
    private var currentHosters: List<Hoster> = emptyList()
    private var currentVideo: Video? = null

    private val playerPreferences: PlayerPreferences by lazy { Injekt.get() }
    private val networkHelper: NetworkHelper by lazy { Injekt.get() }
    private val getAnime: GetAnime by lazy { Injekt.get() }
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId by lazy { Injekt.get() }
    private val updateEpisode: UpdateEpisode by lazy { Injekt.get() }
    private val upsertAnimeHistory: UpsertAnimeHistory by lazy { Injekt.get() }
    private val sourceManager: AnimeSourceManager by lazy { Injekt.get() }

    private var currentLoadingJob: Job? = null

    // State holders
    private var currentEpisodeIdState = mutableLongStateOf(-1L)
    private var isPlayingState = mutableStateOf(false)
    private var isBufferingState = mutableStateOf(true)
    private var positionState = mutableLongStateOf(0L)
    private var durationState = mutableLongStateOf(0L)
    private var animeTitleState = mutableStateOf("")
    private var episodeNameState = mutableStateOf("")
    private var qualityInfoState = mutableStateOf("")
    private var speedState = mutableFloatStateOf(1.0f)
    private var resizeModeState = mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var hasPrevEpisodeState = mutableStateOf(false)
    private var hasNextEpisodeState = mutableStateOf(false)
    private var episodesListState = mutableStateOf<List<Episode>>(emptyList())
    private var hostersListState = mutableStateOf<List<Hoster>>(emptyList())
    private var selectedHosterNameState = mutableStateOf("")
    private val failedHosterNames = mutableSetOf<String>()
    private var availableSubtitlesState = mutableStateOf<List<ExoTrackInfo>>(emptyList())
    private var availableAudioTracksState = mutableStateOf<List<ExoTrackInfo>>(emptyList())
    private var availableVideoQualitiesState = mutableStateOf<List<ExoVideoQualityInfo>>(emptyList())
    private var selectedQualityLabelState = mutableStateOf("Auto")
    private var isSubtitleDisabledState = mutableStateOf(false)
    private var isInPipModeState = mutableStateOf(false)
    private var showStatsOverlayState = mutableStateOf(false)
    private val externalSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
    private var pendingSelectedSubtitleLabel: String? = null
    private var pipReceiver: BroadcastReceiver? = null
    private var isPipClosing = false

    private val subtitlePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            addExternalSubtitle(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            } else {
                curVol == 0
            }
            if (isMuted || curVol == 0) {
                val targetVol = (maxVol / 2).coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()

        animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1L)
        episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)
        currentEpisodeIdState.longValue = episodeId

        if (animeId == -1L || episodeId == -1L) {
            toast("Invalid episode parameters")
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveProgress()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        playerPreferences.enablePip().get() &&
                        playerPreferences.pipOnExit().get() &&
                        player?.isPlaying == true
                    ) {
                        enterPip()
                    } else {
                        finish()
                    }
                }
            },
        )

        setContent {
            TachiyomiTheme {
                ExoPlayerScreen()
            }
        }

        lifecycleScope.launch {
            CloudStreamAnimeSource.discoveredHostersFlow.collect { event ->
                val ep = currentEpisode
                if (ep != null && (ep.url == event.episodeUrl || event.episodeUrl.endsWith(ep.url) || ep.url.endsWith(event.episodeUrl))) {
                    val updatedList = hostersListState.value.toMutableList()
                    var hasChanges = false
                    for (newHoster in event.hosters) {
                        val existingIdx = updatedList.indexOfFirst { it.hosterName.equals(newHoster.hosterName, ignoreCase = true) }
                        if (existingIdx != -1) {
                            val existing = updatedList[existingIdx]
                            val existingVids = existing.videoList.orEmpty()
                            val newVids = (newHoster.videoList.orEmpty()).filter { nv ->
                                existingVids.none { it.videoUrl == nv.videoUrl || it.videoTitle == nv.videoTitle }
                            }
                            if (newVids.isNotEmpty()) {
                                updatedList[existingIdx] = existing.copy(videoList = existingVids + newVids)
                                hasChanges = true
                            }
                        } else {
                            updatedList.add(newHoster)
                            hasChanges = true
                        }
                    }
                    if (hasChanges) {
                        hostersListState.value = updatedList
                        currentHosters = updatedList
                    }
                }
            }
        }

        loadEpisodeAndPlay(episodeId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newAnimeId = intent.getLongExtra(EXTRA_ANIME_ID, -1L)
        val newEpisodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)
        if (newAnimeId != -1L && newEpisodeId != -1L) {
            val isDifferentAnime = newAnimeId != animeId
            animeId = newAnimeId
            episodeId = newEpisodeId
            currentEpisodeIdState.longValue = newEpisodeId
            saveProgress()
            if (isDifferentAnime) {
                currentAnime = null
                allEpisodes = emptyList()
                episodesListState.value = emptyList()
            }
            loadEpisodeAndPlay(newEpisodeId, 0L)
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun loadEpisodeAndPlay(targetEpisodeId: Long, resumePosition: Long = -1L) {
        CloudStreamAnimeSource.cancelExtraction()
        currentLoadingJob?.cancel()
        currentLoadingJob = lifecycleScope.launch {
            try {
                // Halt any currently running playback immediately so it doesn't continue in the background
                player?.stop()
                player?.clearMediaItems()
                externalSubtitleConfigs.clear()
                pendingSelectedSubtitleLabel = null

                currentEpisode = null
                currentHosters = emptyList()
                hostersListState.value = emptyList()
                selectedHosterNameState.value = ""
                failedHosterNames.clear()
                currentVideo = null
                qualityInfoState.value = ""
                availableSubtitlesState.value = emptyList()
                availableAudioTracksState.value = emptyList()
                availableVideoQualitiesState.value = emptyList()
                selectedQualityLabelState.value = "Auto"

                isBufferingState.value = true
                currentEpisodeIdState.longValue = targetEpisodeId
                episodeId = targetEpisodeId

                val anime = if (currentAnime?.id == animeId) {
                    currentAnime!!
                } else {
                    currentAnime = null
                    allEpisodes = emptyList()
                    episodesListState.value = emptyList()
                    withContext(Dispatchers.IO) { getAnime.await(animeId) }
                }
                if (anime == null) {
                    toast("Failed to load anime details")
                    finish()
                    return@launch
                }
                currentAnime = anime
                animeTitleState.value = anime.title

                if (allEpisodes.isEmpty() || allEpisodes.none { it.animeId == anime.id }) {
                    val rawEpisodes = withContext(Dispatchers.IO) { getEpisodesByAnimeId.await(animeId) }
                    allEpisodes = rawEpisodes.sortedWith(getEpisodeSort(anime, sortDescending = false))
                    episodesListState.value = allEpisodes
                }

                val episode = allEpisodes.find { it.id == targetEpisodeId }
                if (episode == null) {
                    toast("Episode not found")
                    isBufferingState.value = false
                    return@launch
                }

                currentEpisode = episode
                episodeNameState.value = episode.name

                val currIdx = allEpisodes.indexOfFirst { it.id == episode.id }
                hasPrevEpisodeState.value = currIdx > 0
                hasNextEpisodeState.value = currIdx != -1 && currIdx < allEpisodes.size - 1

                val source = sourceManager.get(anime.source) ?: run {
                    toast("Source not found")
                    finish()
                    return@launch
                }

                val hosters = withContext(Dispatchers.IO) {
                    EpisodeLoader.getHosters(episode, anime, source)
                }
                currentHosters = hosters
                hostersListState.value = hosters

                if (hosters.isEmpty()) {
                    toast("No servers available for this episode")
                    isBufferingState.value = false
                    return@launch
                }

                val initialHosterName = intent.getStringExtra(EXTRA_INITIAL_HOSTER_NAME)
                intent.removeExtra(EXTRA_INITIAL_HOSTER_NAME)
                val targetHoster = if (!initialHosterName.isNullOrEmpty()) {
                    hosters.find { it.hosterName.equals(initialHosterName, ignoreCase = true) }
                } else null

                val hosterAndVideo = withContext(Dispatchers.IO) {
                    if (targetHoster != null) {
                        try {
                            val hosterState = EpisodeLoader.loadHosterVideos(source, targetHoster)
                            val chosenVideo = if (hosterState is eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState.Ready && hosterState.videoList.isNotEmpty()) {
                                HosterLoader.getResolvedVideo(source, hosterState.videoList.first())
                            } else null
                            if (chosenVideo != null && chosenVideo.videoUrl.isNotEmpty()) {
                                Pair(targetHoster, chosenVideo)
                            } else {
                                HosterLoader.getBestHosterAndVideo(source, hosters)
                            }
                        } catch (e: Exception) {
                            HosterLoader.getBestHosterAndVideo(source, hosters)
                        }
                    } else {
                        HosterLoader.getBestHosterAndVideo(source, hosters)
                    }
                }

                if (hosterAndVideo == null || hosterAndVideo.second.videoUrl.isEmpty()) {
                    val startPos = if (resumePosition >= 0) resumePosition else episode.lastSecondSeen
                    switchToNextServer(startPos, "No playable stream found")
                    return@launch
                }

                val (chosenHoster, bestVideo) = hosterAndVideo
                selectedHosterNameState.value = chosenHoster.hosterName
                currentVideo = bestVideo
                qualityInfoState.value = buildString {
                    append(bestVideo.videoTitle.ifEmpty { "Default" })
                    if (!bestVideo.quality.isNullOrEmpty() && bestVideo.quality != bestVideo.videoTitle) {
                        append(" • ${bestVideo.quality}")
                    }
                }

                val startPos = if (resumePosition >= 0) resumePosition else episode.lastSecondSeen
                initExoPlayer(bestVideo, startPos)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                logcat(LogPriority.ERROR, t) { "ExoPlayer loading error: ${t.message}" }
                toast("Error loading episode: ${t.message}")
                isBufferingState.value = false
            }
        }
    }

    private fun switchToNextServer(resumePosition: Long = -1L, reason: String? = null) {
        val currentHosterName = selectedHosterNameState.value
        if (currentHosterName.isNotEmpty()) {
            failedHosterNames.add(currentHosterName)
        }

        val allHosters = hostersListState.value
        val availableHosters = allHosters.filter { it.hosterName !in failedHosterNames }

        if (availableHosters.isEmpty()) {
            toast(if (!reason.isNullOrEmpty()) "All servers failed ($reason)" else "All servers failed")
            isBufferingState.value = false
            return
        }

        val nextHoster = availableHosters.first()
        val currentPosSec = if (resumePosition >= 0) {
            resumePosition
        } else {
            (player?.currentPosition ?: 0L) / 1000L
        }

        toast(
            if (currentHosterName.isNotEmpty()) {
                "Server '$currentHosterName' failed. Switching to '${nextHoster.hosterName}'..."
            } else {
                "Switching to '${nextHoster.hosterName}'..."
            }
        )

        lifecycleScope.launch {
            try {
                isBufferingState.value = true
                val source = sourceManager.get(currentAnime?.source ?: -1L) ?: run {
                    isBufferingState.value = false
                    return@launch
                }
                val hosterState = withContext(Dispatchers.IO) {
                    EpisodeLoader.loadHosterVideos(source, nextHoster)
                }
                val chosenVideo = if (hosterState is eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState.Ready && hosterState.videoList.isNotEmpty()) {
                    HosterLoader.getResolvedVideo(source, hosterState.videoList.first())
                } else null

                if (chosenVideo != null && chosenVideo.videoUrl.isNotEmpty()) {
                    currentVideo = chosenVideo
                    selectedHosterNameState.value = nextHoster.hosterName
                    qualityInfoState.value = chosenVideo.videoTitle
                    initExoPlayer(chosenVideo, currentPosSec)
                } else {
                    failedHosterNames.add(nextHoster.hosterName)
                    switchToNextServer(currentPosSec, "Failed to load from ${nextHoster.hosterName}")
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                failedHosterNames.add(nextHoster.hosterName)
                switchToNextServer(currentPosSec, e.message)
            }
        }
    }

    private fun createMediaSource(video: Video): MediaSource {
        val client = networkHelper.client.newBuilder().build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(client).apply {
            setUserAgent(networkHelper.defaultUserAgentProvider())
            val headersMap = mutableMapOf<String, String>()
            video.headers?.forEach { (k, v) ->
                headersMap[k] = v
            }
            if (headersMap.isNotEmpty()) {
                setDefaultRequestProperties(headersMap)
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val allSubtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

        // Embedded / network subtitle tracks from video object
        if (video.subtitleTracks.isNotEmpty()) {
            for (track in video.subtitleTracks) {
                try {
                    val mimeType = when {
                        track.url.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                        track.url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                        track.url.endsWith(".ass", ignoreCase = true) || track.url.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                        .setMimeType(mimeType)
                        .setLanguage(track.lang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    allSubtitleConfigs.add(subConfig)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Failed to create subtitle source for ${track.url}" }
                }
            }
        }

        // External sidecar subtitle files added by user
        allSubtitleConfigs.addAll(externalSubtitleConfigs)

        // Build main video media item with subtitles attached directly
        val mediaItem = MediaItem.Builder()
            .setUri(video.videoUrl)
            .setSubtitleConfigurations(allSubtitleConfigs)
            .build()
        val videoSource = mediaSourceFactory.createMediaSource(mediaItem)

        // Additional audio tracks if present
        if (video.audioTracks.isNotEmpty()) {
            val sources = mutableListOf<MediaSource>(videoSource)
            for (audioTrack in video.audioTracks) {
                val audioItem = MediaItem.Builder()
                    .setUri(audioTrack.url)
                    .build()
                sources.add(mediaSourceFactory.createMediaSource(audioItem))
            }
            return MergingMediaSource(*sources.toTypedArray())
        }

        return videoSource
    }

    private fun addExternalSubtitle(uri: Uri) {
        try {
            var displayName = "External Subtitle"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val n = cursor.getString(nameIndex)
                    if (!n.isNullOrBlank()) displayName = n
                }
            }

            // Copy subtitle stream to local cache directory so ExoPlayer has direct, persistent file access
            val subCacheDir = File(cacheDir, "subtitles").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localSubFile = File(subCacheDir, "${System.currentTimeMillis()}_$safeName")
            contentResolver.openInputStream(uri)?.use { input ->
                localSubFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Sniff format from filename or content
            var mimeType = MimeTypes.APPLICATION_SUBRIP
            val lowerName = displayName.lowercase()
            if (lowerName.endsWith(".vtt")) {
                mimeType = MimeTypes.TEXT_VTT
            } else if (lowerName.endsWith(".ass") || lowerName.endsWith(".ssa")) {
                mimeType = MimeTypes.TEXT_SSA
            } else if (lowerName.endsWith(".srt")) {
                mimeType = MimeTypes.APPLICATION_SUBRIP
            } else if (localSubFile.exists() && localSubFile.length() > 0) {
                try {
                    val header = localSubFile.bufferedReader().use { reader ->
                        (1..10).mapNotNull { reader.readLine() }.joinToString("\n")
                    }
                    if (header.contains("WEBVTT", ignoreCase = true)) {
                        mimeType = MimeTypes.TEXT_VTT
                    } else if (header.contains("[Script Info]", ignoreCase = true) || header.contains("[Events]", ignoreCase = true)) {
                        mimeType = MimeTypes.TEXT_SSA
                    }
                } catch (_: Throwable) {}
            }

            val targetUri = if (localSubFile.exists() && localSubFile.length() > 0) {
                Uri.fromFile(localSubFile)
            } else {
                uri
            }

            val subConfig = MediaItem.SubtitleConfiguration.Builder(targetUri)
                .setId(displayName)
                .setMimeType(mimeType)
                .setLabel(displayName)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            externalSubtitleConfigs.add(subConfig)
            pendingSelectedSubtitleLabel = displayName

            val exo = player
            val video = currentVideo
            if (exo != null && video != null) {
                val currentPos = exo.currentPosition
                val wasPlaying = exo.isPlaying
                val mediaSource = createMediaSource(video)
                exo.setMediaSource(mediaSource, currentPos)
                exo.prepare()
                if (wasPlaying) exo.play()
                toast("Loaded subtitle: $displayName")
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load external subtitle: ${e.message}" }
            toast("Failed to load subtitle: ${e.message}")
        }
    }

    private fun initExoPlayer(video: Video, startPositionSeconds: Long) {
        val client = networkHelper.client.newBuilder().build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(client).apply {
            setUserAgent(networkHelper.defaultUserAgentProvider())
            val headersMap = mutableMapOf<String, String>()
            video.headers?.forEach { (k, v) ->
                headersMap[k] = v
            }
            if (headersMap.isNotEmpty()) {
                setDefaultRequestProperties(headersMap)
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        // Reuse existing player if available to prevent Surface detachment or recreation flicker
        val exo = player ?: ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000L)
            .setSeekForwardIncrementMs(10000L)
            .build().also { newPlayer ->
                player = newPlayer
                playerView?.player = newPlayer

                newPlayer.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            isPlayingState.value = isPlaying
                            updatePipParams()
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            isBufferingState.value = (state == Player.STATE_BUFFERING)
                            if (state == Player.STATE_READY) {
                                durationState.longValue = newPlayer.duration.coerceAtLeast(0L)
                                updatePipParams()
                            } else if (state == Player.STATE_ENDED) {
                                markEpisodeSeen()
                                playNextEpisodeIfExists()
                            }
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            val subs = mutableListOf<ExoTrackInfo>()
                            val audios = mutableListOf<ExoTrackInfo>()
                            val videos = mutableListOf<ExoVideoQualityInfo>()

                            for (group in tracks.groups) {
                                val type = group.type
                                val isAudio = type == C.TRACK_TYPE_AUDIO ||
                                    (group.length > 0 && group.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true)
                                if (type == C.TRACK_TYPE_TEXT) {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val lang = format.language?.uppercase() ?: ""
                                        val label = format.label ?: format.id ?: if (lang.isNotEmpty()) lang else "Subtitle ${subs.size + 1}"
                                        val selected = group.isTrackSelected(i)
                                        subs.add(ExoTrackInfo(group, i, label, lang, selected))
                                    }
                                } else if (isAudio) {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val lang = format.language?.uppercase() ?: ""
                                        val label = format.label ?: if (lang.isNotEmpty()) lang else "Audio ${audios.size + 1}"
                                        val selected = group.isTrackSelected(i)
                                        audios.add(ExoTrackInfo(group, i, label, lang, selected))
                                    }
                                } else if (type == C.TRACK_TYPE_VIDEO) {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        if (format.height > 0) {
                                            val selected = group.isTrackSelected(i)
                                            videos.add(ExoVideoQualityInfo(group, i, format.height, format.width, format.bitrate, selected))
                                        }
                                    }
                                }
                            }

                            availableSubtitlesState.value = subs
                            availableAudioTracksState.value = audios

                            // Auto-select pending external subtitle if requested
                            val pendingLabel = pendingSelectedSubtitleLabel
                            if (pendingLabel != null) {
                                val targetSub = subs.find {
                                    it.name.equals(pendingLabel, ignoreCase = true) ||
                                        it.group.getTrackFormat(it.trackIndex).label?.equals(pendingLabel, ignoreCase = true) == true ||
                                        it.group.getTrackFormat(it.trackIndex).id?.equals(pendingLabel, ignoreCase = true) == true
                                } ?: subs.lastOrNull()
                                if (targetSub != null) {
                                    val exoInstance = player ?: newPlayer
                                    exoInstance.trackSelectionParameters = exoInstance.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .setOverrideForType(TrackSelectionOverride(targetSub.group.mediaTrackGroup, targetSub.trackIndex))
                                        .build()
                                    isSubtitleDisabledState.value = false
                                    pendingSelectedSubtitleLabel = null
                                }
                            }

                            if (audios.isNotEmpty() && !audios.any { it.isSelected }) {
                                val firstAudio = audios.first()
                                newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .setOverrideForType(TrackSelectionOverride(firstAudio.group.mediaTrackGroup, firstAudio.trackIndex))
                                    .build()
                            }
                            val distinctVideos = videos.groupBy { it.height }
                                .mapValues { entry -> entry.value.maxByOrNull { it.bitrate } ?: entry.value.first() }
                                .values
                                .sortedByDescending { it.height }
                            availableVideoQualitiesState.value = distinctVideos
                            isSubtitleDisabledState.value = newPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            logcat(LogPriority.WARN, error) { "ExoPlayer error: ${error.message}" }
                            if (externalSubtitleConfigs.isNotEmpty()) {
                                toast("Subtitle error: ${error.message ?: "Failed to display subtitle"}")
                                externalSubtitleConfigs.clear()
                                pendingSelectedSubtitleLabel = null
                                currentVideo?.let { vid ->
                                    val exo = player ?: return
                                    val curPos = exo.currentPosition
                                    val wasPlaying = exo.isPlaying
                                    val mediaSource = createMediaSource(vid)
                                    exo.setMediaSource(mediaSource, curPos)
                                    exo.prepare()
                                    if (wasPlaying) exo.play()
                                }
                                return
                            }
                            val remaining = hostersListState.value.filter { it.hosterName !in failedHosterNames && it.hosterName != selectedHosterNameState.value }
                            if (remaining.isNotEmpty()) {
                                switchToNextServer(reason = error.errorCodeName)
                            } else {
                                val isCodecOrFormatError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
                                if (isCodecOrFormatError) {
                                    toast("Hardware decoder failed. Retrying in MPV Player...")
                                    val targetAnimeId = currentAnime?.id ?: animeId
                                    val targetEpisodeId = currentEpisodeIdState.longValue.takeIf { it != -1L } ?: episodeId
                                    val intent = PlayerActivity.newIntent(this@ExoPlayerActivity, targetAnimeId, targetEpisodeId)
                                    startActivity(intent)
                                    finish()
                                    return
                                }
                                toast("Playback error: ${error.message}")
                                isBufferingState.value = false
                            }
                        }
                    },
                )
            }

        // Clean slate for new media: clear stale overrides, reset volume
        exo.stop()
        exo.clearMediaItems()
        exo.volume = 1.0f
        exo.trackSelectionParameters = exo.trackSelectionParameters
            .buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()

        val mediaSource = createMediaSource(video)
        exo.setMediaSource(mediaSource)
        exo.setPlaybackSpeed(speedState.value)

        if (startPositionSeconds > 0) {
            exo.seekTo(startPositionSeconds * 1000L)
        }

        exo.prepare()
        exo.playWhenReady = true
    }

    private fun playPreviousEpisodeIfExists() {
        val currIdx = allEpisodes.indexOfFirst { it.id == currentEpisodeIdState.longValue }
        if (currIdx > 0) {
            val prev = allEpisodes[currIdx - 1]
            if (playerPreferences.pipEpisodeToasts().get()) {
                toast("Playing: ${prev.name}")
            }
            loadEpisodeAndPlay(prev.id, 0L)
        }
    }

    private fun playNextEpisodeIfExists() {
        val currIdx = allEpisodes.indexOfFirst { it.id == currentEpisodeIdState.longValue }
        if (currIdx != -1 && currIdx < allEpisodes.size - 1) {
            val next = allEpisodes[currIdx + 1]
            if (playerPreferences.pipEpisodeToasts().get()) {
                toast("Playing: ${next.name}")
            }
            loadEpisodeAndPlay(next.id, 0L)
        }
    }

    private fun saveProgress() {
        val exo = player ?: return
        val episode = currentEpisode ?: return
        val currentPositionSec = exo.currentPosition / 1000L
        val totalSec = exo.duration / 1000L

        if (totalSec > 0 && currentPositionSec >= 0) {
            val progressThreshold = playerPreferences.progressPreference().get()
            val isWatched = (currentPositionSec.toFloat() / totalSec.toFloat()) >= progressThreshold

            CoroutineScope(Dispatchers.IO + NonCancellable).launch {
                updateEpisode.await(
                    EpisodeUpdate(
                        id = episode.id,
                        lastSecondSeen = currentPositionSec,
                        totalSeconds = totalSec,
                        seen = isWatched || episode.seen,
                    ),
                )
                upsertAnimeHistory.await(
                    AnimeHistoryUpdate(
                        episodeId = episode.id,
                        seenAt = Date(),
                    ),
                )
            }
        }
    }

    private fun markEpisodeSeen() {
        val episode = currentEpisode ?: return
        val exo = player ?: return
        val totalSec = exo.duration / 1000L

        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id,
                    lastSecondSeen = totalSec,
                    totalSeconds = totalSec,
                    seen = true,
                ),
            )
            upsertAnimeHistory.await(
                AnimeHistoryUpdate(
                    episodeId = episode.id,
                    seenAt = Date(),
                ),
            )
        }
    }

    private fun createPipParams(): PictureInPictureParams {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            error("PiP is not supported on Android < O")
        }
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val animeTitle = animeTitleState.value
            val epName = episodeNameState.value
            if (animeTitle.isNotBlank()) {
                builder.setTitle(animeTitle)
            }
            if (epName.isNotBlank()) {
                builder.setSubtitle(epName)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit().get()
            val isPlaying = player?.isPlaying == true
            builder.setAutoEnterEnabled(isPlaying && autoEnter)
            builder.setSeamlessResizeEnabled(isPlaying && autoEnter)
        }
        val currIdx = allEpisodes.indexOfFirst { it.id == episodeId }
        val actions = createPipActions(
            context = this,
            isPaused = player?.isPlaying != true,
            replaceWithPrevious = playerPreferences.pipReplaceWithPrevious().get(),
            playlistCount = allEpisodes.size,
            playlistPosition = currIdx.coerceAtLeast(0),
        )
        builder.setActions(actions)

        try {
            val videoFormat = player?.videoFormat
            if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
                val ratio = videoFormat.width.toFloat() / videoFormat.height.toFloat()
                if (ratio in 0.42f..2.38f) {
                    builder.setAspectRatio(Rational(videoFormat.width, videoFormat.height))
                } else {
                    builder.setAspectRatio(Rational(16, 9))
                }
            } else {
                builder.setAspectRatio(Rational(16, 9))
            }
        } catch (_: Throwable) {
            builder.setAspectRatio(Rational(16, 9))
        }

        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (isInPictureInPictureMode) {
                    setPictureInPictureParams(createPipParams())
                }
            } catch (_: Throwable) {}
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = createPipParams()
                enterPictureInPictureMode(params)
            } catch (_: Throwable) {}
        }
    }

    private fun selectVideoQuality(track: ExoVideoQualityInfo?) {
        val exo = player ?: return
        val params = exo.trackSelectionParameters.buildUpon()
        if (track == null) {
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            params.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            selectedQualityLabelState.value = "Auto"
            qualityInfoState.value = currentVideo?.videoTitle?.let { "$it • Auto" } ?: "Auto"
        } else {
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            params.setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
            selectedQualityLabelState.value = "${track.height}p"
            qualityInfoState.value = currentVideo?.videoTitle?.let { "$it • ${track.height}p" } ?: "${track.height}p"
        }
        exo.trackSelectionParameters = params.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerPipReceiver() {
        if (pipReceiver == null) {
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                        PIP_PAUSE -> player?.pause()
                        PIP_PLAY -> player?.play()
                        PIP_NEXT -> playNextEpisodeIfExists()
                        PIP_PREVIOUS -> playPreviousEpisodeIfExists()
                        PIP_SKIP -> {
                            player?.let { p ->
                                val target = (p.currentPosition + 10_000L).coerceIn(0L, p.duration.coerceAtLeast(0L))
                                p.seekTo(target)
                            }
                        }
                    }
                    updatePipParams()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }
    }

    private fun unregisterPipReceiver() {
        pipReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            pipReceiver = null
        }
    }

    private fun releasePlayerAndFinish() {
        currentLoadingJob?.cancel()
        currentLoadingJob = null
        unregisterPipReceiver()
        saveProgress()
        player?.let { p ->
            try {
                p.stop()
                p.clearMediaItems()
                p.release()
            } catch (_: Throwable) {}
        }
        player = null
        playerView = null
        if (!isFinishing) {
            finish()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        isInPipModeState.value = isInPictureInPictureMode
        playerView?.useController = false
        if (!isInPictureInPictureMode) {
            unregisterPipReceiver()
            if (isPipClosing || isFinishing) {
                releasePlayerAndFinish()
            }
        } else {
            isPipClosing = false
            updatePipParams()
            registerPipReceiver()
            player?.play()
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode && !isFinishing && !isPipClosing) {
            return
        }
        saveProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player?.pause()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            isPipClosing = true
            releasePlayerAndFinish()
            return
        }

        if (isFinishing || isPipClosing) {
            releasePlayerAndFinish()
        }
    }

    override fun onDestroy() {
        CloudStreamAnimeSource.cancelExtraction()
        if (!AnimeDownloadJob.isRunning(this)) {
            TorrentServerService.stop()
        }
        super.onDestroy()
        releasePlayerAndFinish()
    }

    // -------------------------------------------------------------
    // Compose CloudStream Player UI
    // -------------------------------------------------------------
    @Composable
    private fun ExoPlayerScreen() {
        val isPlaying by isPlayingState
        val isBuffering by isBufferingState
        val duration by durationState
        val animeTitle by animeTitleState
        val episodeName by episodeNameState
        val qualityInfo by qualityInfoState
        val speed by speedState
        val resizeMode by resizeModeState
        val hasPrevEpisode by hasPrevEpisodeState
        val hasNextEpisode by hasNextEpisodeState
        val episodesList by episodesListState
        val currentEpisodeId by currentEpisodeIdState
        val hostersList by hostersListState
        val availableSubtitles by availableSubtitlesState
        val availableAudioTracks by availableAudioTracksState
        val availableVideoQualities by availableVideoQualitiesState
        val selectedQualityLabel by selectedQualityLabelState
        val isSubtitleDisabled by isSubtitleDisabledState
        val isInPipMode by isInPipModeState

        var currentPosition by remember { mutableLongStateOf(0L) }
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubPosition by remember { mutableFloatStateOf(0f) }

        var controlsVisible by remember { mutableStateOf(true) }
        var isLocked by remember { mutableStateOf(false) }
        var showUnlockBriefly by remember { mutableStateOf(false) }
        var userInteractionTick by remember { mutableIntStateOf(0) }

        var showEpisodesDialog by remember { mutableStateOf(false) }
        var showServersDialog by remember { mutableStateOf(false) }
        var showQualityDialog by remember { mutableStateOf(false) }
        var showSpeedDialog by remember { mutableStateOf(false) }
        var showTracksDialog by remember { mutableStateOf(false) }

        var is2xSpeedActive by remember { mutableStateOf(false) }
        var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
        var volumeOverlay by remember { mutableStateOf<Float?>(null) }
        var seekOverlayText by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()

        // Position tracking ticker
        LaunchedEffect(player, isPlaying) {
            while (isActive) {
                player?.let { p ->
                    if (!isScrubbing) {
                        currentPosition = p.currentPosition
                        durationState.longValue = p.duration.coerceAtLeast(0L)
                    }
                }
                delay(200)
            }
        }

        // Auto-hide controls after 4 seconds
        LaunchedEffect(controlsVisible, isPlaying, userInteractionTick, isLocked) {
            if (controlsVisible && isPlaying && !isLocked) {
                delay(4000)
                controlsVisible = false
            }
        }

        fun notifyInteraction() {
            userInteractionTick++
            controlsVisible = true
        }

        fun flashSeek(text: String) {
            seekOverlayText = text
            scope.launch {
                delay(800)
                seekOverlayText = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Video Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = this@ExoPlayerActivity.player
                        this.useController = false
                        this.resizeMode = resizeMode
                        this.setBackgroundColor(android.graphics.Color.BLACK)
                        this.subtitleView?.apply {
                            val style = CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            )
                            setStyle(style)
                            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                        }
                        this@ExoPlayerActivity.playerView = this
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (!isInPipMode) {
                // Gesture Detector Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isLocked, isPlaying) {
                            detectTapGestures(
                            onPress = {
                                if (!isLocked && isPlaying) {
                                    val originalSpeed = player?.playbackParameters?.speed ?: 1.0f
                                    val speedJob = scope.launch {
                                        delay(450L)
                                        is2xSpeedActive = true
                                        player?.setPlaybackSpeed(2.0f)
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        speedJob.cancel()
                                        if (is2xSpeedActive) {
                                            is2xSpeedActive = false
                                            player?.setPlaybackSpeed(originalSpeed)
                                        }
                                    }
                                }
                            },
                            onTap = {
                                if (isLocked) {
                                    showUnlockBriefly = !showUnlockBriefly
                                } else {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) notifyInteraction()
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!isLocked) {
                                    val screenWidth = size.width
                                    val current = player?.currentPosition ?: 0L
                                    if (offset.x < screenWidth / 2) {
                                        val target = (current - 10000L).coerceAtLeast(0L)
                                        player?.seekTo(target)
                                        currentPosition = target
                                        flashSeek("-10s")
                                    } else {
                                        val target = (current + 10000L).coerceAtMost(player?.duration ?: Long.MAX_VALUE)
                                        player?.seekTo(target)
                                        currentPosition = target
                                        flashSeek("+10s")
                                    }
                                    if (controlsVisible) {
                                        userInteractionTick++
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(isLocked) {
                        if (!isLocked) {
                            var isDraggingLeft = false
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDraggingLeft = offset.x < size.width / 2
                                },
                                onDragEnd = {
                                    brightnessOverlay = null
                                    volumeOverlay = null
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val delta = -dragAmount / size.height
                                    if (isDraggingLeft) {
                                        val cur = window.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                                        val newB = (cur + delta * 1.5f).coerceIn(0.01f, 1.0f)
                                        val lp = window.attributes
                                        lp.screenBrightness = newB
                                        window.attributes = lp
                                        brightnessOverlay = newB
                                    } else {
                                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                        val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curV = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val step = (delta * maxV * 1.5f).toInt()
                                        if (step != 0) {
                                            val newV = (curV + step).coerceIn(0, maxV)
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                            volumeOverlay = newV.toFloat() / maxV.toFloat()
                                        }
                                    }
                                    notifyInteraction()
                                },
                            )
                        }
                    },
            )

            // Overlays: Brightness & Volume Feedback Badges
            brightnessOverlay?.let { b ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(b * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            volumeOverlay?.let { v ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(v * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Seek Indicator (+10s / -10s)
            seekOverlayText?.let { txt ->
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = txt,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            // 2X Speed Press-and-Hold Indicator
            if (is2xSpeedActive) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "2X Speed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Lock Overlay Button
            if (isLocked) {
                AnimatedVisibility(
                    visible = showUnlockBriefly,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.clickable {
                            isLocked = false
                            controlsVisible = true
                            notifyInteraction()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Full CloudStream Controls Overlay
            AnimatedVisibility(
                visible = controlsVisible && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                ) {
                    // TOP BAR: Back, Title, Episode, Quality
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        IconButton(onClick = {
                            saveProgress()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                playerPreferences.enablePip().get() &&
                                player?.isPlaying == true
                            ) {
                                enterPip()
                            } else {
                                finish()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = animeTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (episodeName.isNotEmpty()) {
                                Text(
                                    text = episodeName,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (qualityInfo.isNotEmpty()) {
                                Text(
                                    text = qualityInfo,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.clickable {
                                        notifyInteraction()
                                        showQualityDialog = true
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.width(48.dp))
                    }

                    // CENTER CONTROLS: Prev, -10s, Play/Pause/Buffer, +10s, Next
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                    ) {
                        // Previous Episode Button
                        IconButton(
                            enabled = hasPrevEpisode,
                            onClick = {
                                notifyInteraction()
                                saveProgress()
                                playPreviousEpisodeIfExists()
                            },
                            modifier = Modifier.size(54.dp),
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Episode",
                                tint = if (hasPrevEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(Modifier.width(20.dp))

                        // Rewind 10s Button
                        IconButton(
                            onClick = {
                                notifyInteraction()
                                val target = ((player?.currentPosition ?: 0L) - 10000L).coerceAtLeast(0L)
                                player?.seekTo(target)
                                currentPosition = target
                                flashSeek("-10s")
                            },
                            modifier = Modifier.size(54.dp),
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(Modifier.width(24.dp))

                        // Play/Pause/Buffering Big Center Button
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp),
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            notifyInteraction()
                                            player?.let { p ->
                                                if (p.isPlaying) p.pause() else p.play()
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(24.dp))

                        // Fast Forward 10s Button
                        IconButton(
                            onClick = {
                                notifyInteraction()
                                val target = ((player?.currentPosition ?: 0L) + 10000L).coerceAtMost(player?.duration ?: Long.MAX_VALUE)
                                player?.seekTo(target)
                                currentPosition = target
                                flashSeek("+10s")
                            },
                            modifier = Modifier.size(54.dp),
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(Modifier.width(20.dp))

                        // Next Episode Button
                        IconButton(
                            enabled = hasNextEpisode,
                            onClick = {
                                notifyInteraction()
                                saveProgress()
                                playNextEpisodeIfExists()
                            },
                            modifier = Modifier.size(54.dp),
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Episode",
                                tint = if (hasNextEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    // BOTTOM CONTROLS: Scrubber & CloudStream Action Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        // Time & Scrubber Slider Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val displayPos = if (isScrubbing) scrubPosition.toLong() else currentPosition
                            Text(
                                text = formatDuration(displayPos),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Slider(
                                value = if (isScrubbing) scrubPosition else currentPosition.toFloat(),
                                onValueChange = {
                                    isScrubbing = true
                                    scrubPosition = it
                                    notifyInteraction()
                                },
                                onValueChangeFinished = {
                                    player?.seekTo(scrubPosition.toLong())
                                    currentPosition = scrubPosition.toLong()
                                    isScrubbing = false
                                    saveProgress()
                                    notifyInteraction()
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )

                            val remaining = (duration - displayPos).coerceAtLeast(0L)
                            Text(
                                text = "-${formatDuration(remaining)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // CloudStream Action Bar (Sources, Speed, Resize, Rotate, Lock, PiP)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CloudStreamActionButton(
                                icon = Icons.Default.VideoLibrary,
                                label = "Sources",
                                onClick = {
                                    notifyInteraction()
                                    showServersDialog = true
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Default.HighQuality,
                                label = "Quality",
                                onClick = {
                                    notifyInteraction()
                                    showQualityDialog = true
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Default.ViewList,
                                label = "Episodes",
                                onClick = {
                                    notifyInteraction()
                                    showEpisodesDialog = true
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Default.Speed,
                                label = "${speed}x",
                                onClick = {
                                    notifyInteraction()
                                    showSpeedDialog = true
                                },
                            )

                            val resizeLabel = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                else -> "Fit"
                            }
                            CloudStreamActionButton(
                                icon = Icons.Default.AspectRatio,
                                label = resizeLabel,
                                onClick = {
                                    notifyInteraction()
                                    val nextMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    resizeModeState.intValue = nextMode
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Default.ScreenRotation,
                                label = "Rotate",
                                onClick = {
                                    notifyInteraction()
                                    requestedOrientation = when (requestedOrientation) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Default.Lock,
                                label = "Lock",
                                onClick = {
                                    isLocked = true
                                    controlsVisible = false
                                    showUnlockBriefly = true
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Outlined.Subtitles,
                                label = "Tracks",
                                onClick = {
                                    notifyInteraction()
                                    showTracksDialog = true
                                },
                            )

                            CloudStreamActionButton(
                                icon = Icons.Outlined.Info,
                                label = "Stats",
                                onClick = {
                                    notifyInteraction()
                                    showStatsOverlayState.value = !showStatsOverlayState.value
                                },
                            )

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerPreferences.enablePip().get()) {
                                CloudStreamActionButton(
                                    icon = Icons.Default.PictureInPictureAlt,
                                    label = "PiP",
                                    onClick = {
                                        notifyInteraction()
                                        enterPip()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Stream Info ("Stats for Nerds") HUD Overlay
            val showStatsOverlay by showStatsOverlayState
            AnimatedVisibility(
                visible = showStatsOverlay && !isInPipMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 56.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .widthIn(min = 230.dp, max = 320.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Stream Info",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Stats",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showStatsOverlayState.value = false },
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Video Stats
                        val vf = player?.videoFormat
                        val res = if (vf != null && vf.width > 0 && vf.height > 0) "${vf.width}x${vf.height}" else "Unknown"
                        val fps = if (vf != null && vf.frameRate > 0) "%.1f fps".format(vf.frameRate) else "N/A"
                        val vBitrate = if (vf != null && vf.bitrate > 0) "${vf.bitrate / 1000} kbps" else "Adaptive / N/A"
                        val vCodec = vf?.codecs ?: vf?.sampleMimeType?.substringAfter('/')?.uppercase() ?: "Auto"

                        // Audio Stats
                        val af = player?.audioFormat
                        val aCodec = af?.codecs ?: af?.sampleMimeType?.substringAfter('/')?.uppercase() ?: "Auto"
                        val aChannels = when (af?.channelCount) {
                            1 -> "Mono (1ch)"
                            2 -> "Stereo (2ch)"
                            6 -> "5.1 Surround"
                            null -> "Auto"
                            else -> "${af.channelCount} ch"
                        }
                        val aSampleRate = if (af != null && af.sampleRate > 0) "${af.sampleRate / 1000.0} kHz" else "N/A"

                        // Connection & Buffer
                        val bufMs = ((player?.bufferedPosition ?: 0L) - currentPosition).coerceAtLeast(0L)
                        val bufSec = "%.1f s".format(bufMs / 1000.0)

                        val url = currentVideo?.videoUrl?.lowercase() ?: ""
                        val protocol = when {
                            url.contains(".m3u8") -> "HLS"
                            url.contains(".mpd") -> "DASH"
                            url.startsWith("https") -> "HTTPS Direct"
                            url.startsWith("http") -> "HTTP Direct"
                            else -> "Direct Stream"
                        }

                        StreamInfoRow("Resolution", res)
                        StreamInfoRow("Bitrate", vBitrate)
                        StreamInfoRow("Framerate", fps)
                        StreamInfoRow("Video Codec", vCodec)
                        StreamInfoRow("Audio Codec", "$aCodec ($aChannels)")
                        StreamInfoRow("Sample Rate", aSampleRate)
                        StreamInfoRow("Buffer Health", bufSec)
                        StreamInfoRow("Protocol", protocol)
                    }
                }
            }

            // DIALOG: Episodes List
            if (showEpisodesDialog) {
                AlertDialog(
                    onDismissRequest = { showEpisodesDialog = false },
                    title = { Text("Episodes (${episodesList.size})") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(episodesList) { ep ->
                                val isSelected = ep.id == currentEpisodeId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showEpisodesDialog = false
                                            saveProgress()
                                            loadEpisodeAndPlay(ep.id, 0L)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = ep.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (ep.seen) {
                                        Text(
                                            text = "Seen",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEpisodesDialog = false }) {
                            Text("Close")
                        }
                    },
                )
            }

            // DIALOG: Servers / Quality
            if (showServersDialog) {
                AlertDialog(
                    onDismissRequest = { showServersDialog = false },
                    title = { Text("Servers / Quality") },
                    text = {
                        if (hostersList.isEmpty()) {
                            Text("No alternate servers available.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                                items(hostersList) { hoster ->
                                    val isCurrent = hoster.hosterName.equals(selectedHosterNameState.value, ignoreCase = true) ||
                                        (selectedHosterNameState.value.isEmpty() && currentVideo?.videoTitle?.contains(hoster.hosterName, ignoreCase = true) == true)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable {
                                                showServersDialog = false
                                                lifecycleScope.launch {
                                                    try {
                                                        isBufferingState.value = true
                                                        val source = sourceManager.get(currentAnime?.source ?: -1L) ?: return@launch
                                                        val hosterState = withContext(Dispatchers.IO) {
                                                            EpisodeLoader.loadHosterVideos(source, hoster)
                                                        }
                                                        val chosenVideo = if (hosterState is eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState.Ready && hosterState.videoList.isNotEmpty()) {
                                                            HosterLoader.getResolvedVideo(source, hosterState.videoList.first())
                                                        } else null

                                                        if (chosenVideo != null && chosenVideo.videoUrl.isNotEmpty()) {
                                                            currentVideo = chosenVideo
                                                            selectedHosterNameState.value = hoster.hosterName
                                                            failedHosterNames.remove(hoster.hosterName)
                                                            qualityInfoState.value = chosenVideo.videoTitle
                                                            val resumePos = player?.currentPosition ?: 0L
                                                            initExoPlayer(chosenVideo, resumePos / 1000L)
                                                        } else {
                                                            toast("Failed to load stream from ${hoster.hosterName}")
                                                            isBufferingState.value = false
                                                        }
                                                    } catch (e: Exception) {
                                                        toast("Server switch failed: ${e.message}")
                                                        isBufferingState.value = false
                                                    }
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                    ) {
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active server",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                            )
                                        }
                                        Text(
                                            text = hoster.hosterName,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                modifier = Modifier.padding(start = 8.dp),
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showServersDialog = false }) {
                            Text("Close")
                        }
                    },
                )
            }

            // DIALOG: Playback Speed
            if (showSpeedDialog) {
                val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                AlertDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    title = { Text("Playback Speed") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(speedOptions) { s ->
                                val isSelected = (s == speed)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            speedState.floatValue = s
                                            player?.setPlaybackSpeed(s)
                                            showSpeedDialog = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = "${s}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSpeedDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            // DIALOG: Video Quality
            if (showQualityDialog) {
                AlertDialog(
                    onDismissRequest = { showQualityDialog = false },
                    title = { Text("Video Quality") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            // Auto option
                            item {
                                val isAuto = selectedQualityLabel == "Auto"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectVideoQuality(null)
                                            showQualityDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = "Auto (Adaptive)",
                                        fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isAuto) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isAuto) {
                                        Text(
                                            text = "Active",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            if (availableVideoQualities.isNotEmpty()) {
                                items(availableVideoQualities) { q ->
                                    val isSelected = selectedQualityLabel == "${q.height}p"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectVideoQuality(q)
                                                showQualityDialog = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = q.resolutionLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "Active",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showQualityDialog = false }) {
                            Text("Close")
                        }
                    },
                )
            }

            // DIALOG: Audio & Subtitles
            if (showTracksDialog) {
                AlertDialog(
                    onDismissRequest = { showTracksDialog = false },
                    title = { Text("Audio & Subtitles") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                            // Subtitles Section
                            item {
                                Text(
                                    text = "SUBTITLES",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            try {
                                                subtitlePickerLauncher.launch(
                                                    arrayOf(
                                                        "text/*",
                                                        "application/x-subrip",
                                                        "application/octet-stream",
                                                        "*/*",
                                                    ),
                                                )
                                            } catch (_: Exception) {
                                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Add external subtitle (.srt, .vtt, .ass)...",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            item {
                                val isOff = isSubtitleDisabled
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player?.let { p ->
                                                p.trackSelectionParameters = p.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                    .build()
                                            }
                                            isSubtitleDisabledState.value = true
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = "Off",
                                        fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isOff) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isOff) {
                                        Text(
                                            text = "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            if (availableSubtitles.isEmpty()) {
                                item {
                                    Text(
                                        text = "No subtitle tracks found.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                items(availableSubtitles) { sub ->
                                    val isCurrent = sub.isSelected && !isSubtitleDisabled
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                player?.let { p ->
                                                    p.trackSelectionParameters = p.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                        .setOverrideForType(TrackSelectionOverride(sub.group.mediaTrackGroup, sub.trackIndex))
                                                        .build()
                                                }
                                                isSubtitleDisabledState.value = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = sub.name,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = "✓",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }

                            // Audio Section
                            item {
                                Text(
                                    text = "AUDIO TRACKS",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            if (availableAudioTracks.isEmpty()) {
                                item {
                                    Text(
                                        text = "Default audio only.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                items(availableAudioTracks) { audio ->
                                    val isCurrent = audio.isSelected
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                player?.let { p ->
                                                    p.trackSelectionParameters = p.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                        .setOverrideForType(TrackSelectionOverride(audio.group.mediaTrackGroup, audio.trackIndex))
                                                        .build()
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = audio.name,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = "✓",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTracksDialog = false }) {
                            Text("Done")
                        }
                    },
                )
            }
        }
    }
}

    @Composable
    private fun CloudStreamActionButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    @Composable
    private fun StreamInfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$label:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    companion object {
        const val EXTRA_ANIME_ID = "extra_anime_id"
        const val EXTRA_EPISODE_ID = "extra_episode_id"
        const val EXTRA_INITIAL_HOSTER_NAME = "extra_initial_hoster_name"
        const val EXTRA_INITIAL_VIDEO_URL = "extra_initial_video_url"

        fun newIntent(
            context: Context,
            animeId: Long,
            episodeId: Long,
            initialHosterName: String? = null,
            initialVideoUrl: String? = null,
        ): Intent {
            return Intent(context, ExoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ANIME_ID, animeId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
                if (!initialHosterName.isNullOrEmpty()) {
                    putExtra(EXTRA_INITIAL_HOSTER_NAME, initialHosterName)
                }
                if (!initialVideoUrl.isNullOrEmpty()) {
                    putExtra(EXTRA_INITIAL_VIDEO_URL, initialVideoUrl)
                }
            }
        }

        fun formatDuration(millis: Long): String {
            if (millis <= 0) return "00:00"
            val totalSeconds = millis / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}
