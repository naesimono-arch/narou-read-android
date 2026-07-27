package com.novelreader.narou

import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode

sealed interface ContinuationInfo {
    val ncode: Ncode
    val totalEpisodes: Int
    data class NewEpisodes(
        override val ncode: Ncode,
        override val totalEpisodes: Int,
        val pdfEpisodes: Int,
        val nextEpisode: Int,  // = pdfEpisodes + 1
        val newCount: Int,     // = totalEpisodes - pdfEpisodes
    ) : ContinuationInfo
    data class UpToDate(
        override val ncode: Ncode,
        override val totalEpisodes: Int,
    ) : ContinuationInfo
}

/**
 * 手元PDFの章数となろう上の作品情報を突き合わせ、継続読書に必要な情報を計算する。
 *
 * なぜここで各種バリデーションを行うか:
 * 入力値 of-bound や短編設定などの境界条件を適切に処理しないと、ユーザーに誤った
 * 続きの有無やリンクURLを提示してしまい、読書体験を著しく損ねるため。
 */
fun computeContinuation(pdfChapterCount: Int, novel: NarouNovel): ContinuationInfo? =
    // なろう短編は novelType==2。判定本体は [computeContinuationCore] に集約（下の WorkSummary 版と同一）。
    computeContinuationCore(
        ncodeRaw = novel.ncode,
        totalEpisodes = novel.generalAllNo,
        isShort = novel.novelType == 2,
        pdfChapterCount = pdfChapterCount,
    )

/**
 * [computeContinuation] のサイト非依存版。詳細取得を [WorkSummary] へ写像した後の継続判定に使う
 * （NativeReadingScreen の最終章カード等）。ncode/総話数/短編判定は summary から取り、判定本体は共通。
 */
fun computeContinuation(pdfChapterCount: Int, summary: WorkSummary): ContinuationInfo? =
    computeContinuationCore(
        ncodeRaw = summary.ncode,
        totalEpisodes = summary.chapterCount,
        isShort = summary.serialState == SerialState.SHORT,
        pdfChapterCount = pdfChapterCount,
    )

/**
 * 継続判定の本体（NarouNovel/WorkSummary いずれの入口からも同一ロジックを通す唯一の正本）。
 * @param ncodeRaw 生 ncode（前後空白を含みうる）。@param totalEpisodes なろう上の総話数。
 * @param isShort 短編か（短編は続きの概念が無く常に UpToDate）。
 */
private fun computeContinuationCore(
    ncodeRaw: String?,
    totalEpisodes: Int?,
    isShort: Boolean,
    pdfChapterCount: Int,
): ContinuationInfo? {
    // なぜトリムするか: APIレスポンスや手動入力等によって前後に空白が混入した場合でも、
    // 一致判定やURL生成を安定して行えるようにするため。
    val ncode = ncodeRaw?.trim()

    // なぜ空判定をするか: 紐付けキーであるNコードが存在しない場合、
    // なろう上のどの作品を指しているか突き合わせることが不可能なため、処理を進めずnullを返す。
    if (ncode.isNullOrEmpty()) {
        return null
    }
    // 境界変換点: 生 String（trim 済み）をここで一度だけ Ncode へ包み、
    // 以降のドメイン戻り値（ContinuationInfo）は型付き ncode で扱う（挙動不変＝正規化は素通し）。
    val id = Ncode(ncode)

    val total = totalEpisodes
    // なぜ総話数をバリデーションするか: 総エピソード数が未取得、または不正な値（0以下）の場合は、
    // なろう側の話数をベースにした継続判定ができないため、安全側に倒してnullを返す。
    if (total == null || total <= 0) {
        return null
    }

    // なぜPDF章数をバリデーションするか: 手元のPDFが0章以下（解析エラーや未ロードなど）の場合、
    // どこから読み進めればいいかの比較基準が作れず、誤った新着案内をするリスクがあるためnullを返す。
    if (pdfChapterCount <= 0) {
        return null
    }

    // なぜ短編の特別扱いが必要か: なろうの短編は一話完結であり、
    // 「続きの話」という概念自体が存在しないため、常に追いつき済み(UpToDate)とする。
    if (isShort) {
        return ContinuationInfo.UpToDate(id, total)
    }

    // なぜ引き算で判定するか: なろう上の総エピソード数から手元のPDF章数を引き、
    // 正の数であれば未読のエピソードがなろう上に存在する（新着あり）と判断するため。
    // それ以外（追いつき、またはなろう側で話数削減が発生して手元PDFの方が多い場合）は追いつき済みとする。
    return if (total - pdfChapterCount > 0) {
        ContinuationInfo.NewEpisodes(
            ncode = id,
            totalEpisodes = total,
            pdfEpisodes = pdfChapterCount,
            nextEpisode = pdfChapterCount + 1,
            newCount = total - pdfChapterCount
        )
    } else {
        ContinuationInfo.UpToDate(id, total)
    }
}

