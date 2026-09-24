package com.novelreader.typeset

import com.novelreader.model.TextSegment
import com.novelreader.perf.TypesetWorkProbe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 改善 A（組版の composition 離脱＋章スコープキャッシュ）の**効果を回数で固定する**回帰テスト。
 *
 * なぜ端末計測でなくここで固定できるのか: 2026-08-25 の計測で確定した問題は「同じ版面を何回組み直すか」
 * であって速さではない（`tools/measure_typeset_work.sh` 冒頭の「なぜ ms でなく回数か」）。回数は
 * composition の起こり方だけで決まるので、[ChapterTypesetStore] を純 JVM で同じ順に叩けば実機と
 * **同じ数字**が出る。よって A の合否は端末を使わずここで判定できる。
 *
 * 実測ベースライン（改善前・フォント全振り1ドラッグ）:
 * `typeset()` 55回 / [PositionedGlyph] 1,845個 / Paint 実測 1,845回。
 * 分母は「離散10値 × 可視段落 5〜6」。本テストは可視6段落・10値で写し取る（＝旧構造なら 60回）。
 */
class ChapterTypesetStoreTest {

    // 実測の分母をそのまま写す（文字サイズは 14..24 / steps=9 ＝1ドラッグで必ず10回の値変化）。
    private val visibleParagraphs = 6
    private val fontDragSteps = 10

    private val baseFontPx = 48f

    private fun paragraph(index: Int): List<TextSegment> =
        listOf(TextSegment.Plain("吾輩は猫である。名前はまだ無い。どこで生れたか頓と見当がつかぬ。$index"))

    private fun constraintsAt(fontSizePx: Float) = TypesetConstraints(
        columnHeightPx = 600f,
        fontSizePx = fontSizePx,
        rubyFontSizePx = fontSizePx / 2f,
        columnAdvancePx = fontSizePx * 2.4f,
        indentFirstColumn = false,
    )

    private fun request(store: ChapterTypesetStore, index: Int, constraints: TypesetConstraints) =
        TypesetRequest(TypesetSlotId(index), paragraph(index), constraints)
            .also { store.slot(it.id) } // スロットを先に起こしておく（同一性の検証で使う）

    /** 「何回組んだか」を貯蔵庫の外から独立に数える器（グローバルな [TypesetWorkProbe] に依存しない）。 */
    private class CountingTypesetter(
        private val onTypeset: (Int) -> Unit = {},
        private val delegate: VerticalTypesetter = DefaultVerticalTypesetter(FakeMonospaceMetrics()),
    ) : VerticalTypesetter {
        var calls = 0
            private set
        var glyphs = 0
            private set

        override fun typeset(segments: List<TextSegment>, constraints: TypesetConstraints): ParagraphLayout {
            calls++
            val layout = delegate.typeset(segments, constraints)
            glyphs += layout.glyphs.size
            onTypeset(calls)
            return layout
        }
    }

    @Test
    fun `列が視界を出入りしても組み直さない（キャッシュ寿命が item 寿命から章の寿命へ移った）`() {
        val typesetter = CountingTypesetter()
        val store = ChapterTypesetStore(typesetter)
        val constraints = constraintsAt(baseFontPx)

        repeat(visibleParagraphs) { store.slot(TypesetSlotId(it)).resolve(paragraph(it), constraints) }
        assertEquals("初回表示は段落数ぶんだけ組む", visibleParagraphs, typesetter.calls)

        // LazyRow の item が破棄→再生成される（横スクロールで列が出入りする）を3往復ぶん模す。
        // 旧構造では item と一緒に remember が消えるため、ここで毎回 6回ずつ積み増していた。
        repeat(3) {
            repeat(visibleParagraphs) { store.slot(TypesetSlotId(it)).resolve(paragraph(it), constraints) }
        }
        assertEquals("再入場では1回も組み直さない", visibleParagraphs, typesetter.calls)
    }

    @Test
    fun `フォントスライダーのドラッグは composition 段の組版を1回も増やさない`() {
        val typesetter = CountingTypesetter()
        val store = ChapterTypesetStore(typesetter)
        val initial = constraintsAt(baseFontPx)

        repeat(visibleParagraphs) { store.slot(TypesetSlotId(it)).resolve(paragraph(it), initial) }
        val afterFirstPaint = typesetter.calls
        val glyphsAfterFirstPaint = typesetter.glyphs

        // 端から端まで1ドラッグ＝離散10値。旧構造では 10×6＝60回が UI スレッドの composition 段で走った。
        repeat(fontDragSteps) { step ->
            val dragged = constraintsAt(baseFontPx + step + 1)
            repeat(visibleParagraphs) { index ->
                val result = store.slot(TypesetSlotId(index)).resolve(paragraph(index), dragged)
                // 据え置き＝**古い版面をそのまま返す**（新しい版面は背景が差し替える）。
                // 寸法も束で返るので、描画側が旧座標に新しい字寸を打って字面を重ねることがない。
                assertEquals(baseFontPx, result.constraints.fontSizePx, 0f)
            }
        }

        assertEquals("ドラッグ中の同期組版は 0 回", afterFirstPaint, typesetter.calls)
        assertEquals("置き直したグリフも 0 個", glyphsAfterFirstPaint, typesetter.glyphs)
    }

