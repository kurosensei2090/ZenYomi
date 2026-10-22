@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * chapter data.
     *
     * @since extensions-lib 1.5
     */
    var memo: JsonObject

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    fun copy() = create().also {
        it.url = url
        it.name = name
        it.date_upload = date_upload
        it.chapter_number = chapter_number
        it.scanlator = scanlator
        it.memo = memo
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
