package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.anime.components.BaseAnimeSourceItem
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourcesScreen(
    state: AnimeSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSearchQueryChange: (String?) -> Unit = {},
) {
    PullRefresh(
        refreshing = isRefreshing,
        enabled = true,
        onRefresh = onRefresh,
        indicatorPadding = contentPadding,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.isEmpty && state.searchQuery.isNullOrBlank() -> EmptyScreen(
                stringRes = MR.strings.source_empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                ScrollbarLazyColumn(
                    contentPadding = contentPadding + topSmallPaddingValues,
                ) {
                    item(key = "anime_sources_search_box") {
                        OutlinedTextField(
                            value = state.searchQuery.orEmpty(),
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            placeholder = { Text("Search sources...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (!state.searchQuery.isNullOrEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear",
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    if (state.isEmpty && !state.searchQuery.isNullOrBlank()) {
                        item(key = "no_search_results") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No sources found matching \"${state.searchQuery}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    items(
                        items = state.items,
                        contentType = {
                            when (it) {
                                is AnimeSourceUiModel.Header -> "header"
                                is AnimeSourceUiModel.Item -> "item"
                            }
                        },
                        key = {
                            when (it) {
                                is AnimeSourceUiModel.Header -> "header-${it.language}"
                                is AnimeSourceUiModel.Item -> "source-${it.source.key()}"
                            }
                        },
                    ) { model ->
                        when (model) {
                            is AnimeSourceUiModel.Header -> {
                                AnimeSourceHeader(
                                    modifier = Modifier,
                                    language = model.language,
                                )
                            }
                            is AnimeSourceUiModel.Item -> AnimeSourceItem(
                                modifier = Modifier,
                                source = model.source,
                                onClickItem = onClickItem,
                                onLongClickItem = onLongClickItem,
                                onClickPin = onClickPin,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSourceHeader(
    language: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun AnimeSourceItem(
    source: AnimeSource,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAnimeSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            AnimeSourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun AnimeSourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun AnimeSourceOptionsDialog(
    source: AnimeSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalAnimeSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface AnimeSourceUiModel {
    data class Item(val source: AnimeSource) : AnimeSourceUiModel
    data class Header(val language: String) : AnimeSourceUiModel
}
