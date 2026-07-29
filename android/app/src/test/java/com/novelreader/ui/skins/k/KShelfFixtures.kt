package com.novelreader.ui.skins.k

import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.workSummary
import com.novelreader.domain.ReadingStatus
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfChrome
import com.novelreader.ui.skins.ShelfData
import com.novelreader.ui.skins.ShelfSelection
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.viewmodel.ProcessingState

/**
 * 明快K 本棚（[BookshelfK]）の golden 用フィクスチャ。
 *
 * 決定性の条件（golden は同じ入力で必ず同じ絵になること）:
 *  ・時刻は固定定数 [BASE_TIME] から作る（System.currentTimeMillis は使わない）。並び順は
 *    domain の二層 RecencyKey（未読=tier1/addedAt・触った本=tier0/lastReadAt）で決まるため、
 *    時刻がぶれると carded の並びごと golden がぶれる。
 *  ・[mergeShelfItems] は books が「二層キー降順に整列済み」であることを前提に（DAO が保証する契約を
 *    そのまま引き継いで）マージする。ここでも降順に並べた列を渡す＝実アプリと同じ入力形にする。
 *  ・栞書影の先端種/棒長は片方の本で明示指定（永続値あり＝取込済みの本の経路）・他はnull（title 由来の
 *    決定論フォールバック経路）にして、両経路とも golden に載せる。
 */
internal object KShelfFixtures {

    /** 固定の基準時刻（2023-11-14T22:13:20Z 相当）。値そのものは画面に出ない＝並び順の決定にだけ効く。 */
    private const val BASE_TIME = 1_700_000_000_000L

    /** 続きバッジ検証用に蔵書へ紐付ける ncode（新着差分＝30話−24章=6話が「続き6話」として出る）。 */
    private const val LINKED_NCODE = "N1234AB"

    /**
     * 未読の本（進捗レコード無し＝tier1 で最上位）。題名は 2列グリッドのキャプション1行に収まる長さ
     * ＝ellipsis しない側の見本。
     */
    private val unreadBook = BookEntity(
        id = "b_unread",
        title = "夜明けの図書館",
        htmlDirPath = "/nonexistent/b_unread",
        author = "月島 静",
        addedAt = BASE_TIME + 30_000L,
        // 永続抽選済みの栞（取込済みの本の経路）。tipIndex は [0,SHIORI_TIP_COUNT=174)・lenFrac は 0.30..0.60 の範囲内。
        shioriTipIndex = 12,
        shioriLenFrac = 0.45f,
    )

    /**
     * よみかけの本（第7/24話）。長い題名＝キャプション1行 clamp（グリッド）／題字1行 ellipsis（リスト・案A）
     * が効いていることを golden に固定するための見本。ncode 紐付けあり＝リスト行の「続き6話」バッジも出る。
     */
    private val readingBook = BookEntity(
        id = "b_reading",
        title = "転生したら辺境伯の三男だった件について語る長い長い物語",
        htmlDirPath = "/nonexistent/b_reading",
        author = "如月 かなた",
        addedAt = BASE_TIME + 1_000L,
        ncode = LINKED_NCODE,
    )

    /** 読了の本（reachedEnd=true＝状態行が「読了」になる唯一の根拠。進捗率からは導出されない）。 */
    private val finishedBook = BookEntity(
        id = "b_finished",
        title = "硝子の海図",
        htmlDirPath = "/nonexistent/b_finished",
        author = "南 灯",
        addedAt = BASE_TIME + 2_000L,
    )

    /** 未取込の Web由来カード（青磁破線＋field 沈め＋「なろう・未取込」＝K の未取込署名）。 */
    private val webNovel = WebNovelEntity(
        ncode = "N7777XX",
        title = "星降る町の観測日誌",
        writer = "七尾 みなも",
        generalAllNo = 45,
        addedAt = BASE_TIME + 15_000L,
    )

    private val progressMap = mapOf(
        // よみかけ＝chap_7 まで（K の状態行「第7/24話」の素）。lastReadAt>0 で tier0 へ落ちる。
        readingBook.id to ProgressEntity(
            bookId = readingBook.id,
            lastReadFilename = "chap_7.html",
            scrollIndex = 3,
            lastReadAt = BASE_TIME + 20_000L,
        ),
        // 読了＝末尾到達の実績（reachedEnd）でのみ FINISHED になる。
        finishedBook.id to ProgressEntity(
            bookId = finishedBook.id,
            lastReadFilename = "chap_18.html",
            lastReadAt = BASE_TIME + 10_000L,
            reachedEnd = true,
        ),
    )

