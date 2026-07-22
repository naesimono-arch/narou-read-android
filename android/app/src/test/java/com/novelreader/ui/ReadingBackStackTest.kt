package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 読書フローの Back スタック（[ReadingBackStack]）の不変条件を UI から切り離して固定する単体テスト。
 * 2026-07-19 裁定「Back は経路を逆再生せず必ず一つ上の階層へ」を反映した2つの不変条件を守る:
 *   ① Back は末尾 pop（経路逆再生）でなく一階層 up（章→目次・目次→本棚）＝左上 ← ボタンと完全一致
 *   ② 前進の覗き（目次⇄章のプレビュー往復）で段が増えないこと（旧 navHistory 全逆再生バグの再発防止）
 */
class ReadingBackStackTest {

    private val INDEX = ReadingBackStack.INDEX

    // ── ① 本棚→本文直行（続きから）: Back は目次を経て本棚（2段。1発で本棚にしない） ──
    @Test
    fun `本文直行の入場でも Back は目次を経て本棚へ（2段・2026-07-19裁定）`() {
        // startFile が章＝続きから直行。経路に目次が無くても Back は「一階層 up」＝まず目次を開く。
        val stack = ReadingBackStack.initial("c5.html")
        assertEquals("c5.html", stack.current)
        val toToc = stack.back()!!                       // 章→目次（経路に無いので目次を積む）
        assertEquals(listOf("c5.html", INDEX), toToc.screens)
        assertNull("目次→これ以上上位なし＝本棚へ", toToc.back())
    }

    @Test
    fun `本文直行で何話読み進めても Back は目次経由の2段（話送りは置き換えで深さ不変）`() {
        var stack = ReadingBackStack.initial("c5.html")
        stack = stack.sibling("c6.html").sibling("c7.html").sibling("c8.html")
        assertEquals(listOf("c8.html"), stack.screens) // 話送りは replace＝深さ1のまま
        val toToc = stack.back()!!                      // 章→目次
        assertEquals(listOf("c8.html", INDEX), toToc.screens)
        assertNull(toToc.back())                        // 目次→本棚
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
            stack = stack.back()!! // 章→目次（既存目次へ巻き戻し）
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

    // ── 直行本文から目次を開く: 現在地が目次＝Back は本棚へ（下段の本文へ逆走しない） ──
    @Test
    fun `直行本文から目次を開いた後の Back は本文へ戻らず本棚へ抜ける（経路逆再生の廃止）`() {
        val stack = ReadingBackStack.initial("c5.html").openToc() // 直行本文→目次ボタン [c5, index]
        assertEquals(listOf("c5.html", INDEX), stack.screens)
        // 現在地が目次＝一階層 up の先は本棚。下段に c5 が在っても経路を逆走しない（07/15 逆再生の撤回）。
        assertNull("目次の Back は本棚へ（c5 へは戻さない）", stack.back())
    }

    @Test
    fun `直行本文から目次を開き別章を覗いても覗きは相殺され深さは最大3で不変`() {
        var stack = ReadingBackStack.initial("c5.html").openToc() // [c5, index]
        repeat(30) { i ->
            stack = stack.openChapter("peek$i.html") // [c5, index, peek]
            assertEquals(3, stack.screens.size)
            stack = stack.back()!! // 章→目次（既存目次へ巻き戻し）→ [c5, index]
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
        // 復帰後の Back は経路を逆走せず一階層 up＝目次を開いてから本棚（直行本文と同じ2段）。
        val toToc = stack.back()!!
        assertEquals(listOf("c5.html", INDEX), toToc.screens)
        assertNull(toToc.back())
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

    // ── ①不変条件: back は常に一階層 up（経路を逆走しない・空スタックも生まない）── 2026-07-19裁定の中核
    @Test
    fun `back は常に一階層 up＝章なら現在地が目次に・目次なら本棚へ抜ける（空を生まない）`() {
        // あらゆる入場・移動形を列挙。裁定＝Back は末尾 pop でなく必ず一つ上の階層へ。
        val chapterTops = listOf(
            ReadingBackStack.initial("c5.html"),                                  // 直行本文 [c5]
            ReadingBackStack.initial(INDEX).openChapter("c1.html"),               // 目次経由 [index, c1]
            ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html"), // 覗き [c5, index, c2]
            ReadingBackStack.initial("c5.html").sibling("c6.html"),               // 話送り後 [c6]
        )
        chapterTops.forEach { stack ->
            val up = stack.back()
            assertNotNull("章の Back は本棚へ抜けず必ず目次を開く", up)
            assertTrue("back は空スタックを生まない", up!!.screens.isNotEmpty())
            assertEquals("章の一つ上の階層は目次", INDEX, up.current)
        }
        val tocTops = listOf(
            ReadingBackStack.initial(INDEX),               // 目次入場 [index]
            ReadingBackStack.initial("c5.html").openToc(), // 直行→目次 [c5, index]
        )
        tocTops.forEach { stack ->
            assertNull("目次の Back は本棚へ（一つ上の階層＝本棚）", stack.back())
        }
    }

    // ── 直行入場でも Back と 左上 ← ボタンが同一の遷移列を辿る（階層統一の要）── 2026-07-19裁定
    @Test
    fun `全入場形で Back の遷移列と 左上← の遷移列が一致する`() {
        // 左上←＝章では目次を開き（openToc）・目次では本棚へ（onNavigateToBookshelf＝null 相当）。
        // Back を同モデルへ寄せた＝両者の遷移列が入場形に依らず一致（将来 back が逆再生へ退行したら検知）。
        fun up(s: ReadingBackStack): ReadingBackStack? =
            if (s.current == INDEX) null else s.openToc()
        listOf(
            ReadingBackStack.initial("c5.html"),                    // 直行本文
            ReadingBackStack.initial(INDEX).openChapter("c1.html"), // 目次経由
            ReadingBackStack.initial("c5.html").openToc(),          // 直行→目次
        ).forEach { entry ->
            var viaBack: ReadingBackStack? = entry
            var viaUp: ReadingBackStack? = entry
            while (viaBack != null && viaUp != null) {
                assertEquals("Back と ← の各段の経路が一致", viaUp.screens, viaBack.screens)
                viaBack = viaBack.back()
                viaUp = up(viaUp)
            }
            assertEquals("同時に本棚へ抜ける（双方 null）", viaUp, viaBack)
        }
    }
}
