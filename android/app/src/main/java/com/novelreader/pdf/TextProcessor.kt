package com.novelreader.pdf

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文字座標から縦書きの列を再構成し、ルビを紐付けて段落文字列を組み立てる。
 * 移植元 pdf_extractor.py の _group_chars_by_line / _associate_ruby /
 * _build_line_str / _process_pages（「移植元」の意味は PdfBookExtractor の注記を参照）。
 *
 * ルビは中間表現として「|親文字《よみ》」マーカーで段落文字列に埋め込み、後段の
 * [ChapterProcessor] が `<ruby>` タグへ変換する。**この記法は今も live な内部契約**で、
 * 二重に守られている:
 * - `JvmGoldenRegressionTest` がこの段落文字列そのものを body_sha256 と 《 の出現数で固定する
 * - Web 取込側 [com.novelreader.scrape.NovelSiteAdapter] も同じ記法で本文を返し、同じ変換段を通る
 * ＝記法を変えるなら PDF/Web 両経路とゴールデンを同時に更新すること。
 */
object TextProcessor {

    /**
     * 本文から落とす空白＝**改行・復帰・タブだけ**。
     *
     * 移植元 pdf_extractor.py は [" ", "\n", "\r", "\t", "\xa0"] を一括で捨てており、半角スペースと
     * NBSP まで消していた（S1 の真因）。半角スペースは本文の**文字そのもの**で、顔文字・AA・
     * 連続スペースの演出が壊れる（実測: 全10サンプルで半角スペースが 0 個＝100% 脱落。
     * N6169DZ だけで 10,530 個を落としていた）。改行・復帰・タブは縦組み PDF のグリフとしては
     * 現れないので、落としても本文は変わらない。
     */
    private val DROPPED_WHITESPACE = setOf("\n", "\r", "\t")

    /**
     * 本文文字を x0（縦列）でグループ化する。挿入順を保持する LinkedHashMap。
     *
     * [absTol] は「同じ列とみなす x0 の許容差」。既定 [ParserRules.TOLERANCE]（0.1）は
     * [DetectedRules.detect]（列ピッチ自体をこの結果から測る＝広げると測定対象が歪む）が使う。
     *
     * 本文整形からは**列ピッチの半分**を渡す。なぜ広げる必要があるか（S7 の真因）＝縦組みフォントの
     * 半角プロポーショナル字（`'` など）は em ボックスの中央に置かれるため、同じ列に居ても x0 が
     * 全角字より右へずれる（N6169DZ 実測: 列 735.68 に対し `'` は 741.17＝+5.49）。0.1 の窓では
     * 別列に割れ、列を x 降順に並べる段で**語中から行頭へ飛ぶ**（原順保持違反）。列ピッチの半分なら
     * 隣接列（実測ピッチ 22.68）とは決して混ざらない。
     *
     * 併せて列のキーを**構成文字の最小 x0**へ寄せる。ずれるのは常に右方向（中央寄せ＝x0 が大きくなる側）
     * なので最小値が列の真の左端になり、キーが先着文字のずれを引きずらない
     * （キーは空行判定の列間距離とルビ親列の逆算に使われるため、ずれたままだと下流が狂う）。
     */
    internal fun groupCharsByLine(
        bodiesAll: List<CharBox>,
        absTol: Double = ParserRules.TOLERANCE,
    ): LinkedHashMap<Double, MutableList<CharBox>> {
        val linesDict = LinkedHashMap<Double, MutableList<CharBox>>()
        for (c in bodiesAll) {
            val xVal = c.x0
            var matchedKey: Double? = null
            for (k in linesDict.keys) {
                if (ParserRules.isClose(xVal, k, absTol = absTol)) {
                    matchedKey = k
                    break
                }
            }
            if (matchedKey != null) {
                linesDict[matchedKey]!!.add(c)
            } else {
                linesDict[xVal] = mutableListOf(c)
            }
        }
        if (absTol <= ParserRules.TOLERANCE) return linesDict
        // キーを列の真の左端（最小 x0）へ寄せ直す。窓を広げたときだけ意味を持つ処理。
        val rekeyed = LinkedHashMap<Double, MutableList<CharBox>>()
        for ((_, members) in linesDict) {
            var minX = Double.POSITIVE_INFINITY
            for (m in members) if (m.x0 < minX) minX = m.x0
            rekeyed[minX] = members
        }
        return rekeyed
    }

