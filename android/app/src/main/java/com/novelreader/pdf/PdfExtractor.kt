package com.novelreader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 表紙(1ページ目)から得た書籍メタ情報。 */
data class BookMeta(val title: String, val author: String)

/**
 * PDFBox-android の CID→Unicode 出力を pdfminer（移植のオラクル）に揃える 1 文字正規化。
 *
 * なぜ: PDFBox-android は一部グリフを Adobe-Japan1/pdfminer と別コードポイントへ写す。放置すると
 * title・本文・章題が実機とオラクルでズレ、ゴールデン回帰がグリフ差だけで不一致になる。1:1 で対応が
 * 付くものをオラクル側へ寄せる（N6169DZ 章題ドリフト・task_diary #35）。写像:
 *   - FF5E FULLWIDTH TILDE → 301C WAVE DASH（有名な「波ダッシュ問題」の CMap 版。なろうでは波ダッシュが
 *     正で FF5E の正当用例はほぼ無く低リスク）
 *   - FF0D FULLWIDTH HYPHEN-MINUS → 2212 MINUS SIGN（章題6件）
 *   - 2191/2193 UP/DOWN ARROW → 2190/2192 LEFT/RIGHT ARROW（PDFBox が矢印を 90° 回転誤読するのを補正・章題3件）
 *
 * ⚠ FF0D→2212 は body にも同グリフが出れば正規化され、短中編の body_sha256（現状 pdfminer と完全一致）を
 *   破壊しうる＝pdfminer が本文では FF0D のまま出す証拠になる。実機ゲート(PdfExtractorDeviceSpikeTest)で
 *   検証し、短中編 body_sha256 が壊れたら FF0D→2212 は取り下げる（golden から離れる写像は入れない）。
 *   矢印は本文に出にくく低リスク。
 *
 * 各写像は個別 indexOf ガードで包み、対象を含まない大多数のグリフでは新規文字列を確保しない
 * （processTextPosition は 1 グリフ毎＝超長編で数百万回走るホットパス。PdfExtractorTest の assertSame 契約）。
 * 見た目が酷似する文字が多いため取り違え防止にエスケープで明示する。
 */
internal fun normalizeGlyphUnicode(s: String): String {
    var r = s
    if (r.indexOf('\uFF5E') >= 0) r = r.replace('\uFF5E', '\u301C')  // FULLWIDTH TILDE → WAVE DASH
    if (r.indexOf('\uFF0D') >= 0) r = r.replace('\uFF0D', '\u2212')  // FULLWIDTH HYPHEN-MINUS → MINUS SIGN
    if (r.indexOf('\u2191') >= 0) r = r.replace('\u2191', '\u2190')  // UPWARDS → LEFTWARDS ARROW
    if (r.indexOf('\u2193') >= 0) r = r.replace('\u2193', '\u2192')  // DOWNWARDS → RIGHTWARDS ARROW
    return r
}

/**
 * ToUnicode CMap に穴が在るグリフを、フォントの**符号化そのもの**から復号し直す（U+FFFD の真因対処）。
 *
 * なぜ成立するか: なろうの縦書き PDF が使うのは `…-UniJIS-UTF16-V/H` 系の CMap で、**文字コードが
 * UTF-16 符号単位そのもの**（CMap 名の UTF16 がその契約）。PDFBox は ToUnicode を先に引くため、
 * そこに載っていない字だけが U+FFFD へ化ける＝コード自体には正しい字が入っている。
 * 実測（N6169DZ 全ページ）: `MS-Mincho-UniJIS-UTF16-V` の code=0x25FC(◼)・0xFE0E(異体字セレクタ)が
 * FFFD 化しており、この2コードの連なりが本文中に 32 件あった。
 *
 * ⚠️ **フォント名に UTF16 を含むときだけ**適用する。この前提が無い符号化（Identity-H で CID が
 * グリフ番号のフォント等）でコードを文字扱いすると、読めない字を**別の読める字に化けさせる**＝
 * U+FFFD より悪い壊れ方をするため、前提が確認できないフォントには触らない。
 * サロゲート対は 2 コードで来るのでそのまま連結すれば正しい対になる。
 *
 * @return 復号できたら文字列・前提を満たさないなら null（呼び出し側が元の値を使う）
 */
private fun decodeFromCharacterCodes(text: TextPosition): String? {
    val fontName = text.font?.name ?: return null
    if (!fontName.contains("UTF16")) return null
    val codes = text.characterCodes ?: return null
    if (codes.isEmpty()) return null
    val sb = StringBuilder(codes.size)
    for (c in codes) {
        // UTF-16 符号単位に収まらない値＝上の前提が崩れているので、推測せず復号を諦める。
        if (c < 0 || c > 0xFFFF) return null
        sb.append(c.toInt().toChar())
    }
    return sb.toString()
}

