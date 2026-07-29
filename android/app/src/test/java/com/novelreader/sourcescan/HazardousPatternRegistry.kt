package com.novelreader.sourcescan

// ============================================================
// 禁忌パターン走査（L2）の登録簿・2026-07-30。
//
// docs/known-bugs-registry.md が「検知手段なし」と判定したバグ型のうち、**ソース走査で機械列挙できる型**の
// 許容例外を集めたファイル。走査エンジンは [KotlinSourceScanner] が共通で持ち、**何を危険とみなすか**は
// HazardousPatternScanTest の述語が、**なぜ今そうなっているのか**はこの登録簿が持つ（発見ホームの
// DiscoveryHomeRegistry と同じ分業）。
//
// 設計の非対称さ（そのまま踏襲）:
//   偽陰性（罠を踏み得る形の取りこぼし）は誰も気づけないまま出荷される＝最悪。
//   偽陽性（掛かりすぎ）は**理由付きで**ここへ1行足せば解消できる＝安い。
//   よって述語は広めに取り、掛かった分をここで説明する。**理由なしの登録は禁止**（黙って通すのを防ぐ）。
//
// 「登録して黙らせる」のが正しい場面と間違っている場面の区別:
//   正しい＝意図してそうしている（例: 本文に suspension point が無い runCatching）。
//   間違い＝症状を隠したいだけ。その場合は真因を直すこと（2026-07-30 の初回導入では
//   ScrapeHttpClient の callTimeout 欠落と BookshelfViewModel の素の Dispatchers.IO を実際に直した）。
// ============================================================

internal object HazardousPatternRegistry {

    /**
     * 【型1】`runCatching` が `CancellationException` まで飲む（`known-bugs-registry` の
     * `runcatching-swallows-cancellation`）。
     *
     * 走査が「キャンセル文脈」とみなした runCatching のうち、**再送出していないのに許してよい**もの。
     * 許容の唯一の正当理由は「ブロック本文に suspension point が無い＝そこから CancellationException は
     * 生じ得ない」こと。ブロックが suspend 呼び出しを含むなら登録ではなく再送出で直すこと。
     */
    val cancellationSafeRunCatching: Map<String, String> = mapOf(
        "MainActivity.kt#NovelReaderApp::activityContext.startActivity(Intent(Intent.ACTI" to
            "本文は startActivity 1つ＝非 suspend。ブラウザ不在の ActivityNotFoundException だけを無害化する意図的な握り",
        "ui/BookshelfScreen.kt#BookshelfScreen::context.startActivity(Intent(Intent.ACTION_VIEW," to
            "同上（スナックバー『公式サイトで読む』の外部遷移）。本文は非 suspend",
        "ui/ChapterScreen.kt#ChapterScreen::ChapterHtmlParser.parse(File(htmlDirPath, prevFi" to
            "本文はブロッキングな HTML パース＝非 suspend。失敗は前章の覗き無しへの縮退（エラー表示は本遷移側が正本）",
        "ui/ChapterScreen.kt#ChapterScreen::ChapterHtmlParser.parse(File(htmlDirPath, nextFi" to
            "同上（次章の先読み）",
        "ui/discovery/PdfImportScreen.kt#PdfImportScreen::CookieManager.getInstance().getCookie(downloadUr" to
            "本文は WebView の Cookie 取得1つ＝非 suspend。Cookie 不在は null へ縮退させる意図的な握り",
        "repository/LibraryDeleter.kt#deleteBook::DocumentsContract.deleteDocument(context.content" to
            "本文は SAF の deleteDocument＝非 suspend のバインダ呼び出し。失敗要因（権限失効・削除非対応）を結末値へ畳む意図的な握り",
        "viewmodel/PdfImportViewModel.kt#onDownloadRequested::if (outFile.exists()) outFile.delete()" to
            "本文は File.delete のみ＝非 suspend。キャンセル時の部分DL掃除そのものなので、ここで再送出すると掃除が飛ぶ",
    )