    /**
     * ルビを親文字へ [CharBox.rubyText] として紐付ける（in-place）。
     *
     * ## 真因と設計（S4a 親範囲・S4b 傍点取りこぼしの共通原因）
     * 旧実装は**ルビ 1 文字ずつを最近傍の親へ**割り当てていた。これは 2 つの意味で壊れる:
     * 1. 上端同士で距離を測っていた＝本文とルビは字面高が違う（実測 7.7pt 対 3.85pt）ので
     *    常に約 1.9pt の偏りが乗り、境界のルビが隣の親へ吸われる。→ [CharBox.center] で比べる。
     * 2. 読みが親より長い/短いとき、1 文字ずつでは親の範囲を復元できない。送り仮名や直前の
     *    句読点まで親に食い込み（`な蔑称`・`る主`）、逆に 2 つのルビが同じ親へ衝突すると
     *    親が 1 文字足りなくなる（1:1 で並ぶ傍点 S4b はこの衝突で run が落ちる）。
     *
     * 実測した組版規則（N6169DZ）: **ルビ run はその親範囲の中心に揃えて置かれる**
     * （例『蔑称《あいしょう》』run 中心 365.97 に対し親 蔑称 の中心 366.51）。よって
     * 「ルビ run の垂直範囲に**字面中心が入る**本文字」を親範囲として採るのが素直な逆算になる。
     * ただし句読点・記号も中心だけで見れば range に入りうる（実測『改！《あらため》』＝「！」の
     * 中心が run 範囲に入り親へ混入）ので、**字種条件（[isRubyBaseChar]）を containment 自体にも
     * 課す**（旧実装は延長候補にしか課しておらず、最初の containment がノーガードだった＝S4a trim 系
     * 191 件の真因）。
     */
    internal fun associateRuby(
        linesDict: LinkedHashMap<Double, MutableList<CharBox>>,
        rubiesAll: List<CharBox>,
        rubyOffsetX: Double = ParserRules.RUBY_OFFSET_X,
        columnTol: Double = ParserRules.TOLERANCE,
    ) {
        if (rubiesAll.isEmpty()) return
        // (1) ルビを親列ごとに束ねる（ルビ x0 = 親列 x0 + rubyOffsetX の逆算）。
        val byColumn = LinkedHashMap<Double, MutableList<CharBox>>()
        for (r in rubiesAll) {
            val targetX = r.x0 - rubyOffsetX
            for (xKey in linesDict.keys) {
                if (ParserRules.isClose(xKey, targetX, absTol = columnTol)) {
                    byColumn.getOrPut(xKey) { mutableListOf() }.add(r)
                    break
                }
            }
        }
        for ((xKey, rubies) in byColumn) {
            val bodies = linesDict[xKey]!!.sortedBy { it.top }
            val sorted = rubies.sortedBy { it.top }
            // (2) 縦に連続するルビを 1 run へ束ねる。run 内の字間（実測 約3.2pt）と run 間の空き
            //     （親 1 文字ぶん＝14pt 以上）は十分離れているので、ルビ 1 字ぶんを閾値にすれば足りる。
            val runs = mutableListOf<List<CharBox>>()
            var i = 0
            while (i < sorted.size) {
                var j = i + 1
                while (j < sorted.size && sorted[j].top - sorted[j - 1].bottom <= sorted[j].size) j++
                runs.add(sorted.subList(i, j))
                i = j
            }
            // 傍点（S4b）のように同じ列で 1 文字ずつ run が並ぶ組版では、[assignRun] の延長が
            // 次の run 用の文字まで奪う事故が起きる（実測: 導入直後に傍点数が 3221→3215 に減少＝
            // 隣の run と地続きになって buildLineStr 側で 1 run へ合体していた）。次 run の開始位置を
            // 上限として渡し、それを超える延長はしない。
            for ((idx, run) in runs.withIndex()) {
                val nextRunTop = runs.getOrNull(idx + 1)?.first()?.top
                assignRun(run, bodies, nextRunTop)
            }
        }
    }

