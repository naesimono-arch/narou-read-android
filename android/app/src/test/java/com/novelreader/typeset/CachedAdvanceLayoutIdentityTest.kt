package com.novelreader.typeset

import com.novelreader.model.TextSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * advance キャッシュ（改善 D）を噛ませても**版面が1ビットも変わらない**ことの回帰ゲート。
 *
 * なぜこの形の検査が要るか: D の裁定は「実測値を覚えるだけ・縦送りの定数化は不採用」だった
 * （書体差で版面がずれる＝[FontMetricsProvider] の KDoc）。キャッシュはその境界の内側に入る変更なので、
 * キーを1成分でも取り違えると**組版は静かに通り、版面だけが壊れる**。よって
 * 「キャッシュ有り」と「キャッシュ無し」を同じ組版器・同じ寸法源関数で回して [ParagraphLayout] を
 * 丸ごと突き合わせる。寸法源は [VariableFontMetrics]（3成分すべてに反応する非等幅）＝
 * 等幅フェイクでは取り違えても値が同じで検査が空振りする。
 */
class CachedAdvanceLayoutIdentityTest {

    /** キャッシュ有りの実測源（呼び出し回数＝外れ回数）。 */
    private val cachedSource = VariableFontMetrics()

    /** 対照群（キャッシュ無し＝毎回実測）。 */
    private val plainSource = VariableFontMetrics()

    private val cache = CachingFontMetrics(cachedSource)

    /** 章の寿命ぶん使い回す＝サイズを跨いだキーが1つの表に溜まる状態で突き合わせる。 */
    private val cached = DefaultVerticalTypesetter(cache)

    private val plain = DefaultVerticalTypesetter(plainSource, cacheAdvances = false)

    /** 14→24→14 の往復＝スライダーのドラッグと同じくサイズが行き来する形（LRU の効き方も込みで見る）。 */
    private val sizes = listOf(14f, 24f, 14f)

    private fun constraintsAt(fontSizePx: Float) = TypesetConstraints(
        columnHeightPx = fontSizePx * 18f,
        fontSizePx = fontSizePx,
        // 本文とルビが同じ組版の中で別サイズを引く＝キーの sizePx 成分が効く条件。
        rubyFontSizePx = fontSizePx / 2f,
        columnAdvancePx = fontSizePx * 2f,
        indentFirstColumn = true,
    )

    private fun syntheticParagraphs(): List<Pair<String, List<TextSegment>>> = listOf(
        "かな漢字" to listOf(TextSegment.Plain("吾輩は猫である。名前はまだ無いが、それでも困らない。")),
        // 同じ "3" が UPRIGHT（単独ラン）と ROTATE（4字以上ランの各字）で同居する＝
        // キーから charClass を落とすとここで版面が割れる（golden 監査 2026-08-06 G-3 の形）。
        "向き違いの同字" to listOf(TextSegment.Plain("3日と1234年、3人が3AB語った。")),
        // ルビの読みの字が本文にも出る＝キーから sizePx を落とすとルビが本文寸法で置かれる。
        "ルビと本文の同字" to listOf(
            TextSegment.Ruby("薔薇", "ばら"),
            TextSegment.Plain("ばらの花がひらく。"),
            TextSegment.Ruby("空", "そら"),
            TextSegment.Plain("そらは青い。"),
        ),
        "列を跨ぐルビ" to listOf(
            TextSegment.Plain("あいうえおかきくけこさしすせそたちつてとなにぬねの"),
            TextSegment.Ruby("超電磁砲", "レールガン"),
            TextSegment.Plain("を撃つ。"),
        ),
        "リーダーと禁則" to listOf(TextSegment.Plain("「そうか……」と彼は言った――。だが、、。")),
        "縦中横" to listOf(TextSegment.Plain("第12話 AWとSIMを12個、123456を数える。")),
    )

