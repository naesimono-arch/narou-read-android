package com.novelreader.ui.skins.k

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.TocEntry
import com.novelreader.ui.TocState
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.screenshot.goldenName
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「目次」（[TocK]）のスクリーンショット回帰（ADR 0009 増補1）。
 *
 * 既存の NativeTableOfContentsScreenScreenshotTest は D 実装の目次を撮っており、K の目次は別実装
 * （現在地バー・行の語彙化・「ここから再開」チップ）ゆえ一切カバーされていなかった。K は既定スキン
 * ＝出荷時に実際に開かれる目次はこちら（ADR 0027 の単独公開スコープ）。
 *
 * 撮る状態と選定理由: Content・第3話が現在章（全8話）。
 *  この1状態で K 目次の見分けが全部そろう＝既読行（題名を沈めて行末✓）・現在行（藍の左ルール＋藍10%地＋
 *  太字＋唯一の実アクション「ここから再開」）・未読行（通常）・現在地バー（「いま読んでいる: 第3話」チップ＋
 *  「全8話・読了率25%」）。既読も現在も未読も出ない状態（例: 未読で開いた目次）を1枚目に選ぶと、
 *  K が最も主張している「いま自分がどこか」の表現がまるごと無検査になる。
 *  読了率は現在章 index÷全話数＝画面の✓数と一致する定義（捏造しない）ため、2/8=25% が golden に載る。
 *  初期スクロール位置（現在章の1つ手前＝第2話が先頭）も同じ理由でそのまま golden に載る。
 *
 * このテストが赤くなる条件:
 *  ・ヘッダ（←44dp タップ面・「目次」＋作品名サブ1行省略・下罫）
 *  ・現在地バーの有無/文言/藍10%ピル/右の進捗表記（「全N話・読了率X%」）
 *  ・行の構造（話数ラベルの整列幅・明朝の題名・行の上下余白・下罫）
 *    ただし**この fixture は8話固定＝話数ラベルは1桁しか出ない**。整列幅が桁数で壊れる退行
 *    （3桁「第132話」の折り返し・2026-07-29 実機）はここでは検出できず、
 *    [TocKEpisodeDigitsScreenshotTest]（3桁/4桁）が担当する。
 *  ・既読/現在/未読の描き分け（沈め色・左ルール3dp・地色・✓アイコン・「ここから再開」チップの塗り）
 *  ・初期スクロール位置の導出（現在章の1つ手前）が変わった場合
 *  ・ReadingColors（background/text/textSecondary/accent/divider/infoText）の値変更
 *  ・fontScale 2.0 で行が崩れる/切り詰まる変化
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TocKScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    // 章題は長短を混ぜる（1行に収まる行と、行幅で折り返す/詰まる行の両方を golden に載せる）。
    private val tocContent = TocState.Content(
        listOf(
            TocEntry(title = "旅立ちの朝", fileName = "chap_1.html"),
            TocEntry(title = "港町エルドの喧騒と、名も無き剣士の噂", fileName = "chap_2.html"),
            TocEntry(title = "灯台守の少女", fileName = "chap_3.html"),
            TocEntry(title = "海図に無い島", fileName = "chap_4.html"),
            TocEntry(title = "嵐の夜に交わした約束", fileName = "chap_5.html"),
            TocEntry(title = "帰還", fileName = "chap_6.html"),
            TocEntry(title = "静かな戦い", fileName = "chap_7.html"),
            TocEntry(title = "そして灯りは受け継がれる", fileName = "chap_8.html"),
        )
    )

    @Test
    fun capture() {
        composeTestRule.captureSkinK(theme, fontScale, goldenName("TocK", "current", theme, fontScale)) { colors ->
            // TocK は自前で背景（colors.background）と system bar inset を持つ＝素地を敷き足さない。
            TocK(
                tocState = tocContent,
                colors = colors,
                workTitle = "灯台守の少女と海図に無い島",
                // 第3話を現在章にして「既読／現在／未読」の3種を1画面に同居させる。
                currentChapterFile = "chap_3.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
