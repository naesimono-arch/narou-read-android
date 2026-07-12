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
    // 最終章の末尾まで到達した＝読了の実績（『了』印・読了フィルタの正本／ssot Major 2026-07-12）。
    // なぜ進捗率でなく専用フラグか（公理8）: 最終章を1行スクロールしただけでは末尾到達の保証が無く
    // （章内総量を DB に持たないため末尾検出は読書画面側でしか出来ない）、進捗率から読了を導出すると
    // 「開いた瞬間に読了」の嘘になる。読書画面が本当に末尾を可視化したときだけ立てる事実ベースの印。
    // なぜ sticky（一度 true になったら維持）か: 読了は実績であり、読み直し（前の章へ戻る・再読）で
    // 取り消されるべきものではない。position 保存（updatePosition）はこの列を意図的に touch しない。
    // default 0（false）は Room read／Migration 補完値（v18 追加・既存行は未読了で補完）。
    val reachedEnd: Boolean = false,
)
