package com.novelreader.ui.screenshot

/**
 * 本棚ルート層のダイアログ群（[com.novelreader.ui.BookshelfScreen]）の文言の写し。
 *
 * なぜ写しが要るか: これらのダイアログは `BookshelfScreen` の中に**インラインのラムダ**として書かれており
 * （`title = { Text("…") }` / `text = { … }`）、テストから呼べる Composable にも定数にもなっていない。
 * ホストごと撮るには実 ViewModel（DB・Service・権限・SAF ランチャー）を立ち上げたうえで UI 操作で
 * 各ダイアログを開かせる必要があり、撮りたい版面と無関係な配線でテストが折れる。
 * よって撮影は「同じ入口（`NovelReaderAlertDialog`）へ同じ中身を組む」形を採る
 * （`ModalBottomSheet` に対する [ReadingSettingsSheetScreenshotTest] と同じ前例）。
 *
 * ⚠️ 写しである以上、本番の文言が変わってもこの定数は黙って古いままになり得る＝
 * 「golden で守っているつもり」が静かに嘘になる。それを防ぐのが [SOURCE_FRAGMENTS] と
 * [BookshelfDialogTextFidelityTest] で、**本番ソースに実在しない写しはテストが赤にする**。
 * 文言を変えたら (1) ここの定数、(2) 必要なら [SOURCE_FRAGMENTS]、(3) golden の撮り直し、の3点セット。
 *
 * 撮影対象の選定は [BookshelfDialogScreenshotTest] の KDoc を参照。
 */
internal object BookshelfDialogFixtures {

    // ── ①バッテリー最適化案内（BookshelfScreen.kt の showBatteryOptDialog）──────────────────
    // 器は「本文＋チェックボックス行」＝本文とチェック行が同じ text スロットを分け合う唯一の形。
    // 本文は 2026-08-07 の棚卸し裁定で 124字→78字（改行込み）へ短縮済み。これで**最長は
    // [NOTIF_BODY]（80字）へ移った**が、代表 case を battery のままにしてあるのは、
    // 本 case が守っているのが長さではなく上記の器の形（溢れ→チェック行と重なる経路）だから。
    const val BATTERY_TITLE = "バックグラウンド処理について"
    const val BATTERY_BODY =
        "取り込みが途中で止まらないよう、電池の最適化から除外してください。\n" +
            "設定 → バッテリー → アプリごとの消費管理 → バックグラウンドアクティビティを許可"
    const val BATTERY_CHECK = "二度と表示しない"
    const val BATTERY_CONFIRM = "設定を開く"
    const val BATTERY_DISMISS = "閉じる"

    // ── ②通知権限 priming（showNotifPriming）────────────────────────────────────────────
    // 単一 Text の本文としては最長（80字。①の短縮後はユーザー可視文言そのものの最長でもある）。
    // 器は「題字＋本文＋2ボタン」の最も素朴な形。
    const val NOTIF_TITLE = "変換の進捗を通知でお知らせできます"
    const val NOTIF_BODY =
        "PDFの変換には時間がかかることがあります。通知を許可すると、他のアプリを使っている間も" +
            "進捗と完了をお知らせします。通知はあとで設定からいつでもオフにできます。"
    const val NOTIF_CONFIRM = "通知を許可"
    const val NOTIF_DISMISS = "今はしない"

    // ── ③なろう形式でないPDFの確認（importPrompt）────────────────────────────────────────
    // 器の特徴は**ボタン3個**（dismissButton が Row で2個を抱える）＋確定ラベルが動的で長い。
    // 件数は golden を安定させるため固定値で撮る（12件中5件が非なろう＝なろう7件）。
    const val IMPORT_TITLE = "なろう形式でないPDFがあります"
    const val IMPORT_BODY_TAIL =
        "件は、なろうの縦書きPDF（ファイル名が「N＋数字＋英字」の形式）ではありません。" +
            "うまく変換できない場合があります。"
    const val IMPORT_BODY = "選択した 12 件のうち 5 $IMPORT_BODY_TAIL"
    const val IMPORT_CONFIRM = "なろう形式のみ (7件)"
    const val IMPORT_ALL = "すべて (12件)"
    const val IMPORT_CANCEL = "キャンセル"

    // ── ④本文欠落→フォルダ走査で再取込（reimportTarget / PickPdf 系・指紋あり・場所未記憶）────────
    // 器の特徴は「本文＋補助行（bodySmall の取込元ヒント）」＋ボタン3個。ヒントはファイル名なので長い。
    const val REIMPORT_TITLE = "PDFのある場所から探しますか？"
    const val REIMPORT_BODY =
        "フォルダを教えていただければ、中身を照合して自動で見つけます。" +
            "読書位置としおりは残ります。"
    const val REIMPORT_HINT_PREFIX = "取込元の PDF: "
    const val REIMPORT_HINT =
        REIMPORT_HINT_PREFIX + "悪役令嬢に転生したはずが気づけば魔王の義妹になっていました.pdf"
    const val REIMPORT_CONFIRM = "場所から探す"
    const val REIMPORT_PICK = "自分で選ぶ"
    const val REIMPORT_DISMISS = "やめる"

    // ── ⑤取込元PDF削除の失敗通知（BookshelfViewModel.deleteBooks → emitError）──────────────
    // ダイアログではなく Snackbar だが、長文が「1行の器」に入る点で同じ危険を持つ。
    // actionLabel が付く理由＝transient=false の既定経路（BookshelfScreen の errorEvents 収集）。
    // ⚠ 文言は ADR 0043 の実装便で推測形→断定形へ是正した（機序は VM 側のコメント）。
    // 版面が変わるため golden（ShelfSnackbarK の deleteFail 系）は撮り直しが要る。
    const val SNACKBAR_DELETE_FAIL_TAIL =
        "件・ファイルが移動/削除済みか、この保存先が削除に対応していません）"
    const val SNACKBAR_DELETE_FAIL = "取込元PDFを削除できませんでした（3$SNACKBAR_DELETE_FAIL_TAIL"
    const val SNACKBAR_ACTION = "閉じる"

    /**
     * 本番ソースに **verbatim で実在すべき素片** → その素片が在るファイル（走査根からの相対パス）。
     *
     * `${…}` テンプレートで割れる文言は静的部だけを並べる（件数や書名は撮影用の固定値なので照合しない）。
     * 照合の正規化は [BookshelfDialogTextFidelityTest] 側が持つ（`"…" + "…"` の連結畳み込みと `\n` の再エスケープ）。
     */
    val SOURCE_FRAGMENTS: Map<String, String> = buildMap {
        val shelf = "ui/BookshelfScreen.kt"
        val vm = "viewmodel/BookshelfViewModel.kt"
        put(BATTERY_TITLE, shelf)
        put(BATTERY_BODY, shelf)
        put(BATTERY_CHECK, shelf)
        put(BATTERY_CONFIRM, shelf)
        put(NOTIF_TITLE, shelf)
        put(NOTIF_BODY, shelf)
        put(NOTIF_CONFIRM, shelf)
        put(NOTIF_DISMISS, shelf)
        put(IMPORT_TITLE, shelf)
        put(IMPORT_BODY_TAIL, shelf)
        put(IMPORT_CANCEL, shelf)
        put(REIMPORT_TITLE, shelf)
        put(REIMPORT_BODY, shelf)
        put(REIMPORT_HINT_PREFIX, shelf)
        put(REIMPORT_CONFIRM, shelf)
        put(REIMPORT_PICK, shelf)
        put(REIMPORT_DISMISS, shelf)
        put(SNACKBAR_DELETE_FAIL_TAIL, vm)
    }
}
