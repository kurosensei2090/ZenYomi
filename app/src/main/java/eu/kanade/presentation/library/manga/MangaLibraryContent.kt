package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaLibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<LibraryManga>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onMangaClicked: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (LibraryManga) -> Unit,
    onToggleRangeSelection: (LibraryManga) -> Unit,
    onRefresh: (Category?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: (Int) -> List<MangaLibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val coercedCurrentPage = remember { currentPage().coerceAtMost(categories.lastIndex) }
        val pagerState = rememberPagerState(coercedCurrentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.size > 1) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getNumberOfItemsForCategory = getNumberOfMangaForCategory,
            ) {
                scope.launch {
                    if (kotlin.math.abs(pagerState.currentPage - it) > 1) {
                        pagerState.scrollToPage(it)
                    } else {
                        pagerState.animateScrollToPage(it)
                    }
                }
            }
        }

        val notSelectionMode = selection.isEmpty()
        val onClickManga = remember(notSelectionMode, onMangaClicked, onToggleSelection) {
            { manga: LibraryManga ->
                if (notSelectionMode) {
                    onMangaClicked(manga.manga.id)
                } else {
                    onToggleSelection(manga)
                }
            }
        }

        PullRefresh(
            modifier = Modifier.weight(1f),
            refreshing = isRefreshing,
            onRefresh = {
                val currentCategory = categories.getOrNull(currentPage())
                onRefresh(currentCategory)
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            MangaLibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selectedManga = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                onClickManga = onClickManga,
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
