package eu.kanade.domain.source.anime.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

private val animeSourceIconCache = ConcurrentHashMap<Long, ImageBitmap?>()

val AnimeSource.icon: ImageBitmap?
    get() {
        if (animeSourceIconCache.containsKey(id)) {
            return animeSourceIconCache[id]
        }
        val bitmap = Injekt.get<AnimeExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        animeSourceIconCache[id] = bitmap
        return bitmap
    }
