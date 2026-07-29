package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.novelreader.domain.ScanCandidate

/**
 * 責務: SAF ツリー（ACTION_OPEN_DOCUMENT_TREE で得たフォルダ）配下の PDF 列挙と、1件ごとの内容指紋計算。
 *
 * 案X（本文欠落本をフォルダ走査で自動復旧する）の Android 依存部分。判定・照合の純ロジックは
 * domain/PdfFolderScan.kt が持ち、ここは ContentResolver への問い合わせだけを担う。
 *
 * なぜ androidx.documentfile の DocumentFile を使わないか: DocumentFile はファイル1件ごとに
 * ContentProvider へ query を投げる（listFiles→各 getName/getType で再照会）ため、Download のような
 * 大きなフォルダでは列挙だけで数百回の IPC になる。DocumentsContract の子ドキュメント問い合わせは
 * 1ディレクトリ＝1カーソルで必要列をまとめて取れる。依存を1つ増やさずに済む副次効果もある。
 */
internal class PdfTreeScanner(private val context: Context) {

    /**
     * ツリー配下の PDF を再帰的に列挙する（メタデータ照会のみ＝本文は読まない）。
     *
     * @param isCancelled 停止要求。ディレクトリ単位・行単位で協調的に確認する。
     * @throws Exception 根ディレクトリが読めない場合（権限失効など）はそのまま投げる。
     *   なぜ根だけ投げるか: 根が読めない＝走査そのものが成立していないのに「0件でした」と報告すると、
     *   ユーザーには「このフォルダに本は無い」と誤って伝わる（症状を隠す報告になる）。
     *   子ディレクトリ1つの失敗はその枝を飛ばして続ける（1つの読めないサブフォルダで全滅させない）。
     */
    fun enumeratePdfs(treeUri: Uri, isCancelled: () -> Boolean = { false }): List<ScanCandidate> {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: throw IllegalArgumentException("ツリー URI ではありません: $treeUri")
        val found = mutableListOf<ScanCandidate>()
        val pending = ArrayDeque<String>().apply { add(rootDocId) }
        // 同じドキュメントが複数の親に現れる（プロバイダ次第でありうる）ときの無限ループ防止。
        val visited = mutableSetOf(rootDocId)
        var isRoot = true
        while (pending.isNotEmpty() && !isCancelled()) {
            if (visited.size > MAX_DIRECTORIES) {
                // 病的に深い/広いツリーで走査が終わらなくなるのを防ぐ上限。実用上の PDF 保管フォルダは
                // 数階層で収まるため、ここに達したら構成が想定外＝ログを残して打ち切る（黙って切らない）。
                Log.w(TAG, "フォルダ数が上限(${MAX_DIRECTORIES})に達したため列挙を打ち切りました: $treeUri")
                break
            }
            val docId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = try {
                context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
            } catch (e: Exception) {
                if (isRoot) throw e
                Log.w(TAG, "サブフォルダの列挙に失敗（この枝だけ飛ばします）: $docId", e)
                null
            }
            isRoot = false
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                // 列が1つでも欠けるプロバイダは走査対象にできない（ID が無ければ URI を作れない）。
                if (idIdx < 0) {
                    Log.w(TAG, "document_id 列を返さないプロバイダのため列挙できません: $childrenUri")
                    return@use
                }
                while (c.moveToNext() && !isCancelled()) {
                    val childId = c.getString(idIdx) ?: continue
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                    val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (visited.add(childId)) pending.addLast(childId)
                    } else if (isPdf(mime, name)) {
                        found += ScanCandidate(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                            displayName = name,
                        )
                    }
                }
            }
        }
        return found
    }

    /**
     * 1件の PDF の内容指紋（SHA-256）。読めなければ null を返す。
     * なぜ握り潰さず null か: 1件の読み取り失敗（権限の穴・削除直後・壊れたエントリ）で走査全体を
     * 落とすと他の本まで戻せなくなる。真因はログに残し、件数は結果（unreadableCount）としてユーザーへ出す。
     */
    fun sha256Of(docUri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(docUri)?.use { sha256Hex(it) }
        }.onFailure {
            Log.w(TAG, "PDFの指紋計算に失敗（この1件だけ飛ばします）: $docUri", it)
        }.getOrNull()

    /** PDF 判定。MIME を第一にし、application/octet-stream を返すプロバイダ向けに拡張子でも救う。 */
    private fun isPdf(mime: String?, displayName: String): Boolean =
        mime == MIME_PDF || displayName.endsWith(".pdf", ignoreCase = true)

    private companion object {
        const val TAG = "PdfTreeScanner"
        const val MIME_PDF = "application/pdf"
        const val MAX_DIRECTORIES = 500
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
