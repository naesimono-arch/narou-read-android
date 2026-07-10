package com.novelreader.data

import androidx.room.Entity
import androidx.room.Index

// なぜ book_labels なのか: 本（books）とラベル（labels）の多対多リレーションシップを紐付けるための中間テーブル（junction table）として機能させるため。
// なぜ複合主キー (bookId, labelId) なのか: 同じ本に対して同じラベルが重複して付与されるのを DB 構造レベルで防ぐため。
// なぜ labelId 側にインデックスを張るのか: ラベル削除時の junction 掃除（ひも付け解除）や、ラベルによる本の逆引き・絞り込みクエリを高速に行うため。
// なぜ ForeignKey 制約を設けないのか: 本プロジェクトの既存スキーマ（books や progress 等）が外部キー制約を用いず、すべてアプリ層（Dao）で整合性を管理・掃除する設計方針になっているため。
@Entity(
    tableName = "book_labels",
    primaryKeys = ["bookId", "labelId"],
    indices = [Index("labelId")]
)
data class BookLabelEntity(
    // なぜ bookId なのか: 対象の PDF 蔵書（books.id）を識別するため（web_novels への付与は将来の拡張）。
    val bookId: String,
    // なぜ labelId なのか: 付与対象のラベル（labels.id）を識別するため。
    val labelId: String,
)
