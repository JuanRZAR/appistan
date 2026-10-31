package com.appistan.installer

import android.content.pm.PackageInstaller
import org.junit.Assert.*
import org.junit.Test

class PlayServicesPolicyTest {
    @Test fun translatedEmulatorKeepsItsPlatformPlayServicesBuild() {
        assertTrue(PlayServicesPolicy.systemManagedRuntime(listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")))
        assertFalse(PlayServicesPolicy.systemManagedRuntime(listOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(PlayServicesPolicy.systemManagedRuntime(listOf("x86_64", "x86")))
    }
    @Test fun skipsTheSystemVersionThatAlreadyRejectedAnUpdate() {
        assertTrue(PlayServicesPolicy.blockedForCurrentSystem(241017026, "build-a", 241017026, "build-a"))
        assertFalse(PlayServicesPolicy.blockedForCurrentSystem(241017026, "build-a", -1, null))
    }
    @Test fun systemUpdateAllowsCheckingAgain() {
        assertFalse(PlayServicesPolicy.blockedForCurrentSystem(251000000, "build-a", 241017026, "build-a"))
        assertFalse(PlayServicesPolicy.blockedForCurrentSystem(241017026, "build-b", 241017026, "build-a"))
    }
    @Test fun temporaryFailuresDoNotDisableUpdates() {
        assertTrue(PlayServicesPolicy.permanentRejection(PackageInstaller.STATUS_FAILURE_INVALID))
        assertTrue(PlayServicesPolicy.permanentRejection(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE))
        assertFalse(PlayServicesPolicy.permanentRejection(PackageInstaller.STATUS_FAILURE_STORAGE))
        assertFalse(PlayServicesPolicy.permanentRejection(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertFalse(PlayServicesPolicy.permanentRejection(PackageInstaller.STATUS_FAILURE_BLOCKED))
    }
    @Test fun recoversTheRejectionRecordedByThePreviousVersion() {
        val rejected = QueueItem("com.google.android.gms", "Google Play Services", 0, Phase.FAILED, "Android rechazó esta actualización de Play Services. El resto del lote puede continuar.")
        assertTrue(PlayServicesPolicy.previousRejection(rejected))
        assertFalse(PlayServicesPolicy.previousRejection(rejected.copy(message = "Se interrumpió la conexión de descarga")))
    }
}
