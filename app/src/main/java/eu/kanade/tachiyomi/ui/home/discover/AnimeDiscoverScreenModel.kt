package eu.kanade.tachiyomi.ui.home.discover

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.domain.source.anime.model.Pins
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class AnimeDiscoverScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
) : StateScreenModel<AnimeDiscoverScreenModel.State>(State()) {

    data class FeedCache(
        val popular: List<Anime>,
        val latest: List<Anime>,
    )

    private val feedCache = ConcurrentHashMap<Long, FeedCache>()
    private var loadFeedJob: Job? = null
    private var searchJob: Job? = null

    init {
        screenModelScope.launchIO {
            combine(
                sourceManager.sources,
                sourcePreferences.pinnedAnimeSources().changes(),
                sourcePreferences.disabledAnimeSources().changes(),
            ) { allSources, pinnedSourceIds, disabledSources ->
                allSources
                    .filterIsInstance<AnimeCatalogueSource>()
                    .filterNot { it.id.toString() in disabledSources }
                    .distinctBy { it.id }
                    .map { cs ->
                        val isPinned = cs.id.toString() in pinnedSourceIds
                        AnimeSource(
                            id = cs.id,
                            lang = cs.lang,
                            name = cs.name,
                            supportsLatest = cs.supportsLatest,
                            isStub = false,
                            pin = if (isPinned) Pins.pinned else Pins.unpinned,
                        )
                    }
                    .sortedWith(
                        compareByDescending<AnimeSource> { Pin.Actual in it.pin }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.removeSuffix(" [CS]").trim() },
                    )
            }.collectLatest { catalogueSources ->
                if (catalogueSources.isEmpty()) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLatestLoading = false,
                            sources = persistentListOf(),
                            currentSource = null,
                            popularAnime = persistentListOf(),
                            latestAnime = persistentListOf(),
                            errorMessage = null,
                        )
                    }
                    return@collectLatest
                }

                mutableState.update { it.copy(sources = catalogueSources.toImmutableList()) }

                val savedSourceId = sourcePreferences.preferredHomeSource().get()
                val targetSourceId = if (catalogueSources.any { it.id == savedSourceId }) {
                    savedSourceId
                } else {
                    catalogueSources.firstOrNull()?.id ?: 0L
                }

                if (targetSourceId != 0L) {
                    val currentId = state.value.currentSource?.id
                    if (currentId != targetSourceId || state.value.popularAnime.isEmpty()) {
                        loadSourceFeed(targetSourceId)
                    }
                } else {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLatestLoading = false,
                            currentSource = null,
                            popularAnime = persistentListOf(),
                            latestAnime = persistentListOf(),
                        )
                    }
                }
            }
        }
    }

    fun selectSource(sourceId: Long) {
        sourcePreferences.preferredHomeSource().set(sourceId)
        loadSourceFeed(sourceId)
        val currentQuery = state.value.searchQuery
        if (!currentQuery.isNullOrBlank()) {
            search(currentQuery)
        }
    }

    fun togglePin(sourceId: Long) {
        sourcePreferences.pinnedAnimeSources().getAndSet { pinned ->
            if (sourceId.toString() in pinned) {
                pinned - sourceId.toString()
            } else {
                pinned + sourceId.toString()
            }
        }
    }

    fun refresh() {
        val currentId = state.value.currentSource?.id ?: return
        val currentQuery = state.value.searchQuery
        if (!currentQuery.isNullOrBlank()) {
            search(currentQuery)
        } else {
            loadSourceFeed(currentId, isRefresh = true)
        }
    }

    fun search(query: String?) {
        searchJob?.cancel()
        if (query == null) {
            mutableState.update {
                it.copy(
                    searchQuery = null,
                    isSearching = false,
                    searchResults = persistentListOf(),
                )
            }
            return
        }

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            mutableState.update {
                it.copy(
                    searchQuery = query,
                    isSearching = false,
                    searchResults = persistentListOf(),
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                searchQuery = query,
                isSearching = true,
            )
        }

        searchJob = screenModelScope.launchIO {
            delay(400)
            val source = state.value.currentSource
            if (source == null) {
                mutableState.update { it.copy(isSearching = false) }
                return@launchIO
            }

            try {
                val page = source.getSearchAnime(1, trimmed, AnimeFilterList())
                val results = coroutineScope {
                    page.animes.map { anime ->
                        async { networkToLocalAnime.await(anime.toDomainAnime(source.id)) }
                    }.awaitAll().distinctBy { it.id }
                }
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results.toImmutableList(),
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to search source ${source.id} for '$trimmed'" }
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = persistentListOf(),
                    )
                }
            }
        }
    }

    private fun loadSourceFeed(sourceId: Long, isRefresh: Boolean = false) {
        val catalogueSource = sourceManager.get(sourceId) as? AnimeCatalogueSource
        if (catalogueSource == null) {
            mutableState.update { it.copy(isLoading = false, isRefreshing = false, isLatestLoading = false) }
            return
        }

        // Instant feed display from in-memory cache if available
        val cached = feedCache[sourceId]
        if (cached != null && !isRefresh) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLatestLoading = false,
                    currentSource = catalogueSource,
                    popularAnime = cached.popular.toImmutableList(),
                    latestAnime = cached.latest.toImmutableList(),
                    errorMessage = null,
                )
            }
        }

        loadFeedJob?.cancel()
        loadFeedJob = screenModelScope.launchIO {
            val hasCache = cached != null && !isRefresh
            val supportsLatest = catalogueSource.supportsLatest
            mutableState.update {
                it.copy(
                    isLoading = !hasCache && !isRefresh,
                    isRefreshing = isRefresh,
                    isLatestLoading = supportsLatest && (!hasCache || it.latestAnime.isEmpty()),
                    currentSource = catalogueSource,
                    popularAnime = if (hasCache) it.popularAnime else persistentListOf(),
                    latestAnime = if (hasCache) it.latestAnime else persistentListOf(),
                    errorMessage = null,
                )
            }

            var popularResult = cached?.popular.orEmpty()
            var latestResult = cached?.latest.orEmpty()

            val popularJob = launch {
                try {
                    val page = catalogueSource.getPopularAnime(1)
                    val list = coroutineScope {
                        page.animes.map { anime ->
                            async { networkToLocalAnime.await(anime.toDomainAnime(sourceId)) }
                        }.awaitAll().distinctBy { it.id }
                    }
                    if (list.isNotEmpty()) {
                        popularResult = list
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                popularAnime = list.toImmutableList(),
                            )
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Failed to load popular anime from source $sourceId" }
                } finally {
                    mutableState.update { it.copy(isLoading = false) }
                }
            }

            val latestJob = launch {
                if (supportsLatest) {
                    try {
                        val page = catalogueSource.getLatestUpdates(1)
                        val list = coroutineScope {
                            page.animes.map { anime ->
                                async { networkToLocalAnime.await(anime.toDomainAnime(sourceId)) }
                            }.awaitAll().distinctBy { it.id }
                        }
                        if (list.isNotEmpty()) {
                            latestResult = list
                            mutableState.update {
                                it.copy(
                                    isLatestLoading = false,
                                    latestAnime = list.toImmutableList(),
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Failed to load latest anime from source $sourceId" }
                    } finally {
                        mutableState.update { it.copy(isLatestLoading = false) }
                    }
                } else {
                    mutableState.update { it.copy(isLatestLoading = false) }
                }
            }

            popularJob.join()
            latestJob.join()

            if (popularResult.isNotEmpty() || latestResult.isNotEmpty()) {
                feedCache[sourceId] = FeedCache(popularResult, latestResult)
            }

            mutableState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLatestLoading = false,
                    popularAnime = if (popularResult.isNotEmpty()) popularResult.toImmutableList() else it.popularAnime,
                    latestAnime = if (latestResult.isNotEmpty()) latestResult.toImmutableList() else it.latestAnime,
                    errorMessage = if (it.popularAnime.isEmpty() && it.latestAnime.isEmpty()) "Failed to load content" else null,
                )
            }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isLatestLoading: Boolean = false,
        val sources: ImmutableList<AnimeSource> = persistentListOf(),
        val currentSource: AnimeCatalogueSource? = null,
        val popularAnime: ImmutableList<Anime> = persistentListOf(),
        val latestAnime: ImmutableList<Anime> = persistentListOf(),
        val errorMessage: String? = null,
        val searchQuery: String? = null,
        val isSearching: Boolean = false,
        val searchResults: ImmutableList<Anime> = persistentListOf(),
    )
}
