package eu.kanade.tachiyomi.ui.home.discover

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDiscoverScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getEnabledAnimeSources: GetEnabledAnimeSources = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
) : StateScreenModel<AnimeDiscoverScreenModel.State>(State()) {

    private var loadFeedJob: Job? = null

    init {
        screenModelScope.launchIO {
            getEnabledAnimeSources.subscribe().collectLatest { enabledSources ->
                val catalogueSources = enabledSources
                    .distinctBy { it.id }
                    .filter { domainSource ->
                        sourceManager.get(domainSource.id) is AnimeCatalogueSource
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
    }

    fun refresh() {
        val currentId = state.value.currentSource?.id ?: return
        loadSourceFeed(currentId, isRefresh = true)
    }

    private fun loadSourceFeed(sourceId: Long, isRefresh: Boolean = false) {
        val catalogueSource = sourceManager.get(sourceId) as? AnimeCatalogueSource
        if (catalogueSource == null) {
            mutableState.update { it.copy(isLoading = false, isRefreshing = false) }
            return
        }

        loadFeedJob?.cancel()
        loadFeedJob = screenModelScope.launchIO {
            mutableState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    currentSource = catalogueSource,
                    errorMessage = null,
                )
            }

            try {
                val popularJob = async {
                    try {
                        val page = catalogueSource.getPopularAnime(1)
                        page.animes.map { networkToLocalAnime.await(it.toDomainAnime(sourceId)) }.distinctBy { it.id }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Failed to load popular anime from source $sourceId" }
                        emptyList()
                    }
                }

                val latestJob = async {
                    try {
                        if (catalogueSource.supportsLatest) {
                            val page = catalogueSource.getLatestUpdates(1)
                            page.animes.map { networkToLocalAnime.await(it.toDomainAnime(sourceId)) }.distinctBy { it.id }
                        } else {
                            emptyList()
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Failed to load latest anime from source $sourceId" }
                        emptyList()
                    }
                }

                val popular = popularJob.await()
                val latest = latestJob.await()

                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        popularAnime = popular.toImmutableList(),
                        latestAnime = latest.toImmutableList(),
                        errorMessage = if (popular.isEmpty() && latest.isEmpty()) "Failed to load content" else null,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Error loading feed for source $sourceId: ${e.message}" }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Failed to load feed",
                    )
                }
            }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val sources: ImmutableList<AnimeSource> = persistentListOf(),
        val currentSource: AnimeCatalogueSource? = null,
        val popularAnime: ImmutableList<Anime> = persistentListOf(),
        val latestAnime: ImmutableList<Anime> = persistentListOf(),
        val errorMessage: String? = null,
    )
}
