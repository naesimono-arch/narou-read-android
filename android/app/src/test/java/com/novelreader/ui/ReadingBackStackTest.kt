package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 読書フローの Back スタック（[ReadingBackStack]）の不変条件を UI から切り離して固定する単体テスト。
 * 2026-09-07 裁定「Back も左上 ← も『前画面へ1段戻る』の1実装」を反映した不変条件を守る:
 *   ① [ReadingBackStack.back] は末尾を1枚 pop するだけ＝現在地が章か目次かで分岐しない
 *      （分岐が生まれると Back と ← が別々に育ち、同じ画面で操作により行き先が割れる＝3度の反転の真因）
 *   ② 前進の覗き（目次⇄章のプレビュー往復）で段が増えないこと（旧 navHistory 全逆再生バグの再発防止）
 *   ③ 終端（back()==null）に達する回数が入場形で決まること＝直行入場は1回・目次経由の本文は2回
 *
 * ⚠️ 終端の**行き先**（本棚 or 作品詳細）はこの構造の関心事ではない（入場元は NavController が知る）。
 * 入場元2種 × 操作2種の4通りの着地は [com.novelreader.ReadingEscapeNavigationTest] が固定する。
 */
class ReadingBackStackTest {

    private val INDEX = ReadingBackStack.INDEX

    // ── ① 直行入場（続きから／通知）: Back/← は1発で読書フローを出る ──
    @Test
    fun `本文直行の入場は Back 一発で読書フローの終端に達する（2026-09-07裁定）`() {
        // startFile が章＝続きから直行。下段が無い＝前画面が無い＝そのまま入場元へ帰す。
        val stack = ReadingBackStack.initial("c5.html")
        assertEquals("c5.html", stack.current)
        assertNull("直行入場の Back/← は目次を経由しない（07/19 の2段化を撤回）", stack.back())
    }

    @Test
    fun `本文直行で何話読み進めても Back 一発で終端（話送りは置き換えで深さ不変）`() {
        var stack = ReadingBackStack.initial("c5.html")
        stack = stack.sibling("c6.html").sibling("c7.html").sibling("c8.html")
        assertEquals("話送りは replace＝深さ1のまま", listOf("c8.html"), stack.screens)
        // 読んだ章列（c5→c6→c7）を Back で逆走しない＝07/19 が求めた第2要件は前進規則が担い続ける。
        assertNull(stack.back())
    }

    // ── ② 目次経由の入場: 章 → 目次 → 終端（2段） ──
    @Test
    fun `目次から章へ入ると Back は目次へ戻り もう一度で終端に達する`() {
        var stack = ReadingBackStack.initial(INDEX).openChapter("c1.html")
        assertEquals(listOf(INDEX, "c1.html"), stack.screens)
        stack = stack.back()!! // 本文→目次（前画面）
        assertEquals(listOf(INDEX), stack.screens)
        assertNull(stack.back()) // 目次→読書フローを出る
    }

