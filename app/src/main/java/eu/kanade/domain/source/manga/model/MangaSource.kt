package eu.kanade.domain.source.manga.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import tachiyomi.domain.source.manga.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

private object NoIcon
private val mangaSourceIconCache = ConcurrentHashMap<Long, Any>()

val Source.icon: ImageBitmap?
    get() {
        val cached = mangaSourceIconCache[id]
        if (cached === NoIcon) return null
        if (cached is ImageBitmap) return cached

        val bitmap = Injekt.get<MangaExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        mangaSourceIconCache[id] = bitmap ?: NoIcon
        return bitmap
    }
