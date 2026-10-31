package com.appistan.installer

import java.util.Base64
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

object ReleasePolicy {
    fun needsUpdate(installed: Long?, remote: Long) = installed == null || remote == 0L || remote > installed
    fun compatibleAbis(candidate: List<String>, device: List<String>) = candidate.isEmpty() || candidate.any { normalizeAbi(it) in device.map(::normalizeAbi) }
    fun normalizeAbi(abi: String) = when (abi.lowercase().replace('-', '_')) {
        "x64", "x86_64" -> "x86_64"
        "arm64", "arm64_v8a" -> "arm64_v8a"
        "arm", "armeabi_v7a" -> "armeabi_v7a"
        else -> abi.lowercase().replace('-', '_')
    }
    fun trustedSigners(candidate: Set<String>, allowed: Set<String>) = candidate.isNotEmpty() && candidate.all { it in allowed }
    fun requireHttps(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.userInfo == null) { "La fuente no ofrece una descarga HTTPS válida" }
    }
}

class ApkSource {
    fun resolve(app: CatalogApp, device: DeviceProfile): AppRelease {
        try { return resolveAptoide(app, device) } catch (error: Exception) {
            // Official URLs never substitute an Android package with a different variant.
            val official = when (app.id) {
                "com.whatsapp" -> "https://www.whatsapp.com/android/current/WhatsApp.apk"
                "us.zoom.videomeetings" -> "https://zoom.us/client/latest/zoom.apk"
                else -> null
            } ?: throw error
            return AppRelease(app.id, 0, "Última oficial", listOf(ApkArtifact("base", official, 0)), app.domain)
        }
    }

    private fun resolveAptoide(app: CatalogApp, device: DeviceProfile): AppRelease {
        require(app.id.matches(Regex("[a-zA-Z0-9_.]+")))
        val encoded = encodeDeviceQuery(device)
        val response = getJson("https://ws75.aptoide.com/api/7/app/get/package_name=${app.id}/aab=1/not_apk_tags=alpha,beta/q=$encoded/country=EC/lang=es_EC")
        require(response.getJSONObject("info").getString("status") == "OK") { "La fuente no dispone de esta aplicación" }
        val meta = response.getJSONObject("nodes").getJSONObject("meta").getJSONObject("data")
        return parseAptoide(meta, app.id, device)
    }

    companion object {
        fun encodeDeviceQuery(device: DeviceProfile): String {
            val query = "maxSdk=${device.sdk}&maxScreen=normal&maxGles=3.2&myCPU=${device.abis.joinToString(",")}&myDensity=${device.density}"
            // Aptoide's SBASE64 validator rejects '=' padding.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(query.toByteArray(Charsets.UTF_8))
        }
        fun getJson(url: String): JSONObject {
            ReleasePolicy.requireHttps(url)
            val connection = URI(url).toURL().openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "AppIstan/${BuildConfig.VERSION_NAME}")
                when (val code = connection.responseCode) {
                    404 -> error("No disponible en esta fuente")
                    403 -> error("La fuente no permite esta descarga")
                    429 -> error("La fuente está ocupada; vuelve a intentarlo")
                    in 200..299 -> Unit
                    else -> error("La fuente respondió HTTP $code")
                }
                return connection.inputStream.bufferedReader().use { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(text.length + count <= 4_000_000) { "Respuesta de catálogo demasiado grande" }
                        text.append(buffer, 0, count)
                    }
                    JSONObject(text.toString())
                }
            } finally { connection.disconnect() }
        }
        fun parseAptoide(meta: JSONObject, expectedPackage: String, device: DeviceProfile): AppRelease {
            require(meta.getString("package") == expectedPackage) { "La fuente devolvió otra aplicación" }
            val file = meta.getJSONObject("file")
            val hardware = file.getJSONObject("hardware")
            require(hardware.optInt("sdk") <= device.sdk) { "Requiere una versión de Android más reciente" }
            require(ReleasePolicy.compatibleAbis(hardware.optJSONArray("cpus")?.strings().orEmpty(), device.abis)) { "No hay una variante compatible con este procesador" }
            require(file.optJSONArray("tags")?.strings().orEmpty().none { it.equals("beta", true) || it.equals("alpha", true) }) { "La fuente solo ofrece una versión de prueba" }
            require(meta.optJSONObject("obb") == null) { "Esta versión requiere archivos OBB; no disponible para este lote" }
            val files = mutableListOf(ApkArtifact("base", file.getString("path"), file.getLong("filesize"), file.optString("md5sum")))
            meta.optJSONObject("aab")?.let { bundle ->
                val splits = bundle.optJSONArray("splits")?.objects().orEmpty()
                val required = bundle.optJSONArray("required_split_types")?.strings().orEmpty()
                required.forEach { type -> require(splits.any { it.optString("type") == type }) { "La fuente no incluye todos los APK necesarios" } }
                // Aptoide's device query returns the matching configuration splits. Keep the complete set.
                splits.forEach { split -> files += ApkArtifact(split.getString("name"), split.getString("path"), split.getLong("filesize"), split.optString("md5sum")) }
            }
            require(files.size <= 100 && files.map { it.name }.distinct().size == files.size) { "Conjunto de APK inválido" }
            files.forEach { ReleasePolicy.requireHttps(it.url); require(it.size in 0..5_000_000_000L) }
            return AppRelease(expectedPackage, file.getLong("vercode"), file.getString("vername"), files, "Aptoide")
        }
    }
}
