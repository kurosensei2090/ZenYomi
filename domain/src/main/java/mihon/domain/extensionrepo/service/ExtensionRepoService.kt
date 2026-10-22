package mihon.domain.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            val cleanBaseUrl = repo.trimEnd('/')
            try {
                val response = client.newCall(GET("$cleanBaseUrl/repo.json")).awaitSuccess()
                val body = response.body.string()
                val dto = with(json) {
                    try {
                        decodeFromString<ExtensionRepoMetaDto>(body).meta
                    } catch (_: Exception) {
                        decodeFromString<ExtensionRepoDto>(body)
                    }
                }
                ExtensionRepo(
                    baseUrl = cleanBaseUrl,
                    name = dto.name,
                    shortName = dto.shortName,
                    website = dto.website,
                    signingKeyFingerprint = dto.signingKeyFingerprint,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }
}
