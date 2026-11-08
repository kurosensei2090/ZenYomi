package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disabling app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //    return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreview = isPreviewBuildType,
                    commitCount = BuildConfig.COMMIT_COUNT.toIntOrNull() ?: 0,
                    versionName = BuildConfig.VERSION_NAME,
                    repository = GITHUB_REPO,
                    forceCheck = forceCheck,
                    versionCode = BuildConfig.VERSION_CODE,
                    installedAbi = getInstalledAbi(context),
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(
                    result.release,
                )
                else -> {}
            }

            result
        }
    }

    companion object {
        fun getInstalledAbi(context: Context): String {
            try {
                val apkFile = java.io.File(context.applicationInfo.sourceDir)
                java.util.zip.ZipFile(apkFile).use { zip ->
                    val entries = zip.entries()
                    val abisInApk = mutableSetOf<String>()
                    while (entries.hasMoreElements()) {
                        val entryName = entries.nextElement().name
                        if (entryName.startsWith("lib/")) {
                            val abi = entryName.substringAfter("lib/").substringBefore("/")
                            if (abi.isNotBlank()) {
                                abisInApk.add(abi)
                            }
                        }
                    }
                    if (abisInApk.size > 1) {
                        return "universal"
                    } else if (abisInApk.size == 1) {
                        return abisInApk.first()
                    }
                }
            } catch (_: Exception) {}

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            return when {
                nativeLibDir.contains("arm64") -> "arm64-v8a"
                nativeLibDir.contains("arm") -> "armeabi-v7a"
                nativeLibDir.contains("x86_64") -> "x86_64"
                nativeLibDir.contains("x86") -> "x86"
                else -> "universal"
            }
        }
    }
}

val GITHUB_REPO: String by lazy {
    if (isPreviewBuildType) {
        "kurosensei2090/ZenYomi-preview"
    } else {
        "kurosensei2090/ZenYomi"
    }
}

val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
