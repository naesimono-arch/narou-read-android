package com.novelreader.scrape

import com.novelreader.scrape.adapter.KakuyomuAdapter
import com.novelreader.scrape.generic.GenericSiteAdapter
import com.novelreader.scrape.generic.SiteProfiles

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
    private val adapters: List<NovelSiteAdapter> = sharedDefaultAdapters,
) {
    sealed interface Resolution {
        /** 自前 DL 可能なサイト。[adapter] と正規化済み作品 URL を持つ。 */
        data class Supported(val adapter: NovelSiteAdapter, val workUrl: String) : Resolution

        /** 規約で自前 DL 不可のサイト。公式サイト/API へ逃がす。[hostLabel] は表示用。 */
        data class Blocked(val hostLabel: String) : Resolution

        /** サイト自体が未知（アダプタ未整備）。公式サイト直行を案内。 */
        data object Unsupported : Resolution
    }

    /** 登録済みアダプタ一覧（破損監視・層3 の [AdapterHealthCheck] が全アダプタの自己診断を回すために読む）。 */
    val registeredAdapters: List<NovelSiteAdapter> get() = adapters

    fun resolve(inputUrl: String): Resolution {
        val host = hostOf(inputUrl) ?: return Resolution.Unsupported

        blockedHosts.firstOrNull { host == it.host || host.endsWith(".${it.host}") }?.let {
            return Resolution.Blocked(it.label)
        }

        // 規約裁定待ち（pending）ゲート: 現状は自前 DL の可否が未確定なため、保守側に倒して Blocked 相当
        // （公式サイトで読む導線）で返す。なぜここか＝将来 catch-all（設定に無いホストの本文自動検出＝G2）を
        // 足したとき、裁定前のこれらサイトが誤って取り込み対象へ滑り落ちる構造穴を、blockedHosts の直後で
        // 先回り封鎖するため。裁定が下りたら該当行を外すだけで解放できる（アダプタ追加は別途 G3）。
        pendingHosts.firstOrNull { host == it.host || host.endsWith(".${it.host}") }?.let {
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
            // ---- 以下4件は 2026-07-23 ユーザー裁定＝NG。ただし上のなろう群と性質が違う点に注意:
            // いずれも規約の**包括条項（複製・翻案等の一括禁止）による「グレー領域の保守裁定」**であり、
            // 自動取得を名指しで禁じる明文があったわけではない（私的複製との関係は法解釈依存）。
            // 後日の再裁定で解放される可能性を織り込んで記録を残す（裁定材料＝ADR 0024 追記 2026-07-23・
            // 技術検討資産〔アルファポリスのバックオフ要件・Pixiv の Cookie 認証設計論点〕は handover に温存）。
            BlockedHost("alphapolis.co.jp", "アルファポリス"), // 規約10条3項の包括禁止（グレー・保守裁定）
            BlockedHost("pixiv.net", "pixiv"), // ログイン必須設計も要る＝規約と技術の複合で見送り（グレー・保守裁定）
            BlockedHost("no-ichigo.jp", "野いちご"), // 規約第5条の包括複製禁止（グレー・保守裁定）
            BlockedHost("berrys-cafe.jp", "ベリーズカフェ"), // 同上（野いちごと同一運営・同文条項）
        )

        /**
         * 規約裁定待ち（pending）サイト。自前 DL の可否が未確定＝保守側に倒して blockedHosts と同じ
         * 「公式サイトで読む」導線（[Resolution.Blocked]）へ逃がす。裁定が下りたら該当行を外すだけで
         * Unsupported→アダプタ追加（G3）の通常経路に戻せる（機序＝設計正本 2026-07-23 の pendingHosts ゲート）。
         * 2026-07-23 まとめ裁定で残るはハーメルンのみ（他4件は NG 裁定で blockedHosts へ移動）。
         */
        private val pendingHosts = listOf(
            BlockedHost("syosetu.org", "ハーメルン"),
        )

        /**
         * 既定アダプタ束＝**プロセス全体で1つ**。全アダプタが1つの [ScrapeHttpClient] を共有し、
         * さらにその束を全 registry が共有することで、グローバル床（全ホスト横断の最低間隔 1req/s）が
         * 実際にアプリ全体へ効く。
         *
         * なぜ「registry ごとに new」ではいけなかったか（監査 2026-08-06 C4 の真因）:
         * [ScrapeHttpClient] の gate(Mutex)/lastRequestByHost はインスタンスフィールドなので、床は
         * client インスタンス単位でしか掛からない。以前はここが `defaultAdapters()` 関数で、既定
         * registry を作るたびに client ごと新規生成していた。実フェッチする registry は
         * `DefaultBookRepository`（取込）と `NewEpisodeCheckWorker`（新着照会）の2つ、debug の
         * `AdapterHealthBoardDialog` を数えれば3つあり、互いのロックを見ないまま同一ホストへ
         * 重なり得た（実効 2req/s）。上の「全アダプタで1つ」という宣言が registry 境界で破れていた。
         *
         * なぜ [ScrapeHttpClient] 側を static 化せず「束の共有」で直すか:
         * あちらは nowMs/sleep/fetch を差し替えられる注入クラス（テストの継ぎ目）で、床の状態を
         * クラス変数へ上げるとテスト間で状態が漏れ、明示生成したインスタンスまで黙って同じ床を
         * 共有してしまう。守るべき不変条件は「**既定の結線**では client は1つ」であって
         * 「client クラスは常に1つ」ではない。アダプタは [http] 以外に可変状態を持たない純粋な
         * 変換器なので、束ごと使い回して安全（生成コストも消える）。
         *
         * lazy は既定で同期化＝初回参照が複数スレッドから来ても実体は1つ。
         */
        private val sharedDefaultAdapters: List<NovelSiteAdapter> by lazy {
            val http = ScrapeHttpClient()
            // JSON（__NEXT_DATA__）系のカクヨムは専用アダプタで温存し、旧来型サーバサイド HTML 勢は
            // SiteProfiles の設定表 1 行ごとに GenericSiteAdapter を量産する（1 プロファイル=1 アダプタ）。
            listOf(KakuyomuAdapter(http)) + SiteProfiles.ALL.map { GenericSiteAdapter(it, http) }
        }

        private fun hostOf(url: String): String? = runCatching {
            java.net.URI(url.trim()).host?.lowercase()
        }.getOrNull()
    }

    private data class BlockedHost(val host: String, val label: String)
}
