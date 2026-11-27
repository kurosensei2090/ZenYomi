package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeRelationGroup
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.padding
import kotlin.math.ceil

private val RELATED_ITEM_WIDTH = 96.dp
private const val MAX_ITEMS_PER_ROW = 3

@Composable
fun RelatedAnimeRows(
    relations: List<AnimeRelationGroup>,
    displayMode: LibraryDisplayMode? = LibraryDisplayMode.default,
    onRelatedClick: (Anime) -> Unit,
    onRelatedLongClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (relations.isEmpty()) return

    val mode = displayMode ?: LibraryDisplayMode.default
    val (smalls, multis) = relations.partition { it.anime.size <= MAX_ITEMS_PER_ROW }

    Column(modifier = modifier.padding(bottom = MaterialTheme.padding.medium)) {
        if (smalls.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.small,
                        vertical = MaterialTheme.padding.small,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                smalls.packRows(MAX_ITEMS_PER_ROW).forEach { rowRelations ->
                    val startSlots = rowRelations.runningFold(0) { acc, relation ->
                        acc + relation.anime.size
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowRelations.forEachIndexed { index, relation ->
                            val size = relation.anime.size
                            val startSlot = startSlots[index]
                            val endSlot = startSlot + size - 1

                            Box(
                                modifier = Modifier.weight(size.toFloat()),
                                contentAlignment = when {
                                    startSlot == 0 -> Alignment.CenterStart
                                    endSlot == MAX_ITEMS_PER_ROW - 1 -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                },
                            ) {
                                Column {
                                    Text(
                                        text = relation.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(MaterialTheme.padding.extraSmall))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            MaterialTheme.padding.extraSmall,
                                        ),
                                    ) {
                                        relation.anime.forEach { anime ->
                                            Box(modifier = Modifier.widthIn(max = RELATED_ITEM_WIDTH)) {
                                                RelatedAnimeItem(
                                                    anime = anime,
                                                    relationName = relation.name,
                                                    displayMode = mode,
                                                    onClick = { onRelatedClick(anime) },
                                                    onLongClick = { onRelatedLongClick(anime) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val filled = rowRelations.sumOf { it.anime.size }
                        if (filled < MAX_ITEMS_PER_ROW) {
                            Spacer(Modifier.weight((MAX_ITEMS_PER_ROW - filled).toFloat()))
                        }
                    }
                }
            }
        }

        multis.forEach { relation ->
            Text(
                text = relation.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.small,
                ),
            )

            LazyRow(
                contentPadding = PaddingValues(MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                items(relation.anime, key = { it.id }) { anime ->
                    Box(modifier = Modifier.width(RELATED_ITEM_WIDTH)) {
                        RelatedAnimeItem(
                            anime = anime,
                            relationName = relation.name,
                            displayMode = mode,
                            onClick = { onRelatedClick(anime) },
                            onLongClick = { onRelatedLongClick(anime) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedAnimeItem(
    anime: Anime,
    relationName: String,
    displayMode: LibraryDisplayMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val coverAlpha = if (anime.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f
    val seasonBadge = extractSeasonBadgeLabel(anime.title, relationName)

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid,
        LibraryDisplayMode.List,
        -> {
            EntryComfortableGridItem(
                title = anime.title,
                titleMaxLines = 3,
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                coverBadgeEnd = { if (seasonBadge != null) Badge(text = seasonBadge) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid -> {
            EntryCompactGridItem(
                title = anime.title,
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                coverBadgeEnd = { if (seasonBadge != null) Badge(text = seasonBadge) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
        LibraryDisplayMode.CoverOnlyGrid -> {
            EntryCompactGridItem(
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                coverBadgeEnd = { if (seasonBadge != null) Badge(text = seasonBadge) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
        else -> {
            EntryCompactGridItem(
                coverData = anime.asAnimeCover(),
                coverAlpha = coverAlpha,
                coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                coverBadgeEnd = { if (seasonBadge != null) Badge(text = seasonBadge) },
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

private fun extractSeasonBadgeLabel(title: String, relationName: String): String? {
    val seasonMatch = Regex("""(?:season|s)\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)
        ?: Regex("""(\d+)(?:st|nd|rd|th)\s+season""", RegexOption.IGNORE_CASE).find(title)
    val partMatch = Regex("""part\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)
    val seasonNum = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val partNum = partMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

    return when {
        seasonNum != null && partNum != null -> "S$seasonNum P$partNum"
        seasonNum != null -> "Season $seasonNum"
        partNum != null -> "Part $partNum"
        Regex("""\bII\b""").containsMatchIn(title) -> "Season 2"
        Regex("""\bIII\b""").containsMatchIn(title) -> "Season 3"
        Regex("""\bIV\b""").containsMatchIn(title) -> "Season 4"
        Regex("""\bV\b""").containsMatchIn(title) -> "Season 5"
        title.contains("الموسم الأول") || title.contains("الجزء الأول") -> "Season 1"
        title.contains("الموسم الثاني") || title.contains("الجزء الثاني") -> "Season 2"
        title.contains("الموسم الثالث") || title.contains("الجزء الثالث") -> "Season 3"
        relationName.contains("Previous", ignoreCase = true) -> "Season 1"
        relationName.contains("Next", ignoreCase = true) -> "Next"
        relationName.contains("Prequel", ignoreCase = true) -> "Prequel"
        relationName.contains("Sequel", ignoreCase = true) -> "Sequel"
        else -> null
    }
}

private fun List<AnimeRelationGroup>.packRows(maxPerRow: Int): List<List<AnimeRelationGroup>> {
    if (isEmpty()) return emptyList()

    val total = sumOf { it.anime.size }
    val rowCount = ceil(total / maxPerRow.toDouble()).toInt()
    val base = total / rowCount
    val remainder = total % rowCount

    val rows = mutableListOf<List<AnimeRelationGroup>>()
    var current = mutableListOf<AnimeRelationGroup>()
    var filled = 0
    var cap = base + if (remainder > 0) 1 else 0

    forEach { relation ->
        val size = relation.anime.size
        if (current.isNotEmpty() && filled + size > cap) {
            rows += current
            current = mutableListOf()
            filled = 0
            cap = base + if (rows.size < remainder) 1 else 0
            if (cap < size) cap = maxPerRow
        }
        current += relation
        filled += size
    }
    if (current.isNotEmpty()) rows += current

    return rows
}
