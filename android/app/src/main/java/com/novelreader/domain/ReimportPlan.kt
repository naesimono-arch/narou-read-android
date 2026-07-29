package com.novelreader.domain

import com.novelreader.data.BookEntity
import java.net.URLDecoder

// ============================================================
// 本文欠落→再取込提案（2026-07-29 裁定・案B＋案C、同日 実機検証を受けた案X 増補）
//
// 「books 行はあるが本文実体（index.html 等）が無い」蔵書を起動時に一括検出し、
// 復旧手段を sourceUri/sourceUrl の状態で4分岐に分類する純ロジック。
// 機序＝uninstall→Auto Backup が DB のみ復元して files/novels/ が空になる
// （knowledge upload-signing-verify-wipes-device-library）。
// ファイル実在判定・永続権限照会は Android 依存のため関数注入で受け、
// 分類そのものは JVM 単体テスト可能な純関数に保つ。
//
// 【案X＝実機で確定した真因への対処】初版（案B/C）は自動復旧の対象を①AutoPdf（永続URI権限が生存する
// PDF）と④AutoWeb に限っていたが、実蔵書4冊で「まとめて再取込」が常に0冊になった。真因は
// **uninstall すれば永続 URI 権限も必ず一緒に消える**こと（Auto Backup は URI 権限を復元しない）。
// つまり想定した主機序では PDF 本は 100% ②PickPdfPermissionLost へ落ち、①は「本文ファイルだけが
// 消えた」稀なケースでしか成立しない＝自動対象は構造的に常にゼロだった。
// さらに②③は「PDFを選び直してください」と SAF ピッカーを出すが、なろうPDFのファイル名は Nコード
// （n0000xx.pdf）で人間には中身が判別できない。一方アプリは中身で判別できる（contentSha256）。
// そこで探す作業を人間からアプリへ移す＝「PDFのある場所」を SAF フォルダ選択で1回だけ教われば、
// 配下のPDFを列挙して指紋照合し自動で戻す（走査の純ロジックは domain/PdfFolderScan.kt）。
// 本ファイルの責務は「どの本が走査で戻せるか（＝内容指紋を持つか）」までを分類に含めること。
// ============================================================

/**
 * 欠落本1冊の復旧手段（モック bookshelf-reimport-badge-D.html の4分岐）。
 * 分岐①④＝取込元の記録だけで自動復旧できる／②③＝取込元へ到達する手段が無い。
 *
 * ②③でも [scanSha256] が非 null なら「PDFのある場所を1回教えてもらう」だけで機械照合できる（案X）。
 * 1冊ずつの SAF ピッカーが真に必要なのは [scanSha256] が null の本（v11 前の旧取込＝contentSha256 が
 * NULL で照合材料が無い）だけになった。
 */
sealed interface ReimportPlan {
    /** ① sourceUri 記録あり・読取権限が生存＝元のPDFから自動で再変換できる。 */
    data class AutoPdf(val sourceUri: String) : ReimportPlan

    /** ② sourceUri 記録はあるが永続権限が失効。uninstall→Auto Backup の主機序ではPDF本が全てここへ落ちる
     *  （権限は DB と一緒には戻らない）。fileNameHint は走査候補の優先順位付けと個別選び直しの手がかり、
     *  contentSha256 はフォルダ走査での機械照合キー（NULL＝v11 前の旧取込で照合不能）。 */
    data class PickPdfPermissionLost(
        val fileNameHint: String?,
        val contentSha256: String?,
    ) : ReimportPlan

    /** ③ sourceUri NULL（v20 前の旧取込等）＝取込元の手がかりが無い。
     *  ただし contentSha256 は v11 以降なら入っている＝フォルダ走査では②と同じく機械照合できる。 */
    data class PickPdfNoRecord(val contentSha256: String?) : ReimportPlan

    /** ④ Web 取込本（sourceUrl あり）＝作品ページから自動で再取得できる。 */
    data class AutoWeb(val sourceUrl: String) : ReimportPlan

