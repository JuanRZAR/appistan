package com.appistan.installer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile

// Debug-only, shell-protected fixture access: BlueStacks disables run-as and crashes on the instrumentation APK.
class DeviceTestProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(BuildConfig.DEBUG)
        val app = context!!.applicationContext as AppIstan
        val id = "com.appistan.fixture"
        val result = runBlocking(Dispatchers.IO) {
            when (method) {
                "catalog" -> "${app.catalog.apps.size}|${app.catalog.apps.map { it.id }.distinct().size}"
                "install-permission" -> app.packageManager.canRequestPackageInstalls().toString()
                "status" -> app.engine.dao.item(id)?.let { "${it.phase}|${it.message}" } ?: "EMPTY"
                "batch" -> app.engine.dao.items().joinToString("\n") { "${it.id}: ${it.phase} | ${it.message} | ${it.downloaded}/${it.total}" }
                "seed-v1", "seed-v2", "seed-corrupt" -> {
                    val rows = app.engine.dao.items()
                    require(rows.all { it.id == id && it.phase.terminal }) { "La cola contiene apps reales o una prueba activa" }
                    val version = if (method == "seed-v1") 1 else 2
                    val source = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "fixture-tests/fixture-v$version.apk")
                    require(source.isFile && source.length() < 2_000_000) { "Falta el APK de prueba" }
                    val target = File(app.filesDir, "packages/$id/0.apk").apply { parentFile!!.mkdirs() }
                    source.copyTo(target, overwrite = true)
                    if (method == "seed-corrupt") RandomAccessFile(target, "rw").use { it.seek(200); val old = it.readByte(); it.seek(200); it.writeByte(old.toInt() xor 0xff) }
                    val release = AppRelease(id, version.toLong(), "1.0.$version", listOf(ApkArtifact("base", "https://example.com/fixture.apk", target.length())), "Fixture local")
                    val row = QueueItem(id, "AppIstan Test", 1, Phase.VERIFYING, "Prueba ADB", release.encode())
                    app.engine.dao.newBatch(listOf(row))
                    app.engine.dao.putArtifact(ArtifactRow(appId = id, name = "base", url = release.files[0].url, size = target.length(), md5 = "", privatePath = target.absolutePath))
                    app.engine.dao.put(row.copy(phase = Phase.READY))
                    "READY"
                }
                else -> error("Método de prueba desconocido")
            }
        }
        return Bundle().apply { putString("result", result) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