    private fun corpusLines(): List<String> {
        val stream = javaClass.getResourceAsStream("/typeset/edgecase_corpus.txt")
            ?: error("コーパス未配置: src/test/resources/typeset/edgecase_corpus.txt")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trimEnd() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.replaceFirst(Regex("^\\[[^]]*] "), "") }
                .toList()
        }
    }

    private fun assertSameLayout(label: String, expected: ParagraphLayout, actual: ParagraphLayout) {
        assertEquals("$label 列数", expected.columnCount, actual.columnCount)
        assertEquals("$label 幅", expected.widthPx, actual.widthPx, 0f)
        assertEquals("$label 高さ", expected.heightPx, actual.heightPx, 0f)
        assertEquals("$label グリフ数", expected.glyphs.size, actual.glyphs.size)
        expected.glyphs.forEachIndexed { i, g -> assertEquals("$label glyph[$i]", g, actual.glyphs[i]) }
        assertEquals("$label ルビ数", expected.rubies.size, actual.rubies.size)
        expected.rubies.forEachIndexed { i, r -> assertEquals("$label ruby[$i]", r, actual.rubies[i]) }
        // 逐次比較の後にデータクラス全体でも突き合わせる＝将来 ParagraphLayout に項目が増えても
        // 上の並びだけ通って新項目が素通りする、という抜けを作らない。
        assertEquals("$label 版面全体", expected, actual)
    }

    @Test
    fun `合成コーパスの版面がキャッシュ有無で完全一致する`() {
        for (size in sizes) {
            val constraints = constraintsAt(size)
            for ((label, segments) in syntheticParagraphs()) {
                assertSameLayout(
                    "$label @${size}px",
                    plain.typeset(segments, constraints),
                    cached.typeset(segments, constraints),
                )
            }
        }
        assertTrue("キャッシュが実際に当たっている（当たっていなければ一致は自明で無意味）", cache.stats().hits > 0)
        assertTrue(
            "実測回数がキャッシュ無しより少ない（${cachedSource.verticalCalls} < ${plainSource.verticalCalls}）",
            cachedSource.verticalCalls < plainSource.verticalCalls,
        )
    }

    @Test
    fun `実データのエッジコーパス全行で版面が完全一致する`() {
        val lines = corpusLines()
        assertTrue("コーパスが読めていること", lines.isNotEmpty())
        for (size in sizes) {
            val constraints = constraintsAt(size)
            for ((index, line) in lines.withIndex()) {
                val segments = listOf(TextSegment.Plain(line))
                assertSameLayout(
                    "corpus[$index] @${size}px",
                    plain.typeset(segments, constraints),
                    cached.typeset(segments, constraints),
                )
            }
        }
        assertTrue("実データでも当たっている", cache.stats().hits > 0)
    }

    @Test
    fun `上限超過で破棄が走っても版面は一致する`() {
        // 常駐上限をわざと極小にして LRU の破棄を強制する＝「捨てた後に組み直した値」が
        // 捨てる前と同じであることの確認（破棄が版面を汚さない）。
        val tinySource = VariableFontMetrics()
        val tiny = CachingFontMetrics(tinySource, maxEntries = 8)
        val tinyTypesetter = DefaultVerticalTypesetter(tiny)
        for (size in sizes) {
            val constraints = constraintsAt(size)
            for ((label, segments) in syntheticParagraphs()) {
                assertSameLayout(
                    "tiny $label @${size}px",
                    plain.typeset(segments, constraints),
                    tinyTypesetter.typeset(segments, constraints),
                )
            }
        }
        assertEquals("上限を超えて抱え込まない", 8, tiny.stats().entries)
        assertTrue("破棄が実際に走っている", tinySource.verticalCalls > tiny.stats().entries)
    }

    @Test
    fun `コーパスは取り違えを検出できる形になっている`() {
        // この検査が無いと、合成コーパスから「同字・向き違い」や「本文とルビの同字」が消えたときに
        // 一致テストが静かに空振りになる（緑のまま守っていない状態＝最も避けたい壊れ方）。
        val constraints = constraintsAt(16f)
        val layouts = syntheticParagraphs().map { plain.typeset(it.second, constraints) }

        val classesByText = HashMap<String, MutableSet<CharClass>>()
        for (layout in layouts) {
            for (glyph in layout.glyphs) {
                classesByText.getOrPut(glyph.text) { HashSet() }.add(glyph.charClass)
            }
        }
        assertTrue(
            "同じ字が2つ以上の向きで現れること（charClass 成分の検出力）",
            classesByText.any { it.value.size >= 2 },
        )

        val bodyTexts = layouts.flatMap { layout -> layout.glyphs.map { it.text } }.toSet()
        val rubyTexts = layouts.flatMap { layout -> layout.rubies.flatMap { r -> r.cells.map { it.text } } }.toSet()
        assertTrue(
            "本文とルビに共通の字があること（sizePx 成分の検出力）",
            bodyTexts.intersect(rubyTexts).isNotEmpty(),
        )
    }
}
