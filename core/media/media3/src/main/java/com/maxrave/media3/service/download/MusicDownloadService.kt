package com.maxrave.media3.service.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.maxrave.media3.R
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import org.koin.android.ext.android.inject

@UnstableApi
internal class MusicDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        1000L,
        CHANNEL_ID,
        R.string.download,
        0,
    ) {
    private val downloadUtil: DownloadHandler by inject<DownloadHandler>()

    override fun getDownloadManager() = (downloadUtil as DownloadUtils).downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        (downloadUtil as DownloadUtils).downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.mono,
            null,
            if (downloads.size == 1) {
                Util.fromUtf8Bytes(downloads[0].request.data)
            } else {
                resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
            },
            downloads,
            notMetRequirements,
        )

    class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
    ) : DownloadManager.Listener {
        // Post a single summary notification when the whole queue reaches a terminal state,
        // instead of one notification per track. Reset as soon as new work starts.
        private var allDoneNotified = false

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            when (download.state) {
                Download.STATE_QUEUED,
                Download.STATE_DOWNLOADING,
                Download.STATE_STOPPED,
                -> allDoneNotified = false

                Download.STATE_COMPLETED,
                Download.STATE_FAILED,
                -> {
                    val all = downloadManager.getCurrentDownloads()
                    val anyActive =
                        all.any {
                            it.state == Download.STATE_QUEUED ||
                                it.state == Download.STATE_DOWNLOADING ||
                                it.state == Download.STATE_STOPPED ||
                                it.state == Download.STATE_REMOVING
                        }
                    if (!anyActive && !allDoneNotified) {
                        allDoneNotified = true
                        val completed = all.count { it.state == Download.STATE_COMPLETED }
                        val failed = all.count { it.state == Download.STATE_FAILED }
                        if (completed + failed == 0) return
                        val title =
                            if (failed == 0) {
                                context.resources.getQuantityString(
                                    R.plurals.n_song_downloaded,
                                    completed,
                                    completed,
                                )
                            } else {
                                context.getString(
                                    R.string.download_completed_with_errors,
                                    completed,
                                    completed + failed,
                                    failed,
                                )
                            }
                        val notification =
                            notificationHelper.buildDownloadCompletedNotification(
                                context,
                                R.drawable.baseline_downloaded,
                                null,
                                title,
                            )
                        // Fixed ID so a new summary replaces the previous one.
                        NotificationUtil.setNotification(
                            context,
                            NOTIFICATION_ID + 1,
                            notification,
                        )
                    }
                }

                else -> Unit
            }
        }

        override fun onDownloadsPausedChanged(
            downloadManager: DownloadManager,
            downloadsPaused: Boolean,
        ) {
            if (downloadsPaused) {
                downloadManager.resumeDownloads()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1000
        const val JOB_ID = 1000
    }
}