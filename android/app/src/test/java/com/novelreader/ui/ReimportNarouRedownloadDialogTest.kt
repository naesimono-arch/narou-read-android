package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 再取込ダイアログ③'＝[ReimportNarouRedownloadDialog] の番人。
 *
 * ## 何を守るか（2026-09-03 に直した実バグ）
 * なろう縦書きPDF取込の本は取込元 PDF がアプリ cache にしか無く、uninstall→Auto Backup の復元後は
 * 権限も cache も戻らない。それでも分類は③へ落として「PDFのある場所から探す／自分で選ぶ」を出しており、
 * **ユーザーの手元に一度も存在しないファイルを探せと言う実行不能な提案**になっていた。
 * ここで縛るのは (1) 実行できる唯一の導線（なろうで作り直す）が出ること (2) 空振りが確定している
 * 2操作が**出ないこと** (3) 確定操作が取り込み画面への遷移を1回だけ呼ぶこと。
 * (2) を明示的に断言するのは、将来「選択肢は多い方が親切」と見えて戻されうる変更だからで、
 * 戻すなら「なぜ空振りしないのか」を先に説明する必要がある、という関門をテストで立てておく。
 *
 * ## なぜ本番の Composable を直に描くか
 * 写しを検査するテストは本番が変わっても緑のまま通る＝番人にならない（[ReimportScanDialogStackTest]
 * の KDoc と同じ理由）。ダイアログを internal へ切り出してあるのはそのため。
 *
 * 版面の断言は持たない（2ボタン＝①①'④と同じ既定の並びで、[ReimportScanDialogStackTest] が守る
 * 3段スタックのような固有の不変条件が無い）ため GraphicsMode は既定のままでよい。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ReimportNarouRedownloadDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(onRedownload: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                ReimportNarouRedownloadDialog(
                    bookTitle = "悪役令嬢に転生したはずが気づけば魔王の義妹になっていました",
                    onRedownload = onRedownload,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun `実行できる唯一の導線を出す`() {
        setContent()
        composeTestRule.onNodeWithText("なろうで作り直す").assertIsDisplayed()
        composeTestRule.onNodeWithText("やめる").assertIsDisplayed()
        // 題名節と操作節は別 Text（長題でも操作の問いが切れない＝ReimportDialogTitle の契約）。
        composeTestRule.onNodeWithText("なろうで作り直して再取込しますか？").assertIsDisplayed()
    }

    @Test
    fun `空振りが確定している2操作は出さない`() {
        setContent()
        composeTestRule.onNodeWithText("場所から探す").assertDoesNotExist()
        composeTestRule.onNodeWithText("自分で選ぶ").assertDoesNotExist()
        composeTestRule.onNodeWithText("PDFを選ぶ").assertDoesNotExist()
    }

    @Test
    fun `確定操作は取り込み画面への遷移を一度だけ呼ぶ`() {
        var redownloads = 0
        setContent(onRedownload = { redownloads++ })
        composeTestRule.onNodeWithText("なろうで作り直す").performClick()
        assertEquals(1, redownloads)
    }
}
