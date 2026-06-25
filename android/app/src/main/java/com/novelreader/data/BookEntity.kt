package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val htmlDirPath: String,
    // 末尾に追加：既存の位置引数呼び出し BookEntity(id, title, htmlDirPath) が壊れないよう
    // デフォルト値 "" を設定。STEP 9 で author 取得後に渡すようになる。
    val author: String = "",
    // 本棚を「最近の活動順」に並べるための追加日時（UNIX ミリ秒）。
    // 未読の本はこの値で、既読の本は progress.lastReadAt で並ぶ（BookDao 参照）。
    // default 0 は Migration 補完値（既存行は 0＝最後尾クラスタでタイトル順）。
    val addedAt: Long = 0L,
)
