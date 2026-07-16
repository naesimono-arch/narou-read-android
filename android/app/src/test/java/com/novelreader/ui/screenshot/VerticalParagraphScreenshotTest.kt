package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.TextSegment
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.ParagraphLayout
import com.novelreader.typeset.TypesetConstraints
import com.novelreader.typeset.render.PaintFontMetrics
import com.novelreader.ui.compose.VerticalParagraph
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [VerticalParagraph] の縦組み描画 golden（P2 完了定義）。DefaultVerticalTypesetter + PaintFontMetrics で
 * 実組版してから描き、正立・句読点/括弧・縦中横・手動回転・ルビ列跨ぎの各処理を画像で固定する。
 *
 * なぜ LIGHT/DARK の 2 テーマ・fontScale 1.0 のみ（＝計 8 枚）か: 本文は Canvas 直描き（px 直指定）で
 * fontScale に依存せず、色差の退行検知にはライト/ダークの対極 2 点で足りる（SEPIA と 2.0 は
 * VerticalParagraph の描画分岐に新たな経路を通さない）。golden 枚数を最小に保つ。
 *
 * 注意（Robolectric のフォントレンダリング）: JVM 側の vert フィーチャの効きは実機と割れうるため、
 * 句読点/括弧の縦字形は golden 上で実機と一致しない可能性がある。回帰固定としては有効（回転・縦中横・
 * ルビ位置・列送りの構造が保たれる）。見た目の言語化は報告本文に記す。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class VerticalParagraphScreenshotTest(
    private val theme: ReadingTheme,
    private val caseId: String,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "VerticalParagraph_${caseId}_${ScreenshotConfig.themeLabel(theme)}.png"
        composeTestRule.captureThemed(theme, fontScale = 1.0f, name) { colors ->
            val layout = buildLayout(caseId)
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                VerticalParagraph(
                    layout = layout,
                    fontSizePx = FONT_SIZE_PX,
                    rubyFontSizePx = RUBY_FONT_SIZE_PX,
                    textColor = colors.text,
                    modifier = Modifier.wrapContentSize(Alignment.TopEnd),
                )
            }
        }
    }

    companion object {
        private const val FONT_SIZE_PX = 44f
        private const val RUBY_FONT_SIZE_PX = 22f
        // 列送り＝本文セル + ルビ帯 + わずかな余白（ルビが隣列へ食い込まない幅）。
        private const val COLUMN_ADVANCE_PX = 72f
        private const val COLUMN_HEIGHT_PX = 520f
        // ルビが列を跨ぐことを強制する狭い列高（親文字 5 字×44px=220px を 140px 列で折る）。
        private const val NARROW_COLUMN_HEIGHT_PX = 140f

        private fun constraints(columnHeightPx: Float) = TypesetConstraints(
            columnHeightPx = columnHeightPx,
            fontSizePx = FONT_SIZE_PX,
            rubyFontSizePx = RUBY_FONT_SIZE_PX,
            columnAdvancePx = COLUMN_ADVANCE_PX,
        )

        /**
         * ケース毎の実組版。純層 → 描画層の一貫経路を golden で担保する。
         * なぜ typesetter をここで生成するか: PaintFontMetrics は Paint を作るため Robolectric
         * サンドボックス内でのみ有効。companion の eager val にすると data() 収集時（サンドボックス外）に
         * 初期化され NPE になる。buildLayout は capture() 内＝サンドボックス内で呼ばれる。
         */
        private fun buildLayout(caseId: String): ParagraphLayout {
            val typesetter = DefaultVerticalTypesetter(PaintFontMetrics())
            return when (caseId) {
            // 正立＋句読点＋括弧の混在（読点・句点・鉤括弧・波・ダッシュ・三点リーダ）。
            "punct" -> typesetter.typeset(
                listOf(TextSegment.Plain("彼は言った、「静かに。」と〜——そして")),
                constraints(COLUMN_HEIGHT_PX),
            )
            // 縦中横（2桁=TCY・1桁=正立・4桁=各字回転・!?/!!=TCY）。
            "tatechuyoko" -> typesetter.typeset(
                listOf(TextSegment.Plain("12月3日の!?と1234と1と!!")),
                constraints(COLUMN_HEIGHT_PX),
            )
            // ルビ列跨ぎ（狭い列高で親文字 5 字が 2 列に割れ、ルビも按分される）。
            "ruby" -> typesetter.typeset(
                listOf(TextSegment.Ruby("表現不可能", "ひょうげんふかのう")),
                constraints(NARROW_COLUMN_HEIGHT_PX),
            )
            // 手動回転字（… ‥ ； − ＝vert が効かず自前 90 度回転）。
            "rotate" -> typesetter.typeset(
                listOf(TextSegment.Plain("………‥；−の回転")),
                constraints(COLUMN_HEIGHT_PX),
            )
            else -> error("unknown caseId=$caseId")
            }
        }

        @JvmStatic
        @Parameters(name = "{1}_{0}")
        fun data(): List<Array<Any>> {
            val themes = listOf(ReadingTheme.LIGHT, ReadingTheme.DARK)
            val cases = listOf("punct", "tatechuyoko", "ruby", "rotate")
            return themes.flatMap { t -> cases.map { c -> arrayOf<Any>(t, c) } }
        }
    }
}
