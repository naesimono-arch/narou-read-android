package com.novelreader.ui.screenshot

import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.WorkDetail
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.discovery.model.workDetail
import com.novelreader.discovery.model.workPoints
import com.novelreader.discovery.model.workSummary

/**
 * さがす配下（検索・ジャンル・結果一覧・作品詳細）の screenshot フィクスチャ（監査 2026-08-06 G-8 の 0枚ルート）。
 *
 * ### なぜ「無難な短い値」でなく長い実データ相当を既定にするか
 * この束が狙う退行は fontScale 2.0 での**器の破綻**で、走査 `tools/check_golden_*.py` 3本が見るのも
 * 「同一 case の 1.0 と 2.0 の差」だけ。題名 "t"・作者 "w"（[workSummary] の既定値）で撮ると
 * 2.0 でも折り返しが起きず、1.0 と 2.0 の差がほぼ出ない＝**撮っているのに何も守らない golden** になる。
 * よって各値は「なろうで実際に見かける長さ」を採る:
 *  - 題名 37字＝書籍化前の説明的タイトルの実測レンジ上限側（ヒーロー書影の題字と TopAppBar の省略を試す）
 *  - 作者 15字＋装飾＝長ハンドルの実例形。詳細画面の作者行は `weight(1f, fill=false)` でジャンルタグの
 *    幅を先に確保する設計なので、短い作者名では**その防御が効いているかを一切検証できない**
 *    （同型の実機バグが DiscoveryCommon.NovelListRow で起きた経緯が本番コメントに残っている）。
 *  - ジャンルは 302「ヒューマンドラマ」＝小ジャンル最長ラベル。`softWrap = false` の縦積み禁止が
 *    最も破れやすい組み合わせを狙う。
 *  - あらすじ 約210字＝なろう API の `story` が実際に返す長さ帯。2.0 で本文ブロックが何行になるかを見る。
 *  - キーワード 24個＝FlowRow が何段に折り返すかを見る（実作品のタグ数は10〜30が普通）。
 *
 * ### 決定性について
 * 値はすべて定数で、日付・乱数・端末状態に依存しない。唯一の外部依存は作品詳細の「HH:mm 時点の情報」
 * （`SimpleDateFormat` が既定 TimeZone を読む）で、そこは撮影テスト側が TimeZone を固定して断つ。
 */
internal object DiscoveryScreenshotFixtures {

    /** 37字。書影ヒーローの題字・TopAppBar の1行省略・結果一覧行の2行 clamp を同時に試す長さ。 */
    const val LONG_TITLE = "追放された万能付与術師は、辺境の廃鉱山で伝説の炉を掘り当てて静かに暮らしたい"

    /** 長ハンドル＋付記。作者行がジャンルタグを幅0まで押し出さないことを見る。 */
    const val LONG_AUTHOR = "藍染めの月見団子＠書籍化作業中"

    /** 小ジャンル最長ラベル「ヒューマンドラマ」。 */
    const val GENRE_CODE_LONGEST = 302

    /** 約210字。あらすじブロックが 2.0 で何行に膨らむか（＝以降の節を押し下げる量）を見る。 */
    const val LONG_STORY =
        "王都の付与術ギルドを追われた青年アルヴィスが流れ着いたのは、地図から消された辺境の廃鉱山だった。" +
            "誰も見向きもしない坑道の奥で、彼は失われた古代文明の炉と、そこに眠る一振りの無銘刀を掘り当てる。" +
            "炉に触れた瞬間よみがえったのは、付与術の常識をひっくり返す「銘を刻む」という技法だった。" +
            "静かに暮らしたいだけの彼のもとへ、やがて王都から追手が、そして鉱山の主を名乗る少女が訪ねてくる。"

    /** 24個。半角スペース区切り＝本番の `keyword.split(Regex("[\\s　]+"))` と同じ分解を通す。 */
    const val MANY_KEYWORDS =
        "R15 残酷な描写あり 異世界転移 主人公最強 チート ざまぁ 追放 スローライフ 鍛冶 錬金術 " +
            "ダンジョン 内政 ほのぼの 男主人公 時間逆行 魔法 剣と魔法 職人 ものづくり 廃鉱山 " +
            "師弟 姉御肌ヒロイン 書籍化 完結済み"

    /** 作品詳細（Content）の代表フィクスチャ。ステータス2×2表の全セルが埋まる値を与える。 */
    fun detail(): WorkDetail = workDetail(
        summary = workSummary(
            title = LONG_TITLE,
            author = LONG_AUTHOR,
            ncode = "N9876AB",
            chapterCount = 412,
            serialState = SerialState.ONGOING,
            // 123.5万字＝「読了目安」セルが "約41時間（123.5万字）" になる最長級の値。
            // 2×2表は IntrinsicSize.Min で組まれており、右上セルの最長値がそのまま列幅の主張になる。
            lengthChars = 1_234_567,
            readMinutes = 2_469,
            genreCode = GENRE_CODE_LONGEST,
            points = workPoints(global = 987_654, weekly = 54_321),
            updatedAt = "2026-07-31 21:04:11",
        ),
        story = LONG_STORY,
        keyword = MANY_KEYWORDS,
        kaiwaritu = 43,
        sasieCnt = 12,
        favNovelCnt = 98_765,
        allHyokaCnt = 4_321,
        generalLastup = "2026-07-31 21:04:11",
    )

    /**
     * 結果一覧の行。1件目だけ長題名＋長作者にして「1行に収まる行」と混在させる
     * ＝行ごとの高さが揃わなくなる（＝区切り線の間隔が崩れる）方向の退行も1枚で見えるようにする。
     */
    fun resultRows(): List<WorkSummary> = listOf(
        workSummary(
            title = LONG_TITLE,
            author = LONG_AUTHOR,
            ncode = "N9876AB",
            chapterCount = 412,
            serialState = SerialState.ONGOING,
            lengthChars = 1_234_567,
            readMinutes = 2_469,
            genreCode = GENRE_CODE_LONGEST,
            points = workPoints(weekly = 54_321),
        ),
        workSummary(
            title = "石畳の街と時計職人",
            author = "七瀬",
            ncode = "N0002AB",
            chapterCount = 88,
            serialState = SerialState.COMPLETED,
            lengthChars = 240_000,
            readMinutes = 480,
            genreCode = 201,
            points = workPoints(weekly = 21_000),
        ),
        workSummary(
            title = "最果ての灯台",
            author = "汐見",
            ncode = "N0003AB",
            serialState = SerialState.SHORT,
            lengthChars = 4_800,
            readMinutes = 10,
            genreCode = 301,
            points = workPoints(weekly = 3_400),
        ),
        workSummary(
            title = "雨天決行の冒険者たち",
            author = "みなも",
            ncode = "N0004AB",
            chapterCount = 25,
            serialState = SerialState.ONGOING,
            lengthChars = 62_000,
            readMinutes = 124,
            genreCode = 307,
            points = workPoints(weekly = 1_200),
        ),
    )
}
