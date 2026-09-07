package com.novelreader.typeset

/**
 * 組版の最小単位。1書記素、または縦中横として1マスに収める部分文字列（"12" 等）。
 * @param text 表示文字列
 * @param charClass この単位の向き
 * @param isRubyBase ルビ親文字か（RubyPlacer が親文字スパンを求めるのに使う）
 * @param segmentIndex Ruby セグメント由来ならその通し番号。非ルビは -1
 */
data class TypesetUnit(
    val text: String,
    val charClass: CharClass,
    val isRubyBase: Boolean,
    val segmentIndex: Int,
)

/**
 * 列内に配置済みの単位。yTop は列内でのこの字の天（上端）オフセット px。
 */
data class PlacedUnit(
    val unit: TypesetUnit,
    val yTop: Float,
    val advance: Float,
)

/** 1列（縦の1行分）。単位は上から下（yTop 昇順）に並ぶ。 */
data class Column(val units: List<PlacedUnit>)

/**
 * 禁則つきで単位列を縦の列へ折る（純ロジック）。
 *
 * 実装済みの禁則:
 * - 行頭禁則（列頭に置けない字）: **句点類・読点類だけ「ぶら下げ」**（版面外へ1字ぶん出す・1列1回まで）、
 *   それ以外は**「追い出し」**＝直前の単位を道連れに次列へ送り、列高超過を作らない（JLReq §3.1.7 / §3.1.12）。
 * - 行末禁則（列末に置けない開き括弧）: 次列へ「追い出し」。
 * - 段落頭インデント: 有効時、最初の列の先頭に fontSizePx×1 の空きを入れる。
 * 縦中横 run・ルビ親文字も1ユニットとして通常どおり折る（親文字列の分断は許容＝按分は RubyPlacer の仕事）。
 *
 * ## 禁則字集合の出所（2026-09-03 改訂＝差分表 B-1〜B-3・B-11 の着地）
 * 規範は JLReq（`https://www.w3.org/TR/jlreq/`）§3.1.7 行頭禁則 / §3.1.8 行末禁則 / §3.1.12 ぶら下げ組。
 * ⚠️ **規範をそのまま写しても足りない**——実データ（なろう公式PDF）には規範が収録していない字形が主役で出る
 * （`〟`U+301F・`～`U+FF5E・`－`U+FF0D）。**採否は「JLReq に載っているか」でなく「実データに出るか」で決める**
 * のがこの表の運用規則（2026-09-03 の人間裁定）。
 * 出現数は sample_pdfs 全10本を現行抽出（[com.novelreader.pdf.PdfExtractor.runFinalEngine]）に通した全数実測で、
 * 各字の脇に「総数（内訳の主）」で書いてある。0 件の字も**族として**残す場合はその旨を明記する。
 * 差分の棚卸しと裁定の正本＝`docs/knowledge/vertical-typeset-external-oracle-gap.md`。
 */
object LineBreaker {

