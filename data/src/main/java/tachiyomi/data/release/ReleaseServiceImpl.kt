package tachiyomi.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        val release = with(json) {
            networkService.client
                .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }
        val downloadLink = getDownloadLink(release = release, installedAbi = arguments.installedAbi) ?: return null

        val buildCode = extractBuildCode(release.name, release.version, release.info)
        val formattedVersion = if (buildCode != null && !release.version.contains("build", ignoreCase = true) && !release.version.contains("b$buildCode", ignoreCase = true)) {
            "${release.version} (Build $buildCode)"
        } else {
            release.version
        }

        return Release(
            version = formattedVersion,
            info = release.info.replace(gitHubUsernameMentionRegex) { mention ->
                "[${mention.value}](https://github.com/${mention.value.substring(1)})"
            },
            releaseLink = release.releaseLink,
            downloadLink = downloadLink,
            buildCode = buildCode,
        )
    }

    private fun extractBuildCode(name: String?, tag: String?, info: String?): Int? {
        val regex = Regex("""(?i)\bbuild\s*[:#\-]?\s*(\d+)\b|\bb(\d+)\b""")

        // 1. Release title / name
        name?.let {
            val match = regex.find(it)
            val code = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: match?.groupValues?.getOrNull(2)?.toIntOrNull()
            if (code != null) return code
        }

        // 2. Release tag
        tag?.let {
            val match = regex.find(it)
            val code = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: match?.groupValues?.getOrNull(2)?.toIntOrNull()
            if (code != null) return code
        }

        // 3. First few lines of release body
        info?.lines()?.take(5)?.forEach { line ->
            val match = regex.find(line)
            val code = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: match?.groupValues?.getOrNull(2)?.toIntOrNull()
            if (code != null) return code
        }

        return null
    }

    private fun getDownloadLink(release: GithubRelease, installedAbi: String? = null): String? {
        val map = release.assets.associate { asset ->
            BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        if (!installedAbi.isNullOrBlank()) {
            map[installedAbi]?.let { return it }
        }

        for (abi in Build.SUPPORTED_ABIS) {
            map[abi]?.let { return it }
        }

        return map["universal"] ?: map[null] ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadLink
    }

    companion object {
        private val BUILD_TYPES = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

        /**
         * Regular expression that matches a mention to a valid GitHub username, like it's
         * done in GitHub Flavored Markdown. It follows these constraints:
         *
         * - Alphanumeric with single hyphens (no consecutive hyphens)
         * - Cannot begin or end with a hyphen
         * - Max length of 39 characters
         *
         * Reference: https://stackoverflow.com/a/30281147
         */
        private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
            .toRegex(RegexOption.IGNORE_CASE)
    }
}
