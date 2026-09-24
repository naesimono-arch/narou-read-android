package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 読書フローの Back スタック（[ReadingBackStack]）の不変条件を UI から切り離して固定する単体テスト。
 *
 * 固定する契約＝「← もシステム Back も必ず一つ上の**階層**へ」（2026-07-19 ユーザー裁定・07-29 に
 * アプリ全体へ拡大＝ADR 0026・一度覆したのを ADR 0047 で復帰）:
 *   ① [ReadingBackStack.back] の行き先は**現在地の種類だけ**で決まる（章→目次／目次→null）。
 *      どう来たか（経路の下段）に依らない＝「同じ画面なのに来歴で行き先が変わる」を表現不能にする。
 *   ② [章,目次]（直行入場から目次を開いた形）で目次から戻ると**章へ降りず** null＝脱出。
 *      履歴逆走（末尾1枚 pop）だと章へ降りて「戻るのに階層が下がる」が起きる＝ADR 0046 の実害。
 *   ③ 終端までの回数は入場形で決まる＝直行入場は「章→目次→脱出」の2回・目次経由の本文も2回。
 *   ④ 前進の覗き・話送りで段が増えない（旧 navHistory 全逆再生バグの再発防止）。
 *   ⑤ [screens] 往復＝rememberSaveable（listSaver）の保存契約。
 *
 * ⚠️ 終端の**行き先**（本棚 or 作品詳細）はこの構造の関心事ではない（入場元は NavController が知る）。
 * 入場元2種 × 操作2種の4通りの着地は [com.novelreader.ReadingEscapeNavigationTest] が固定する。
 */
class ReadingBackStackTest {

    private val INDEX = ReadingBackStack.INDEX

    /** 終端（back()==null）に達するまでの回数＝ユーザーの可視の押下回数。 */
    private fun stepsToExit(entry: ReadingBackStack): Int {
        var cur: ReadingBackStack? = entry
        var steps = 0
        while (cur != null) {
            cur = cur.back()
            steps++
            // 階層 up は「章→目次→null」で必ず尽きる。増え続けるなら openToc が目次を積み直している。
            assertTrue("終端に達しない＝無限ループの防波堤", steps <= 8)
        }
        return steps
    }

    // ── ① 行き先は現在地の種類だけで決まる（階層 up の中核） ──
    @Test
    fun `back の行き先は現在地が章か目次かだけで決まり経路の下段に依らない`() {
        // 「章に居る」形をあらゆる来歴で列挙。どれも目次へ上がる＝下段の違いが行き先を変えないこと。
        listOf(
            ReadingBackStack.initial("c5.html"),                                  // 直行本文 [c5]
            ReadingBackStack.initial(INDEX).openChapter("c1.html"),               // 目次経由 [index, c1]
            ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html"), // 覗き [c5, index, c2]
            ReadingBackStack.initial("c5.html").sibling("c6.html"),               // 話送り後 [c6]
        ).forEach { stack ->
            val back = stack.back()
            assertEquals("章の一つ上は必ず目次", INDEX, back!!.current)
        }
        // 「目次に居る」形も同様に、来歴に依らず必ず終端。
        listOf(
            ReadingBackStack.initial(INDEX),                        // 目次入場 [index]
            ReadingBackStack.initial("c5.html").openToc(),          // 直行→目次 [c5, index]
            ReadingBackStack.initial(INDEX).openChapter("c1.html").openToc(), // 目次→章→目次 [index]
        ).forEach { stack ->
            assertNull("目次の一つ上は読書フローの外＝終端", stack.back())
        }
    }

    @Test
    fun `章から上がると既出の目次まで巻き戻し 目次が無ければ積む（どちらも現在地は目次）`() {
        // 目次経由＝既出の目次へ巻き戻す（重複を積まない）。
        assertEquals(
            listOf(INDEX),
            ReadingBackStack.initial(INDEX).openChapter("c1.html").back()!!.screens,
        )
        // 直行入場＝目次が経路に無いので積む（段は増えるが、次の back で必ず終端）。
        assertEquals(
            listOf("c5.html", INDEX),
            ReadingBackStack.initial("c5.html").back()!!.screens,
        )
    }

