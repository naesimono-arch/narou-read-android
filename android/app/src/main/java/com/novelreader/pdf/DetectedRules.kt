package com.novelreader.pdf

/**
 * 1 文書ぶんの解析パラメータを、その文書の文字配置から自動検出した結果。
 *
 * なぜ: [ParserRules] の絶対値定数は現行 PDF 出力形状（フォントサイズ・列ピッチ・ページ番号座標）への
 * ハードコードで、生成側が「同じ形状のまま」寸法だけ微調整すると全滅しうる。文書ごとに実測して寸法を
 * 相対化し、検出できない項目だけ [FALLBACK]（＝現行実測値）へ退避することで、形状不変の微小変更に耐える。
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
        )

        /** 0.1pt/0.1px 単位のバケットキー（実測ヒストグラムの粒度。較正プローブと同一の丸め）。 */
        private fun bucket01(v: Double): Double = Math.round(v * 10.0) / 10.0

        /**
         * バケットカウンタの最頻キーを返す（空なら null）。同数タイは小さいキー優先で決定的にする
         * （HashMap 反復順に依存すると同一入力で結果が揺れ、合成テストが非決定になるため）。
         *
         * 決定性のこの一点を [modeBucketKey] と bodySize 検出（カウンタを直接積む経路）で共有し、
         * 選び方が2箇所へ分岐しないようにする。
         */
        private fun modeOfBucketCounts(counts: Map<Double, Int>): Double? {
            if (counts.isEmpty()) return null
            return counts.entries
                .sortedWith(compareByDescending<Map.Entry<Double, Int>> { it.value }.thenBy { it.key })
                .first().key
        }

        /** 生値リストを 0.1 バケットへ集計して最頻キーを返す（空なら null）。 */
        private fun modeBucketKey(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val counts = HashMap<Double, Int>()
            for (v in values) { val b = bucket01(v); counts[b] = (counts[b] ?: 0) + 1 }
            return modeOfBucketCounts(counts)
        }

        /** 昇順ソート済みリストの中央値（偶数個は中央 2 値の平均）。 */
        private fun median(sorted: List<Double>): Double {
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }

        /**
         * 最頻 0.1 バケットを選び、そのバケット内の生値の中央値で精緻化して返す。
         * なぜ中央値: 0.1 丸めだと 22.7 のような境界値に量子化されるが、閾値比較・除算丸めには真値
         * （22.68 等）が要る。最頻バケットへ生値を集め直してから中央値を採ることで量子化誤差を外す。
         */
        private fun bucketModeRefined(values: List<Double>): Double {
            val mode = modeBucketKey(values)!!
            val inBucket = values.filter { bucket01(it) == mode }.sorted()
            return median(inBucket)
        }

        /**
         * 文書全ページの文字配置から解析パラメータを検出する。各項目は独立にフォールバックする。
         *
         * @param charListsByPage [PdfExtractor.loadPages] が返す全ページ×全文字（表紙・注意ページ含む）。
         */
        fun detect(charListsByPage: List<List<CharBox>>): DetectedRules {
            val totalPages = charListsByPage.size

            // --- bodySize: 全 CharBox サイズの 0.1 バケット最頻。ヒストグラム空（＝文字ゼロ）なら FALLBACK。
            //     元は charListsByPage.flatten() で全グリフのコピーリストを作り、さらに map{ it.size } で
            //     全グリフぶんの boxed Double リストを作ってからヒストグラムへ渡していた。338万グリフの
            //     文書ではヒストグラム1本のために概算100MB超の一時確保が走る（そのどちらもここでしか
            //     使われていなかった）。ページ配列を直接走査してバケットカウンタへ積む形へ畳む。
            //     値が変わらない理由: 丸めは同じ bucket01、選び方は同じ modeOfBucketCounts（同数タイは
            //     小さいキー優先）で、flatten の連結順は件数の集計に影響しない。文字ゼロならカウンタも
            //     空になり、従来の「空リスト→null」と同じく FALLBACK へ退避する。
            val sizeBuckets = HashMap<Double, Int>()
            for (page in charListsByPage) {
                for (c in page) {
                    val b = bucket01(c.size)
                    sizeBuckets[b] = (sizeBuckets[b] ?: 0) + 1
                }
            }
            val bodySize = modeOfBucketCounts(sizeBuckets) ?: FALLBACK.bodySize

            // --- rubySize: 本文×0.5（実測でルビは厳密に本文の半分）。ヒストグラム出現は要求しない
            //     ＝ルビが無い文書では size==rubySize の分類が発火しないだけで、値自体は常に定義できる。
            val rubySize = bodySize * 0.5

            // --- pageNum: 本文サイズ以外の (サイズ0.1バケット, top1.0バケット) シグネチャで、
            //     出現ページ数（＝ページ再出率）が最大の組を採用。少ページ文書は統計が立たないため
            //     再出率 ≥0.5 かつ 総ページ>3 のときだけ採用し、それ以外は FALLBACK。
            val comboPages = HashMap<Pair<Double, Double>, MutableSet<Int>>()
            for ((pi, page) in charListsByPage.withIndex()) {
                for (c in page) {
                    if (ParserRules.isClose(c.size, bodySize)) continue // 本文サイズはページ番号候補から除外
                    val key = bucket01(c.size) to Math.round(c.top).toDouble()
                    comboPages.getOrPut(key) { mutableSetOf() }.add(pi)
                }
            }
            val bestPn = comboPages.entries.maxByOrNull { it.value.size }
            val (pageNumSize, pageNumY) =
                if (bestPn != null && totalPages > 3 &&
                    bestPn.value.size.toDouble() / totalPages >= 0.5
                ) {
                    bestPn.key.first to bestPn.key.second
                } else {
                    FALLBACK.pageNumSize to FALLBACK.pageNumY
                }

            // --- lineStepX / rubyOffsetX: どちらも「本文サイズの文字だけを列復元した結果」を入力にする。
            //     元はページごとに同じ filter+groupCharsByLine を2周して別々に組み立てていたが、
            //     完全に同一の計算なので1周へ畳んで共有する。畳む前は detect 実測の中で
            //     lineStepX 側 30.8% + rubyOffsetX 側 37.0% を占め、うち片方ぶんの列復元が
            //     まるごと重複していた（実測＝ExtractPhaseProfileTest の DetectBreakdown）。
            //     列キーは同じ groupCharsByLine の LinkedHashMap 由来で集合も順序も変わらないため、
            //     stepRaws / offRaws に積む値は畳み込み前と一致する。
            //   - lineStepX: 列 x0 を降順整列した隣接差分（>0 のみ）を全ページ集計→最頻 0.1 バケット→
            //     バケット内中央値で精緻化。サンプル<10 は統計不足で FALLBACK。
            //   - rubyOffsetX: ルビサイズ帯(rubySize±0.1)の文字 x0 と「その x0 未満で最大の本文列 x0」との
            //     差分を全ページ集計→最頻 0.1 バケット→バケット内中央値。サンプル<10 は FALLBACK。
            //     なぜ主峰のみ: 実測は二峰性（主峰≈14.8・副峰≈9.8が約10%）。副峰 9.8 群は現行定数 14.84 でも
            //     isClose(±0.1) の窓から外れて取りこぼしており、挙動保存のため主峰だけを検出する
            //     （副峰救済は将来の挙動変更＝本リファクタのスコープ外）。
            val stepRaws = ArrayList<Double>()
            val offRaws = ArrayList<Double>()
            for (page in charListsByPage) {
                val bodyColKeys = TextProcessor.groupCharsByLine(
                    page.filter { ParserRules.isClose(it.size, bodySize) }
                ).keys

                val descending = bodyColKeys.sortedDescending()
                for (i in 0 until descending.size - 1) {
                    val d = descending[i] - descending[i + 1]
                    if (d > 0.0) stepRaws.add(d)
                }

                if (bodyColKeys.isEmpty()) continue
                for (r in page) {
                    // 元は page.filter{ルビ帯} で新規リストを作っていたが、走査順は同じなのでその場で弾く
                    // （結果は同一で、ページごとのリスト確保だけが消える）。
                    if (!ParserRules.isClose(r.size, rubySize)) continue
                    // 親列 = ルビ x0 未満で最大の本文列 x0（associateRuby の targetX=r.x0-offset の逆算）。
                    // 元は bodyCols.filter{ it < r.x0 }.maxOrNull()＝ルビ1個ごとに新規リストを作っていた。
                    // 「r.x0 未満の最大」は1パス走査でも同値なので、確保を伴わない形へ置き換える。
                    var parent: Double? = null
                    for (x in bodyColKeys) {
                        if (x < r.x0 && (parent == null || x > parent)) parent = x
                    }
                    if (parent != null) offRaws.add(r.x0 - parent)
                }
            }
            val lineStepX = if (stepRaws.size >= 10) bucketModeRefined(stepRaws) else FALLBACK.lineStepX
            val rubyOffsetX = if (offRaws.size >= 10) bucketModeRefined(offRaws) else FALLBACK.rubyOffsetX

            return DetectedRules(
                bodySize = bodySize,
                rubySize = rubySize,
                pageNumSize = pageNumSize,
                pageNumY = pageNumY,
                rubyOffsetX = rubyOffsetX,
                lineStepX = lineStepX,
            )
        }
    }
}
