package com.novelreader.ui.intro

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 描画層（stateless）の分岐を固定する。**文言は正本 §4 の写経**なので、ここで文字列を書き換えて
 * テストを通すのは禁止——正本モックを直してから来ること。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntroOverlayContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(flow: IntroFlow, onNext: () -> Unit = {}, onBack: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                IntroOverlayContent(flow = flow, onNext = onNext, onBack = onBack, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun `組A の 1 枚目は 扉そのもの（あとで／つづける）`() {
        var next = 0
        var dismiss = 0
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = false), onNext = { next++ }, onDismiss = { dismiss++ })
        rule.onNodeWithText("はじめに").assertIsDisplayed()
        rule.onNodeWithText("あとで").performClick()
        rule.onNodeWithText("つづける").performClick()
        assertEquals(1, dismiss)
        assertEquals(1, next)
    }

    @Test
    fun `組A の 2 枚目は もどる と 再訪導線の 1 行を持つ`() {
        var back = 0
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 1), onBack = { back++ })
        rule.onNodeWithText("読みかたは、2 通り").assertIsDisplayed()
        rule.onNodeWithText("読みかたの説明は、読みはじめてから ［設定］＞［操作の説明］ で。").assertIsDisplayed()
        rule.onNodeWithText("もどる").performClick()
        assertEquals(1, back)
        rule.onNodeWithText("はじめる").assertIsDisplayed()
    }

    @Test
    fun `組B は本文の割り当てだけを言う（一般的なジェスチャの挙動は書かない）`() {
        show(IntroFlow(IntroGroup.READING, walkthrough = false))
        rule.onNodeWithText("横書きで読む").assertIsDisplayed()
        rule.onNodeWithText("メニューを出す").assertIsDisplayed()
        rule.onNodeWithText("本文のどこでもタップ。もう一度で消えます。").assertIsDisplayed()
        rule.onNodeWithText("つぎへ").assertIsDisplayed()
    }

    @Test
    fun `組C は 1 枚で終わるので あとで を置かず とじる だけ`() {
        show(IntroFlow(IntroGroup.SEARCH, walkthrough = false))
        rule.onNodeWithText("さがして、本棚に入れる").assertIsDisplayed()
        // 3 項目＋再訪導線を積む最長のカード＝既定画面では下端が折り返す。スクロールで**必ず到達できる**
        // ことまで見る（非スクロール面だと、ここで到達手段そのものが消える）。
        rule.onNodeWithText("とじる").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("あとで").assertDoesNotExistCompat()
        rule.onNodeWithText("もどる").assertDoesNotExistCompat()
    }

    @Test
    fun `通しでは組の最後でも つぎへ が出る（列の最後だけが終端）`() {
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 1))
        rule.onNodeWithText("つぎへ").assertIsDisplayed()
        rule.onNodeWithText("はじめる").assertDoesNotExistCompat()
    }
}

/** `assertDoesNotExist` は例外送出型のため、意図を読めるようにした薄い別名。 */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
