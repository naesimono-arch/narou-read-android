package com.novelreader.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 3 系統の**出現条件**と**消費条件**を固定する（正本モック tutorial-onboarding-K.html §8「フラグ」）。
 *
 * 消費は 3 本とも同じ 1 つの規則＝「その組の最後のカードまで到達したうえで、閉じた／次の組へ進んだ時点」。
 * 途中で閉じた回を焼いてしまうのが最も取り返しのつかない失敗（T4 の轍＝見た保証が無いのに機会を焼く）で、
 * ここが緑でなくなったら実装ではなく規則の方を疑うこと。
 */
class IntroControllerTest {

    private fun controller(store: FakeIntroFlagStore = FakeIntroFlagStore()) =
        store to IntroController(store)

    /**
     * その組の**最後のカードまで**進める。
     *
     * ⚠️ `next()` を枚数ぶん数えて打つ書き方をしないこと。枚数は列の都合で変わるのに、数えて打つ形は
     * 列に 1 枚差された瞬間**「1 枚手前で止まった」へ静かに化ける**——そして消費の規則自体は無傷なのに、
     * 遠くのアサーション（`assertNull(flow)` や「あとから改めて出る」）が赤くなるので、
     * 実装が壊れたように見える。2026-09-03 に向きの選択カードを組A へ差したとき、
     * この書き方のテストが**5 本まとめて赤くなった**（実装は正しかった）。
     * 消費の規則は枚数と無関係＝「その組の最後に居たか」なので、位置も列から引く。
     */
    private fun IntroController.advanceToEndOf(group: IntroGroup) {
        val goal = IntroDeck.lastIndexOf(group)
        while (true) {
            val at = flow?.index ?: error("$group の最後へ着く前に回が終わった（列の並びが変わった？）")
            if (at == goal) return
            next()
            check(flow?.index != at) { "$group で進めなくなった（列の並びが変わった？）" }
        }
    }

    // ── 出現条件 ────────────────────────────────────────────────

    @Test
    fun `未消費なら出す・消費済みなら出さない（3 系統とも同じ）`() {
        IntroGroup.entries.forEach { group ->
            val (_, fresh) = controller()
            fresh.requestAuto(group)
            assertNotNull("$group は未消費なら出る", fresh.flow)
            assertEquals(group, fresh.flow?.group)

            val burned = FakeIntroFlagStore().apply { preShow(group) }
            val done = IntroController(burned)
            done.requestAuto(group)
            assertNull("$group は消費済みなら出さない", done.flow)
        }
    }

    @Test
    fun `回は必ずその組の先頭カードから始まる（途中再開の状態を持たない）`() {
        val (_, c) = controller()
        c.requestAuto(IntroGroup.READING)
        assertEquals(IntroDeck.firstIndexOf(IntroGroup.READING), c.flow?.index)
        c.next()
        c.dismiss()
        // 2 度目（フラグを消していない別インスタンス相当）でも先頭から。
        val (_, again) = controller()
        again.requestAuto(IntroGroup.READING)
        assertEquals(IntroDeck.firstIndexOf(IntroGroup.READING), again.flow?.index)
    }

    @Test
    fun `1 起動 1 組の間引きはしない＝条件を満たした組はその場で出す（2026-08-21 裁定）`() {
        val (store, c) = controller()
        // 組A を最後まで読んで閉じる（＝この起動で 1 組すでに出した）。
        c.requestAuto(IntroGroup.ABOUT)
        c.advanceToEndOf(IntroGroup.ABOUT)
        c.next() // 組の最後で主ボタン＝閉じる
        assertNull(c.flow)
        assertTrue(store.isShown(IntroGroup.ABOUT))

        // 同じ起動のまま検索画面へ着いた。ここで黙ると「実感と説明が同時に来る」設計の芯が崩れる。
        c.requestAuto(IntroGroup.SEARCH)
        assertEquals(IntroGroup.SEARCH, c.flow?.group)

        // 本文へ着いた回も同様（3 組が同じ起動で出てよい）。
        c.dismiss()
        c.requestAuto(IntroGroup.READING)
        assertEquals(IntroGroup.READING, c.flow?.group)
    }

    // ── 消費条件 ────────────────────────────────────────────────

    @Test
    fun `組の最後まで到達して閉じたときだけ焼く（途中で閉じた回は焼かない）`() {
        // 組B の 3 枚目（＝組の最後ではない）で閉じた。
        val (store, c) = controller()
        c.requestAuto(IntroGroup.READING)
        c.dismiss()
        assertFalse("途中で閉じた回を焼いてはならない", store.isShown(IntroGroup.READING))

        // 次の機会にまた出る。
        c.requestAuto(IntroGroup.READING)
        assertNotNull(c.flow)
        c.next() // 4 枚目＝組の最後
        c.dismiss() // スクリム外タップ／システム Back でも終端ボタンでも同じ扱い
        assertTrue("最後まで到達して閉じたら焼く", store.isShown(IntroGroup.READING))
        assertEquals(listOf(IntroGroup.READING), store.marked)
    }

