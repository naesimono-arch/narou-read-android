package com.novelreader.typeset

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.novelreader.model.TextSegment
import com.novelreader.perf.TypesetWorkProbe
import kotlinx.coroutines.yield

/**
 * 章内で1つの組版結果を指すID。
 *
 * [itemIndex] は **LazyRow の item 位置そのもの**（0＝章見出し、1..＝段落）。先行組版の窓を
 * `LazyListState.layoutInfo.visibleItemsInfo` の index から直に作れるよう、独自採番せず item index に
 * 合わせてある。
 * [subIndex] は item 内の通し番号で、-1＝item 本体（通常段落・章見出し）、0＝前後書きブロックのラベル、
 * 1+j＝ブロック内 j 番目の段落。
 */
data class TypesetSlotId(val itemIndex: Int, val subIndex: Int = -1)

/**
 * 組版結果と、**それを組んだときの制約**の対。
 *
 * なぜ制約を同梱するか: 設定変更（フォント/行間スライダー）の直後は、新しい寸法の組版が背景で
 * 出来上がるまでの数フレーム、意図的に「古い寸法で組んだ版面」を描き続ける（UI スレッドを止めない
 * ための取引）。このとき描画側 `VerticalParagraph` に**今の** fontSizePx を渡すと、旧座標に新サイズの
 * 字を打つことになり字面が重なる。版面と寸法は必ず束で運ぶ。
 */
data class TypesetResult(val layout: ParagraphLayout, val constraints: TypesetConstraints)

/** 背景組版の依頼1件。 */
data class TypesetRequest(
    val id: TypesetSlotId,
    val segments: List<TextSegment>,
    val constraints: TypesetConstraints,
)

/**
 * 章1つぶんの縦組み版面を保持し、**再組版を composition 段の外へ出す**ための貯蔵庫（改善 A の本体）。
 *
 * ## なぜ composition 段で組むと不味いのか（この型が在る理由）
 * 縦書きは Compose のテキストエンジンを使わず自前で組む（ADR 0020＝公式 text-vertical までのつなぎ）。
 * 改善前の形は段落 item ごとの
 * `BoxWithConstraints { remember(paragraph, 列高, 文字寸, 列送り) { typesetter.typeset(...) } }` で、
 * 次の2つが同時に成り立っていた:
 * 1. 組版が **composition 段・UI スレッド**で同期に走る（`remember` の計算ブロック）。
 * 2. その `remember` の寿命が **LazyRow の item 寿命**と同じ＝列が視界を出入りするたびに作り直す。
 *
 * 結果として、(a) 横スクロールで列が入るたびに新規段落の全組版が composition に乗り、
 * (b) 文字サイズ/行間スライダーは離散値ごとに onChange を撃つ（14..24 / steps=9 ＝**1ドラッグで必ず10回**）ので
 * 「毎値 × 可視段落数」の再組版が UI スレッドで走る。2026-08-25 の実測（端末非依存の回数指標
 * [TypesetWorkProbe]）でフォント全振り1ドラッグ＝`typeset()` 55回・[PositionedGlyph] 1,845個・
 * Paint 実測 1,845回。**同じ版面を10回組み直している**のが構造の姿で、速い端末では見えず遅い端末でだけ出る。
 *
 * ## この型が変えたもの＝「キャッシュの寿命」
 * 版面の寿命を **LazyRow の item 寿命 → 章の寿命**（このインスタンスの寿命）へ移す。
 * なぜ章スコープが正しい寿命か: 版面を決める入力は〈段落の中身・列高・文字寸・列送り〉で、
 * どれも **item が視界に居るかどうかとは無関係**。item 寿命に紐づけていたのは `remember` を使った
 * ことによる副産物であって、設計上の必然ではなかった。逆に章より長く持つのも誤り＝段落の同一性は
 * 章内の位置（[TypesetSlotId]）でしか与えられておらず、別の章では同じ index が別の段落を指す。
 *
 * ## 3つの経路（どれが composition 段に残るか）
 * - **背景**（[typeset]）: `Dispatchers.Default` から呼ぶ。可視 item の前後を先行して組む。ここが主経路。
 * - **据え置き**: 設定が変わった直後、まだ新しい版面が無い間は**古い版面をそのまま返す**（[TypesetSlot.resolve]）。
 *   ここで同期に組み直さないことがスライダー 55回 → 0回 の実体。
 * - **同期**（[typesetInComposition]）: そのスロットの版面が**一度も無い**ときだけ。
 *   なぜ残すか＝版面が無い item は幅0で置かれ、(1) 章頭で全段落が一斉に視界へ入ってから実寸へ跳ね、
 *   (2) 既読位置の復元 `scrollToItem(index, offset)` の offset が幅0の item に対して解決され着地がずれる。
 *   未再現の報告バグ「章遷移で描画が上部にジャンプ」と同じ形の事故を自分で作ることになるため、
 *   **初回だけは今までどおり同期**に組んで寸法を確定させる（＝章送りの回数は下がらない。下がるのは反復ぶん）。
 *
 * ## スレッド
 * [typeset] は背景スレッド、[TypesetSlot.resolve] は composition（UI スレッド）から呼ばれる。
 * 版面の受け渡しは Compose のスナップショット状態1個（[TypesetSlot.fresh]）で、背景からの書き込みは
 * [Snapshot.withMutableSnapshot] でくるむ。購読点を段落ごとに分けてあるので、ある段落の版面が届いても
 * 再コンポーズされるのはその段落の item だけ（`SnapshotStateMap` を1本使うと全 item が巻き込まれる）。
 */