/**
 * PDFTextStripper をカスタマイズし、processTextPosition で 1 文字ずつ座標付きで収集する。
 *
 * pdfminer 版の座標変換（top = page_height - y1, bottom = page_height - y0）に合わせ、
 * PDFBox の上原点座標から以下のように対応付ける：
 *   - x0     = getXDirAdj()                    （文字左端）
 *   - bottom = getYDirAdj()                    （上端からの距離＝文字下端）
 *   - top    = getYDirAdj() - getHeightDir()   （文字上端）
 *
 * sortByPosition は無効（既定）。並びは TextProcessor 側で座標から再構成する。
 *
 * 移植元 submission-B GlyphStripper と同一。import のみ apache→tom_roush へ差し替え
 * （TextPosition.{unicode,xDirAdj,yDirAdj,heightDir,font,fontSizeInPt} は 2.0.x 系で同名同義）。
 */
class GlyphStripper(
    // ページ開始ごとに発火する省略可のフック。
    // なぜ: 本文グリフ抽出は getText(doc) の単一走査で、そのままでは進捗を出さない。
    // load フェーズの進捗バー連動に使う（未指定＝通知なし＝オラクル/1ページ抽出など通知不要な用途）。
    // なぜ「開始済みページ数」を引数で渡さないか: 並列走行では各ストリッパが自分の担当範囲しか数えず、
    // 局所カウントは進捗として意味を持たない。数える責務は呼び出し側（[PdfExtractor.loadPages]）が持つ。
    private val onPageStart: (() -> Unit)? = null,
    /**
     * 処理するページ範囲（0 始まり・両端含む）。null＝全ページ（従来の単一走行）。
     * 並列走行で各スレッドが同一 PDF の別範囲だけを担当するために使う。
     */
    private val pageRange: IntRange? = null,
    /**
     * 1 ページ分のグリフが揃った時点で発火する省略可のフック（**ストリーミング取り出し**）。
     *
     * 指定すると [pages] へは一切溜めず、ページ完了ごとにその 1 ページ分だけを渡して即座に捨てる。
     * なぜ要るか＝全ページを溜める形（[pages]）は保持量がページ数に比例し、8,668 ページの実測で
     * 500MB 級に達して端末のヒープ天井を越える（真因）。ページを跨いで CharBox を参照する処理は
     * 一つも無いので、同時生存は 1 ページで足りる。
     *
     * ⚠️ 渡した List は復帰後に破棄される前提＝**受け取り側は参照を持ち越してはならない**
     * （持ち越すとページ数比例の保持に戻り、この経路を入れた意味が消える）。
     */
    private val onPageComplete: ((List<CharBox>) -> Unit)? = null,
) : PDFTextStripper() {

    /** 全ページ蓄積モードの結果。[onPageComplete] 指定時は常に空（溜めないのが目的のため）。 */
    val pages: MutableList<MutableList<CharBox>> = mutableListOf()
    private var current: MutableList<CharBox> = mutableListOf()

    init {
        if (pageRange != null) {
            // なぜ 0..0 か: [processPages] を差し替えると `currentPageNo`（private・setter 無し）が 0 のまま
            // になり、`processPage` 先頭の範囲判定 `currentPageNo in startPage..endPage` を通せなくなる。
            // `startBookmarkPageNumber`/`endBookmarkPageNumber` は差し替えた processPages を通らないので
            // 既定の 0 のままだが、判定は「-1 でなければ currentPageNo と比較」で 0 同士なら両方通る
            // （javap -c で確認済み・実機スパイク PdfParallelLoadPagesSpikeTest で 8,668 ページ分の等価性も確認済み）。
            startPage = 0
            endPage = 0
        }
    }

    /**
     * [pageRange] 指定時だけ、担当範囲のページだけを処理して打ち切る。範囲外は `processPage` を
     * 呼ばない＝そのページのコンテンツストリームを一切パースしない（これが並列分割の実体）。
     */
    override fun processPages(tree: PDPageTree) {
        val range = pageRange ?: return super.processPages(tree)
        var index = 0
        for (page in tree) {
            if (index > range.last) break
            if (index >= range.first) processPage(page)
            index++
        }
    }

    /**
     * ストリーミング取り出し時だけ、揃った 1 ページ分を渡して即座に手放す。
     * `endPage` は PDFTextStripper が「そのページの全グリフを processTextPosition へ流し終えた後」に
     * 呼ぶフック＝ここが 1 ページ完成の唯一の確定点。
     */
    override fun endPage(page: PDPage) {
        val sink = onPageComplete
        if (sink != null) {
            sink(current)
            // 参照を切って GC 可能にする（次ページの startPage で作り直す）。
            current = mutableListOf()
        }
        super.endPage(page)
    }

    override fun startPage(page: PDPage) {
        current = mutableListOf()
        // ストリーミング取り出し時は溜めない（溜めるとページ数比例の保持に戻る＝真因そのもの）。
        if (onPageComplete == null) pages.add(current)
        // getText の走査中にページ毎に発火するため、支配的コストの本文抽出中も進捗バーを前進させられる
        // （handover の UX ギャップ対策）。並列走行ではキャンセル確認もこの経路に相乗りする。
        onPageStart?.invoke()
        super.startPage(page)
    }

    override fun processTextPosition(text: TextPosition) {
        val raw = text.unicode
        // ToUnicode に穴が在るグリフだけ、フォントの符号化から直接復号し直す（U+FFFD 対策）。
        val decoded = if (raw == null || raw.isEmpty() || raw.indexOf('�') >= 0) {
            decodeFromCharacterCodes(text) ?: raw
        } else {
            raw
        }
        if (decoded.isNullOrEmpty()) return
        // PDFBox-android の CID→Unicode を pdfminer(オラクル)へ揃える（波ダッシュ等・task_diary #35）。
        val s = normalizeGlyphUnicode(decoded)

        val bottom = text.yDirAdj.toDouble()
        val top = bottom - text.heightDir.toDouble()
        current.add(
            CharBox(
                text = s,
                fontName = text.font?.name,
                size = text.fontSizeInPt.toDouble(),
                x0 = text.xDirAdj.toDouble(),
                top = top,
                bottom = bottom,
            )
        )
    }
}

