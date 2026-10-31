package com.appistan.installer

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ReleasePolicyTest {
    private val device = DeviceProfile(28, listOf("x86_64", "armeabi-v7a"), 240, "es-EC")
    private fun meta() = JSONObject("""{"package":"com.test.app","file":{"vercode":42,"vername":"4.2","filesize":128,"path":"https://pool.apk.aptoide.com/base.apk","hardware":{"sdk":26,"cpus":["x86-64"]},"tags":[]},"aab":{"required_split_types":["ABI"],"splits":[{"name":"config.x86_64","type":"ABI","path":"https://pool.apk.aptoide.com/x86.apk","filesize":32}]}}""")
    @Test fun updatesNeverDowngrade() {
        assertTrue(ReleasePolicy.needsUpdate(null, 1))
        assertTrue(ReleasePolicy.needsUpdate(10, 11))
        assertFalse(ReleasePolicy.needsUpdate(10, 10))
        assertFalse(ReleasePolicy.needsUpdate(11, 10))
        assertTrue(ReleasePolicy.needsUpdate(10, 0)) // official APK metadata is checked after downloading
    }
    @Test fun cpuAliasesAreNormalized() {
        assertTrue(ReleasePolicy.compatibleAbis(listOf("x86-64"), device.abis))
        assertFalse(ReleasePolicy.compatibleAbis(listOf("arm64-v8a"), listOf("x86")))
        assertTrue(ReleasePolicy.compatibleAbis(emptyList(), device.abis))
    }
    @Test fun everySignerMustBeTrusted() {
        assertTrue(ReleasePolicy.trustedSigners(setOf("A"), setOf("A", "B")))
        assertFalse(ReleasePolicy.trustedSigners(setOf("A", "EVIL"), setOf("A")))
        assertFalse(ReleasePolicy.trustedSigners(emptySet(), setOf("A")))
    }
    @Test fun parsesBaseAndRequiredSplit() {
        val release = ApkSource.parseAptoide(meta(), "com.test.app", device)
        assertEquals(42L, release.versionCode)
        assertEquals(2, release.files.size)
        assertEquals(release, AppRelease.decode(release.encode()))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongPackage() { ApkSource.parseAptoide(meta(), "com.other", device) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNewerAndroid() { ApkSource.parseAptoide(meta(), "com.test.app", device.copy(sdk = 25)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingRequiredSplit() {
        val json = meta(); json.getJSONObject("aab").getJSONArray("required_split_types").put("DENSITY")
        ApkSource.parseAptoide(json, "com.test.app", device)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCleartextDownload() {
        val json = meta(); json.getJSONObject("file").put("path", "http://example.com/malicious.apk")
        ApkSource.parseAptoide(json, "com.test.app", device)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBetaChannel() {
        val json = meta(); json.getJSONObject("file").getJSONArray("tags").put("beta")
        ApkSource.parseAptoide(json, "com.test.app", device)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedObb() {
        val json = meta().put("obb", JSONObject().put("main", "asset.obb"))
        ApkSource.parseAptoide(json, "com.test.app", device)
    }
}
