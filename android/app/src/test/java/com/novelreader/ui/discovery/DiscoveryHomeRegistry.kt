package com.novelreader.ui.discovery

import com.novelreader.ui.theme.Skin

// ============================================================
// 発見ホームの「不変条件テスト」登録簿（L1/L2 の単一情報源・2026-07-29）。
//
// なぜ登録簿を1箇所に置くか: 個別の実装分岐を固定するテストは「既存実装の退行」しか防げず、
// 新しく書いたコードが同じ罠を踏むのは防げなかった（K のランキング Pager 化＝新規コードで
// スクロール位置リセットが再発した実例）。そこで
//   L1 = この登録簿を回して全実装に同じ不変条件をかける（DiscoveryHomeInvariantTest）
//   L2 = ソースを走査して「罠を踏み得る形」を機械列挙し、この登録簿と突合する
//        （DiscoveryHomeInvariantCoverageTest）
// の2層にする。実装が増えたらこのファイルへ1行足すだけで L1 の検査対象になり、足し忘れは L2 が落とす。
// ============================================================

/**
 * 発見ホームの1実装。
 *
 * @param displayName パラメタライズドテストの表示名・FAIL メッセージ用。
 * @param skin この実装へ分岐させるスキン（入口は共通の [DiscoveryHomeContent]）。
 * @param sourceFile L2 の突合キー（`src/main/java/com/novelreader/` からの相対パス）。
 * @param composables この実装で「一覧の状態分岐」を担う合成関数名（L2 の突合キー）。
 *   同じ実装を共有するスキンでは重複登録を避けるため空でよい（C は D と同一実装）。
 * @param emptyText 0件表示の文言＝Empty を骨格で覆い隠していないことの観測点。
 */
// public にしているのは、パラメタライズドテスト（DiscoveryHomeInvariantTest）の public コンストラクタが
// この型を受け取るため（internal のままだと「public 宣言が internal 型を露出」でコンパイルが通らない）。
data class DiscoveryHomeImpl(
    val displayName: String,
    val skin: Skin,
    val sourceFile: String,
    val composables: List<String>,
    val emptyText: String,
)

object DiscoveryHomeRegistry {

    /** 一覧の0件文言（D 系の共通文。P だけボード語彙で短い）。 */
    private const val EMPTY_DEFAULT = "作品が見つかりませんでした"

    /**
     * L1 の検査対象。**新しい発見ホーム実装を書いたらここへ足す**（足すまで L2 が赤で止める）。
     * D と C は同一実装（[DiscoveryHomeContent] の既定描画）を共有するが、将来の分岐に備えて
     * 両スキンとも不変条件を回す（composables は D 側にだけ持たせて二重登録を避ける）。
     */
    val implementations: List<DiscoveryHomeImpl> = listOf(
        DiscoveryHomeImpl(
            displayName = "D 和モダン・共通実装",
            skin = Skin.WAMODERN_D,
            sourceFile = "ui/discovery/DiscoveryHomeScreen.kt",
            composables = listOf("DiscoveryHomeScreen", "DiscoveryHomeContent"),
            emptyText = EMPTY_DEFAULT,
        ),
        DiscoveryHomeImpl(
            displayName = "C 夜行・D実装を共有",
            skin = Skin.YAKO_C,
            sourceFile = "ui/discovery/DiscoveryHomeScreen.kt",
            composables = emptyList(),
            emptyText = EMPTY_DEFAULT,
        ),
        DiscoveryHomeImpl(
            displayName = "M 星図",
            skin = Skin.SEIZU_M,
            sourceFile = "ui/skins/m/DiscoveryHomeSkyM.kt",
            composables = listOf("DiscoveryHomeSkyM"),
            emptyText = EMPTY_DEFAULT,
        ),
        DiscoveryHomeImpl(
            displayName = "J 扉の回廊",
            skin = Skin.PORTAL_J,
            sourceFile = "ui/skins/j/DiscoveryPortalJ.kt",
            composables = listOf("DiscoveryHomePortalJ"),
            emptyText = EMPTY_DEFAULT,
        ),
        DiscoveryHomeImpl(
            displayName = "P カートリッジ",
            skin = Skin.CARTRIDGE_P,
            sourceFile = "ui/skins/p/DiscoveryCartridgeP.kt",
            // HI-SCORE ボードは親 LazyColumn の1 item に全行を抱く非遅延 Column＝K のページャと同型の
            // 「item ごと高さが崩れる」形。ホーム本体と一緒に登録する。
            composables = listOf("DiscoveryHomeCartridgeP", "HiScoreBoard"),
            emptyText = "該当なし",
        ),
        DiscoveryHomeImpl(
            displayName = "K 明快",
            skin = Skin.MEIKAI_K,
            sourceFile = "ui/skins/k/DiscoveryHomeK.kt",
            composables = listOf("DiscoveryHomeK", "RankingPagerK", "RankingPageK", "RankingRowsK"),
            emptyText = EMPTY_DEFAULT,
        ),
    )

    /**
     * 走査には掛かるが発見ホームの不変条件の対象外＝**理由付きで**認めるもの。
     * 理由を必須にしているのは「黙って除外リストへ逃がす」のを防ぐため（除外の判断も記録に残す）。
     * key は L2 の突合キー（`<sourceFile>#<関数名>`）。
     */
    val acknowledgedOutOfScope: Map<String, String> = mapOf(
        "ui/discovery/DiscoveryResultScreen.kt#DiscoveryResultScreen" to
            "結果一覧は「新クエリなら先頭表示が正」の解釈があり、位置保持を不変条件にするか未裁定（knowledge の未展開の同型）",
        "ui/discovery/DiscoveryResultScreen.kt#DiscoveryResultContent" to
            "同上（結果一覧の D/C 共通描画）",
        "ui/skins/m/DiscoveryHomeSkyM.kt#DiscoveryResultSkyM" to "同上（結果一覧の M 実装）",
        "ui/skins/j/DiscoveryPortalJ.kt#DiscoveryResultPortalJ" to "同上（結果一覧の J 実装）",
        "ui/skins/p/DiscoveryCartridgeP.kt#DiscoveryResultCartridgeP" to "同上（結果一覧の P 実装）",
        "ui/discovery/DiscoverySearchScreen.kt#DiscoverySearchScreen" to
            "検索条件の入力画面＝一覧の Loading 分岐を持たない（VM を受けるため走査に掛かるだけ）",
        "ui/discovery/SearchConditionSheet.kt#SearchConditionSheet" to
            "検索条件シート＝一覧を描かない（VM を受けるため走査に掛かるだけ）",
    )

    /** L2 が「登録済み」とみなす識別子の全体（`<sourceFile>#<関数名>`）。 */
    val registeredIds: Set<String> =
        implementations.flatMap { impl -> impl.composables.map { "${impl.sourceFile}#$it" } }.toSet()
}