    /**
     * 蔵書3冊＋Web由来1件の混在棚。
     *
     * なぜこの組合せを「最も回帰価値の高い状態」に選んだか: K のカードは状態ごとに描き分けが違い
     * （読了＝文字「読了」／未読＝藍ドット＋「未読」／よみかけ＝「第N/M話」／Web未取込＝青磁破線＋
     * 紙地沈め＋secondary 文字）、この4種を1枚に同居させると 2列グリッドの列数・書影アスペクト・
     * キャプション1行 clamp・可視⋮の位置まで同時に固定できる。単一状態（例: 空棚だけ）では
     * 出荷画面の大半が無検査のまま残る。
     *
     * 並び（domain の二層キーで決まる決定論的な結果）: 未読(tier1) → よみかけ(tier0/20_000) →
     * Web(tier0/15_000) → 読了(tier0/10_000)。2列グリッドでは1行目＝未読・よみかけ、2行目＝Web・読了。
     */
    fun mixedData(): ShelfData = ShelfData(
        books = listOf(unreadBook, readingBook, finishedBook),
        webNovels = listOf(webNovel),
        // Web は未取込＝読書位置なし（「なろう・未取込」の徴を出す）。
        webReadingProgress = emptyMap(),
        webLastReadAt = emptyMap(),
        progressMap = progressMap,
        chapterCountMap = mapOf(
            unreadBook.id to 12,
            readingBook.id to 24,
            finishedBook.id to 18,
        ),
        // 続き（新着）バッジ＝K のリスト行だけが出す（グリッドのキャプションには出ない）。
        // chapterCount 30 − 章数 24 = 6 話ぶんが「続き6話」。
        newEpisodeNovelMap = mapOf(
            LINKED_NCODE to workSummary(
                title = readingBook.title,
                author = readingBook.author,
                ncode = LINKED_NCODE,
                chapterCount = 30,
                serialState = SerialState.ONGOING,
            ),
        ),
        // 本文欠落（案B バッジ）は別レーンが触っている最中の新機能＝この golden の対象外（空で固定）。
        reimportPlans = emptyMap(),
    )

    /** 空棚（初回起動で最初に見える顔＝CTA2つの空状態）。 */
    fun emptyData(): ShelfData = ShelfData(
        books = emptyList(),
        webNovels = emptyList(),
        webReadingProgress = emptyMap(),
        webLastReadAt = emptyMap(),
        progressMap = emptyMap(),
        chapterCountMap = emptyMap(),
        newEpisodeNovelMap = emptyMap(),
        reimportPlans = emptyMap(),
    )

    /**
     * 額縁（フィルタチップ・バナー類）。[statusCounts] が 0 の分類はチップが淡く不活性になるため、
     * 混在棚では3分類すべて 1 件、空棚では 0 件を渡して「活性チップ／不活性チップ」の両方を撮る。
     */
    fun chrome(statusCounts: Map<ReadingStatus, Int>): ShelfChrome = ShelfChrome(
        // 「すべて」選択＝フィルタ非適用の既定状態（藍塗りピルは先頭チップに出る）。
        selectedStatus = null,
        statusCounts = statusCounts,
        onSelectStatus = {},
        processingState = ProcessingState(),
        // Loading を抜けた確定状態で撮る（Loading 中は空状態を出さない設計＝空棚 golden の前提）。
        isLoading = false,
        // バナー3種（取込中・欠落一括検出・フォルダ走査）は出さない状態で固定。出る側の意匠は
        // それぞれ専用の回帰（ProcessingBannerTest 等）が持ち、ここは本棚本体の版面に集中する。
        sweepBannerVisible = false,
        onSweepLater = {},
        onSweepConfirm = {},
        folderScan = null,
        onScanStop = {},
    )

    /** 混在棚の分類件数（よみかけ/未読/読了 各1件）。 */
    val mixedStatusCounts: Map<ReadingStatus, Int> = mapOf(
        ReadingStatus.READING to 1,
        ReadingStatus.UNREAD to 1,
        ReadingStatus.FINISHED to 1,
    )

    /** 操作束（golden は静止画＝すべて no-op で足りる。束は既定値を持たない契約のため全数明示する）。 */
    val actions = ShelfActions(
        onOpenBook = {},
        onFabClick = {},
        onOpenDiscovery = {},
        onOpenWardrobe = {},
        onCancelProcessing = {},
    )

    /** 選択モードは非選択で固定（選択中の下端バー・チェックマークは意匠が別＝必要になったら別 case を足す）。 */
    val selection = ShelfSelection(
        selectionMode = false,
        selectedIds = emptyList(),
        onToggleSelect = {},
        onEnterSelection = {},
        onExitSelection = {},
        onSelectAll = {},
        onDeleteBooks = { _, _ -> },
    )

    val webActions = ShelfWebActions(
        onOpenWebNovel = {},
        onResumeWebNovel = { _, _ -> },
        onImportWebNovel = {},
        onRemoveWebNovel = {},
    )
}