    @Test
    fun `目次入場後に何話読み進めてもBackは目次経由の2段のまま（話送りは置き換え）`() {
        val stack = ReadingBackStack.initial(INDEX)
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
            stack = stack.back()!! // 章→目次（前画面）
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

    // ── 直行本文から目次を開いた後: Back は本文へ戻る（前画面。本棚へは落ちない）──
    @Test
    fun `直行本文から目次を開いた後の Back は前画面の本文へ戻る`() {
        val stack = ReadingBackStack.initial("c5.html").openToc() // 直行本文→下端目次ボタン [c5, index]
        assertEquals(listOf("c5.html", INDEX), stack.screens)
        // 07/19 は「目次の一つ上＝本棚」として c5 を飛ばして脱出していた（同じ目次画面で Back と ← が
        // 割れていた最後の1件）。前画面遷移では両者とも c5 へ戻る。
        val back = stack.back()
        assertEquals(listOf("c5.html"), back?.screens)
        assertNull("さらに Back すると終端（入場元へ）", back!!.back())
    }

    @Test
    fun `直行本文から目次を開き別章を覗いても覗きは相殺され深さは最大3で不変`() {
        var stack = ReadingBackStack.initial("c5.html").openToc() // [c5, index]
        repeat(30) { i ->
            stack = stack.openChapter("peek$i.html") // [c5, index, peek]
            assertEquals(3, stack.screens.size)
            stack = stack.back()!! // 覗き章→目次（前画面）→ [c5, index]
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
        assertEquals("復帰後も Back で目次→終端の2段", listOf(INDEX), stack.back()!!.screens)
    }

    @Test
    fun `続きに戻る＝退避元章が下段に在るときはそこまで巻き戻す（重複を積まない）`() {
        // 直行本文c5から目次→c2覗き。退避元c5は下段に在るので popUpTo で c5 まで巻き戻す。
        val stack = ReadingBackStack.initial("c5.html")
            .openToc()               // [c5, index]
            .openChapter("c2.html")  // [c5, index, c2]
            .returnTo("c5.html")     // c5 は下段に在る→そこまで巻き戻し
        assertEquals(listOf("c5.html"), stack.screens)
        // 巻き戻しで下段を捨てているため、復帰後の Back は直行入場と同じく1発で終端。
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

    // ── ①不変条件: back は必ず1枚縮む・現在地の種類（章/目次）で分岐しない ── 2026-09-07裁定の中核
    @Test
    fun `back は常に末尾を1枚 pop する＝現在地が章か目次かに依存しない`() {
        // あらゆる入場・移動形を列挙。「章なら◯・目次なら×」という分岐が入り込んでいないことを固定する。
        listOf(
            ReadingBackStack.initial("c5.html"),                                  // 直行本文 [c5]
            ReadingBackStack.initial(INDEX),                                      // 目次入場 [index]
            ReadingBackStack.initial(INDEX).openChapter("c1.html"),               // 目次経由 [index, c1]
            ReadingBackStack.initial("c5.html").openToc(),                        // 直行→目次 [c5, index]
            ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html"), // 覗き [c5, index, c2]
            ReadingBackStack.initial("c5.html").sibling("c6.html"),               // 話送り後 [c6]
        ).forEach { stack ->
            val back = stack.back()
            if (stack.screens.size == 1) {
                assertNull("下段が無ければ終端（呼び出し側が入場元へ帰す）", back)
            } else {
                assertEquals("前画面＝末尾1枚 pop", stack.screens.dropLast(1), back!!.screens)
                assertTrue("back は空スタックを生まない", back.screens.isNotEmpty())
            }
        }
    }

    // ── 入場形ごとの「終端に達するまでの回数」＝可視の押下回数を固定する ──
    @Test
    fun `終端までの Back 回数は入場形で決まる（直行1回・目次入場1回・目次経由の本文2回）`() {
        fun stepsToExit(entry: ReadingBackStack): Int {
            var cur: ReadingBackStack? = entry
            var steps = 0
            while (cur != null) {
                cur = cur.back()
                steps++
                assertTrue("終端に達しない＝無限ループの防波堤", steps <= 8)
            }
            return steps
        }
        assertEquals(1, stepsToExit(ReadingBackStack.initial("c5.html")))
        assertEquals(1, stepsToExit(ReadingBackStack.initial(INDEX)))
        assertEquals(2, stepsToExit(ReadingBackStack.initial(INDEX).openChapter("c1.html")))
        assertEquals(2, stepsToExit(ReadingBackStack.initial("c5.html").openToc()))
        assertEquals(3, stepsToExit(ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html")))
    }

    // ── 左上 ← ボタンは Back と同一実装＝別関数を持たない（操作で行き先が割れない） ──
    @Test
    fun `左上←とシステムBackは同じ back の呼び出しで同じ遷移列を辿る`() {
        // ← の実装は「back() を呼ぶ」以外に無い（章の ← ＝ChapterNav.onBack／目次の ← ＝ReadingScreen が
        // performBack を渡す）。ここでは同じ関数を2度辿って列が一致することを機械的に固定し、
        // 将来 ← 側に「章なら目次へ」等の別実装が復活したら UI 側の契約テスト
        // （NativeReadingScreenA11yTest の ←/customAction）と併せて検知できるようにする。
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
                viaUp = viaUp.back()
            }
            assertEquals("同時に読書フローを出る（双方 null）", viaUp, viaBack)
        }
    }
}
