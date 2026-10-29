package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.cloudstream.csExtensionsTab
import eu.kanade.tachiyomi.ui.browse.cloudstream.csSourcesTab
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    // TODO: Find a way to let it open Global Anime/Manga Search depending on what Tab(e.g. Anime/Manga Source Tab) is open
    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    enum class SubTab {
        AnimeSources,
        MangaSources,
        CsSources,
        AnimeExtensions,
        MangaExtensions,
        CsExtensions,
    }

    private val switchToSubTabChannel = Channel<SubTab>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToSubTabChannel.trySend(SubTab.MangaExtensions)
    }

    fun showAnimeExtension() {
        switchToSubTabChannel.trySend(SubTab.AnimeExtensions)
    }

    fun showCsExtension() {
        switchToSubTabChannel.trySend(SubTab.CsExtensions)
    }

    fun showCsSources() {
        switchToSubTabChannel.trySend(SubTab.CsSources)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val showAnime by sourcePreferences.showAnimeInBrowse().collectAsState()
        val showManga by sourcePreferences.showMangaInBrowse().collectAsState()
        val showCloudStream by sourcePreferences.showCloudStreamInBrowse().collectAsState()

        // Hoisted for extensions tab's search bar
        val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsState()

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsState()

        val animeSources = if (showAnime) animeSourcesTab() else null
        val mangaSources = if (showManga) mangaSourcesTab() else null
        val csSources = if (showCloudStream) csSourcesTab() else null
        val animeExtensions = if (showAnime) animeExtensionsTab(animeExtensionsScreenModel) else null
        val mangaExtensions = if (showManga) mangaExtensionsTab(mangaExtensionsScreenModel) else null
        val csExtensions = if (showCloudStream) csExtensionsTab() else null
        val migrateAnime = if (showAnime) migrateAnimeSourceTab() else null
        val migrateManga = if (showManga) migrateMangaSourceTab() else null

        val tabs = listOfNotNull(
            animeSources,
            mangaSources,
            csSources,
            animeExtensions,
            mangaExtensions,
            csExtensions,
            migrateAnime,
            migrateManga,
        ).ifEmpty { listOf(animeSources ?: animeSourcesTab()) }.toPersistentList()

        val state = rememberPagerState { tabs.size }

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            mangaSearchQuery = mangaExtensionsState.searchQuery,
            onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
            animeSearchQuery = animeExtensionsState.searchQuery,
            onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
            scrollable = true,
        )
        LaunchedEffect(tabs) {
            switchToSubTabChannel.receiveAsFlow()
                .collectLatest { subTab ->
                    val targetTitleRes = when (subTab) {
                        SubTab.AnimeSources -> AYMR.strings.label_anime_sources
                        SubTab.MangaSources -> AYMR.strings.label_manga_sources
                        SubTab.CsSources -> AYMR.strings.label_cs_sources
                        SubTab.AnimeExtensions -> AYMR.strings.label_anime_extensions
                        SubTab.MangaExtensions -> AYMR.strings.label_manga_extensions
                        SubTab.CsExtensions -> AYMR.strings.label_cs_extensions
                    }
                    val index = tabs.indexOfFirst { it.titleRes == targetTitleRes }
                    if (index in 0 until tabs.size) {
                        state.scrollToPage(index)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
