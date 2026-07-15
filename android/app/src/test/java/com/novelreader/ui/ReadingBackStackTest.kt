package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 読書フローの Back スタック（[ReadingBackStack]）の不変条件を UI から切り離して固定する単体テスト。
 * 2026-07-15 の再設計「固定2階層 collapse → 実経路反映＋覗きは置き換え」の2つの不変条件を守る:
 *   ① スタックが実際に辿った経路の写しであること（Back が経路どおり逆再生）
 *   ② 覗き（目次⇄章のプレビュー往復）で段が増えないこと（旧 navHistory 全逆再生バグの再発防止）
 */
class ReadingBackStackTest {

    private val INDEX = ReadingBackStack.INDEX

    // ── ① 本棚→本文直行（続きから）: Back 1発で本棚 ──
    @Test
    fun `本文直行の入場はBack1発で本棚へ抜ける（back がnull）`() {
        // startFile が章＝続きから直行。目次を挟まないので経路は本文1枚だけ。
        val stack = ReadingBackStack.initial("c5.html")
        assertEquals("c5.html", stack.current)
        assertNull("入場画面1枚のみ＝これ以上戻れない＝本棚へ", stack.back())
    }

    @Test
    fun `本文直行で何話読み進めてもBackは1発で本棚（話送りは置き換えで深さ不変）`() {
        var stack = ReadingBackStack.initial("c5.html")
        stack = stack.sibling("c6.html").sibling("c7.html").sibling("c8.html")
        assertEquals(listOf("c8.html"), stack.screens) // 話送りは replace＝深さ1のまま
        assertNull(stack.back())
    }

    // ── ② 本棚→目次→本文: Back で目次→本棚（2段） ──
    @Test
    fun `目次から章へ入ると Back で目次を経て本棚へ戻る（2段）`() {
        var stack = ReadingBackStack.initial(INDEX).openChapter("c1.html")
        assertEquals(listOf(INDEX, "c1.html"), stack.screens)
        stack = stack.back()!! // 本文→目次
        assertEquals(listOf(INDEX), stack.screens)
        assertNull(stack.back()) // 目次→本棚
    }

    @Test
    fun `目次入場後に何話読み進めてもBackは目次経由の2段のまま（話送りは置き換え）`() {
        var stack = ReadingBackStack.initial(INDEX)
            .openChapter("c1.html").sibling("c2.html").sibling("c3.html")
        assertEquals(listOf(INDEX, "c3.html"), stack.screens)
        assertEquals(listOf(INDEX), stack.back()!!.screens)
    }

    // ── ③ 覗きの反復でスタック深さ不変（不変条件②・旧 navHistory バグ再発防止） ──
    @Test
    fun `目次から章を何度覗いてもスタック深さは増えない（Back で目次へ戻る反復）`() {
        var stack = ReadingBackStack.initial(INDEX)
        repeat(50) { i ->
            stack = stack.openChapter("peek$i.html") // 覗く（push）
            assertEquals("覗き中は目次+章の2枚", 2, stack.screens.size)
            stack = stack.back()!! // 目次へ戻る（pop で相殺）
            assertEquals(listOf(INDEX), stack.screens)
        }
    }

    @Test
    fun `覗き→目次ボタン→別章覗き…の反復もスタック深さ不変（目次ボタン経路）`() {
        // Back でなく下端「目次」ボタン（openToc）で目次へ戻る経路。既存目次へ巻き戻すため重複を積まない。
        var stack = ReadingBackStack.initial(INDEX)
        repeat(50) { i ->
            stack = stack.openChapter("peek$i.html").openToc()
            assertEquals("既存目次へ popUpTo＝目次1枚に戻る", listOf(INDEX), stack.screens)
        }
    }

    @Test
    fun `目次ボタンで既存目次へ戻っても目次が二重に積まれない（popUpTo）`() {
        val stack = ReadingBackStack.initial(INDEX).openChapter("c1.html").openToc()
        assertEquals(listOf(INDEX), stack.screens)
    }

