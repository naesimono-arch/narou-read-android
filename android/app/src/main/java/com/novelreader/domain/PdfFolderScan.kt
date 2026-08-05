package com.novelreader.domain

import com.novelreader.data.BookEntity

// ============================================================
// PDF フォルダ走査による欠落本の自動復旧（2026-07-29 裁定・案X）
//
// 【なぜ要るか＝実機で確定した真因】本文欠落の主機序は uninstall→Auto Backup で、このとき
// **永続 URI 権限も DB と一緒には戻らない**。よって PDF 本は全て「取込元の記録はあるが読めない」
// （ReimportPlan.PickPdfPermissionLost）へ落ち、権限生存を前提にした自動再取込は構造的に0冊になる。
// 従来の受け皿は「同じ PDF を選び直してください」だったが、なろう縦書きPDF のファイル名は Nコード
// （n0000xx.pdf）で人間には中身が判別できない——時間の経った Download から目視で4冊探すのは非現実的。
// 一方アプリは中身で判別できる（取込時に焼いた contentSha256）。そこで探す作業を人間からアプリへ移す:
//   ユーザーは「PDFのある場所」をフォルダ選択で1回だけ教える → アプリが配下のPDFを列挙して指紋照合 →
//   一致した本を既存の復元経路（PdfBookImporter の restoreByHash）へ投入して自動復旧する。
// 選んだツリーの権限は永続化するので、次回以降の欠落は「フォルダを選ぶ」すら不要な自動走査になる
// （＝「①AutoPdf が主機序では成立しない」という構造問題そのものへの解）。
//
// 【対象の限界＝設計前提（2026-07-30 実機実測）】案X が救えるのは「ユーザーが自分で端末のフォルダに
// PDF ファイルを保存している本」だけ。なろう縦書きPDF取込の本（sourceUri/sourceUrl 両 NULL）は PDF が
// アプリ専用の cache/pdf_import/ にしか存在せず、ユーザーのフォルダをいくら走査しても一致しない。
// その本の受け皿は ReimportPlan.AutoCachePdf（cache 実体の直接再変換）＝案X とは別経路。
// 手元のフォルダにも cache にも PDF が無い本は、この導線では原理的に救えない（自動DLは実装しない＝
// ユーザー裁定）。
//
// 本ファイルは走査の純ロジック（対象の選別・候補の並べ替え・照合・結果集計）だけを持つ。
// SAF ツリーの列挙・ハッシュ計算という Android 依存は関数注入で受ける（実装＝repository/PdfTreeScanner）。
// ============================================================

/**
 * 走査で照合したい欠落本1冊。
 * 内容指紋（contentSha256）を持つ本だけがなれる＝指紋が無い本（v11 前の旧取込）は機械照合の材料が無く、
 * 走査では戻せない。これを黙って落とさず [FolderScanReport] の外側（内訳の unscannable）で正直に扱う。
 */
data class ScanTarget(
    val bookId: String,
    val title: String,
    val contentSha256: String,
    /** 取込元 URI から復元したファイル名（分岐②のみ非 null）。候補の優先順位付けにだけ使う手がかりで、
     *  一致判定には一切使わない（改名された PDF でも指紋が同じなら必ず戻る）。 */
    val fileNameHint: String? = null,
)

/** フォルダ走査で見つかった候補 PDF 1件（列挙結果の純データ）。 */
data class ScanCandidate(val uri: String, val displayName: String)

/** 一致1件（どの本がどのファイルで戻るか）。 */
data class ScanMatch(val target: ScanTarget, val candidate: ScanCandidate)

/** 走査中の進捗（バナー表示用）。total は列挙で確定した候補PDF総数。 */
data class ScanProgress(val hashed: Int, val total: Int, val matched: Int)

/**
 * 走査の結果（結果ダイアログの表示データ）。
 * 「戻せた」ではなく「一致した」であることに注意: 実際の復元は既存の取込キューが非同期に行う。
 */
data class FolderScanReport(
    val matches: List<ScanMatch>,
    /** 照合対象のうち一致しなかった本（別の場所にある／もう存在しない）。 */
    val unmatched: List<ScanTarget>,
    /** フォルダ配下で見つかった PDF の総数（照合したかどうかに関わらず）。 */
    val candidateCount: Int,
    /** 実際に読んで指紋計算を試みた件数（早期終了・キャンセルで candidateCount より少なくなる）。 */
    val hashedCount: Int,
    /** 開けなかった／読めなかったファイル数（真因はログへ。0件でないなら結果表示にも出す）。 */
    val unreadableCount: Int,
    /** ユーザーが停止した（＝結果は途中経過）。 */
    val cancelled: Boolean,
) {
    val matchedCount: Int get() = matches.size
    val unmatchedCount: Int get() = unmatched.size
}

/**
 * 欠落本の分類地図から走査対象を組み立てる。
 * plans に載っていない本（＝本文あり）と、指紋を持たない本は対象外。title は結果表示のためだけに運ぶ。
 */
