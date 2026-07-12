package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress")
    fun getAllProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT lastReadFilename FROM progress WHERE bookId = :bookId")
    suspend fun getLastRead(bookId: String): String?

    // 章ファイル名とスクロール位置をまとめて取得する（読書再開時の復元用）
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ProgressEntity?

    // 行が無いときだけ挿入する（IGNORE）。既存行があれば何もしない＝reachedEnd 等の既存値を保持する。
    // なぜ REPLACE 上書きをやめたか（reachedEnd を sticky に保つため）: 旧実装は保存のたびに全列を
    // REPLACE していたため、位置更新のたびに reachedEnd が既定 0 へ戻り『了』印が消えてしまう。
    // insertIfAbsent（新規行の作成のみ）＋ updatePosition（位置列のみ更新）に分割して読了実績を守る。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: ProgressEntity)

    // 読書位置（章・スクロール・最終読書日時）だけを更新する。reachedEnd 列は意図的に触らない
    // ＝読了実績を sticky に保つ（位置の上書きで『了』を消さないため／ssot Major 2026-07-12）。
    // なぜ ON CONFLICT DO UPDATE(UPSERT) を使わないか: SQLite の UPSERT は 3.24（API 30）以降で、
    // minSdk 26 の古い端末（SQLite 3.18）では使えない。insertIfAbsent＋この UPDATE の2手で代替する。
    @Query(
        "UPDATE progress SET lastReadFilename = :filename, scrollIndex = :scrollIndex, " +
            "scrollOffset = :scrollOffset, lastReadAt = :lastReadAt WHERE bookId = :bookId"
    )
    suspend fun updatePosition(
        bookId: String,
        filename: String,
        scrollIndex: Int,
        scrollOffset: Int,
        lastReadAt: Long,
    )

    // 末尾到達＝読了フラグを立てる（sticky・冪等）。位置列は触らない。
    // なぜ UPDATE のみ（行の作成をしない）か: 読書画面で最終章の末尾を可視化する頃には、
    // debounce スクロール保存が必ず progress 行を作成済み（章に入ると初期位置が一度書かれる）。
    // 万一 0 行でも実害は「次に最終章を開き末尾を見れば立つ」で自己回復する（冪等な UPDATE）。
    @Query("UPDATE progress SET reachedEnd = 1 WHERE bookId = :bookId")
    suspend fun markReachedEnd(bookId: String)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
