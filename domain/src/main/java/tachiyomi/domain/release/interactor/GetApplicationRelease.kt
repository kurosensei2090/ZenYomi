package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 1 hour at most when not force checked
        if (!arguments.forceCheck &&
            now.isBefore(
                Instant.ofEpochMilli(lastChecked.get()).plus(1, ChronoUnit.HOURS),
            )
        ) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            isPreview = arguments.isPreview,
            commitCount = arguments.commitCount,
            versionName = arguments.versionName,
            versionCode = arguments.versionCode,
            versionTag = release.version,
            remoteBuildCode = release.buildCode,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionCode: Int,
        versionTag: String,
        remoteBuildCode: Int?,
    ): Boolean {
        // Removes prefixes and extra annotations (e.g. "v1.0.2 (Build 26)" -> "1.0.2")
        val cleanRemote = versionTag
            .substringBefore(" ")
            .substringBefore("(")
            .substringBefore("-")
            .replace("[^\\d.]".toRegex(), "")

        return if (isPreview) {
            cleanRemote.toIntOrNull()?.let { it > commitCount } ?: false
        } else {
            val cleanOld = versionName
                .substringBefore(" ")
                .substringBefore("(")
                .substringBefore("-")
                .replace("[^\\d.]".toRegex(), "")

            val newParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val oldParts = cleanOld.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(newParts.size, oldParts.size)
            for (i in 0 until maxLen) {
                val n = newParts.getOrElse(i) { 0 }
                val o = oldParts.getOrElse(i) { 0 }
                if (n > o) return true
                if (n < o) return false
            }

            // If major.minor.patch are equal, check build number / version code
            val effectiveRemoteBuildCode = remoteBuildCode ?: run {
                val regex = Regex("""(?i)\bbuild\s*[:#\-]?\s*(\d+)\b|\bb(\d+)\b""")
                val match = regex.find(versionTag)
                match?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: match?.groupValues?.getOrNull(2)?.toIntOrNull()
            }

            if (effectiveRemoteBuildCode != null && effectiveRemoteBuildCode > versionCode) {
                return true
            }

            false
        }
    }

    data class Arguments(
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
        val versionCode: Int = 0,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
