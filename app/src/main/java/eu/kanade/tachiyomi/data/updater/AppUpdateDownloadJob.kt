package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class AppUpdateDownloadJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = AppUpdateNotifier(context)
    private val network: NetworkHelper by injectLazy()

    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Int) : State
        data class Finished(val uri: Uri) : State
        data class Error(val message: String) : State
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL)
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: context.stringResource(MR.strings.app_name)

        if (url.isNullOrEmpty()) {
            _state.value = State.Error("Download URL is empty")
            return Result.failure()
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to run on foreground service" }
        }

        withIOContext {
            downloadApk(title, url)
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_APP_UPDATER,
            notifier.onDownloadStarted().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Called to start downloading apk of new update
     *
     * @param url url location of file
     */
    private suspend fun downloadApk(title: String, url: String) {
        // Show notification download starting.
        notifier.onDownloadStarted(title)
        _state.value = State.Downloading(0)

        val progressListener = object : ProgressListener {
            // Progress of the download
            var savedProgress = 0

            // Keep track of the last notification sent to avoid posting too many.
            var lastTick = 0L

            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                val progress = if (contentLength > 0) (100 * (bytesRead.toFloat() / contentLength)).toInt() else 0
                val currentTime = System.currentTimeMillis()
                if (progress > savedProgress && currentTime - 200 > lastTick) {
                    savedProgress = progress
                    lastTick = currentTime
                    notifier.onProgressChange(progress)
                }
                _state.value = State.Downloading(progress)
            }
        }

        try {
            // Download the new update.
            val response = network.client.newCachelessCallWithProgress(GET(url), progressListener)
                .await()

            // File where the apk will be saved.
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val tmpFile = File(cacheDir, "update.apk.tmp")
            val apkFile = File(cacheDir, "update.apk")

            if (response.isSuccessful) {
                if (tmpFile.exists()) tmpFile.delete()
                response.body.source().saveTo(tmpFile)
                if (apkFile.exists()) apkFile.delete()
                tmpFile.renameTo(apkFile)
            } else {
                response.close()
                throw Exception("Unsuccessful response: ${response.code}")
            }
            notifier.cancel()
            val uri = apkFile.getUriCompat(context)
            _state.value = State.Finished(uri)
            notifier.promptInstall(uri)
        } catch (e: Exception) {
            val shouldCancel = e is CancellationException ||
                (e is StreamResetException && e.errorCode == ErrorCode.CANCEL)
            if (shouldCancel) {
                _state.value = State.Idle
                notifier.cancel()
            } else {
                _state.value = State.Error(e.message ?: "Download error")
                notifier.onDownloadError(url)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateDownload"

        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
        const val EXTRA_DOWNLOAD_TITLE = "DOWNLOAD_TITLE"

        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = _state.asStateFlow()

        fun resetState() {
            _state.value = State.Idle
        }

        fun checkExistingApk(context: Context): Uri? {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, "update.apk")
            if (!apkFile.exists() || apkFile.length() == 0L) return null
            return try {
                val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (packageInfo != null && packageInfo.packageName == context.packageName) {
                    val uri = apkFile.getUriCompat(context)
                    _state.value = State.Finished(uri)
                    uri
                } else {
                    apkFile.delete()
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun start(context: Context, url: String, title: String? = null) {
            _state.value = State.Downloading(0)
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .setConstraints(constraints)
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        EXTRA_DOWNLOAD_URL to url,
                        EXTRA_DOWNLOAD_TITLE to title,
                    ),
                )
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
            _state.value = State.Idle
        }
    }
}
