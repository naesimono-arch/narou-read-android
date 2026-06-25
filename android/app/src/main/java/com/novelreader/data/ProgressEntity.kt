package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val lastReadFilename: String,
    // 最後に読んでいた章（lastReadFilename）内のスクロール位置。
    // LazyListState の firstVisibleItemIndex / ScrollOffset に対応する。
    // なぜ default 0 か: 既存行の Migration 補完値であり、未スクロール（章先頭）を表す。
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    // 最後にこの本を読んだ日時（UNIX ミリ秒）。本棚の最近読書順ソートに使う。
    // 書き込み時に Repository が System.currentTimeMillis() をスタンプする。
    // default 0 は Room read／Migration 補完値（既存行は 0＝未読扱いで最後尾）。
    val lastReadAt: Long = 0L,
)
