package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// なぜ基準値が必要か: 差分の基準を『手元の章数』にすると取込むまで毎日同じ通知が再送されるため、『前回通知済みの話数』を持ち、増分があった時だけ通知する。
@Entity(tableName = "new_episode_marks")
data class NewEpisodeMarkEntity(
    // なぜ ncode が主キーか: 大文字正規化済みのなろう作品の Nコード（books.ncode 紐付け作品）によって基準値を一意に識別するため。
    @PrimaryKey val ncode: String,
    // なぜ lastNotifiedAllNo が必要なのか: この話数までは通知済みであることを示し、初回チェック時は現在値で無音初期化し、次回以降に増分があったときだけ通知するために使用する。
    val lastNotifiedAllNo: Int,
    // なぜ lastCheckedAt が必要なのか: 最後に更新チェックを実行した時刻（UNIX ミリ秒）を記録し、正しくチェックが行われているかのデバッグや診断に用いるため。
    val lastCheckedAt: Long,
)