    @Test
    fun `どの組も「最後まで到達して閉じた」時点で焼ける（枚数に依存しない規則）`() {
        // 上の各テストは「その組を読み切る」までを手順で書くので、列の枚数が変わると手順の方が先に壊れる。
        // ここだけは**規則そのもの**——〈組の最後に居たか〉——を列から引いて全組で確かめ、
        // 「カードを差したら消費が壊れたのか、テストの手順が古いだけか」を次回 1 本で切り分けられるようにする。
        IntroGroup.entries.forEach { group ->
            val store = FakeIntroFlagStore()
            val c = IntroController(store)
            c.requestAuto(group)
            val goal = IntroDeck.lastIndexOf(group)
            while (c.flow?.index != goal) {
                val at = c.flow?.index ?: error("$group: 組の最後へ着く前に回が終わった")
                assertFalse("$group: 組の最後へ着く前に焼いた（T4 の轍）", store.isShown(group))
                c.next()
                // 進まなくなったら**固まらせずに落とす**——ゲートが赤くなるのは直せるが、
                // 固まるとどのテストで止まったかも分からない。
                check(c.flow?.index != at) { "$group: 進めなくなった（列の並びが変わった？）" }
            }
            c.dismiss()
            assertTrue("$group: 組の最後まで到達して閉じたら焼く", store.isShown(group))
        }
    }

    @Test
    fun `1 枚だけの組は開いた回を閉じた時点で焼ける`() {
        val (store, c) = controller()
        c.requestAuto(IntroGroup.SEARCH)
        c.dismiss()
        assertTrue(store.isShown(IntroGroup.SEARCH))
    }

    @Test
    fun `もどるで組の最後より前へ退がってから閉じても、到達済みの組は焼いたまま`() {
        val (store, c) = controller()
        c.openWalkthrough()
        c.advanceToEndOf(IntroGroup.ABOUT) // 組A の最後へ（枚数は列から引く）
        c.next() // 組A の最後から組B へ＝ここで組A を焼く
        assertTrue(store.isShown(IntroGroup.ABOUT))
        c.back() // 組A の最後へ退がる
        c.dismiss()
        assertTrue("読み進めた組はその場で消費済み（あとから自動で割り込まない）", store.isShown(IntroGroup.ABOUT))
        assertFalse("到達していない組は焼かない", store.isShown(IntroGroup.SEARCH))
    }

    // ── 設定からの通し ──────────────────────────────────────────

    @Test
    fun `設定からの通しはフラグを見ない（何度でも読める）が、読み進めた組はその場で消費する`() {
        val store = FakeIntroFlagStore()
        val c = IntroController(store)
        store.preShow(IntroGroup.ABOUT, IntroGroup.READING, IntroGroup.SEARCH)

        c.openWalkthrough()
        assertEquals(IntroDeck.firstIndexOf(IntroGroup.ABOUT), c.flow?.index)

        // 通しは列の最後まで進む。回数も到達点も**列から引く**（直書きすると枚数の増減で静かにずれ、
        // 「1 枚手前で終端を期待する」＝ assertNull が赤くなる形で実装のせいに見える）。
        repeat(IntroDeck.cards.lastIndex) { c.next() }
        assertEquals("列の最後まで通しで進む", IntroDeck.cards.lastIndex, c.flow?.index)
        c.next() // 終端＝閉じる
        assertNull(c.flow)
        assertTrue(store.marked.containsAll(listOf(IntroGroup.ABOUT, IntroGroup.READING, IntroGroup.SEARCH)))
    }

    // ── ピルとの二重表示 ────────────────────────────────────────

    @Test
    fun `組B を表示した瞬間にピルを黙らせる（prefs と同一セッションの state の両方）`() {
        val (store, c) = controller()
        c.requestAuto(IntroGroup.ABOUT)
        assertFalse("組A ではピルの機会を焼かない", c.chromeHintSilenced)
        assertFalse(store.immersiveHintShown)

        c.dismiss()
        c.requestAuto(IntroGroup.READING)
        assertTrue("表示した瞬間に黙らせる（閉じるのを待たない）", c.chromeHintSilenced)
        assertTrue("旧キーはそのまま流用する＝キー名を変えない", store.immersiveHintShown)

        // 組B 自身の消費は従来どおり「閉じたとき」＝目的が違う 2 つを同じ瞬間に縛らない。
        assertFalse(store.isShown(IntroGroup.READING))
    }

    @Test
    fun `設定からの通しで組B へ届いたときも同じくピルを黙らせる`() {
        val (store, c) = controller()
        c.openWalkthrough()
        assertFalse(c.chromeHintSilenced)
        c.advanceToEndOf(IntroGroup.ABOUT)
        assertFalse("組A を読んでいる間は黙らせない（黙らせるのは組B を出した瞬間）", c.chromeHintSilenced)
        c.next() // 組B の先頭へ
        assertTrue(c.chromeHintSilenced)
        assertTrue(store.immersiveHintShown)
    }

    @Test
    fun `合流は論理和（永続フラグと同一セッションの黙らせのどちらでも消費済み）`() {
        assertFalse(immersiveHintConsumed(persisted = false, introSilenced = false))
        assertTrue(immersiveHintConsumed(persisted = true, introSilenced = false))
        assertTrue(immersiveHintConsumed(persisted = false, introSilenced = true))
    }

    @Test
    fun `別のカードを出している間は割り込まないが、その組のフラグは焼かない（機会を失わない）`() {
        val (store, c) = controller()
        c.requestAuto(IntroGroup.ABOUT)
        c.requestAuto(IntroGroup.SEARCH)
        assertEquals("同時に 2 枚は描けない", IntroGroup.ABOUT, c.flow?.group)
        assertFalse("割り込めなかった組を焼いてはならない", store.isShown(IntroGroup.SEARCH))
        // 組A を最後まで読み切って閉じる＝ここまで来て初めて次の組が出られる
        // （1 枚手前で止まると flow が残り、requestAuto は「同時に 2 枚は描けない」で黙って弾かれる）。
        c.advanceToEndOf(IntroGroup.ABOUT)
        c.next()
        c.requestAuto(IntroGroup.SEARCH)
        assertEquals("あとから改めて出る", IntroGroup.SEARCH, c.flow?.group)
    }
}