@Stable
class ChapterTypesetStore(
    private val typesetter: VerticalTypesetter,
    /**
     * 版面を同時に抱えていられるグリフ数の上限。超えたぶんは**視界から遠い順に**手放す。
     * 既定 12,000 は [PositionedGlyph] 換算でおよそ 1MB 強＝長い章1つを丸ごと抱えても収まる水準で、
     * 通常の読書では一度も効かない安全網。回数を下げる代わりに常駐メモリが青天井、を避けるために置く。
     */
    private val glyphBudget: Int = DEFAULT_GLYPH_BUDGET,
) : RememberObserver {

    private val lock = Any()

    /**
     * 所属する composition が消えたか。**立った後は二度と版面を公開しない**。
     *
     * なぜ要るか（実測で踏んだ事故）: 背景の組版は取り消しが協調的で、`typesetter.typeset()` の実行中に
     * 取り消されても**その1件ぶんは走り切ってから**次の中断点に来る。この「1件ぶん」の公開先は Compose の
     * スナップショット状態なので、composition が破棄された後に別スレッドから書くと**破棄後のグローバル
     * スナップショットへ書き込む**ことになる。単体テストのように1つの JVM で composition を張っては
     * 捨てるのを繰り返す環境では、これが**次のテストへ漏れる**（Robolectric の
     * VerticalChapterContentScreenshotTest で、同じ内容の LIGHT が通って**後から走る DARK だけ**が
     * 落ちる、という順番依存の形で出た。LIGHT と DARK は版面が同一なので、内容起因なら両方落ちる）。
     * 寿命を [RememberObserver] に結び直して、**書ける期間＝store を覚えている composition が在る期間**に
     * 構造的に一致させる。
     */
    @Volatile
    private var closed = false

    /** id → スロット。**一度作ったら捨てない**（下の [TypesetSlot] KDoc の同一性の理由）。 */
    private val slots = HashMap<TypesetSlotId, TypesetSlot>()

    /** 版面を抱えているスロットのアクセス順（accessOrder=true＝先頭が最も古い）。値は抱えているグリフ数。 */
    private val resident = LinkedHashMap<TypesetSlotId, Int>(16, 0.75f, true)

    private var residentGlyphs = 0

    /** [id] のスロットを得る。composition 側は `remember` して持ち続ける（毎フレーム引き直さない）。 */
    fun slot(id: TypesetSlotId): TypesetSlot = synchronized(lock) {
        slots.getOrPut(id) { TypesetSlot(id, this) }
    }

    /**
     * 依頼された順に、まだ版面を持たないものだけを組んで公開する（**背景スレッドから呼ぶ契約**）。
     *
     * 1件ごとに [yield] を挟むのは協調的な取り消し点を作るため。呼び出し側は `collectLatest` で包むので、
     * ドラッグ中に次の値が届くと**走行中の残りは捨てられる**——改善案 B（ドラッグ中の再組版抑制）を
     * 別の仕掛けとして作らずに済むのはこの1点による。⚠️ 取り消しが効くのは「背景が値の到着に
     * 追い越されたとき」だけ＝速い端末では10値ぶん全部が完走しうる。**回数の下限が1ラウンドになる**
     * のであって、常に1ラウンドになるのではない（composition 段の回数だけは端末によらず 0 になる）。
     */
    suspend fun typeset(requests: List<TypesetRequest>) {
        for (request in requests) {
            yield()
            if (closed) return
            val slot = slot(request.id)
            if (slot.hasLayoutFor(request.constraints)) continue
            val layout = typesetter.typeset(request.segments, request.constraints)
            publish(slot, TypesetResult(layout, request.constraints))
        }
        // いま依頼中のスロット（呼び出し側の契約で**可視範囲を必ず含む**）は手放さない
        // ＝目の前の段落の版面が予算都合で消えることはない。
        trim(requests.mapTo(HashSet()) { it.id })
    }

    /**
     * 背景の組版結果を公開する。**すでに同じ寸法の版面を持っていたら何もしない**。
     *
     * なぜ「持っていたら何もしない」が要るか: 初回表示では composition 側の同期組版（[typesetInComposition]）と
     * 背景が同じスロットを同時に狙いうる。素直に上書きすると、同じ内容の版面で state を書き換える＝
     * **見た目が1ピクセルも変わらないのに再コンポーズが1回増える**。しかもそれが背景スレッドの都合で
     * いつ起きるか決まらないため、描画の確定タイミングが実行ごとにぶれる（golden 撮影のような
     * 「idle になったところを撮る」検証がぶれの直撃を受ける）。ここで弾くと、どちらが先に走っても
     * **結果の版面は同一・item のコンポーズは1回**になり、順序に依らない。
     */
    private fun publish(slot: TypesetSlot, result: TypesetResult) {
        synchronized(lock) {
            if (closed || slot.hasLayoutFor(result.constraints)) return
            Snapshot.withMutableSnapshot { slot.publish(result) }
            noteResident(slot.id, result.layout.glyphs.size)
        }
    }

    private fun trim(protect: Set<TypesetSlotId>) {
        synchronized(lock) {
            // 手放しもスナップショット書き込み＝[closed] の後は行わない（publish と同じ理由）。
            if (closed) return
            val dropped = collectOverBudget(protect)
            if (dropped.isNotEmpty()) Snapshot.withMutableSnapshot { dropped.forEach { it.release() } }
        }
    }

    override fun onRemembered() = Unit

    override fun onForgotten() {
        closed = true
    }

    override fun onAbandoned() {
        closed = true
    }

    /**
     * 版面が一度も無いスロットを、**その場（composition 段）で**組む。
     * 呼ぶのは [TypesetSlot.resolve] だけ＝この関数の呼び出し回数がそのまま「composition に残った組版」の
     * 回数になり、[TypesetWorkProbe] の `v_typeset_comp` として端末非依存に数えられる。
     */
    internal fun typesetInComposition(
        slot: TypesetSlot,
        segments: List<TextSegment>,
        constraints: TypesetConstraints,
    ): TypesetResult {
        val layout = typesetter.typeset(segments, constraints)
        TypesetWorkProbe.onVerticalTypesetInComposition(layout.glyphs.size)
        val result = TypesetResult(layout, constraints)
        // なぜスナップショット状態でなく素のフィールドへ書くか: ここは composition の**最中**で、
        // 直前に fresh を読んだスコープへ書き戻すと同じフレームでそのスコープが無効化され、
        // 段落ごとに1回よけいな再コンポーズが起きる（cross-phase back-write）。素のフィールドなら
        // 通知が飛ばず、次の再コンポーズでそのまま拾われる。
        slot.warm = result
        synchronized(lock) { noteResident(slot.id, layout.glyphs.size) }
        return result
    }

    /** 予算超過ぶんを古い順に選ぶ（[protect]＝いま依頼中＝可視を含む窓は除外）。呼び出しは錠の中。 */
    private fun collectOverBudget(protect: Set<TypesetSlotId>): List<TypesetSlot> {
        if (residentGlyphs <= glyphBudget) return emptyList()
        val dropped = ArrayList<TypesetSlot>()
        val iterator = resident.entries.iterator()
        while (residentGlyphs > glyphBudget && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in protect) continue
            iterator.remove()
            residentGlyphs -= entry.value
            slots[entry.key]?.let(dropped::add)
        }
        return dropped
    }

    private fun noteResident(id: TypesetSlotId, glyphs: Int) {
        val previous = resident.put(id, glyphs)
        residentGlyphs += glyphs - (previous ?: 0)
    }

    /** 常駐しているグリフ総数（予算の効きを固定するテスト用）。 */
    internal fun residentGlyphCount(): Int = synchronized(lock) { residentGlyphs }

    companion object {
        private const val DEFAULT_GLYPH_BUDGET = 12_000
    }
}