    /**
     * 行頭禁則（列頭に来てはいけない）。
     *
     * 既存分＝約物・小書き仮名・閉じ括弧・繰返し記号（cl-02/04/05/06/07/09/10/11）。
     * ⚠️ `…‥` は JLReq では cl-08 分離禁止であって行頭禁則ではない＝**実装が規範より厳しい**（差分表 B-10）。
     * 追い込み方式では超過を生む害があったが、B-11 で「ぶら下げ以外は追い出し」へ倒したため害が消えた＝温存。
     */
    private val LINE_HEAD_FORBIDDEN: Set<Char> = (
        "、。，．・：；？！ー…‥ゝゞ々" +
            "ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ" +
            "」』）〕］｝〉》】" +
            // ── B-2: 縦書き用の閉じ二重引用符（cl-02） ──
            // 〟U+301F ＝ 593 件（N8809BK。開き 〝 と同数で対になる）。JLReq cl-02 収録字。
            // 〞U+301E ＝ **0 件**（ADR 0041 で抽出を web 原文一致へ直した結果、実データからは消えた）。
            // 0 件でも残す理由は CharClass.PUNCT_REPOSITION_CHARS と同じ＝同族で扱いが同一・収録費用が
            // Set 1 要素、かつ他の生成器が出したときに落とす理由が無いため。
            "〟〞" +
            // ── B-3: 波ダッシュ（cl-03 ハイフン類）。**規範は 〜 のみ収録・実データは両方出る** ──
            // 〜U+301C ＝ 565 件（N3957FQ 254 / N6169DZ 261 / N8809BK 45 / N7038HV 5）
            // ～U+FF5E ＝ 516 件（N8809BK 169 / N6169DZ 124 / N0833HI 106 / N9463BR 75 / N7038HV 30 ほか）
            // PDF は両者を撃ち分けて保持しており、どちらかへ寄せるのは情報の破壊（ADR 0041）。
            "〜～" +
            // ── 差分表に行が無かったが実データに出た字（2026-09-03 の全数実測で判明）──
            // ⚠️ 下 3 群は元の差分表が「実文コーパス（52,765 字）で 0 件」と判定して温存に倒したもの。
            // 全10本の PDF を数え直すと 0 件ではなかった＝**表に無いことは存在しない証拠にならない**の再演。
            // ・cl-02 終わり二重引用符 ”U+201D ＝ 5,416 件（N9463BR 2,222 / N2367FS 1,807 / N6169DZ 1,313 ほか）
            //   ’U+2019 ＝ 29 件（N8809BK 25 ほか。アポストロフィ用途も含むが行頭に来ない点は同じ）
            // ・cl-04 区切り約物 ‼U+203C ＝ 756 件（N3957FQ）／⁉U+2049 ＝ 501 件（N3957FQ 497 / N7038HV 4）
            //   ⚠️ 実データではどちらも VS15(U+FE0E) を伴う書記素で届く（[stripVariationSelectors] 参照）。
            //   ⁇U+2047・⁈U+2048 は 0 件のため足さない（出ない字を足す根拠が無い）。
            // ・cl-09 繰返し記号 ヽU+30FD ＝ 8 件・ヾU+30FE ＝ 2 件（ともに N0833HI）。〻U+303B は 0 件＝足さない。
            // ・cl-13 後置省略記号 ％U+FF05 ＝ 321 件／″U+2033 ＝ 10 件／°U+00B0 ＝ 9 件／℃U+2103 ＝ 3 件／
            //   ′U+2032 ＝ 2 件。‰U+2030・¢U+00A2 は 0 件＝足さない。
            "”’‼⁉ヽヾ％″°℃′" +
            // ── 規範に**無い**と一次ソースで確定したうえで、運用規則に従って収録した字（2026-09-07 照合）──
            // －U+FF0D ＝ 1,874 件（N6169DZ 1,057 / N3957FQ 745 ほか）。web 原文の半角 `-` を PDF 生成器が
            // 全角化したもの（`extraction-charmap-diverges-from-web-source.md`）。
            // ⚠️ 旧コメントの「JLReq cl-03 の UCS 欄に FF0D が在るかは未確認」を実際に当たった結果＝**不在**。
            //   JLReq 付録A cl-03（ハイフン類）の UCS 欄は 2010 / 301C / 30A0 / 2013 の 4 字だけで、
            //   付録A 全体（A.1〜A.30）を通しても FF0D は 1 件も無い（`https://www.w3.org/TR/jlreq/#cl-03`。
            //   ⚠️ 罠: A.18 演算記号の減算記号は**字形セルの実体が FF0D** だが UCS 欄の宣言は 2212＝
            //   字形だけ見ると誤読する。コードポイント欄で見ること）。
            // ∴ 規範からは導けない。それでも収録するのは、この表の運用規則が
            //   「採否は JLReq 収録でなく実データ出現で決める」（2026-09-03 の人間裁定）だから＝規則どおりの判断。
            //   ⚠️ Unicode 側も「行頭禁則にせよ」とは言っていない（`LineBreak.txt`: `FF0D ; ID`＝表意文字扱いで
            //   前後どちらでも分割可・`UnicodeData.txt`: 分解が `<wide> 002D`＝ASCII ハイフンの全角互換形）。
            //   ID は全角互換形一般に付く既定であって組版上の役割を表さないので、原文の意図（ハイフン類）に
            //   沿った B-3 の ～U+FF5E と同じ扱いへ寄せる。外す判断もありえたが、実データ 1,874 件のハイフンが
            //   列頭に立つ版面を許すだけで実益が無いため温存する。
            "－"
        ).toSet()

