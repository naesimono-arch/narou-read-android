package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.ui.ChapterContent as ChapterContentComposable
import com.novelreader.ui.theme.ReadingTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 横書き章見出し（共通 ChapterHeader＝D/C/K 経路）の**話数ラベルが4桁になる長編**のスクリーンショット回帰
 * （[com.novelreader.ui.skins.k.TocKEpisodeDigitsScreenshotTest] と同じ「桁数の穴を塞ぐ」流儀）。
 *
 * なぜこの golden か: 話数ラベル（2026-08-06 裁定①）は 11sp＋字間 .3em の1行組で、桁が増えるほど
 * 横幅が伸びる。実蔵書には 860 話が実在し、なろう系は4桁も普通＝最長ラベル「第 千二十四 話」が
 * 折り返さず中央整列を保つことを固定する。フォント最大（2.0）は「最長ラベル × 最大フォント」＝
 * 最も折り返しやすい worst case なので1枚だけ足す（テーマ・スケール全数は既存マトリクスの守備範囲）。
 *
 * このテストが赤くなる条件:
 *  ・4桁ラベルが折り返して2行になる／中央整列が崩れる
 *  ・ラベルと題の間隔（.t margin-top 8px）や題・ルールの位置が桁数で変わる
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ChapterHeaderEpisodeDigitsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture_fourDigits() = capture(fontScale = 1.0f)

    @Test
    fun capture_fourDigitsLargeFont() = capture(fontScale = 2.0f)

    private fun capture(fontScale: Float) {
        val theme = ReadingTheme.LIGHT
        composeTestRule.captureThemed(
            theme,
            fontScale,
            goldenName("ChapterHeader", "ep4digits", theme, fontScale),
        ) { colors ->
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                // LocalSkin 既定＝WAMODERN_D → 共通 ChapterHeader 経路（話数ラベルの当事者）。
                ChapterContentComposable(
                    content = ChapterContent(
                        title = "雨上がりの城門にて、彼女は静かに剣を置いた",
                        segments = persistentListOf(
                            TextSegment.Plain("濡れた石畳が夕陽を照り返している。"),
                        ),
                    ),
                    colors = colors,
                    fontSize = 17,
                    lineHeightEm = 2.4f,
                    bodyMarginDp = 20,
                    chapterNumber = 1024, // 4桁（kanjiNumber → 「第 千二十四 話」＝最長級ラベル）
                    totalChapters = 1240,
                )
            }
        }
    }
}
