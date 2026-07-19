package com.novelreader

/**
 * 共有(ACTION_SEND)テキストからの Web 小説 URL 抽出（P3 取込導線）。
 *
 * ここは「Android の Intent 入口を捌く」UI 層の関心事で、スクレイピング基盤（scrape/）とは別レイヤ。
 * 純関数＝Android 非依存で JVM 単体テスト可能にするため、Intent からの取り出し（EXTRA_TEXT / data）は
 * 呼び出し側（MainActivity）が行い、ここは「テキスト → 最初の http(s) URL」の抽出だけを担う。
 * ACTION_VIEW は intent.data がそのまま URL のためこの抽出を通さない（呼び出し側で直接使う）。
 */
object WebImportIntentParser {

    // 共有テキストは「タイトル + 改行 + URL」形が普通なので、最初に現れる http(s):// URL を1つ取り出す。
    // \S+ は空白（改行・スペース）直前までを URL 本体とみなす。末尾の句読点・全角記号までは厳密に除去しない：
    // 抽出後の正規化（作品トップ URL への畳み込み）は各サイトアダプタの canonicalWorkUrl が担うため、
    // ここは「URL らしき最初のトークン」を切り出すだけに留める（過剰な整形で誤って本体を削らない）。
    private val URL_REGEX = Regex("""https?://\S+""")

    /** [text] に含まれる最初の http(s) URL を返す。URL が無い・null・空白のみなら null。 */
    fun firstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.find(text)?.value
    }
}
