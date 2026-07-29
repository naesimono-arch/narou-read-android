package com.novelreader.ui.discovery

import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 発見ホームの不変条件テスト（L1）の**取りこぼし検知メタテスト**（L2・2026-07-29）。
 *
 * なぜこの層が要るか: 個別の回帰テストも knowledge も揃っていたのに、K のランキングを Pager 化した
 * **新しいコード**が同じ罠（一覧を status 1行へ潰して高さを崩壊させる）を踏んで再発した。既存実装の退行は
 * 既存テストが防ぐが、新しく書かれた実装は誰も見ていない——その隙間を「ソースを走査して罠を踏み得る形を
 * 機械列挙し、[DiscoveryHomeRegistry] と突合する」ことで塞ぐ。
 *
 * 罠を踏み得る形（この層のバグ型固有の述語）＝**引数で `DiscoveryUiState` / `DiscoveryViewModel` を受け取る
 * 描画層（ui/）の関数**。一覧の行と status を出し分ける「選択者」は必ず状態を受け取るので、この述語は
 * 選択者を取りこぼさない。取りこぼし（偽陰性）を出さないことが最優先で、掛かりすぎ（偽陽性）は
 * 登録簿へ理由付きで足せば解消できる——という非対称さに合わせて広めに取ってある。
 *
 * この層が実際に効くことの確認（2026-07-29・再発コミット 23ace39 で実測）:
 *  - Pager 化前の K は `DiscoveryHomeK` / `RankingStaleRows` の2件。
 *  - Pager 化後は `DiscoveryHomeK` / `RankingPagerK` / `RankingPageK` / `RankingRowsK` の4件。
 *  → 新設3件が「未登録の新実装」、消えた `RankingStaleRows` が「実体を失った登録」として両方向から落ちる。
 *    つまり 23ace39 を書いた時点でこのテストは赤くなり、登録＝不変条件の確認を強制していた。
 *
 * 横展開について: 走査そのものは [KotlinSourceScanner]（バグ型非依存）に切り出してある。別のバグ型を
 * 足すときは「述語」と「登録簿」だけを新しく書けばよい。
 */
class DiscoveryHomeInvariantCoverageTest {

    /** 一覧の状態分岐を握り得る型。引数にこれらが現れる関数を「罠を踏み得る形」とみなす。 */
    private val riskTypes = listOf("DiscoveryUiState", "DiscoveryViewModel")

    /** 走査範囲＝描画層。状態を作る側（viewmodel/）は対象外（高さが潰れるのは描く側だけ）。 */
    private val scannedSubPath = "ui"

    @Test
    fun `罠を踏み得る描画層はすべて不変条件テストに登録されている`() {
        val root = KotlinSourceScanner.findModuleSourceRoot()
        if (root == null) {
            // 解決できないまま PASS すると検知器が死んでいることに気づけない（生成物依存の判定が
            // 恒久 dead 化してもテストは緑、という 2026-07-12 の実例）。ここは必ず明示的に落とす。
            fail(
                "ソースツリーの根（src/main/java/com/novelreader）を解決できなかった。" +
                    "作業ディレクトリ=${System.getProperty("user.dir")}。" +
                    "テストの作業ディレクトリ設定か KotlinSourceScanner.findModuleSourceRoot の探索条件を直すこと。",
            )
            return
        }

        val declarations = KotlinSourceScanner.declarations(root, scannedSubPath)
        assertTrue(
            "走査したのに関数宣言が1件も取れていない（走査根=$root）。" +
                "抽出条件が壊れると検知器は黙って全通過するため、ここで止める。",
            declarations.isNotEmpty(),
        )

        val atRisk = declarations
            .filter { decl -> riskTypes.any { it in decl.signature } }
            .map { it.id }
            .toSortedSet()
        assertTrue(
            "危険な形が1件も見つからない＝述語かパスが壊れている（走査根=$root / 走査対象=$scannedSubPath）。",
            atRisk.isNotEmpty(),
        )

        val known = DiscoveryHomeRegistry.registeredIds + DiscoveryHomeRegistry.acknowledgedOutOfScope.keys

        val unregistered = atRisk - known
        if (unregistered.isNotEmpty()) {
            fail(
                "新しい実装 ${unregistered.joinToString(" / ")} が見つかったが不変条件テストに登録されていない。" +
                    "登録して不変条件を満たすことを確認せよ: " +
                    "(1) 発見ホームの一覧を描くなら DiscoveryHomeRegistry.implementations の該当スキンの " +
                    "composables へ関数名を足し、DiscoveryHomeInvariantTest（L1）が緑になることを確かめる" +
                    "——特に『再取得(Loading)中に一覧を status 1行へ潰さない＝高さを崩壊させない』を満たすこと。" +
                    "(2) 一覧を描かない/別の裁定に従うなら DiscoveryHomeRegistry.acknowledgedOutOfScope へ" +
                    "**理由付きで**登録する（理由なしの除外は禁止）。",
            )
        }

        val stale = known - atRisk
        if (stale.isNotEmpty()) {
            fail(
                "登録簿の ${stale.joinToString(" / ")} が実体を失っている（リネーム・削除・引数からの状態除去）。" +
                    "DiscoveryHomeRegistry を実体へ追随させること。放置すると『登録済みだから守られている』が嘘になる" +
                    "——構造を差し替える再発コミットを捕まえるのは、この向きの検知の方。",
            )
        }
    }

    @Test
    fun `全スキンの発見ホームが不変条件テストに登録されている`() {
        // ルーター（DiscoveryHomeContent）は exhaustive な when でスキンを分岐する＝新スキンは必ず発見ホームを
        // 持つ。登録簿がスキンを取りこぼすと、そのスキンだけ誰も不変条件を確かめないまま出荷される。
        val registered = DiscoveryHomeRegistry.implementations.map { it.skin }.toSet()
        val missing = Skin.entries.filterNot { it in registered }
        assertTrue(
            "新しいスキン ${missing.joinToString()} が見つかったが不変条件テストに登録されていない。" +
                "DiscoveryHomeRegistry.implementations へ足し、DiscoveryHomeInvariantTest（L1）が緑になることを確かめよ。",
            missing.isEmpty(),
        )
    }
}
