package com.novelreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * なろう紐付けシートの中身（[NcodeLinkSheetContent]）が可視域を超えたときの到達性の回帰。
 *
 * なぜ絵（golden）でなくここで張るか: 押し出しの是正はスクロール容器の付与＝**スクロール位置0の絵は
 * 変わらない**（頭から同じ高さぶんが見えるだけ）。したがって
 * [com.novelreader.ui.screenshot.NcodeLinkSheetScreenshotTest] の golden も
 * `tools/check_golden_rule_loss.py` の罫線本数も、器が外れた退行を検出できない。「送る手段が残っているか」は
 * 到達性そのものを聞くしかない。
 *
 * 塞いだ穴（2026-08-17）: 中身の Column に縦スクロールが無く、fontScale 2.0＋長書名で補足文が膨らむと
 * 手動 N コード欄・「紐付け」・「再試行」が画面外へ落ち、シートを閉じる以外にできることが無くなっていた
 *（候補リストだけは内側 LazyColumn で送れる）。
 *
 * 画面を h200dp と小さく取るのは、Robolectric 既定の GraphicsMode(LEGACY) が文字幅を字数近似で返す
 *（docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md）ため、実機と同じ
 * h640dp では本文が縮んで溢れが再現しないから。溢れさせる条件を器の側（窓の高さ）で作る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h200dp-xhdpi")
class NcodeLinkSheetReachabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `内容が可視域を超えても紐付けと再試行へ到達できる`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 2.0f,
                ),
            ) {
                // 本番の ModalBottomSheet の枠は Robolectric では別ウィンドウ＋部分展開で下端が落ちるため、
                // 中身だけを画面いっぱいの器へ置く（screenshot 側と同じ扱い）。
                Box(modifier = Modifier.fillMaxSize()) {
                    NcodeLinkSheetContent(
                        bookTitle = "追放された万能付与術師は辺境でスローライフを送りたい",
                        // 通信失敗＝「再試行」も同じ Column の下側に居る状態を撮る。
                        searchState = NcodeSearchUiState.Error("通信に失敗しました。電波の良い場所で再試行してください。"),
                        colors = ReadingTheme.LIGHT.colors,
                        inputText = "追放された万能付与術師は辺境でスローライフを送りたい",
                        onInputTextChange = {},
                        manualNcode = "N1234AB",
                        onManualNcodeChange = {},
                        onSearch = {},
                        onRetry = {},
                        onConfirm = {},
                    )
                }
            }
        }

        // 前提の確認: この条件では末尾のボタンは初期表示で画面の外に居る（＝溢れが起きている）。
        composeTestRule.onNodeWithText("紐付け").assertIsNotDisplayed()
        // 本題: 溢れた先を受ける器があるので送り出せる。器が外れると performScrollTo が
        // 「スクロール可能な親が無い」で落ちる＝退行がここで赤くなる。
        composeTestRule.onNodeWithText("紐付け").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("再試行").performScrollTo().assertIsDisplayed()
    }
}
