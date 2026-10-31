package com.appistan.installer

import java.io.File

class PackageFiles(private val privateRoot: File, private val downloadRoot: File?) {
    private fun appDirectory(root: File, id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+"))) { "Identificador de aplicación inválido" }
        return File(root, id)
    }

    fun prepareDownload(id: String, artifactId: Long): File {
        require(artifactId > 0)
        val root = requireNotNull(downloadRoot) { "El almacenamiento de descargas no está disponible" }
        val directory = appDirectory(File(root, "appistan"), id)
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo preparar la carpeta de descarga" }
        return File(directory, "$artifactId.apk").also { remove(it, root) }
    }

    fun deleteApp(id: String) {
        remove(appDirectory(privateRoot, id), privateRoot)
        downloadRoot?.let { remove(appDirectory(File(it, "appistan"), id), it) }
    }

    fun deleteLegacy(file: File) {
        val root = downloadRoot?.canonicalFile ?: return
        val directory = file.canonicalFile.parentFile ?: return
        if (directory.parentFile == root && legacyDirectory(directory)) remove(directory, root)
    }

    fun prune(activeIds: Set<String>) {
        privateRoot.listFiles()?.filter { it.name !in activeIds }?.forEach { remove(it, privateRoot) }
        downloadRoot?.let { root ->
            File(root, "appistan").listFiles()?.filter { it.name !in activeIds }?.forEach { remove(it, root) }
            // Old releases used random folders; keep them while any old download could still be active.
            if (activeIds.isEmpty()) root.listFiles()?.filter(::legacyDirectory)?.forEach { remove(it, root) }
        }
    }

    private fun legacyDirectory(file: File) = file.name.matches(Regex("appistan-[a-fA-F0-9-]{36}"))

    private fun remove(file: File, root: File) {
        check(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Ruta temporal fuera de AppIstan" }
        check(!file.exists() || file.deleteRecursively()) { "No se pudo borrar el APK anterior. Reintenta para liberar los archivos temporales." }
    }
}
