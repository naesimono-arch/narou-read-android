package com.novelreader.discovery.model

import androidx.compose.runtime.Immutable

/**
 * サイト非依存の作品要約モデル（発見層＝P5 脱なろう refactor の第1段）。
 *
 * なぜ narou/ の外・ui/ の外に置くか: これまで発見系 UI はなろう固有の
 * [com.novelreader.narou.model.NarouNovel]（Moshi DTO）を直接消費し、novelType の二重キー・
 * end の逆転意味論・allcount センチネルといった「なろうAPIの都合」が UI 層まで漏れていた。
 * 将来の Web スクレイピング系サイト（scrape/ アダプタ）を同じ一覧・詳細に載せるには、UI が読むのは
 * サイト非依存な要約型である必要がある。本型はその共通語彙で、各サイトのマッパ
 * （なろうは [com.novelreader.narou.toWorkSummary]）が自サイトの DTO をここへ翻訳する。
 *
 * この第1段では型を新設するのみで既存 UI 消費経路は切り替えない（第2段で全面移行）。
 *
 * なぜ @Immutable か: Compose の一覧に安定パラメータとして渡す前提のため（不変・再コンポーズ最適化）。
 */
@Immutable
data class WorkSummary(
    val title: String,
    /** 作者名（← writer）。 */
    val author: String,
    /**
     * 出自サイトのキー（なろう＝"narou"）。scrape アダプタの siteKey（例 "kakuyomu"）と同じ語彙空間で、
     * どのサイト由来かを型でなく値で持つ（サイト非依存層に enum を増やさず新規サイトを足せるように）。
     */
    val sourceSite: String,
    /** 作品の公式 URL（なろうは ncode から導出）。導出不能なら null。 */
    val workUrl: String?,
    /**
     * ⚠️ なろう固有の残置フィールド（サイト非依存化の**例外**・なろう作品のみ非 null）。
     * 蔵書突合（BookEntity.ncode）と web_reading_progress のキーに現用のため、抽象化の対象外として
     * ここに残す。他サイト由来の要約では常に null。恒久的にはサイト非依存の作品識別子へ寄せたいが、
     * 現行の突合キーを壊さないため第1段では残置する。
     */
    val ncode: String?,
    /** 総掲載話数（← general_all_no）。短編は 1、欠損時 null。 */
    val chapterCount: Int?,
    /** 連載状態。なろうの novelType＋end の意味論はマッパ [com.novelreader.narou.toWorkSummary] が吸収済み。 */
    val serialState: SerialState?,
    /** 実質文字数（← length）。 */
    val lengthChars: Int?,
    /** 読了目安（分）（← time）。 */
    val readMinutes: Int?,
    /**
     * ⚠️ なろうジャンルコード（固有値）。ラベル引きは既存流儀のまま
     * [com.novelreader.narou.model.NarouGenres.genreLabel] に委ねる（コード→ラベルの対応表はなろう固有）。
     * 他サイトはジャンルの概念・コード体系が異なるため、これも当面はなろう固有の残置。
     */
    val genreCode: Int?,
    /** 期間別ポイント。どのポイントも欠損ならマッパが集約して null にする（[WorkPoints] の KDoc 参照）。 */
    val points: WorkPoints?,
)

/**
 * 連載状態のサイト非依存表現。SHORT=短編・ONGOING=連載中・COMPLETED=完結済。
 *
 * なろうは novelType（1=連載/2=短編）と end（0=短編 or 完結・1=連載中）の2軸から
 * [com.novelreader.narou.toWorkSummary] が判定する（UI からなろうコードを消すための吸収層）。
 * enum を nullable フィールドで持つのは、連載状態を露出しない他サイト由来の要約に備えた抽象化上の都合。
 */
enum class SerialState { SHORT, ONGOING, COMPLETED }

/**
 * 期間別の作品ポイント（サイト非依存）。各値は欠損しうるため個別に nullable。
 *
 * @param global  累計（総合）ポイント（← global_point）。
 * @param daily   日間ポイント（← daily_point）。
 * @param weekly  週間ポイント（← weekly_point）。
 * @param monthly 月間ポイント（← monthly_point）。
 * @param quarter 四半期ポイント（← quarter_point）。
 */
@Immutable
data class WorkPoints(
    val global: Int?,
    val daily: Int?,
    val weekly: Int?,
    val monthly: Int?,
    val quarter: Int?,
)
