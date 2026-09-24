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

    /**
     * 既定 registry を何度作っても、実フェッチに使う [ScrapeHttpClient] はプロセス全体で1つであること。
     *
     * なぜ固定するか（監査 2026-08-06 C4）: per-host スロットルとグローバル床（1req/s）は client の
     * インスタンスフィールド（gate・lastRequestByHost）なので、床は client 単位でしか効かない
     * （その性質自体は ScrapeHttpClientTest.separateClients_doNotThrottleEachOther が固定）。
     * 既定 registry ごとに client を新規生成していた頃は、実フェッチする経路（取込＝DefaultBookRepository、
     * 新着照会＝NewEpisodeCheckWorker、debug 診断＝AdapterHealthBoardDialog）が互いのロックを見ず、
     * 宣言した 1req/s が registry 境界で黙って破れていた。
     *
     * 実フェッチ経路は実ネットワークを叩くため待ち時間そのものは単体テストで観測できない。よって
     * 「床を共有していること」＝**アダプタ束が同じ client を指すこと**を構造で固定する。
     * private フィールドを反射で覗くのは、この不変条件に公開面が無い（アダプタは http を公開しない）ため。
     * テスト都合で本番 API を広げるより、結線が変わったら落ちる形で反射する方を採る。
     */
    @Test
    fun defaultRegistries_shareOneHttpClient() {
        val adapters = SiteAdapterRegistry().registeredAdapters + SiteAdapterRegistry().registeredAdapters
        assertTrue("既定アダプタが空＝このテストが何も見ていない", adapters.isNotEmpty())

        // ScrapeHttpClient は equals を持たない＝distinct は参照同一性で効く。
        val clients = adapters.map { httpClientOf(it) }.distinct()

        assertEquals(
            "既定 registry を跨いで ScrapeHttpClient は1つであること（1req/s の床は client 単位）",
            1,
            clients.size,
        )
    }

    /** アダプタが握っている [ScrapeHttpClient]（private フィールド）を取り出す。無ければ結線変更＝失敗させる。 */
    private fun httpClientOf(adapter: NovelSiteAdapter): ScrapeHttpClient {
        val field = requireNotNull(
            adapter.javaClass.declaredFields.firstOrNull { it.type == ScrapeHttpClient::class.java }
        ) { "${adapter.javaClass.simpleName} が ScrapeHttpClient を保持していない（アダプタの結線が変わった）" }
        field.isAccessible = true
        return field.get(adapter) as ScrapeHttpClient
    }
}