    /**
     * 【型2】本番コードの `launch(Dispatchers.IO)` 等の直書き（`test-dispatcher-escape-flaky`）。
     *
     * 走査に掛かったうち、**注入へ移さなくてよい**もの。許容の正当理由は
     * 「その起動を `advanceUntilIdle` で待つ JVM 単体テストが存在し得ない」こと。
     * ViewModel のように TestDispatcher 下で検証される層は登録ではなく注入で直すこと。
     */
    val allowedHardcodedDispatchers: Map<String, String> = mapOf(
        "NovelReaderApplication.kt#applicationScope::CoroutineScope(SupervisorJob() + Dispatchers.IO)" to
            "アプリ全体スコープ。JVM 単体テストは Application を生成せず（VM テストは mockk の偽 Application を渡す）、この起動を待つテストが存在しない",
        "PdfProcessingService.kt#scope::CoroutineScope(Dispatchers.IO + SupervisorJob())" to
            "FGS の処理ループ。Service は JVM 単体テスト対象外（実機/androidTest でのみ動く）ため TestDispatcher の管理下に置く意味が無い",
        "PdfProcessingService.kt#onTimeout::CoroutineScope(Dispatchers.IO + SupervisorJob())" to
            "同上（タイムアウト後のスコープ再生成。cancelled-scope-reuse-silent-stop の修正箇所）",
        "ui/skins/m/HighLoadSkyM.kt#ensureDepth::launch(Dispatchers.Default)" to
            "星図チャンクのビットマップ焼き込み。描画の先読みで UI スレッドから外すのが目的、かつ完了を待つ JVM テストが無い（Roborazzi は焼き込み前の状態を撮る）",
    )

    /**
     * 【型3】`OkHttpClient` に `callTimeout` が無い（`no-network-timeout`）。
     *
     * 許容の正当理由は「全体時間に上限を設けないことが仕様として正しい」こと。
     * それ以外（単に付け忘れ）は登録ではなく `callTimeout` 追加で直すこと。
     */
    val allowedMissingCallTimeout: Map<String, String> = mapOf(
        "viewmodel/PdfImportViewModel.kt#onDownloadRequested::OkHttpClient.Builder() .connectTimeout(30, TimeU" to
            "なろう縦書き PDF の DL は正当でも長時間になりうる。全体上限で殺さず connectTimeout/readTimeout で" +
                "『無進捗の停滞』だけを切る設計（本番コード側に同趣旨の why コメントあり）",
    )

    /**
     * 【型4】FGS 通知と終端通知の ID 衝突（`fgs-notification-id-collision`）。
     *
     * 通知の投稿口は少数なので許容リストではなく**全数登録**にする（新しい通知を足したら
     * ここへ役割を宣言するまで赤で止まる）。走査は宣言と実コードのズレも突合する。
     */
    enum class NotificationRole {
        /** `startForeground` で FGS 通知そのものを立てる／更新する口。 */
        FGS_ONGOING,

        /** 完了・失敗・重複など「サービスが止まった後も残るべき」終端通知。FGS の ID を使ってはならない。 */
        TERMINAL,

        /** FGS と無関係な通知（Worker 等）。 */
        INDEPENDENT,
    }

    /**
     * @param idToken 実コードが ID として渡している式（走査結果と一致しなければ落とす＝宣言の陳腐化検知）。
     * @param role この投稿口の役割。
     * @param why なぜこの役割・この ID なのか。
     */
    data class NotificationSite(
        val idToken: String,
        val role: NotificationRole,
        val why: String,
    )

    val notificationSites: Map<String, NotificationSite> = mapOf(
        "PdfProcessingService.kt#onStartCommand::startForeground" to NotificationSite(
            idToken = "NOTIFICATION_ID",
            role = NotificationRole.FGS_ONGOING,
            why = "FGS 通知そのもの。この ID がサービスの生存に縛られる＝終端通知が使ってはいけない ID の定義点",
        ),
        "PdfProcessingService.kt#updateProgressNotification::notify" to NotificationSite(
            idToken = "NOTIFICATION_ID",
            role = NotificationRole.FGS_ONGOING,
            why = "進行中通知の更新＝FGS 通知の張り替えなので同一 ID が正しい（別 ID にすると通知が2枚に増える）",
        ),
        "PdfProcessingService.kt#showCompletionNotification::notify" to NotificationSite(
            idToken = "COMPLETION_NOTIFICATION_ID",
            role = NotificationRole.TERMINAL,
            why = "変換完了。FGS と同一 ID で投稿するとサービス停止の道連れで出た瞬間に消える（本バグ型の実例）",
        ),
        "PdfProcessingService.kt#showDuplicateNotification::notify" to NotificationSite(
            idToken = "DUPLICATE_NOTIFICATION_ID",
            role = NotificationRole.TERMINAL,
            why = "二重取込の通知。進行中の ongoing 通知を潰さないためにも別 ID が要る",
        ),
        "PdfProcessingService.kt#showErrorNotification::notify" to NotificationSite(
            idToken = "ERROR_NOTIFICATION_ID",
            role = NotificationRole.TERMINAL,
            why = "変換失敗の通知。完了通知と同じ理由で別 ID",
        ),
        "NewEpisodeCheckWorker.kt#showNotification::notify" to NotificationSite(
            idToken = "NEW_EPISODE_NOTIFICATION_ID",
            role = NotificationRole.INDEPENDENT,
            why = "新着チェック Worker の通知。FGS とは無関係な経路（ID 空間だけ衝突しなければよい）",
        ),
    )
}
