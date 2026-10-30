package eu.kanade.domain.source.anime.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

private object NoIcon
private val animeSourceIconCache = ConcurrentHashMap<Long, Any>()

val AnimeSource.icon: ImageBitmap?
    get() {
        val cached = animeSourceIconCache[id]
        if (cached === NoIcon) return null
        if (cached is ImageBitmap) return cached

        val bitmap = Injekt.get<AnimeExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        animeSourceIconCache[id] = bitmap ?: NoIcon
        return bitmap
    }
