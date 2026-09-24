package com.novelreader.ui.intro

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.novelreader.ui.NarouExternalPageNotice
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

    private fun show(
        flow: IntroFlow,
        onNext: () -> Unit = {},
        onBack: () -> Unit = {},
        onDismiss: () -> Unit = {},
        orientationVertical: Boolean = true,
        onOrientationChange: (Boolean) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                IntroOverlayContent(
                    flow = flow,
                    onNext = onNext,
                    onBack = onBack,
                    onDismiss = onDismiss,
                    orientationVertical = orientationVertical,
                    onOrientationChange = onOrientationChange,
                )
            }
        }
    }

    /** 向きの選択カードの位置（数値で書かない＝並べ替えでテストが別のカードを見に行かないため）。 */
    private val choiceIndex get() = IntroDeck.cards.indexOfFirst { it.choice != null }

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
    fun `組A の最後の枚は もどる と 再訪導線の 1 行を持つ`() {
        var back = 0
        // ⚠️ 位置（index = 1）で引かないこと。このテストが見たいのは「組A の**最後**＝再訪導線と
        // ［はじめる］ を持つ枚」であって 2 枚目という位置ではない。2026-09-03 に向きの選択カードを
        // 2 枚目へ差したとき、位置で引いていたせいで別のカードを検分して赤くなった。
        show(
            IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = IntroDeck.lastIndexOf(IntroGroup.ABOUT)),
            onBack = { back++ },
        )
        rule.onNodeWithText("読みかたは、2 通り").assertIsDisplayed()
        // ⚠️ 2026-09-07 に performScrollTo を足した（文言は 1 字も変えていない）。カードの構造が
        // 〈全体が 1 つのスクロール器〉から〈**本文域だけ**がスクロールし、点とボタンは下端に固定〉へ
        // 変わり、本文域に割かれる高さがボタン列ぶん減ったため、Robolectric の既定画面（小さい）では
        // 再訪導線が折り返し位置より下に来る。**見たいのは「文があること」ではなく「到達できること」**。
        rule.onNodeWithText("読みかたの説明は、読みはじめてから ［設定］＞［操作の説明］ で。")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("もどる").performClick()
        assertEquals(1, back)
        rule.onNodeWithText("はじめる").assertIsDisplayed()
    }

    @Test
    fun `向きの選択カードは 2 択チップを出し、押した側を通知する`() {
        val picked = mutableListOf<Boolean>()
        show(
            IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = choiceIndex),
            orientationVertical = true,
            onOrientationChange = { picked += it },
        )
        rule.onNodeWithText("どちらで読みますか").assertIsDisplayed()
        rule.onNodeWithText("あとから ［表示設定］＞［本文の向き］ で変えられます。")
            .performScrollTo().assertIsDisplayed() // 上と同じ理由（本文域スクロール化）
        rule.onNodeWithText("横書き").performClick()
        rule.onNodeWithText("縦書き").performClick()
        // 描画層は state を持たない＝押されたことを**そのまま**上へ渡すだけ（確定は Controller の責務）。
        assertEquals(listOf(false, true), picked)
    }

    @Test
    fun `チップは単一選択として読み上げる（両方 on にできると誤解させない）`() {
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = choiceIndex), orientationVertical = true)
        // 見えでは「チップが 2 つ並ぶ」以上のことが伝わらない＝選択状態は semantics 側でしか守れない。
        rule.onNodeWithText("縦書き").assertIsSelected()
        rule.onNodeWithText("横書き").assertIsNotSelected()
    }

    @Test
    fun `選択カード以外にチップは出ない（列に選択が漏れ出していない）`() {
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = IntroDeck.firstIndexOf(IntroGroup.ABOUT)))
        rule.onNodeWithText("縦書き").assertDoesNotExistCompat()
        rule.onNodeWithText("横書き").assertDoesNotExistCompat()
    }

    @Test
    fun `組B は本文の割り当てだけを言う（一般的なジェスチャの挙動は書かない）`() {
        show(IntroFlow(IntroGroup.READING, walkthrough = false))
        rule.onNodeWithText("横書きで読む").assertIsDisplayed()
        // 1 項目目〈横書きにする〉は 2026-09-07 に足した（縦書きが既定なので、このカードが
        // 「いまの操作の説明」と誤読されるのを 1 行目で塞ぐ＝IntroDeck.kt の理由コメントと対）。
        rule.onNodeWithText("横書きにする").assertIsDisplayed()
        rule.onNodeWithText("メニューを出す").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("本文のどこでもタップ。もう一度で消えます。").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("つぎへ").assertIsDisplayed()
    }

    @Test
    fun `組C は 1 枚で終わるので あとで を置かず とじる だけ`() {
        show(IntroFlow(IntroGroup.SEARCH, walkthrough = false))
        rule.onNodeWithText("さがして、本棚に入れる").assertIsDisplayed()
        // ⚠️ 2026-09-07 に performScrollTo を**外した**（緩めたのではなく強めた）。ボタンは
        // スクロール器の外＝カード下端に固定されたので、**スクロールせずに見えていなければ赤**にする。
        // 旧構造（カード全体が 1 つのスクロール器）では到達に必ずスクロールが要り、
        // 「スクロールすれば届く」までしか縛れなかった。ここを performScrollTo に戻すと
        // 「Scroll SemanticsAction を持つ親が無い」で落ちる＝構造の退行がそのまま検出される。
        rule.onNodeWithText("とじる").assertIsDisplayed()
        rule.onNodeWithText("あとで").assertDoesNotExistCompat()
        rule.onNodeWithText("もどる").assertDoesNotExistCompat()
    }

    @Test
    fun `組D も 1 枚で終わるので あとで を置かず とじる だけ`() {
        show(IntroFlow(IntroGroup.IMPORT, walkthrough = false))
        rule.onNodeWithText("つくって、本棚に入れる").assertIsDisplayed()
        rule.onNodeWithText("ここはなろうのページ").performScrollTo().assertIsDisplayed()
        // 境界の告知（ADR 0042）はこの 1 項目が受ける＝取り込みルートに NarouExternalPageNoticeHost は
        // **置かない**（両方入れると初回だけダイアログとカードが連続する＝2026-09-04 裁定）。
        // その代わり、この項目の本文が NarouExternalPageNotice.BODY の**第 1 文と 1 字同一**であることを
        // ここで機械的に縛る。左辺を定数から導くので、**どちらを直しても**このテストが落ちる
        // ——同じ事実を 2 通りに言って片方が黙って腐るのを防ぐ（IntroDeck.kt 側のコメントと対）。
        rule.onNodeWithText(NarouExternalPageNotice.BODY.substringBefore("。") + "。")
            .performScrollTo().assertIsDisplayed()
        // 組C と同じ＝ボタンはスクロール器の外にいるので、スクロールなしで見えていること（上の理由）。
        rule.onNodeWithText("とじる").assertIsDisplayed()
        rule.onNodeWithText("あとで").assertDoesNotExistCompat()
        rule.onNodeWithText("もどる").assertDoesNotExistCompat()
    }

    @Test
    fun `通しでは組の最後でも つぎへ が出る（列の最後だけが終端）`() {
        // 同上＝「組の最後」という特徴で引く（通しではその枚が終端でなくなる、が見たいこと）。
        show(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = IntroDeck.lastIndexOf(IntroGroup.ABOUT)))
        rule.onNodeWithText("つぎへ").assertIsDisplayed()
        rule.onNodeWithText("はじめる").assertDoesNotExistCompat()
    }
}

/** `assertDoesNotExist` は例外送出型のため、意図を読めるようにした薄い別名。 */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