/**
 * 章内の1スロット（段落・章見出し・ブロックのラベル/内側段落）ぶんの版面入れ。
 *
 * なぜ [ChapterTypesetStore] から**取り除かれない**か: composition 側はこのオブジェクトを `remember` して
 * 持ち続ける。予算超過で版面を手放すときにスロットごと地図から消すと、背景側が次に作る**別インスタンス**へ
 * 結果を公開してしまい、画面が購読しているのは古いインスタンス＝更新が永久に届かない、という静かな
 * 取りこぼしになる。版面（重い側）だけを手放し、同一性（軽い側）は章の間ずっと保つ。
 */
@Stable
class TypesetSlot internal constructor(
    val id: TypesetSlotId,
    private val store: ChapterTypesetStore,
) {

    /** 背景が公開した最新の版面＝**画面の購読点**。 */
    internal val fresh: MutableState<TypesetResult?> = mutableStateOf(null)

    /** composition 段で同期に組んだ版面（購読しない）。背景の版面が届いた時点で捨てる。 */
    @Volatile
    internal var warm: TypesetResult? = null

    /**
     * 今このスロットに描かせる版面を返す（**composition から呼ぶ契約**）。
     *
     * 版面が[constraints]と食い違っていても、在るならそれを返す＝**設定変更の直後は古い版面のまま描く**。
     * これが「スライダーの毎値で組み直さない」の実装そのもので、新しい版面は背景が数フレームのうちに
     * 差し替える。⚠️ 呼び出し側は描画に [TypesetResult.constraints] の寸法を使うこと（今の寸法ではない）。
     */
    fun resolve(segments: List<TextSegment>, constraints: TypesetConstraints): TypesetResult {
        fresh.value?.let { return it }
        warm?.let { return it }
        return store.typesetInComposition(this, segments, constraints)
    }

    internal fun hasLayoutFor(constraints: TypesetConstraints): Boolean =
        fresh.value?.constraints == constraints || warm?.constraints == constraints

    internal fun publish(result: TypesetResult) {
        warm = null
        fresh.value = result
    }

    internal fun release() {
        warm = null
        fresh.value = null
    }
}