    /** 自動復旧可能な分岐か（取込元の記録だけで戻せる＝①④）。 */
    val isAuto: Boolean
        get() = this is AutoPdf || this is AutoWeb

    /**
     * フォルダ走査（案X）で機械照合するときのキー。null＝走査では戻せない。
     * ①④を除外するのは、取込元の記録から直接戻せる本を重い全走査の対象に混ぜないため
     * （①は再変換を、④はWeb再取得を既に投入済み＝二重取込になる）。
     */
    val scanSha256: String?
        get() = when (this) {
            is PickPdfPermissionLost -> contentSha256
            is PickPdfNoRecord -> contentSha256
            is AutoPdf, is AutoWeb -> null
        }
}

/**
 * 1冊を4分岐へ分類する。呼び出し側の前提: この本は本文欠落と判定済み。
 * Web 判定を先に置くのは、Web 本は sourceUri（content://）を持たない設計（BookEntity の列コメント）だが、
 * 万一両方入っていても「PDF を選ばせるより sourceUrl 再取得が確実」なため（防御的順序）。
 */
fun classifyReimport(
    book: BookEntity,
    hasPersistedRead: (uriString: String) -> Boolean,
): ReimportPlan {
    val sourceUrl = book.sourceUrl
    if (sourceUrl != null) return ReimportPlan.AutoWeb(sourceUrl)
    val sourceUri = book.sourceUri ?: return ReimportPlan.PickPdfNoRecord(book.contentSha256)
    return if (hasPersistedRead(sourceUri)) {
        ReimportPlan.AutoPdf(sourceUri)
    } else {
        ReimportPlan.PickPdfPermissionLost(sourceFileNameHint(sourceUri), book.contentSha256)
    }
}

/**
 * 全蔵書から欠落本を検出し bookId→復旧手段の地図を返す（案B バッジ・案C バナーの共通データ源）。
 * isContentMissing はファイル実在判定（Android の filesDir 依存）を注入で受ける。
 */
fun buildReimportPlans(
    books: List<BookEntity>,
    isContentMissing: (BookEntity) -> Boolean,
    hasPersistedRead: (uriString: String) -> Boolean,
): Map<String, ReimportPlan> =
    books.asSequence()
        .filter(isContentMissing)
        .associate { it.id to classifyReimport(it, hasPersistedRead) }

/**
 * 「ファイル名として妥当か」の定義＝末尾に拡張子（. と 1〜8 文字の英数字）を持つこと。
 *
 * なぜこの定義か（2026-07-29 実機実測で発覚した誤表示の根治）: 授権 ID に実ファイル名を埋め込む
 * プロバイダ（externalstorage の "primary:Download/foo.pdf"、downloads の "raw:/storage/…/foo.pdf"）は
 * 必ず拡張子ごとファイル名を含む。一方、不透明な内部 ID を使うプロバイダ——実測された MediaStore
 * Documents（"document:1000027648"）や UUID 形式——は拡張子を持たない。よって「拡張子の有無」が
 * 〈人間がファイルを探す手がかりになる文字列〉と〈provider の内部 ID〉を分ける構造的な境界になる。
 * 数字だけを弾く等の対症的な条件にしないのは、UUID 形式など別の不透明 ID をまた取りこぼすため。
 */
private val FILE_NAME_WITH_EXTENSION = Regex(""".+\.[A-Za-z0-9]{1,8}$""")