/**
 * 全ページのグリフを **1 ページずつ** 消費側へ渡す供給源。
 *
 * なぜ型として切るか（真因対処の中核）: 旧経路は [PdfExtractor.loadPages] の戻り値
 * `List<List<CharBox>>` ＝**全ページ分の CharBox を同時生存**させる形で、保持量がページ数に比例した
 * （N6169DZ 8,668 ページで Dalvik ピーク実測 514MB ＝約 59KB/ページ。192MB 端末では確実に、
 * 512MB 級の実機でも使い切る寸前）。だがページを跨いで CharBox を参照する処理は**一つも無い**
 * ——rules 検出も段落化もページ内で閉じる——ので、同時生存は 1 ページに畳める。
 *
 * [forEachPage] は**複数回呼べる**。rules 検出（bodySize→列/ルビ）と本文整形は「前段の結果が要る」
 * 依存があり 1 走査に畳めないため、走査を繰り返せることを型の契約にする。
 *
 * ⚠️ consume へ渡した List は**復帰後に破棄されうる**。受け取り側は参照を持ち越してはならない。
 */
internal fun interface PageGlyphSource {
    /** ページ 0..n-1 を昇順に [consume] へ渡す。ページ順は rules 検出・段落縫合の前提。 */
    fun forEachPage(consume: (pageIndex: Int, chars: List<CharBox>) -> Unit)
}

/**
 * 既に materialize 済みのページ列をそのまま流す供給源。
 * 走査ごとの再パースが無い＝**ヒープに余裕がある端末での高速経路**（従来と同じ 1 パース）。
 */
internal class MaterializedPageSource(private val pages: List<List<CharBox>>) : PageGlyphSource {
    override fun forEachPage(consume: (Int, List<CharBox>) -> Unit) {
        for (i in pages.indices) consume(i, pages[i])
    }
}

/**
 * 走査のたびに PDF を読み直す供給源。保持量は常に 1 ページ分＝**ページ数に依存しない**。
 *
 * 代償は走査回数ぶんの再パース（PDFBox のパースは抽出コストの 85〜91%）。よって
 * [PdfExtractor.canMaterializeAllPages] が「溜めても安全」と判断した端末では使わない。
 * 同一 [PDDocument] を再利用するのでフォント/CMap の解決結果はドキュメント内キャッシュに乗る。
 */
internal class ReparsingPageSource(
    private val doc: PDDocument,
    private val onPageLoaded: (() -> Unit)? = null,
) : PageGlyphSource {
    override fun forEachPage(consume: (Int, List<CharBox>) -> Unit) {
        var index = 0
        GlyphStripper(
            onPageComplete = { chars ->
                onPageLoaded?.invoke()
                consume(index, chars)
                index++
            },
        ).apply {
            sortByPosition = false
            startPage = 1
            endPage = Int.MAX_VALUE
        }.getText(doc)
    }
}

object PdfExtractor {

