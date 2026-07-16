package com.novelreader.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ShioriCover] の縦組み題字 golden（P2.5）。題字の 1 字描画を CharClassifier→VertGlyphRenderer 経由に
 * 変えた後、約物・波ダッシュ・括弧入りの実データタイトルでも固定グリッド（3列・⋮省略・列送り）が
 * 崩れないことを固定する。
 *
 * なぜ 2 タイトル × 2 テーマ（＝4 枚）か: 題字は Canvas 直描き（px 直指定）で fontScale に依存しないため
 * スケール軸は不要。色差退行はライト/ダークの対極 2 点で足り、タイトルは「！！＋波ダッシュ＋全角空白」系と
 * 「丸括弧」系の 2 実データで ROTATE/UPRIGHT 経路を通す。golden 枚数を最小に保つ。
 *
 * 注意（Robolectric のフォントレンダリング）: JVM 側は vert フィーチャが no-op のため、vert 任せの回転字
 * （（）「」ー～ 等）は golden 上では正立のまま写る＝括弧・長音の実回転は写らない。この golden は「分類
 * 経路に通しても従来グリッドが崩れない」ことの固定であり、（）～ー の回転の実確認は実機（呼び出し側）で行う。
 * 自前 90 度回転する実測字（…‥；−・半角 ASCII）は Robolectric でも回転して写る。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ShioriCoverScreenshotTest(
    private val theme: ReadingTheme,
    private val caseId: String,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "ShioriCover_${caseId}_${ScreenshotConfig.themeLabel(theme)}.png"
        composeTestRule.captureThemed(theme, fontScale = 1.0f, name) { _ ->
            // 書影は 2:3。設計基準幅 150px 相当のサイズで固定 px 意匠が潰れないようにする。
            Box(modifier = Modifier.size(width = 150.dp, height = 225.dp)) {
                ShioriCover(title = TITLES.getValue(caseId), modifier = Modifier.size(150.dp, 225.dp))
            }
        }
    }

    companion object {
        // 実データタイトル（edgecase_corpus.txt の title セクション）。
        private val TITLES = mapOf(
            // N0833HI＝！！・波ダッシュ(～)・全角空白入り（正立の全角約物 ＋ 回転対象の波ダッシュ）。
            "bang" to "あたしの悪徳領主様！！　～俺は星間国家の悪徳領主！　外伝～",
            // N9463BR＝丸括弧（）入り（ROTATE→vert 経路。Robolectric では正立のまま写るが列は崩れない）。
            "paren" to "僕と彼女と実弾兵器（アンティーク）",
        )

        @JvmStatic
        @Parameters(name = "{1}_{0}")
        fun data(): List<Array<Any>> {
            val themes = listOf(ReadingTheme.LIGHT, ReadingTheme.DARK)
            val cases = listOf("bang", "paren")
            return themes.flatMap { t -> cases.map { c -> arrayOf<Any>(t, c) } }
        }
    }
}
