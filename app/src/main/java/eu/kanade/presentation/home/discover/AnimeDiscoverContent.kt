package eu.kanade.presentation.home.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.home.discover.AnimeDiscoverScreenModel
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
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
    onExploreExtensionsClick: () -> Unit,
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
                state.isLoading && state.popularAnime.isEmpty() && state.latestAnime.isEmpty() -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }

                state.sources.isEmpty() -> {
                    EmptyScreen(
                        stringRes = AYMR.strings.no_sources_installed,
                        modifier = Modifier.padding(contentPadding),
                    )
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
                    LazyColumn(
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
                        text = state.currentSource?.name ?: stringResource(AYMR.strings.action_select_source),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSource(source.id)
                                    showSourceSheet = false
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (source.lang.isNotBlank()) {
                                    Text(
                                        text = source.lang.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
