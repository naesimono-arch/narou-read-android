package com.novelreader.ui.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DiagnosticsExportContent]（診断の記録・書き出し面の stateless 描画層）の状態分岐と結線テスト（ADR 0009）。
 *
 * 守る対象は正本モック `skins/diagnostics-export-K.html` 案B が持つ**2状態**:
 *   ・記録あり＝件数と容量が読める・主操作が押せる
 *   ・まだ無し＝両値が「まだ無し」・主操作が非活性・注記が「何も無いのは正常です」へ入れ替わる
 * 空のときにボタンを沈めるのは意匠でなく契約（押せると「押したのに何も起きない」になる）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsExportContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        state: DiagnosticsExportState,
        resultMessage: String? = null,
        onExport: () -> Unit = {},
        onUp: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                DiagnosticsExportContent(
                    state = state,
                    resultMessage = resultMessage,
                    onExport = onExport,
                    onUp = onUp,
                )
            }
        }
    }

    @Test
    fun `記録ありは件数と容量を読める形で出し主操作が押せる`() {
        var exported = false
        setContent(
            state = DiagnosticsExportState(eventCount = 3, jankBytes = 48L * 1024L),
            onExport = { exported = true },
        )
        composeTestRule.onNodeWithText("3件").assertIsDisplayed()
        composeTestRule.onNodeWithText("48KB").assertIsDisplayed()
        composeTestRule.onNodeWithText("ファイルへ書き出す").assertIsEnabled().performClick()
        assertTrue("主操作は onExport へ結線されている", exported)
    }

    @Test
    fun `まだ無しは両方の値が空表示で主操作が非活性`() {
        setContent(DiagnosticsExportState(eventCount = 0, jankBytes = 0L))
        // 2行とも同じ文言＝ノードは2つ。片方だけ空表示になる退行（値の取り違え）をここで止める。
        composeTestRule.onAllNodesWithText("まだ無し").assertCountEquals(2)
        // 空で押せると「押したのに何も起きない」＝行き止まりになる。押下の不発でなく
        // **意味木で無効**であることを要求する（TalkBack にも「無効」と読ませる側）。
        composeTestRule.onNodeWithText("ファイルへ書き出す").assertIsNotEnabled()
    }

    @Test
    fun `空のときは「なぜ空か」の注記へ入れ替わる`() {
        // 記録は異常時にしか作られない＝空を「壊れている」と誤読させないための注記（モックの空状態）。
        setContent(DiagnosticsExportState(eventCount = 0, jankBytes = 0L))
        // 折り目の直上に居るため素の assertIsDisplayed でも今は緑になるが、文言が1行伸びただけで
        // 折り目の下へ落ちて赤くなる＝面の契約と無関係な理由で割れる。器の要求（到達性）へ揃える。
        composeTestRule
            .onNodeWithText("記録は、アプリが落ちたときと画面がカクついたときにだけ作られます。何も無いのは正常です。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `記録があるときは保存先の案内を出す`() {
        setContent(DiagnosticsExportState(eventCount = 1, jankBytes = 0L))
        // 同上（折り目の直上＝素の可視判定は面の契約でなくテスト窓の高さを測ってしまう）。
        composeTestRule
            .onNodeWithText("保存する場所はあなたが選びます。1つのテキストファイルにまとめます。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `←は onUp へ結線されている`() {
        // ← の着地（設定タブへ1段上がる）は nav 配線側の契約＝ここは「押したら onUp が来る」だけを見る
        //（着地そのものは DiagnosticsExportUpNavigationTest が固定する）。
        var upped = false
        setContent(DiagnosticsExportState(eventCount = 0, jankBytes = 0L), onUp = { upped = true })
        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        assertTrue(upped)
    }

    @Test
    fun `書き出しの結末は主操作のすぐ下に出る（失敗が黙って消えない）`() {
        setContent(
            state = DiagnosticsExportState(eventCount = 1, jankBytes = 0L),
            resultMessage = MESSAGE_EXPORT_FAILED,
        )
        // performScrollTo を通すのは緩めるためではなく**強めるため**＝この面はスクロールする器なので、
        // 素の assertIsDisplayed は「Robolectric の既定ウィンドウで折り目がどこに来たか」を測るだけになり、
        // 面の契約を1つも見ていない（実機 412×915 では正本モックの案B が丸ごと収まる＝スクロール自体が起きない）。
        // performScrollTo は**スクロール可能な親が居ること**まで要求する＝器が外れると
        // 「スクロール可能な親が無い」で落ちる（BookshelfDialogReachabilityTest と同流儀＝
        // docs/knowledge/sheet-without-verticalscroll-hides-actions.md の欠陥クラスをここでも締める）。
        composeTestRule.onNodeWithText(MESSAGE_EXPORT_FAILED).performScrollTo().assertIsDisplayed()

        // 位置の契約: 結末は〈主操作の下・「書き出される内容」節より上〉＝押したボタンの近傍に留まる。
        // なぜ順序まで縛るか: この1行を足した理由は「SAF から戻った人の目に入ること」なので、
        // 面の末尾へ移されると**描かれてはいるが誰も見ない**形になり、目的を果たさないまま緑になる。
        // 存在確認だけではこの退行を取り逃す（元の assert が見ていなかったのがまさにここ）。
        assertTrue(
            "結末が主操作より上に出ている（押した結果は押した場所の下に出る）",
            topOf(MESSAGE_EXPORT_FAILED) >= bottomOf("ファイルへ書き出す"),
        )
        assertTrue(
            "結末が「書き出される内容」節より下へ流れている＝主操作から離れて見落とされる",
            topOf(MESSAGE_EXPORT_FAILED) < topOf("書き出される内容"),
        )
    }

    /** 文字ノードの上端 y（root 座標）。clip されない [SemanticsNode.positionInRoot] で読む＝折り目の外でも測れる。 */
    private fun topOf(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    /** 文字ノードの下端 y（root 座標）。 */
    private fun bottomOf(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().let {
            it.positionInRoot.y + it.size.height
        }

    @Test
    fun `書き出しの器は2種の記録を見出しつきで連ねる`() {
        val text = diagnosticsExportText(events = "EVENT-BODY", jank = "JANK-BODY")
        assertTrue(text.contains("EVENT-BODY"))
        assertTrue(text.contains("JANK-BODY"))
        assertTrue("不具合の記録が先（読む側が最初に見たいのは異常の方）", text.indexOf("EVENT-BODY") < text.indexOf("JANK-BODY"))
    }

    @Test
    fun `片方が空でも「なし」と明示する（連結失敗と区別できる）`() {
        val text = diagnosticsExportText(events = "", jank = "JANK-BODY")
        assertTrue("空は無言の空行でなく (なし) と書く", text.contains("(なし)"))
        assertEquals("空の側だけが (なし) になる", 1, Regex(Regex.escape("(なし)")).findAll(text).count())
    }
}
