package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import java.util.Locale

sealed interface ContinuationInfo {
    val ncode: String
    val totalEpisodes: Int
    data class NewEpisodes(
        override val ncode: String,
        override val totalEpisodes: Int,
        val pdfEpisodes: Int,
        val nextEpisode: Int,  // = pdfEpisodes + 1
        val newCount: Int,     // = totalEpisodes - pdfEpisodes
    ) : ContinuationInfo
    data class UpToDate(
        override val ncode: String,
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
fun computeContinuation(pdfChapterCount: Int, novel: NarouNovel): ContinuationInfo? {
    // なぜトリムするか: APIレスポンスや手動入力等によって前後に空白が混入した場合でも、
    // 一致判定やURL生成を安定して行えるようにするため。
    val ncode = novel.ncode?.trim()
    
    // なぜ空判定をするか: 紐付けキーであるNコードが存在しない場合、
    // なろう上のどの作品を指しているか突き合わせることが不可能なため、処理を進めずnullを返す。
    if (ncode.isNullOrEmpty()) {
        return null
    }

    val total = novel.generalAllNo
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

    // なぜ短編の特別扱いが必要か: なろうの短編（novelType=2）は一話完結であり、
    // generalAllNo が 1 であるものの「続きの話」という概念自体が存在しないため、常に追いつき済み(UpToDate)とする。
    if (novel.novelType == 2) {
        return ContinuationInfo.UpToDate(ncode, total)
    }

    // なぜ引き算で判定するか: なろう上の総エピソード数から手元のPDF章数を引き、
    // 正の数であれば未読のエピソードがなろう上に存在する（新着あり）と判断するため。
    // それ以外（追いつき、またはなろう側で話数削減が発生して手元PDFの方が多い場合）は追いつき済みとする。
    return if (total - pdfChapterCount > 0) {
        ContinuationInfo.NewEpisodes(
            ncode = ncode,
            totalEpisodes = total,
            pdfEpisodes = pdfChapterCount,
            nextEpisode = pdfChapterCount + 1,
            newCount = total - pdfChapterCount
        )
    } else {
        ContinuationInfo.UpToDate(ncode, total)
    }
}

/**
 * なろうの特定話（エピソード）ページURLを生成する。
 */
fun narouEpisodeUrl(ncode: String, episode: Int): String {
    // なぜ小文字化するか: なろうのWebサーバーはURLパスに含まれるNコードを
    // 小文字で要求するため、安全のために Locale.ROOT で小文字化して結合する。
    val lowerNcode = ncode.trim().lowercase(Locale.ROOT)
    return "https://ncode.syosetu.com/$lowerNcode/$episode/"
}

/**
 * なろうの作品（作品トップ）ページURLを生成する。
 */
fun narouWorkUrl(ncode: String): String {
    // なぜ小文字化するか: なろうのWebサーバーはURLパスに含まれるNコードを
    // 小文字で要求するため、安全のために Locale.ROOT で小文字化して結合する。
    val lowerNcode = ncode.trim().lowercase(Locale.ROOT)
    return "https://ncode.syosetu.com/$lowerNcode/"
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
