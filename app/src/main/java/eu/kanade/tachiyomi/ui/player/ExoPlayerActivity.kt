package eu.kanade.tachiyomi.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    // State holders
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
    private var availableSubtitlesState = mutableStateOf<List<ExoTrackInfo>>(emptyList())
    private var availableAudioTracksState = mutableStateOf<List<ExoTrackInfo>>(emptyList())
    private var isSubtitleDisabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()

        animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1L)
        episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1L)

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

        loadEpisodeAndPlay(episodeId)
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun loadEpisodeAndPlay(targetEpisodeId: Long, resumePosition: Long = -1L) {
        lifecycleScope.launch {
            try {
                isBufferingState.value = true
                val anime = currentAnime ?: withContext(Dispatchers.IO) { getAnime.await(animeId) }
                if (anime == null) {
                    toast("Failed to load anime details")
                    finish()
                    return@launch
                }
                currentAnime = anime
                animeTitleState.value = anime.title

                if (allEpisodes.isEmpty()) {
                    val rawEpisodes = withContext(Dispatchers.IO) { getEpisodesByAnimeId.await(animeId) }
                    allEpisodes = rawEpisodes.sortedWith(getEpisodeSort(anime, sortDescending = false))
                    episodesListState.value = allEpisodes
                }

                val episode = allEpisodes.find { it.id == targetEpisodeId }
                if (episode == null) {
                    toast("Episode not found")
                    finish()
                    return@launch
                }

                currentEpisode = episode
                episodeId = episode.id
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

                val bestVideo = withContext(Dispatchers.IO) {
                    HosterLoader.getBestVideo(source, hosters)
                }

                if (bestVideo == null || bestVideo.videoUrl.isEmpty()) {
                    toast("No playable stream found")
                    isBufferingState.value = false
                    return@launch
                }

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
                logcat(LogPriority.ERROR, t) { "ExoPlayer loading error: ${t.message}" }
                toast("Error loading episode: ${t.message}")
                isBufferingState.value = false
            }
        }
    }

    private fun initExoPlayer(video: Video, startPositionSeconds: Long) {
        player?.release()

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
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10000L)
            .setSeekForwardIncrementMs(10000L)
            .build()

        player = exo
        playerView?.player = exo

        val mediaItemBuilder = MediaItem.Builder().setUri(video.videoUrl)
        if (video.subtitleTracks.isNotEmpty()) {
            val subtitleConfigs = video.subtitleTracks.map { track ->
                val mimeType = when {
                    track.url.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    track.url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                    track.url.endsWith(".ass", ignoreCase = true) || track.url.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
                    .setMimeType(mimeType)
                    .setLanguage(track.lang)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }
        val mediaItem = mediaItemBuilder.build()
        exo.setMediaItem(mediaItem)

        exo.setPlaybackSpeed(speedState.value)

        if (startPositionSeconds > 0) {
            exo.seekTo(startPositionSeconds * 1000L)
        }

        exo.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isPlayingState.value = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBufferingState.value = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) {
                        durationState.longValue = exo.duration.coerceAtLeast(0L)
                    } else if (state == Player.STATE_ENDED) {
                        markEpisodeSeen()
                        playNextEpisodeIfExists()
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val subs = mutableListOf<ExoTrackInfo>()
                    val audios = mutableListOf<ExoTrackInfo>()

                    for (group in tracks.groups) {
                        val type = group.type
                        if (type == C.TRACK_TYPE_TEXT) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language?.uppercase() ?: ""
                                val label = format.label ?: if (lang.isNotEmpty()) lang else "Subtitle ${subs.size + 1}"
                                val selected = group.isTrackSelected(i)
                                subs.add(ExoTrackInfo(group, i, label, lang, selected))
                            }
                        } else if (type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language?.uppercase() ?: ""
                                val label = format.label ?: if (lang.isNotEmpty()) lang else "Audio ${audios.size + 1}"
                                val selected = group.isTrackSelected(i)
                                audios.add(ExoTrackInfo(group, i, label, lang, selected))
                            }
                        }
                    }

                    availableSubtitlesState.value = subs
                    availableAudioTracksState.value = audios
                    isSubtitleDisabledState.value = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                }

                override fun onPlayerError(error: PlaybackException) {
                    logcat(LogPriority.WARN, error) { "ExoPlayer error: ${error.message}" }
                    toast("Playback error: ${error.message}")
                    isBufferingState.value = false
                }
            },
        )

        exo.prepare()
        exo.playWhenReady = true
    }

    private fun playNextEpisodeIfExists() {
        val currIdx = allEpisodes.indexOfFirst { it.id == episodeId }
        if (currIdx != -1 && currIdx < allEpisodes.size - 1) {
            val next = allEpisodes[currIdx + 1]
            toast("Playing next: ${next.name}")
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

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Throwable) {}
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playerPreferences.pipOnExit().get() &&
            playerPreferences.enablePip().get() &&
            player?.isPlaying == true
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView?.useController = false
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        player?.release()
        player = null
        playerView = null
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
        val hostersList by hostersListState
        val availableSubtitles by availableSubtitlesState
        val availableAudioTracks by availableAudioTracksState
        val isSubtitleDisabled by isSubtitleDisabledState

        var currentPosition by remember { mutableLongStateOf(0L) }
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubPosition by remember { mutableFloatStateOf(0f) }

        var controlsVisible by remember { mutableStateOf(true) }
        var isLocked by remember { mutableStateOf(false) }
        var showUnlockBriefly by remember { mutableStateOf(false) }
        var userInteractionTick by remember { mutableIntStateOf(0) }

        var showEpisodesDialog by remember { mutableStateOf(false) }
        var showServersDialog by remember { mutableStateOf(false) }
        var showSpeedDialog by remember { mutableStateOf(false) }
        var showTracksDialog by remember { mutableStateOf(false) }

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
                        this@ExoPlayerActivity.playerView = this
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Gesture Detector Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLocked) {
                        detectTapGestures(
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
                                    notifyInteraction()
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
                                )
                            }
                        }

                        IconButton(onClick = {
                            notifyInteraction()
                            showTracksDialog = true
                        }) {
                            Icon(
                                Icons.Outlined.Subtitles,
                                contentDescription = "Audio & Subtitles",
                                tint = Color.White,
                            )
                        }
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
                                val currIdx = allEpisodes.indexOfFirst { it.id == episodeId }
                                if (currIdx > 0) {
                                    val prev = allEpisodes[currIdx - 1]
                                    loadEpisodeAndPlay(prev.id, 0L)
                                }
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
                                val currIdx = allEpisodes.indexOfFirst { it.id == episodeId }
                                if (currIdx != -1 && currIdx < allEpisodes.size - 1) {
                                    val next = allEpisodes[currIdx + 1]
                                    loadEpisodeAndPlay(next.id, 0L)
                                }
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

            // DIALOG: Episodes List
            if (showEpisodesDialog) {
                AlertDialog(
                    onDismissRequest = { showEpisodesDialog = false },
                    title = { Text("Episodes (${episodesList.size})") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(episodesList) { ep ->
                                val isSelected = ep.id == episodeId
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
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                items(hostersList) { hoster ->
                                    val isCurrent = currentVideo?.videoTitle?.contains(hoster.hosterName, ignoreCase = true) == true
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
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
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = hoster.hosterName,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = "Active",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
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

    companion object {
        const val EXTRA_ANIME_ID = "extra_anime_id"
        const val EXTRA_EPISODE_ID = "extra_episode_id"

        fun newIntent(context: Context, animeId: Long, episodeId: Long): Intent {
            return Intent(context, ExoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ANIME_ID, animeId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
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
