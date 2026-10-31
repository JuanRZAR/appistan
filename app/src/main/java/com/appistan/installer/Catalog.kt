package com.appistan.installer

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class CatalogApp(val id: String, val name: String, val category: String, val domain: String)
data class DeviceProfile(val sdk: Int, val abis: List<String>, val density: Int, val locale: String)
data class ApkArtifact(val name: String, val url: String, val size: Long, val md5: String = "")
data class AppRelease(val packageName: String, val versionCode: Long, val versionName: String, val files: List<ApkArtifact>, val source: String) {
    fun encode() = JSONObject().put("package", packageName).put("code", versionCode).put("version", versionName)
        .put("source", source).put("files", JSONArray().apply { files.forEach { put(JSONObject().put("name", it.name).put("url", it.url).put("size", it.size).put("md5", it.md5)) } }).toString()
    companion object {
        fun decode(value: String): AppRelease {
            val json = JSONObject(value)
            return AppRelease(json.getString("package"), json.getLong("code"), json.getString("version"),
                json.getJSONArray("files").objects().map { ApkArtifact(it.getString("name"), it.getString("url"), it.getLong("size"), it.optString("md5")) }, json.getString("source"))
        }
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun normalizeFingerprint(value: String) = value.replace(":", "").uppercase()

class Catalog(context: Context) {
    val apps = JSONArray(context.assets.open("catalog.json").bufferedReader().use { it.readText() }).objects()
        .map { CatalogApp(it.getString("id"), it.getString("name"), it.getString("category"), it.getString("domain")) }
    val playServices = CatalogApp("com.google.android.gms", "Google Play Services", "Sistema", "www.google.com")
    private val trust = JSONObject(context.assets.open("trust.json").bufferedReader().use { it.readText() })
    fun pins(packageName: String): Set<String> = trust.optJSONObject(packageName)?.optJSONArray("sha256")?.strings()?.map(::normalizeFingerprint)?.toSet().orEmpty()
    fun find(id: String) = if (id == playServices.id) playServices else apps.firstOrNull { it.id == id }
    companion object {
        fun device(context: Context) = DeviceProfile(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), context.resources.displayMetrics.densityDpi, context.resources.configuration.locales[0].toLanguageTag())
    }
}