/**
 * SAF ドキュメント URI からファイル名の手がかりを取り出す（分岐②の表示と走査候補の優先順位付け）。
 * 権限失効後は ContentProvider へ DISPLAY_NAME を照会できない（SecurityException）ため、
 * URI 文字列そのものから復元する: 最終セグメントを URL デコードし、
 * "primary:Download/foo.pdf" / "raw:/storage/.../foo.pdf" 形式の授権 ID から末尾のファイル名を切り出す。
 *
 * 切り出せても [FILE_NAME_WITH_EXTENSION] を満たさない文字列は null にする。実機の蔵書は全冊が
 * MediaStore Documents 由来（"…/document/document%3A1000027648"）で、旧実装はここから "1000027648" を
 * 取り出してダイアログに『取込元の PDF: 1000027648』と表示していた——手がかりとして無価値なだけでなく、
 * ファイル名だと誤認させる。手がかりが無いなら黙って出さない方が正しい（ダイアログは手がかり行を
 * 落とすだけで成立する）。この事実は案X（フォルダ走査＝内容指紋での自動照合）が必須である根拠でもある:
 * 主要プロバイダではファイル名の手がかりすら残らず、人間には選び直す材料が無い。
 */
fun sourceFileNameHint(sourceUri: String): String? {
    val lastSegment = sourceUri.substringAfterLast('/')
    if (lastSegment.isBlank()) return null
    val decoded = runCatching { URLDecoder.decode(lastSegment, "UTF-8") }.getOrNull() ?: return null
    // 授権 ID（"primary:Download/foo.pdf" 等）は「最後の : の後ろ」→さらに「最後の / の後ろ」がファイル名。
    val name = decoded.substringAfterLast(':').substringAfterLast('/').trim()
    return name.takeIf { FILE_NAME_WITH_EXTENSION.matches(it) }
}

/**
 * 案C バナーに出す内訳。
 * [autoPdf]/[autoWeb]/[pickPermissionLost]/[pickNoRecord] は sourceUri/sourceUrl による分岐4系統の冊数
 * （分類の構造そのもの＝診断・テスト用に保つ）。
 * 一括確認ダイアログが実際に見せるのは「復旧経路」による3分類＝[autoTotal]／[scannable]／[unscannable] で、
 * これは案X 以後「ユーザーが何をすれば戻るか」が②③という分岐名と一致しなくなったため（②③はどちらも
 * フォルダを1回教えれば自動で戻る＝人にとっては同じ操作）。
 */
data class ReimportBreakdown(
    val autoPdf: Int,
    val autoWeb: Int,
    val pickPermissionLost: Int,
    val pickNoRecord: Int,
    /** ②③のうち内容指紋を持つ＝フォルダ走査で機械照合できる冊数（案X の主対象）。 */
    val scannable: Int,
    /** ②③のうち内容指紋が無い＝走査では戻せず1冊ずつPDFを選ぶしかない冊数（v11 前の旧取込）。 */
    val unscannable: Int,
) {
    val total: Int get() = autoPdf + autoWeb + pickPermissionLost + pickNoRecord

    /** 取込元の記録だけで戻せる冊数（①＋④＝確認だけで実行できる分）。 */
    val autoTotal: Int get() = autoPdf + autoWeb

    /** 取込元の記録では戻せない冊数（②＋③）。内訳は [scannable] ＋ [unscannable]（不変条件）。 */
    val manualTotal: Int get() = pickPermissionLost + pickNoRecord

    /** 一括復旧のワンアクションで戻る見込みの冊数（自動＋フォルダ走査）。CTA の冊数表示に使う。 */
    val recoverableTotal: Int get() = autoTotal + scannable
}

/** 検出結果から内訳冊数を数える（一括確認ダイアログの表示データ）。 */
fun reimportBreakdown(plans: Collection<ReimportPlan>): ReimportBreakdown {
    // 走査可否は②③の中だけで数える（scanSha256 が①④で必ず null を返す＝定義上の保証）。
    val manual = plans.filter { !it.isAuto }
    return ReimportBreakdown(
        autoPdf = plans.count { it is ReimportPlan.AutoPdf },
        autoWeb = plans.count { it is ReimportPlan.AutoWeb },
        pickPermissionLost = plans.count { it is ReimportPlan.PickPdfPermissionLost },
        pickNoRecord = plans.count { it is ReimportPlan.PickPdfNoRecord },
        scannable = manual.count { it.scanSha256 != null },
        unscannable = manual.count { it.scanSha256 == null },
    )
}

