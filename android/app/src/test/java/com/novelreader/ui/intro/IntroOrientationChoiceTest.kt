package com.novelreader.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 向きの選択カード（正本モック `tutorial-onboarding-K.html` §3 の組A 2/3・§8）の**書き込みの作法**を縛る。
 *
 * ## なぜ「押した／進んだ」と「見ただけ」を分けて固定するか
 * このカードは設定画面ではなく**初回に 1 度だけ割り込む案内**なので、`reading_vertical` を
 * いつ書くかで意味がまるごと変わる:
 *  - **描いた瞬間に書く**と、［あとで］で降りた人や、たまたま扉を開いただけの人の端末設定を
 *    こちらが勝手に変えたことになる（このカードが持ってよい権能を超える）。
 *  - **一度も書かない**と、チップに触れず ［つづける］ を押した人の画面（縦書きが選ばれて見えていた）と
 *    保存値（キー不在＝横書き）が食い違い、本文が「選んだのと違う向き」で開く。
 * どちらも**画面には痕跡が残らない**——保存値を覗く以外に検出手段が無いので、機械で縛る。
 *
 * ## 既定値そのものは動かさない
 * [PrefKeys.READING_VERTICAL] の既定は false（横書き）のまま。既定を true へ倒すと
 * **まだ一度も向きを触っていない既存ユーザーの本文が次回起動で縦書きに変わる**（キー不在＝既定値が読まれる）。
 * カードの初期選択が縦書きなのは [INTRO_ORIENTATION_DEFAULT_VERTICAL] であって、prefs の既定ではない。
 */
class IntroOrientationChoiceTest {

    private val choiceIndex = IntroDeck.cards.indexOfFirst { it.choice != null }

    /** 扉から選択カードまで進めた状態を作る（数値の直書きをしない＝並べ替えに追従する）。 */
    private fun openAtChoice(store: FakeIntroFlagStore): IntroController {
        val controller = IntroController(store)
        controller.requestAuto(IntroGroup.ABOUT)
        while (controller.flow?.index != choiceIndex) {
            val before = controller.flow?.index
            controller.next()
            check(controller.flow?.index != before) { "選択カードへ到達できない（列の並びが変わった？）" }
        }
        return controller
    }

    @Test
    fun `カードを出しただけでは prefs を書かない（見せただけで設定を変えない）`() {
        val store = FakeIntroFlagStore()
        openAtChoice(store)
        assertEquals(0, store.orientationWrites)
        assertNull("描画しただけでキーが生えた＝端末設定を勝手に変えている", store.orientationVertical)
    }

    @Test
    fun `あとで で降りた人の端末は書き換えない`() {
        val store = FakeIntroFlagStore()
        val controller = openAtChoice(store)
        controller.dismiss() // ［あとで］／スクリム外タップ／先頭 Back はすべてここへ来る
        assertEquals(0, store.orientationWrites)
        assertNull(store.orientationVertical)
    }

    @Test
    fun `チップ押下はその場で確定する（画面の見えと保存値を 1 操作もずらさない）`() {
        val store = FakeIntroFlagStore()
        val controller = openAtChoice(store)
        controller.selectOrientation(false)
        assertFalse(controller.orientationVertical)
        assertEquals(false, store.orientationVertical)
        assertEquals(1, store.orientationWrites)
    }

    @Test
    fun `チップに触れずに進んでも、見えていた初期選択が確定する`() {
        val store = FakeIntroFlagStore()
        val controller = openAtChoice(store)
        assertTrue("初期選択は縦書き＝看板と初見を一致させるのがこのカードの用", controller.orientationVertical)
        controller.next()
        assertEquals(
            "［つづける］ を押した人は「見えていたもの」が選ばれる（画面と保存値が食い違わない）",
            true,
            store.orientationVertical,
        )
        assertEquals(1, store.orientationWrites)
    }

    @Test
    fun `押してから降りた選択は残る（明示的に選んだ人の意思は消さない）`() {
        val store = FakeIntroFlagStore()
        val controller = openAtChoice(store)
        controller.selectOrientation(false)
        controller.dismiss()
        assertEquals(false, store.orientationVertical)
    }

    @Test
    fun `既に保存値がある端末では、その値が初期選択になる（選び直しを強いない）`() {
        // 横書きを選んだ人へ「縦書きが選ばれています」と見せると、選び直しを強いることになる。
        // 既定値 false のまま getBoolean で読むと〈横書きを選んだ〉と〈まだ選んでいない〉が
        // 同じ false に潰れるので、store 側は Boolean? で返している。
        val store = FakeIntroFlagStore().apply { preSelectOrientation(false) }
        assertFalse(IntroController(store).orientationVertical)
        assertEquals("読んだだけでは書かない", 0, store.orientationWrites)
    }

    @Test
    fun `選択カードでない枚から進んでも書かない（列の他の枚に副作用が漏れていない）`() {
        val store = FakeIntroFlagStore()
        val controller = IntroController(store)
        controller.requestAuto(IntroGroup.READING)
        controller.next()
        controller.next()
        assertEquals(0, store.orientationWrites)
    }
}
