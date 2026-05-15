package com.neo.chevere.data.download

import androidx.work.Data
import androidx.work.WorkInfo
import com.neo.chevere.domain.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.UUID

class WorkManagerModelDownloadManagerTest {

    private lateinit var downloadManager: WorkManagerModelDownloadManager

    @Before
    fun setup() {
        downloadManager = WorkManagerModelDownloadManager(mock())
    }

    @Test
    fun mapWorkInfo_whenRunningWithProgress_returnsProgress() {
        val progressData = Data.Builder().putInt("progress", 45).build()
        val workInfo = createWorkInfo(WorkInfo.State.RUNNING, progressData)

        val result = downloadManager.mapWorkInfoToDownloadProgress(workInfo)

        assertTrue(result is DownloadProgress.Progress)
        assertEquals(45, (result as DownloadProgress.Progress).percent)
    }

    @Test
    fun mapWorkInfo_whenSucceeded_returnsFinished() {
        val workInfo = createWorkInfo(WorkInfo.State.SUCCEEDED)

        val result = downloadManager.mapWorkInfoToDownloadProgress(workInfo)

        assertEquals(DownloadProgress.Finished, result)
    }

    @Test
    fun mapWorkInfo_whenFailedWithError_returnsError() {
        val errorData = Data.Builder().putString("error", "Disk Full").build()
        val workInfo = createWorkInfo(WorkInfo.State.FAILED, outputData = errorData)

        val result = downloadManager.mapWorkInfoToDownloadProgress(workInfo)

        assertTrue(result is DownloadProgress.Error)
        assertEquals("Disk Full", (result as DownloadProgress.Error).message)
    }

    @Test
    fun mapWorkInfo_whenEnqueued_returnsZeroProgress() {
        val workInfo = createWorkInfo(WorkInfo.State.ENQUEUED)

        val result = downloadManager.mapWorkInfoToDownloadProgress(workInfo)

        assertTrue(result is DownloadProgress.Progress)
        assertEquals(0, (result as DownloadProgress.Progress).percent)
    }

    @Test
    fun mapWorkInfo_whenCancelled_returnsError() {
        val workInfo = createWorkInfo(WorkInfo.State.CANCELLED)

        val result = downloadManager.mapWorkInfoToDownloadProgress(workInfo)

        assertTrue(result is DownloadProgress.Error)
        assertEquals("Download cancelled.", (result as DownloadProgress.Error).message)
    }

    private fun createWorkInfo(
        state: WorkInfo.State,
        progress: Data = Data.EMPTY,
        outputData: Data = Data.EMPTY
    ): WorkInfo {
        return WorkInfo(
            UUID.randomUUID(),
            state,
            setOf(),
            outputData,
            progress,
            0,
            0
        )
    }
}
