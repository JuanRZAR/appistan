package com.appistan.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

data class VerifiedApk(val versionCode: Long, val versionName: String, val signers: Set<String>)

class PackageChecks(private val context: Context, private val catalog: Catalog) {
    private val pm get() = context.packageManager
    @Suppress("DEPRECATION")
    fun installed(id: String): PackageInfo? = try { pm.getPackageInfo(id, flags()) } catch (_: PackageManager.NameNotFoundException) { null }
    @Suppress("DEPRECATION")
    fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certificates.orEmpty().map { digest(it.toByteArray(), "SHA-256") }.toSet()
    }
    fun trusted(id: String) = catalog.pins(id) + installed(id)?.let(::signers).orEmpty()
    @Suppress("DEPRECATION")
    fun verify(id: String, release: AppRelease, files: List<File>): VerifiedApk {
        require(files.isNotEmpty() && files.size == release.files.size) { "Faltan archivos de instalación" }
        val allowed = trusted(id)
        require(allowed.isNotEmpty()) { "Todavía no hay un certificado original verificado para esta aplicación" }
        val base = pm.getPackageArchiveInfo(files.first().absolutePath, flags()) ?: error("El APK base está dañado o es incompatible")
        require(base.packageName == id) { "El archivo no corresponde a la aplicación seleccionada" }
        val code = version(base)
        require(release.versionCode == 0L || code == release.versionCode) { "La versión del archivo no coincide con la anunciada" }
        var baseSigners: Set<String> = emptySet()
        files.forEachIndexed { index, apk ->
            val artifact = release.files[index]
            require(artifact.size == 0L || apk.length() == artifact.size) { "La descarga está incompleta" }
            if (artifact.md5.isNotEmpty()) require(hash(apk, "MD5").equals(artifact.md5, true)) { "El archivo descargado está dañado" }
            val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
            require(result.isVerified) { "La firma criptográfica del APK no es válida" }
            val actual = result.signerCertificates.map { digest(it.encoded, "SHA-256") }.toSet()
            require(ReleasePolicy.trustedSigners(actual, allowed)) { "El APK no tiene una firma original admitida" }
            if (index == 0) baseSigners = actual else require(actual == baseSigners) { "Los APK divididos tienen firmas diferentes" }
        }
        return VerifiedApk(code, base.versionName.orEmpty(), baseSigners)
    }
    private fun flags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    companion object {
        @Suppress("DEPRECATION") fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun digest(data: ByteArray, algorithm: String) = MessageDigest.getInstance(algorithm).digest(data).joinToString("") { "%02X".format(it) }
        fun hash(file: File, algorithm: String): String {
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
            return digest.digest().joinToString("") { "%02X".format(it) }
        }
    }
}
