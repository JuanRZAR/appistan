package com.appistan.installer

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PackageFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val privateRoot get() = File(temporary.root, "packages").apply { mkdirs() }
    private val downloadRoot get() = File(temporary.root, "downloads").apply { mkdirs() }
    private val files get() = PackageFiles(privateRoot, downloadRoot)
    private fun apk(file: File) = file.apply { requireNotNull(parentFile).mkdirs(); writeText("apk") }

    @Test fun completionDeletesPrivateBaseSplitsAndDownloadedCopies() {
        apk(File(privateRoot, "com.example.app/0.apk"))
        apk(File(privateRoot, "com.example.app/1.apk"))
        apk(files.prepareDownload("com.example.app", 1))
        apk(files.prepareDownload("com.example.app", 2))
        val other = apk(files.prepareDownload("com.example.other", 3))
        files.deleteApp("com.example.app")
        assertFalse(File(privateRoot, "com.example.app").exists())
        assertFalse(File(downloadRoot, "appistan/com.example.app").exists())
        assertTrue(other.exists())
    }

    @Test fun retryDeletesOldFilesBeforePreparingTheNextDownload() {
        val previous = apk(files.prepareDownload("com.example.app", 1))
        apk(File(privateRoot, "com.example.app/0.apk"))
        files.deleteApp("com.example.app")
        val next = apk(files.prepareDownload("com.example.app", 2))
        assertFalse(previous.exists())
        assertFalse(File(privateRoot, "com.example.app").exists())
        assertEquals(listOf(next), requireNotNull(next.parentFile).listFiles()!!.toList())
        assertFalse(files.prepareDownload("com.example.app", 2).exists())
    }

    @Test fun recoveryRemovesOrphansButPreservesTheActiveBatchAndUnrelatedFiles() {
        val active = apk(files.prepareDownload("com.example.active", 1))
        val activePrivate = apk(File(privateRoot, "com.example.active/0.apk"))
        val orphan = apk(files.prepareDownload("com.example.orphan", 2))
        val orphanPrivate = apk(File(privateRoot, "com.example.orphan/0.apk"))
        val legacy = apk(File(downloadRoot, "appistan-12345678-1234-1234-1234-123456789012/base.apk"))
        val unrelated = apk(File(downloadRoot, "personal/keep.apk"))
        files.prune(setOf("com.example.active"))
        assertTrue(active.exists())
        assertTrue(activePrivate.exists())
        assertTrue(legacy.exists())
        assertFalse(orphan.exists())
        assertFalse(orphanPrivate.exists())
        files.prune(emptySet())
        assertFalse(active.exists())
        assertFalse(requireNotNull(legacy.parentFile).exists())
        assertTrue(unrelated.exists())
    }

    @Test fun legacyDownloadIsRemovedWithoutTouchingOtherApps() {
        val old = apk(File(downloadRoot, "appistan-12345678-1234-1234-1234-123456789012/base.apk"))
        val other = apk(files.prepareDownload("com.example.other", 1))
        files.deleteLegacy(old)
        assertFalse(requireNotNull(old.parentFile).exists())
        files.deleteLegacy(other)
        assertTrue(other.exists())
    }

    @Test fun rejectsPathsOutsideItsOwnFolders() {
        val outside = apk(File(temporary.root, "keep.apk"))
        for (id in listOf("..", "../escape", "/tmp", "com.example/../../escape")) {
            assertThrows(IllegalArgumentException::class.java) { files.deleteApp(id) }
        }
        files.deleteLegacy(outside)
        assertTrue(outside.exists())
    }
}
