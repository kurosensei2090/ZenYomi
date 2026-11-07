package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    downloadState: AppUpdateDownloadJob.State = AppUpdateDownloadJob.State.Idle,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    val headingText = when (downloadState) {
        is AppUpdateDownloadJob.State.Idle -> stringResource(MR.strings.update_check_notification_update_available)
        is AppUpdateDownloadJob.State.Downloading -> stringResource(MR.strings.update_check_notification_download_in_progress)
        is AppUpdateDownloadJob.State.Finished -> stringResource(MR.strings.update_check_notification_download_complete)
        is AppUpdateDownloadJob.State.Error -> stringResource(MR.strings.update_check_notification_download_error)
    }

    val subtitleText = when (downloadState) {
        is AppUpdateDownloadJob.State.Downloading -> if (downloadState.progress > 0) "$versionName • ${downloadState.progress}%" else versionName
        is AppUpdateDownloadJob.State.Error -> downloadState.message
        else -> versionName
    }

    val acceptText = when (downloadState) {
        is AppUpdateDownloadJob.State.Idle -> stringResource(MR.strings.update_check_confirm)
        is AppUpdateDownloadJob.State.Downloading -> if (downloadState.progress > 0) {
            "${stringResource(MR.strings.update_check_notification_download_in_progress)} (${downloadState.progress}%)"
        } else {
            stringResource(MR.strings.update_check_notification_download_in_progress)
        }
        is AppUpdateDownloadJob.State.Finished -> stringResource(MR.strings.action_install)
        is AppUpdateDownloadJob.State.Error -> stringResource(MR.strings.action_retry)
    }

    val canAccept = downloadState !is AppUpdateDownloadJob.State.Downloading

    val rejectText = when (downloadState) {
        is AppUpdateDownloadJob.State.Downloading -> stringResource(MR.strings.action_cancel)
        else -> stringResource(MR.strings.action_not_now)
    }

    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = headingText,
        subtitleText = subtitleText,
        acceptText = acceptText,
        onAcceptClick = onAcceptUpdate,
        canAccept = canAccept,
        rejectText = rejectText,
        onRejectClick = onRejectUpdate,
    ) {
        if (downloadState is AppUpdateDownloadJob.State.Downloading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.padding.medium),
            ) {
                if (downloadState.progress > 0) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else if (downloadState is AppUpdateDownloadJob.State.Finished) {
            Text(
                text = stringResource(MR.strings.update_check_notification_download_complete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            )
        } else if (downloadState is AppUpdateDownloadJob.State.Error) {
            Text(
                text = downloadState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            )
        }

        RichText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
            style = RichTextStyle(
                stringStyle = RichTextStringStyle(
                    linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                ),
            ),
        ) {
            Markdown(content = changelogInfo)

            TextButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(MR.strings.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiPreviewTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            changelogInfo = """
                ## Yay
                Foobar

                ### More info
                - Hello
                - World
            """.trimIndent(),
            downloadState = AppUpdateDownloadJob.State.Idle,
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}
