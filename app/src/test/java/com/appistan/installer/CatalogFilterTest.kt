package com.appistan.installer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class CatalogFilterTest {
    private val assets = File(requireNotNull(System.getProperty("appAssets")))
    private val apps = JSONArray(File(assets, "catalog.json").readText()).objects().map {
        CatalogApp(it.getString("id"), it.getString("name"), it.getString("category"), it.getString("domain"), it.optInt("minSdk", 26), it.optJSONArray("abis")?.strings().orEmpty())
    }

    @Test fun commonFilterContainsExactlyTheRequestedAppsInOrder() {
        val common = CatalogFilter.apply(apps, "Comunes", "")
        assertEquals(listOf("Facebook", "WhatsApp", "TikTok", "Messenger"), common.map { it.name })
        assertTrue(common.all { it.category == "Social" })
        assertTrue(CatalogFilter.apply(apps, "Social", "").containsAll(common))
        assertFalse(common.any { it.id == "com.facebook.lite" || it.id == "com.whatsapp.w4b" })
    }

    @Test fun searchCombinesWithCommonFilter() {
        assertEquals(listOf("WhatsApp"), CatalogFilter.apply(apps, "Comunes", "  WHATSAPP  ").map { it.name })
        assertTrue(CatalogFilter.apply(apps, "Comunes", "Netflix").isEmpty())
        assertEquals(apps, CatalogFilter.apply(apps, "Todas", ""))
    }

    @Test fun everyRetainedAppHasAnIndependentlyVerifiedSigner() {
        val trust = JSONObject(File(assets, "trust.json").readText())
        assertTrue(apps.isNotEmpty())
        assertEquals(apps.size, apps.map { it.id }.distinct().size)
        apps.forEach { assertTrue("Missing signer: ${it.id}", trust.optJSONObject(it.id)?.optJSONArray("sha256")?.length()?.let { size -> size > 0 } == true) }
    }

    @Test fun incompatibleAndroidAndCpuAreHidden() {
        val universal = CatalogApp("app.universal", "Universal", "Social", "example.com")
        val newer = universal.copy(id = "app.newer", minSdk = 32)
        val armOnly = universal.copy(id = "app.arm", abis = listOf("arm64-v8a"))
        val device = DeviceProfile(28, listOf("x86_64"), 240, "es-EC")
        assertEquals(listOf(universal), CatalogFilter.compatible(listOf(universal, newer, armOnly), device))
    }

    @Test fun queryForPhysicalPhonesDoesNotContainRejectedPadding() {
        val device = DeviceProfile(36, listOf("arm64-v8a", "armeabi-v7a"), 480, "es-EC")
        val encoded = ApkSource.encodeDeviceQuery(device)
        assertFalse(encoded.contains('='))
        assertFalse(encoded.contains('\n'))
        val decoded = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        assertEquals("maxSdk=36&maxScreen=normal&maxGles=3.2&myCPU=arm64-v8a,armeabi-v7a&myDensity=480", decoded)
    }
}