fun buildScanTargets(books: List<BookEntity>, plans: Map<String, ReimportPlan>): List<ScanTarget> =
    books.mapNotNull { book ->
        val plan = plans[book.id] ?: return@mapNotNull null
        val sha = plan.scanSha256 ?: return@mapNotNull null
        ScanTarget(
            bookId = book.id,
            title = book.title,
            contentSha256 = sha,
            fileNameHint = (plan as? ReimportPlan.PickPdfPermissionLost)?.fileNameHint,
        )
    }

/**
 * 候補PDFを「一致し得る順」に並べ替える（安定ソート＝同ランク内は列挙順のまま）。
 *
 * なぜ並べ替えるか: 照合は全一致した時点で打ち切る（早期終了）ため、当たりが先に来るほど実際に読む
 * バイト数が減る。Download に無関係な大きい PDF が大量にあっても、蔵書のPDFが先に当たれば走査は短い。
 *
 * なぜ「ファイルサイズによる事前絞り込み」を採らないか（検討して却下した記録）:
 * 取込時の PDF サイズを DB に記録していないため比較対象が無い。列を足すと Room Migration が要るうえ、
 * 補完値は既存行 NULL＝いちばん救いたい旧取込本には効かない。指紋照合そのものが唯一の正解判定なので、
 * 絞り込みではなく「順序＋早期終了」でコストを下げる方針を採る。
 *
 * @param isLikelyNovelPdf ファイル名が蔵書PDFらしいかの判定（なろう縦書きPDF の Nコード命名など）。
 *   ドメイン層はファイル名規約を知らないので注入で受ける（既定＝順位付けなし）。
 */
fun orderScanCandidates(
    candidates: List<ScanCandidate>,
    targets: List<ScanTarget>,
    isLikelyNovelPdf: (String) -> Boolean = { false },
): List<ScanCandidate> {
    // 取込元の記録と同名のファイルが最有力（分岐②は元のファイル名を持っている）。
    // 大文字小文字はプロバイダ差があるため潰して比べる（一致判定でなく順序付けなので安全側で緩く）。
    val hints = targets.mapNotNull { it.fileNameHint?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    return candidates.sortedBy { c ->
        when {
            c.displayName.trim().lowercase() in hints -> 0
            isLikelyNovelPdf(c.displayName) -> 1
            else -> 2
        }
    }
}

/**
 * フォルダ配下のPDFを内容指紋で照合し、欠落本と突き合わせる（案X の中核・純ロジック）。
 *
 * @param enumerate 候補PDFの列挙（SAF ツリー走査＝メタデータ照会のみで軽い）。
 * @param hashOf 1件の SHA-256 を返す。読めなければ null（1件の失敗で走査全体を落とさないため。真因は実装側でログ）。
 * @param isCancelled ユーザーの停止要求。ファイル単位で協調的に確認する＝停止は「今読んでいる1件の完了後」。
 *   コルーチンキャンセルにしないのは、途中経過（どこまで調べて何冊当たったか）を結果として返したいため
 *   （CancellationException で巻き戻すと部分成果を報告できず、ユーザーには何も起きなかったように見える）。
 * @param onProgress 進捗通知（バナー）。列挙直後に total 確定分を1回、以後は1件ごと。
 */
suspend fun scanPdfFolder(
    targets: List<ScanTarget>,
    enumerate: suspend () -> List<ScanCandidate>,
    hashOf: suspend (ScanCandidate) -> String?,
    isLikelyNovelPdf: (String) -> Boolean = { false },
    isCancelled: () -> Boolean = { false },
    onProgress: (ScanProgress) -> Unit = {},
): FolderScanReport {
    if (targets.isEmpty()) {
        return FolderScanReport(emptyList(), emptyList(), 0, 0, 0, cancelled = false)
    }
    // 指紋→本。同一指紋の本が2冊並ぶ状態は取込側（findByContentSha256 での重複遮断）が構造的に作らない。
    // 万一並んでも associateBy は後勝ちで1冊しか残らないが、取りこぼした側は下の unmatched 差分で拾われる
    // （＝黙って消えない）。
    val remaining = targets.associateBy { it.contentSha256 }.toMutableMap()
    val ordered = orderScanCandidates(enumerate(), targets, isLikelyNovelPdf)
    val matches = mutableListOf<ScanMatch>()
    var hashed = 0
    var unreadable = 0
    var cancelled = false
    onProgress(ScanProgress(hashed = 0, total = ordered.size, matched = 0))
    for (candidate in ordered) {
        // 全冊当たったら残りのハッシュ計算は無意味＝ここで打ち切る（早期終了）。
        if (remaining.isEmpty()) break
        if (isCancelled()) {
            cancelled = true
            break
        }
        val sha = hashOf(candidate)
        hashed++
        if (sha == null) {
            unreadable++
        } else {
            remaining.remove(sha)?.let { matches += ScanMatch(it, candidate) }
        }
        onProgress(ScanProgress(hashed = hashed, total = ordered.size, matched = matches.size))
    }
    val matchedIds = matches.mapTo(mutableSetOf()) { it.target.bookId }
    return FolderScanReport(
        matches = matches,
        unmatched = targets.filterNot { it.bookId in matchedIds },
        candidateCount = ordered.size,
        hashedCount = hashed,
        unreadableCount = unreadable,
        cancelled = cancelled,
    )
}
