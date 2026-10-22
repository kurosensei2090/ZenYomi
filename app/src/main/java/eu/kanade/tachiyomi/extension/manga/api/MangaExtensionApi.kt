package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okio.BufferedSource
import okio.buffer
import okio.gzip
import okio.use
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val protoBuf: ProtoBuf by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        // First try protobuf index.pb (preferred by Keiyoushi and modern repos)
        try {
            val pbExtensions = getExtensionsFromProtobuf(repoBaseUrl)
            if (pbExtensions.isNotEmpty()) {
                return pbExtensions
            }
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "Failed to get extensions via protobuf from $repoBaseUrl, falling back to JSON" }
        }

        // Fallback to legacy index.min.json
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
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

    private suspend fun getExtensionsFromProtobuf(repoBaseUrl: String): List<MangaExtension.Available> {
        val response = networkService.client
            .newCall(GET("$repoBaseUrl/index.pb"))
            .awaitSuccess()

        val networkStore = response.body.source().decompressIfGzipped().use { source ->
            protoBuf.decodeFromByteArray(NetworkMangaExtensionStore.serializer(), source.readByteArray())
        }

        val extensions = networkStore.extensionList?.extensions
            ?: networkStore.extensionListUrl?.let { extListUrl ->
                val extListResponse = networkService.client.newCall(GET(extListUrl)).awaitSuccess()
                extListResponse.body.source().decompressIfGzipped().use { extListSource ->
                    protoBuf.decodeFromByteArray(
                        NetworkMangaExtensionStore.ExtensionList.serializer(),
                        extListSource.readByteArray(),
                    ).extensions
                }
            }
            ?: return emptyList()

        return extensions
            .filter { ext ->
                val libVersion = ext.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .filterNot { ext ->
                ext.name.contains("Outdated App", ignoreCase = true) || ext.name.contains("Update to", ignoreCase = true)
            }
            .map { ext ->
                val apkUrl = when {
                    ext.resources.apkUrl.startsWith("http://") || ext.resources.apkUrl.startsWith("https://") -> ext.resources.apkUrl
                    ext.resources.apkUrl.startsWith("//") -> "https:${ext.resources.apkUrl}"
                    else -> "$repoBaseUrl/apk/${ext.resources.apkUrl.substringAfterLast('/')}"
                }
                val iconUrl = when {
                    ext.resources.iconUrl.startsWith("http://") || ext.resources.iconUrl.startsWith("https://") -> ext.resources.iconUrl
                    ext.resources.iconUrl.startsWith("//") -> "https:${ext.resources.iconUrl}"
                    ext.resources.iconUrl.startsWith("main/") -> {
                        if (repoBaseUrl.contains("/extensions/repo")) {
                            repoBaseUrl.replace("/extensions/repo", "/extensions-source/") + ext.resources.iconUrl
                        } else {
                            "$repoBaseUrl/${ext.resources.iconUrl}"
                        }
                    }
                    else -> "$repoBaseUrl/icon/${ext.packageName}.png"
                }

                val extLangs = ext.sources.map { it.language }.filter { it.isNotEmpty() }.toSet()
                val lang = when {
                    extLangs.size == 1 -> extLangs.first()
                    extLangs.size > 1 -> "all"
                    else -> ext.packageName.substringAfter("eu.kanade.tachiyomi.extension.").substringBefore('.')
                }

                MangaExtension.Available(
                    name = ext.name.substringAfter("Tachiyomi: "),
                    pkgName = ext.packageName,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode,
                    libVersion = ext.extractLibVersion(),
                    lang = lang,
                    isNsfw = ext.contentWarning >= NetworkMangaExtensionStore.ContentWarning.MIXED,
                    sources = ext.sources.map {
                        MangaExtension.Available.MangaSource(
                            id = it.id,
                            lang = it.language,
                            name = it.name,
                            baseUrl = it.homeUrl,
                        )
                    },
                    apkName = apkUrl,
                    iconUrl = iconUrl,
                    repoUrl = repoBaseUrl,
                )
            }
    }

    private fun NetworkMangaExtensionStore.Extension.extractLibVersion(): Double {
        return extensionLib.toDoubleOrNull()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: 0.0
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .filterNot {
                it.name.contains("Outdated App", ignoreCase = true) || it.name.contains("Update to", ignoreCase = true)
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return when {
            extension.apkName.startsWith("http://") || extension.apkName.startsWith("https://") -> extension.apkName
            extension.apkName.startsWith("//") -> "https:${extension.apkName}"
            else -> "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}

@Serializable
private data class NetworkMangaExtensionStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(
        @ProtoNumber(1) val extensions: List<Extension> = emptyList(),
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: Resources = Resources(),
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0L,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long = 0L,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val message: String? = null,
    )

    enum class ContentWarning {
        @ProtoNumber(0)
        UNSPECIFIED,

        @ProtoNumber(1)
        SAFE,

        @ProtoNumber(2)
        MIXED,

        @ProtoNumber(3)
        NSFW,
    }
}
