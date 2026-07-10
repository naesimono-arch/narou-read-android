package com.novelreader.viewmodel

/**
 * 複数 PDF 同時取込の仕分け・並べ替えロジック（純関数）。
 *
 * なぜ純関数として分離するか: 「なろう形式かどうかの判定」と「本棚に巻順で並ぶ投入順」は
 * Android 非依存の判断ロジックであり、ContentResolver / Uri を伴う VM とは切り離して
 * JVM 単体テストで担保したい（[ShelfItems] と同じ方針＝viewmodel パッケージの純ロジック）。
 * VM 側は Uri↔表示名の解決だけを担い、判断はここへ委ねる。
 */

// なろう公式の縦書きPDF は DL 時のファイル名が「Nコード.pdf」になる（例: N2959KI.pdf）。
// Nコードは「N + 数字(4桁以上) + 英字1〜2文字」。この形にだけマッチさせて、他サイト由来や
// 手元の無関係 PDF を弾く。厳しめ（whole-match）にするのは precision 優先＝混在時の誤取込を
// 避けるため。ユーザーが改名した正当ななろうPDFはここで漏れるが、UI 側の「すべて取り込む」で
// 救済できる（＝取りこぼしを不可逆にしない）。拡張子は大文字小文字を無視。
private val NAROU_PDF_NAME_RE = Regex("""^n\d{4,}[a-z]{1,2}\.pdf$""", RegexOption.IGNORE_CASE)

/** 表示名がなろう公式縦書きPDF のファイル名形式（Nコード.pdf）かどうか。 */
fun isNarouPdfFileName(displayName: String): Boolean =
    NAROU_PDF_NAME_RE.matches(displayName.trim())

/**
 * 複数 PDF 取込の実行計画。入力 displayNames のインデックスを、投入すべき順に並べて返す。
 * @property narouOrder   なろう形式（Nコード名）PDF のインデックス列（投入順）
 * @property nonNarouOrder なろう形式でない PDF のインデックス列（投入順）
 */
data class PdfImportPlan(
    val narouOrder: List<Int>,
    val nonNarouOrder: List<Int>,
)

/**
 * 表示名の並び（picker が返した順）から取込計画を組み立てる。
 *
 * 投入順の設計: 本棚は addedAt の降順で並ぶ（[com.novelreader.data.BookDao] getAllBooks の
 * `ORDER BY ... DESC`）。addedAt は変換完了時刻＝Service が ArrayDeque を1件ずつ処理するため
 * 「後に投入した本ほど新しい」。よって自然順で先頭に来る巻（第1巻）を最後に投入すれば、その巻が
 * 最新 addedAt を得て本棚の先頭に立ち、分割PDFが巻順（1→N が上→下）に並ぶ。
 * ＝各群を「自然昇順の逆順」で投入する（[naturalFileNameComparator] で数字を数値比較し
 * 「第2話 < 第10話」を保証）。
 */
fun planNarouPdfImport(displayNames: List<String>): PdfImportPlan {
    val (narou, nonNarou) = displayNames.indices.partition { isNarouPdfFileName(displayNames[it]) }
    return PdfImportPlan(
        narouOrder = narou.sortedWith(compareBy(naturalFileNameComparator) { displayNames[it] }).reversed(),
        nonNarouOrder = nonNarou.sortedWith(compareBy(naturalFileNameComparator) { displayNames[it] }).reversed(),
    )
}

/**
 * ファイル名の自然順比較器。数字の並びを数値として比較し「第2話 < 第10話」を保証する
 * （辞書順だと "10" < "2" になり巻数が乱れる）。数字以外はコードポイント順。
 * 先頭ゼロは無視して数値比較（"02" == "2"）し、同値なら元の桁数で安定化する。
 */
val naturalFileNameComparator: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

private fun compareNatural(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            // 連続する数字列を丸ごと切り出して数値として比較する。
            var ei = i
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j
            while (ej < b.length && b[ej].isDigit()) ej++
            // 先頭ゼロを除いた有効桁で比較（"007" と "7" を同値、"08" < "10" を成立させる）。
            val na = a.substring(i, ei).trimStart('0')
            val nb = b.substring(j, ej).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val cmp = na.compareTo(nb)
            if (cmp != 0) return cmp
            // 数値が等しければ元の桁数（先頭ゼロの個数）で決定的に順序付けし、安定比較にする。
            if (ei - i != ej - j) return (ei - i) - (ej - j)
            i = ei
            j = ej
        } else {
            if (ca != cb) return ca.compareTo(cb)
            i++
            j++
        }
    }
    // 片方が他方の接頭辞なら、残り文字数が少ない方（＝短い方）を前に置く。
    return (a.length - i) - (b.length - j)
}
