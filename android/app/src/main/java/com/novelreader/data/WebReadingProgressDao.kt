package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebReadingProgressDao {
    // なぜ Flow<List> か: 本棚カード・作品詳細の「続きから読む」表示を、記録更新に応じてリアルタイム反映させるため。
    @Query("SELECT * FROM web_reading_progress")
    fun getAll(): Flow<List<WebReadingProgressEntity>>

    // なぜ ncode 単体取得も要るか: WebReader 起動時に現在の記録話数を1件だけ照会したい場面（テスト・将来照会）向け。
    @Query("SELECT * FROM web_reading_progress WHERE ncode = :ncode")
    suspend fun get(ncode: String): WebReadingProgressEntity?

    // なぜ REPLACE か: 同じ作品を読み進めるたびに「最後に開いた話数」で上書きする（1作品1行の現在位置表現＝last-wins）。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WebReadingProgressEntity)

    // なぜ ncode 指定の削除が要るか（UX監査 privacy・公理15③/削除の完全性）: WebView で読んだ位置履歴
    // （ncode+話数+時刻）が本削除・Webカード除去でも消えず端末に永久残留する穴を塞ぐため。呼び出し側は
    // 記録時と同じ trim().uppercase() 正規化キーで渡すこと（表記ゆれで削除が空振りしないように）。
    @Query("DELETE FROM web_reading_progress WHERE ncode = :ncode")
    suspend fun deleteByNcode(ncode: String)
}