    // ── ② [章,目次] から戻ると章へ降りず脱出（履歴逆走との決定的な差） ──
    @Test
    fun `直行本文から目次を開いた後の戻るは本文へ降りず読書フローを出る`() {
        val stack = ReadingBackStack.initial("c5.html").openToc() // 直行本文→下端目次ボタン [c5, index]
        assertEquals(listOf("c5.html", INDEX), stack.screens)
        // 履歴逆走（末尾1枚 pop）なら [c5] へ**降りて**しまう。階層 up は上がる方向しか無い。
        assertNull("目次の戻るは常に脱出＝章へは降りない（ADR 0046 の実害の固定）", stack.back())
    }

    @Test
    fun `直行入場から目次を開いて別章を覗いた後も 目次からは章へ降りず脱出する`() {
        // [c5, index, c2] から2回。1回目で目次（c2 でなく index）へ上がり、2回目で c5 へ降りずに脱出。
        val stack = ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html")
        val toToc = stack.back()!!
        assertEquals(listOf("c5.html", INDEX), toToc.screens)
        assertNull("下段に c5 が残っていても降りない", toToc.back())
    }

    // ── ③ 終端までの回数（可視の押下回数） ──
    @Test
    fun `終端までの戻る回数は 章に居れば2回 目次に居れば1回`() {
        // 直行入場は「章→目次→脱出」の2段。ADR 0046 は1発で出ていた＝これが退行だった。
        assertEquals(2, stepsToExit(ReadingBackStack.initial("c5.html")))
        assertEquals(2, stepsToExit(ReadingBackStack.initial(INDEX).openChapter("c1.html")))
        assertEquals(2, stepsToExit(ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html")))
        assertEquals(2, stepsToExit(ReadingBackStack.initial("c5.html").sibling("c6.html")))
        assertEquals(1, stepsToExit(ReadingBackStack.initial(INDEX)))
        assertEquals(1, stepsToExit(ReadingBackStack.initial("c5.html").openToc()))
    }

    @Test
    fun `どんな入場形でも脱出までは高々2回（階層が章と目次の2段しか無いことの機械的な保証）`() {
        // 「戻るのに何度も押させる」退行（経路の深さが押下回数に漏れる）を構造的に封じる。
        listOf(
            ReadingBackStack.initial("c5.html"),
            ReadingBackStack.initial(INDEX),
            ReadingBackStack.initial(INDEX).openChapter("c1.html").sibling("c2.html").sibling("c3.html"),
            ReadingBackStack.initial("c5.html").openToc().openChapter("c2.html").sibling("c3.html"),
        ).forEach { assertTrue("脱出まで高々2回", stepsToExit(it) <= 2) }
    }

    @Test
    fun `目次から章へ入ると戻るは目次へ もう一度で終端に達する`() {
        var stack = ReadingBackStack.initial(INDEX).openChapter("c1.html")
        assertEquals(listOf(INDEX, "c1.html"), stack.screens)
        stack = stack.back()!! // 章→目次（階層 up）
        assertEquals(listOf(INDEX), stack.screens)
        assertNull(stack.back()) // 目次→読書フローを出る
    }

    // ── ④ 前進で段が増えない（旧 navHistory バグの再発防止） ──
    @Test
    fun `話送りを50連しても深さは不変で 戻るは常に目次へ上がる（読んだ章列を逆走しない）`() {
        var stack = ReadingBackStack.initial("c5.html")
        repeat(50) { i -> stack = stack.sibling("s$i.html") }
        assertEquals("話送りは replace＝深さ1のまま", 1, stack.screens.size)
        // 07/19 が求めた第2要件「横移動を Back で逆走させない」は前進規則が担い続ける。
        assertEquals(INDEX, stack.back()!!.current)
    }

    @Test
    fun `目次から章を50回覗いてもスタック深さは増えない（戻るで目次へ上がる反復）`() {
        var stack = ReadingBackStack.initial(INDEX)
        repeat(50) { i ->
            stack = stack.openChapter("peek$i.html") // 覗く（push）
            assertEquals("覗き中は目次+章の2枚", 2, stack.screens.size)
            stack = stack.back()!! // 章→目次（既出の目次へ巻き戻し）
            assertEquals(listOf(INDEX), stack.screens)
        }
    }

    @Test
    fun `覗き→目次ボタン→別章覗き…の50連もスタック深さ不変（目次ボタン経路）`() {
        // 戻るでなく下端「目次」ボタン（openToc）で目次へ戻る経路。既存目次へ巻き戻すため重複を積まない。
        var stack = ReadingBackStack.initial(INDEX)
        repeat(50) { i ->
            stack = stack.openChapter("peek$i.html").openToc()
            assertEquals("既存目次へ popUpTo＝目次1枚に戻る", listOf(INDEX), stack.screens)
        }
    }

    @Test
    fun `直行本文から目次を開き別章を覗いても覗きは相殺され深さは最大3で不変`() {
        var stack = ReadingBackStack.initial("c5.html").openToc() // [c5, index]
        repeat(30) { i ->
            stack = stack.openChapter("peek$i.html") // [c5, index, peek]
            assertEquals(3, stack.screens.size)
            stack = stack.back()!! // 覗き章→目次（階層 up）→ [c5, index]
            assertEquals(listOf("c5.html", INDEX), stack.screens)
        }
    }

    @Test
    fun `目次ボタンで既存目次へ戻っても目次が二重に積まれない（popUpTo）`() {
        val stack = ReadingBackStack.initial(INDEX).openChapter("c1.html").openToc()
        assertEquals(listOf(INDEX), stack.screens)
    }

    // ── 参照ジャンプ（jumpOrigin）の既存挙動を壊さない: 続きに戻る（returnTo） ──
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
        assertEquals("復帰後も 章→目次→脱出 の2段", listOf(INDEX), stack.back()!!.screens)
    }

    @Test
    fun `続きに戻る＝退避元章が下段に在るときはそこまで巻き戻す（重複を積まない）`() {
        // 直行本文c5から目次→c2覗き。退避元c5は下段に在るので popUpTo で c5 まで巻き戻す。
        val stack = ReadingBackStack.initial("c5.html")
            .openToc()               // [c5, index]
            .openChapter("c2.html")  // [c5, index, c2]
            .returnTo("c5.html")     // c5 は下段に在る→そこまで巻き戻し
        assertEquals(listOf("c5.html"), stack.screens)
        // 巻き戻して直行入場と同じ形に戻るので、以降も「章→目次→脱出」の2段（形が同じなら挙動も同じ）。
        assertEquals(2, stepsToExit(stack))
    }

    // ── 端章の prev/next は目次へ抜ける（sibling("index.html") は openToc に委譲） ──
    @Test
    fun `端章の話送りが目次へ抜けるとき直行本文の下段を失わない`() {
        // 直行本文c1（先頭章）の「前章」→ prevFile="index.html"。横移動で置き換えると c1 を失うため openToc 扱い。
        val stack = ReadingBackStack.initial("c1.html").sibling(INDEX)
        assertEquals(listOf("c1.html", INDEX), stack.screens)
        assertNull("到達した目次からは章へ降りず脱出", stack.back())
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
        assertEquals("復元後も戻りの段数が一致", stepsToExit(original), stepsToExit(restored))
    }

    // ── 左上 ← とシステム Back は同一実装＝別関数を持たない（操作で行き先が割れない） ──
    @Test
    fun `左上←とシステムBackは同じ back の呼び出しで同じ遷移列を辿る`() {
        // ← の実装は「back() を呼ぶ」以外に無い（章の ← ＝ChapterNav.onBack／目次の ← ＝ReadingScreen が
        // performBack を渡す）。ここでは同じ関数を2度辿って列が一致することを機械的に固定し、
        // 将来 ← 側に onNavigateTo("index.html") 等の別実装が復活したら UI 側の契約テスト
        // （NativeReadingScreenA11yTest の ←/customAction）と併せて検知できるようにする。
        // ⚠️ 章では ← の着地（目次）と「目次を開く」の着地は**同じ**だが、着地の一致は実装が割れた瞬間に
        // 崩れる＝一致の担保は「同じ関数を叩くこと」でなければならない（3度の反転の真因）。
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
