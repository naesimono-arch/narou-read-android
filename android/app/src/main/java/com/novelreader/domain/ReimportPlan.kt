package com.novelreader.domain

import com.novelreader.data.BookEntity
import java.net.URLDecoder

// ============================================================
// 本文欠落→再取込提案（2026-07-29 裁定・案B＋案C）
//
// 「books 行はあるが本文実体（index.html 等）が無い」蔵書を起動時に一括検出し、
// 復旧手段を sourceUri/sourceUrl の状態で4分岐に分類する純ロジック。
// 機序＝uninstall→Auto Backup が DB のみ復元して files/novels/ が空になる
// （knowledge upload-signing-verify-wipes-device-library）。
// ファイル実在判定・永続権限照会は Android 依存のため関数注入で受け、
// 分類そのものは JVM 単体テスト可能な純関数に保つ。
// ============================================================

/**
 * 欠落本1冊の復旧手段（モック bookshelf-reimport-badge-D.html の4分岐）。
 * 分岐①④＝自動復旧可能（ユーザー操作は確認のみ）／②③＝SAF ピッカーでの選び直しが必須。
 * 案C の「まとめて再取込」は①④だけを対象にする（ピッカー必須分を混ぜると「まとめて」が嘘になる）。
 */
sealed interface ReimportPlan {
    /** ① sourceUri 記録あり・読取権限が生存＝元のPDFから自動で再変換できる。 */
    data class AutoPdf(val sourceUri: String) : ReimportPlan

    /** ② sourceUri 記録はあるが永続権限が失効＝同じPDFの選び直しが必要（fileNameHint が手がかり）。 */
    data class PickPdfPermissionLost(val fileNameHint: String?) : ReimportPlan

    /** ③ sourceUri NULL（v20 前の旧取込等）＝手がかり無しでPDFを選んでもらう。 */
    data object PickPdfNoRecord : ReimportPlan

    /** ④ Web 取込本（sourceUrl あり）＝作品ページから自動で再取得できる。 */
    data class AutoWeb(val sourceUrl: String) : ReimportPlan

    /** 自動復旧可能な分岐か（案C の一括対象＝①④）。 */
    val isAuto: Boolean
        get() = this is AutoPdf || this is AutoWeb
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
    val sourceUri = book.sourceUri ?: return ReimportPlan.PickPdfNoRecord
    return if (hasPersistedRead(sourceUri)) {
        ReimportPlan.AutoPdf(sourceUri)
    } else {
        ReimportPlan.PickPdfPermissionLost(sourceFileNameHint(sourceUri))
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
 * SAF ドキュメント URI からファイル名の手がかりを取り出す（分岐②の選び直し材料）。
 * 権限失効後は ContentProvider へ DISPLAY_NAME を照会できない（SecurityException）ため、
 * URI 文字列そのものから復元する: 最終セグメントを URL デコードし、
 * "primary:Download/foo.pdf" / "raw:/storage/.../foo.pdf" 形式の授権 ID から末尾のファイル名を切り出す。
 * 形式が読めない URI では null（ダイアログは手がかり行を出さないだけで成立する）。
 */
fun sourceFileNameHint(sourceUri: String): String? {
    val lastSegment = sourceUri.substringAfterLast('/')
    if (lastSegment.isBlank()) return null
    val decoded = runCatching { URLDecoder.decode(lastSegment, "UTF-8") }.getOrNull() ?: return null
    // 授権 ID（"primary:Download/foo.pdf" 等）は「最後の : の後ろ」→さらに「最後の / の後ろ」がファイル名。
    val name = decoded.substringAfterLast(':').substringAfterLast('/').trim()
    return name.ifBlank { null }
}

/** 案C バナーに出す内訳（分岐4系統の冊数。モックの .roll と1対1）。 */
data class ReimportBreakdown(
    val autoPdf: Int,
    val autoWeb: Int,
    val pickPermissionLost: Int,
    val pickNoRecord: Int,
) {
    val total: Int get() = autoPdf + autoWeb + pickPermissionLost + pickNoRecord

    /** 自動で戻せる冊数（①＋④＝一括再取込の実行対象）。 */
    val autoTotal: Int get() = autoPdf + autoWeb

    /** ユーザー操作（SAF 選び直し）が要る冊数（②＋③）。 */
    val manualTotal: Int get() = pickPermissionLost + pickNoRecord
}

/** 検出結果から内訳冊数を数える（一括確認ダイアログの表示データ）。 */
fun reimportBreakdown(plans: Collection<ReimportPlan>): ReimportBreakdown = ReimportBreakdown(
    autoPdf = plans.count { it is ReimportPlan.AutoPdf },
    autoWeb = plans.count { it is ReimportPlan.AutoWeb },
    pickPermissionLost = plans.count { it is ReimportPlan.PickPdfPermissionLost },
    pickNoRecord = plans.count { it is ReimportPlan.PickPdfNoRecord },
)

/**
 * 案C バナーを出すか（「新規に検出した際に一度だけ表示」のユーザー裁定）。
 * seenIds＝前回バナーを提示（あとで/実行）した時点の欠落 bookId 集合（prefs 永続）。
 * 「欠落集合に seen に無い id が1つでもあれば表示」＝同一集合では再表示せず、
 * 新たな欠落が増えたときだけ再表示する。集合が縮む（復旧が進む）だけでは出さない。
 */
fun shouldShowReimportSweep(missingIds: Set<String>, seenIds: Set<String>): Boolean =
    (missingIds - seenIds).isNotEmpty()

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