    /**
     * 全ページの文字を取得する（list[list[CharBox]]）。
     * onPageLoaded はページ開始ごとに (開始済みページ数, 総ページ数) を通知する（既定＝無通知）。
     *
     * [source] に PDF の実ファイルを渡すと、端末のヒープに余裕がある場合だけ**ページ範囲を分割して
     * 並列に**読む（[parallelDegree] が K を決める。K=1 と判定されたら従来の単一経路をそのまま通る）。
     * null なら常に単一経路＝並列化前と完全に同じ挙動（オラクル/プロファイラ/テストの既定）。
     * 並列でも結果はページ順に連結するので `body_sha256` は動かない（JVM/実機の両スパイクで全 K 一致を確認済み）。
     */
    fun loadPages(
        doc: PDDocument,
        source: File? = null,
        onPageLoaded: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): List<List<CharBox>> {
        val total = doc.numberOfPages
        if (source == null) return loadPagesSingle(doc, total, onPageLoaded)
        val degree = parallelDegree(
            totalPages = total,
            maxMemoryBytes = Runtime.getRuntime().maxMemory(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
        )
        // K=1 相当なら**並列ハーネスを通さない**。ハーネスをスレッド1本で回すと PDF の再オープンと
        // 連結のぶんだけ単一より遅い（実機実測 0.87〜0.96x）＝余裕の無い端末で速度まで落とす筋の悪い形になる。
        if (degree <= 1) return loadPagesSingle(doc, total, onPageLoaded)
        return loadPagesParallel(doc, source, degree, onPageLoaded)
    }

    /** 従来の単一走行経路（並列化前と同一）。 */
    private fun loadPagesSingle(
        doc: PDDocument,
        total: Int,
        onPageLoaded: (loaded: Int, total: Int) -> Unit,
    ): List<List<CharBox>> {
        var loaded = 0
        val stripper = GlyphStripper(onPageStart = { onPageLoaded(++loaded, total) }).apply {
            sortByPosition = false
            startPage = 1
            endPage = Int.MAX_VALUE
        }
        stripper.getText(doc)
        return stripper.pages
    }

    // ==========================================================
    // ページ範囲並列（loadPages は engine の 66〜70%＝ここだけが単一スレッド天井の外側）
    // ==========================================================

    /**
     * 並列度 K を**端末のヒープ上限と文書規模から事前に**決める。
     *
     * なぜ事前決定しかないか: 実機では ART が [OutOfMemoryError] を投げる前に OEM がプロセスごと殺す
     * （PGEM10 実測＝`o-kill(109)`・logcat の OutOfMemoryError は 0 件）。走らせてから落ちても
     * 捕捉できないので、走らせる前にヒープから決めるのが唯一の防ぎ方
     * （`docs/knowledge/pdf-extract-engine-cost-ceiling.md`「実機ではヒープ天井が並列利得の上限を決める」）。
     *
     * 式（すべて PGEM10 実測の数値だけから導く。N6169DZ 8,668ページ・`heapgrowthlimit=384m`）:
     * - 予算 = maxMemory の [HEAP_BUDGET_PERCENT]%。残す 10%（384MB なら 38.4MB）は GC 猶予＝
     *   同一文書・同一走行形状でも単一のピークが 276.1〜313.4MB と振れた実測幅（37.3MB＝未回収ゴミぶん）
     *   とほぼ同じ。ゴミの量は確保量に比例するので、固定 MB でなく上限に対する割合で持つ。
     * - 単一走行の必要量 = ページ数 × [SINGLE_RUN_KB_PER_PAGE]（276.1MB ÷ 8,668ページ＝約 32KB/ページ）。
     *   ⚠️ **文書規模を式に入れる**のがここ。1セットの量は結果の CharBox 列が支配し、これは K に依らず
     *   必ず要る＝固定 MB で判定すると長編で K を許して殺し、短編で不要に K=1 へ落とす両方の誤りが出る。
     *   ページ数を代理変数にするのはグリフ数が事前に分からないため（実測のページ密度は 390〜476 グリフ/ページ）。
     * - 並列の上乗せ = (K−1) × [PARALLEL_EXTRA_MB_PER_THREAD]（K=3 のピーク 360.5MB − 単一 276.1MB を
     *   増えた 2 スレッドで割って 42MB/スレッド）。K 個の `PDDocument` が同時に開き、各スレッドの
     *   パース済み COS とフォント資源が重なるぶん。
     *   ⚠️ この上乗せを**文書規模に比例させなかった**理由: 実測点が (8,668ページ, K=3) の 1 点しかなく、
     *   比例係数を決める根拠が無い。固定値は小さい文書に対しては過剰予約（＝安全側）になり、逆に
     *   8,668ページより大きい文書では上の単一走行項が先に予算を食い潰して K=1 に落ちるため、
     *   固定値を外挿する領域へそもそも入らない（512MB 端末でも 13,000ページ台で K=1 になる）。
     *
     * 上限は3つ: [MAX_PARALLEL_DEGREE]（実機で K=3 が最良＝2.1x／JVM でも長編は K=4 で伸びない）、
     * CPU コア数、そして [MIN_PAGES_PER_THREAD]（1スレッドあたりの持ち分）。
     */
    internal fun parallelDegree(totalPages: Int, maxMemoryBytes: Long, availableProcessors: Int): Int {
        val budgetMb = maxMemoryBytes / (1024L * 1024L) * HEAP_BUDGET_PERCENT / 100
        val estSingleRunMb = totalPages.toLong() * SINGLE_RUN_KB_PER_PAGE / 1024
        val headroomMb = budgetMb - estSingleRunMb
        // 単一走行だけで予算を超える文書＝並列化する余地が無い（単一経路で祈るしかない領域）。
        if (headroomMb <= 0) return 1
        val byHeap = 1 + (headroomMb / PARALLEL_EXTRA_MB_PER_THREAD).toInt()
        // 実測で良好だった最小の持ち分は中編 799ページ ÷ K=3 ＝ 266ページ/スレッド。これを下回る
        // 細分は測っていない（再オープン＋連結の固定費 4〜15% を取り返せる保証が無い）＝外挿しない。
        val byPages = totalPages / MIN_PAGES_PER_THREAD
        return minOf(byHeap, byPages, minOf(availableProcessors, MAX_PARALLEL_DEGREE)).coerceAtLeast(1)
    }

    /**
     * ページ範囲を [degree] 分割し、各スレッドが**自分の [PDDocument] を開いて**担当範囲だけ収集し、
     * ページ順に連結する。自前の PDDocument が要るのは PDFBox の COSDocument がスレッド安全でないため。
     *
     * 内部可視なのは JVM テスト（`PdfParallelLoadPagesTest`）が K を固定して単一経路との等価性を
     * 縛るため＝ヒープの都合で K が動くと等価性の網が端末依存になってしまう。
     */
    internal fun loadPagesParallel(
        doc: PDDocument,
        source: File,
        degree: Int,
        onPageLoaded: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): List<List<CharBox>> {
        require(degree >= 1) { "degree は 1 以上（指定値: $degree）" }
        val total = doc.numberOfPages
        // 並列区間に入る前に単一スレッドで 1 ページ処理し、静的キャッシュ（PDFBoxResourceLoader/CMap/
        // glyphlist）を温める。スパイクで等価性が成立した条件のひとつがこれで、未初期化のまま K スレッドが
        // 同時に触る形は検証していない。本番経路では直前の extractBookMeta が既に温めているので実質再実行
        // だが（1ページ＝数ミリ秒）、この関数の暗黙の前提条件にはしない。
        if (total > 0) loadFirstPage(doc)

        val progress = PageLoadProgress(total, onPageLoaded)
        // 最初に失敗した例外。キャンセル（進捗コールバックが投げる CancellationException）もここに載る。
        // 「最初の1つ」を保つのは、後続スレッドが投げる打ち切り例外で本物のキャンセルが化けないようにするため。
        val firstFailure = AtomicReference<Throwable?>(null)
        val threadSeq = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(degree) { runnable ->
            // 名前を付けるのは実機トレース/ANR ログでどのスレッドが抽出中か判別するため。
            // デーモンにするのは、万一ワーカーが停止に応じなくてもプロセス終了を妨げないようにするため。
            Thread(runnable, "$LOAD_THREAD_PREFIX${threadSeq.incrementAndGet()}").apply { isDaemon = true }
        }
        try {
            val futures = splitRanges(total, degree).map { range ->
                pool.submit(
                    // 型を明示するのは `stripper.pages` の可変型がそのまま Future の型引数へ流れ込むのを避けるため。
                    Callable<List<List<CharBox>>> {
                        try {
                            PDDocument.load(source).use { own ->
                                val stripper = GlyphStripper(
                                    onPageStart = {
                                        // 仲間が既に落ちている（＝キャンセル含む）なら自分もページ境界で降りる。
                                        // use を抜けて自分の PDDocument を閉じさせるのが目的なので、
                                        // firstFailure を上書きしない専用の型で抜ける。
                                        if (firstFailure.get() != null) throw LoadAborted()
                                        progress.onPageStart()
                                    },
                                    pageRange = range,
                                ).apply { sortByPosition = false }
                                stripper.getText(own)
                                stripper.pages
                            }
                        } catch (t: Throwable) {
                            // 握り潰さない。記録して同じものを投げ直す（記録は他スレッドを降ろすため）。
                            firstFailure.compareAndSet(null, t)
                            throw t
                        }
                    }
                )
            }
            val merged = ArrayList<List<CharBox>>(total)
            for (f in futures) merged.addAll(f.get())
            return merged
        } catch (t: Throwable) {
            // ExecutionException は原因を剥がして投げ直す。剥がさないとキャンセルの型が呼び出し側
            // （PdfBookImporter の `if (e is CancellationException) throw e`）で判別できなくなり、
            // 「停止」が Unknown エラーに化ける。
            val primary = firstFailure.get() ?: (t as? ExecutionException)?.cause ?: t
            // main 側起因（get の割り込みなど）でもワーカーを降ろすため必ず記録してから待つ。
            firstFailure.compareAndSet(null, primary)
            awaitWorkersStopped(pool, primary)
            throw primary
        } finally {
            pool.shutdown()
        }
    }

    /**
     * 全ワーカーが**終了しきる**（＝各スレッドの `use` が自分の PDDocument を閉じ終える）のを待つ。
     *
     * なぜ待つか: 待たずに例外を投げ返すと、走り続けるワーカーが数十〜数百 MB を掴んだまま呼び出し元の
     * 後始末（出力ディレクトリ削除・次の取込）と重なり、ヒープ天井付近で「2セット同時生存」を作る
     * ＝実機ではこの形が OEM の system kill を招く。閉じ順は ①ワーカー内 `use`（例外経路でも必ず）→
     * ②ここで全ワーカーの終了確認 → ③呼び出し元へ例外 → ④facade の `use` が元の PDDocument を閉じる。
     */
    private fun awaitWorkersStopped(pool: ExecutorService, primary: Throwable) {
        // 打ち切りはページ境界で見るので、1ページぶんの仕事（実機で約 7ms）で降りるのが正常。
        // shutdownNow は待機中タスクの取り消しと割り込みの試行（PDFBox のパースは割り込みに応じないので保険）。
        pool.shutdownNow()
        try {
            if (!pool.awaitTermination(WORKER_STOP_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                // 症状を隠さない: 停止しないワーカーが居ることは PDDocument が開きっぱなしという意味なので、
                // 本来の例外に添えて必ず外へ出す（本来の例外は差し替えない＝キャンセルの型を保つ）。
                primary.addSuppressed(
                    IllegalStateException(
                        "並列 loadPages のワーカーが ${WORKER_STOP_TIMEOUT_SEC}s 以内に停止しなかった（PDDocument が開いたままの可能性）"
                    )
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            primary.addSuppressed(e)
        }
    }

    /** [0, totalPages) を先頭から順に [degree] 個の連続範囲（0 始まり・両端含む）へ分ける。 */
    private fun splitRanges(totalPages: Int, degree: Int): List<IntRange> {
        val per = totalPages / degree
        val rem = totalPages % degree
        var from = 0
        return (0 until degree).map {
            // 余りは先頭から1ページずつ配る（分割の仕方は結果に影響しないが、実機スパイクと同一にしておく）。
            val size = per + if (it < rem) 1 else 0
            val range = from until (from + size)
            from += size
            range
        }
    }

    /**
     * 並列走行の進捗を1本のカウンタへ集約する。
     *
     * なぜ atomic + 直列化か: 下流（PdfBookImporter → PdfProcessingService の通知間引き）は
     * 「1度に1スレッドから・単調増加で来る」既存の契約で書かれている。並列化はエンジン内部の実装詳細に
     * 留め、外へ出す進捗の形は単一走行時と同一に保つ。到着が入れ替わって後退した通知は捨てる
     * （捨てても最後の loaded==total は常に最大値なので取りこぼさない）。
     */
    private class PageLoadProgress(
        private val total: Int,
        private val onPageLoaded: (loaded: Int, total: Int) -> Unit,
    ) {
        private val loaded = AtomicInteger(0)
        private var lastEmitted = 0 // synchronized(this) で保護

        fun onPageStart() {
            val n = loaded.incrementAndGet()
            synchronized(this) {
                if (n <= lastEmitted) return
                lastEmitted = n
                // キャンセル（呼び出し側の ensureActive）はここから投げられる。ロックは巻き戻しで解放される。
                onPageLoaded(n, total)
            }
        }
    }

    /** 仲間の失敗を受けて自分のページ走査を打ち切るための内部専用シグナル（外へは出ない）。 */
    private class LoadAborted : RuntimeException("並列 loadPages: 他スレッドの失敗により打ち切り")

    /** 1 ページ目だけの文字を取得する（タイトル・著者抽出用）。 */
    private fun loadFirstPage(doc: PDDocument): List<CharBox> {
        if (doc.numberOfPages == 0) return emptyList()
        val stripper = GlyphStripper().apply {
            sortByPosition = false
            startPage = 1
            endPage = 1
        }
        stripper.getText(doc)
        return stripper.pages.firstOrNull() ?: emptyList()
    }

    // ==========================================================
    // 【Phase 00】 表紙からタイトル・著者を抽出
    // ==========================================================

    /**
     * タイトルと著者を「1 ページ目を 1 回だけストリップして」両方算出する。
     * 旧実装は title/author が個別に 1 ページ目を再ストリップしており表紙のパースが 2 回走っていた。
     * 短編ではこの重複が抽出時間の無視できない割合を占めるため 1 パスに統合する
     * （本文抽出の loadPages とは別パス＝著者/タイトルは表紙のみで完結するため）。
     */
    fun extractBookMeta(doc: PDDocument): BookMeta {
        val chars = loadFirstPage(doc)
        // 表紙フッター帯をページ高さ相対で出すため、実ページ高さを mediaBox から直接取る
        // （GlyphStripper/CharBox の型は変えない＝変更面最小化の設計判断）。ページ0が無ければ
        // 基準高さへフォールバック（この後 chars も空なので著者は空文字になる）。
        val pageHeight =
            if (doc.numberOfPages > 0) doc.getPage(0).mediaBox.height.toDouble()
            else ParserRules.COVER_PAGE_HEIGHT
        return BookMeta(titleFromChars(chars), authorFromChars(chars, pageHeight))
    }

    /**
     * 表紙文字列から「最大フォントサイズの文字を top→x0 順で結合」してタイトルを得る。
     * 移植元 extract_book_title と同一。座標計算を伴わない純関数なのでユニットテスト可能。
     */
    fun titleFromChars(chars: List<CharBox>): String {
        if (chars.isEmpty()) return "不明なタイトル"
        val maxSize = chars.maxOf { it.size }
        val title = chars
            .filter { ParserRules.isClose(it.size, maxSize, absTol = 0.1) }
            .sortedWith(compareBy({ it.top }, { it.x0 }))
            .joinToString("") { it.text }
            .trim()
        return if (title.isNotEmpty()) title else "無題の作品"
    }

    /**
     * 表紙文字列から著者（タイトル未満の最大サイズ群・フッター除外）を結合して得る。
     * 移植元 extract_book_author の思想を相対化: サイズは絶対 12pt 固定でなく「表紙内の最大サイズ
     * ＝タイトル、より小さい最大サイズ＝著者」と実配置から選ぶ（検出できなければ FONT_SIZE_AUTHOR へ退避）。
     * フッター帯はページ高さ相対（実高さ×COVER_FOOTER_Y/COVER_PAGE_HEIGHT・幅±COVER_FOOTER_Y_TOL）で除く。
     * フッターを除くのは、ページ番号/シリーズ名（発行元表記）が著者と同サイズで下部に出るため。
     */
    fun authorFromChars(chars: List<CharBox>, pageHeight: Double = ParserRules.COVER_PAGE_HEIGHT): String {
        if (chars.isEmpty()) return ""
        val authorSize = detectAuthorSize(chars)
        // フッター帯の中心をページ高さ相対で求める。golden 高さ(595.28)では 500.0 と数値等価になり現行挙動保存。
        val footerY = pageHeight * (ParserRules.COVER_FOOTER_Y / ParserRules.COVER_PAGE_HEIGHT)
        return chars
            .filter {
                ParserRules.isClose(it.size, authorSize) &&
                    !ParserRules.isClose(it.top, footerY, absTol = ParserRules.COVER_FOOTER_Y_TOL)
            }
            .sortedWith(compareBy({ Math.round(it.top) }, { it.x0 }))
            .joinToString("") { it.text }
            .trim()
    }

    /**
     * 表紙の著者サイズ＝「最大サイズ(タイトル)未満の最大サイズ群」。タイトル1種しかない表紙では
     * 検出不能なので FONT_SIZE_AUTHOR(=12.0) へフォールバックする。
     * なぜ最頻でなく最大: 著者はタイトル直下の見出し格で、惹句(より小さいサイズ)より必ず大きいという
     * 現行レイアウト事実に基づく（惹句 11pt 等を誤って拾わないため）。
     */
    private fun detectAuthorSize(chars: List<CharBox>): Double {
        val maxSize = chars.maxOf { it.size }
        val below = chars.map { it.size }.filter { !ParserRules.isClose(it, maxSize) }
        return below.maxOrNull() ?: ParserRules.FONT_SIZE_AUTHOR
    }

    // ==========================================================
    // 【Phase 01-02】 本文抽出エンジン
    // ==========================================================

    /**
     * 全ページを読み込み TextProcessor で段落列（章マーカー・ルビマーカー入り）へ変換する。
     *
     * onProgress は load(全ページのグリフ抽出＝超長編の支配的コスト)と process(段落化)を [EnginePhase]
     * で区別して通知する。facade(PdfBookExtractor) が両フェーズを重み合成し、load 中も進捗バーを前進
     * させるために渡す（未指定＝null なら通知しない＝オラクル/テスト用途）。
     *
     * [source] は [doc] の元ファイル。渡すと load フェーズがヒープに応じて並列化されうる（[loadPages]）。
     * 省略すると単一走行＝並列化前と同じ挙動になる（ゴールデン/プロファイラはこちらを使う）。
     */
    fun runFinalEngine(
        doc: PDDocument,
        source: File? = null,
        onProgress: ((phase: EnginePhase, current: Int, total: Int) -> Unit)? = null,
    ): List<String> = runFinalEngine(doc, source, Runtime.getRuntime().maxMemory(), onProgress)

    /**
     * ヒープ上限を注入できる [runFinalEngine]。
     *
     * なぜ注入口を開けるか: 経路の分岐（全ページ保持 / ストリーミング）は端末のヒープ天井で決まるが、
     * **開発機は高性能で低スペック側の分岐を人間が踏めない**。人が実機で確認できない以上、境界の
     * 保証は機械の網しか無いので、テストから両経路を名指しで叩けるようにする。
     */
    internal fun runFinalEngine(
        doc: PDDocument,
        source: File?,
        maxMemoryBytes: Long,
        onProgress: ((phase: EnginePhase, current: Int, total: Int) -> Unit)? = null,
    ): List<String> {
        val totalPages = doc.numberOfPages
        // 走らせる前にヒープ上限から経路を決める（[canMaterializeAllPages] の why を参照）。
        val materialize = canMaterializeAllPages(totalPages, maxMemoryBytes)

        // LOAD フェーズ（進捗バー前半）＝「rules 検出に要る走査」。溜める経路は 1 走査、溜めない経路は
        // 2 走査なので、通し進捗が巻き戻らないよう総数を走査数倍して単調増加のカウンタで出す。
        val loadTotal = totalPages * (if (materialize) 1 else DetectedRules.STREAMING_PASSES)
        var loaded = 0

        val rulesSource: PageGlyphSource
        val bodySource: PageGlyphSource
        if (materialize) {
            // 従来と同じ 1 パース経路。全ページ分の CharBox を保持できると判断した端末だけが通る。
            val all = MaterializedPageSource(
                loadPages(doc, source) { n, _ -> onProgress?.invoke(EnginePhase.LOAD, n, loadTotal) },
            )
            rulesSource = all
            bodySource = all
        } else {
            // 保持量をページ数から切り離す経路。走査のたびに再パースする代わりに同時生存は 1 ページ。
            // rules 用と本文用でインスタンスを分けるのは、進捗通知を LOAD フェーズの走査だけに限るため
            // （本文走査の通知は PROCESS 側が出す＝1 ページで 2 回数えない）。
            rulesSource = ReparsingPageSource(doc) {
                loaded++
                onProgress?.invoke(EnginePhase.LOAD, loaded, loadTotal)
            }
            bodySource = ReparsingPageSource(doc)
        }

        // 本文処理の前に、この文書の実配置から解析パラメータを検出する（検出不能な項目は FALLBACK＝
        // 現行実測値へ退避）。生成側が同形状のまま寸法を微調整しても追随できるようにするため。
        val rules = DetectedRules.detect(rulesSource, totalPages)

        // 段落化もページ単位のストリーミングで回す（中間の全ページ CharBox を持たない）。
        // processPages が出す pct(10-60) は元々未使用のため捨て、(processed, bodyTotal) のみ前送りする。
        val paragraphs = ArrayList<String>()
        val streamer = TextProcessor.ParagraphStreamer(totalPages, rules, { _, processed, bodyTotal ->
            onProgress?.invoke(EnginePhase.PROCESS, processed, bodyTotal)
        }) { paragraphs.add(it) }
        bodySource.forEachPage { index, chars -> streamer.addPage(index, chars) }
        streamer.finish()
        return paragraphs
    }

    /**
     * 全ページ分の CharBox を**同時に保持してよいか**（＝再パースの無い高速経路を採れるか）を、
     * 端末のヒープ上限と文書規模から**走らせる前に**決める。
     *
     * なぜ事前判定しかないか: [OutOfMemoryError] を捕まえてから退避する形は採れない。捕捉できた時点で
     * ヒープは既に危険域で後続の確保も失敗しうるうえ、実機では ART が投げる前に OEM がプロセスごと
     * 殺すこともある（`docs/knowledge/pdf-extract-engine-cost-ceiling.md`）。**踏む前に避ける**のが唯一の道。
     *
     * 係数の出典（いずれも実測。端末の ART 実装で 2 倍近く振れるので**大きい側**を安全側として採る）:
     * - OPPO PGEM10 実機: engine ピーク 276.1MB / 8,668 ページ ＝ 約 32KB/ページ
     * - x86_64 エミュレータ(API34): Dalvik ピーク 514MB / 8,668 ページ ＝ 約 59KB/ページ
     * 保持の実体は 1 グリフ 1 [CharBox]（実測 390〜476 グリフ/ページ）なので、ページ数を代理変数にする。
     *
     * 予算を上限の [MATERIALIZE_BUDGET_PERCENT]% に留めるのは、CharBox 以外に
     * PDDocument とフォント/CMap（実測で数十 MB）・段落/章の文字列・GC 猶予が同時に要るため。
     *
     * @param maxMemoryBytes `Runtime.getRuntime().maxMemory()` 相当。**引数で受けるのはテストのため**
     *   ＝開発機が高性能で低スペック側の分岐を人間が踏めない以上、境界の保証は機械の網しか無い。
     */
    internal fun canMaterializeAllPages(totalPages: Int, maxMemoryBytes: Long): Boolean {
        val budgetBytes = maxMemoryBytes / 100 * MATERIALIZE_BUDGET_PERCENT
        val estimatedBytes = totalPages.toLong() * MATERIALIZED_BYTES_PER_PAGE
        return estimatedBytes <= budgetBytes
    }

    // ==========================================================
    // 並列 loadPages の較正値（すべて PGEM10 実機実測が出典・根拠は parallelDegree の KDoc）
    // 出典: docs/knowledge/pdf-extract-engine-cost-ceiling.md
    // ==========================================================

    /**
     * 全ページ保持の見積り（1 ページあたり）。実測 32KB（PGEM10 実機）〜59KB（x86_64 エミュ）の
     * **大きい側**へ寄せた安全値。小さく見積もると天井を越えて落ちる／大きく見積もると遅い経路へ
     * 落ちるだけ＝誤りの代償が非対称なので安全側に倒す。
     */
    internal const val MATERIALIZED_BYTES_PER_PAGE = 64L * 1024

    /**
     * 全ページ保持に割いてよいヒープ上限の割合。残りは PDDocument とフォント/CMap（実測で数十 MB）・
     * 段落/章の文字列・GC 猶予が同時に要るぶん。
     */
    internal const val MATERIALIZE_BUDGET_PERCENT = 50L

    /** ヒープ上限のうち抽出に使ってよい割合。残り 10% は GC 猶予（未回収ゴミの実測振れ幅 37.3MB/384MB 相当）。 */
    private const val HEAP_BUDGET_PERCENT = 90L

    /** 単一走行 1 セットの必要量（276.1MB ÷ 8,668ページ）。結果の CharBox 列が支配し K に依らず必ず要る。 */
    private const val SINGLE_RUN_KB_PER_PAGE = 32L

    /** スレッドを 1 本増やすごとの上乗せ（K=3 の 360.5MB − 単一 276.1MB を増分 2 スレッドで割った値）。 */
    private const val PARALLEL_EXTRA_MB_PER_THREAD = 42L

    /** 並列度の上限。実機は K=3 が最良（長編 2.1x・中編 2.7x）で、JVM でも長編は K=4 で伸びない。 */
    private const val MAX_PARALLEL_DEGREE = 3

    /** 1 スレッドあたりの最小ページ数。実測で良好だった最小の持ち分（中編 799 ÷ K=3 ＝ 266）を下回らない値。 */
    private const val MIN_PAGES_PER_THREAD = 256

    /** 並列ワーカーのスレッド名接頭辞（トレース/ANR ログでの識別と、テストの停止確認に使う）。 */
    internal const val LOAD_THREAD_PREFIX = "pdf-load-"

    /** 打ち切り後にワーカーの終了（＝PDDocument の close）を待つ上限。1ページの仕事は実機で約 7ms。 */
    private const val WORKER_STOP_TIMEOUT_SEC = 10L
}
