package com.novelreader.discovery.model

import androidx.compose.runtime.Immutable

/**
 * 作品詳細画面向けのサイト非依存モデル（発見層＝P5 脱なろう refactor 第2段）。
 *
 * なぜ [WorkSummary] を内包し詳細フィールドを足す形か: 一覧・バッジが必要とするのは要約
 * （[WorkSummary]）で足りるが、詳細画面はあらすじ・タグ・各種統計まで見せる。両者を1型に混ぜると
 * 一覧の要約に不要な重いフィールドが載るため、「要約＋詳細だけの上乗せ」で表現する
 * （＝詳細は summary を持つ works detail）。
 *
 * なぜ詳細フィールドが「なろう寄り」の粒度か: D5 初期スコープでは発見＝なろうAPIのままで
 * （検索語彙 DiscoveryQuery/ジャンルもなろう固有）、詳細に出すのもなろう詳細項目。将来サイトを
 * 増やす際は、そのサイトが提供しない項目は null で埋める（各フィールド nullable の理由）。
 *
 * この段では NovelDetailViewModel の公開状態をなろう DTO からこの型へ切替える（境界＝Repository の
 * novelDetail が [com.novelreader.narou.toWorkDetail] で写像）。
 */
@Immutable
data class WorkDetail(
    /** 一覧と共通の要約（タイトル・作者・連載状態・文字数・読了目安・ポイント・ジャンル等）。 */
    val summary: WorkSummary,
    /** あらすじ。 */
    val story: String?,
    /** タグ（なろうは空白区切りの1文字列。表示側で分割する）。 */
    val keyword: String?,
    /** 会話率（％）。 */
    val kaiwaritu: Int?,
    /** 挿絵数（枚）。 */
    val sasieCnt: Int?,
    /** ブックマーク数。 */
    val favNovelCnt: Int?,
    /** 評価者数。 */
    val allHyokaCnt: Int?,
    /** 最終更新日時（文字列 "yyyy-MM-dd HH:mm:ss"）。表示側で整形する。 */
    val generalLastup: String?,
)
