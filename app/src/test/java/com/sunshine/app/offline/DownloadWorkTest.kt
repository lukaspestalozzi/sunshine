package com.sunshine.app.offline

import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadWorkTest {
    @Test
    fun `no unfinished work is idle`() {
        assertEquals(DownloadWork.IDLE, downloadWork(listOf(SUCCEEDED to NOT_STOPPED), online = true))
    }

    @Test
    fun `offline wins over running, e_g_ on Wi-Fi without internet`() {
        assertEquals(DownloadWork.WAITING_FOR_NETWORK, downloadWork(listOf(RUNNING to NOT_STOPPED), online = false))
    }

    @Test
    fun `a running entry counts even behind its appended successor`() {
        assertEquals(DownloadWork.RUNNING, downloadWork(listOf(BLOCKED to NOT_STOPPED, RUNNING to NOT_STOPPED), online = true))
    }

    @Test
    fun `work stopped for low storage waits for storage`() {
        assertEquals(
            DownloadWork.WAITING_FOR_STORAGE,
            downloadWork(listOf(ENQUEUED to WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW), online = true),
        )
    }

    @Test
    fun `enqueued work that has not started yet is waiting`() {
        assertEquals(DownloadWork.IDLE, downloadWork(listOf(ENQUEUED to NOT_STOPPED), online = true))
    }

    private companion object {
        const val NOT_STOPPED = WorkInfo.STOP_REASON_NOT_STOPPED
    }
}