    /**
     * 行末禁則（列末に来てはいけない）＝開き括弧類（cl-01）と前置省略記号（cl-12）。
     */
    private val LINE_END_FORBIDDEN: Set<Char> = (
        "「『（〔［｛〈《【" +
            // ── B-1: 縦書き用の開き二重引用符（cl-01）。〝U+301D ＝ 593 件（N8809BK）──
            // 列末に開き引用符が居残る欠陥がそのまま実データの規模で出ていた。
            "〝" +
            // ── 差分表に行が無かったが実データに出た字 ──
            // cl-01 始め二重引用符 “U+201C ＝ 1,872 件（N2367FS 1,814 ほか）／‘U+2018 ＝ 2 件。
            //   ⚠️ 旧コメントの「JLReq 付録 A での cl-01 収録は未確認」を実際に当たった結果＝**収録あり**
            //   （2026-09-07・`https://www.w3.org/TR/jlreq/#cl-01`。cl-01 の UCS 欄に 2018 と 201C が在り、
            //   備考は「横組で使用」。対の 201D・2019 も cl-02 終わり括弧類に収録＝上の「”’」も規範裏付きになった。
            //   縦組用の 301D/301F は同じ cl-01/cl-02 の別行）。∴ 採用根拠は「対で出る実測」からの類推止まりでなく
            //   **規範どおり**へ格上げ済み。実データ側の裏（N2367FS 1,814/1,807・N8809BK 36/36・N0833HI 9/9）も同じ結論。
            // cl-12 前置省略記号 ＃U+FF03 ＝ 18 件（N0833HI 17 ほか）／＄U+FF04 ＝ 2 件（N6169DZ）。
            //   ￥U+FFE5・€U+20AC・№U+2116 は 0 件＝足さない。
            "“‘＃＄"
        ).toSet()

    /**
     * ぶら下げを許す字＝JLReq §3.1.12 で「句点類（cl-06）及び読点類（cl-07）に限り」版面外へ出してよい字。
     * 実データの出現数: 。254,896 ／ 、256,670 ／ ．1,665 ／ ，58（全10本合計）。
     */
    private val HANGING_ALLOWED: Set<Char> = "。．、，".toSet()

    /**
     * 追い出しで道連れにする直前ユニットの上限。
     *
     * なぜ上限が要るか: 道連れの先頭がまた行頭禁則なら遡り続ける設計（「あ。」」のような連なりを
     * 列頭に禁則字を残さず送るため）だが、遡り過ぎると次列が道連れで溢れる＝
     * **列高超過を無くすための処理が列高超過を作る**。実文で連なるのは 2 個程度（。」）なので 4 で足りる。
     */
    private const val MAX_PUSH_OUT_UNITS = 4

    /** 異体字セレクタの範囲（U+FE00–FE0F）。字面をリテラルで書くと不可視で読めないためコード値で持つ。 */
    private val VARIATION_SELECTORS = '\uFE00'..'\uFE0F'

    /**
     * 異体字セレクタ（U+FE00–FE0F）を落とした素の字面。
     *
     * なぜ要るか: 実データの `‼`U+203C・`⁉`U+2049 は VS15(U+FE0E) を伴う書記素で届く
     * （N3957FQ の FE0E 1,267 件 ≒ 203C 756 + 2049 497）。禁則判定は「全字が禁則字か」を見るので、
     * VS が 1 文字混じるだけで false になり、**禁則表に足しても黙って効かない**（登録したのに効かない
     * のは、効かないと分かっているより悪い）。判定の直前で VS を落として素の字面で照合する。
     */
    private fun stripVariationSelectors(text: String): String =
        if (text.none { it in VARIATION_SELECTORS }) text else text.filterNot { it in VARIATION_SELECTORS }

