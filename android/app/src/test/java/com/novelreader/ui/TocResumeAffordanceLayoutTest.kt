package com.novelreader.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.novelreader.model.TocEntry
import com.novelreader.ui.skins.j.TocPortalJ
import com.novelreader.ui.skins.k.TocK
import com.novelreader.ui.skins.m.TocSkyM
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.rememberReadingColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 目次の現在章行が **fontScale 2.0 の長編（4桁話数）でも読める**ことを4スキン同型で固定する不変条件テスト。
 *
 * なぜ golden（絵）でなくレイアウト値の断言か: 破綻は「章題が1行1文字で縦に割れる」「話数ラベル自体が
 * 3行に割れる」「進捗が『全…』で切れる」＝**どれも数値で言い切れる**。golden は変化を見つけるが
 * 「何が正しいか」は言わない（実際、監査 2026-08-06 まで 37枚の golden が全て緑のまま破綻を通していた）。
 * ここでは [TextLayoutResult] を semantics 経由で取り出し、行の折れ方そのものを断言する。
 *
 * 固定する真因（2026-08-06 監査 → 2026-08-07 裁定）:
 *  ・行末の再開チップが**非加重子**として実寸を先取りしていた。文字入りチップは fontScale 2.0 で
 *    幅 156dp（=312px）に膨らみ、内側 Row の残りを食って章題の取り分を 45dp まで削っていた。
 *    裁定＝チップを**アイコンのみ**（▶ の丸・24dp）にする。名前は contentDescription が担う。
 *  ・M/J は話数ラベルが `width(52.dp)` 固定で、4桁「第1240話」が**ラベル自体で3行に割れて**いた。
 *    K と同じ桁数追従（rememberTocEpLabelWidth）へ寄せる。
 *  ・現在地バーの**現在話チップも同じ型の非加重子**で、前置き「いま読んでいる: 」ごと幅を全取りしていた。
 *    実測（2026-08-07・下の条件）＝チップ幅 K 641px / D 627px / J 631px / M 563px（＋星と間隔で 655px）に対し
 *    バーの内寸は 656px＝**チップ単独で溢れており**、同じ行の進捗テキストに残るのは 15/29/25/1px＝**可視0文字**。
 *    裁定＝見える文字を「第N話」だけに短縮する（バー自体が現在地バーなので前置きは文脈で伝わる）。
 *  ⚠️ 行末チップと ep 幅は**同時に**入れないと悪化する: ep 幅だけ広げるとチップが幅を握ったまま章題だけが痩せる。
 *
 * 閾値の根拠: 「1行に全角4文字以上」は**1文字ずつ縦に割れる状態の否定**であり、本実装での実測値
 *（各テストの inline コメントに測定値を残す）から余裕を引いた下限。ピクセル一致でなく破綻の型を縛る。
 * 進捗の閾値を「可視1文字以上」に置くのは、裁定の要件が**読める状態への復帰**そのものであり、
 * 破綻時の実測が一律 0文字だったため（何文字読めるかはスキンの字面で変わる＝実測値は println に残す）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TocResumeAffordanceLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun stopPulses() {
        // M の現在章ドットは rememberInfiniteTransition で脈動し、走ったままだと合成が idle にならず
        // fetchSemanticsNode が安定しない。実画面の reduce-motion 分岐（静止描画）へ倒して決定的にする。
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @Test
    fun k_currentRow_keepsTitleWidth_atLargeFont() {
        setToc(Skin.MEIKAI_K) { colors ->
            TocK(
                tocState = TocState.Content(longToc()),
                colors = colors,
                workTitle = WORK_TITLE,
                currentChapterFile = "chap_$CURRENT.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
        assertResumeIsIconOnly()
        assertTitleNotSplitPerCharacter("K") // 実測 2026-08-07: 1行目 6文字・2行
        assertHereChipShortenedButNamed("K")
        assertProgressIsReadable("K")
    }

    @Test
    fun d_currentRow_keepsTitleWidth_atLargeFont() {
        setToc(Skin.WAMODERN_D) { colors ->
            NativeTableOfContentsScreen(
                tocState = TocState.Content(longToc()),
                colors = colors,
                workTitle = WORK_TITLE,
                currentChapterFile = "chap_$CURRENT.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
        assertResumeIsIconOnly()
        assertTitleNotSplitPerCharacter("D") // 実測 2026-08-07: inline コメント参照
        assertHereChipShortenedButNamed("D")
        assertProgressIsReadable("D")
    }

    @Test
    fun m_currentRow_keepsTitleWidth_atLargeFont() {
        setToc(Skin.SEIZU_M) {
            TocSkyM(
                tocState = TocState.Content(longToc()),
                workTitle = WORK_TITLE,
                currentChapterFile = "chap_$CURRENT.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
        assertResumeIsIconOnly()
        assertEpLabelOnOneLine("M")
        assertTitleNotSplitPerCharacter("M")
        assertHereChipShortenedButNamed("M")
        assertProgressIsReadable("M")
    }

    @Test
    fun j_currentRow_keepsTitleWidth_atLargeFont() {
        setToc(Skin.PORTAL_J) {
            TocPortalJ(
                tocState = TocState.Content(longToc()),
                workTitle = WORK_TITLE,
                currentChapterFile = "chap_$CURRENT.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
        assertResumeIsIconOnly()
        assertEpLabelOnOneLine("J")
        assertTitleNotSplitPerCharacter("J")
        assertHereChipShortenedButNamed("J")
        assertProgressIsReadable("J")
    }

    // ---------------------------------------------------------------- 断言

    /** 再開の到達口は「アイコン＋読み上げ名」だけ＝見える文字は持たない（裁定 2026-08-07）。 */
    private fun assertResumeIsIconOnly() {
        // 名前が無いとアイコンだけでは何のボタンか分からない＝裁定の条件そのもの。
        composeTestRule.onNodeWithContentDescription(RESUME_LABEL, useUnmergedTree = true).assertExists()
        // 文字として描かれていたら幅を先取りする側へ戻っている。
        composeTestRule.onAllNodesWithText(RESUME_LABEL, useUnmergedTree = true).assertCountEquals(0)
    }

    /** 現在章の題名が「1行1〜2文字」で縦に割れていない（＝章題列に実用的な幅が残っている）。 */
    private fun assertTitleNotSplitPerCharacter(skin: String) {
        val layout = layoutOf(composeTestRule.onNodeWithText(CURRENT_TITLE, useUnmergedTree = true))
        val firstLineChars = layout.getLineEnd(0, visibleEnd = true)
        println("[toc-resume] $skin 章題: 1行目=${firstLineChars}文字 / 全${layout.lineCount}行")
        assertTrue(
            "$skin: 章題の1行目が ${firstLineChars} 文字＝縦に割れている（章題列の幅が足りない）",
            firstLineChars >= MIN_CHARS_PER_LINE,
        )
    }

    /**
     * 話数ラベルは1行に収まる（M/J の `width(52.dp)` 固定は4桁でラベル自体が3行に割れていた）。
     * 現在地バーのチップも短縮後は同じ「第N話」を描くため、**cd を持たない方＝行のラベル**で名指しする。
     */
    private fun assertEpLabelOnOneLine(skin: String) {
        val layout = layoutOf(
            composeTestRule.onNode(hasText(EP_LABEL) and !hasContentDescription(HERE_CHIP_CD), useUnmergedTree = true),
        )
        println("[toc-resume] $skin 話数ラベル「$EP_LABEL」: ${layout.lineCount}行")
        assertEquals("$skin: 話数ラベルが折り返している（整列幅が桁数へ追従していない）", 1, layout.lineCount)
        assertFalse("$skin: 話数ラベルが切り詰められている", layout.hasVisualOverflow)
    }

    /**
     * 現在話チップは**見える文字が「第N話」だけ**で、削った前置きは読み上げに残っている（裁定 2026-08-07）。
     * 見た目の短縮と a11y の情報保持は表裏＝どちらか片方だけ戻る改修を両方向から塞ぐ。
     */
    private fun assertHereChipShortenedButNamed(skin: String) {
        // 読み上げには画面の文脈が無いので、視覚から削った語はここに残っていなければならない。
        composeTestRule.onNode(hasText(HERE_CHIP_LABEL) and hasContentDescription(HERE_CHIP_CD), useUnmergedTree = true)
            .assertExists("$skin: 現在話チップが「$HERE_CHIP_LABEL」＋読み上げ名「$HERE_CHIP_CD」になっていない")
        // 前置きが**見える文字**として戻っていたら、進捗の幅をまた奪う側へ逆戻りしている（区切り文字差も拾う部分一致）。
        composeTestRule.onAllNodesWithText(DROPPED_CHIP_PREFIX, substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /**
     * 現在地バーの進捗（全N話・読了率X%）が**1文字も読めない状態でない**ことを固定する。
     * 破綻時はチップが非加重子として幅を全取りし、進捗の残り幅が 1〜29px＝可視0文字だった（KDoc の実測値）。
     * 末尾省略そのものは許容する（1行固定で縮退させる裁定＝章一覧の押し出しより軽い）ため、
     * 断言は「可視文字が存在する」まで。実際に何文字読めるかはスキンの字面で変わるので実測値を println に残す。
     */
    private fun assertProgressIsReadable(skin: String) {
        val progress = composeTestRule.onNodeWithText(PROGRESS_PREFIX, substring = true, useUnmergedTree = true)
        val layout = layoutOf(progress)
        val visibleChars = layout.getLineEnd(0, visibleEnd = true)
        val chip = composeTestRule.onNode(
            hasText(HERE_CHIP_LABEL) and hasContentDescription(HERE_CHIP_CD),
            useUnmergedTree = true,
        )
        println(
            "[toc-resume] $skin 進捗: 省略=${layout.hasVisualOverflow}" +
                " / 可視${visibleChars}文字" +
                " / 進捗幅=${progress.fetchSemanticsNode().size.width}px" +
                " / 現在話チップ幅=${chip.fetchSemanticsNode().size.width}px",
        )
        assertTrue(
            "$skin: 進捗の可視文字が ${visibleChars} 文字＝現在話チップが行の幅を全取りしている",
            visibleChars >= MIN_PROGRESS_VISIBLE_CHARS,
        )
    }

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = requireNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action) {
            "GetTextLayoutResult アクションが無い＝Text ノードでない"
        }
        action(results)
        return results.first()
    }

    // ---------------------------------------------------------------- 土台

    private fun setToc(skin: Skin, content: @Composable (ReadingColors) -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            // フォントスケールだけ差し替える（density は端末値を維持）＝既存の撮影ヘルパと同方式。
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = FONT_SCALE),
            ) {
                NovelReaderTheme(skin = skin, theme = ReadingTheme.LIGHT) {
                    content(rememberReadingColors(ReadingTheme.LIGHT))
                }
            }
        }
    }

    /**
     * 長編の目次。現在章だけ題名を一意にして [onNodeWithText] で名指しできるようにする
     *（他行は既存 golden の fixture と同じく長短を周期で混ぜる）。
     */
    private fun longToc(): List<TocEntry> = (1..TOTAL).map { n ->
        val title = if (n == CURRENT) CURRENT_TITLE else FILLER_TITLES[n % FILLER_TITLES.size]
        TocEntry(title = title, fileName = "chap_$n.html")
    }

    private companion object {
        const val TOTAL = 1240 // 4桁（なろう系長編）＝既存 golden と同値
        const val CURRENT = 1024 // 現在話も4桁＝ラベル・チップ文言が最長になる
        const val FONT_SCALE = 2.0f // 当の破綻条件（端末の「最大」文字サイズ）
        const val WORK_TITLE = "辺境の薬師は千日の旅路をゆく"
        const val CURRENT_TITLE = "雨上がりの城門にて、彼女は静かに剣を置いた"
        const val EP_LABEL = "第${CURRENT}話"
        const val RESUME_LABEL = "ここから再開"
        const val PROGRESS_PREFIX = "全${TOTAL}話"

        /** 短縮後に現在話チップが描く文字。行の話数ラベルと同じ字面になるため、名指しは cd の有無で分ける。 */
        const val HERE_CHIP_LABEL = EP_LABEL

        /** チップの読み上げ名。期待値は production の定数を引かず literal で持つ（実装側の書き換えを検知するため）。 */
        const val HERE_CHIP_CD = "いま読んでいる: 第${CURRENT}話"

        /** 視覚から削った前置き。区切り（「: 」/半角空白）のスキン差ごと拾えるよう部分一致で使う。 */
        const val DROPPED_CHIP_PREFIX = "いま読んでいる"

        /** 全角4文字＝「1文字ずつ縦に割れている」状態の否定（実測値からの下限。KDoc「閾値の根拠」参照）。 */
        const val MIN_CHARS_PER_LINE = 4

        /** 進捗は末尾省略まで許容し「1文字も読めない」だけを不可とする（破綻時の実測は4スキンとも 0文字）。 */
        const val MIN_PROGRESS_VISIBLE_CHARS = 1

        val FILLER_TITLES = listOf(
            "帰路",
            "夜明けの峠を越えて、名も無き村へ至る道すがら",
            "薬草採りの朝",
            "旅の途中で交わした約束と、置いてきた灯りのこと",
            "静かな雨",
        )
    }
}