    /**
     * ルビの親になりうる字か＝漢字・々〆〇・カタカナ（長音符含む）。
     * ひらがな（送り仮名）と約物は親の外という日本語ルビの慣習をそのまま条件にしている。
     */
    private fun isRubyBaseChar(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' ||          // CJK 統合漢字
        c in '\u3005'..'\u3007' ||          // 々 〆 〇
        c in '\u30A1'..'\u30FA' || c == '\u30FC' ||  // カタカナ・長音符
        c in '\uFF66'..'\uFF9D'             // 半角カタカナ

    /** 文字列が丸ごとルビの親になりうる字種か（空文字は不可）。 */
    private fun isRubyBaseText(s: String): Boolean = s.isNotEmpty() && s.all { isRubyBaseChar(it) }

    /**
     * 句読点・約物か（Unicode 一般カテゴリの P* 全般＝開き/閉じ括弧・句点・読点・感嘆符等）。
     *
     * なぜ「漢字・カタカナ限定」ではなく「約物だけを除く」広い条件か: ルビの親文字列には
     * ひらがな＋漢字の**フレーズ全体**を base にする用例が実在する（実測『ただの称号』
     * 『お説教』のように、送り仮名的なひらがなが base の一部として正しく含まれる）。
     * [isRubyBaseChar]（漢字・カタカナのみ）で containment 自体を絞ると、この種のひらがな込み
     * base を丸ごと落として読みの断片化（別の run に分裂）を起こす（実測: 導入直後に
     * unpaired が 104→354 へ悪化）。一方で「約物の中心がルビ range にたまたま入る」誤混入
     * （実測『改！《あらため》』の「！」）は実在するので、そこだけは除く。
     */
    private fun isPunctuationChar(c: Char): Boolean = when (Character.getType(c)) {
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        -> true
        else -> false
    }

    /**
     * ルビ run を、その垂直範囲に字面中心が入る本文字へ配分する。
     * [nextRunTop] は同じ列の次の ruby run の先頭 top（無ければ null）＝延長がそこを侵さないための上限。
     */
    private fun assignRun(run: List<CharBox>, bodies: List<CharBox>, nextRunTop: Double? = null) {
        val top = run.first().top
        val bottom = run.last().bottom
        // containment は「約物混入」だけ弾く（字種を漢字・カタカナに絞らない理由は上記 KDoc）。
        var parents = bodies.filter {
            it.center in top..bottom && it.text.isNotEmpty() && it.text.none { c -> isPunctuationChar(c) }
        }
        if (parents.isEmpty()) {
            // 幾何で親が取れないとき（想定外の組版）はルビを捨てず、中心が最も近い 1 文字へ寄せる。
            val best = bodies.minByOrNull { abs(it.center - (top + bottom) / 2.0) } ?: return
            best.rubyText = (best.rubyText ?: "") + run.joinToString("") { it.text }
            return
        }
        // 親範囲の**終端**を字種で最大 1 文字だけ延長する。
        // なぜ要るか（実測）: 生成側はルビ run を親範囲の先頭から固定ピッチで詰めて置き、読みが
        // 親より短くても引き伸ばさない（『物理演算《あたりまえ》』＝親 4 字ぶん 56pt に対し
        // ルビ 5 字ぶん 35pt）。つまり親範囲の**開始**は幾何に出るが**終端**は出ない。日本語のルビは
        // 漢字・カタカナの連なりに付くので、containment で取れた末尾から同じ性質の字が続く限り
        // 親に含め、送り仮名・約物で止める（幾何だけだと末尾が必ず 1 文字以上短くなる）。
        //
        // ⚠️ 延長は**高々 1 文字**まで＝2文字以上要る本物の複数文字語は、そのすべての文字が
        // 幾何 containment（字面中心が run 範囲に入る）で直接検出できる（実測 N6169DZ 全件で
        // 「2 文字以上の延長」が必要な例は 0 件）。延長が要るのは常に「境界の 1 文字だけが
        // ちょうど収まりきらない」ケースのみ。
        // ⚠️ 残る既知の穴（未確定）: この最後の 1 文字を「足すべきか否か」を字面座標だけで
        // 一意に決める式は実測でも見つからなかった——同じ (親字数 N0, 読み字数 M) の組でも
        // 延長が要る例（『物理演算《あたりまえ》』N0=3,M=5→+1）と要らない例
        // （『象徴《かんむり》』N0=2,M=4→+0、比率だけで見ると『水晶巣《あそこ》』N0=2,M=3→+1 と
        // 矛盾）が実測で両方存在し、run 下端と次候補の間隔・run 下端と直前の親の下端の差、いずれの
        // 単一指標でも閾値を引けなかった（実測値は本関数の呼び出し元の変更履歴に記録）。**なろう
        // サイト側の実際のルビ範囲マーク（義訓・造語ルビ）に依存する情報で、PDF の字形座標だけでは
        // 再現できない可能性が高い**と推定。ここでは「常に延長を試みる」側へ倒す（延長が必要な例が
        // 不要な例よりずっと多いという実測の非対称性に基づく防御的選択。過延長は次の語の頭を
        // 巻き込む害があるが、不足は読みが親の外へこぼれるだけで文字は失われない。残る具体的な
        // 不一致は `pdf_oracle/N6169DZ.s4a_known_gaps.json` に既知の穴として凍結・監視する）。
        val lastIdx = bodies.indexOf(parents.last())
        val cand = bodies.getOrNull(lastIdx + 1)
        val extended = parents.toMutableList()
        // 延長を試みてよいのは、今の末尾自体が漢字・カタカナ（続きがありうる字種）のときだけ。
        // 末尾がひらがな（送り仮名）で終わっている＝日本語の語はそこで閉じている合図なので、
        // 候補がどれだけ字種条件を満たしても延長しない（実測『笑み《えみり》』の「み」で正しく
        // 止め、続く無関係なカタカナ「リ」を巻き込まない）。
        // ⚠️ 候補が**次の ruby run の領域**に入っていないことも必須（傍点のように同じ列へ 1 文字ずつ
        // run が並ぶ組版では、この延長が次 run 用の文字まで奪うと buildLineStr の「ルビ付き文字の
        // 連続」判定で両 run が合体しうる、という理屈上のリスクへの防御）。ただし実測では
        // このガードの有無で S4b の傍点数（3215、オラクル 3221 に対し6件不足）は変化しなかった＝
        // 現状の不足はこの経路が真因ではないと推定されるが、真因は特定できていない
        // （既知の穴として `pdf_oracle/N6169DZ.s4a_known_gaps.json` 相当の扱いで監視する）。
        if (isRubyBaseText(parents.last().text) &&
            cand != null && isRubyBaseText(cand.text) &&
            (nextRunTop == null || cand.center < nextRunTop) &&
            2 * extended.size < 2 + run.size
        ) {
            extended.add(cand)
        }
        parents = extended
        // 親が読みより多いと 1 文字も貰えない親が出て run が途切れる（[buildLineStr] はルビ付き
        // 文字の**連続**で run を作る）。読みの数まで詰めて連続を保つ。
        if (parents.size > run.size) parents = parents.subList(0, run.size)
        // 読みを親へ順に配る（余りは前の親から 1 文字ずつ）。連結すれば元の読みに戻る。
        val per = run.size / parents.size
        var extra = run.size % parents.size
        var k = 0
        for (p in parents) {
            var take = per
            if (extra > 0) { take++; extra-- }
            val sb = StringBuilder()
            repeat(take) { sb.append(run[k].text); k++ }
            p.rubyText = (p.rubyText ?: "") + sb
        }
    }

    /** Y 昇順ソート済みの文字列から「|親《よみ》」付き文字列を組み立てる。 */
    internal fun buildLineStr(lineBodies: List<CharBox>): String {
        val sb = StringBuilder()
        var j = 0
        while (j < lineBodies.size) {
            val bc = lineBodies[j]
            val charText = bc.text
            if (charText in DROPPED_WHITESPACE) {
                j++
                continue
            }

            val rubyText = bc.rubyText
            if (!rubyText.isNullOrEmpty()) {
                // ルビ付き文字が連続する範囲をまとめて 1 つの |base《ruby》 に合成する。
                val baseRun = StringBuilder()
                val rubyRun = StringBuilder()
                while (j < lineBodies.size) {
                    val bc2 = lineBodies[j]
                    val t2 = bc2.text
                    if (t2 in DROPPED_WHITESPACE) {
                        j++
                        continue
                    }
                    val r2 = bc2.rubyText
                    if (r2.isNullOrEmpty()) break
                    baseRun.append(t2)
                    rubyRun.append(r2)
                    j++
                }
                if (baseRun.isNotEmpty() && rubyRun.isNotEmpty()) {
                    sb.append("|").append(baseRun).append("《").append(rubyRun).append("》")
                } else {
                    sb.append(charText)
                    j++
                }
            } else {
                sb.append(charText)
                j++
            }
        }
        return sb.toString()
    }

    /**
     * 本文抽出コア。ページごとの文字リストから段落文字列のリストを返す。
     * 題名は "【題名】..." プレフィックス付きの段落として混在させる（章分割で利用）。
     *
     * 中身は [ParagraphStreamer] へ 1 ページずつ流すだけ（実装は一本＝全ページ版とストリーミング版で
     * 段落の縫合規則が食い違わないようにするため）。全ページを同時に持てる呼び出し側（テスト・
     * オラクル・ヒープに余裕のある端末）はこちらを使ってよい。
     *
     * progressCallback: 有効ページの処理開始ごとに (pct, processed, bodyTotal) を通知する。
     *   pct は 10〜60% にマップ。本文抽出(step1)の進捗バーをページ単位でライブ更新するために使う。
     *   null なら通知しない。
     */
    fun processPages(
        charListsByPage: List<List<CharBox>>,
        totalPages: Int,
        rules: DetectedRules = DetectedRules.FALLBACK,
        progressCallback: ((pct: Int, processed: Int, bodyTotal: Int) -> Unit)? = null,
    ): List<String> {
        val out = mutableListOf<String>()
        val streamer = ParagraphStreamer(totalPages, rules, progressCallback) { out.add(it) }
        for ((pageNum, chars) in charListsByPage.withIndex()) streamer.addPage(pageNum, chars)
        streamer.finish()
        return out
    }

    /**
     * ページを 1 枚ずつ受け取り、確定した段落を [emit] へ吐き出す逐次処理器。
     *
     * なぜ逐次か（OOM の真因対処）: 段落化はページ内で閉じる処理で、ページを跨いで要る状態は
     * **組み立て中の段落 1 本だけ**（[currentParagraph]）。にもかかわらず旧経路は全ページ分の
     * CharBox を先に materialize してから回していたため、保持量がページ数に比例した。
     * 1 ページ受け取るたびに使い切って捨てれば、保持量はページ数に依存しない。
     *
     * ⚠️ [addPage] に渡された `chars` は復帰後に破棄されてよい（参照を持ち越さない）。
     * ⚠️ ページは**昇順**に渡すこと（段落の縫合と先頭/末尾ページのトリムが順序に依存する）。
     */
    class ParagraphStreamer(
        private val totalPages: Int,
        private val rules: DetectedRules = DetectedRules.FALLBACK,
        private val progressCallback: ((pct: Int, processed: Int, bodyTotal: Int) -> Unit)? = null,
        private val emit: (String) -> Unit,
    ) {
        private var currentParagraph = StringBuilder()

        /**
         * 直前に本文列を出したページの「最終列 x0」と「そのページ番号」。**ページを跨いで持ち越す**。
         *
         * なぜ持ち越すか（S3 の真因）: 縦組みは列を右から左へ送るので、空行はそのぶん列が飛ぶ形で現れる。
         * 旧実装は列間 X の比較をページ内で閉じており（ページ先頭で prevX を null に戻していた）、
         * **ページ末尾と次ページ先頭の間隔だけが測れず、そこに在った空行が丸ごと落ちていた**
         * （実測 N0833HI 全 66 話で欠落 654 件・その全件がページ境界）。
         */
        private var carryX: Double? = null
        private var carryPage: Int = NO_PAGE

        // 本文ページ総数（先頭3＋末尾1 を除いた数）。0除算回避で最小1。
        private val bodyTotal = maxOf(totalPages - 4, 1)

        /**
         * 確定した段落を整形して外へ出す。
         * クリーンアップ規則は全ページ版と同一＝空行は "" のまま保持、それ以外は trim して空なら捨てる。
         * 段落ごとに閉じた規則なので、全部溜めてから一括で掛けても 1 本ずつ掛けても結果は同じ。
         */
        private fun emitParagraph(p: String) {
            if (p.isEmpty()) {
                emit("")
            } else {
                // 半角スペースは本文の文字なので**端でも落とさない**（S1 の真因の一部＝行末/行頭の
                // 空白演出が段落境界で消える）。落とすのは縦組み PDF に本来現れない制御空白だけ。
                val cleaned = p.trim('\t', '\n', '\r')
                if (cleaned.isNotEmpty()) emit(cleaned)
            }
        }

        /** 1 ページ分の文字を処理する（[pageNum] は 0 始まりの通しページ番号）。 */
        fun addPage(pageNum: Int, chars: List<CharBox>) {
            // 先頭3ページ（表紙・注意事項）と最終ページ（クレジット）を除外
            if (pageNum < 3 || pageNum >= totalPages - 1) return

            // 進捗通知（10〜60%）
            if (progressCallback != null) {
                val processed = pageNum - 3
                val pct = 10 + (processed.toDouble() / bodyTotal * 50).toInt()
                progressCallback.invoke(pct, processed, bodyTotal)
            }

            val titlesAll = mutableListOf<CharBox>()
            val bodiesAll = mutableListOf<CharBox>()
            val rubiesAll = mutableListOf<CharBox>()

            for (c in chars) {
                val fontName = c.fontName
                val fontSize = c.size
                val yPos = c.top

                // ① ページ数の除外（ページ番号サイズ かつ ページ番号 Y 付近。窓は現行と同形＝±5.0）
                if (ParserRules.isClose(fontSize, rules.pageNumSize)) {
                    if (ParserRules.isClose(yPos, rules.pageNumY, absTol = 5.0) ||
                        ParserRules.isClose(c.bottom, rules.pageNumY, absTol = 5.0)
                    ) {
                        continue
                    }
                }

                // ② 題名（Bold 判定・本文サイズ基準）
                if (ParserRules.checkIsTitle(fontName, fontSize, rules.bodySize)) {
                    titlesAll.add(c)
                }
                // ③ 本文
                else if (ParserRules.isClose(fontSize, rules.bodySize)) {
                    bodiesAll.add(c)
                }
                // ④ ルビ
                else if (ParserRules.isClose(fontSize, rules.rubySize)) {
                    rubiesAll.add(c)
                }
            }

            // 同じ列とみなす x0 の窓＝列ピッチの半分。半角プロポーショナル字のずれを吸収しつつ
            // 隣接列とは混ざらない（根拠は [groupCharsByLine] の KDoc）。
            val columnTol = rules.lineStepX / 2.0

            // 題名のテキスト化（列ごとに X 降順・列内は Y 昇順）
            if (titlesAll.isNotEmpty()) {
                // 素の x0 降順で並べると、列の中央に置かれる半角字（`'` 等）が x0 の大きさだけで
                // 先頭へ飛ぶ（S7 が章題にも出る実例＝「'鳥ｗｉｔｈ兎ｓ」）。本文と同じ列復元を通す。
                val titleCols = groupCharsByLine(titlesAll, columnTol)
                val sorted = titleCols.keys.sortedDescending()
                    .flatMap { k -> titleCols[k]!!.sortedBy { it.top } }
                val titleText = sorted
                    .filter { it.text != "\n" && it.text != "\r" && it.text != "\t" }
                    .joinToString("") { it.text }
                if (titleText.isNotEmpty()) {
                    if (currentParagraph.isNotEmpty()) {
                        emitParagraph(currentParagraph.toString())
                        currentParagraph = StringBuilder()
                    }
                    emitParagraph("【題名】$titleText")
                }
            }

            // 本文ソート（X 降順・Y 昇順）
            val bodiesSorted = bodiesAll.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })

            val linesDict = groupCharsByLine(bodiesSorted, columnTol)
            // 列キーは最小 x0 へ寄せ直してあるので、逆算の窓も半角字のずれを吸える幅にする
            // （列ピッチ 22.68 の 1/4＝隣の列とは決して混ざらない）。
            associateRuby(linesDict, rubiesAll, rules.rubyOffsetX, rules.lineStepX / 4.0)

            // 右の列から順にテキスト化＆段落の縫合
            val linesSortedX = linesDict.keys.sortedDescending()
            // ページ内の直前列（先頭列では null＝そこだけページ跨ぎの規則へ委ねる）。
            var prevX: Double? = null
            // このページの先頭列に、直前ページからの持ち越しを適用してよいか。
            // ⚠️ この 2 条件は「新しい誤検出を作らない」ための要（下の crossPageBlankCount も参照）。
            //  - 題名のあるページを除く: 章の題名は列グリッドの先頭側を占有し、本文はその何列か左から
            //    始まる。この空きは空行ではなく題名の版面なので、数えると章ごとに偽の空行が湧く
            //    （実測: 数えた場合 N0833HI で誤検出 180 件）。加えて章の変わり目では前ページが
            //    途中で終わる（章末の余白）ため、前ページ側の残り列も空行ではない。
            //  - 直前ページが 1 つ前のページであること: 本文列を 1 つも持たないページ（挿絵・区切り等）を
            //    挟むと、グリッドの連続という前提そのものが成り立たない
            //    （実測: 隣接を要求しない場合 N3957FQ で誤検出 2 件＝いずれも本文なしページを跨いだ形）。
            val carriedX = carryX
            val canCarry = carriedX != null && carryPage == pageNum - 1 && titlesAll.isEmpty()

            for (x in linesSortedX) {
                val lineBodies = linesDict[x]!!.sortedBy { it.top }
                val lineStr = buildLineStr(lineBodies)
                if (lineStr.isEmpty()) continue

                var isNewParagraph = false
                var blankLineCount = 0

                // 行頭が字下げ/開き括弧なら新段落
                if (lineStr.startsWith("　") || lineStr.startsWith("「") ||
                    lineStr.startsWith("『") || lineStr.startsWith("（")
                ) {
                    isNewParagraph = true
                }

                // 列間 X が 1 行ステップの 1.5 倍超なら段落切れ＋空行挿入
                if (prevX != null) {
                    val diffX = prevX - x
                    if (diffX > rules.lineStepX * 1.5) {
                        isNewParagraph = true
                        // 空行数 = round(diffX/lineStepX) - 1。厳密に .5 のとき roundToInt は上へ丸めるが、
                        // 実測 PDF で diffX/lineStepX がちょうど .5 になる例は確認されておらず、
                        // 丸め方向は結果に効いていない。
                        blankLineCount = (diffX / rules.lineStepX).roundToInt() - 1
                    }
                } else if (canCarry) {
                    // ページの先頭列だけは、直前ページの最終列との間で同じことをする。
                    val blanks = crossPageBlankCount(carriedX!!, x)
                    if (blanks >= 1) {
                        isNewParagraph = true
                        blankLineCount = blanks
                    }
                }

                if (isNewParagraph) {
                    if (currentParagraph.isNotEmpty()) {
                        emitParagraph(currentParagraph.toString())
                    }
                    repeat(maxOf(0, blankLineCount)) { emitParagraph("") }
                    currentParagraph = StringBuilder(lineStr)
                } else {
                    currentParagraph.append(lineStr)
                }

                prevX = x
            }

            // 本文列を 1 つでも出したページだけを持ち越す。出さなかったページは carryPage を更新しない
            // ＝次ページで隣接判定が外れ、グリッドの連続が切れた区間には空行を入れない。
            if (prevX != null) {
                carryX = prevX
                carryPage = pageNum
            }
        }

