package com.appistan.installer

import android.app.*
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InstallEngine(private val context: AppIstan, val dao: QueueDao, private val catalog: Catalog) {
    private val lock = Mutex()
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val installer = context.packageManager.packageInstaller
    val checks = PackageChecks(context, catalog)
    private val source = ApkSource()
    private val prompts = ConcurrentHashMap<Int, Intent>()
    private val privateRoot = File(context.filesDir, "packages").apply { mkdirs() }

    suspend fun start(ids: Set<String>) = lock.withLock {
        require(dao.items().none { !it.phase.terminal }) { "Termina o cancela el lote actual" }
        require(ids.isNotEmpty()) { "Selecciona al menos una aplicación" }
        dao.items().forEach { cleanup(it.id) }
        val selected = catalog.apps.filter { it.id in ids }
        require(selected.size == ids.size) { "Selección no válida" }
        dao.newBatch((listOf(catalog.playServices) + selected).mapIndexed { position, app -> QueueItem(app.id, app.name, position) })
    }

    suspend fun recover() = lock.withLock {
        dao.items().forEach { row ->
            when (row.phase) {
                Phase.RESOLVING -> dao.put(row.copy(phase = Phase.QUEUED, message = "Reanudando consulta"))
                Phase.VERIFYING -> dao.put(row.copy(phase = Phase.DOWNLOADING, message = "Revisando archivos"))
                Phase.INSTALLING, Phase.AWAITING_USER -> {
                    val expected = runCatching { AppRelease.decode(row.release).versionCode }.getOrDefault(0)
                    val installed = checks.installed(row.id)?.let(PackageChecks::version)
                    if (installed != null && expected > 0 && installed >= expected) complete(row, Phase.SUCCESS, "Instalada")
                    else dao.put(row.copy(phase = Phase.AWAITING_USER, message = "Pulsa Reintentar para continuar la instalación"))
                }
                else -> Unit
            }
        }
    }

    suspend fun tick() = lock.withLock {
        try {
            dao.items().filter { it.phase == Phase.DOWNLOADING }.forEach { row ->
                try { refresh(row) } catch (error: Exception) { fail(row, error) }
            }
            val rows = dao.items()
            val gms = rows.firstOrNull { it.id == catalog.playServices.id }
            val available = if (gms != null && !gms.phase.terminal) listOf(gms) else rows
            var slots = 2 - rows.count { it.phase == Phase.DOWNLOADING }
            for (row in available.filter { it.phase == Phase.QUEUED }) {
                if (slots <= 0) break
                try { resolveAndDownload(row); slots-- } catch (error: Exception) { fail(row, error) }
            }
            scheduleDownloads()
        } catch (error: Exception) { android.util.Log.e("AppIstan", "No se pudo avanzar la cola", error) }
    }

    private suspend fun resolveAndDownload(row: QueueItem) {
        val app = catalog.find(row.id) ?: error("Aplicación fuera del catálogo")
        val installed = checks.installed(row.id)
        if (app.id == catalog.playServices.id && installed == null) {
            complete(row, Phase.SKIPPED, "Play Services no está instalado. Puedes continuar; algunas apps pueden necesitarlo.")
            return
        }
        require(checks.trusted(row.id).isNotEmpty()) { "No disponible: falta corroborar la firma original del desarrollador" }
        dao.put(row.copy(phase = Phase.RESOLVING, message = "Buscando versión compatible"))
        val release = source.resolve(app, Catalog.device(context))
        if (!ReleasePolicy.needsUpdate(installed?.let(PackageChecks::version), release.versionCode)) {
            complete(row.copy(release = release.encode()), Phase.SKIPPED, "Ya tienes esta versión o una más reciente")
            return
        }
        val total = release.files.sumOf { it.size }
        val required = maxOf(total * 3, 150_000_000L)
        require(StatFs(privateRoot.absolutePath).availableBytes > required) { "Espacio insuficiente para descargar e instalar" }
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("El almacenamiento de descargas no está disponible")
        require(StatFs(external.absolutePath).availableBytes > maxOf(total, 50_000_000L)) { "Espacio insuficiente para la descarga" }
        dao.put(row.copy(phase = Phase.DOWNLOADING, message = "Descargando ${release.versionName}", release = release.encode(), total = total))
        release.files.forEach { artifact -> dao.putArtifact(ArtifactRow(appId = row.id, name = artifact.name, url = artifact.url, size = artifact.size, md5 = artifact.md5)) }
    }

    private suspend fun scheduleDownloads() {
        val rows = dao.items().filter { it.phase == Phase.DOWNLOADING }
        val files = rows.flatMap { dao.artifacts(it.id) }
        var active = files.count { file ->
            file.downloadId >= 0 && downloads.query(DownloadManager.Query().setFilterById(file.downloadId)).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)
            }
        }
        for (file in files.filter { it.downloadId < 0 }) {
            if (active >= 2) break
            val row = rows.first { it.id == file.appId }
            try {
                val request = DownloadManager.Request(Uri.parse(file.url)).setTitle("${row.name} · ${file.name}")
                    .setDescription("AppIstan · Instalación por lotes").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "appistan-${UUID.randomUUID()}/base.apk")
                dao.updateArtifact(file.copy(downloadId = downloads.enqueue(request)))
                active++
            } catch (error: Exception) { fail(row, error); return }
        }
    }

    private suspend fun refresh(row: QueueItem) {
        val artifacts = dao.artifacts(row.id)
        require(artifacts.isNotEmpty()) { "La descarga se interrumpió; vuelve a intentarlo" }
        val release = AppRelease.decode(row.release)
        require(artifacts.size == release.files.size) { "La descarga se interrumpió; vuelve a intentarlo" }
        var received = 0L; var total = 0L; var finished = 0; var paused = false
        artifacts.forEach { artifact ->
            if (artifact.downloadId < 0) { total += artifact.size; return@forEach }
            downloads.query(DownloadManager.Query().setFilterById(artifact.downloadId)).use { cursor ->
                require(cursor.moveToFirst()) { "La descarga fue eliminada; vuelve a intentarlo" }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                require(status != DownloadManager.STATUS_FAILED) { downloadError(reason) }
                paused = paused || status == DownloadManager.STATUS_PAUSED
                received += cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                total += cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(artifact.size)
                if (status == DownloadManager.STATUS_SUCCESSFUL) finished++
            }
        }
        if (finished != artifacts.size) {
            dao.put(row.copy(downloaded = received, total = total, message = if (paused) "Esperando conexión o reintento de Android" else "Descargando ${release.versionName}"))
            return
        }
        dao.put(row.copy(phase = Phase.VERIFYING, message = "Verificando archivos y firma", downloaded = received, total = total))
        val directory = File(privateRoot, row.id).apply { mkdirs() }
        val privateFiles = artifacts.mapIndexed { index, artifact ->
            val destination = File(directory, "$index.apk")
            downloads.openDownloadedFile(artifact.downloadId).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> destination.outputStream().use { input.copyTo(it) } }
            }
            dao.updateArtifact(artifact.copy(privatePath = destination.absolutePath))
            destination
        }
        val verified = checks.verify(row.id, release, privateFiles)
        val actualRelease = release.copy(versionCode = verified.versionCode, versionName = verified.versionName)
        val current = checks.installed(row.id)?.let(PackageChecks::version)
        if (!ReleasePolicy.needsUpdate(current, verified.versionCode)) complete(row.copy(release = actualRelease.encode()), Phase.SKIPPED, "Ya tienes esta versión o una más reciente")
        else dao.put(row.copy(phase = Phase.READY, message = "Lista para instalar · ${verified.versionName}", release = actualRelease.encode(), downloaded = received, total = total))
    }

    suspend fun installNext(): Boolean = lock.withLock {
        val rows = dao.items()
        if (rows.any { it.phase in setOf(Phase.INSTALLING, Phase.AWAITING_USER) }) return@withLock false
        val gms = rows.firstOrNull { it.id == catalog.playServices.id && !it.phase.terminal }
        val row = (if (gms == null) rows else listOf(gms)).firstOrNull { it.phase == Phase.READY } ?: return@withLock false
        var sessionId = -1
        try {
            require(context.packageManager.canRequestPackageInstalls()) { "Activa el permiso para instalar aplicaciones desde AppIstan" }
            val release = AppRelease.decode(row.release)
            val artifacts = dao.artifacts(row.id)
            val files = artifacts.map { File(it.privatePath) }
            checks.verify(row.id, release, files)
            val installed = checks.installed(row.id)?.let(PackageChecks::version)
            if (!ReleasePolicy.needsUpdate(installed, release.versionCode)) { complete(row, Phase.SKIPPED, "La aplicación ya se actualizó"); return@withLock false }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(row.id); setAppLabel(row.name); setSize(files.sumOf { it.length() })
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            sessionId = installer.createSession(params)
            dao.put(row.copy(phase = Phase.INSTALLING, message = "Preparando instalación", sessionId = sessionId))
            installer.openSession(sessionId).use { session ->
                files.forEachIndexed { index, file -> session.openWrite("$index.apk", 0, file.length()).use { output -> file.inputStream().use { it.copyTo(output) }; session.fsync(output) } }
                val intent = Intent(context, InstallReceiver::class.java).setAction("${context.packageName}.INSTALL_STATUS").setData(Uri.parse("appistan://install/$sessionId"))
                val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                session.commit(callback.intentSender)
            }
            true
        } catch (error: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            fail(row, error); false
        }
    }

    suspend fun installStatus(sessionId: Int, status: Int, prompt: Intent?, detail: String?) {
        val row = dao.items().firstOrNull { it.sessionId == sessionId } ?: return
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (prompt == null) { fail(row, IllegalStateException("Android no devolvió la confirmación")); return }
                prompts[sessionId] = prompt
                dao.put(row.copy(phase = Phase.AWAITING_USER, message = "Confirma la instalación en Android"))
                notifyPending(row)
            }
            PackageInstaller.STATUS_SUCCESS -> { complete(row, Phase.SUCCESS, "Instalada correctamente"); prompts.remove(sessionId) }
            else -> {
                val message = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalación cancelada en Android"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Espacio insuficiente para instalar"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "La firma o la versión no permite actualizar la aplicación instalada"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Esta versión no es compatible con el dispositivo"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android bloqueó la instalación. Revisa los permisos o restricciones del dispositivo"
                    PackageInstaller.STATUS_FAILURE_INVALID -> if (row.id == catalog.playServices.id) "Android rechazó esta actualización de Play Services. El resto del lote puede continuar." else "Android rechazó el conjunto de APK para este dispositivo. Prueba más tarde con otra versión."
                    else -> "Android no pudo instalar esta aplicación. Revisa la compatibilidad y vuelve a intentarlo."
                }
                android.util.Log.w("AppIstan", "Instalación rechazada: ${row.id}, estado=$status, ${detail.orEmpty().take(180)}")
                fail(row, IllegalStateException(message)); prompts.remove(sessionId)
            }
        }
    }

    fun takePrompt(sessionId: Int): Intent? = prompts.remove(sessionId)
    fun hasPrompt(sessionId: Int) = prompts.containsKey(sessionId)

    suspend fun cancel(id: String) = lock.withLock {
        val row = dao.item(id) ?: return@withLock
        if (row.phase.terminal) return@withLock
        if (row.sessionId >= 0) { runCatching { installer.abandonSession(row.sessionId) }; prompts.remove(row.sessionId) }
        complete(row, Phase.CANCELLED, "Cancelada")
    }
    suspend fun retry(id: String) = lock.withLock {
        val row = dao.item(id) ?: return@withLock
        if (row.phase !in setOf(Phase.FAILED, Phase.CANCELLED, Phase.AWAITING_USER)) return@withLock
        if (row.sessionId >= 0) { runCatching { installer.abandonSession(row.sessionId) }; prompts.remove(row.sessionId) }
        cleanup(row.id)
        dao.put(QueueItem(row.id, row.name, row.position))
    }
    private suspend fun complete(row: QueueItem, phase: Phase, message: String) { cleanup(row.id); cancelNotification(row); dao.put(row.copy(phase = phase, message = message)) }
    private suspend fun fail(row: QueueItem, error: Exception) { complete(row, Phase.FAILED, error.message ?: "No se pudo completar esta aplicación") }
    private suspend fun cleanup(id: String) {
        dao.artifacts(id).forEach { artifact -> if (artifact.downloadId >= 0) runCatching { downloads.remove(artifact.downloadId) } }
        require(id.matches(Regex("[a-zA-Z0-9_.]+")))
        File(privateRoot, id).deleteRecursively()
        dao.deleteArtifacts(id)
    }
    private fun notifyPending(row: QueueItem) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("installs", "Confirmaciones de instalación", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, "installs").setSmallIcon(R.drawable.ic_app).setContentTitle("${row.name} está lista")
            .setContentText("Abre AppIstan para confirmar la instalación").setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(row.id.hashCode(), notification) }
    }
    private fun cancelNotification(row: QueueItem) { context.getSystemService(NotificationManager::class.java).cancel(row.id.hashCode()) }
    private fun downloadError(reason: Int) = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "No hay espacio suficiente para la descarga"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Se interrumpió la conexión de descarga"
        DownloadManager.ERROR_FILE_ERROR -> "No se pudo guardar la descarga"
        else -> "La descarga falló (código $reason); vuelve a intentarlo"
    }
}

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val app = context.applicationContext as AppIstan
        // Starting Application wakes its persistent-state pump; network work must not hold a broadcast open.
        app.scope.launch { app.engine.tick() }
    }
}
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AppIstan
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        @Suppress("DEPRECATION") val prompt = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        val pending = goAsync()
        app.scope.launch { try { app.engine.installStatus(sessionId, status, prompt, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)) } finally { pending.finish() } }
    }
}
