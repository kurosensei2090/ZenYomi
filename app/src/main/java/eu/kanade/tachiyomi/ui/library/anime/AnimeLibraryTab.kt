package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.home.discover.AnimeDiscoverContent
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.home.discover.AnimeDiscoverScreenModel
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.i18n.MR
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.injectLazy

data object AnimeLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_anime_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(
                R.drawable.anim_animelibrary_leave,
            )
            return TabOptions(
                index = 0u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { AnimeLibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            val started = AnimeLibraryUpdateJob.startNow(context, category)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        suspend fun openEpisode(episode: Episode) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
        }

        val defaultTitle = stringResource(AYMR.strings.label_anime_library)

        val discoverScreenModel = rememberScreenModel { AnimeDiscoverScreenModel() }
        val discoverState by discoverScreenModel.state.collectAsState()
        val sourcePreferences: SourcePreferences by injectLazy()
        var currentMode by sourcePreferences.animeHomeScreenMode().asState(scope)

        Scaffold(
            topBar = { scrollBehavior ->
                Column {
                    val title = if (currentMode == "DISCOVER") {
                        LibraryToolbarTitle(
                            text = stringResource(AYMR.strings.tab_discover),
                        )
                    } else {
                        state.getToolbarTitle(
                            defaultTitle = defaultTitle,
                            defaultCategoryTitle = stringResource(MR.strings.label_default),
                            page = screenModel.activeCategoryIndex,
                        )
                    }
                    val tabVisible = state.showCategoryTabs && state.categories.size > 1 && currentMode == "LIBRARY"
                    LibraryToolbar(
                        hasActiveFilters = state.hasActiveFilters && currentMode == "LIBRARY",
                        selectedCount = if (currentMode == "LIBRARY") state.selection.size else 0,
                        title = title,
                        onClickUnselectAll = screenModel::clearSelection,
                        onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                        onClickInvertSelection = {
                            screenModel.invertSelection(
                                screenModel.activeCategoryIndex,
                            )
                        },
                        onClickFilter = screenModel::showSettingsDialog,
                        onClickRefresh = {
                            if (currentMode == "DISCOVER") {
                                discoverScreenModel.refresh()
                            } else {
                                onClickRefresh(
                                    state.categories.getOrNull(screenModel.activeCategoryIndex),
                                )
                            }
                        },
                        onClickGlobalUpdate = { onClickRefresh(null) },
                        onClickOpenRandomEntry = {
                            scope.launch {
                                val randomItem = screenModel.getRandomAnimelibItemForCurrentCategory()
                                if (randomItem != null) {
                                    navigator.push(AnimeScreen(randomItem.libraryAnime.anime.id))
                                } else {
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(MR.strings.information_no_entries_found),
                                    )
                                }
                            }
                        },
                        searchQuery = state.searchQuery,
                        onSearchQueryChange = screenModel::search,
                        scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
                    )

                    if (state.searchQuery.isNullOrEmpty() && state.selection.isEmpty()) {
                        PrimaryTabRow(
                            selectedTabIndex = if (currentMode == "DISCOVER") 0 else 1,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Tab(
                                selected = currentMode == "DISCOVER",
                                onClick = { currentMode = "DISCOVER" },
                                text = {
                                    Text(
                                        text = stringResource(AYMR.strings.tab_discover),
                                        fontWeight = if (currentMode == "DISCOVER") FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Explore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Tab(
                                selected = currentMode == "LIBRARY",
                                onClick = { currentMode = "LIBRARY" },
                                text = {
                                    Text(
                                        text = stringResource(AYMR.strings.tab_library),
                                        fontWeight = if (currentMode == "LIBRARY") FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CollectionsBookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (currentMode == "LIBRARY") {
                    LibraryBottomActionMenu(
                        visible = state.selectionMode,
                        onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                        onMarkAsViewedClicked = { screenModel.markSeenSelection(true) },
                        onMarkAsUnviewedClicked = { screenModel.markSeenSelection(false) },
                        onDownloadClicked = screenModel::runDownloadActionSelection
                            .takeIf { state.selection.fastAll { !it.anime.isLocal() } },
                        onDeleteClicked = screenModel::openDeleteAnimeDialog,
                        isManga = false,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                currentMode == "DISCOVER" -> {
                    AnimeDiscoverContent(
                        state = discoverState,
                        contentPadding = contentPadding,
                        onRefresh = discoverScreenModel::refresh,
                        onAnimeClick = { navigator.push(AnimeScreen(it.id, true)) },
                        onAnimeLongClick = { anime ->
                            navigator.push(AnimeScreen(anime.id, true))
                        },
                        onMorePopularClick = {
                            discoverState.currentSource?.let {
                                navigator.push(
                                    BrowseAnimeSourceScreen(
                                        sourceId = it.id,
                                        listingQuery = GetRemoteAnime.QUERY_POPULAR,
                                    ),
                                )
                            }
                        },
                        onMoreLatestClick = {
                            discoverState.currentSource?.let {
                                navigator.push(
                                    BrowseAnimeSourceScreen(
                                        sourceId = it.id,
                                        listingQuery = GetRemoteAnime.QUERY_LATEST,
                                    ),
                                )
                            }
                        },
                        onSelectSource = discoverScreenModel::selectSource,
                        onExploreExtensionsClick = {
                            scope.launch {
                                HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true, anime = true))
                            }
                        },
                    )
                }
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    var isRefreshing by remember { mutableStateOf(false) }
                    PullRefresh(
                        refreshing = isRefreshing,
                        onRefresh = {
                            onClickRefresh(null)
                            scope.launch {
                                isRefreshing = true
                                delay(1.seconds)
                                isRefreshing = false
                            }
                        },
                        enabled = true,
                        indicatorPadding = contentPadding,
                    ) {
                        EmptyScreen(
                            stringRes = MR.strings.information_empty_library,
                            modifier = Modifier.padding(contentPadding),
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = AYMR.strings.tab_discover,
                                    icon = Icons.Outlined.Explore,
                                    onClick = { currentMode = "DISCOVER" },
                                ),
                                EmptyScreenAction(
                                    stringRes = MR.strings.getting_started_guide,
                                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                    onClick = { handler.openUri(GETTING_STARTED_URL) },
                                ),
                            ),
                        )
                    }
                }
                else -> {
                    AnimeLibraryContent(
                        categories = state.categories,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = contentPadding,
                        currentPage = { screenModel.activeCategoryIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                        onAnimeClicked = remember(navigator) {
                            { navigator.push(AnimeScreen(it)) }
                        },
                        onContinueWatchingClicked = { it: LibraryAnime ->
                            scope.launchIO {
                                val episode = screenModel.getNextUnseenEpisode(it.anime)
                                if (episode != null) openEpisode(episode)
                            }
                            Unit
                        }.takeIf { state.showAnimeContinueButton },
                        onToggleSelection = screenModel::toggleSelection,
                        onToggleRangeSelection = {
                            screenModel.toggleRangeSelection(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = onClickRefresh,
                        onGlobalSearchClicked = {
                            navigator.push(
                                GlobalAnimeSearchScreen(screenModel.state.value.searchQuery ?: ""),
                            )
                        },
                        getNumberOfAnimeForCategory = { state.getAnimeCountForCategory(it) },
                        getDisplayMode = { screenModel.getDisplayMode() },
                        getColumnsForOrientation = {
                            screenModel.getColumnsPreferenceForCurrentOrientation(
                                it,
                            )
                        },
                    ) { state.getAnimelibItemsByPage(it) }
                }
            }
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is AnimeLibraryScreenModel.Dialog.SettingsSheet -> run {
                val category = state.categories.getOrNull(screenModel.activeCategoryIndex)
                if (category == null) {
                    onDismissRequest()
                    return@run
                }
                AnimeLibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                    category = category,
                )
            }
            is AnimeLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        screenModel.clearSelection()
                        navigator.push(CategoriesTab)
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setAnimeCategories(dialog.anime, include, exclude)
                    },
                )
            }
            is AnimeLibraryScreenModel.Dialog.DeleteAnime -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.anime.any(Anime::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteAnime, deleteEpisode ->
                        screenModel.removeAnimes(dialog.anime, deleteAnime, deleteEpisode)
                        screenModel.clearSelection()
                    },
                    isManga = false,
                )
            }
            null -> {}
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
