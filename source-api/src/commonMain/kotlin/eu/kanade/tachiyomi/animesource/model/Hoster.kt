package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY

open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val memo: JsonObject = JsonObject.EMPTY,
) {
    @Transient
    @Volatile
    var status: State = State.IDLE

    enum class State {
        IDLE,
        LOADING,
        READY,
        ERROR,
    }

    // Ext lib 16 constructor
    constructor(
        hosterUrl: String = "",
        hosterName: String = "",
        videoList: List<Video>? = null,
        internalData: String = "",
        lazy: Boolean = false,
    ) : this(
        hosterUrl = hosterUrl,
        hosterName = hosterName,
        videoList = videoList,
        internalData = internalData,
        lazy = lazy,
        memo = JsonObject.EMPTY,
    )

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
        memo: JsonObject = this.memo,
    ): Hoster {
        return Hoster(hosterUrl, hosterName, videoList, internalData, lazy, memo)
    }

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> {
            if (this.isEmpty()) return emptyList()

            val serverGroups = LinkedHashMap<String, MutableList<Video>>()
            var hasServerPrefix = false

            for (video in this) {
                val title = video.videoTitle.trim()
                val dashIdx = title.indexOf(" - ")
                val bracketIdx = if (title.startsWith("[")) title.indexOf("]") else -1

                if (dashIdx > 0) {
                    val sName = title.substring(0, dashIdx).trim()
                    val qName = title.substring(dashIdx + 3).trim()
                    if (sName.isNotEmpty() && qName.isNotEmpty()) {
                        hasServerPrefix = true
                        serverGroups.getOrPut(sName) { mutableListOf() }.add(video.copy(videoTitle = qName))
                        continue
                    }
                } else if (bracketIdx > 1) {
                    val sName = title.substring(1, bracketIdx).trim()
                    val qName = title.substring(bracketIdx + 1).trim()
                    if (sName.isNotEmpty() && qName.isNotEmpty()) {
                        hasServerPrefix = true
                        serverGroups.getOrPut(sName) { mutableListOf() }.add(video.copy(videoTitle = qName))
                        continue
                    }
                }

                serverGroups.getOrPut(NO_HOSTER_LIST) { mutableListOf() }.add(video)
            }

            if (!hasServerPrefix || (serverGroups.size == 1 && serverGroups.containsKey(NO_HOSTER_LIST))) {
                return listOf(
                    Hoster(
                        hosterUrl = "",
                        hosterName = NO_HOSTER_LIST,
                        videoList = this,
                        memo = JsonObject.EMPTY,
                    ),
                )
            }

            return serverGroups.map { (name, vList) ->
                Hoster(
                    hosterUrl = "",
                    hosterName = name,
                    videoList = vList,
                    memo = JsonObject.EMPTY,
                )
            }
        }
    }
}

@Serializable
data class SerializableHoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: String? = null,
    val internalData: String,
    val lazy: Boolean = false,
    val memo: JsonObject = JsonObject.EMPTY,
) {
    companion object {
        fun List<Hoster>.serialize(): String =
            Json.encodeToString(
                this.map { host ->
                    SerializableHoster(
                        host.hosterUrl,
                        host.hosterName,
                        host.videoList?.serialize(),
                        host.internalData,
                        host.lazy,
                        host.memo,
                    )
                },
            )

        fun String.toHosterList(): List<Hoster> =
            Json.decodeFromString<List<SerializableHoster>>(this)
                .map { sHost ->
                    Hoster(
                        sHost.hosterUrl,
                        sHost.hosterName,
                        sHost.videoList?.toVideoList(),
                        sHost.internalData,
                        sHost.lazy,
                        sHost.memo,
                    )
                }
    }
}