/**
 * 案C バナーを出すか（「新規に検出した際に一度だけ表示」のユーザー裁定）。
 * seenIds＝前回バナーを提示（あとで/実行）した時点の欠落 bookId 集合（prefs 永続）。
 * 「欠落集合に seen に無い id が1つでもあれば表示」＝同一集合では再表示せず、
 * 新たな欠落が増えたときだけ再表示する。集合が縮む（復旧が進む）だけでは出さない。
 */
fun shouldShowReimportSweep(missingIds: Set<String>, seenIds: Set<String>): Boolean =
    (missingIds - seenIds).isNotEmpty()

/**
 * 一括復旧の1操作でバナー指紋を消費してよいか（＝以後この欠落集合では知らせを出さない）。
 *
 * なぜ関数として切り出すか（初版の欠陥の再発防止）: 旧 runSweepReimport は実行の先頭で無条件に
 * 指紋を保存していたため、自動対象が0冊で「実行したのに何も起きない」ときでもバナーが二度と
 * 出なくなり、1タップで復旧導線そのものを失っていた。判定を名前のある純関数に固定して
 * JVM テストで縛る（VM 側は永続化の副作用だけを持つ）。
 *
 * 規則: 実際に1冊でも復旧を投入できたときだけ消費する。1冊も動かせなかったなら知らせは残す
 * （＝ユーザーは同じバナーからやり直せる。明示的に閉じたい人には「あとで」がある）。
 * フォルダ走査を始めた操作では [scanMatched] が確定する走査完了時にこの判定を行う（開始時ではない）。
 *
 * @param autoSubmitted ①元PDF＋④Web で取込キューへ投入した冊数。
 * @param scanMatched フォルダ走査で指紋一致した冊数（走査していない／未完了なら 0）。
 */
fun shouldConsumeSweepBanner(autoSubmitted: Int, scanMatched: Int): Boolean =
    autoSubmitted > 0 || scanMatched > 0

/**
 * 検出のたびに seen 集合を現欠落へ刈り込む（seen = seen ∩ missing）。
 * なぜ刈るか: 一度復旧した本が将来また欠落したとき、それは「新規の検出」＝バナーを出し直すべきイベント。
 * seen に残したままだと二度目の欠落が永久に黙殺される。
 */
fun pruneReimportSeenIds(seenIds: Set<String>, missingIds: Set<String>): Set<String> =
    seenIds intersect missingIds

/** 欠落カードの状態行文言（モック .st）。PDF 系は分岐に関わらず同文＝棚面に4種の語彙を発明しない。 */
fun reimportStatusLabel(plan: ReimportPlan): String = when (plan) {
    is ReimportPlan.AutoWeb -> "Web作品・再取得できます"
    else -> "本文なし・タップで再取込"
}

// ============================================================
// 欠落本の削除＝「復元の最後の機会」を消す破壊的操作（2026-07-29 の実害への対処）
//
// 【真因】復元（PdfBookImporter の restoreByHash／title＋author 経路・WebBookImporter の restoreTarget）は
// 既存の books 行を見つけて updateRestoredContent で本文だけ差し替える＝id・progress・addedAt・栞（shioriTipIndex/
// shioriLenFrac）・ncode が不変のまま戻る。この「見つける鍵」は contentSha256 と title＋author、そして
// sourceUri/sourceUrl のいずれも **books 行そのもの** に載っている。よって欠落本の books 行を消すと:
//   ・鍵が全て消える（照合先が存在しない）
//   ・progress 行も同一トランザクションで消える（LibraryDeleter.deleteBook）
//   ・以後 同じ PDF を取り込んでも新規 UUID の別行になる（PdfBookImporter の bookId＝UUID.randomUUID）
// ＝読書位置・読了の印・追加日・栞の意匠・なろう紐付けが原理的に再結合できない。実害（2026-07-29）はこれで、
// ユーザーは復旧導線が動かず欠落本を削除→取り込み直し、全7冊の addedAt と progress を失った。
// 削除ダイアログがこの不可逆性を語らず「通常の削除」として振る舞っていたことが直接の原因。
//
// 判定を純関数に切り出す理由: 表示分岐（どのスキンの削除ダイアログでも同じ警告が要る）と文言を1か所に固定し、
// 「欠落0冊なら null＝通常の削除は一文字も変わらない」という不変条件を JVM テストで縛るため。
// ============================================================

