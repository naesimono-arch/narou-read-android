package com.novelreader.ui.skins.k

import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 横向き **T1 横一列化**（ADR 0034 の意匠裁定）の横1行——〈検索バー｜期間タブ6本〉——が
 * Rail 80dp を引いた残り幅に**実際に収まるか**を機械実測する。**翻訳前の裁定用**（T1 はまだ未実装）。
 *
 * ## なぜ測るか
 * T1 の寸法根拠はモックの CSS px（検索 320dp ＋ 期間タブ 336dp ＝ 656dp を 720dp へ）だけで、
 * 実装側の実寸ではない。しかも同じ横向き裁定で使われていた固定トップ 105.5dp が
 * **LEGACY GraphicsMode の代用計量による偽装値**（真値 120.5dp）だった前科がある
 *（`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 * ⇒ **横1行が入るか**も同じ質の未検証値なので、翻訳に入る前に実フォントで測り直す。
 *
 * ## 測るもの（横方向だけ。縦は測らない）
 * 期間タブは実装 [DiscoveryHomeK] の実物（`OrderTabsK`）を横向きで描いて1本ずつの実寸を取り、
 * 検索バーは「プレースホルダが省略されない最小幅」＝内側の実測から積み上げる（実装の検索欄は
 * `fillMaxWidth` なので**実幅を測っても要求幅は分からない**＝中身から積む必要がある）。
 * Rail 80dp・左右余白 S24・検索⇄タブ間 S16 は裁定値／モック導出値なので**定数として置き**、
 * 計算に使った値が読めるよう出力にも並べる。
 *
 * ## 出力した値がどちらの GraphicsMode で出たかを、テストの中から自明にする
 * 上記の偽装事故は「`println` した値が人間の裁定へ渡る」型で起きた。アノテーションは**付け忘れても
 * 赤くならない**ので、ここでは [graphicsModeStamp] が**実行時に計量系そのものを試して**モードを判定し、
 * 全ての出力行へ焼き込む（同幅の文字列 "iiii"/"WWWW" が同じ幅に測れる＝文字数代用＝LEGACY）。
 * 判定が LEGACY なら値を出す前に落とす＝**偽装値が一次情報として流通する経路を塞ぐ**。
 */
@RunWith(RobolectricTestRunner::class)
// 横向き（w>h と land を両方明示する理由は BookshelfKLandscapeScreenshotTest と同じ）。
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
// ⚠️ 寸法を人間の裁定へ渡すテストは NATIVE 明示が規約（上記 knowledge・`/build` のゲート節）。
// 忘れた場合に備えた実行時の裏取りが [graphicsModeStamp]。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoveryHomeKLandscapeT1RowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rankingContent = DiscoveryUiState.Content(
        allcount = 3,
        novels = (1..3).map { workSummary(title = "作品$it", ncode = "N%04dAA".format(it)) },
    )

    @Test
    fun `T1の横1行が Rail を引いた残り幅に収まるかを実測する（fontScale 1_0）`() = measureT1Row(1.0f)

    @Test
    fun `T1の横1行が Rail を引いた残り幅に収まるかを実測する（fontScale 2_0）`() = measureT1Row(2.0f)

    private fun measureT1Row(fontScale: Float) {
        val stamp = requireNativeGraphics()

        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale) { _ ->
            // 恒常ボトムナビは置かない＝Rail 化後の姿。ここで測るのは横方向だけで、ナビは縦しか食わない。
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
                // 気分の組を固定＝端末日付でレイアウトが揺れないようにする（既定値は日付導出）。
                initialMoodPattern = MoodPattern.CLASSIC,
            )
        }

        // 期間タブ行は縦リストの下方に居て初期状態では合成されていない＝順位行まで送って貼り付かせる。
        // 縦スクロール軸を持つ唯一のノードで絞る流儀は DiscoveryHomeKRankingTest.scrollListTo と同じ
        //（hasScrollToNodeAction だけだと気分・期間の横ページャも一致する）。
        composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).performScrollToNode(hasText("作品1"))

        // 期間タブ1本＝`selected` セマンティクスを持つ Text（未マージ木で引く＝タブ自身の実幅が要るため）。
        // 順位行のメタにも期間名が出るが、そちらは `selected` を持たないのでこの絞りで一意になる。
        val tabs = NarouOrder.entries.map { o ->
            o to composeTestRule.onNode(
                hasText(o.uiLabel) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
                useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
        }
        // 行の実占有＝先頭タブ左端〜末尾タブ右端（溝 S16×5 込み）。個別幅の和ではなく実座標で取る
        // ＝溝の実装値がずれてもこの値には正しく乗る。
        val tabsWidth = tabs.last().second.right - tabs.first().second.left

        // 検索欄の**要求幅**は中身から積む（実装は fillMaxWidth なので実幅＝画面幅で情報にならない）。
        // マージ木で引くと clickable の Row（＝欄そのもの）、未マージ木で引くと中のプレースホルダ Text。
        val searchField = composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).getUnclippedBoundsInRoot()
        val searchText = composeTestRule
            .onNodeWithText(SEARCH_PLACEHOLDER, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // 前提アサート: プレースホルダが省略されていない（＝測った幅が「要求幅」であって「与えられた幅」でない）。
        // 省略されている場合、Text の右端は欄の内側右端ちょうどまで伸びる。
        assertTrue(
            "プレースホルダが省略されている＝測った幅は要求幅ではない: text=${searchText.right} field=${searchField.right}",
            searchText.right < searchField.right - SearchFieldHPadding,
        )
        val searchMinWidth =
            SearchFieldHPadding * 2 + SearchIconWidth + SearchIconGap + searchText.width

        val available = ScreenWidth - RailWidth
        val required = SideMargin * 2 + searchMinWidth + RowGap + tabsWidth
        val slack = available - required

        // 測った値は**コードに焼かず出力する**（焼くと「いま何dpか」を知る目的そのものが消える）。
        // 裁定の一次情報はこの3行。stamp は実行時に判定した計量モード＝この数値の出所。
        println(
            "[T1横1行実測 $stamp] fontScale=$fontScale " +
                "画面幅=$ScreenWidth Rail=$RailWidth ⇒ 残り幅=$available / " +
                "左右余白=${SideMargin * 2} 検索⇄タブ間=$RowGap",
        )
        println(
            "[T1横1行実測 $stamp] fontScale=$fontScale 期間タブ6本=$tabsWidth " +
                "(${tabs.joinToString(" ") { "${it.first.uiLabel}=${it.second.width}" }}) / " +
                "検索バー最小幅=$searchMinWidth (プレースホルダ実測=${searchText.width}＋アイコン $SearchIconWidth" +
                "＋溝 $SearchIconGap＋左右 ${SearchFieldHPadding * 2})",
        )
        println(
            "[T1横1行実測 $stamp] fontScale=$fontScale 要求幅=$required vs 残り幅=$available ⇒ " +
                (if (slack.value >= 0f) "収まる（余り $slack）" else "**超過 ${-slack.value}dp**") +
                " ／ モック導出値は 検索320dp＋タブ336dp＝656dp",
        )
    }

    /**
     * 「超過を吸う案」がそれぞれ何dp稼ぐかを、同じ実フォント計量で出す。
     *
     * 実画面を描かずトークン（[FontSubTitle]／[FontBody]）で直接測る理由: 案の比較に要るのは
     * **文言と字送りだけ**で、案ごとに実画面を組むと比較できない差（余白・親の制約）が混ざるため。
     * 1.0/2.0 は上の実測と同条件なので、両者の突き合わせでこの probe 自体の妥当性も見える。
     *
     * fontScale ごとに [Density] を差し替えた枝を縦に並べる＝1回のコンポーズで全条件を測る
     *（[createComposeRule] の `setContent` は1回しか呼べない）。縦スクロールで包むのは、
     * 画面高 360dp を超えた枝が高さ0に潰されないようにするため（子を高さ無制限で measure させる）。
     */
    @Test
    fun `超過を吸う候補の稼ぎ高を字送りから実測する`() {
        val stamp = requireNativeGraphics()
        composeTestRule.setContent {
            val base = LocalDensity.current
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FONT_SCALES.forEach { scale ->
                        CompositionLocalProvider(
                            LocalDensity provides Density(density = base.density, fontScale = scale),
                        ) {
                            Column {
                                NarouOrder.entries.forEach { o ->
                                    // 選択タブだけ Bold＝実装の [OrderTabsK] と同じ（Bold は数dp太る）。
                                    listOf(false, true).forEach { bold ->
                                        Text(
                                            o.uiLabel,
                                            fontSize = FontSubTitle,
                                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            modifier = Modifier.testTag(tabTag(scale, o, bold)),
                                        )
                                    }
                                }
                                PLACEHOLDER_CANDIDATES.forEach { candidate ->
                                    Text(
                                        candidate,
                                        fontSize = FontBody,
                                        maxLines = 1,
                                        modifier = Modifier.testTag(textTag(scale, candidate)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FONT_SCALES.forEach { scale ->
            val widths = NarouOrder.entries.associateWith { o ->
                measureTagged(tabTag(scale, o, bold = false))
            }
            // 実装は「選択1本だけ Bold」なので、行の実占有もその形で積む（選択＝週間で代表）。
            val boldDelta = measureTagged(tabTag(scale, NarouOrder.WEEKLY, bold = true)) -
                widths.getValue(NarouOrder.WEEKLY)
            val gaps = OrderTabGapProbe * (NarouOrder.entries.size - 1)
            val tabs6 = widths.values.fold(0.dp) { a, b -> a + b } + gaps + boldDelta
            // 本数を減らす案の稼ぎ＝末尾から落としたときの差分（溝1本ぶんも一緒に消える）。
            val drop1 = widths.getValue(NarouOrder.NEW) + OrderTabGapProbe
            val drop2 = drop1 + widths.getValue(NarouOrder.TOTAL) + OrderTabGapProbe
            println(
                "[T1候補実測 $stamp] fontScale=$scale 期間タブ6本=$tabs6 " +
                    "(${widths.entries.joinToString(" ") { "${it.key.uiLabel}=${it.value}" }} 選択Bold差=$boldDelta 溝計=$gaps) " +
                    "／ 1本減=$drop1 2本減=$drop2",
            )
            println(
                "[T1候補実測 $stamp] fontScale=$scale プレースホルダ候補: " +
                    PLACEHOLDER_CANDIDATES.joinToString(" / ") { "「$it」(${it.length}字)=${measureTagged(textTag(scale, it))}" },
            )
        }
    }

    private fun measureTagged(tag: String): Dp =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().width

    /**
     * 実行時に**計量系そのものを試して** GraphicsMode を判定し、LEGACY なら値を出す前に落とす。
     *
     * 判定法: LEGACY の `ShadowPaint` は幅を「文字数」で返す（`measureText`＝`String.length`）ので、
     * **字幅の全く違う同字数の2語が同じ幅になる**。NATIVE は nativeruntime 同梱の実フォントで測るので違う。
     * 全角1文字が約1em で測れることも併せて確かめる（CJK フォント DroidSansFallback が効いている裏取り）。
     */
    private fun requireNativeGraphics(): String {
        val paint = Paint().apply { textSize = 100f }
        val narrow = paint.measureText("iiii")
        val wide = paint.measureText("WWWW")
        val fullWidth = paint.measureText("日")
        val isNative = narrow != wide && fullWidth > 50f
        val stamp = if (isNative) "NATIVE(実フォント計量)" else "LEGACY(文字数代用＝偽装値)"
        assertTrue(
            "GraphicsMode が NATIVE でない＝出力しようとしている寸法は偽装値: " +
                "iiii=$narrow WWWW=$wide 日=$fullWidth（@GraphicsMode(NATIVE) が外れていないか）",
            isNative,
        )
        return stamp
    }

    private fun tabTag(scale: Float, order: NarouOrder, bold: Boolean) =
        "tab|$scale|${order.name}|${if (bold) "bold" else "normal"}"

    private fun textTag(scale: Float, text: String) = "text|$scale|$text"

    private companion object {
        /** 検索フィールドのプレースホルダ（`SearchHeaderK` の実文言）。 */
        const val SEARCH_PLACEHOLDER = "作品名・作者名・キーワードで探す"

        /** `@Config(qualifiers)` の画面幅。計算に使った値を出力にも並べるため定数で持つ。 */
        val ScreenWidth = 800.dp

        /** ADR 0034 の裁定＝Rail は R1（幅 80dp・ラベルあり）。 */
        val RailWidth = 80.dp

        /** 実装 `DiscoveryListHorizontalMargin`（private のため同値を再掲）＝行の左右余白。 */
        val SideMargin = Spacing.S24

        /** T1 の検索⇄期間タブ間。モック導出（800 − 80 − 24×2 − 320 − 336 ＝ 16dp）。 */
        val RowGap = Spacing.S16

        /** `SearchHeaderK` の内訳（虫めがね 20dp／アイコン⇄文字 S12／欄の左右 S16）。 */
        val SearchIconWidth = 20.dp
        val SearchIconGap = Spacing.S12
        val SearchFieldHPadding = Spacing.S16

        /** 実装 `OrderTabGap`（private のため同値を再掲）＝期間タブ間の溝。 */
        val OrderTabGapProbe = Spacing.S16

        /** 1.0/2.0 は golden の両端・間の3点は実機で選べる中間段（破綻の閾値を挟むため）。 */
        val FONT_SCALES = listOf(1.0f, 1.15f, 1.3f, 1.5f, 2.0f)

        /** 検索バーを縮める案のために測る文言候補（先頭＝現行の実文言）。 */
        val PLACEHOLDER_CANDIDATES = listOf(
            "作品名・作者名・キーワードで探す",
            "作品名・作者名で探す",
            "キーワードで探す",
            "作品を探す",
        )
    }
}
