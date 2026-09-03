package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 禁則つき列分割の検証。FakeMonospaceMetrics（1マス=fontSizePx）で
 * fontSizePx=10・容量50px＝1列5マスの決定的な盤面を使う。
 */
class LineBreakerTest {

    private val metrics = FakeMonospaceMetrics()
    private val fontSize = 10f
    private val capacity = 50f // 5マス

    /** 1文字=1ユニット（charClass は折返しに無関係なので UPRIGHT 固定）でユニット列を作る。 */
    private fun units(text: String): List<TypesetUnit> =
        text.map { TypesetUnit(it.toString(), CharClass.UPRIGHT, isRubyBase = false, segmentIndex = -1) }

    private fun texts(column: Column): String = column.units.joinToString("") { it.unit.text }

    private fun breakUp(units: List<TypesetUnit>, indent: Boolean = false): List<Column> =
        LineBreaker.breakIntoColumns(units, capacity, metrics, fontSize, indent)

    @Test
    fun `普通の折返し`() {
        val cols = breakUp(units("あいうえおかき")) // 7字
        assertEquals(2, cols.size)
        assertEquals("あいうえお", texts(cols[0]))
        assertEquals("かき", texts(cols[1]))
        // y 積算の確認。
        assertEquals(0f, cols[0].units[0].yTop)
        assertEquals(40f, cols[0].units[4].yTop)
    }

    @Test
    fun `句読点だけはぶら下げる（版面外へ1字ぶん出す）`() {
        // 6字目が「。」→ JLReq §3.1.12 のぶら下げ組＝改列せず列高を1マスだけ超えて置く。
        val cols = breakUp(units("あいうえお。"))
        assertEquals(1, cols.size)
        assertEquals("あいうえお。", texts(cols[0]))
        // 容量超過（y=50 に配置）を許容している＝これが「ぶら下げ」。
        assertEquals(50f, cols[0].units[5].yTop)
    }

