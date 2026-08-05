package com.novelreader.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.ui.VerticalChapterContent
import com.novelreader.ui.theme.ReadingTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [VerticalChapterContent] の縦書き章本文 golden（P3 完了定義）。LazyRow(reverseLayout) の版面全体
 *（章見出し・段落・空行・hr・前後書きブロック・ルビ/句読点/縦中横）を画像で固定する。
 *
 * なぜ LIGHT/DARK × 4 ケース＝8 枚か: 本文は Canvas 直描き（px 直指定）で fontScale に依存せず、色差の
 * 退行検知にはライト/ダークの対極 2 点で足りる（SEPIA・2.0 は描画分岐に新経路を通さない）。golden を最小に保つ
 *（ScreenshotConfig の全マトリクスでなく本テスト固有の 2 テーマ×4 ケースを回す）。header/ep4digits は
 * 章見出しの話数ラベル（2026-08-06 裁定①）の縦積みも固定する。
 *
 * 注意（Robolectric のフォントレンダリング）: vert フィーチャの効きは実機と割れうるため句読点/括弧の縦字形は
 * golden 上で実機と一致しない可能性がある。回帰固定としては有効（列送り・右→左積み・ルビ位置・枠クロームの
 * 構造が保たれる）。見た目の言語化は報告本文に記す。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class VerticalChapterContentScreenshotTest(
    private val theme: ReadingTheme,
    private val caseId: String,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "VerticalChapterContent_${caseId}_${ScreenshotConfig.themeLabel(theme)}.png"
        composeTestRule.captureThemed(theme, fontScale = 1.0f, name) { colors ->
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                VerticalChapterContent(
                    content = content(caseId),
                    colors = colors,
                    fontSize = 17,
                    lineHeightEm = 2.4f,
                    bodyMarginDp = 20,
                    chapterNumber = chapterNumberFor(caseId),
                )
            }
        }
    }

    companion object {

        /**
         * 章見出しの話数ラベル（2026-08-06 裁定①）を golden に載せる話数。normal/block は題が漢数字
         * ラベル持ち（第百二十七話　城門）＝補完抑止で描画不変のため null のまま（既存 golden を保つ）。
         * header はモックの canonical 例（第 百二十七 話）・ep4digits は最長ラベルで縦積みの整列を固定
         * （TocKEpisodeDigitsScreenshotTest と同じ「桁数の穴を塞ぐ」流儀）。
         */
        private fun chapterNumberFor(caseId: String): Int? = when (caseId) {
            "header" -> 127
            "ep4digits" -> 1024
            else -> null
        }

        private fun content(caseId: String): ChapterContent = when (caseId) {
            // 通常章: ルビ・句読点・鉤括弧・縦中横（12＝TCY・!?＝TCY）・波・ダッシュの混在。
            "normal" -> ChapterContent(
                title = "第百二十七話　城門",
                segments = persistentListOf(
                    TextSegment.Ruby("辺境", "へんきょう"),
                    TextSegment.Plain("の"),
                    TextSegment.Ruby("城門", "じょうもん"),
                    TextSegment.Plain("に着いたのは、12月3日の!?——そんな夕暮れだった。"),
                    TextSegment.LineBreak,
                    TextSegment.Plain("「——ここまで、よく付いてきてくれた」と"),
                    TextSegment.Ruby("彼女", "かのじょ"),
                    TextSegment.Plain("は言った。"),
                ),
            )
            // block+hr+空行章: 前書きブロック → 本文 → 空行 → シーン区切り → 本文。
            "block" -> ChapterContent(
                title = "第百二十八話　朝市",
                segments = persistentListOf(
                    TextSegment.StyledBlock(
                        label = "前書き",
                        segments = persistentListOf(
                            TextSegment.Plain("お読みいただき"),
                            TextSegment.Ruby("感謝", "かんしゃ"),
                            TextSegment.Plain("します。"),
                        ),
                    ),
                    TextSegment.Plain("城門の外には、昨夜のうちに集まった兵たちが整列していた。"),
                    TextSegment.LineBreak,
                    TextSegment.LineBreak,
                    TextSegment.HorizontalRule,
                    TextSegment.Plain("彼女は一歩前へ進み出ると、置いたはずの剣を再び手に取った。"),
                ),
            )
            // 章見出し強調: 長い題（縦書きで複数列に折り返し＋藍の縦ルール）を主役にした薄い本文。
            // 題に接頭辞が無い＝chapterNumberFor の 127 が「第 百二十七 話」として補完される
            // （モック reading-vertical-scroll-D .chap-h の canonical 例と同構成）。
            "header" -> ChapterContent(
                title = "雨上がりの城門にて、彼女は静かに剣を置いた",
                segments = persistentListOf(
                    TextSegment.Plain("濡れた石畳が夕陽を照り返している。"),
                ),
            )
            // 4桁話数: 最長ラベル「第 千二十四 話」の縦積み（1マス縦積み＋.3em ギャップ）が列内で
            // 崩れないことを固定（実蔵書最大860話・なろう長編は4桁が普通）。
            "ep4digits" -> ChapterContent(
                title = "雨上がりの城門にて、彼女は静かに剣を置いた",
                segments = persistentListOf(
                    TextSegment.Plain("濡れた石畳が夕陽を照り返している。"),
                ),
            )
            else -> error("unknown caseId=$caseId")
        }

        @JvmStatic
        @Parameters(name = "{1}_{0}")
        fun data(): List<Array<Any>> {
            val themes = listOf(ReadingTheme.LIGHT, ReadingTheme.DARK)
            val cases = listOf("normal", "block", "header", "ep4digits")
            return themes.flatMap { t -> cases.map { c -> arrayOf<Any>(t, c) } }
        }
    }
}
