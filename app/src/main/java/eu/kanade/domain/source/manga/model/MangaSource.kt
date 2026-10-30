package eu.kanade.domain.source.manga.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import tachiyomi.domain.source.manga.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

private val mangaSourceIconCache = ConcurrentHashMap<Long, ImageBitmap?>()

val Source.icon: ImageBitmap?
    get() {
        if (mangaSourceIconCache.containsKey(id)) {
            return mangaSourceIconCache[id]
        }
        val bitmap = Injekt.get<MangaExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        mangaSourceIconCache[id] = bitmap
        return bitmap
    }
