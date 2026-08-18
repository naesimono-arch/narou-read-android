package com.novelreader.ui.skins.k

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 明快K「さがす」（[DiscoveryHomeK]）の**固定トップ（題字「さがす」＋実検索フィールド）の実高**を、
 * 横向き修飾子で機械実測する。
 *
 * ## なぜ書くか
 * 横向きの意匠裁定（台帳 §3-1・構造は ADR 0034）は「固定トップ **108dp**」を前提に実効ビューポートを
 * 見積もっているが、その 108dp は**正本モック `discovery-K.html` の CSS px からの導出値**
 * （2 + 26.4 + 14 + 52 + 14）で、実機実測でも実装実測でもない。一方 Compose 実装（[DiscoveryHomeK] の
 * `SearchHeaderK`）が積むのはトークンと M3 タイポで、上 S4 ＋ titleLarge の行高 ＋ S16 ＋ 検索欄 52dp ＋ S16。
 * **CSS px 側の 2/26.4/14 と、トークン側の 4/lineHeight/16 が一致する保証はない**ので、裁定の前提値と
 * 実装値がずれていないかは測らないと分からない。そしてこれは〈数値の比較〉＝実機を待つ理由が無い
 * （仕分けの正本＝`docs/knowledge/awaiting-human-machine-decidable-triage.md`）。
 *
 * **このテストが決めないこと**＝「その高さが意匠として許容できるか」。それは人間の裁定に残る
 * （T1/T2/T3 のどれを採るか、Rail 幅、FAB の置き場）。ここは「いくつなのか」だけを機械で確定させる。
 *
 * ## 測り方
 * 固定トップ＝**ルート上端から縦リスト（LazyColumn）上端まで**。間に居るのは `SearchHeaderK` と、
 * 高さ0の状態供給専用ページャ（`RankingPagerAnchorK`）だけなので、この差分がそのまま固定トップの実高になる。
 * アンカーが将来うっかり高さを持ったらこの実測値に乗って現れる＝それも気づきたい退行なので、
 * `SearchHeaderK` だけを狙い撃ちにするのではなく「リストが始まる位置」を測る形にしている。
 *
 * 縦リストの特定は既存流儀（DiscoveryHomeKRankingTest / [DiscoveryHomeKScreenshotTest]）と同じく
 * 「縦スクロール軸を持つ唯一のノード」で絞る。`hasScrollToNodeAction` だけでは気分・期間の横ページャも
 * 一致してしまう。
 *
 * ## 写せないもの（[BookshelfKLandscapeScreenshotTest] と同じ限界）
 * Robolectric の window inset は全て 0 で、`@Config(qualifiers)` にインセットを与える修飾子は無い。
 * よってステータスバー／ジェスチャーバーはこの実測に含まれない（推測値を流し込むと「推測を基準線へ
 * 焼き込む」ことになるため、あちらと同じく意図的に含めない）。**固定トップそのものはバーの影響を
 * 受けない**（`statusBarsPadding()` は固定トップの外側で効く）ので、この実測値は実機でもそのまま通用する。
 * 併せて出力する実効ビューポート（リスト実高）だけはバーぶん実機より広い＝**参考値**。
 */
@RunWith(RobolectricTestRunner::class)
// 横向き（w>h と land を両方明示する理由は BookshelfKLandscapeScreenshotTest と同じ）。
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class DiscoveryHomeKLandscapeFixedTopTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rankingContent = DiscoveryUiState.Content(
        allcount = 3,
        novels = (1..3).map { workSummary(title = "作品$it", ncode = "N%04dAA".format(it)) },
    )

    @Test
    fun `横向きの固定トップ（題字＋検索フィールド）の実高を測り 予算が崩れるほど太っていないことを確かめる`() {
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            // MainActivity と同じ入れ子（本文が weight(1f)・その下に恒常ナビ）で組む＝併せて出力する
            // 実効ビューポートを、ナビが縦を削った後の値にするため（BookshelfKLandscapeScreenshotTest と同型）。
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    DiscoveryHomeK(
                        order = NarouOrder.WEEKLY,
                        state = rankingContent,
                        onBack = {},
                        onOpenDetail = {},
                        onOpenGenre = {},
                        onPickBiggenre = { _, _ -> },
                        onOpenSearch = {},
                        onPickMood = {},
                        onSelectOrder = {},
                        onRefresh = {},
                        // 気分の組を固定＝端末日付で高さが揺れないことを保証する（既定値は日付導出）。
                        initialMoodPattern = MoodPattern.CLASSIC,
                    )
                }
                KBottomNav(current = KTab.DISCOVER, onSelect = {})
            }
        }

        val rootTop = composeTestRule.onRoot().getUnclippedBoundsInRoot().top
        val list = composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).getUnclippedBoundsInRoot()
        val fixedTop = list.top - rootTop
        val viewport = list.bottom - list.top

        // 測った値は**コードに焼かず出力する**（焼くと「いま何dpか」を知るという目的そのものが消え、
        // 値を更新するだけの儀式テストになる）。裁定の一次情報はこの行。
        println(
            "[固定トップ実測] DiscoveryHomeK fixedTop=$fixedTop / 実効ビューポート(リスト実高)=$viewport " +
                "/ qualifiers=w800dp-h360dp-land-xhdpi fontScale=1.0 insets=0(Robolectric)",
        )

        // 測った塊が本当に「題字＋検索フィールド」であることの裏取り: 検索フィールドが固定トップの内側に
        // 収まっている（＝リスト上端より上に居る）。ここが崩れたら測っている塊が別物になっている。
        // 題字「さがす」でなくプレースホルダ文で引く理由＝「さがす」は KBottomNav のラベルにも在り一意でない。
        val searchBottom = composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).getUnclippedBoundsInRoot().bottom
        assertTrue(
            "検索フィールドが固定トップの外へ出ている（測定対象がずれた）: searchBottom=$searchBottom listTop=${list.top}",
            searchBottom <= list.top,
        )

        // 上限 132dp は**現状値ではなく警報線**。根拠: 台帳 §3-1 の横向き予算は「固定トップ 108dp」を前提に
        // 実効ビューポートを見積もっている。そこから S24 ひとつぶん（24dp）太るまでを許容幅とし 108+24=132dp を
        // 線に置く。ここを超えたら見積もりの前提ごと崩れている＝裁定をやり直す合図。
        // 現状値そのものを焼かないのは上の println のコメントと同じ理由（実測を殺さないため）。
        assertTrue(
            "固定トップが横向き予算の前提（モック導出 108dp ＋ S24 の許容幅）を超えた: fixedTop=$fixedTop",
            fixedTop <= 132.dp,
        )
    }

    private companion object {
        /** 検索フィールドのプレースホルダ（`SearchHeaderK` の実文言）。 */
        const val SEARCH_PLACEHOLDER = "作品名・作者名・キーワードで探す"
    }
}
