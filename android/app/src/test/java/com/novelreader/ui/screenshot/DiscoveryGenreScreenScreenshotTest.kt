package com.novelreader.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.discovery.DiscoveryGenreScreen
import com.novelreader.ui.skins.k.captureSkinK
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ジャンル選択（nav ルート `discovery/genre`＝[DiscoveryGenreScreen]）のスクリーンショット回帰。
 * **監査 2026-08-06 G-8 で 0枚だったさがす配下の面**を埋める束（優先度2位）。
 * K 素地で包む理由は [NovelDetailScreenScreenshotTest] の KDoc と同じ。
 *
 * ### なぜ case が1つで足りるか
 * この画面は引数が callback 3本だけで、描く中身は [com.novelreader.narou.model.NarouGenres] の**固定語彙**
 * ＝状態分岐を持たない。つまり「代表状態」しか存在せず、追加 case を足しても同じ絵になる。
 * 一方で拡大破綻の risk は高い: 大ジャンル見出し＋小ジャンルチップの FlowRow が、
 * 「ヒューマンドラマ」「異世界〔恋愛〕」「ローファンタジー」といった**7〜8字の最長ラベル**を含むため、
 * fontScale 2.0 でチップ1個が行幅を超えると折り返し段数が跳ね、画面が縦に伸びる。
 * 実データ相当を作り込む必要がないのはこの画面だけ（語彙が本番そのものだから）。
 *
 * 撮っていないもの（既知の穴）: 画面下端（スクロール送り後）の「その他」「ノンジャンル」系。
 * 上端の絵に最長ラベル群がすべて含まれるため、折り返し退行はこの1枚で捕まえられると判断した。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DiscoveryGenreScreenScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureSkinK(theme, fontScale, goldenName("DiscoveryGenreScreen", CASE_TOP, theme, fontScale)) { _ ->
            DiscoveryGenreScreen(
                onBack = {},
                onPickBiggenre = { _, _ -> },
                onPickGenre = { _, _ -> },
            )
        }
    }

    companion object {
        private const val CASE_TOP = "top"

        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
