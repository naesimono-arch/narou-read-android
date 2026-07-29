package com.novelreader.ui.skins.k

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.TocEntry
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「目次」の**話数ラベルが3桁・4桁になる長編**のスクリーンショット回帰。
 *
 * なぜ [TocKScreenshotTest] と別クラスか（＝この golden が塞ぐ穴）: 既存 golden の fixture は8話固定で、
 * 話数ラベルは1桁しか出ない。ところが [TocKScreenshotTest] は「話数ラベル 44dp の整列幅」を赤くなる
 * 条件に挙げていた＝**その幅が壊れるケースを fixture が一度も持っていなかった**。実際、
 * 2026-07-29 の実機検証で「第132話」が 44dp に収まらず「第132」「話」で折り返し行高まで崩れる退行が
 * 見つかったが、37枚の golden は全て緑のままだった。実蔵書には 221 話・282 話・860 話の本が実在し、
 * なろう系は4桁も普通＝通常利用で必ず踏む経路をここで固定する。
 *
 * テーマ×スケール全数を回さない理由: この golden が主張するのは**桁数に対する整列幅の追従**だけで、
 * 色トークンとフォントスケール一般の破綻は [TocKScreenshotTest] の全数マトリクスが既に張っている
 * （横向き golden [BookshelfKLandscapeScreenshotTest] と同じ絞り方）。ただし4桁だけは
 * 「最長ラベル × 最大フォント」＝題名列が最も痩せる worst case なので scale 2.0 も1枚撮る。
 *
 * このテストが赤くなる条件:
 *  ・話数ラベルが折り返す/切り詰まる（整列幅が桁数へ追従しなくなった）
 *  ・整列幅が行ごとに変わる（題名の開始 x が行で揃わなくなった）
 *  ・桁数が増えたぶん題名列が痩せて題名の省略位置・行高が変わる
 *  ・現在地バーの「全N話・読了率X%」が桁数の多い N で崩れる
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TocKEpisodeDigitsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture_threeDigits() = capture(
        caseId = "ep3digits",
        total = 282, // 実蔵書に実在する話数（3桁）
        currentEpisode = 132, // 実機で折り返した当のラベル「第132話」を画面内に置く
        fontScale = 1.0f,
    )

    @Test
    fun capture_fourDigits() = capture(
        caseId = "ep4digits",
        total = 1240, // 4桁（なろう系の長編は 1000 話超が珍しくない）
        currentEpisode = 1024,
        fontScale = 1.0f,
    )

    @Test
    fun capture_fourDigitsLargeFont() = capture(
        // 最長ラベル × 最大フォント＝題名列が最も痩せる worst case。
        caseId = "ep4digits",
        total = 1240,
        currentEpisode = 1024,
        fontScale = 2.0f,
    )

    private fun capture(caseId: String, total: Int, currentEpisode: Int, fontScale: Float) {
        val theme = ReadingTheme.LIGHT
        composeTestRule.captureSkinK(
            theme,
            fontScale,
            goldenName("TocK", caseId, theme, fontScale),
        ) { colors ->
            TocK(
                tocState = TocState.Content(longToc(total)),
                colors = colors,
                workTitle = "辺境の薬師は千日の旅路をゆく",
                currentChapterFile = "chap_$currentEpisode.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
    }

    /**
     * 長編の目次データ。題名は長短を周期で混ぜる（1行に収まる行と行幅で詰まる行の両方を golden に載せる
     * ＝既存 fixture と同じ意図）。fileName は `chap_<話数>.html`＝現在章の指定が話数と直結して読める形。
     */
    private fun longToc(total: Int): List<TocEntry> = (1..total).map { n ->
        TocEntry(title = LONG_TOC_TITLES[n % LONG_TOC_TITLES.size], fileName = "chap_$n.html")
    }

    private companion object {
        val LONG_TOC_TITLES = listOf(
            "帰路",
            "夜明けの峠を越えて、名も無き村へ至る道すがら",
            "薬草採りの朝",
            "旅の途中で交わした約束と、置いてきた灯りのこと",
            "静かな雨",
        )
    }
}
