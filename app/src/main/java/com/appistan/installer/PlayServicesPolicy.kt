package com.appistan.installer

import android.content.pm.PackageInstaller

object PlayServicesPolicy {
    const val managedMessage = "Play Services se mantiene con la versión del sistema. Continúas con las aplicaciones seleccionadas."

    fun blockedForCurrentSystem(installedVersion: Long, deviceBuild: String, blockedVersion: Long, blockedBuild: String?) =
        installedVersion >= 0 && installedVersion == blockedVersion && deviceBuild == blockedBuild

    fun systemManagedRuntime(abis: List<String>): Boolean {
        // Native translation uses the platform's own Play Services variant.
        val supported = abis.map(ReleasePolicy::normalizeAbi)
        return supported.any { it.startsWith("x86") } && supported.any { it.startsWith("arm") }
    }

    fun permanentRejection(status: Int) = status in setOf(
        PackageInstaller.STATUS_FAILURE_INVALID,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        PackageInstaller.STATUS_FAILURE_CONFLICT
    )

    fun previousRejection(row: QueueItem): Boolean =
        (row.phase == Phase.SKIPPED && row.message == managedMessage) ||
            (row.phase == Phase.FAILED && (row.message.startsWith("Android rechazó esta actualización de Play Services") || row.message.contains("INSTALL_FAILED_INVALID_APK")))
}
