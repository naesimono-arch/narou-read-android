package com.novelreader.narou.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * なろう小説API(`novelapi/api`) レスポンスの1要素。
 *
 * なぜ全フィールド nullable + デフォルト値か:
 *  (1) API はレスポンス配列の**先頭要素にだけ** `{"allcount": N}` を入れ、以降が作品要素という仕様
 *      （narou_api_manual.md §5）。先頭要素は allcount 以外を持たないため、同じ型で両方を受けるには
 *      作品側フィールドが欠損可（null 許容）である必要がある。
 *  (2) `of` パラメータで取得項目を絞るとレスポンスに含まれないキーが出るため、欠損に強くしておく。
 *
 * ⚠️ JSON のキー名は **フルネーム**（`title`/`ncode`/`global_point` 等）。`of` の略号(t/n/gp…)は
 *    リクエスト時の項目選択用であって、レスポンスのキー名ではない（narou_api_manual.md §5 の対応表）。
 */
@JsonClass(generateAdapter = true)
data class NarouNovel(
    /** 全作品出力数。レスポンス先頭要素にのみ入り、作品要素では null（narou_api_manual.md §5）。 */
    @Json(name = "allcount") val allcount: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "ncode") val ncode: String? = null,
    @Json(name = "writer") val writer: String? = null,
    /** あらすじ。 */
    @Json(name = "story") val story: String? = null,
    /** 総合評価ポイント（ブックマーク数×2 ＋ 評価ポイント）。 */
    @Json(name = "global_point") val globalPoint: Int? = null,
    /** 全掲載エピソード数（短編の場合は1）。=general_all_no。 */
    @Json(name = "general_all_no") val generalAllNo: Int? = null,
    /**
     * ⚠️ 意味が直感と逆。narou_api_manual.md §5 の定義そのまま:
     *   end = 0 → 短編 または 完結済
     *   end = 1 → 連載中
     * （「1=完結」ではないので、完結判定に使うときは要注意。連載/短編/完結の3値は novelType と併用して解釈する）
     */
    @Json(name = "end") val end: Int? = null,
    /** 作品文字数（実質文字数）。=length。 */
    @Json(name = "length") val length: Int? = null,
    /**
     * 作品種別（of 指定時のキー）。読み取りは [novelType] を使うこと（直接読むと of 無指定経路で常に null）。
     * ⚠️ 元の項目名は `novel_type` だが、`of` 指定時のレスポンスでは `noveltype` として返る
     * （narou_api_manual.md §5 の注記。of 無指定＝novelDetail 経路は `novel_type` で返ることを実APIで確認済み 2026-07-07）。
     * Moshi は1フィールドに複数キーを張れないため、両キーを別フィールドで受けて [novelType] で合流させる。
     */
    @Json(name = "noveltype") val noveltypeCompact: Int? = null,
    /** 作品種別（of 無指定時のキー）。読み取りは [novelType] を使うこと。 */
    @Json(name = "novel_type") val novelTypeFull: Int? = null,
    @Json(name = "genre") val genre: Int? = null,
    @Json(name = "keyword") val keyword: String? = null,
    @Json(name = "general_firstup") val generalFirstup: String? = null,
    @Json(name = "general_lastup") val generalLastup: String? = null,
    /** 作品の更新日時。order=new（新着更新順）のソート根拠（narou_api_manual.md §3・§5 の nu）。 */
    @Json(name = "novelupdated_at") val novelupdatedAt: String? = null,
    @Json(name = "time") val time: Int? = null,
    @Json(name = "fav_novel_cnt") val favNovelCnt: Int? = null,
    @Json(name = "review_cnt") val reviewCnt: Int? = null,
    @Json(name = "all_hyoka_cnt") val allHyokaCnt: Int? = null,
    @Json(name = "sasie_cnt") val sasieCnt: Int? = null,
    @Json(name = "kaiwaritu") val kaiwaritu: Int? = null,
    @Json(name = "daily_point") val dailyPoint: Int? = null,
    @Json(name = "weekly_point") val weeklyPoint: Int? = null,
    @Json(name = "monthly_point") val monthlyPoint: Int? = null,
    @Json(name = "quarter_point") val quarterPoint: Int? = null,
    @Json(name = "istensei") val istensei: Int? = null,
    @Json(name = "istenni") val istenni: Int? = null,
    @Json(name = "iszankoku") val iszankoku: Int? = null,
    @Json(name = "isstop") val isstop: Int? = null,
) {
    /**
     * 作品種別。1=連載, 2=短編。of 指定/無指定のどちらの経路でも読める合流アクセサ
     * （キーの二重性は [noveltypeCompact] の KDoc 参照）。
     */
    val novelType: Int? get() = noveltypeCompact ?: novelTypeFull
}
