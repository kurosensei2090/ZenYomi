package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.animeStorageTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        content = { contentPadding, _ ->
            val screenModel = rememberScreenModel { AnimeStorageScreenModel() }
            val state by screenModel.state.collectAsState()

            StorageScreenContent(
                state = state,
                isManga = false,
                contentPadding = contentPadding,
                onCategorySelected = screenModel::setSelectedCategory,
                onDelete = screenModel::deleteEntry,
            )
        },
        navigateUp = navigator::pop,
    )
}
