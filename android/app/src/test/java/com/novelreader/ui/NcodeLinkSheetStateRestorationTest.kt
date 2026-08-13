package com.novelreader.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * なろう紐付けシート（[NcodeLinkSheet]）の入力欄が構成変更・プロセス再生成をまたいで残ることの回帰。
 *
 * なぜこのテストが要るか（監査 2026-08-06 C3）: 開閉フラグ（NativeReadingScreen の showLinkSheet）だけが
 * Saveable 化されていたため、回転すると「シートは開いたまま戻るのに入力欄だけ空」になり、検索語と
 * N コード 8 文字を打ち直させていた。復元されるのが半分だけ＝救済が中途半端な状態を機械で塞ぐ。
 *
 * [StateRestorationTester] は setContent した内容の保存/復元を実際に走らせる（＝rememberSaveable を
 * 通らない状態はここで消える）ので、「回転で消えるか」を実機を使わず観測できる。
 */
@RunWith(RobolectricTestRunner::class)
// 実端末相当の画面（他の Robolectric UI テストと同一 w360dp-h640dp）。シートは下端から出るため、
// 既定の短い画面だと入力欄が画面外に落ちて可視判定が不安定になる。
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NcodeLinkSheetStateRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    @Test
    fun `検索語と手動Nコードは構成変更をまたいで残る`() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            NcodeLinkSheet(
                // 初期値が検索欄へ入る。復元後もこの初期値へ巻き戻らないことを見るため、
                // 下で別の語に打ち替える。
                bookTitle = "初期タイトル",
                // 候補ゼロの Success＝通信も候補リストも絡めず入力欄だけを観測対象にする。
                searchState = NcodeSearchUiState.Success(DiscoveryResult(allcount = 0, novels = emptyList())),
                colors = colors,
                onSearch = {},
                onRetry = {},
                onConfirm = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("初期タイトル").performTextReplacement("打ち替えた検索語")
        // 手動 N コード欄はプレースホルダ（N1234AB）を手掛かりに掴む＝空欄の初期状態を指す。
        composeTestRule.onNodeWithText("N1234AB").performTextReplacement("N9999ZZ")

        // なぜ assertIsDisplayed でなく assertExists か: ModalBottomSheet は別ウィンドウ＋部分展開で
        // 下部（手動 N コード欄）が画面外に残り、Robolectric では可視判定が落ちる（実測で赤・
        // ReadingSettingsSheet テストが同じ理由で枠を剥がしている）。ここで見たいのは可視性ではなく
        // 「入力状態が生き残るか」なので、ノードの存在で足りる。
        composeTestRule.onNodeWithText("打ち替えた検索語").assertExists()
        composeTestRule.onNodeWithText("N9999ZZ").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        // 打ち直しになっていた退行の本体: ここが空や初期値へ戻ったら赤。
        composeTestRule.onNodeWithText("打ち替えた検索語").assertExists()
        composeTestRule.onNodeWithText("N9999ZZ").assertExists()
        // remember 時代の症状そのもの（検索欄が bookTitle へ巻き戻り／N コード欄が空＝プレースホルダ復活）を
        // 名指しで否定しておく。存在確認だけだと「両方出ている」異常を見逃すため。
        composeTestRule.onNodeWithText("初期タイトル").assertDoesNotExist()
        composeTestRule.onNodeWithText("N1234AB").assertDoesNotExist()
    }
}