    @Test
    fun `ぶら下げは1列に1回だけ（2つ目の句読点は追い出し）`() {
        // 1つ目の「。」はぶら下がるが、2つ目は既に列高超過なのでぶら下げ不可＝追い出しへ倒す。
        val cols = breakUp(units("あいうえお。。"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        // 道連れは「。」→さらに遡って「お」まで（道連れの先頭が行頭禁則な限り遡る）。
        assertEquals("お。。", texts(cols[1]))
        // 追い出した先の列は列高内に収まる（30px < 50px）。
        assertEquals(20f, cols[1].units[2].yTop)
    }

    @Test
    fun `閉じ括弧は句読点でないので追い出し`() {
        // 旧実装は追い込み（列高超過）だった。B-11 でぶら下げ対象外＝直前の字を道連れに次列へ。
        val cols = breakUp(units("あいうえお」"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("お」", texts(cols[1]))
        assertEquals(0f, cols[1].units[0].yTop)
    }

    @Test
    fun `ぶら下げた句読点に閉じ括弧が続くと群ごと追い出す`() {
        val cols = breakUp(units("あいうえお。」"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("お。」", texts(cols[1]))
    }

    @Test
    fun `追い出しの道連れには上限がある（次列が道連れで溢れないため）`() {
        // 行頭禁則が5つ連なる病的な入力。道連れは MAX_PUSH_OUT_UNITS=4 で打ち切られ、
        // 列が空になることも無限に遡ることも無い（打ち切った結果、次列頭に禁則字が残るのは既知の妥協）。
        val cols = breakUp(units("あ」」」」」"))
        assertEquals(2, cols.size)
        assertEquals("あ", texts(cols[0]))
        assertEquals("」」」」」", texts(cols[1]))
    }

    // ---- B-1〜B-3: 実データに出る字の禁則登録（出現数は LineBreaker の KDoc が正本） ----

    @Test
    fun `B1 開き二重引用符は行末禁則で次列へ追い出す`() {
        // 〝U+301D＝N8809BK に 593 件。列末に開き引用符が居残るのが旧実装の欠陥。
        val cols = breakUp(units("あいうえ〝お"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("〝お", texts(cols[1]))
    }

    @Test
    fun `B2 閉じ二重引用符は行頭禁則`() {
        // 実データの閉じは 〟U+301F（593 件）。〞U+301E は実データ 0 件だが同族として登録済み。
        for (closing in listOf("〟", "〞")) {
            val cols = breakUp(units("あいうえお$closing"))
            assertEquals("閉じ $closing", 2, cols.size)
            assertEquals("閉じ $closing", "あいうえ", texts(cols[0]))
            assertEquals("閉じ $closing", "お$closing", texts(cols[1]))
        }
    }

    @Test
    fun `B3 波ダッシュは301Cも FF5Eも行頭禁則`() {
        // 規範（JLReq cl-03）が収録するのは 〜U+301C だけだが、実データは ～U+FF5E も 516 件出る。
        for (wave in listOf("\u301C", "\uFF5E")) {
            val cols = breakUp(units("あいうえお$wave"))
            assertEquals("波 $wave", 2, cols.size)
            assertEquals("波 $wave", "お$wave", texts(cols[1]))
        }
    }

    @Test
    fun `差分表に無かったが実データに出る字も禁則が効く`() {
        // 行頭禁則側: ”(5,416件) ‼(756件) ％(321件) －(1,874件) ヽ(8件)
        for (head in listOf("\u201D", "\u203C", "\uFF05", "\uFF0D", "\u30FD")) {
            val cols = breakUp(units("あいうえお$head"))
            assertEquals("行頭禁則 $head", 2, cols.size)
            assertEquals("行頭禁則 $head", "お$head", texts(cols[1]))
        }
        // 行末禁則側: “(1,872件) ＃(18件)
        for (end in listOf("\u201C", "\uFF03")) {
            val cols = breakUp(units("あいうえ${end}お"))
            assertEquals("行末禁則 $end", 2, cols.size)
            assertEquals("行末禁則 $end", "${end}お", texts(cols[1]))
        }
    }

    @Test
    fun `異体字セレクタつきの約物でも禁則が効く`() {
        // 実データの ‼⁉ は VS15(U+FE0E) を伴う書記素で届く（N3957FQ で 1,253 件）。
        // 素の集合照合だと VS が混じった瞬間に禁則が黙って無効化される＝その回帰。
        val list = units("あいうえお") +
            TypesetUnit("\u203C\uFE0E", CharClass.UPRIGHT, isRubyBase = false, segmentIndex = -1)
        val cols = breakUp(list)
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("お\u203C\uFE0E", texts(cols[1]))
    }

    @Test
    fun `列高を超えるのは句読点のぶら下げだけ（1マス以内）`() {
        // B-11 の不変条件を混成文で機械的に確かめる: 超過は「1マス以内」かつ「超過している列の
        // 末尾は句読点」でしかありえない。旧実装（追い込み一択）はここで無制限に超過していた。
        // 禁則字を密に混ぜた合成文（実文の引用ではなく、判定の分岐を網羅するための並び）。
        val text = "ここは列の折返しを試す文。次は閉じ括弧」そして読点、続けて閉じ引用〟" +
            "波ダッシュ～と全角ハイフン－を挟み、二重感嘆‼と百分率％も置く。" +
            "開き括弧「の直前で折れる場合と、リーダー……が来る場合も混ぜる。"
        val cols = breakUp(units(text))
        for ((i, col) in cols.withIndex()) {
            val bottom = col.units.last().let { it.yTop + it.advance }
            assertTrue("列$i が列高を超えるなら1マス以内: bottom=$bottom", bottom <= capacity + fontSize)
            if (bottom > capacity) {
                val last = col.units.last().unit.text
                assertTrue("超過している列$i の末尾は句読点であること: $last", last in listOf("。", "、", "．", "，"))
            }
        }
    }

    @Test
    fun `開き括弧は次列へ追い出し`() {
        // 5字目が開き括弧「で列末に来る→次列へ追い出し。前列は4字。
        val cols = breakUp(units("あいうえ「お"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("「お", texts(cols[1]))
        // 追い出された「は次列先頭 y=0。
        assertEquals(0f, cols[1].units[0].yTop)
    }

    @Test
    fun `段落頭インデントで先頭列は4字しか入らない`() {
        val cols = breakUp(units("あいうえおか"), indent = true)
        assertEquals(4, cols[0].units.size)
        assertEquals("あいうえ", texts(cols[0]))
        // インデント分 y は fontSizePx から始まる。
        assertEquals(10f, cols[0].units[0].yTop)
        assertEquals("おか", texts(cols[1]))
        assertEquals(0f, cols[1].units[0].yTop)
    }

    @Test
    fun `縦中横runは1ユニットとして折れる`() {
        val list = listOf(
            TypesetUnit("あ", CharClass.UPRIGHT, false, -1),
            TypesetUnit("い", CharClass.UPRIGHT, false, -1),
            TypesetUnit("12", CharClass.TATE_CHU_YOKO, false, -1),
            TypesetUnit("う", CharClass.UPRIGHT, false, -1),
            TypesetUnit("え", CharClass.UPRIGHT, false, -1),
        )
        val cols = breakUp(list)
        // 5ユニット（縦中横含む）が1列に収まる。
        assertEquals(1, cols.size)
        assertEquals(5, cols[0].units.size)
        val tcy = cols[0].units[2]
        assertEquals("12", tcy.unit.text)
        assertEquals(CharClass.TATE_CHU_YOKO, tcy.unit.charClass)
        // 縦中横も1マス＝advance は fontSizePx、y は3マス目=20。
        assertEquals(10f, tcy.advance)
        assertEquals(20f, tcy.yTop)
        assertTrue("縦中横は分割されず1ユニット", cols[0].units.count { it.unit.text == "12" } == 1)
    }
}
