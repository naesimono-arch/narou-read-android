package com.novelreader.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * カード列の「回の切りかた」を固定する（正本モック tutorial-onboarding-K.html §3/§8）。
 *
 * ここで守りたいのは **列が 1 本・実装が 1 つ**という設計そのもの。終端・点・ボタンを組ごとの分岐で
 * 書き始めた瞬間に入口が増えるたび条件が増え、文言が二重管理になる（正本が最も警戒している型）。
 */
class IntroFlowTest {

    @Test
    fun `列は 5 枚・組は 2-2-1 で並ぶ（文言の所在が二重化していない）`() {
        assertEquals(5, IntroDeck.cards.size)
        assertEquals(2, IntroDeck.countIn(IntroGroup.ABOUT))
        assertEquals(2, IntroDeck.countIn(IntroGroup.READING))
        assertEquals(1, IntroDeck.countIn(IntroGroup.SEARCH))
        assertEquals(
            listOf(IntroGroup.ABOUT, IntroGroup.ABOUT, IntroGroup.READING, IntroGroup.READING, IntroGroup.SEARCH),
            IntroDeck.cards.map { it.group },
        )
    }

    @Test
    fun `終端は〈通し ? 列の最後 - 組の最後〉の 1 行で決まる`() {
        // 単独の回＝組の最後で終わる。
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 0).isTerminal)
        assertTrue(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 1).isTerminal)
        assertTrue(IntroFlow(IntroGroup.READING, walkthrough = false, index = 3).isTerminal)
        assertTrue(IntroFlow(IntroGroup.SEARCH, walkthrough = false, index = 4).isTerminal)
        // 通し＝組の最後では終わらず、列の最後だけが終端。
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 1).isTerminal)
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 3).isTerminal)
        assertTrue(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 4).isTerminal)
    }

    @Test
    fun `点はその回に出す枚数でなく〈いま居る組〉の枚数ぶんだけ打つ（列全体の 5 個は打たない）`() {
        val walkthrough = IntroFlow(IntroGroup.ABOUT, walkthrough = true)
        assertEquals(listOf(2, 2, 2, 2, 0), (0..4).map { walkthrough.copy(index = it).dotCount })
        // 組が変わるたびに現在地はリセットされる（●○ → ●○ → なし）。
        assertEquals(listOf(0, 1, 0, 1, 0), (0..4).map { walkthrough.copy(index = it).dotIndex })
    }

    @Test
    fun `1 枚だけの回に点を 1 個出さない（壊れて見える・点の不在が終わりの合図）`() {
        assertEquals(0, IntroFlow(IntroGroup.SEARCH, walkthrough = false).dotCount)
    }

    @Test
    fun `もどるはインデックス −1 で組をまたぐ特別扱いを持たない`() {
        val walkthrough = IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 2)
        assertEquals(1, walkthrough.back().index)
        assertEquals(IntroGroup.ABOUT, walkthrough.back().group) // 組B の先頭から組A の末尾へ素直に戻る
        // 回の先頭より前へは戻らない。
        assertEquals(2, IntroFlow(IntroGroup.READING, walkthrough = false, index = 2).back().index)
    }

    @Test
    fun `副ボタンは 先頭＝あとで それ以降＝もどる 1 枚で終わる回＝置かない`() {
        assertEquals(IntroSecondary.LATER, IntroFlow(IntroGroup.ABOUT, false, 0).secondary)
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.ABOUT, false, 1).secondary)
        assertEquals(IntroSecondary.LATER, IntroFlow(IntroGroup.READING, false, 2).secondary)
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.READING, false, 3).secondary)
        assertEquals(IntroSecondary.NONE, IntroFlow(IntroGroup.SEARCH, false, 4).secondary)
        // 通しでは組B の先頭も「途中」なので もどる。
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.ABOUT, true, 2).secondary)
    }

    @Test
    fun `主ボタンの語は回の切りかたで変わる（文章ではなく部品）`() {
        assertEquals("つづける", IntroFlow(IntroGroup.ABOUT, false, 0).primaryLabel)
        assertEquals("はじめる", IntroFlow(IntroGroup.ABOUT, false, 1).primaryLabel)
        assertEquals("つぎへ", IntroFlow(IntroGroup.ABOUT, true, 1).primaryLabel) // 通しでは終端でない
        assertEquals("つぎへ", IntroFlow(IntroGroup.READING, false, 2).primaryLabel)
        assertEquals("とじる", IntroFlow(IntroGroup.READING, false, 3).primaryLabel)
        assertEquals("つぎへ", IntroFlow(IntroGroup.ABOUT, true, 3).primaryLabel)
        assertEquals("とじる", IntroFlow(IntroGroup.SEARCH, false, 4).primaryLabel)
    }

    @Test
    fun `再訪導線の 1 行は各組の最後のカードにだけ置く（新しい常設物を増やさない）`() {
        assertEquals(
            listOf(false, true, false, false, true),
            IntroDeck.cards.map { it.footnote != null },
        )
    }

    @Test
    fun `強調マークは文言を壊さずに展開される（閉じ忘れは素通し）`() {
        // 素の文字列は正本 §4 の写経なので、展開結果が元の平文と一致することを固定する。
        val raw = "**PDF** と Web の連載小説（**小説家になろう**）を、ひとつの本棚で読めます。"
        val annotated = introAnnotated(raw, androidx.compose.ui.text.SpanStyle())
        assertEquals(raw.replace("**", ""), annotated.text)
        assertEquals(2, annotated.spanStyles.size)
        assertEquals("閉じ忘れ **は素通し", introAnnotated("閉じ忘れ **は素通し", androidx.compose.ui.text.SpanStyle()).text)
    }
}
