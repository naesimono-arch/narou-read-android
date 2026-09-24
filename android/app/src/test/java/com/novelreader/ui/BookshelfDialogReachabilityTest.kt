package com.novelreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.novelreader.ui.screenshot.BookshelfDialogFixtures as Fx
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [NovelReaderAlertDialog] の本文スロットが可視域を超えたときの到達性の回帰
 * （電池最適化ダイアログ＝本文とチェックボックス行が同じ text スロットを分け合う唯一の器）。
 *
 * ## なぜ絵（golden）でなくここで張るか
 * 是正の実体はスクロール容器の付与で、**スクロール位置0の絵は変わらない**（`weight(fill=false)` は
 * 内容高と残り高さの小さい方を採るため、器が外れても頭から同じ高さぶんが見えるだけ）。
 * 既存の `BookshelfDialogK_battery_*` golden も、器が外れた退行を1枚も検出できない。
 * 「送る手段が残っているか」は到達性そのものを聞くしかない
 * ——同じ判断の前例が [NcodeLinkSheetReachabilityTest]（シート側）。
 *
 * ## 塞いだ穴（2026-08-07・機序は [NovelReaderAlertDialog] の KDoc「増補」が正本）
 * M3 1.3.2 の `AlertDialogContent` は本文を `Modifier.weight(1f, fill = false)` の `Box` に置くだけで
 * 中身を送らない。溢れた本文はチェックボックス行と重なり、末尾は描かれず**触れも読めもしない**。
 * 文言短縮は対症（もっと長い文言・翻訳・より大きな fontScale で必ず再発する）なので、器の側で塞いである。
 *
 * ## 条件の作り方（h480dp・fontScale 2.0）
 * - **fontScale は [RuntimeEnvironment.setFontScale] で入れる**。`AlertDialog` の中身は `Dialog` が起こす
 *   別ウィンドウのサブコンポジションで、その View が自分の Context の configuration から `LocalDensity` を
 *   再提供する＝呼び出し元で積んだ `LocalDensity` は**上書きされて届かない**（実測の根拠は
 *   `ScreenshotTestSupport.captureDialogSkinned` の KDoc）。`setContent` より前に呼ぶ必要がある。
 * - **画面高は h480dp**。既存 golden（`BookshelfDialogK_battery_light_2.0.png`＝w360dp-h640dp・NATIVE）を
 *   画素で実測すると、本文スロットの内容高 **≈343dp** に対し可視域も **≈343dp**＝**余白ゼロで辛うじて
 *   収まっている**（本文7行＋チェック行 ≈686px／ダイアログ以外の固定分＝上下余白・題字2行・ボタン行 ≈489px、
 *   窓の上下余白 ≈52dp）。h640dp のままでは溢れないので到達性を問えない。
 *   h480dp なら可視域は ≈183dp で確実に 160dp ぶん溢れ、かつボタン行（非 weight＝先に高さを取る）も
 *   本文の可視域も十分残る。窓余白の見積りが多少ずれても両側に余裕がある値として選んだ。
 *   文字幅の実測が要るので [GraphicsMode.Mode.NATIVE] を使う（既定の LEGACY は字数近似で幅が崩れる）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h480dp-xhdpi")
class BookshelfDialogReachabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `電池最適化ダイアログは本文が溢れても末尾のチェック行へ到達でき操作ボタンは押し出されない`() {
        // Configuration へ入れる（LocalDensity 上書きはダイアログ窓へ届かない）。setContent より前が必須。
        RuntimeEnvironment.setFontScale(2.0f)
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                BatteryOptDialog()
            }
        }

        // 前提の確認: この条件では末尾のチェック行は初期表示で本文スロットの外に居る（＝溢れが起きている）。
        // ここが緑にならなくなったら「溢れさせる条件」が壊れた合図＝到達性の問い自体が空振りしている。
        composeTestRule.onNodeWithText(Fx.BATTERY_CHECK).assertIsNotDisplayed()

        // 本題: 溢れた先を受ける器（NovelReaderAlertDialog が巻く verticalScroll）があるので送り出せる。
        // 器が外れると performScrollTo が「スクロール可能な親が無い」で落ちる＝退行がここで赤くなる。
        composeTestRule.onNodeWithText(Fx.BATTERY_CHECK).performScrollTo().assertIsDisplayed()

        // 本文を包んだことでボタン行が犠牲になっていないこと。M3 は本文にだけ weight(fill=false) を張る＝
        // 窓が足りないとき削られるのは本文だけ、という前提の裏取り（この前提が崩れると退避先を誤る）。
        composeTestRule.onNodeWithText(Fx.BATTERY_CONFIRM).assertIsDisplayed()
        composeTestRule.onNodeWithText(Fx.BATTERY_DISMISS).assertIsDisplayed()
    }

    /**
     * BookshelfScreen.kt の `if (showBatteryOptDialog)` ブロックの写し。
     * `BookshelfDialogScreenshotTest` の同名 private ヘルパと同じ組み方（文言は [Fx] 経由＝本番との一致は
     * `BookshelfDialogTextFidelityTest` が機械照合するので、写しが黙って古くなることはない）。
     */
    @Composable
    private fun BatteryOptDialog() {
        NovelReaderAlertDialog(
            onDismissRequest = {},
            title = { Text(Fx.BATTERY_TITLE) },
            text = {
                Column {
                    Text(Fx.BATTERY_BODY)
                    Spacer(Modifier.height(Spacing.S8))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 既定 OFF＝本番の初期状態（doNotShowAgain の初期値）と同じ。
                        Checkbox(checked = false, onCheckedChange = {})
                        Text(Fx.BATTERY_CHECK, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {}) { Text(Fx.BATTERY_CONFIRM) } },
            dismissButton = { TextButton(onClick = {}) { Text(Fx.BATTERY_DISMISS) } },
        )
    }
}
