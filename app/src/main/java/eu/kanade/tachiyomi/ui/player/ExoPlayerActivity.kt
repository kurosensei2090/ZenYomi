package eu.kanade.tachiyomi.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Native Android ExoPlayer (CloudStream player engine) integrated into ZenYomi.
 */
class ExoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var animeId: Long = -1L
    private var episodeId: Long = -1L

    private var currentAnime: Anime? = null
    private var currentEpisode: Episode? = null
    private var currentVideo: Video? = null

    private val playerPreferences: PlayerPreferences by lazy { Injekt.get() }
    private val networkHelper: NetworkHelper by lazy { Injekt.get() }
    private val getAnime: GetAnime by lazy { Injekt.get() }
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId by lazy { Injekt.get() }
    private val updateEpisode: UpdateEpisode by lazy { Injekt.get() }
    private val upsertAnimeHistory: UpsertAnimeHistory by lazy { Injekt.get() }
    private val sourceManager: AnimeSourceManager by lazy { Injekt.get() }

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

        val frame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(Color.BLACK)
        }

        frame.addView(playerView)
        setContentView(frame)

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

        loadEpisodeAndPlay()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun loadEpisodeAndPlay() {
        lifecycleScope.launch {
            try {
                val anime = withContext(Dispatchers.IO) { getAnime.await(animeId) }
                val episodes = withContext(Dispatchers.IO) { getEpisodesByAnimeId.await(animeId) }
                val episode = episodes.find { it.id == episodeId }

                if (anime == null || episode == null) {
                    toast("Failed to load episode details")
                    finish()
                    return@launch
                }

                currentAnime = anime
                currentEpisode = episode

                val source = sourceManager.get(anime.source) ?: run {
                    toast("Source not found")
                    finish()
                    return@launch
                }

                toast("ExoPlayer: Loading servers...")
                val hosters = withContext(Dispatchers.IO) {
                    EpisodeLoader.getHosters(episode, anime, source)
                }

                if (hosters.isEmpty()) {
                    toast("No servers available for this episode")
                    finish()
                    return@launch
                }

                val bestVideo = withContext(Dispatchers.IO) {
                    HosterLoader.getBestVideo(source, hosters)
                }

                if (bestVideo == null || bestVideo.videoUrl.isEmpty()) {
                    toast("No playable video found")
                    finish()
                    return@launch
                }

                currentVideo = bestVideo
                initExoPlayer(bestVideo, episode.lastSecondSeen)
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, t) { "ExoPlayer loading error: ${t.message}" }
                toast("ExoPlayer error: ${t.message}")
                finish()
            }
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
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

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
                    track.url.endsWith(".vtt", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_VTT
                    track.url.endsWith(".srt", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                    track.url.endsWith(".ass", ignoreCase = true) || track.url.endsWith(".ssa", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
                    else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                }
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
                    .setMimeType(mimeType)
                    .setLanguage(track.lang)
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }
        val mediaItem = mediaItemBuilder.build()
        exo.setMediaItem(mediaItem)

        if (startPositionSeconds > 0) {
            exo.seekTo(startPositionSeconds * 1000L)
        }

        exo.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    logcat(LogPriority.WARN, error) { "ExoPlayer error: ${error.message}" }
                    toast("Playback error: ${error.message}")
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        markEpisodeSeen()
                    }
                }
            },
        )

        exo.prepare()
        exo.playWhenReady = true
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
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
        playerView?.useController = !isInPictureInPictureMode
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

    companion object {
        const val EXTRA_ANIME_ID = "extra_anime_id"
        const val EXTRA_EPISODE_ID = "extra_episode_id"

        fun newIntent(context: Context, animeId: Long, episodeId: Long): Intent {
            return Intent(context, ExoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ANIME_ID, animeId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
        }
    }
}
