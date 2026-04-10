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
)
