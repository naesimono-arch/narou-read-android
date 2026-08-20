package com.novelreader.ui.discovery

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.novelreader.discovery.model.workDetail
import com.novelreader.discovery.model.workSummary
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.NovelDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 作品詳細の書影ブロック（案2-c「淡地」）の版面回帰テスト。
 *
 * 何を守るのか: 正本 `docs/design-candidates/discovery/discovery-detail-D.html` は「情報列は帯の中に
 * 高さ96dp で**上下対称**（12/96/12＝帯120）」と**数で**規定している。数で規定されたものは数で守らないと、
 * 行送りの既定値やアキの丸め（S8 へ寄せる等）で静かに崩れて誰も気づかない——実際に案2-c 以前は
 * 情報列が top14・高さ約109.6 の非対称になり、境界を10pxまたいでいた（「帯の中の要素なのか外の要素なのか
 * 読めない」という裁定の原因そのもの）。
 *
 * ⚠️ [GraphicsMode.Mode.NATIVE] は必須。既定の LEGACY は実フォントを使わず「文字幅＝文字数」の代用計量に
 * なるため、行送り・行数に依存する本ファイルの検証は**全緑のまま検出力ゼロ**になる
 * （正本＝`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 *
 * 端末幅は正本と同じ 360dp。水平の規定（左24／カード右138と列左154の間16／右余白24）は幅に依存するため、
 * qualifiers を外すと意味が変わる。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class NovelDetailCoverBlockLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ComposeContentTestRule.showDetail(
        title: String = TITLE_TWO_LINES,
        author: String = "霧島あおい",
    ) {
        setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                NovelDetailContent(
                    uiState = NovelDetailUiState.Content(
                        novel = workDetail(
                            summary = workSummary(
                                title = title,
                                author = author,
                                // 201 = ハイファンタジー。チップが出ないと情報列の下端が測れない。
                                genreCode = 201,
                            ),
                            story = "調薬の才能を見込まれ、宮廷薬師として働いていた万能薬師のアルス。",
                        ),
                        // 末尾メタの時刻。本ファイルは書影ブロックの寸法しか見ないので固定値でよい。
                        fetchedAtMillis = 0L,
                    ),
                    onSearchKeywords = {},
                    onImportPdf = {},
                    onUp = {},
                    onRetry = {},
                    onReadOnNarou = {},
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** dp は密度換算の丸めで端数が出うるので 0.5dp（xhdpi の 1px）まで許す。規定値のズレ（4dp 以上）は素通ししない。 */
    private fun assertDp(message: String, expected: Dp, actual: Dp) {
        assertEquals(message, expected.value.toDouble(), actual.value.toDouble(), 0.5)
    }

    @Test
    fun `情報列は帯の中へ高さ96dpで上下対称に据わる`() {
        composeTestRule.showDetail()
        val band = bounds("detail_cover_band")
        val info = bounds("detail_info_column")

        assertDp("帯の高さ", 120.dp, band.bottom - band.top)
        assertDp("情報列の高さ", 96.dp, info.bottom - info.top)
        // 上下対称＝「境界に近づいた結果の位置」ではなく「帯の内側余白の規定どおりの位置」であることの証拠。
        assertDp("帯の上端から情報列まで", 12.dp, info.top - band.top)
        assertDp("情報列から帯の下端まで", 12.dp, band.bottom - info.bottom)
        assertEquals(
            "上余白と下余白が同値（＝上下対称）",
            (info.top - band.top).value.toDouble(),
            (band.bottom - info.bottom).value.toDouble(),
            0.5,
        )
    }

    /**
     * 情報列の**中身**が 96dp をちょうど埋めることを、箱の高さとは別に測る。
     *
     * なぜ別立てが要るか: 列そのものは `Modifier.height(96.dp)` で固定してあるので、上のテストは
     * 行送りやアキが狂っても緑のまま通る（箱だけ96dpで中身が溢れる）。`getUnclippedBoundsInRoot` は
     * 親の高さでクリップされない実位置を返すので、チップの下端で中身の総高を測れる。
     * これが 44＋6＋16＋6＋24＝96 という内訳そのものの守り。
     */
    @Test
    fun `情報列の中身は44_6_16_6_24で96dpをちょうど埋める`() {
        composeTestRule.showDetail()
        val info = bounds("detail_info_column")
        val title = bounds("detail_info_title")
        val chip = bounds("detail_info_chip")

        val author = bounds("detail_info_author")
        assertDp("作者の行（行送り16dp・1行nowrap）", 16.dp, author.bottom - author.top)
        assertDp("チップの高さ（行14＋padding4×2＋枠1×2）", 24.dp, chip.bottom - chip.top)
        assertDp("題名の箱＝96−(6+16+6+24)", 44.dp, title.bottom - title.top)
        assertDp("アキ（題名→作者）", 6.dp, author.top - title.bottom)
        assertDp("アキ（作者→チップ）", 6.dp, chip.top - author.bottom)
        // 中身がちょうど 96dp を埋める＝溢れも余りも無い。getUnclippedBoundsInRoot は親の高さで
        // 切られない実位置を返すので、内訳が狂えばここが 96 からずれて赤くなる。
        assertDp("中身の総高（列の上端からチップ下端まで）", 96.dp, chip.bottom - info.top)
    }

    /**
     * 「内容に依らず 96dp」の担保。1行に収まる短い題名でも題名の箱が 44dp のままであること
     * （`minLines = 2` を落とすとここだけが赤くなる＝短題名の作品でだけ上下対称が崩れる退行を捕まえる）。
     */
    @Test
    fun `題名が1行に収まっても情報列の高さは変わらない`() {
        composeTestRule.showDetail(title = TITLE_ONE_LINE)
        val band = bounds("detail_cover_band")
        val info = bounds("detail_info_column")
        val title = bounds("detail_info_title")
        val chip = bounds("detail_info_chip")

        assertDp("短題名でも題名の箱は2行ぶん", 44.dp, title.bottom - title.top)
        assertDp("短題名でも中身の総高は96dp", 96.dp, chip.bottom - info.top)
        assertDp("短題名でも下余白は12dp", 12.dp, band.bottom - info.bottom)
        assertDp("短題名でも情報列の高さ", 96.dp, info.bottom - info.top)
    }

    /**
     * 水平の規定と、書影カードが帯の境界を **44dp 越える**こと。
     * 越え幅は意匠の要（10dp の半端なはみ出しとは桁が違うので「面の上に置かれた本」と読める）なので、
     * 「はみ出しを直す」つもりで縮められないよう数で固定する。
     */
    @Test
    fun `書影カードは左24dpに置かれ帯の境界を44dp越える`() {
        composeTestRule.showDetail()
        val block = bounds("detail_cover_block")
        val band = bounds("detail_cover_band")
        val card = bounds("detail_cover_card")
        val info = bounds("detail_info_column")

        assertDp("ブロック高", 166.dp, block.bottom - block.top)
        assertDp("カード幅", 114.dp, card.right - card.left)
        assertDp("カード高", 152.dp, card.bottom - card.top)
        assertDp("カード左＝ページ余白 S24", 24.dp, card.left - block.left)
        assertDp("カード上＝帯の内側余白 S12", 12.dp, card.top - band.top)
        assertDp("カードが帯の境界を越える量", 44.dp, card.bottom - band.bottom)
        assertDp("カード右と情報列左の間 S16", 16.dp, info.left - card.right)
        assertDp("情報列の右余白 S24", 24.dp, block.right - info.right)
    }

    private companion object {
        /** 幅182dpの情報列で 16sp なら 2行になる長さ（正本モックが載せている題名そのもの）。 */
        const val TITLE_TWO_LINES = "追放された万能薬師、辺境でスローライフを始める"

        /** 1行に確実に収まる短題名。 */
        const val TITLE_ONE_LINE = "春嵐"
    }
}
