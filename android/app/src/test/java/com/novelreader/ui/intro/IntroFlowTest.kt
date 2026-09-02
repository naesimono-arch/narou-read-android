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
 *
 * ## なぜこのファイルだけ index を数値で書くのか（他のテストでは禁じているのに）
 * ここは〈index → 終端/点/副ボタン/ボタン語〉の**対応表そのものが検査対象**で、右辺を
 * `IntroDeck.lastIndexOf(...)` で導くと**式が実装と同じ式になり、何も確かめていないテスト**になる
 * （実装がずれても両辺が同じだけずれる）。よって**この 1 本だけが列の形を数値で写し取り**、
 * 他のテスト（IntroControllerTest・IntroOverlayContentTest・撮影表）は必ず列から引く。
 * ⚠️ 列に 1 枚差したら、まず**このファイルの数値を直す**のが正しい順序。
 */
class IntroFlowTest {

    @Test
    fun `列は 6 枚・組は 3-2-1 で並ぶ（文言の所在が二重化していない）`() {
        assertEquals(6, IntroDeck.cards.size)
        assertEquals(3, IntroDeck.countIn(IntroGroup.ABOUT))
        assertEquals(2, IntroDeck.countIn(IntroGroup.READING))
        assertEquals(1, IntroDeck.countIn(IntroGroup.SEARCH))
        assertEquals(
            listOf(
                IntroGroup.ABOUT, IntroGroup.ABOUT, IntroGroup.ABOUT,
                IntroGroup.READING, IntroGroup.READING, IntroGroup.SEARCH,
            ),
            IntroDeck.cards.map { it.group },
        )
    }

    @Test
    fun `2 択を持つのは向きの選択カード 1 枚だけ（列に選択が散らばらない）`() {
        // 選択が 2 枚以上に散ると「どれが効いたのか」が画面からも保存値からも辿れなくなる。
        assertEquals(1, IntroDeck.cards.count { it.choice != null })
        val card = IntroDeck.cards.single { it.choice != null }
        // 本文を見る前に確定させるのが設計の眼目＝組A 以外に置いた時点で remember の罠を踏む
        // （NativeReadingScreen の verticalMode は入場時に 1 度だけ prefs を読む）。
        assertEquals(IntroGroup.ABOUT, card.group)
        assertEquals("どちらで読みますか", card.title)
        assertEquals("横書き", card.choice?.labelForFalse)
        assertEquals("縦書き", card.choice?.labelForTrue)
        assertEquals(IntroFigure.ORIENTATION, card.figure)
    }

    @Test
    fun `終端は〈通し ? 列の最後 - 組の最後〉の 1 行で決まる`() {
        // 単独の回＝組の最後で終わる。
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 0).isTerminal)
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 1).isTerminal)
        assertTrue(IntroFlow(IntroGroup.ABOUT, walkthrough = false, index = 2).isTerminal)
        assertTrue(IntroFlow(IntroGroup.READING, walkthrough = false, index = 4).isTerminal)
        assertTrue(IntroFlow(IntroGroup.SEARCH, walkthrough = false, index = 5).isTerminal)
        // 通し＝組の最後では終わらず、列の最後だけが終端。
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 2).isTerminal)
        assertFalse(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 4).isTerminal)
        assertTrue(IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 5).isTerminal)
    }

    @Test
    fun `点はその回に出す枚数でなく〈いま居る組〉の枚数ぶんだけ打つ（列全体の 6 個は打たない）`() {
        val walkthrough = IntroFlow(IntroGroup.ABOUT, walkthrough = true)
        assertEquals(listOf(3, 3, 3, 2, 2, 0), (0..5).map { walkthrough.copy(index = it).dotCount })
        // 組が変わるたびに現在地はリセットされる（●○○ → ●○ → なし）。
        assertEquals(listOf(0, 1, 2, 0, 1, 0), (0..5).map { walkthrough.copy(index = it).dotIndex })
    }

    @Test
    fun `1 枚だけの回に点を 1 個出さない（壊れて見える・点の不在が終わりの合図）`() {
        assertEquals(0, IntroFlow(IntroGroup.SEARCH, walkthrough = false).dotCount)
    }

    @Test
    fun `もどるはインデックス −1 で組をまたぐ特別扱いを持たない`() {
        val walkthrough = IntroFlow(IntroGroup.ABOUT, walkthrough = true, index = 3)
        assertEquals(2, walkthrough.back().index)
        assertEquals(IntroGroup.ABOUT, walkthrough.back().group) // 組B の先頭から組A の末尾へ素直に戻る
        // 回の先頭より前へは戻らない。
        assertEquals(3, IntroFlow(IntroGroup.READING, walkthrough = false, index = 3).back().index)
    }

    @Test
    fun `副ボタンは 先頭＝あとで それ以降＝もどる 1 枚で終わる回＝置かない`() {
        assertEquals(IntroSecondary.LATER, IntroFlow(IntroGroup.ABOUT, false, 0).secondary)
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.ABOUT, false, 1).secondary)
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.ABOUT, false, 2).secondary)
        assertEquals(IntroSecondary.LATER, IntroFlow(IntroGroup.READING, false, 3).secondary)
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.READING, false, 4).secondary)
        assertEquals(IntroSecondary.NONE, IntroFlow(IntroGroup.SEARCH, false, 5).secondary)
        // 通しでは組B の先頭も「途中」なので もどる。
        assertEquals(IntroSecondary.BACK, IntroFlow(IntroGroup.ABOUT, true, 3).secondary)
    }

    @Test
    fun `主ボタンの語は回の切りかたで変わる（文章ではなく部品）`() {
        assertEquals("つづける", IntroFlow(IntroGroup.ABOUT, false, 0).primaryLabel)
        // 向きの選択カードも「まだ先がある」枚＝つづける（選ばせて終わりにしない）。
        assertEquals("つづける", IntroFlow(IntroGroup.ABOUT, false, 1).primaryLabel)
        assertEquals("はじめる", IntroFlow(IntroGroup.ABOUT, false, 2).primaryLabel)
        assertEquals("つぎへ", IntroFlow(IntroGroup.ABOUT, true, 2).primaryLabel) // 通しでは終端でない
        assertEquals("つぎへ", IntroFlow(IntroGroup.READING, false, 3).primaryLabel)
        assertEquals("とじる", IntroFlow(IntroGroup.READING, false, 4).primaryLabel)
        assertEquals("つぎへ", IntroFlow(IntroGroup.ABOUT, true, 4).primaryLabel)
        assertEquals("とじる", IntroFlow(IntroGroup.SEARCH, false, 5).primaryLabel)
    }

    @Test
    fun `再訪導線の 1 行は各組の最後のカードにだけ置く（新しい常設物を増やさない）`() {
        // ⚠️ 小さい行（正本 `.later`）の**枠**は 2 つの用に使う——各組の最後＝再訪導線、
        // 向きの選択カード＝「あとから変えられる」の明示。枠の有無で数えると選択カードの 1 行が
        // 紛れ込み、**規則が緩んだことに誰も気づかない**ので、再訪導線を名指しで数える。
        assertEquals(
            listOf(false, false, true, false, false, true),
            IntroDeck.cards.map { it.footnote?.contains("［操作の説明］") == true },
        )
        val choiceCard = IntroDeck.cards.single { it.choice != null }
        assertEquals("あとから ［表示設定］＞［本文の向き］ で変えられます。", choiceCard.footnote)
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
