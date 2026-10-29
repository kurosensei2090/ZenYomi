package mihon.data.extension.anime.service

import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.anime.model.NetworkAnimeExtensionStore
import mihon.data.extension.anime.model.NetworkLegacyAnimeExtension
import mihon.data.extension.anime.model.NetworkLegacyAnimeExtensionRepo
import mihon.data.extension.anime.model.toAvailableExtensions
import mihon.domain.extension.anime.model.AnimeExtensionStore
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class AnimeExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    private fun normalizeStoreUrl(raw: String): List<String> {
        var trimmed = raw.trim()
        if (trimmed.startsWith("https://github.com/") || trimmed.startsWith("http://github.com/")) {
            trimmed = trimmed.replace("/tree/", "/raw/").replace("/blob/", "/raw/")
            if (trimmed.contains("/raw/")) {
                trimmed = trimmed.replace("https://github.com/", "https://raw.githubusercontent.com/")
                    .replace("http://github.com/", "https://raw.githubusercontent.com/")
                    .replace("/raw/", "/")
            }
        }
        val baseGithubRegex = "^https?://github\\.com/([^/]+)/([^/]+)/?$".toRegex(RegexOption.IGNORE_CASE)
        val baseGithubMatch = baseGithubRegex.matchEntire(trimmed)
        if (baseGithubMatch != null) {
            val (owner, repo) = baseGithubMatch.destructured
            trimmed = "https://raw.githubusercontent.com/$owner/$repo/repo"
        }
        if (trimmed.contains("keiyoushi.github.io/extensions", ignoreCase = true)) {
            trimmed = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        }
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "https://$trimmed"
        }

        if (trimmed.endsWith("/index.min.json") || trimmed.endsWith("/index.pb") || trimmed.endsWith("/index.json")) {
            return listOf(trimmed)
        }
        if (trimmed.endsWith("/repo.json")) {
            return listOf(
                trimmed.replace("/repo.json", "/index.min.json"),
                trimmed.replace("/repo.json", "/index.pb"),
                trimmed,
            )
        }
        val clean = trimmed.trimEnd('/')
        return listOf(
            "$clean/index.min.json",
            "$clean/index.pb",
            "$clean/repo.json",
            "$clean/index.json",
            clean,
        )
    }

    suspend fun fetch(rawIndexUrl: String): Result<AnimeExtensionStore> {
        val candidates = normalizeStoreUrl(rawIndexUrl)
        var lastException: Exception? = null

        for (candidate in candidates) {
            var updatedIndexUrl: String = candidate
            try {
                val response = network.client.newCall(GET(candidate)).awaitSuccess()
                val store = response.body.source().decompressIfGzipped().use { source ->
                    val networkStore = when (source.peek().readByte()) {
                        // "[..."
                        0x5B.toByte() -> run {
                            if (!candidate.endsWith("/index.min.json")) {
                                throw IllegalArgumentException("Provided legacy store url is not valid")
                            }
                            updatedIndexUrl = candidate.replace("/index.min.json", "/repo.json")
                            network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                                json.decodeFromBufferedSource<NetworkLegacyAnimeExtensionRepo>(it)
                            }
                        }
                        // "{..."
                        0x7B.toByte() -> try {
                            json.decodeFromBufferedSource<NetworkLegacyAnimeExtensionRepo>(source.peek())
                        } catch (_: IllegalArgumentException) {
                            json.decodeFromBufferedSource<NetworkAnimeExtensionStore>(source)
                        }
                        else -> protoBuf.decodeFromByteArray<NetworkAnimeExtensionStore>(source.readByteArray())
                    }

                    if (networkStore is NetworkLegacyAnimeExtensionRepo && networkStore.indexV2 != null) {
                        return fetch(networkStore.indexV2)
                    }

                    networkStore.toExtensionStore(updatedIndexUrl)
                }
                return Result.success(store)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
            }
        }

        logcat(LogPriority.ERROR, lastException) {
            "Failed to add extension store '$rawIndexUrl'"
        }
        return Result.failure(lastException ?: IllegalArgumentException("Invalid repository url"))
    }

    suspend fun getExtensions(store: AnimeExtensionStore): Result<List<AnimeExtension.Available>> {
        return try {
            val extensions = if (store.extensionListUrl != null) {
                val response = network.client.newCall(GET(store.extensionListUrl!!)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkAnimeExtensionStore.ExtensionList>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkAnimeExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .toAvailableExtensions(store)
                }
            } else if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkAnimeExtensionStore>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkAnimeExtensionStore>(source.readByteArray())
                    }
                        .extensionList!!
                        .toAvailableExtensions(store)
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    json.decodeFromBufferedSource<List<NetworkLegacyAnimeExtension>>(source)
                        .map { it.toAvailableExtension(store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }

        return if (isGzip) gzip().buffer() else this
    }
}
