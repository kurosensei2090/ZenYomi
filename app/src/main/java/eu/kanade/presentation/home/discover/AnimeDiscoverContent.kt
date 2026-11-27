package eu.kanade.presentation.home.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.browse.anime.components.AnimeSourceIcon
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.home.discover.AnimeDiscoverScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDiscoverContent(
    state: AnimeDiscoverScreenModel.State,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
    onMorePopularClick: () -> Unit,
    onMoreLatestClick: () -> Unit,
    onSelectSource: (Long) -> Unit,
    onTogglePin: (Long) -> Unit = {},
    onExploreExtensionsClick: () -> Unit,
    onGlobalSearchClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSourceSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        PullRefresh(
            refreshing = state.isRefreshing,
            onRefresh = onRefresh,
            enabled = true,
            indicatorPadding = contentPadding,
        ) {
            when {
                state.sources.isEmpty() -> {
                    EmptyScreen(
                        stringRes = AYMR.strings.no_sources_installed,
                        modifier = Modifier.padding(contentPadding),
                        actions = persistentListOf(
                            EmptyScreenAction(
                                stringRes = MR.strings.label_extensions,
                                icon = Icons.Outlined.Explore,
                                onClick = onExploreExtensionsClick,
                            ),
                        ),
                    )
                }

                // If a search query is active in Discover tab, display search results view
                !state.searchQuery.isNullOrBlank() -> {
                    val query = state.searchQuery
                    val sourceName = state.currentSource?.name?.removeSuffix(" [CS]")?.trim() ?: "Source"
                    if (state.isSearching && state.searchResults.isEmpty()) {
                        LoadingScreen(Modifier.padding(contentPadding))
                    } else if (!state.isSearching && state.searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp),
                            ) {
                                Text(
                                    text = "No results found for \"$query\" on $sourceName",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { onGlobalSearchClick(query) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Search all sources")
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(115.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Results in $sourceName",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (state.isSearching) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                    TextButton(onClick = { onGlobalSearchClick(query) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Search all sources",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }

                            itemsIndexed(state.searchResults, key = { idx, anime -> "search_${anime.id}_$idx" }) { _, anime ->
                                DiscoverAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime) },
                                    onLongClick = { onAnimeLongClick(anime) },
                                )
                            }
                        }
                    }
                }

                state.isLoading && state.popularAnime.isEmpty() && state.latestAnime.isEmpty() -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }

                state.popularAnime.isEmpty() && state.latestAnime.isEmpty() && state.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.failed_to_load_feed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = onRefresh) {
                                Text(stringResource(MR.strings.action_retry))
                            }
                        }
                    }
                }

                else -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.currentSource?.id) {
                        listState.scrollToItem(0)
                    }
                    LaunchedEffect(state.popularAnime.isNotEmpty(), state.isRefreshing) {
                        if (!state.isRefreshing && listState.firstVisibleItemIndex <= 1) {
                            listState.scrollToItem(0)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding + PaddingValues(top = 8.dp, bottom = 88.dp),
                    ) {
                        // Popular Section
                        if (state.popularAnime.isNotEmpty()) {
                            item(key = "header_popular") {
                                DiscoverSectionHeader(
                                    title = stringResource(AYMR.strings.discover_popular),
                                    onMoreClick = onMorePopularClick,
                                )
                            }
                            item(key = "row_popular") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(state.popularAnime, key = { idx, anime -> "pop_${anime.id}_$idx" }) { _, anime ->
                                        DiscoverAnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeClick(anime) },
                                            onLongClick = { onAnimeLongClick(anime) },
                                        )
                                    }
                                }
                            }

                            item(key = "spacer_popular") {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Latest Section
                        if (state.latestAnime.isNotEmpty()) {
                            item(key = "header_latest") {
                                DiscoverSectionHeader(
                                    title = stringResource(AYMR.strings.discover_latest),
                                    onMoreClick = onMoreLatestClick,
                                )
                            }
                            item(key = "row_latest") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(state.latestAnime, key = { idx, anime -> "lat_${anime.id}_$idx" }) { _, anime ->
                                        DiscoverAnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeClick(anime) },
                                            onLongClick = { onAnimeLongClick(anime) },
                                        )
                                    }
                                }
                            }
                        } else if (state.isLatestLoading && (state.currentSource?.supportsLatest == true)) {
                            item(key = "header_latest_loading") {
                                DiscoverSectionHeader(
                                    title = stringResource(AYMR.strings.discover_latest),
                                    onMoreClick = onMoreLatestClick,
                                )
                            }
                            item(key = "row_latest_loading") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    userScrollEnabled = false,
                                ) {
                                    items(5) {
                                        DiscoverAnimeCardPlaceholder()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Source Switcher Button (CloudStream style pill button)
        if (state.sources.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(contentPadding)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showSourceSheet = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.currentSource?.name?.removeSuffix(" [CS]")?.trim() ?: stringResource(AYMR.strings.action_select_source),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    // Source Selection BottomSheet
    if (showSourceSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.action_select_source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )

                LazyColumn {
                    itemsIndexed(state.sources, key = { idx, source -> "src_${source.id}_$idx" }) { _, source ->
                        val isSelected = state.currentSource?.id == source.id
                        val isPinned = Pin.Actual in source.pin
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSource(source.id)
                                    showSourceSheet = false
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            AnimeSourceIcon(
                                source = source,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.name.removeSuffix(" [CS]").trim(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (source.name.endsWith(" [CS]")) {
                                        Text(
                                            text = "CloudStream",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (source.lang.isNotBlank()) {
                                            Text(
                                                text = " • ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (source.lang.isNotBlank()) {
                                        Text(
                                            text = source.lang.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(22.dp),
                                )
                            }
                            IconButton(
                                onClick = { onTogglePin(source.id) },
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = "Pin source",
                                    tint = if (isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(
    title: String,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onMoreClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(AYMR.strings.discover_more),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoverAnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            ItemCover.Book(
                data = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                onClick = onClick,
            )
            if (anime.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    InLibraryBadge(enabled = true)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Fixed title box height to prevent LazyRow height jitter and layout jumping
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DiscoverAnimeCardPlaceholder() {
    Column(modifier = Modifier.width(120.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        )
    }
}
