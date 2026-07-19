package com.novelreader.scrape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry の規約ゲート（3値解決）の単体テスト。net.URI と純ロジックのみ＝素の JVM で動く。
 */
class SiteAdapterRegistryTest {

    private val registry = SiteAdapterRegistry()

    @Test
    fun kakuyomuUrl_resolvesToSupported() {
        val r = registry.resolve("https://kakuyomu.jp/works/16816927859675616240/episodes/1")
        assertTrue(r is SiteAdapterRegistry.Resolution.Supported)
        r as SiteAdapterRegistry.Resolution.Supported
        assertEquals("kakuyomu", r.adapter.siteKey)
        assertEquals("https://kakuyomu.jp/works/16816927859675616240", r.workUrl)
    }

    @Test
    fun narouUrl_isBlockedByTerms() {
        // 本文の機械取得が規約違反（ADR 0010/0012）＝自前 DL しない・公式へ逃がす。
        val r = registry.resolve("https://ncode.syosetu.com/n1234ab/")
        assertTrue(r is SiteAdapterRegistry.Resolution.Blocked)
        assertEquals("小説家になろう", (r as SiteAdapterRegistry.Resolution.Blocked).hostLabel)
    }

    @Test
    fun narouR18Url_isBlocked() {
        val r = registry.resolve("https://novel18.syosetu.com/n5678cd/")
        assertTrue(r is SiteAdapterRegistry.Resolution.Blocked)
    }

    @Test
    fun unknownSite_isUnsupported() {
        val r = registry.resolve("https://example.com/novel/1")
        assertEquals(SiteAdapterRegistry.Resolution.Unsupported, r)
    }

    @Test
    fun garbageInput_isUnsupported() {
        assertEquals(SiteAdapterRegistry.Resolution.Unsupported, registry.resolve("not a url"))
    }
}
