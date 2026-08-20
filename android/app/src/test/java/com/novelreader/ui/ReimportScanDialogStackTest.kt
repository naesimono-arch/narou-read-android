package com.novelreader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 再取込（指紋あり）ダイアログ [ReimportScanDialog] の3操作が **fontScale を上げても縦3段のまま**
 * であることを固定する不変条件テスト。裁定＝2026-08-20 実機裁定（正本モック
 * `docs/design-candidates/bookshelf-reimport-badge-D.html` の分岐②＝`.dlg-acts.stack`）。
 *
 * ## 何を守るか（実機で観測した退行の形）
 * 2ボタンを `dismissButton` の `Row` に詰めていた旧実装では、M3 の `AlertDialogFlowRow` から
 * その `Row` が幅544px の巨大要素1個に見え、確定（336px）と並べると内寸1088px を超えて折り返し、
 * 〈1段＋2段〉に割れていた（実機 OPPO PGEM10: 「場所から探す」だけ y=1774–1855、
 * 「自分で選ぶ」「やめる」が y=2014–2095 で同段）。折り返し任せである限り段構成は
 * 端末幅・fontScale・文言長で変わる＝**どう見えるかをモックが決められない**のが不快の正体だった。
 *
 * ## なぜ golden（絵）でなくレイアウト値の断言か
 * 「割れているか揃っているか」は絵でも見えるが、golden は**壊れた絵をそのまま正として焼ける**
 * （`docs/knowledge/golden-record-bakes-in-regressions.md`）。ここでは段構成そのもの＝
 * 「3つの矩形が重ならず、モックの段順で上から下へ並ぶ」を直接言い切る。
 *
 * ## なぜ本番の Composable を直に描くか
 * 同じ版面を撮る [com.novelreader.ui.screenshot.BookshelfDialogScreenshotTest] は、ダイアログが
 * `BookshelfScreen` のインラインラムダだった時代の名残で**テスト内の写し**を組んでいた。写しを検査する
 * テストは本番が Row へ戻っても緑のまま通る＝番人にならない。そのため本便で本番側を
 * `internal` な [ReimportScanDialog] へ切り出し、写しではなく実物を描いている。
 *
 * ⚠️ [GraphicsMode] NATIVE 必須。判定材料が**文字の実測幅で決まる版面**なので、既定の LEGACY
 * （文字幅＝字数の代用計量）では旧実装ですら折り返さず、断言が自明に真になって検出力が消える
 * （`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 *
 * 画面幅は実機主戦場かつ golden 群と同じ 360dp。スキンは ADR 0027 の初回公開スコープ＝明快K。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ReimportScanDialogStackTest(private val fontScale: Float) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun 三操作が縦三段に並ぶ() {
        // fontScale はダイアログ窓（別ウィンドウ）に LocalDensity 上書きが届かないため Configuration 側で与える
        // （機序＝ScreenshotTestSupport.captureDialogSkinned の KDoc）。setContent より前でなければならない。
        RuntimeEnvironment.setFontScale(fontScale)
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                ReimportScanDialog(
                    // 場所未記憶＋ヒント行あり＝この分岐で最も縦に伸びる組合せ（golden の撮り方と同条件）。
                    folderRemembered = false,
                    fileNameHint = "悪役令嬢に転生したはずが気づけば魔王の義妹になっていました.pdf",
                    onScan = {},
                    onPick = {},
                    onDismiss = {},
                )
            }
        }

        val scan = bounds("場所から探す")
        val pick = bounds("自分で選ぶ")
        val cancel = bounds("やめる")

        // 段順は〈主→副→取消〉＝M3 の縦積み規約（確定が最上段）。重なりが無いことと順序を同時に言う。
        assertStacked("場所から探す", scan, "自分で選ぶ", pick)
        assertStacked("自分で選ぶ", pick, "やめる", cancel)

        // 右揃え（モック .dlg-acts.stack の align-items:flex-end）。Column の horizontalAlignment が
        // 落ちると左寄せの列になり、絵は「縦に並んではいる」ので上の断言だけでは素通りする。
        assertEquals("右端が揃わない（scale=$fontScale）", scan.right.value, pick.right.value, 0.5f)
        assertEquals("右端が揃わない（scale=$fontScale）", scan.right.value, cancel.right.value, 0.5f)
    }

    private fun bounds(text: String): DpRect =
        composeTestRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    private fun assertStacked(upperLabel: String, upper: DpRect, lowerLabel: String, lower: DpRect) {
        assertTrue(
            "「$upperLabel」($upper) と「$lowerLabel」($lower) が同じ段に乗っている（scale=$fontScale）",
            lower.top.value >= upper.bottom.value - 0.5f,
        )
    }

    companion object {
        /** 1.0=既定、2.0=拡大（この裁定の採用理由が「fontScale を上げても段構成が変わらない」こと）。 */
        @JvmStatic
        @Parameters(name = "scale{0}")
        fun data(): List<Array<Any>> = listOf(arrayOf<Any>(1.0f), arrayOf<Any>(2.0f))
    }
}
