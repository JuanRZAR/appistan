package com.appistan.installer

import org.junit.Assert.*
import org.junit.Test

class BatchPolicyTest {
    private fun row(position: Int, phase: Phase) = QueueItem("app.$position", "App $position", position, phase)

    @Test fun waitsForEveryDownloadAndVerification() {
        for (phase in listOf(Phase.QUEUED, Phase.RESOLVING, Phase.DOWNLOADING, Phase.VERIFYING)) {
            val rows = listOf(row(0, Phase.READY), row(1, phase))
            assertTrue(BatchPolicy.hasPendingDownloads(rows))
            assertNull(BatchPolicy.nextInstall(rows))
        }
    }
    @Test fun startsInBatchOrderOnlyAfterEverythingIsPrepared() {
        val first = row(0, Phase.READY)
        assertEquals(first, BatchPolicy.nextInstall(listOf(row(2, Phase.READY), first, row(1, Phase.READY))))
    }
    @Test fun neverStartsASecondInstallerWhileTheFirstIsActive() {
        assertNull(BatchPolicy.nextInstall(listOf(row(0, Phase.INSTALLING), row(1, Phase.READY))))
        assertNull(BatchPolicy.nextInstall(listOf(row(0, Phase.AWAITING_USER), row(1, Phase.READY))))
    }
    @Test fun failedSkippedAndCancelledAppsDoNotBlockThePreparedApps() {
        val next = row(3, Phase.READY)
        assertEquals(next, BatchPolicy.nextInstall(listOf(row(0, Phase.SKIPPED), row(1, Phase.FAILED), row(2, Phase.CANCELLED), next)))
    }
    @Test fun retryingAnAppReturnsToTheDownloadPhase() {
        assertNull(BatchPolicy.nextInstall(listOf(row(0, Phase.SUCCESS), row(1, Phase.QUEUED), row(2, Phase.READY))))
    }
    @Test fun emptyAndFinishedBatchesDoNotStartAnInstaller() {
        assertNull(BatchPolicy.nextInstall(emptyList()))
        assertNull(BatchPolicy.nextInstall(listOf(row(0, Phase.SUCCESS), row(1, Phase.SKIPPED))))
        assertEquals(1, BatchPolicy.preparedCount(listOf(row(0, Phase.READY), row(1, Phase.DOWNLOADING))))
    }
}
