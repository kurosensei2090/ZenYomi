package eu.kanade.tachiyomi.ui.browse.cloudstream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamAnimeSource
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamManager
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.csSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val manager = remember { CloudStreamManager.getInstance(context) }
    val badgeSources by manager.installedSourcesFlow.collectAsState()

    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val pinnedSourcesSet by sourcePreferences.pinnedAnimeSources().collectAsState()
    val lastUsedSourceId by sourcePreferences.lastUsedAnimeSource().collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_cs_sources,
        badgeNumber = if (badgeSources.isNotEmpty()) badgeSources.size else null,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAnimeSearchScreen()) },
            ),
            AppBar.Action(
                title = "Refresh",
                icon = Icons.Outlined.Refresh,
                onClick = { manager.reloadInstalledPlugins() },
            ),
        ),
        content = { contentPadding, _ ->
            val scope = rememberCoroutineScope()
            val sources by manager.installedSourcesFlow.collectAsState()
            val repos by manager.repositoriesFlow.collectAsState()
            val isLoading by manager.isLoadingFlow.collectAsState()
            val lastError by manager.lastErrorFlow.collectAsState()

            var selectedRepoUrl by remember { mutableStateOf<String?>(null) }

            val filteredSources = remember(sources, selectedRepoUrl, repos) {
                if (selectedRepoUrl == null) {
                    sources
                } else {
                    val normSelected = manager.normalizeRepoUrl(selectedRepoUrl!!).trimEnd('/')
                    sources.filter { source ->
                        val repo = manager.getRepoForPlugin(source.pluginName)
                        val repoNorm = repo?.url?.let { manager.normalizeRepoUrl(it).trimEnd('/') }
                        repoNorm == normSelected || repo?.url == selectedRepoUrl
                    }
                }
            }

            val pinnedSources = remember(filteredSources, pinnedSourcesSet) {
                filteredSources.filter { it.id.toString() in pinnedSourcesSet }
            }
            val lastUsedSource = remember(filteredSources, lastUsedSourceId, pinnedSourcesSet) {
                if (lastUsedSourceId.toString() !in pinnedSourcesSet) {
                    filteredSources.firstOrNull { it.id == lastUsedSourceId }
                } else {
                    null
                }
            }
            val otherSources = remember(filteredSources, pinnedSourcesSet, lastUsedSource) {
                filteredSources.filter {
                    it.id.toString() !in pinnedSourcesSet && it.id != lastUsedSource?.id
                }.sortedBy { it.name.lowercase() }
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

            fun browseSource(source: CloudStreamAnimeSource, query: String = GetRemoteAnime.QUERY_POPULAR) {
                sourcePreferences.lastUsedAnimeSource().set(source.id)
                navigator.push(BrowseAnimeSourceScreen(source.id, query))
            }

            PullRefresh(
                refreshing = isLoading,
                enabled = true,
                onRefresh = { manager.reloadInstalledPlugins() },
                indicatorPadding = contentPadding,
            ) {
                if (sources.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No CS Sources Installed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add a repository and install providers from the CS Extensions tab to start streaming anime & movies.\n\nSlide down anytime to refresh.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (!lastError.isNullOrBlank()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "Last Provider Notice",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lastError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = { BrowseTab.showCsExtension() },
                                ) {
                                    Text("Go to CS Extensions")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Repository Filter Bar (when multiple repositories connected)
                        if (repos.size > 1) {
                            item(key = "repo_filter_bar") {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedRepoUrl == null,
                                            onClick = { selectedRepoUrl = null },
                                            label = { Text("All (${sources.size})") },
                                        )
                                    }
                                    items(repos, key = { it.url }) { repo ->
                                        val count = sources.count { source ->
                                            manager.getRepoForPlugin(source.pluginName)?.url == repo.url
                                        }
                                        FilterChip(
                                            selected = selectedRepoUrl == repo.url,
                                            onClick = {
                                                selectedRepoUrl = if (selectedRepoUrl == repo.url) null else repo.url
                                            },
                                            label = { Text("${repo.cleanName} ($count)") },
                                        )
                                    }
                                }
                            }
                        }

                        // 1. PINNED SOURCES SECTION
                        if (pinnedSources.isNotEmpty()) {
                            item(key = "header_pinned") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${stringResource(MR.strings.pinned_sources)} (${pinnedSources.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                                val repo = remember(source.pluginName, repos) { manager.getRepoForPlugin(source.pluginName) }
                                CsSourceCard(
                                    source = source,
                                    isPinned = true,
                                    repoName = repo?.cleanName,
                                    manager = manager,
                                    onBrowse = { browseSource(source) },
                                    onLatest = if (source.supportsLatest) {
                                        { browseSource(source, GetRemoteAnime.QUERY_LATEST) }
                                    } else null,
                                    onTogglePin = { togglePin(source.id) },
                                    onDelete = {
                                        scope.launch {
                                            manager.uninstallPlugin(source.pluginName)
                                            context.toast("Deleted ${source.name.removeSuffix(" [CS]").trim()}")
                                        }
                                    },
                                )
                            }
                        }

                        // 2. LAST USED SOURCE SECTION
                        if (lastUsedSource != null) {
                            item(key = "header_last_used") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (pinnedSources.isNotEmpty()) 12.dp else 8.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(MR.strings.last_used_source),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            item(key = "last_used_${lastUsedSource.id}") {
                                val repo = remember(lastUsedSource.pluginName, repos) { manager.getRepoForPlugin(lastUsedSource.pluginName) }
                                CsSourceCard(
                                    source = lastUsedSource,
                                    isPinned = false,
                                    repoName = repo?.cleanName,
                                    manager = manager,
                                    onBrowse = { browseSource(lastUsedSource) },
                                    onLatest = if (lastUsedSource.supportsLatest) {
                                        { browseSource(lastUsedSource, GetRemoteAnime.QUERY_LATEST) }
                                    } else null,
                                    onTogglePin = { togglePin(lastUsedSource.id) },
                                    onDelete = {
                                        scope.launch {
                                            manager.uninstallPlugin(lastUsedSource.pluginName)
                                            context.toast("Deleted ${lastUsedSource.name.removeSuffix(" [CS]").trim()}")
                                        }
                                    },
                                )
                            }
                        }

                        // 3. ALL / OTHER SOURCES SECTION
                        item(key = "header_other") {
                            val activeRepo = repos.find { it.url == selectedRepoUrl }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (pinnedSources.isNotEmpty() || lastUsedSource != null) 12.dp else 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (activeRepo != null) {
                                        "${activeRepo.cleanName} Sources (${otherSources.size})"
                                    } else if (pinnedSources.isNotEmpty() || lastUsedSource != null) {
                                        "Installed Sources (${otherSources.size})"
                                    } else {
                                        "Installed CS Sources (${otherSources.size})"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (selectedRepoUrl != null) {
                                    TextButton(onClick = { selectedRepoUrl = null }) {
                                        Text("Show All")
                                    }
                                }
                            }
                        }

                        items(otherSources, key = { it.id }) { source ->
                            val repo = remember(source.pluginName, repos) { manager.getRepoForPlugin(source.pluginName) }
                            CsSourceCard(
                                source = source,
                                isPinned = false,
                                repoName = repo?.cleanName,
                                manager = manager,
                                onBrowse = { browseSource(source) },
                                onLatest = if (source.supportsLatest) {
                                    { browseSource(source, GetRemoteAnime.QUERY_LATEST) }
                                } else null,
                                onTogglePin = { togglePin(source.id) },
                                onDelete = {
                                    scope.launch {
                                        manager.uninstallPlugin(source.pluginName)
                                        context.toast("Deleted ${source.name.removeSuffix(" [CS]").trim()}")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CsSourceCard(
    source: CloudStreamAnimeSource,
    isPinned: Boolean,
    repoName: String?,
    manager: CloudStreamManager,
    onBrowse: () -> Unit,
    onLatest: (() -> Unit)?,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val cleanName = remember(source.name) {
        source.name.removeSuffix(" [CS]").trim()
    }
    val iconUrl = remember(source.id, source.iconUrl) {
        source.iconUrl
            ?: manager.getPluginIconUrl(source.pluginName)
            ?: manager.getPluginIconUrl(cleanName)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBrowse),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = cleanName,
                        placeholder = painterResource(R.mipmap.ic_default_source),
                        error = painterResource(R.mipmap.ic_default_source),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cleanName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val extensionTag = repoName?.takeIf { it.isNotBlank() }
                        ?: source.pluginName.removeSuffix(".cs3").takeIf { it.isNotBlank() }
                    if (extensionTag != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = extensionTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        text = cleanName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${source.pluginName} • v${source.pluginVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onLatest != null) {
                    TextButton(onClick = onLatest) {
                        Text(
                            text = stringResource(MR.strings.latest),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                SuggestionChip(
                    onClick = onBrowse,
                    label = { Text("Browse") },
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Source",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}
