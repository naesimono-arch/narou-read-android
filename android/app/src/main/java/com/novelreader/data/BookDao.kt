package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // 二層の既定ソート（ADR 0016・2026-07-16 層反転・ShelfItems.recencyKeyOf と一致必須）:
    //   第1層＝未読（lastReadAt=0）を常に上、第2層＝触った本（lastReadAt>0）を下に置く。
    //   層内は 第1層＝addedAt / 第2層＝lastReadAt の降順、最後にタイトル昇順で安定化。
    // なぜ未読が上（層反転・実使用フィードバック）: 旧版は読書中を上層にしていたが「取り込んだばかりの
    // 未読が読みかけの下に埋もれて見つけにくい」不満が出た。ユーザー裁定で「取り込んだ本＝未読を最上位に」反転。
    // CASE のソートキーは 未読→addedAt・触った本→lastReadAt を選び、tier は未読を大きく（先）にする。
    @Query(
        "SELECT b.* FROM books b " +
        "LEFT JOIN progress p ON b.id = p.bookId " +
        "ORDER BY (CASE WHEN COALESCE(p.lastReadAt, 0) > 0 THEN 0 ELSE 1 END) DESC, " +
        "(CASE WHEN COALESCE(p.lastReadAt, 0) > 0 THEN p.lastReadAt ELSE b.addedAt END) DESC, " +
        "b.title ASC"
    )
    fun getAllBooks(): Flow<List<BookEntity>>

    /** 孤立HTML掃除（BookRepository.cleanOrphanHtmlDirs）の突合用。
     *  Flow の getAllBooks と違い一回きりのスナップショットで足りるため suspend で返す。 */
    @Query("SELECT id FROM books")
    suspend fun getAllBookIds(): List<String>

    /** 同一PDF二重取込のべき等ガード（UX監査 F-G 公理3）用の既存蔵書照合。
     *  なぜ title＋author か: 取込元 URI（sourceUri 列）を持つ本もあるが、同一 PDF を別パスから選び直すと
     *  URI は変わる＝取込元 URI では「同じ本」を同定できない。抽出後に必ず得られる安定属性の組で判定する。
     *  完全一致 1 件で足りるため LIMIT 1。 */
    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findByTitleAndAuthor(title: String, author: String): BookEntity?

    /** 内容ハッシュによる二重変換の変換前遮断（F-G 恒久策）用の既存蔵書照合。
     *  なぜ title＋author 照合（findByTitleAndAuthor）と別に要るか: URI が変わる再取込は
     *  変換前にはタイトル/著者が未判明（抽出しないと得られない）だが、PDF バイト列の SHA-256 は
     *  ファイル選択直後に計算できる。同一内容の PDF を別パスから選び直した場合を「重い変換を
     *  走らせる前」に弾くための指紋照合。既存行（v11 未満で取り込んだ蔵書）は contentSha256 が
     *  NULL のため SQL の NULL 比較で一致せず、その場合は従来の title＋author 照合へ委ねる。
     *  完全一致 1 件で足りるため LIMIT 1。 */
    @Query("SELECT * FROM books WHERE contentSha256 = :hash LIMIT 1")
    suspend fun findByContentSha256(hash: String): BookEntity?

    /** Web 取込の重複ガード用の既存蔵書照合（汎用Web小説DL基盤・P3）。
     *  なぜ contentSha256 照合（findByContentSha256）と別に要るか: Web 取込は本文を取得しないと
     *  内容ハッシュを計算できないが、作品トップの正規 URL（sourceUrl）はURL投入直後に確定する。
     *  同一作品の再取込を「目次・全章の取得という重い処理を走らせる前」に弾くための URL 指紋照合＝
     *  PDF 経路の「変換前遮断」（findByContentSha256）と同じ位置づけ。PDF 由来の蔵書は sourceUrl が
     *  NULL のため SQL の NULL 比較で決してヒットしない（＝Web 取込分だけを対象にできる）。
     *  完全一致 1 件で足りるため LIMIT 1。 */
    @Query("SELECT * FROM books WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun findBySourceUrl(sourceUrl: String): BookEntity?

    /** 取込元 URI を保持する（＝削除可能な取込元PDFを持つ）全蔵書の sourceUri 一覧。
     *  起動時の孤児権限掃除（DefaultBookRepository.releaseOrphanedPermissions）で、これらの永続 URI 権限を
     *  誤って解放しないための keepUris へ合流させる（NovelReaderApplication 呼び出し側で union）。
     *  一回きりのスナップショットで足りるため suspend（getAllBookIds と同方針）。 */
    @Query("SELECT sourceUri FROM books WHERE sourceUri IS NOT NULL")
    suspend fun getPersistedSourceUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。
    // なぜ部分 UPDATE か: insertBook(REPLACE) で全列上書きすると、読書中に保持している
    // 古い BookEntity スナップショットで他列（addedAt 等）を巻き戻すリスクがあるため。
    @Query("UPDATE books SET ncode = :ncode WHERE id = :bookId")
    suspend fun updateNcode(bookId: String, ncode: String?)
}
