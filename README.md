<div align="center">

# ZenYomi (全読み)

### The Ultimate Unified Anime Streaming & Manga Reading Application for Android
*Taking the best of Tachiyomi and Aniyomi into one next-generation powerhouse.*

</div>

## Overview

**ZenYomi** (derived from Japanese **全** "Zen" meaning All/Omni/Universal and **読み** "Yomi" meaning Reading/Media Experience) brings together the full feature set of both **Tachiyomi** (the premier manga reader) and **Aniyomi** (the anime player and extension powerhouse) into a single, high-performance, modern Android application.

## Key Features

### 📖 Complete Manga Reader (Tachiyomi Foundation)
- **Multi-Mode Reader**: Left-to-Right, Right-to-Left (traditional Japanese manga), Vertical, and Webtoon continuous scrolling with seamless page stitching.
- **Visual Enhancements**: Automatic border cropping, custom background colors, split dual-page spread in landscape, color filters (inverted, grayscale, sepia, AMOLED black).
- **Offline Reading**: Batch download chapters for offline reading with background download manager.
- **Local Manga Support**: Read local archives (ZIP, CBZ, CBR, EPUB) and image folders.

### 🎬 Advanced Anime Video Player (Aniyomi Media Engine)
- **High-Performance Player Backend**: Built on Media3 / ExoPlayer with hardware decoding.
- **Intuitive Gesture Controls**:
  - Vertical swipe left: Screen brightness control
  - Vertical swipe right: Audio volume control
  - Horizontal swipe: Smooth seeking
  - Double tap left/right: 10s fast forward and rewind
  - Pinch-to-zoom: Aspect ratio modes (Fit, Stretch, Crop, 16:9, Original)
- **Audio & Subtitle Controls**: Multi-audio track switching (Dub/Sub) and styled subtitles (internal ASS/SSA/SRT/VTT and external subtitle tracks).
- **Stream Quality**: Direct quality selector (1080p, 720p, 480p, 360p, Auto).
- **Smart Features**: AniSkip integration (auto-skip anime openings and endings), Picture-in-Picture (PiP), and background audio playback.
- **Episode Downloads**: Download video streams directly for offline viewing.

### 🌐 Pluggable Source & Extension System
- **Dual Extension Support**: Compatible with both Manga extension repositories and Anime extension repositories.
- **Source Extractors**: Automatically extracts and streams video links from major anime hosts.
- **Migration Assistant**: Easily migrate manga and anime titles between different sources when a source goes offline.

### 📊 Universal Tracking & Sync
- **Tracking Services**: Full bi-directional sync with AniList, MyAnimeList (MAL), Kitsu, Shikimori, and Bangumi.
- Automatically marks chapters read and anime episodes watched as you finish them.

### 🎨 Modern Jetpack Compose UI
- Material 3 & Material You dynamic color theming.
- Pitch Black AMOLED Dark Mode for battery saving.
- Biometric & PIN App Lock for privacy.
- Automated JSON/Protobuf Backup & Restore across devices.

## How to Open and Build in Android Studio

1. Launch **Android Studio**.
2. Select **File > Open...** (or click **Open** on the Welcome screen).
3. Navigate to `D:\Anime Manga RW\ZenYomi` and click **OK**.
4. Android Studio will automatically recognize the Gradle build files and sync the project dependencies.
5. Select the **app** run configuration and an Android emulator or physical device (Android 8.0+), then press **Run** (`Shift + F10`).