    // 全文字が禁則字なら禁則（結合リーダー「……」等の複数字ユニットも行頭禁則を維持するため。
    // 縦中横 "12" 等は数字が禁則表に無いので該当しない）。
    private fun isLineHeadForbidden(text: String): Boolean {
        val s = stripVariationSelectors(text)
        return s.isNotEmpty() && s.all { it in LINE_HEAD_FORBIDDEN }
    }

    private fun isLineEndForbidden(text: String): Boolean {
        val s = stripVariationSelectors(text)
        return s.length == 1 && s[0] in LINE_END_FORBIDDEN
    }

    /** ぶら下げてよい字か（1書記素の句読点だけ＝結合リーダー等の複数字ユニットは対象外）。 */
    private fun isHangingPunctuation(text: String): Boolean {
        val s = stripVariationSelectors(text)
        return s.length == 1 && s[0] in HANGING_ALLOWED
    }

    /**
     * @param units 展開済み単位列（読み順）
     * @param columnHeightPx 1列の縦容量 px
     * @param indentFirstColumn 段落頭インデントを最初の列に入れるか
     * @return 列ごとの配置結果（各単位に列内 y を確定）
     */
    fun breakIntoColumns(
        units: List<TypesetUnit>,
        columnHeightPx: Float,
        metrics: FontMetricsProvider,
        fontSizePx: Float,
        indentFirstColumn: Boolean,
    ): List<Column> {
        val columns = ArrayList<Column>()
        var current = ArrayList<PlacedUnit>()
        // 段落頭インデントは最初の列にだけ効かせる（改列時は y=0 に戻すので自然に1列目限定になる）。
        var y = if (indentFirstColumn) fontSizePx else 0f

        for (unit in units) {
            val adv = metrics.verticalAdvance(unit.text, unit.charClass, fontSizePx)
            var needBreak = current.isNotEmpty() && (y + adv > columnHeightPx)
            // 行頭禁則にあたる単位を「直前の単位を道連れに次列へ送る」で解消するか（B-11 の追い出し）。
            var pushOutTrailing = false

            if (needBreak && isLineHeadForbidden(unit.text)) {
                // ぶら下げ: 句点類・読点類だけは版面外へ 1 字ぶん出す（JLReq §3.1.12）。
                // 1列につき1回に限る判定が `y <= columnHeightPx`＝既に何かがぶら下がった列は y が列高を
                // 超えているので二重にはぶら下がらない。旧実装はここで全ての行頭禁則字を無条件に追い込んで
                // おり、連続すれば超過は無制限だった（差分表 B-11）。
                if (isHangingPunctuation(unit.text) && y <= columnHeightPx) {
                    needBreak = false
                } else {
                    pushOutTrailing = true
                }
            }

            if (needBreak) {
                val carried = ArrayList<PlacedUnit>()
                if (pushOutTrailing) {
                    // 直前の単位を道連れにする＝この行頭禁則字が次列の頭に来ないようにする。
                    // 道連れの先頭がまた行頭禁則なら（「あ。」」等）さらに遡る。上限は MAX_PUSH_OUT_UNITS。
                    // なぜ size>1 か: 列を空にしてまで追い出すと列が消える＝行末禁則側と同じ妥協。
                    while (current.size > 1 && carried.size < MAX_PUSH_OUT_UNITS) {
                        carried.add(0, current.removeAt(current.size - 1))
                        if (!isLineHeadForbidden(carried.first().unit.text)) break
                    }
                }
                // 行末禁則の追い出し: 閉じる直前の列末が開き括弧なら次列先頭へ移す。
                // なぜ size>1 条件か: 列が開き括弧だけになる場合は追い出し不能（空列化を避け、そのまま残す）。
                while (current.size > 1 && isLineEndForbidden(current.last().unit.text)) {
                    carried.add(0, current.removeAt(current.size - 1))
                }
                columns.add(Column(current.toList()))
                current = ArrayList()
                y = 0f
                for (c in carried) {
                    current.add(PlacedUnit(c.unit, y, c.advance))
                    y += c.advance
                }
            }

            current.add(PlacedUnit(unit, y, adv))
            y += adv
        }

        if (current.isNotEmpty()) columns.add(Column(current.toList()))
        return columns
    }
}