    // ── 直行本文から目次を開く: 実経路が [本文, 目次] として残り Back で逆再生 ──
    @Test
    fun `直行本文から目次を開くと経路は本文の下に目次が積まれBackで本文へ戻る`() {
        var stack = ReadingBackStack.initial("c5.html").openToc() // 直行本文→目次ボタン
        assertEquals(listOf("c5.html", INDEX), stack.screens)
        stack = stack.back()!! // 目次→本文（辿った経路どおり）
        assertEquals(listOf("c5.html"), stack.screens)
        assertNull(stack.back()) // 本文→本棚
    }

    @Test
    fun `直行本文から目次を開き別章を覗いても覗きは相殺され深さは最大3で不変`() {
        var stack = ReadingBackStack.initial("c5.html").openToc() // [c5, index]
        repeat(30) { i ->
            stack = stack.openChapter("peek$i.html") // [c5, index, peek]
            assertEquals(3, stack.screens.size)
            stack = stack.back()!! // [c5, index]
            assertEquals(listOf("c5.html", INDEX), stack.screens)
        }
    }

    // ── ④ 参照ジャンプ（jumpOrigin）の既存挙動を壊さない: 続きに戻る（returnTo） ──
    @Test
    fun `続きに戻る＝退避元が下段に無ければ覗き章を置き換える（深さ不変で復帰）`() {
        // 目次から続き章c5を読み、目次に戻って別章c2を覗いた後「続きに戻る」。
        // 目次へ戻る際に c5 は popUpTo で外れ下段に無いため、returnTo は覗き章c2を退避元c5へ置き換える。
        var stack = ReadingBackStack.initial(INDEX)
            .openChapter("c5.html") // 続き位置
            .openToc()              // 目次へ戻る（既存目次へ popUpTo）→ [index]
            .openChapter("c2.html") // 覗き → [index, c2]
        stack = stack.returnTo("c5.html") // 退避元は下段に無い→置き換え → [index, c5]
        assertEquals(listOf(INDEX, "c5.html"), stack.screens)
        assertEquals("復帰後も Back で目次→本棚の2段", listOf(INDEX), stack.back()!!.screens)
    }

    @Test
    fun `続きに戻る＝退避元章が下段に在るときはそこまで巻き戻す（重複を積まない）`() {
        // 直行本文c5から目次→c2覗き。退避元c5は下段に在るので popUpTo で c5 まで巻き戻す。
        val stack = ReadingBackStack.initial("c5.html")
            .openToc()               // [c5, index]
            .openChapter("c2.html")  // [c5, index, c2]
            .returnTo("c5.html")     // c5 は下段に在る→そこまで巻き戻し
        assertEquals(listOf("c5.html"), stack.screens)
        assertNull(stack.back())
    }

    // ── 端章の prev/next は目次へ抜ける（sibling("index.html") は openToc に委譲） ──
    @Test
    fun `端章の話送りが目次へ抜けるとき直行本文の下段を失わない`() {
        // 直行本文c1（先頭章）の「前章」→ prevFile="index.html"。横移動で置き換えると c1 を失うため openToc 扱い。
        val stack = ReadingBackStack.initial("c1.html").sibling(INDEX)
        assertEquals(listOf("c1.html", INDEX), stack.screens)
    }

    // ── ⑤ プロセス再生成（rememberSaveable）で経路が復元される ──
    @Test
    fun `screens 経由の往復で経路が完全復元される（listSaver 保存契約）`() {
        // rememberSaveable の Saver は screens リストをそのまま保存/復元する（readingBackStackSaver）。
        // その保存契約＝ReadingBackStack(x.screens)==x を純粋レベルで固定する。
        val original = ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html")
        val restored = ReadingBackStack(original.screens)
        assertEquals(original, restored)
        assertEquals(original.current, restored.current)
    }

    // ── 防御的不変: back は決して空スタックを生まない ──
    @Test
    fun `back は空スタックを生まず必ず現在地1枚を残すかnullを返す`() {
        var stack = ReadingBackStack.initial(INDEX).openChapter("c1.html")
        while (true) {
            val next = stack.back() ?: break
            assertTrue("back の結果は常に非空", next.screens.isNotEmpty())
            stack = next
        }
    }
}
