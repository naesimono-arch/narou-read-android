package com.novelreader.ui.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
 * 何を守るのか: 正本 `docs/design-candidates/discovery/discovery-detail-D.html` は書影ブロックを
 * **数（式）で**規定している。数で規定されたものは数で守らないと、行送りの既定値やアキの丸め
 * （S8 へ寄せる等）で静かに崩れて誰も気づかない——実際に案2-c 以前は情報列が top14・高さ約109.6 の
 * 非対称になり、境界を10pxまたいでいた（「帯の中の要素なのか外の要素なのか読めない」という裁定の原因そのもの）。
 *
 * ---- 2026-08-26 裁定（案A ＋ T2）で式になった。F = fontScale ----
 * ・帯 = **74·F + 46**（F=1.0 で **120**＝2026-07-31 裁定値のまま。74＝sp で伸びる箱の総和 44+16+14、
 *   46＝伸びない部分＝アキ6×2 ＋ チップの padding/枠 10 ＋ 帯の上下内側余白 12×2）
 * ・情報列 = **76·F + 22**（F=1.0 で **98**）＝題名 46F ＋ アキ6 ＋ 作者 16F ＋ アキ6 ＋ チップ(14F+10)
 * ・帯の上下内側余白 = **12 − F**（F=1.0 で **11**）＝**上下対称は不変で、値だけが変わった**。
 *   ⚠️ 12 が 11 になったのは T2（題名の箱 44→46）の +2dp を**帯を太らせずに**吸収したため。
 *   帯の式は 74F+46 のまま据え置くのが裁定なので、ここを 76F+46 へ「直す」と別物になる。
 * ・ブロック = **max(帯, カード下端164) + 逃げ2**（F<1.60 では 164 が支配的＝**166 のまま動かない**）
 * ・書影カードは **dp 固定＝F に追従しない**（114×152・上端 S12・下端 164）。よって
 *   「帯の境界を越える量 = 164 − 帯」は F と共に痩せ、F≥1.60 で 0（帯がカードを飲む）＝案A の受諾済みの代償。
 *
 * ---- 題名の箱が 44 でなく 46 な理由（T2）----
 * 44dp は「22×2行ぶん」のつもりの値だったが、自然行高の実測は 22.75dp で2行に **45.5dp** 要る。
 * 1.5dp 足りず、`maxLines = 2` と書いてありながら**2行目が一度も描かれていなかった**。46 にすると
 * 46 ÷ 45.5 = 1.011 > 1 ＝ 箱と行の**比**が立ち、F を掛けても比は動かないので全 fontScale で2行が出る。
 * ⚠️ だから本ファイルは箱の高さ（46・92）だけでなく **実際に組まれた行数**（[titleLineCount]）も測る——
 * 高さだけ見ていたのが、2行と書いて1行しか出ていない状態を4か月見逃した原因そのものだから。
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

    /**
     * @param fontScale 非 null なら [LocalDensity] を差し替えて fontScale だけを変える（density は
     *   qualifiers の xhdpi=2 を保つ）。⚠️ `@Config(qualifiers)` には fontScale の直接指定が無いため
     *   この張り方になる（screenshot 側 `ScreenshotTestSupport` と同一の作法）。テーマの**内側**で
     *   provide するのは、テーマが density を張り直しても必ず上書きが勝つようにするため。
     */
    private fun ComposeContentTestRule.showDetail(
        title: String = TITLE_TWO_LINES,
        author: String = "霧島あおい",
        fontScale: Float? = null,
    ) {
        setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides
                        if (fontScale == null) base else Density(base.density, fontScale)
                ) {
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
    }

    private fun bounds(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /**
     * 題名として**実際に組まれた行数**を返す（箱の高さではなく描画結果を見る）。
     *
     * なぜ要るか: 箱の高さだけを測っていると「箱は2行ぶんあるのに Paragraph が2行目を落としている」状態が
     * 緑のまま通る＝T2 で直した既存バグそのものが再発しても検出できない。`maxLines = 2` は上限であって
     * 「2行組まれる」保証ではなく、行が入りきらなければ Compose は行を落として1行目に省略記号を出す。
     * ⚠️ この測り方は [GraphicsMode.Mode.NATIVE] とセットでしか意味を持たない（LEGACY は実フォントを
     * 使わないので折り返し位置が現実と無関係になる＝クラス KDoc の警告）。
     */
    private fun titleLineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithTag("detail_info_title", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.first().lineCount
    }

    /** dp は密度換算の丸めで端数が出うるので 0.5dp（xhdpi の 1px）まで許す。規定値のズレ（4dp 以上）は素通ししない。 */
    private fun assertDp(message: String, expected: Dp, actual: Dp) {
        assertEquals(message, expected.value.toDouble(), actual.value.toDouble(), 0.5)
    }

    @Test
    fun `情報列は帯の中へ高さ98dpで上下対称に据わる`() {
        composeTestRule.showDetail()
        val band = bounds("detail_cover_band")
        val info = bounds("detail_info_column")

        // 帯 = 74×1.0 + 46。⚠️ **120 は動いていない**——T2 で情報列が 2dp 太っても帯は太らせない、が裁定。
        assertDp("帯の高さ", 120.dp, band.bottom - band.top)
        // 情報列 = 76×1.0 + 22（旧 96 から +2＝題名の箱 44→46 のぶん）。
        assertDp("情報列の高さ", 98.dp, info.bottom - info.top)
        // 上下対称＝「境界に近づいた結果の位置」ではなく「帯の内側余白の規定どおりの位置」であることの証拠。
        // 余白 = 12 − F。⚠️ 11 は「12 を守れなかった結果」ではなく、太った 2dp を帯でなく余白から出す
        // という裁定の結果＝(120 − 98) / 2 で導かれる値（対称であることが規定で、12 は F=0 の値にすぎない）。
        assertDp("帯の上端から情報列まで", 11.dp, info.top - band.top)
        assertDp("情報列から帯の下端まで", 11.dp, band.bottom - info.bottom)
        assertEquals(
            "上余白と下余白が同値（＝上下対称）",
            (info.top - band.top).value.toDouble(),
            (band.bottom - info.bottom).value.toDouble(),
            0.5,
        )
    }

    /**
     * 情報列の**中身**が 98dp をちょうど埋めることを、箱の高さとは別に測る。
     *
     * なぜ別立てが要るか: 列そのものは `Modifier.height(metrics.infoColumnHeight)` で確定させてあるので、
     * 上のテストは行送りやアキが狂っても緑のまま通る（箱だけ98dpで中身が溢れる）。
     * `getUnclippedBoundsInRoot` は親の高さでクリップされない実位置を返すので、チップの下端で
     * 中身の総高を測れる。これが **46＋6＋16＋6＋24＝98** という内訳そのものの守り
     * （旧 44＋6＋16＋6＋24＝96 から、動いたのは**題名の箱だけ**＝T2）。
     */
    @Test
    fun `情報列の中身は46_6_16_6_24で98dpをちょうど埋める`() {
        composeTestRule.showDetail()
        val info = bounds("detail_info_column")
        val title = bounds("detail_info_title")
        val chip = bounds("detail_info_chip")

        // 作者・チップ・アキは **T2 でも案A でも動いていない**（F=1.0 なら 16F=16・14F+10=24・アキは常に6）。
        val author = bounds("detail_info_author")
        assertDp("作者の行（行送り16dp・1行nowrap）", 16.dp, author.bottom - author.top)
        assertDp("チップの高さ（行14＋padding4×2＋枠1×2）", 24.dp, chip.bottom - chip.top)
        // 題名の箱＝98−(6+16+6+24)。46 の出どころは「自然行高 22.75 の2行＝45.5dp が入る最小の整数」＝T2。
        assertDp("題名の箱＝98−(6+16+6+24)", 46.dp, title.bottom - title.top)
        assertDp("アキ（題名→作者）", 6.dp, author.top - title.bottom)
        assertDp("アキ（作者→チップ）", 6.dp, chip.top - author.bottom)
        // 中身がちょうど 98dp を埋める＝溢れも余りも無い。getUnclippedBoundsInRoot は親の高さで
        // 切られない実位置を返すので、内訳が狂えばここが 98 からずれて赤くなる。
        assertDp("中身の総高（列の上端からチップ下端まで）", 98.dp, chip.bottom - info.top)
    }

    /**
     * 題名の箱 46dp が「2行を本当に描くための値」であることの守り（T2 の核心）。
     *
     * ⚠️ **箱の高さを測るテストではこれを守れない**。旧 44dp でも箱は 44dp ちょうどに測れてしまい、
     * 「2行ぶんの箱があるのに1行しか組まれていない」状態が緑で通っていた（正本もコードも「題名は2行」と
     * 書きながら2行目が一度も描かれなかった既存バグ）。箱を 46→44 に戻すとここだけが赤くなる。
     */
    @Test
    fun `長い題名は2行が実際に組まれる`() {
        composeTestRule.showDetail()
        assertEquals("箱46dp に自然行高22.75×2＝45.5dp が入る＝2行が組まれる", 2, titleLineCount())
    }

    /**
     * 「内容に依らず 98dp」の担保。1行に収まる短い題名でも題名の箱が 46dp のままであること
     * （箱の高さ指定を落とすとここだけが赤くなる＝短題名の作品でだけ上下対称が崩れる退行を捕まえる）。
     */
    @Test
    fun `題名が1行に収まっても情報列の高さは変わらない`() {
        composeTestRule.showDetail(title = TITLE_ONE_LINE)
        val band = bounds("detail_cover_band")
        val info = bounds("detail_info_column")
        val title = bounds("detail_info_title")
        val chip = bounds("detail_info_chip")

        // 実際に組まれるのは1行。それでも下の3つが動かない＝「箱は内容でなく fontScale だけで決まる」。
        assertEquals("短題名なら組まれる行は1行", 1, titleLineCount())
        assertDp("短題名でも題名の箱は2行ぶん", 46.dp, title.bottom - title.top)
        assertDp("短題名でも中身の総高は98dp", 98.dp, chip.bottom - info.top)
        assertDp("短題名でも下余白は11dp", 11.dp, band.bottom - info.bottom)
        assertDp("短題名でも情報列の高さ", 98.dp, info.bottom - info.top)
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

        // ⚠️ ここは **6つとも 2026-08-26 の裁定で動いていない**（案A が伸ばすのは帯と情報列だけ）。
        // ブロック = max(帯120, カード下端164) + 逃げ2 で、F=1.0 では 164 が支配的＝166 のまま。
        assertDp("ブロック高", 166.dp, block.bottom - block.top)
        assertDp("カード幅", 114.dp, card.right - card.left)
        assertDp("カード高", 152.dp, card.bottom - card.top)
        assertDp("カード左＝ページ余白 S24", 24.dp, card.left - block.left)
        // ⚠️ カードの上端は **S12 固定**＝情報列の内側余白（いまは 11dp）とは別の値になった。
        // 案A でカードは追従しない（dp 固定）と決めたので、ここが 12 のままなのが正しい。
        assertDp("カード上＝S12 固定（帯の内側余白 11dp とは別物）", 12.dp, card.top - band.top)
        // 越え = カード下端164 − 帯120。F と共に痩せる量なので、F=1.0 のこの 44 が最大値。
        assertDp("カードが帯の境界を越える量", 44.dp, card.bottom - band.bottom)
        assertDp("カード右と情報列左の間 S16", 16.dp, info.left - card.right)
        assertDp("情報列の右余白 S24", 24.dp, block.right - info.right)
    }

    /**
     * 案A（帯の fontScale 追従）の守り。**F=1.0 のテストだけでは案A を1つも守れない**——
     * 74·F + 46 は F=1.0 で 120 という従来値に一致するので、追従を丸ごと消しても他のテストは全部緑になる。
     *
     * 2.0 を選ぶのは、①実機で壊れて裁定の発端になった段がここで、②「帯がカードを飲む」
     * （越えが 0 になる＝案A の受諾済みの代償）が現れる唯一の段だから。
     */
    @Test
    fun `fontScale2_0では帯と情報列だけが式どおり伸びカードは伸びない`() {
        composeTestRule.showDetail(fontScale = 2f)
        val block = bounds("detail_cover_block")
        val band = bounds("detail_cover_band")
        val card = bounds("detail_cover_card")
        val info = bounds("detail_info_column")
        val title = bounds("detail_info_title")

        assertDp("帯 ＝ 74×2.0 + 46", 194.dp, band.bottom - band.top)
        assertDp("情報列 ＝ 76×2.0 + 22", 174.dp, info.bottom - info.top)
        // 余白 = 12 − F。F=1.0 の 11 と同じく (帯 − 情報列) / 2 から導かれる値で、対称であることが規定。
        assertDp("帯の上端から情報列まで ＝ 12 − 2.0", 10.dp, info.top - band.top)
        assertDp("情報列から帯の下端まで（上下対称）", 10.dp, band.bottom - info.bottom)
        assertDp("題名の箱 ＝ 46×2.0", 92.dp, title.bottom - title.top)
        // 箱 92 ≧ 2行 45.5×2.0＝91。⚠️ 比 46/45.5 は F に依らないので、ここが2行なら全段で2行になる
        // （旧 44 のままだと 88 < 91 で、2.0 でも1行しか組まれない）。
        assertEquals("大きい文字設定でも題名は2行が組まれる", 2, titleLineCount())

        // ---- 追従**しない**側（案A で伸ばすと決めなかったもの）----
        assertDp("カード幅（dp 固定）", 114.dp, card.right - card.left)
        assertDp("カード高（dp 固定）", 152.dp, card.bottom - card.top)
        assertDp("カード上端 ＝ S12 固定", 12.dp, card.top - band.top)
        assertDp("ブロック ＝ max(帯194, カード下端164) + 逃げ2", 196.dp, block.bottom - block.top)
        // ⚠️ 2.0 では帯がカードを飲む（F=1.0 の「44dp 越える」が 0 を通り越して逆転する）。
        // これは意匠の破綻ではなく**受諾済みの代償**なので、「はみ出しが消えたから帯を縮めよう」で
        // 案A を戻されないよう、飲んでいること自体を数で固定する（164 に対し帯が 194）。
        assertDp("帯がカード下端を飲み込む量", 30.dp, band.bottom - card.bottom)
    }

    private companion object {
        /**
         * 幅182dpの情報列で 16sp なら 2行になる長さ（正本モックが載せている題名そのもの）。
         * ⚠️ **2行に折り返す長さであることが本ファイルの前提**（[titleLineCount] の期待値2の根拠）。
         * 短くすると、題名の箱が2行ぶん確保されているかを誰も測らなくなる。
         */
        const val TITLE_TWO_LINES = "追放された万能薬師、辺境でスローライフを始める"

        /** 1行に確実に収まる短題名。 */
        const val TITLE_ONE_LINE = "春嵐"
    }
}