/**
 * なろうの特定話（エピソード）ページURLを生成する。
 */
fun narouEpisodeUrl(ncode: Ncode, episode: Int): String {
    // なぜ小文字化するか: なろうのWebサーバーはURLパスに含まれるNコードを小文字で要求するため。
    // 正規化は Ncode.urlSlug（trim+小文字＝不変ロケール）に集約（2026-07-27 型化。Ncode の KDoc 参照）。
    val lowerNcode = ncode.urlSlug
    return "https://ncode.syosetu.com/$lowerNcode/$episode/"
}

/**
 * なろうの作品（作品トップ）ページURLを生成する。
 */
fun narouWorkUrl(ncode: Ncode): String {
    // なぜ小文字化するか: なろうのWebサーバーはURLパスに含まれるNコードを小文字で要求するため。
    // 正規化は Ncode.urlSlug（trim+小文字＝不変ロケール）に集約（2026-07-27 型化。Ncode の KDoc 参照）。
    val lowerNcode = ncode.urlSlug
    return "https://ncode.syosetu.com/$lowerNcode/"
}

/**
 * なろうの話ページURLから話数(N)を取り出す。話ページ(.../<ncode>/N/)以外（目次・感想・ユーザーページ・
 * 外部リンク等）なら null。機能②の WebView 読書で、onPageFinished のURLから「今どの話を開いているか」を
 * 割り出して読書位置に記録するために使う。
 *
 * なぜ ncode を照合するか: WebView 読書中に別作品ページや外部リンクへ遷移した場合に、当該作品の話ページだけを
 * 読書位置として拾い、無関係なURLで進捗を汚さないため（記録の取り違え防止）。
 * なぜ URL 観測のみで足りるか（規約＝ADR 0012）: 本文の機械的取得やページ加工は一切行わず、ブラウザが辿った
 * URL 文字列を読むだけで話数が得られる（加工に当たらない）。
 */
fun parseNarouEpisodeNumber(url: String, ncode: Ncode): Int? {
    // なろうは URL パスの ncode を小文字で扱う（narouWorkUrl/narouEpisodeUrl と同じ Ncode.urlSlug で照合する）。
    val lower = ncode.urlSlug
    // https?://ncode.syosetu.com/<ncode>/<N>/ 形のみ受理。末尾スラッシュ有無を許容し、話数は正の整数。
    // ncode は Regex.escape で literal 扱い（万一メタ文字が混じっても誤マッチしない防御）。
    val regex = Regex("^https?://ncode\\.syosetu\\.com/${Regex.escape(lower)}/(\\d+)/?$")
    val match = regex.find(url.trim()) ?: return null
    return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
}

/**
 * 入力文字列がなろうNコードの正規表現に合致するか判定する。
 */
fun isValidNcode(input: String): Boolean {
    // なぜトリムしてから正規表現を適用するか: ユーザーの手動入力などで前後に空白が入る場合があるが、
    // トリムしてNコードの本質的な部分のみが適合していれば正常なNコードとして扱いたいため。
    val trimmed = input.trim()
    
    // なぜ (?i) を使用するか: Nコードの先頭は 'N' または 'n' のどちらでも許容されるため、
    // 大文字小文字を区別しない正規表現で判定する。なろうのNコード規則（N+数字4桁+英字1〜2桁）に厳密に合わせる。
    val regex = Regex("(?i)^n\\d{4}[a-z]{1,2}$")
    return regex.matches(trimmed)
}