    @Test
    fun `先行組版が済んだスロットは composition 段で組み直さない`() = runTest {
        val typesetter = CountingTypesetter()
        val store = ChapterTypesetStore(typesetter)
        val constraints = constraintsAt(baseFontPx)

        store.typeset((0 until visibleParagraphs).map { request(store, it, constraints) })
        assertEquals(visibleParagraphs, typesetter.calls)

        repeat(visibleParagraphs) { store.slot(TypesetSlotId(it)).resolve(paragraph(it), constraints) }
        assertEquals("先行組版が当たれば同期経路は通らない", visibleParagraphs, typesetter.calls)
    }

    @Test
    fun `同期が先に確保したスロットへ背景は書き込まない`() = runTest {
        val typesetter = CountingTypesetter()
        val store = ChapterTypesetStore(typesetter)
        val constraints = constraintsAt(baseFontPx)
        val slot = store.slot(TypesetSlotId(0))

        slot.resolve(paragraph(0), constraints)
        store.typeset(listOf(request(store, 0, constraints)))

        assertEquals("同期と背景で二重に組まない", 1, typesetter.calls)
        // 購読点（スナップショット状態）が触られていないこと。ここを素直に上書きすると、見た目が
        // 1ピクセルも変わらないのに item が1回よけいに再コンポーズされ、しかもそれが背景スレッドの
        // 都合で起きるので描画の確定タイミングが実行ごとにぶれる（golden 撮影が順番依存で落ちる形）。
        assertNull("同じ版面で state を書き換えない", slot.fresh.value)
    }

    @Test
    fun `composition が消えた後は組まない・公開しない`() = runTest {
        val typesetter = CountingTypesetter()
        val store = ChapterTypesetStore(typesetter)
        val constraints = constraintsAt(baseFontPx)
        val slot = store.slot(TypesetSlotId(0))

        // 章が切り替わる・画面を離れる＝store を覚えていた composition が消える。
        store.onForgotten()
        store.typeset(listOf(request(store, 0, constraints)))

        // 背景の取り消しは協調的で、走り切った1件ぶんが破棄後に公開されうる。それが Compose の
        // グローバルスナップショットへの書き込みになるため、1つの JVM で composition を張っては
        // 捨てる単体テスト環境では**次のテストへ漏れる**。寿命を RememberObserver に結び直して断つ。
        assertEquals("閉じた後は1件も組まない", 0, typesetter.calls)
        assertNull("閉じた後は1件も公開しない", slot.fresh.value)
    }

    @Test
    fun `背景組版は取り消せる（ドラッグの途中値は残りを捨てられる）`() = runTest {
        var running: Job? = null
        // 3件組んだところで「次の設定値が来た」を模して取り消す。
        val typesetter = CountingTypesetter(onTypeset = { calls -> if (calls == 3) running?.cancel() })
        val store = ChapterTypesetStore(typesetter)
        val constraints = constraintsAt(baseFontPx)
        val requests = (0 until 20).map { request(store, it, constraints) }

        // StandardTestDispatcher なので launch の本体は join まで走らない＝running の代入が先に済む。
        val job = launch { store.typeset(requests) }
        running = job
        job.join()

        // ここが改善案 B（ドラッグ中の再組版抑制）を別仕掛けにせず閉じられる根拠。
        // ⚠️ 取り消しが効くのは「背景が値の到着に追い越されたとき」だけ＝速い端末では10値ぶん完走しうる。
        assertEquals("取り消し後の残りは組まない", 3, typesetter.calls)
    }

    @Test
    fun `プローブは composition 段の組版だけを v_typeset_comp に数える`() = runTest {
        TypesetWorkProbe.reset()
        TypesetWorkProbe.enabled = true
        try {
            val store = ChapterTypesetStore(DefaultVerticalTypesetter(FakeMonospaceMetrics()))
            val constraints = constraintsAt(baseFontPx)

            store.slot(TypesetSlotId(1)).resolve(paragraph(1), constraints) // composition 段
            store.typeset(listOf(request(store, 2, constraints))) // 背景

            val snapshot = TypesetWorkProbe.snapshot()
            assertTrue(snapshot, snapshot.contains(" v_typeset=2 "))
            assertTrue(snapshot, snapshot.contains(" v_typeset_comp=1 "))
        } finally {
            TypesetWorkProbe.enabled = false
            TypesetWorkProbe.reset()
        }
    }

    @Test
    fun `予算を超えたら遠い版面だけ手放し、スロットの同一性は保つ`() = runTest {
        val typesetter = CountingTypesetter()
        // 予算 1 グリフ＝「依頼窓のぶんしか抱えない」極端値（保護対象は依頼中のスロットだけ）。
        val store = ChapterTypesetStore(typesetter, glyphBudget = 1)
        val constraints = constraintsAt(baseFontPx)

        store.typeset((0 until 10).map { request(store, it, constraints) })
        val residentAfterFirstWindow = store.residentGlyphCount()
        val slotZeroBefore = store.slot(TypesetSlotId(0))

        // 窓が遠くへ移る（読み進めた）。前の窓のスロットは保護対象から外れる。
        store.typeset((100 until 104).map { request(store, it, constraints) })

        assertTrue("窓が移れば古い版面は手放される", store.residentGlyphCount() < residentAfterFirstWindow)
        // 手放すのは**版面だけ**。スロット（＝画面が購読しているオブジェクト）は同じものが返る——
        // ここが別インスタンスになると、背景の結果が誰も見ていない側へ公開されて更新が永久に届かない。
        assertSame(slotZeroBefore, store.slot(TypesetSlotId(0)))

        val callsBefore = typesetter.calls
        store.slot(TypesetSlotId(0)).resolve(paragraph(0), constraints)
        assertEquals("手放したスロットは要求されたら組み直す", callsBefore + 1, typesetter.calls)
    }
}
