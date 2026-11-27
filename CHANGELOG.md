# Changelog

All notable changes to **ZenYomi** will be documented in this file.

---

## [v1.0.6] - 2026-11-27

### 🌟 Overview of Release 1.0.6 (Builds 86 – 99)
ZenYomi v1.0.6 fixes all in-app Getting Started and Help/Troubleshooting URLs across onboarding, library empty states, settings, migration, WebView, and local sources (`kurosensei2090/ZenYomi`), stabilizes Discover scroll position on open/refresh, smooths out Manga details screen transitions, improves ExoPlayer external subtitle loading, fixes CloudStream server discovery disappearing when restarting an episode (#58), adds same-source related seasons/recommendations with cover badges (#59), fixes MPV server switching & adds in-player server refresh/deduplication (#60), fixes episode downloader Wi-Fi/VPN detection, WorkManager auto-retry, and link-resolution timeouts (#61), persists the user's selected anime season across navigation (#62), and switches Manga/Anime extension installation to private in-app loading without `REQUEST_INSTALL_PACKAGES` (Build 93).

### 🎨 UI, Player & Documentation Fixes
- **Private In-App Extension Loading & Play Protect Fix (Build 93)**:
  - **Switched Default Extension Installer to `PRIVATE` (`ExtensionInstallerPreference`)**: Manga & Anime extensions now download and load privately inside ZenYomi (`filesDir/exts`) just like CloudStream `.cs3` plugins, without needing Android's system package installer or "Install unknown apps" permission.
  - **Removed `REQUEST_INSTALL_PACKAGES` (`AndroidManifest.xml`, `NewUpdateScreen`, `Permissions.kt`)**: Removed `REQUEST_INSTALL_PACKAGES` to eliminate Google Play Protect's *"This app can install potentially harmful apps without your permission"* block, updated in-app update downloads to open in the user's browser, and guarded `canRequestPackageInstalls()` against `SecurityException` on Android 17+ (Build 94).
- **Persistent Anime Season Selection (Issue #62, Build 92)**:
  - Saved the user's selected season per anime ID (`anime_selected_seasons` preferences) in `AnimeScreen` so returning to an anime restores the chosen season instead of reverting to the first unseen season.
- **Downloader Wi-Fi/VPN Detection, Auto-Retry & Timeouts (Issue #61, Build 91)**:
  - **Wi-Fi & VPN Detection (`NetworkStateTracker`)**: Fixed `isWifi` detection to support `NET_CAPABILITY_NOT_METERED` alongside `TRANSPORT_WIFI` so downloads don't falsely report "no Wi-Fi" when using a VPN.
  - **WorkManager Constraints & Auto-Retry (`AnimeDownloadJob`, `MangaDownloadJob`)**: Added `NetworkType.UNMETERED` / `CONNECTED` constraints, returned `Result.retry()` when waiting for Wi-Fi/network, and replaced the tight busy-wait loop with `delay(250L)`.
  - **Resolver Timeouts (`AnimeDownloader`)**: Added 25s/15s timeouts to `getHosters()`, `getBestVideo()`, and `getResolvedVideo()` so hanging hosters fail over instead of getting stuck at `"Downloading"`.
- **MPV Server Switching, Refresh & Deduplication (Issue #60, Build 90)**:
  - **Clean MPV Stream Replacement (`PlayerActivity`, `PlayerViewModel`)**: Removed the racy `MPVLib.command("stop")` before `loadfile(..., "replace")`, reset MPV `start` option when `resumePosSec == 0`, unpaused MPV after `loadfile`, and added a 15s resolver timeout + cancellable `manualSwitchJob` so switching servers no longer gets stuck.
  - **In-Player Server Refresh & Deduplication (`QualitySheet`, `PlayerViewModel`)**: Added a **Refresh** button in the Qualities sheet to re-fetch servers without leaving playback, and deduplicated identical `videoUrl` entries per hoster.
- **Same-Source Related Seasons & Cover Badges (Issue #59, Build 89)**:
  - **Same-Source Season Auto-Discovery (`SyncRelatedAnimeWithSource`, `AnimeScreenModel`)**: Automatically searches the same source for previous/next seasons and parts when an extension doesn't implement `getRelatedAnimeList()`, grouping them into **Previous Seasons**, **Next Seasons**, and **Related Seasons**.
  - **CloudStream Recommendations (`CloudStreamAnimeSource`)**: Enabled `supportsRelatedAnime` and `getRelatedAnimeList()` using `LoadResponse.recommendations`.
  - **Season/Part Cover Badges (`RelatedAnimeRows`)**: Added cover overlay badges (`Season 1`, `Season 2`, `Part 2`, `Prequel`, `Sequel`, etc.) on related anime cards.
- **Session-Scoped CloudStream Server Extraction (Issue #58, Build 88)**:
  - Scoped `CloudStreamAnimeSource.cancelExtraction()` to the active player session (`beginPlayerSession()` / `cancelExtractionIfSession()`) in both `PlayerActivity` and `ExoPlayerActivity`, preventing an exiting player's `onDestroy()` from cancelling a newly restarted episode's server extraction job.
  - Added a replay buffer (`replay = 8`) to `CloudStreamAnimeSource.discoveredHostersFlow` so late-discovered servers aren't lost before the player UI attaches.
- **Discover Scroll, Manga Transition & ExoPlayer External Subtitles (Build 87)**:
  - **Discover Scroll Position (`AnimeDiscoverContent`)**: Added `rememberLazyListState()` and anchored scroll to top on source switch, initial load, and refresh completion so content no longer jumps upward when sections populate.
  - **Smoother Manga Details Transition (`MangaScreenModel`)**: Deferred background tracker observation and remote metadata/chapter syncs by 220ms so the 200ms screen slide transition completes smoothly before heavy updates run.
  - **ExoPlayer External Subtitles (`ExoPlayerActivity`)**: Fixed remote subtitle MIME detection for URLs with query parameters, stripped UTF-8 BOM from picked subtitle files, merged local sidecar subtitles via `SingleSampleMediaSource` + `MergingMediaSource`, and ensured text track selection is enabled before prepare.
- **Getting Started Guide & Help URLs (Build 86)**:
  - Updated all 14 files containing `https://github.com/zenyomiapp/zenyomi` URLs (`GuidesStep`, `Constants`, `SettingsDataScreen`, `SettingsTrackingScreen`, `PlayerSettingsCustomButtonScreen`, `WebViewScreenContent`, `AnimeLibraryUpdateJob`, `AnimeLibraryUpdateNotifier`, `MangaLibraryUpdateJob`, `MangaLibraryUpdateNotifier`, `MigrateAnimeSourceTab`, `MigrateMangaSourceTab`, `LocalAnimeSource`, `LocalMangaSource`) to point to `https://github.com/kurosensei2090/ZenYomi` with matching section anchors.
  - Added **Getting Started**, **Tracking**, **Local Manga**, **Local Anime**, **Migration**, **Bulk Updates**, **Custom Buttons**, and **Troubleshooting** sections to `README.md`.

### 📦 Build History for v1.0.6

| Build | Commit | Description |
|---|---|---|
| **Build 99** | `b3b33b8` | Smooth Anime details screen transition by deferring background syncs until slide animation settles |
| **Build 98** | `1d5833c` | Remove unused `PackageInstaller`, `Legacy`, and `Shizuku` installer classes/dependencies (private-only extension loading) |
| **Build 97** | `2dc6979` | Release verification build & Issue #65 resolution |
| **Build 96** | `de004ca` | Improve external downloader chooser/cleanup, add Seal/YTDLnis/ADM Pro support, and harden episode download candidate retry cleanup (Issue #64) |
| **Build 95** | `ef145be` | Release verification build & Issue #63 resolution |
| **Build 94** | `4327a7f` | Guard `canRequestPackageInstalls()` against `SecurityException` when `REQUEST_INSTALL_PACKAGES` is omitted (Issue #63) |
| **Build 93** | `2e3edf4` | Switch Manga/Anime extension loading to private in-app mode & remove `REQUEST_INSTALL_PACKAGES` |
| **Build 92** | `acc30b3` | Persist selected anime season per anime ID across navigation and app restarts (Issue #62) |
| **Build 91** | `367b519` | Fix downloader Wi-Fi/VPN detection, add WorkManager network constraints & auto-retry, and add hoster resolution timeouts (Issue #61) |
| **Build 90** | `ce64ca4` | Fix MPV server-switching stall, add 15s resolver timeout, deduplicate hoster videos, and add Refresh button in Qualities sheet (Issue #60) |
| **Build 89** | `eebf677` | Same-source related seasons/recommendations discovery, CloudStream `getRelatedAnimeList()`, and season/part cover badges (Issue #59) |
| **Build 88** | `9278ca1` | Session-scoped CloudStream server extraction & replay buffer for `discoveredHostersFlow` (Issue #58) |
| **Build 87** | `62fa705` | Fix Discover scroll jump, smooth Manga details transition, and fix ExoPlayer external subtitles |
| **Build 86** | `2a7af78` | Fix Getting Started & Help URLs (`kurosensei2090/ZenYomi`) and add full documentation sections to `README.md` |

---

## [v1.0.5] - 2026-11-26

### 🌟 Overview of Release 1.0.5 (Builds 66 – 85)
ZenYomi v1.0.5 brings extensive improvements across the media playback engines (both MPV and ExoPlayer), external player integrations, episode downloader, multi-season presentation, source browsing, manga chapter loading, and UI stability. Highlights include internal multi-resolution quality selection for MPV Player (#51), MPV startup and buffer latency optimizations (#51), fatal crash prevention and safe chooser fallback for external players (#52), official support for the mpvRx external player (#53), source search query retention across navigation (#54), 1DM-style episode download stats (size, speed, ETA) with anti-IP-block pacing & automatic download profiles (#55, Build 82), live video player loading stage & network speed indicator across MPV and ExoPlayer (#56, Build 83), multi-season selector dropdown for CloudStream anime (#57, Build 84), `memo` database persistence for manga/chapters plus automatic chapter-list refresh fallback in `HttpPageLoader` to fix "Refresh Chapter List" errors on modern manga extensions like Asura Scans, Vortex Scans, and AllManga (Build 85), upstream stability features including "Load Ahead Only on Wi-Fi" reader data saving, one-tap player playback speed preset cycling, and SQLite concurrency hardening (Build 78), CloudStream provider timeout guards, automatic ExoPlayer to MPV codec error failover, TorrServer idle auto-shutdown, and backup validation guards (Build 79), ExoPlayer preferred audio/subtitle language auto-selection, 30s backward seek cache, Coil `onTrimMemory` memory management, transient HTTP retry interceptor, and automatic extension refresh on network reconnect (Build 80), failover timestamp persistence, configurable image disk cache size, and safe legacy SoC decoder profile (Build 81), active server indicators and auto-failover in ExoPlayer (#49), real-time link discovery in episode options (#50), and external subtitle rendering fixes.

---

### 🚀 Highlights & Features by Category

#### 🎬 MPV Player Enhancements
- **Multi-Quality Stream Resolution Selection (Issue #51, Build 74)**:
  - Extracted internal video tracks from MPV's `track-list` (`type == video`).
  - Added a dedicated **Stream Quality** resolution selector inside MPV Player's `QualitySheet` (e.g. *Auto (Adaptive)*, *1080p*, *720p*, *480p*, *360p*) with live stream switching via `vid`.
- **Buffering & Startup Performance Optimization (Issue #51, Build 74)**:
  - Removed hardcoded `hls-bitrate = max`, enabling fast adaptive chunk loading and preventing buffering freezes on slow connections.
  - Tuned initial buffer readahead from 25s down to 8s and optimized stream buffer size (4MB) and cache settings for instant playback start.
- **HLS Master Playlist Variant Expansion (Issue #51, Build 74)**:
  - In `CloudStreamAnimeSource` and `M3u8Helper`, auto-expanded `#EXT-X-STREAM-INF` master playlists so sources like *Carateen* expose individual quality links (`1080p`, `720p`, `480p`, `360p`, `Auto`) across all players and downloaders.

#### 🎮 External Media Players
- **Fatal Crash Prevention (`ActivityNotFoundException`) (Issue #52, Build 75)**:
  - Wrapped `externalPlayerResult.launch(intent)` and `context.startActivity(intent)` in defensive `try-catch` blocks to prevent crashes when an external player is missing, uninstalled, or restricted.
  - Added user-friendly toast notifications (*"Could not launch external player"*) and automatic graceful fallback to the Android system app chooser (`Intent.createChooser`).
- **External Player Whitelist & Preference Sanitization (Issue #52, Build 75)**:
  - Removed unsupported `CloudStream` entries from `externalPlayers` and `getComponent()` as CloudStream does not provide a standalone third-party video player activity.
  - Added automatic preference sanitization to reset invalid/uninstalled external player choices back to "None" on launch and settings display.
- **Support for `mpvRx` Player (Issue #53, Build 76)**:
  - Added official integration for **mpvRx** (`app.gyrolet.mpvrx`, `com.gyrolet.mpvrx`, `app.gyrolet.mpvrx.debug`) in **Settings -> Player -> Preferred external player**.
  - Directly targets `app.gyrolet.mpvrx.ui.player.PlayerActivity` with complete video stream parameters, headers, subtitles, and last-seen position sync.

#### 🔍 Browse & Sources
- **Preserve Source Search Query Across Navigation (Issue #54, Build 77)**:
  - In **CloudStream Sources**, switched search query state to `rememberSaveable` so the entered query is preserved when clicking into a source's catalog and pressing Back.
  - In **Anime Sources**, hoisted `AnimeSourcesScreenModel` to the `BrowseTab` level, ensuring the screen model and filtered search state persist when returning from a source.
  - Search query is now only cleared when explicitly clicking the **Clear ("X")** button or deleting the text.
- **In-Source Search on Discover Tab (Build 71)**:
  - Added ability to search within individual sources directly from the Discover screen.

#### ⚡ ExoPlayer Engine, Subtitles & Servers
- **Active Server Visual Indicator & Auto-Failover (Issue #49, Build 68)**:
  - Added a distinct visual indicator for the actively playing server/hoster in the ExoPlayer server sheet.
  - Implemented automatic server failover: if a server link errors out or fails to load, ExoPlayer automatically attempts playback from the next available hoster server.
- **Real-Time Episode Options Discovery & Direct External Player (Issue #50, Build 69)**:
  - Enabled real-time link discovery inside the Episode Options dialog, allowing users to pick server and quality before playing.
  - Supported passing chosen servers and qualities directly to external players from the dialog.
- **Cross-Source Playback State Reset (Build 66)**:
  - Reset anime, episode, and hoster states when switching between anime from different sources, eliminating stale state carry-overs.
- **External Subtitle Rendering & Stability (Builds 70, 71, 73)**:
  - Fixed ExoPlayer external subtitle rendering (WebVTT / SRT / ASS parsing and display).
  - Resolved network timeout errors and crashes when adding external subtitles.

#### 🎨 UI & Stability Fixes
- **Manga/Chapter `memo` Persistence & Auto-Refresh Fallback (Build 85)**:
  - **SQLite `memo` Column (`33.sqm`, `mangas.sq`, `chapters.sq`)**: Added `memo BLOB AS JsonObject NOT NULL DEFAULT '{}'` to `mangas` and `chapters` tables (matching `animes` and `episodes`) and wired `memo` across `domain`, `data`, `AppModule`, `SyncChaptersWithSource`, and backup restoration (`BackupChapter` / `MangaRestorer`) so extension metadata is preserved on save.
  - **Auto-Refresh Chapter List (`HttpPageLoader`)**: Updated `HttpPageLoader` to match both `"Refresh Chapter List"` and `"Refresh the chapter list"` case-insensitively and automatically re-sync the chapter list via `SyncChaptersWithSource` before retrying `source.getPageList`, resolving chapter open failures on Asura Scans, Vortex Scans, AllManga, and similar extensions.
- **Multi-Season Anime Presentation & Season Selector (Issue #57, Build 84)**:
  - Preserved CloudStream `Episode.season` and `TvSeriesLoadResponse.seasonNames` metadata in `CloudStreamAnimeSource.toSEpisode()`.
  - Added a Material 3 Season Selector dropdown (`Season 1`, `Season 2`, ..., plus `All seasons`) right below the episode header on `AnimeScreen`, filtering episodes and episode counts cleanly by season while defaulting to the season of the next unwatched episode.
- **Video Player Loading Stage & Live Speed Indicator (Issue #56, Build 83)**:
  - Added `PlayerLoadingStatusBadge` (`TrafficStats.getUidRxBytes` sampled once per second only while buffering) across both MPV Player and ExoPlayer.
  - Displays clear progress stages underneath the loading spinner (e.g., *"Searching servers…"*, *"Connecting to [Server]…"*, *"Switching to [Server]…"*, or *"Buffering (42%)… • 2.1 MB/s"*).
- **1DM-Style Episode Download Stats & Anti-IP-Block Pacing (Issue #55, Build 82)**:
  - Added lightweight throttled (`800ms`) tracking of downloaded size, estimated total size, smoothed download speed (`KB/s` / `MB/s`), and ETA in `AnimeDownloader` and displayed them in the Download Queue UI and notifications.
  - Reduced download notification polling from `50ms` to `1000ms` to eliminate SystemUI lag.
  - Added **Settings -> Downloads -> Anime download mode** (`Auto (Recommended)`, `Safe (Anti-Ban)`, `Fast`) with automatic per-source cooldowns, per-source concurrency limits, `3x` backoff on HTTP `429`/`403` rate-limits, and `-http_persistent 1` in FFmpeg for faster HLS downloads with fewer TLS handshakes.
- **ExoPlayer ➔ MPV Failover Timestamp Persistence (Build 81)**:
  - Synchronously persists exact playback position (`saveProgress(blocking = true)`) before handing off from ExoPlayer to MPV on codec failures, ensuring zero lost seconds.
- **Configurable Image Disk Cache Size (Build 81)**:
  - Added **Settings -> Advanced -> Data -> Image disk cache size** (`100 MB` default, up to `1 GB`) for Coil 3's disk cache.
- **Safe Decoder Profile for Legacy SoCs (Build 81)**:
  - Added **Settings -> Player -> Decoder -> Safe decoder profile (Legacy SoCs)** to limit MPV hardware decoding to H.264 and cap decoder threads to `4`, preventing green screens or thermal throttling on older devices.
- **ExoPlayer Preferred Audio & Subtitle Auto-Selection (Build 80)**:
  - Wired `AudioPreferences.preferredAudioLanguages()` and `SubtitlePreferences.preferredSubLanguages()` into ExoPlayer's `TrackSelectionParameters`, ensuring multi-audio/multi-sub streams automatically start in your preferred languages.
- **ExoPlayer Backward Seek Cache (Build 80)**:
  - Configured `DefaultLoadControl` with a 30-second keyframe-retained back buffer for instant backward seeking without network re-buffering.
- **System Memory Trimming (`onTrimMemory`) (Build 80)**:
  - Implemented `Application.onTrimMemory` in `App.kt` to dynamically trim or clear Coil 3's `MemoryCache` under memory pressure, preventing OS background kills and OOMs on 4GB–6GB RAM devices.
- **Transient Network Error Auto-Retry (Build 80)**:
  - Added `TransientErrorRetryInterceptor` to `NetworkHelper` to automatically retry idempotent `GET` requests once after 800ms on transient `502`/`503`/`504` gateway errors or socket/connect timeouts.
- **Auto-Refresh Extensions on Network Reconnect (Build 80)**:
  - Wired `networkStateFlow` into both `AnimeExtensionsScreenModel` and `MangaExtensionsScreenModel` to automatically refresh repositories when transitioning from offline to online.
- **CloudStream Provider Timeout Guard (Build 79)**:
  - Enforced strict 15s timeouts for search/main page feeds and 20s for detail extraction in `CloudStreamAnimeSource`.
  - Prevents unresponsive, dead, or Cloudflare-blocked provider websites from hanging Global Search or catalog views.
- **ExoPlayer to MPV Codec/Format Failover (Build 79)**:
  - Automatically fails over to MPV Player when ExoPlayer hits fatal hardware decoder initialization failures (`ERROR_CODE_DECODER_INIT_FAILED`, unsupported containers/manifests).
  - Guarantees seamless video playback regardless of whether a device has hardware decoders for exotic codecs (10-bit HEVC, Hi10P).
- **TorrServer Idle Auto-Shutdown (Build 79)**:
  - Added cleanup handlers in `PlayerActivity` and `ExoPlayerActivity` to terminate the TorrServer daemon upon exiting the player if no background torrent downloads are active, saving CPU and battery.
- **Backup Restorer Validation & Source Map Fix (Build 79)**:
  - Fixed `animeSourceMapping` typo in `BackupRestorer` and added dry-run validation to guard against corrupted or empty backup files.
- **Manga Reader Data Saver ("Load Ahead Only on Wi-Fi", Build 78)**:
  - Added new toggle in Reader Settings to restrict pre-fetching chapters and pages ahead strictly to Wi-Fi networks.
  - Prevents unwanted cellular data consumption by loading only immediate reading view on mobile networks.
- **One-Tap Player Speed Preset Cycling (Build 78)**:
  - Short-tapping the playback speed icon now cycles through user-defined speed presets in ascending order with wraparound.
  - Leaves the saved default playback speed setting untouched (unlike previous short-clicks that accidentally overwrote it).
- **SQLite Database Concurrency & Locking Hardening (Build 78)**:
  - Configured 5-second `busy_timeout` pragma across both Manga and Anime SQLite databases.
  - Eliminates `SQLiteDatabaseLockedException` during concurrent background library updates and active reading/playback.
- **Discover Tab Feed Stability (Build 71)**:
  - Resolved UI shifting / jittering when scrolling through popular releases in Discover.
- **Discover Search Bar State (Builds 72, 73)**:
  - Fixed clickability and open state of the Discover search bar and restored Global Search execution.
- **Display Mode NPE & Grid Item Key Fixes (Builds 67, 72)**:
  - Fixed `relatedAnimeDisplayMode` NullPointerExceptions on AnimeScreen.
  - Fixed duplicate grid item keys that could crash Compose lazy grids.

---

### 📦 Build History for v1.0.5

| Build | Commit | Description |
|---|---|---|
| **Build 85** | `56f545b` | Manga/chapter `memo` DB persistence & `HttpPageLoader` auto-refresh fallback for "Refresh Chapter List" errors |
| **Build 84** | `258b382` | Multi-season CloudStream metadata preservation & Season Selector dropdown on AnimeScreen (Issue #57) |
| **Build 83** | `8f87e95` | Live video player loading stage & network speed indicator in MPV and ExoPlayer (Issue #56) |
| **Build 82** | `7d6b2b0` | 1DM-style episode download stats (size, speed, ETA), anti-IP-block pacing, and automatic download profiles (Issue #55) |
| **Build 81** | `d1ed9c0` | Failover timestamp persistence, configurable image cache size, safe decoder profile |
| **Build 80** | `5c8c637` | ExoPlayer preferred languages & 30s seek cache, onTrimMemory, HTTP 502/503/504 retry, reconnect auto-refresh |
| **Build 79** | `50f4db9` | CloudStream timeout guards, ExoPlayer-to-MPV failover, TorrServer auto-shutdown, backup validation |
| **Build 78** | `ab0c75b` | Load ahead only on Wi-Fi, one-tap player speed cycling, SQLite concurrency hardening |
| **Build 77** | `e34ab87` | Preserve source search query across navigation (Issue #54) |
| **Build 76** | `6a7e0dc` | Add mpvRx external player support (Issue #53) |
| **Build 75** | `48d8cf7` | Fix external player ActivityNotFoundException crash and sanitize preferences (Issue #52) |
| **Build 74** | `5124680` | Improve MPV Player quality selection, buffering speed, and HLS variant expansion (Issue #51) |
| **Build 73** | `7be5781` | Fix ExoPlayer external subtitles crash and Discover global search |
| **Build 72** | `1221b21` | Fix AnimeScreen relatedDisplayMode NPE and Discover search bar open state |
| **Build 71** | `a4728a8` | Fix external subtitle loading, Discover feed stability, and source search |
| **Build 70** | `d0fd4a3` | Fix ExoPlayer external subtitle rendering and optimize Discover source feed caching |
| **Build 69** | `56c68bb` | Fix episode options dialog real-time discovery and external player playback (Issue #50) |
| **Build 68** | `aeac447` | Improve ExoPlayer: active server indicator and auto-failover (Issue #49) |
| **Build 67** | `394d365` | Browse/Details: fix duplicate grid item keys and relatedAnimeDisplayMode NPE |
| **Build 66** | `27225ac` | ExoPlayer: reset anime, episode, and hoster states on cross-source playback |

---

## [v1.0.4] - 2026-11-25

### Highlights in v1.0.4 (Builds 53 – 65):
- Fixed `IndexOutOfBoundsException` when dynamically toggling Browse tab sections (Build 65).
- Real-time dynamic server extraction and live quality streaming in video player (#48, Build 64).
- High-performance source resolution, concurrent in-memory metadata caching, and navigation optimizations (#47, Build 63).
- Inline search box for CloudStream Sources and native Anime Sources (#46, Build 62).
- Live Stream Info HUD ("Stats for Nerds") and external subtitle loader in ExoPlayer (#45, Build 61).
- MPV Player stream buffer expansion (16MB buffer, 25s readahead) and anti-distortion fixes (#44, Build 60).
- ExoPlayer Picture-in-Picture (PiP) overhaul with instant audio termination on dismiss (Builds 54–55).
- Seamless episode transitions without SurfaceView detachment (Builds 58–59).
- Media3 AudioAttributes, audio focus routing, and volume stream binding (Build 57).

---

## [v1.0.3] - 2026-11-20

### Highlights in v1.0.3:
- Introduced built-in native ExoPlayer engine alongside MPV Player with full CloudStream UI overlay (#36, Build 50).
- Fast-start eager stream yielding (#38, #40, Build 49).
- Press-and-hold 2.0x playback speed boost with HUD indicator (Build 53).
- Memory downsampling optimizations for source icons preventing OOM crashes (#34, Build 48).
- Alphabetical sorting for unpinned sources with pinned sources anchored at top (#41, Build 51).

---

## [v1.0.2] - 2026-11-15

### Highlights in v1.0.2:
- CloudStream 3 plugin integration and provider loader.
- Repository management for CloudStream extensions.
- Hybrid content library support for anime streaming and manga reading.
- Bi-directional tracking integration (AniList, MAL, Kitsu, Shikimori, Bangumi).
- Material 3 dynamic color theming and AMOLED pitch-black dark mode.
