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
    // PDF↔Web継続読書（目玉①）: この蔵書に対応するなろう作品の Nコード。
    // null = 未紐付け。紐付けはユーザー確定操作（候補選択 or 手動入力）でのみ行う。
    // なぜ自動判定で埋めないか: title 一致だけでは同名別作品の誤紐付けリスクがあり、
    // 誤った「続き」へ誘導すると読書体験を壊すため（候補提示→人間の確定を必須にする）。
    val ncode: String? = null,
    // 取込元 PDF バイト列の SHA-256（小文字16進）。F-G 恒久策：同一内容の PDF を
    // 別 URI（別パスから選び直し）で再取込したとき、重い変換を走らせる前に弾くための内容指紋。
    // null = 判定不能。既存行（本カラム追加=v11 より前に取り込んだ蔵書）は Migration で NULL 補完され、
    // ハッシュ照合では一致しない（＝旧取込分は変換前遮断の対象外で、従来どおり抽出後の
    // title＋author 照合に委ねる＝多層防御の縮退として許容する）。
    val contentSha256: String? = null,
)