/**
 * 欠落本を含む削除の警告文（削除確認ダイアログの追加ブロック）。
 * 正本＝モック bookshelf-multiselect-D「削除確認（欠落本を含む）」の .dlg p（分岐4系統の文脈は
 * bookshelf-reimport-badge-D ⑤）。[emphasis] が .warn（--ink・600＝失うものの核心）、
 * [detail] が地の文（--ink-soft）。
 */
data class MissingContentDeleteWarning(val emphasis: String, val detail: String)

/**
 * 削除対象（蔵書 id）のうち本文欠落＝復元の最後の機会を持つ冊数。
 * 判定を [plans]（buildReimportPlans の結果）への所属だけで行うのは、棚のバッジ・カードタップの復旧導線と
 * 「欠落とは何か」の定義を1点に保つため（ここで独自の実体チェックを書くと定義が二重化して片方だけ腐る）。
 */
fun countMissingContentTargets(bookIds: List<String>, plans: Map<String, ReimportPlan>): Int =
    bookIds.count { it in plans }

/**
 * 欠落本を含む削除の警告。[missingCount]==0 なら null＝通常の削除ダイアログは文言も操作も従来と同一。
 *
 * 文言の根拠: ①失うものを具体名で言う（読書位置・しおり・追加日＝再取込ダイアログ群が「残ります」と
 * 約束している当のもの＝語彙を反転させて使う）②脅すのでなく代替手段（カードからの再取込）を必ず添える
 * ——実害の本質は「まだ戻せると知らないまま消した」ことで、警告だけでは同じ結末を防げないため。
 * 「本文なし」は棚バッジの語をそのまま使う（ユーザーが画面で見ている語と一致させる）。
 *
 * @param missingCount 削除対象のうち本文欠落の冊数。
 * @param bookCount 削除対象の蔵書総数（Web カードは含めない＝Web は失うものが無く別文言）。
 */
fun missingContentDeleteWarning(missingCount: Int, bookCount: Int): MissingContentDeleteWarning? {
    if (missingCount <= 0) return null
    // 対象の言い方は3通り。1冊だけ選んで消す（実装上の「単数削除」＝選択1件）ときに「1冊」と数えると
    // 不自然な日本語になるため、そこだけ「この本」と呼ぶ。
    val subject = when {
        bookCount <= 1 -> "この本"
        missingCount >= bookCount -> "選択した${missingCount}冊"
        else -> "選択のうち${missingCount}冊"
    }
    return MissingContentDeleteWarning(
        emphasis = "「本文なし」の本は、削除すると復元できなくなります。",
        detail = "${subject}は、カードから再取込すれば読書位置・しおり・追加日を保ったまま戻せます。" +
            "削除して取り込み直すと別の本になり、これらは戻りません。",
    )
}

/**
 * 削除確定ボタンの文言。欠落本を含むときだけ「復元せずに」を冠して、押す直前の最後の一語でも
 * 何を捨てるのかが分かるようにする（本文コピーは読み飛ばされうる＝実害の再発点はここ）。
 * 通常の削除は従来どおり「削除する」＝既存テスト・スキンの語彙を変えない。
 */
fun deleteConfirmLabel(hasMissingContent: Boolean): String =
    if (hasMissingContent) "復元せずに削除する" else "削除する"
