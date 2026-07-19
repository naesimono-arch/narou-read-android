package com.novelreader.narou

import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.WorkPoints
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode

/** なろうの sourceSite キー（scrape アダプタの siteKey と同じ語彙空間＝小文字のサイト識別子）。 */
const val SOURCE_SITE_NAROU = "narou"

/**
 * [NarouNovel]（なろうAPI の Moshi DTO）をサイト非依存の [WorkSummary] へ翻訳する。
 *
 * なぜ narou/ に置くか: この関数はなろうJSONの都合（novelType の二重キー合流・end の逆転意味論・
 * allcount センチネル）を知る唯一の場所であるべきで、それらの知識を discovery/ 側へ漏らさないため。
 * 発見系 UI は [WorkSummary] だけを読み、なろうコードはこのマッパの内側で完結させる（第2段で UI を切替）。
 *
 * @return title または writer が欠損（null）なら null。呼び出し側でフィルタする設計。
 */
fun NarouNovel.toWorkSummary(): WorkSummary? {
    // なぜ title/writer 欠落で skip か: レスポンス先頭の allcount 専用センチネル要素（title/writer が無い）や
    // of で項目を絞った異常応答をここで弾き、UI に「（無題）」の空作品を流さない。Repository が list.drop(1) で
    // センチネルを除く経路とは独立に、マッパ単体でも安全側（null 返し）に倒す。
    val safeTitle = title ?: return null
    val safeAuthor = writer ?: return null

    return WorkSummary(
        title = safeTitle,
        author = safeAuthor,
        sourceSite = SOURCE_SITE_NAROU,
        // 公式 URL は既存の正規化（trim+lowercase）を持つ narouWorkUrl に委ね、導出規則を一箇所に保つ。
        workUrl = ncode?.let { narouWorkUrl(Ncode(it)) },
        ncode = ncode,
        chapterCount = generalAllNo,
        serialState = toSerialState(),
        lengthChars = length,
        readMinutes = time,
        genreCode = genre,
        points = toWorkPoints(),
    )
}

/**
 * novelType（1=連載/2=短編）と end（0=短編 or 完結・1=連載中）から連載状態を判定する。
 *
 * なぜ既存 [com.novelreader.ui.discovery.novelStatusLabel] と同じ分岐か: 第2段で UI 消費を
 * [WorkSummary] へ移す際に表示が変わらないよう、その分岐（type==2→短編／end==0→完結／それ以外→連載中）を
 * そのまま写す。なろうは常にどれかへ定まる（novelType/end 欠損時も「連載中」フォールバックに一致）ため
 * null を返さない。[SerialState] を nullable フィールドにしているのは、連載状態を持たない他サイト由来の
 * 要約に備えた抽象化上の都合（[WorkSummary.serialState] の KDoc 参照）。
 */
private fun NarouNovel.toSerialState(): SerialState = when {
    novelType == 2 -> SerialState.SHORT
    end == 0 -> SerialState.COMPLETED
    else -> SerialState.ONGOING
}

/**
 * 期間別ポイントを集約する。
 *
 * なぜ全欠損を null に集約するか: どのポイントも null の作品は「ポイント情報なし」であり、その状態を
 * WorkPoints（全フィールド null）で持つと消費側が5フィールドを個別に null 判定せねば has-points を
 * 判断できない。1つの null に畳むことで「ポイントがあるか」を一度に判定できる。1つでも値があれば
 * WorkPoints を返し、個々の欠損はフィールドの null として保つ（特定期間の null を見て表示を伏せる
 * pointLabel の既存挙動と整合＝集約で情報は落ちない）。
 */
private fun NarouNovel.toWorkPoints(): WorkPoints? {
    if (globalPoint == null && dailyPoint == null && weeklyPoint == null &&
        monthlyPoint == null && quarterPoint == null
    ) {
        return null
    }
    return WorkPoints(
        global = globalPoint,
        daily = dailyPoint,
        weekly = weeklyPoint,
        monthly = monthlyPoint,
        quarter = quarterPoint,
    )
}
