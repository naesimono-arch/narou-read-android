package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// なぜ web_novels と別テーブルか: 読書位置の記録は「本棚に置いた作品(web_novels)」に限らず、検索経由で
// 開いただけの未配置作品でも記録したい（機能②は検索画面推移も対象）。web_novels に lastReadEpisode 列を
// 足すと未配置作品を記録できず、記録のために本棚へ勝手にカードを生やす副作用が要る。記録(進捗)と配置(本棚)を
// 直交させるため、蔵書の progress テーブルと同型で ncode を主キーにした独立テーブルにする。
@Entity(tableName = "web_reading_progress")
data class WebReadingProgressEntity(
    // なぜ ncode が主キーか: なろう作品を一意に識別するため（呼び出し側は trim+uppercase 正規化して渡す
    // ＝web_novels/紐付けと同系。表記ゆれで同一作品が二重行にならないようにする）。
    @PrimaryKey val ncode: String,
    // なぜ lastReadEpisode か: WebView 読書で開いた話ページ(URL .../N/)の話数。続き再開の着地先(第N話)になる。
    // なぜ「最大到達話」か（furthest-wins・2026-07-12 UX監査 continuity Major で last-opened から転換）:
    // 目次から前の話を「確認しに」開くだけで再開先端が後退する事故を防ぐ（公理14: 参照ジャンプは自動保存を
    // 動かさない）。更新判定は recordWebReadingEpisode 側＝前進時のみ upsert。task_diary #56 の
    // 「戻り遷移は記録しない」ガードとは相補（あちらは goBack 遡行・こちらは新規リンクでの前話再訪を止める）。
    // 読了そのものは観測できないため、着地は従来どおり記録話の冒頭(同じ話へ戻す)のまま。
    val lastReadEpisode: Int,
    // なぜ lastReadAt か: 記録時刻(UNIX ミリ秒)。将来の鮮度・並び順判断に使えるよう progress と同じくスタンプする。
    val lastReadAt: Long,
)
