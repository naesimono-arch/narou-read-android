package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// なぜ books と別テーブルか: BookEntity は htmlDirPath 必須など取込済み前提の不変条件が強く、未取込作品を混ぜると全経路に null ガードが波及するため
@Entity(tableName = "web_novels")
data class WebNovelEntity(
    // なぜ ncode が主キーか: 大文字正規化済みのなろう作品の Nコード（呼び出し側で Ncode.value を渡す想定）によって作品を一意に識別するため。
    @PrimaryKey val ncode: String,
    // なぜ title が必要なのか: 本棚に「未取込カード」として作品タイトルを表示するため。
    val title: String,
    // なぜ writer が必要なのか: 本棚に「未取込カード」として作者名を表示するため。
    val writer: String,
    // なぜ generalAllNo が必要なのか: 置いた（登録した）時点での全話数スナップショットを記録し、その後の話数更新チェックなどの基準点とするため。
    val generalAllNo: Int,
    // なぜ addedAt が必要なのか: 本棚に追加した日時（UNIX ミリ秒）を記録し、最近追加された順でのソートを行うため。
    val addedAt: Long,
)
