package com.novelreader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// なぜフォルダでなくフラットなラベルか: モック正本 bookshelf-fusion-D.html の絞り込みチップ行（.lchip）がフラットな多対多を前提とした意匠のため（階層は設けない）。
@Entity(
    tableName = "labels",
    indices = [Index(value = ["name"], unique = true)]
)
data class LabelEntity(
    // なぜ UUID 文字列か: books.id と同じ流儀で、分散生成やローカルでの ID 競合を避け、一意性を確保するため。
    @PrimaryKey val id: String,
    // なぜ name なのか: ユーザーが識別できるラベル名（例: 異世界・完結済み・あとで読む）を表示するため。
    // なぜ name に unique index を付与するのか: 同名ラベルの二重作成を DB 層で遮断し、一意性を保証するため。
    val name: String,
    // なぜ createdAt なのか: ラベルの追加日時（UNIX ミリ秒）を記録し、本棚などのチップ行で作成された順番通りに安定してソート・表示するため。
    val createdAt: Long,
)
