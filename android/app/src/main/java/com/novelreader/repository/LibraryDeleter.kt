package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.WebNovelDao
import com.novelreader.data.WebReadingProgressDao
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

// 分割後も従来どおりのタグで出す（ログ検索の継続性＝挙動不変のため各協力クラスで同一タグを使う）。
private const val TAG = "BookRepository"

/**
 * 責務: 蔵書・Webカードの削除（取込元PDF削除のオプトイン含む）と、それに伴うカスケード掃除・
 * 起動時の孤児回収（孤立HTML・孤児 Web読書位置）。
 *
 * [DefaultBookRepository] の責務分割（2026-07-27 構造リファクタ）で委譲抽出した協力クラス。
 * runInTransaction（トランザクション境界の差替継ぎ目）を注入で受ける理由は
 * DefaultBookRepository のコンストラクタ引数コメント参照。
 */
internal class LibraryDeleter(
    private val context: Context,
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val webNovelDao: WebNovelDao,
    private val webReadingProgressDao: WebReadingProgressDao,
    private val runInTransaction: suspend (block: suspend () -> Unit) -> Unit,
) {

    // なぜ削除も storageKey 正規化か: 保存側（putWebNovel の契約）と同じ Ncode.storageKey を通さないと、
    // 表記ゆれの ncode で削除が空振りしてカードが残り続けるため（NcodeLinkSheet の保存正規化と同系）。
    // 併せて Web読書位置履歴も相乗り削除する（UX監査 privacy）: カードを外したのに位置履歴だけ端末へ
    // 残る穴を塞ぐ。ただし同 ncode を紐付けた蔵書がまだ在れば「続きから」に要るため残す（下記 helper が判定）。
    suspend fun removeWebNovel(ncode: Ncode) = withContext(Dispatchers.IO) {
        val key = ncode.storageKey
        webNovelDao.deleteByNcode(key)
        cascadeDeleteWebProgressIfUnreferenced(key)
    }

    suspend fun deleteBook(book: BookEntity, deleteSource: Boolean): SourceDeleteOutcome = withContext(Dispatchers.IO) {
        // なぜトランザクションか（孤児progress行の恒久残留を防ぐ）: books 行を消してから progress 行を消すまでの間に
        // プロセスが kill されると、本体が消えたのに progress だけ残る「孤児」になる。progress は本削除時にしか
        // 掃除されない（books 経由でしか辿らない）ため掃除経路が無く恒久残留する。両削除を1トランザクションに束ね、
        // どちらも commit されるか一切行われないか（原子性）にして中間状態を消す。
        runInTransaction {
            bookDao.deleteById(book.id)
            progressDao.deleteByBookId(book.id)
        }
        // 紐付いていた Web読書位置履歴も相乗り削除する（UX監査 privacy）: 本を消したのに、その本に
        // 紐付いた ncode の WebView 読書位置だけ端末へ残る穴を塞ぐ。ただし同 ncode が web_novels カード
        // として独立に棚に在るなら、その Web 読書はまだ現役なので残す（helper が参照有無で判定）。
        book.ncode?.let { cascadeDeleteWebProgressIfUnreferenced(Ncode(it).storageKey) }
        // 取込時 cache PDF（cache/pdf_import/<ncode>.pdf・なろう縦書きPDF取込の DL 実体）の後始末。
        // いつ消すか＝「この ncode を名乗る蔵書行が1冊も残らなくなったとき」。
        //   ・無条件即消しにしない理由: この PDF は sourceUri/sourceUrl を持たない本の唯一の復旧資源
        //     （ReimportPlan.AutoCachePdf＝本文欠落時に無操作で戻す鍵）。同 ncode の蔵書行が残る間は
        //     その本のために残す。行が全て消えた後は復元の鍵（id・進捗・栞）ごと消えており、
        //     残しても誰も使えないただの残骸＝ここで消す（放置すると数十MB/冊が cache に堆積する実測）。
        //   ・web_novels カードは判定に含めない理由: Web カードは cache PDF を読まない（復旧は sourceUrl の
        //     Web 再取得で戻る）＝参照実態が無いものを「参照」と数えると残骸が永久に消えなくなる。
        //   ・ユーザーのオプトイン（deleteSource）に載せない理由: あれはユーザー自身が保存した SAF 上の
        //     ファイルの扱いを問うもの。cache はアプリ専用領域でユーザーからは見えず、問うだけ混乱させる。
        // 削除と本チェックの間にプロセス kill が入って残っても、起動時の sweepOrphanNarouPdfCache が拾う。
        book.ncode?.let { ncode ->
            val key = Ncode(ncode).storageKey
            val stillReferenced = bookDao.getAllBooks().first()
                .any { b -> b.ncode?.let { Ncode(it).storageKey } == key }
            if (!stillReferenced) NarouPdfCache.deleteFor(context.cacheDir, ncode)
        }
        // HTMLディレクトリ削除は DB 外の副作用のためトランザクション外に置く（ファイルIO は Room の
        // トランザクションでロールバックできず、失敗しても DB 削除は成立させたい＝掃除は次回起動の
        // cleanOrphanHtmlDirs が拾う）。
        if (!File(book.htmlDirPath).deleteRecursively()) {
            Log.w(TAG, "HTMLディレクトリの削除に失敗: ${book.htmlDirPath}")
        }
        // 取込元 PDF 本体の削除（オプトイン）と、取込元 URI 永続権限の解放。
        // 本が消えた時点でこの URI の永続権限は保持不要（起動時掃除の keepUris 対象から外れ孤児化する）ため、
        // 取込元削除の有無に関わらず必ず解放する（残すと端末上限128件を圧迫。次回起動の掃除でも拾えるが即時が明快）。
        val src = book.sourceUri ?: return@withContext SourceDeleteOutcome.NoSource
        val srcUri = Uri.parse(src)
        val outcome = if (!deleteSource) {
            SourceDeleteOutcome.NotRequested
        } else {
            // なぜ deleteDocument か: SAF ドキュメント URI（ACTION_OPEN_DOCUMENT 由来）の実体削除の標準 API。
            // 失敗要因（既に移動/削除済み=FileNotFoundException・権限失効=SecurityException・削除非対応
            // プロバイダ=UnsupportedOperationException・戻り値 false）を全て runCatching で吸収し、本削除は
            // 既に成立させたまま結末だけ返す（handover 提起③の失敗ハンドリング）。
            val ok = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, srcUri)
            }.getOrDefault(false)
            if (ok) SourceDeleteOutcome.Deleted else SourceDeleteOutcome.Failed
        }
        // 権限解放は削除を試みた後（deleteDocument に書込権限が要るため）。
        releasePersistedPermission(context, srcUri)
        outcome
    }

    /** ある ncode（正規化済み）が books.ncode / web_novels のどちらからも参照されなくなっていれば、
     *  その Web読書位置履歴を削除する（相乗り削除の安全弁）。削除後の現況を books/web_novels の
     *  スナップショットで確認し、まだ参照が残るなら「続きから」に要るため履歴を残す（過剰削除の防止）。
     *  なぜ Flow.first() で snapshot を取るか: 一度きりの現況照会に十分で、BookDao/WebNovelDao へ
     *  専用 suspend クエリを足さずに済む（呼び出しは削除操作の直後のみで高頻度でない）。 */
    private suspend fun cascadeDeleteWebProgressIfUnreferenced(normalizedNcode: String) {
        val referencedByBook = bookDao.getAllBooks().first()
            .any { b -> b.ncode?.let { Ncode(it).storageKey } == normalizedNcode }
        val referencedByCard = webNovelDao.getAll().first()
            .any { Ncode(it.ncode).storageKey == normalizedNcode }
        if (!referencedByBook && !referencedByCard) {
            webReadingProgressDao.deleteByNcode(normalizedNcode)
        }
    }

    /** books テーブルに存在しない bookId の HTML ディレクトリを削除する（孤立HTML掃除）。
     *  強制終了（OEM kill/OOM）ではプロセスごと消えるため addBook 内 catch のクリーンアップが
     *  走らず、書きかけの novels/<bookId>/ が残り得る。DB 登録（NonCancellable の最終確定）が
     *  完了の境界なので「DB に無い = 未完了の書きかけ」と判定して安全に消せる。
     *  【前提】Service 非稼働時に呼ぶこと（処理中の本の出力ディレクトリを誤削除しないため。
     *  呼び出し側 runStartupRecoveryOnce が processingState で判定する）。 */
    // 明示 : Unit — 式本体の最終式が `?.forEach`（Unit?）のため、interface の Unit と型を一致させる。
    suspend fun cleanOrphanHtmlDirs(): Unit = withContext(Dispatchers.IO) {
        val novelsDir = File(context.filesDir, BookEntity.NOVELS_SUBDIR)
        val validIds = bookDao.getAllBookIds().toSet()
        novelsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in validIds) {
                if (dir.deleteRecursively()) Log.i(TAG, "孤立HTMLを掃除: ${dir.name}")
                else Log.w(TAG, "孤立HTMLの削除に失敗: ${dir.absolutePath}")
            }
        }
    }

    /**
     * 起動時クリーンアップ: どの蔵書（books.ncode）にも対応しない取込時 cache PDF
     * （cache/pdf_import/）を回収する。
     *
     * なぜ削除時カスケード（deleteBook 内）だけでは足りないか:
     *   ①カスケード導入以前の削除で既に残った分（実機で実測済みの残骸）はどの削除経路も再訪しない
     *   ②行削除→cache 削除の間のプロセス kill で取りこぼす
     * cleanOrphanHtmlDirs / pruneOrphanWebReadingProgress と同じ「起動時に不変条件を回復する」掃除で
     * 完全化する。【前提】呼び出しは runStartupRecoveryOnce の Service 非稼働ガード下＝変換中の DL 実体を
     * 誤削除しない。DL そのもの（PdfImportViewModel）も Activity 生成後のユーザー操作でしか始まらず、
     * プロセスごと1回の起動時掃除とは時間的に重ならない。
     *
     * @param pendingImportFileNames pending_jobs が参照する cache 内ファイル名（再開予定の DL 実体）。
     *   呼び出し側（DefaultBookRepository）が pending URI から復元して渡す。
     * @return 削除したファイル数（呼び出し側のログ用）。
     */
    suspend fun sweepOrphanNarouPdfCache(pendingImportFileNames: Set<String>): Int = withContext(Dispatchers.IO) {
        val keepNcodes = bookDao.getAllBooks().first()
            .mapNotNullTo(mutableSetOf()) { b -> b.ncode?.let { Ncode(it).storageKey } }
        val swept = NarouPdfCache.sweepOrphans(context.cacheDir, keepNcodes, pendingImportFileNames)
        if (swept > 0) Log.i(TAG, "孤児の取込時cache PDFを掃除: ${swept}件")
        swept
    }

    /**
     * 起動時クリーンアップ: どの棚項目（books.ncode / web_novels）にも紐付かない「孤児」の
     * web_reading_progress 行を回収する（UX監査 privacy・削除の完全性）。
     *
     * なぜ相乗り削除だけでは足りないか: 相乗り削除は removeWebNovel/deleteBook の直後にしか走らないため、
     * その途中でプロセスが kill された場合や、過去バージョンで削除経路が無かった頃に溜まった履歴は残る。
     * cleanOrphanHtmlDirs（孤立HTML掃除）と同じ「起動時に不変条件を回復する」掃除でこれを完全化する。
     *
     * @return 削除した孤児行数（呼び出し側のログ用）。
     */
    suspend fun pruneOrphanWebReadingProgress(): Int = withContext(Dispatchers.IO) {
        val keep = buildSet {
            bookDao.getAllBooks().first().forEach { b -> b.ncode?.let { add(Ncode(it).storageKey) } }
            webNovelDao.getAll().first().forEach { add(Ncode(it.ncode).storageKey) }
        }
        val all = webReadingProgressDao.getAll().first().map { it.ncode }.toSet()
        val orphans = orphanedWebProgressNcodes(all, keep)
        orphans.forEach { webReadingProgressDao.deleteByNcode(it) }
        orphans.size
    }
}

/**
 * どの棚項目にも紐付かない「孤児」の web_reading_progress を判定する純関数（テスト対象）。
 * 全 ncode から、books.ncode / web_novels に生きている ncode（keep）を差し引く。
 * orphanedPermissionUris と同じ流儀で、掃除の中核ロジックを contentResolver/Room 非依存で単体テストする
 * （pruneOrphanWebReadingProgress が snapshot 取得と実削除の副作用を担い、判定はここ）。
 *
 * 突合は storageKey 形で行う（keep は呼び出し側で storageKey 化済み・all は DB 生値のまま渡す）。
 * なぜ生値を単純差集合しないか: 保存契約（書き込み側が storageKey 正規化）に違反した過去行が
 * あった場合、無正規化の突合では「生きている本の読書位置」を孤児と誤判定して削除してしまう
 * （2026-07-27 是正・ユーザー裁定）。戻り値は DAO の deleteByNcode キーに使うため生値のまま返す。
 */
internal fun orphanedWebProgressNcodes(
    allNcodes: Set<String>,
    keepNcodes: Set<String>,
): Set<String> = allNcodes.filterTo(mutableSetOf()) { Ncode(it).storageKey !in keepNcodes }
