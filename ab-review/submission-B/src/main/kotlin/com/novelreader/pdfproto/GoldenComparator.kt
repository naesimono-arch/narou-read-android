package com.novelreader.pdfproto

/**
 * 抽出結果(Book)を Python リファレンス由来のゴールデン(Book)と数値比較する検証器。
 *
 * 移植元 Python は pdfminer、本実装は PDFBox を使うため、同じ PDF でも一部グリフの
 * CID→Unicode マッピングが食い違う（代表例：波ダッシュ 〜 U+301C ↔ ～ U+FF5E）。
 * これはどちらのロジックの誤りでもなく抽出エンジンの差なので、比較時に正規化して
 * 偽の不一致を生まないようにする（抽出物そのものは改変しない）。
 */
object GoldenComparator {

    /**
     * pdfminer↔PDFBox で割れやすいグリフを代表字へ寄せる。
     * 必要に応じ追記する（過剰正規化は本物の差を隠すため、確証のある対のみ）。
     */
    private val GLYPH_CANON = mapOf(
        '～' to '〜', // FULLWIDTH TILDE → WAVE DASH
        '―' to '—', // HORIZONTAL BAR → EM DASH（罫線ダッシュの揺れ）
        '－' to '−', // FULLWIDTH HYPHEN-MINUS → MINUS SIGN
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(GLYPH_CANON[c] ?: c)
        return sb.toString()
    }

    /** 1 章を比較用テキストへ平坦化（ruby は "|親《読》" マーカー形へ戻して構造ごと比較）。 */
    private fun flatten(chapter: Chapter): String {
        val sb = StringBuilder()
        for (n in chapter.paragraphs) when (n) {
            is Node.Plain -> sb.append(n.text)
            is Node.Ruby -> sb.append('|').append(n.base).append('《').append(n.reading).append('》')
        }
        return normalize(sb.toString())
    }

    private fun rubySet(book: Book): List<String> =
        book.chapters.flatMap { ch ->
            ch.paragraphs.filterIsInstance<Node.Ruby>()
                .map { normalize(it.base) + "《" + normalize(it.reading) + "》" }
        }

    data class Result(
        val titleMatch: Boolean,
        val authorMatch: Boolean,
        val goldenChapters: Int,
        val candidateChapters: Int,
        val chapterTitleMatchRate: Double,
        val lineCoverage: Double,        // golden の非空行のうち候補にも在る文字数の割合
        val rubyPrecision: Double,
        val rubyRecall: Double,
    )

    fun compare(golden: Book, candidate: Book): Result {
        val titleMatch = normalize(golden.title) == normalize(candidate.title)
        val authorMatch = normalize(golden.author) == normalize(candidate.author)

        // 章タイトル一致率（min(章数) で位置合わせ）
        val n = minOf(golden.chapters.size, candidate.chapters.size)
        var titleHits = 0
        for (i in 0 until n) {
            if (normalize(golden.chapters[i].title) == normalize(candidate.chapters[i].title)) titleHits++
        }
        val titleRate = if (golden.chapters.isNotEmpty()) titleHits.toDouble() / golden.chapters.size else 0.0

        // 行カバレッジ：全書を行(\n 区切り)の多重集合にし、交差した文字数/golden 総文字数。
        // 章内の並べ替えに頑健で O(n)。A の GoldenFileComparator の発想を仕様形式へ移植したもの。
        val goldenLines = lineMultiset(golden)
        val candLines = lineMultiset(candidate)
        var matchedChars = 0L
        var goldenChars = 0L
        for ((line, gc) in goldenLines) {
            goldenChars += line.length.toLong() * gc
            val cc = candLines[line] ?: 0
            matchedChars += line.length.toLong() * minOf(gc, cc)
        }
        val coverage = if (goldenChars > 0) matchedChars.toDouble() / goldenChars else 0.0

        // ルビ Precision / Recall（多重集合の交差）
        val gRuby = golden.let(::rubySet)
        val cRuby = candidate.let(::rubySet)
        val gCount = HashMap<String, Int>().apply { gRuby.forEach { merge(it, 1, Int::plus) } }
        val cCount = HashMap<String, Int>().apply { cRuby.forEach { merge(it, 1, Int::plus) } }
        var inter = 0
        for ((k, gc) in gCount) inter += minOf(gc, cCount[k] ?: 0)
        // ルビが存在しない側は「誤検出ゼロ／取りこぼしゼロ」として 100% 扱いにする
        // （0/0 を 0% と出すとルビ無し作品で誤解を招くため）。
        val precision = if (cRuby.isEmpty()) 1.0 else inter.toDouble() / cRuby.size
        val recall = if (gRuby.isEmpty()) 1.0 else inter.toDouble() / gRuby.size

        return Result(
            titleMatch, authorMatch,
            golden.chapters.size, candidate.chapters.size,
            titleRate, coverage, precision, recall,
        )
    }

    private fun lineMultiset(book: Book): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (ch in book.chapters) {
            for (line in flatten(ch).split('\n')) {
                if (line.isEmpty()) continue
                m.merge(line, 1, Int::plus)
            }
        }
        return m
    }

    /** 比較結果を人間可読に整形（数値は再現可能・コンソール文字化けの影響を受けない英数字）。 */
    fun format(name: String, r: Result): String = buildString {
        appendLine("=== golden compare: $name ===")
        appendLine("  title match    : ${r.titleMatch}")
        appendLine("  author match   : ${r.authorMatch}")
        appendLine("  chapters       : golden=${r.goldenChapters} candidate=${r.candidateChapters} (match=${r.goldenChapters == r.candidateChapters})")
        appendLine("  chapter titles : ${"%.2f".format(r.chapterTitleMatchRate * 100)}%")
        appendLine("  line coverage  : ${"%.2f".format(r.lineCoverage * 100)}%")
        appendLine("  ruby precision : ${"%.2f".format(r.rubyPrecision * 100)}%")
        append("  ruby recall    : ${"%.2f".format(r.rubyRecall * 100)}%")
    }
}
