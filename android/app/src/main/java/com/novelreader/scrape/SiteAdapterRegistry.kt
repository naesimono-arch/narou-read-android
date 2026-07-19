package com.novelreader.scrape

import com.novelreader.scrape.adapter.KakuyomuAdapter

/**
 * URL → アダプタ解決の中心。**規約ゲートをここに集約**する。
 *
 * 規約線（handover 2026-07-20 裁定・確定事項①②）:
 * - [adapters] に載せるのは「利用規約で自動取得が禁止でない」サイトのみ。載せない＝自前 DL しない。
 * - [blockedHosts] は自前 DL を明示的に禁じるサイト（本文の機械取得が規約違反＝なろうグループ等）。
 *   ここに一致した URL は [Resolution.Blocked] を返し、UI は**公式サイトで読む導線のみ**を出す（＝逃げ道）。
 * - どちらにも当たらない未知サイトは [Resolution.Unsupported]（将来アダプタ追加候補・現状は公式サイト送り）。
 *
 * なぜ「非登録＝黙って無視」でなく明示3値にするか: ユーザーがなろう作品 URL を貼ったとき「未対応」でなく
 * 「これは公式サイト/API で読む対象」と正しく案内するため（なろうは発見層 API＋WebView 読書が正路）。
 */
class SiteAdapterRegistry(
    private val adapters: List<NovelSiteAdapter> = defaultAdapters(),
) {
    sealed interface Resolution {
        /** 自前 DL 可能なサイト。[adapter] と正規化済み作品 URL を持つ。 */
        data class Supported(val adapter: NovelSiteAdapter, val workUrl: String) : Resolution

        /** 規約で自前 DL 不可のサイト。公式サイト/API へ逃がす。[hostLabel] は表示用。 */
        data class Blocked(val hostLabel: String) : Resolution

        /** サイト自体が未知（アダプタ未整備）。公式サイト直行を案内。 */
        data object Unsupported : Resolution
    }

    fun resolve(inputUrl: String): Resolution {
        val host = hostOf(inputUrl) ?: return Resolution.Unsupported

        blockedHosts.firstOrNull { host == it.host || host.endsWith(".${it.host}") }?.let {
            return Resolution.Blocked(it.label)
        }

        for (adapter in adapters) {
            val work = adapter.canonicalWorkUrl(inputUrl)
            if (work != null) return Resolution.Supported(adapter, work)
        }
        return Resolution.Unsupported
    }

    companion object {
        /** 自前 DL を明示的に禁じるサイト（本文の機械取得が規約違反）。ADR 0010/0012＝なろうグループ。 */
        private val blockedHosts = listOf(
            BlockedHost("syosetu.com", "小説家になろう"),
            BlockedHost("ncode.syosetu.com", "小説家になろう"),
            BlockedHost("novel18.syosetu.com", "ノクターン/ムーンライト"),
            BlockedHost("noc.syosetu.com", "ノクターン"),
            BlockedHost("mnlt.syosetu.com", "ムーンライト"),
            BlockedHost("mid.syosetu.com", "ミッドナイト"),
        )

        private fun defaultAdapters(): List<NovelSiteAdapter> = listOf(
            KakuyomuAdapter(),
        )

        private fun hostOf(url: String): String? = runCatching {
            java.net.URI(url.trim()).host?.lowercase()
        }.getOrNull()
    }

    private data class BlockedHost(val host: String, val label: String)
}
