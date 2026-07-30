package com.novelreader.discovery.model

/**
 * UI/viewmodel テスト用の [WorkSummary]/[WorkPoints]/[WorkDetail] ビルダ（P5 第2段の脱なろう切替に伴う共通ヘルパ）。
 * 既定値はテストが関心を持たないフィールドを埋めるだけ（title/author は非 null 必須なので既定を与える）。
 * narou/ 配下のデータ層テストは DTO のままなので、このヘルパは discovery 層の消費側テスト専用。
 */
fun workSummary(
    title: String = "t",
    author: String = "w",
    sourceSite: String = "narou",
    workUrl: String? = null,
    ncode: String? = null,
    chapterCount: Int? = null,
    serialState: SerialState? = null,
    lengthChars: Int? = null,
    readMinutes: Int? = null,
    genreCode: Int? = null,
    points: WorkPoints? = null,
    updatedAt: String? = null,
): WorkSummary = WorkSummary(
    title = title,
    author = author,
    sourceSite = sourceSite,
    workUrl = workUrl,
    ncode = ncode,
    chapterCount = chapterCount,
    serialState = serialState,
    lengthChars = lengthChars,
    readMinutes = readMinutes,
    genreCode = genreCode,
    points = points,
    updatedAt = updatedAt,
)

fun workPoints(
    global: Int? = null,
    daily: Int? = null,
    weekly: Int? = null,
    monthly: Int? = null,
    quarter: Int? = null,
): WorkPoints = WorkPoints(global = global, daily = daily, weekly = weekly, monthly = monthly, quarter = quarter)

fun workDetail(
    summary: WorkSummary = workSummary(),
    story: String? = null,
    keyword: String? = null,
    kaiwaritu: Int? = null,
    sasieCnt: Int? = null,
    favNovelCnt: Int? = null,
    allHyokaCnt: Int? = null,
    generalLastup: String? = null,
): WorkDetail = WorkDetail(
    summary = summary,
    story = story,
    keyword = keyword,
    kaiwaritu = kaiwaritu,
    sasieCnt = sasieCnt,
    favNovelCnt = favNovelCnt,
    allHyokaCnt = allHyokaCnt,
    generalLastup = generalLastup,
)
