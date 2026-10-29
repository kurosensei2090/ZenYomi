package eu.kanade.tachiyomi.source.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
    private val sourceRepository: AnimeStubSourceRepository,
) : AnimeSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val downloadManager: AnimeDownloadManager by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, AnimeSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubAnimeSource>()

    override val sources: Flow<List<AnimeSource>> = sourcesMapFlow.map { it.values.toList() }

    init {
        val cloudStreamManager = eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamManager.getInstance(context)

        scope.launch {
            combine(
                extensionManager.installedExtensionsFlow,
                cloudStreamManager.installedSourcesFlow,
            ) { extensions, csSources ->
                val mutableMap = ConcurrentHashMap<Long, AnimeSource>(
                    mapOf(
                        LocalAnimeSource.ID to LocalAnimeSource(
                            context,
                            Injekt.get(),
                            Injekt.get(),
                            Injekt.get(),
                            Injekt.get(),
                            Injekt.get(),
                        ),
                    ),
                )
                extensions.forEach { extension ->
                    extension.sources.forEach {
                        mutableMap[it.id] = it
                        registerStubSource(StubAnimeSource.from(it))
                    }
                }
                csSources.forEach { csSource ->
                    mutableMap[csSource.id] = csSource
                    registerStubSource(StubAnimeSource.from(csSource))
                }
                sourcesMapFlow.value = mutableMap
                _isInitialized.value = true
            }.collect()
        }

        scope.launch {
            sourceRepository.subscribeAllAnime()
                .collectLatest { sources ->
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): AnimeSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap[sourceKey] ?: run {
            val stub = StubAnimeSource(sourceKey, "", "")
            stubSourcesMap[sourceKey] = stub
            scope.launch {
                val realStub = createStubSource(sourceKey)
                stubSourcesMap[sourceKey] = realStub
            }
            stub
        }
    }

    override fun getAll(): List<AnimeSource> = sourcesMapFlow.value.values.toList()

    override fun getOnlineSources(): List<AnimeHttpSource> {
        return sourcesMapFlow.value.values.filterIsInstance<AnimeHttpSource>()
    }

    override fun getStubSources(): List<StubAnimeSource> {
        val onlineSourceIds = getAll().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubAnimeSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubAnimeSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubAnimeSource {
        sourceRepository.getStubAnimeSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubAnimeSource(id = id, lang = "", name = "")
    }
}
