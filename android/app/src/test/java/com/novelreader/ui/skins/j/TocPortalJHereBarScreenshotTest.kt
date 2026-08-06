package com.novelreader.ui.skins.j

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.TocEntry
import com.novelreader.ui.TocState
import com.novelreader.ui.screenshot.captureSkinned
import com.novelreader.ui.screenshot.goldenName
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ポータルJ「目次」の現在地バー（.here＝TocHereBarJ）のスクリーンショット回帰。
 *
 * なぜ新設か（監査 2026-08-06 G-1）: 現在地バーは K/D/M/J の4スキン同型で、fontScale 2.0 で進捗文
 * 「全N話・読了率X%」が折り返して章一覧を押し出す破綻も同型だった。ところが J は golden 0枚＝
 * 同便で実装を直しても再発が絵で見えないため、破綻条件（4桁話数 × 1.0/2.0）だけを最小で固定する。
 * 条件・fixture は K の同条件 golden（[com.novelreader.ui.skins.k.TocKEpisodeDigitsScreenshotTest]）に倣う。
 *
 * テーマを light 1本・2枚に絞る理由: J は固定ポータルパレット（Toc/Portal トークン直参照）で
 * ReadingTheme に追従する要素がほぼ無く、テーマ軸はこの golden の主張（現在地バーのレイアウト）に
 * 含まれない。スキン J 全体の意匠網羅は ADR 0027（出荷対象は K 単独）の優先度に従い張らない。
 *
 * このテストが赤くなる条件:
 *  ・現在地バーの進捗が1行・右端寄せを保てなくなる（折返し膨張＝章一覧の押し出し）
 *  ・現在話チップ（淡い金地・金枠・「いま読んでいる: 第N話」）の構成・文言が変わる
 *  ・章行（道の列の縦線・節・話数ラベル・明朝章題）の構成や初期スクロール位置が変わる
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TocPortalJHereBarScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture_fourDigits() = capture(fontScale = 1.0f)

    @Test
    fun capture_fourDigitsLargeFont() = capture(fontScale = 2.0f) // 最長ラベル×最大フォント＝当の破綻条件

    private fun capture(fontScale: Float) {
        val theme = ReadingTheme.LIGHT
        composeTestRule.captureSkinned(
            Skin.PORTAL_J,
            theme,
            fontScale,
            goldenName("TocPortalJ", "ep4digits", theme, fontScale),
        ) {
            TocPortalJ(
                tocState = TocState.Content(longToc(TOTAL)),
                workTitle = "辺境の薬師は千日の旅路をゆく",
                currentChapterFile = "chap_$CURRENT.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
    }

    /** K の同条件 fixture と同じ意図: 題名の長短を周期で混ぜ、収まる行と詰まる行の両方を golden に載せる。 */
    private fun longToc(total: Int): List<TocEntry> = (1..total).map { n ->
        TocEntry(title = LONG_TOC_TITLES[n % LONG_TOC_TITLES.size], fileName = "chap_$n.html")
    }

    private companion object {
        const val TOTAL = 1240 // 4桁（なろう系長編）＝K 側 golden と同値
        const val CURRENT = 1024 // 現在話も4桁＝チップ文言が最長になる
        val LONG_TOC_TITLES = listOf(
            "帰路",
            "夜明けの峠を越えて、名も無き村へ至る道すがら",
            "薬草採りの朝",
            "旅の途中で交わした約束と、置いてきた灯りのこと",
            "静かな雨",
        )
    }
}