        /**
         * 前ページ最終列 [prevPageLastX] と次ページ先頭列 [x] の間に在る**空き列の数**＝復元すべき空行数。
         *
         * 導出: 1 ページの列を右から 0..C-1 と数え、列 j の x0 は Xright − j·step。
         * 前ページの最終列を a、次ページの先頭列を b とすると、間に在る空き列は
         * 「前ページで余った (C-1-a)」＋「次ページで空いた b」＝ (C-1) − a + b。
         * a = (Xright − prevPageLastX)/step、b = (Xright − x)/step を入れると Xright が消えて
         *   ((C-1)·step + prevPageLastX − x) / step ＝ (columnSpanX + prevPageLastX − x) / step。
         * ＝**両端の絶対座標は要らず、列グリッドの幅だけで足りる**（[DetectedRules.columnSpanX]）。
         *
         * ページ内の `round(diffX/step) - 1` と同じ「間に挟まる空き列の数」を返すので、下流の扱いも同じ。
         * columnSpanX の検出は原理的に過小側にしか外れない（[DetectedRules] の検出関数の KDoc）ので、
         * ここが過大になって偽の空行を生むことは無い。負値は 0 とみなす（版面が読めなかった＝入れない）。
         */
        private fun crossPageBlankCount(prevPageLastX: Double, x: Double): Int =
            ((rules.columnSpanX + prevPageLastX - x) / rules.lineStepX).roundToInt()

        /** 全ページを渡し終えた後に必ず呼ぶ（組み立て途中の最後の段落を吐き出す）。 */
        fun finish() {
            if (currentParagraph.isNotEmpty()) {
                emitParagraph(currentParagraph.toString())
                currentParagraph = StringBuilder()
            }
        }

        private companion object {
            /** 「まだ本文列を出したページが無い」を表す番号（0 は正当なページ番号なので使えない）。 */
            const val NO_PAGE = -1
        }
    }
}
