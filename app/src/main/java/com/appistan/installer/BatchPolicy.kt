package com.appistan.installer

object BatchPolicy {
    private val downloadPhases = setOf(Phase.QUEUED, Phase.RESOLVING, Phase.DOWNLOADING, Phase.VERIFYING)
    fun hasPendingDownloads(rows: List<QueueItem>) = rows.any { it.phase in downloadPhases }
    fun preparedCount(rows: List<QueueItem>) = rows.count { it.phase !in downloadPhases }
    fun nextInstall(rows: List<QueueItem>): QueueItem? {
        if (hasPendingDownloads(rows) || rows.any { it.phase == Phase.INSTALLING || it.phase == Phase.AWAITING_USER }) return null
        return rows.filter { it.phase == Phase.READY }.minByOrNull { it.position }
    }
}
