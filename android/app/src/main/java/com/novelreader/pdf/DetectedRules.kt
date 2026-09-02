package com.novelreader.pdf

import java.util.BitSet

/**
 * 1 文書ぶんの解析パラメータを、その文書の文字配置から自動検出した結果。
 *
 * なぜ: [ParserRules] の絶対値定数は現行 PDF 出力形状（フォントサイズ・列ピッチ・ページ番号座標）への
 * ハードコードで、生成側が「同じ形状のまま」寸法だけ微調整すると全滅しうる。文書ごとに実測して寸法を
 * 相対化し、検出できない項目だけ [FALLBACK]（＝現行実測値）へ退避することで、形状不変の微小変更に耐える。
 *
 * ⚠️ **この機構は予防であって、実測された揺れに基づいていない**（ADR 0041 決定3・裏取りは
 * `docs/knowledge/dimension-autodetect-was-preventive.md`）。導入時（2026-07-16）に「寸法が揺れる文書」を
 * 観測した事実は無く、当時唯一疑われたインシデント（N2959KI の章分割破綻）は**寸法揺れ仮説が明示的に
 * 棄却**されている（真因は「単話には章見出しグリフが存在しない」という別の設計漏れ）。導入後に本機構の
 * 検出値がフォールバック定数と食い違って挙動を救ったバグ修正は **0 件**で、逆に検出の中間表を肥大させて
 * OOM の一因になった実例（`e0165c7`）と、検出が固定値より**悪化**させた実例（[fundamentalStepRefined]
 * の KDoc＝倍音を掴んで本文空行が全滅した）は在る。2026-08-30 の独立再実装は 23,640 ページで
 * mm 格子の逸脱ゼロ＝「揺れが実在しない」を支持する新証拠（ただしサンプルは単一生成系）。
 *
 * それでも**撤去しない**＝既に動いており最適化も済んだものを剥がす動機が、将来なろう公式の生成器が
 * 寸法を微調整したときの全滅リスクを上回らない、というのが裁定（ADR 0041 決定3）。この段落は
 * 「効いている証拠がある機構」と誤読させないための注記であり、**新しい寸法をここへ足すときは
 * 同じ理由で足りるかを先に問うこと**（実測された揺れが無い項目は、固定定数＋テストでの全数検証の方が
 * 誤検出の面を作らない。実例＝[ParserRules.COLUMN_CAPACITY]）。
 *
 * 検出は純関数 [detect]（I/O・時刻・乱数に非依存）で、同一入力に対し決定的な値を返す。
 */
