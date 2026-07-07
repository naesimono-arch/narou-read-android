package com.novelreader.narou.model

// why: なろうの keyword は作者が自由に付けるタグで語彙の相場を知らないと検索が組めない。頻出タグをカテゴリ別に先回り提示する（ADR 0007 原則3・カクヨムのキュレーションタグ方式）。
data class CuratedKeywordCategory(val title: String, val words: List<String>)

object NarouCuratedKeywords {
    val categories: List<CuratedKeywordCategory> = listOf(
        CuratedKeywordCategory("舞台", listOf("異世界", "現代", "学園", "ゲーム", "VRMMO", "ダンジョン", "和風", "西洋")),
        CuratedKeywordCategory("主人公", listOf("悪役令嬢", "勇者", "魔王", "聖女", "賢者", "冒険者", "騎士", "最強", "おっさん")),
        CuratedKeywordCategory("展開", listOf("追放", "成り上がり", "ざまぁ", "婚約破棄", "復讐", "無双", "チート", "スローライフ", "内政", "溺愛", "ハーレム")),
        CuratedKeywordCategory("雰囲気", listOf("ほのぼの", "コメディ", "シリアス", "ダーク", "切ない", "ハッピーエンド")),
    )
}
