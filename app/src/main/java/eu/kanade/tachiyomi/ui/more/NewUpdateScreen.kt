package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class NewUpdateScreen(
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
    private val buildCode: Int? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }

        val downloadState by AppUpdateDownloadJob.state.collectAsState()

        LaunchedEffect(Unit) {
            AppUpdateDownloadJob.checkExistingApk(context, buildCode)
        }

        NewUpdateScreen(
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            downloadState = downloadState,
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = {
                if (downloadState is AppUpdateDownloadJob.State.Downloading) {
                    AppUpdateDownloadJob.stop(context)
                } else {
                    navigator.pop()
                }
            },
            onAcceptUpdate = {
                when (val state = downloadState) {
                    is AppUpdateDownloadJob.State.Idle, is AppUpdateDownloadJob.State.Error -> {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = downloadLink,
                            title = versionName,
                        )
                    }
                    is AppUpdateDownloadJob.State.Finished -> {
                        installApk(context, state.uri)
                    }
                    is AppUpdateDownloadJob.State.Downloading -> {
                        // Already downloading
                    }
                }
            },
        )
    }

    private fun installApk(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfos) {
                try {
                    context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to launch package installer" }
            context.toast(e.message ?: "Failed to launch installer")
        }
    }
}