data class DetectedRules(
    val bodySize: Double,
    val rubySize: Double,
    val pageNumSize: Double,
    val pageNumY: Double,
    val rubyOffsetX: Double,
    val lineStepX: Double,
    /** 1 ページの本文列グリッドの横幅（右端列 x0 −左端列 x0）。ページ跨ぎの空行数の算出に使う。 */
    val columnSpanX: Double = ParserRules.COLUMN_SPAN_X,
) {
    companion object {
        /**
         * 検出不能時のフォールバック＝現行 PDF 形状の実測値（[ParserRules] の定数群が正本）。
         * 少ページ／統計不足の文書ではここへ退避し、現行挙動をそのまま維持する。
         */
        val FALLBACK = DetectedRules(
            bodySize = ParserRules.FONT_SIZE_BODY_TITLE,
            rubySize = ParserRules.FONT_SIZE_RUBY,
            pageNumSize = ParserRules.FONT_SIZE_PAGE,
            pageNumY = ParserRules.PAGE_NUM_Y,
            rubyOffsetX = ParserRules.RUBY_OFFSET_X,
            lineStepX = ParserRules.LINE_STEP_X,
            columnSpanX = ParserRules.COLUMN_SPAN_X,
        )

        /** 0.1pt/0.1px 単位のバケットキー（実測ヒストグラムの粒度。較正プローブと同一の丸め）。 */
        private fun bucket01(v: Double): Double = Math.round(v * 10.0) / 10.0

        /**
         * バケットカウンタの最頻キーを返す（空なら null）。同数タイは小さいキー優先で決定的にする
         * （HashMap 反復順に依存すると同一入力で結果が揺れ、合成テストが非決定になるため）。
         *
         * 決定性のこの一点を [bucketModeRefined] と bodySize 検出（カウンタを直接積む経路）で共有し、
         * 選び方が2箇所へ分岐しないようにする。
         */
        private fun modeOfBucketCounts(counts: Map<Double, Int>): Double? {
            if (counts.isEmpty()) return null
            return counts.entries
                .sortedWith(compareByDescending<Map.Entry<Double, Int>> { it.value }.thenBy { it.key })
                .first().key
        }

        /**
         * 最頻 0.1 バケットを選び、そのバケット内の生値の中央値で精緻化して返す。
         * なぜ中央値: 0.1 丸めだと 22.7 のような境界値に量子化されるが、閾値比較・除算丸めには真値
         * （22.68 等）が要る。最頻バケットへ生値を集め直してから中央値を採ることで量子化誤差を外す。
         *
         * なぜ生値リストでなく**出現回数表**を受けるか（保持量の真因対処）: 旧実装は列間差分と
         * ルビ横オフセットを `ArrayList<Double>` へ 1 件ずつ積んでいた＝**件数がページ数に比例**して
         * 伸びる（長編ではルビ 1 グリフごとに 1 件）。集計に要るのは「どの値が何回出たか」だけで
         * 同じ値の重複を持つ必要は無く、回数表に畳むと保持量は**文書の幾何的な種類数**で頭打ちになる。
         * 選ぶ値は畳む前と同一＝丸めは同じ [bucket01]、最頻は同じ [modeOfBucketCounts]（同数タイは
         * 小さいキー優先）、中央値も同じ「昇順に並べて中央（偶数個は中央 2 値の平均）」を回数つきで数える。
         */
        private fun bucketModeRefined(freq: Map<Double, Int>): Double =
            refineWithinBucket(freq, pickBucket(freq, fundamental = false))

        /**
         * 列ピッチ（1 行ぶんの x 移動量）専用の選び方＝**基本波を採る**。
         *
         * なぜ最頻ではいけないか（S3 の真因）: 列間距離は必ず「1 行ぶん × 整数」で現れる。
         * 段落間に必ず空行を置く文書では **2 行ぶんの距離が最頻**になり、最頻値を採ると
         * lineStepX が真値の 2 倍にロックされる（N0833HI 実測: 検出 45.36 ／ 真値 22.68。
         * 列間距離の分布は 45.4×481・22.7×313）。すると空行判定 `diffX > lineStepX*1.5` の閾値が
         * 68.04 まで上がり、本物の空行（45.4）が 1 つも引っ掛からず
         * **本文中の空行が全滅**した（実測 本文空行 17 対 オラクル 10,178）。
         * 皮肉なことにフォールバック定数 [ParserRules.LINE_STEP_X]=22.68 なら正しく動いていた＝
         * 自動検出が固定値より悪化させていた事例。
         *
         * 直し方: 倍音ではなく基本波＝**最頻値の約数 mode/k のうち、支持のある最小**を採る
         * （最頻値は必ず基本波の整数倍なので、基本波は必ず約数の側に在る）。候補を約数に限らないと、
         * 半角字のずれ由来の小さな偽ピッチ（実測 N6169DZ: 5.1・17.6＝最頻 22.7 と整除関係が無い）を
         * 基本波と誤認して lineStepX が崩壊する（実測で空行が 58,341→687,336 に暴発した）。
         */
        private fun fundamentalStepRefined(freq: Map<Double, Int>): Double =
            refineWithinBucket(freq, pickBucket(freq, fundamental = true))

        /** 0.1 バケットへ畳んで、最頻（または支持のある最小の約数＝基本波）のバケットを返す。 */
        private fun pickBucket(freq: Map<Double, Int>, fundamental: Boolean): Double {
            val bucketCounts = HashMap<Double, Int>()
            for ((v, n) in freq) { val b = bucket01(v); bucketCounts[b] = (bucketCounts[b] ?: 0) + n }
            val mode = modeOfBucketCounts(bucketCounts)!!
            if (!fundamental) return mode
            val total = bucketCounts.values.sum()
            val floor = maxOf(3, (total * FUNDAMENTAL_SUPPORT_RATIO).toInt())
            // 候補は**最頻値の約数だけ**に限る。最頻値は必ず「基本波 × 整数」なので、基本波は
            // mode/k のいずれか。単に「支持のある最小バケット」を採ると、半角字のずれ由来の
            // 小さな偽ピッチ（実測 N6169DZ: 5.1・17.6。最頻 22.7 とは整除関係が無い）を拾って
            // lineStepX が崩壊する（実測: 空行が 58,341→687,336 に暴発した）。
            for (k in MAX_STEP_HARMONIC downTo 2) {
                val candidate = bucket01(mode / k)
                if ((bucketCounts[candidate] ?: 0) >= floor) return candidate
            }
            return mode
        }

        /** バケット内を回数つき中央値で精緻化する（畳む前のリスト median と同値）。 */
        private fun refineWithinBucket(freq: Map<Double, Int>, mode: Double): Double {
            val inBucket = freq.entries.filter { bucket01(it.key) == mode }.sortedBy { it.key }
            val total = inBucket.sumOf { it.value }
            // 昇順に並べたときの中央位置。偶数個は中央 2 値の平均＝ソート済みリストの median と同値。
            val lowerIndex = if (total % 2 == 1) total / 2 else total / 2 - 1
            val upperIndex = total / 2
            var seen = 0
            var lower = Double.NaN
            for ((value, n) in inBucket) {
                seen += n
                if (lower.isNaN() && seen > lowerIndex) lower = value
                if (seen > upperIndex) return (lower + value) / 2.0
            }
            return lower
        }

        /**
         * 文書全ページの文字配置から解析パラメータを検出する。各項目は独立にフォールバックする。
         *
         * ページは [PageGlyphSource] から **1 ページずつ**受け取り、走査を跨いで CharBox を保持しない
         * （保持量をページ数から切り離す＝OOM の真因対処）。走査は [STREAMING_PASSES] 回:
         * - ①走査: 全グリフのサイズヒストグラム → bodySize
         * - ②走査: bodySize を前提とする「ページ番号シグネチャ」「列ピッチ」「ルビ横オフセット」
         *
         * ②を①に畳めない理由: ②の分類はすべて bodySize との近さで決まるが、bodySize は文書を
         * 最後まで読み切るまで確定しない（最頻値のため）。候補ごとの仮説を全ページ並走させれば 1 走査に
         * できるが、候補集合自体が読み切るまで確定せず、途中で現れた候補は過去ページを取りこぼす
         * ＝正しさを保てないので採らない。
         *
         * @param totalPages 文書の総ページ数（ページ番号シグネチャの再出率判定に使う）。
         */
        // internal なのは [PageGlyphSource]（モジュール内部の実装詳細）がシグネチャに現れるため。
        // 外部公開の入口は下の materialize 済みリストを取る overload。
        internal fun detect(source: PageGlyphSource, totalPages: Int): DetectedRules {
            // --- ①走査 bodySize: 全 CharBox サイズの 0.1 バケット最頻。ヒストグラム空（＝文字ゼロ）なら FALLBACK。
            //     ページ配列を直接走査してバケットカウンタへ積む（flatten や map{it.size} の一時リストを作らない
            //     ＝338万グリフで概算100MB超の確保が消える。2026-08-17 計測）。
            val sizeBuckets = HashMap<Double, Int>()
            source.forEachPage { _, page ->
                for (c in page) {
                    val b = bucket01(c.size)
                    sizeBuckets[b] = (sizeBuckets[b] ?: 0) + 1
                }
            }
            val bodySize = modeOfBucketCounts(sizeBuckets) ?: FALLBACK.bodySize

            // --- rubySize: 本文×0.5（実測でルビは厳密に本文の半分）。ヒストグラム出現は要求しない
            //     ＝ルビが無い文書では size==rubySize の分類が発火しないだけで、値自体は常に定義できる。
            val rubySize = bodySize * 0.5

            // --- ②走査。以下3項目はいずれも bodySize を前提にするので同じ走査に相乗りさせる。
            //   - pageNum: 本文サイズ以外の (サイズ0.1バケット, top1.0バケット) シグネチャで、
            //     出現ページ数（＝ページ再出率）が最大の組を採用。少ページ文書は統計が立たないため
            //     再出率 >=0.5 かつ 総ページ>3 のときだけ採用し、それ以外は FALLBACK。
            //     ⚠️ 出現ページの記録に [BitSet] を使う: 旧実装は組ごとに `MutableSet<Int>` を持っており、
            //     1 ページ 1 要素＝**組数 × ページ数**の確保になっていた（長編ではルビ由来の組が数百生まれ、
            //     組あたり数百 KB＝これ自体が OOM の第二の原因）。ビット列なら 1 ページ 1 ビットで、
            //     判定に要る「異なるページ数」は cardinality() がそのまま返す＝値は完全に同一。
            //   - lineStepX: 列 x0 を降順整列した隣接差分（>0 のみ）を全ページ集計→最頻 0.1 バケット→
            //     バケット内中央値で精緻化。サンプル<10 は統計不足で FALLBACK。
            //   - rubyOffsetX: ルビサイズ帯(rubySize±0.1)の文字 x0 と「その x0 未満で最大の本文列 x0」との
            //     差分を全ページ集計→最頻 0.1 バケット→バケット内中央値。サンプル<10 は FALLBACK。
            //     なぜ主峰のみ: 実測は二峰性（主峰≈14.8・副峰≈9.8が約10%）。副峰 9.8 群は現行定数 14.84 でも
            //     isClose(±0.1) の窓から外れて取りこぼしており、挙動保存のため主峰だけを検出する。
            //     lineStepX と rubyOffsetX は同じ列復元結果を共有する（別々に2周すると engine の 5〜6% を捨てる）。
            val comboPages = HashMap<Pair<Double, Double>, BitSet>()
            val stepFreq = HashMap<Double, Int>()
            val offFreq = HashMap<Double, Int>()
            // ページ先頭列 / 末尾列の x0 の出現回数（列グリッド幅 columnSpanX の検出用）。
            val firstColFreq = HashMap<Double, Int>()
            val lastColFreq = HashMap<Double, Int>()
            var colPageCount = 0
            var stepCount = 0
            var offCount = 0
            source.forEachPage { pageIndex, page ->
                for (c in page) {
                    if (ParserRules.isClose(c.size, bodySize)) continue // 本文サイズはページ番号候補から除外
                    val key = bucket01(c.size) to Math.round(c.top).toDouble()
                    comboPages.getOrPut(key) { BitSet() }.set(pageIndex)
                }

                val bodyColKeys = TextProcessor.groupCharsByLine(
                    page.filter { ParserRules.isClose(it.size, bodySize) }
                ).keys

                val descending = bodyColKeys.sortedDescending()
                if (descending.isNotEmpty()) {
                    val f = descending.first()
                    val l = descending.last()
                    firstColFreq[f] = (firstColFreq[f] ?: 0) + 1
                    lastColFreq[l] = (lastColFreq[l] ?: 0) + 1
                    colPageCount++
                }
                for (i in 0 until descending.size - 1) {
                    val d = descending[i] - descending[i + 1]
                    if (d > 0.0) {
                        stepFreq[d] = (stepFreq[d] ?: 0) + 1
                        stepCount++
                    }
                }

                if (bodyColKeys.isEmpty()) return@forEachPage
                for (r in page) {
                    // 走査順は同じなのでその場で弾く（ページごとの filter リストを作らない）。
                    if (!ParserRules.isClose(r.size, rubySize)) continue
                    // 親列 = ルビ x0 未満で最大の本文列 x0（associateRuby の targetX=r.x0-offset の逆算）。
                    // 「r.x0 未満の最大」は1パス走査でも同値なので、確保を伴わない形で求める。
                    var parent: Double? = null
                    for (x in bodyColKeys) {
                        if (x < r.x0 && (parent == null || x > parent)) parent = x
                    }
                    if (parent != null) {
                        val off = r.x0 - parent
                        offFreq[off] = (offFreq[off] ?: 0) + 1
                        offCount++
                    }
                }
            }

            val bestPn = comboPages.entries.maxByOrNull { it.value.cardinality() }
            val (pageNumSize, pageNumY) =
                if (bestPn != null && totalPages > 3 &&
                    bestPn.value.cardinality().toDouble() / totalPages >= 0.5
                ) {
                    bestPn.key.first to bestPn.key.second
                } else {
                    FALLBACK.pageNumSize to FALLBACK.pageNumY
                }

            val lineStepX = if (stepCount >= 10) fundamentalStepRefined(stepFreq) else FALLBACK.lineStepX
            val rubyOffsetX = if (offCount >= 10) bucketModeRefined(offFreq) else FALLBACK.rubyOffsetX
            val columnSpanX = detectColumnSpanX(firstColFreq, lastColFreq, colPageCount)

            return DetectedRules(
                bodySize = bodySize,
                rubySize = rubySize,
                pageNumSize = pageNumSize,
                pageNumY = pageNumY,
                rubyOffsetX = rubyOffsetX,
                lineStepX = lineStepX,
                columnSpanX = columnSpanX,
            )
        }

        /**
         * 本文列グリッドの横幅（右端列 x0 −左端列 x0）を、ページ先頭列/末尾列の出現分布から検出する。
         *
         * なぜ「最頻」でなく**支持のある最大 first ／支持のある最小 last**か:
         * 観測できる先頭列 x0 は必ず真の右端 ≦ で、末尾列 x0 は必ず真の左端 ≧ になる
         * （空行や章末の余白ぶん内側へ寄るだけで、グリッドの外へは出られない）。よって
         * この採り方の誤差は**必ず過小側**＝ページ跨ぎの空行数を過大に見積もることが原理的に無い
         * ＝**新しい誤検出を作らない**。最頻値にすると「毎ページ先頭が空行」のような文書で
         * 1 列ぶん内側に張り付き、やはり過小側に倒れる（安全側だが精度が落ちる）。
         *
         * 支持の下限を置くのは、半角プロポーショナル字のずれ（[TextProcessor.groupCharsByLine] の
         * KDoc 参照＝x0 が右へ最大 0.25 列ぶんずれる）や外れ値 1 件で両端が動かないようにするため。
         * ずれの大きさは 1 列の半分に満たないので、下の round で吸収され列数は変わらない。
         *
         * 統計不足（支持のある候補が無い）なら [FALLBACK] へ退避する＝他項目と同じ方針。
         */
        private fun detectColumnSpanX(
            firstColFreq: Map<Double, Int>,
            lastColFreq: Map<Double, Int>,
            pageCount: Int,
        ): Double {
            if (pageCount < COLUMN_SPAN_MIN_PAGES) return FALLBACK.columnSpanX
            val floor = maxOf(COLUMN_SPAN_MIN_SUPPORT, (pageCount * COLUMN_SPAN_SUPPORT_RATIO).toInt())
            val right = firstColFreq.entries.filter { it.value >= floor }.maxOfOrNull { it.key }
            val left = lastColFreq.entries.filter { it.value >= floor }.minOfOrNull { it.key }
            if (right == null || left == null || right <= left) return FALLBACK.columnSpanX
            return right - left
        }

        /**
         * 全ページを materialize 済みのリストから検出する薄いラッパー（既存呼び出し・テスト用）。
         * 中身は [detect] と同一で、走査が再パースでなくメモリ上の再走査になるだけ。
         */
        fun detect(charListsByPage: List<List<CharBox>>): DetectedRules =
            detect(MaterializedPageSource(charListsByPage), charListsByPage.size)

        /**
         * 列ピッチの基本波とみなすのに要る支持率（全サンプルに対する割合）。
         * 実測の偽ピッチ（N6169DZ の 5.1・17.6 は全体の 1% 未満）を基本波と誤認せず、
         * 本物の 1 行ピッチ（同 22.7 は 60%超・N0833HI でも 313/791＝40%）は必ず拾える位置に置く。
         */
        /**
         * 列ピッチの最頻値が基本波の何倍までを想定するか。段落間に空行1つ（＝2倍）が実測の最大で、
         * 余裕を見て 4 まで見る。大きくし過ぎると偶然 mode/k に当たる無関係なバケットを拾いうる。
         */
        private const val MAX_STEP_HARMONIC = 4

        private const val FUNDAMENTAL_SUPPORT_RATIO = 0.05

        /** 列グリッド幅の検出に要る最小ページ数。これ未満は統計が立たない＝[FALLBACK] へ退避する。 */
        private const val COLUMN_SPAN_MIN_PAGES = 4

        /** 列グリッド幅の両端候補に要る最小支持ページ数（比率が下回るときの床）。 */
        private const val COLUMN_SPAN_MIN_SUPPORT = 3

        /** 列グリッド幅の両端候補に要る支持率（全本文ページに対する割合）。 */
        private const val COLUMN_SPAN_SUPPORT_RATIO = 0.05

        /** [detect] がストリーミング供給源に対して要求する走査回数（進捗の総数計算に使う）。 */
        const val STREAMING_PASSES = 2
    }
}
