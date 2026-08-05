package com.novelreader

import android.content.Context
import android.content.Intent

/**
 * 背面 PDF 取込の「取込済み（上書き確認待ち）」通知タップ → 上書き確認ダイアログへの直接テレポート
 * （U1 残り・2026-08-06）。従来は openAppIntent（ただ開くだけ）で、深い画面（読書等）・他タブへ帰還すると
 * ダイアログのホスト（本棚ページの BookshelfScreen）が compose されず確認に届かなかった。
 *
 * 設計＝「extras はナビゲーション旗のみ・確認内容は運ばない」:
 * OverwriteRequest 本体は既に Application スコープの Channel → BookshelfViewModel.overwritePrompt が保持し、
 * 本棚ページが表示された時点でダイアログが状態駆動で自動表示される（帰還時確認と同じ機序）。
 * Intent に内容まで載せると正本が2つになり、アプリ内で先に応答済みのダイアログを stale な extras が
 * 再表示する等の不整合を生むため、通知タップは「ダイアログの居る場所へ移動する」だけを担う。
 * 帰還時確認（通知を消した場合の保険）はこの旗が無いだけで従来どおり生きる。
 *
 * MainActivity(送受)と PdfProcessingService(送)の両側をこの2関数へ閉じ、生キーは private＝
 * キー綴りの片側変更で配線が無音で切れる欠陥クラスを表現不能にする（WebImportIntentParser と同じ
 * 「Intent 入口の関心事を1オブジェクトへ」の流儀）。
 */
object OverwriteConfirmTeleport {

    // 生キーは公開しない（両側とも下の2関数経由を強制する）。
    private const val EXTRA_OPEN_OVERWRITE_CONFIRM = "com.novelreader.extra.OPEN_OVERWRITE_CONFIRM"

    /** 通知の contentIntent に包む起動 Intent。SINGLE_TOP|CLEAR_TOP＋manifest launchMode=singleTop で
     *  稼働中は onNewIntent・不在時は cold start の onCreate と、両経路とも [isRequested] が拾う
     *  （openBookIntent＝変換完了 deep link と同じ多重起動回避の型）。 */
    fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_OVERWRITE_CONFIRM, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /** この Intent が上書き確認へのテレポート要求か。無関係な Intent（launcher・変換完了 deep link・
     *  共有取込）では false＝保留中の他の消費流儀（deepLinkBookId 等）を潰さない。 */
    fun isRequested(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_OVERWRITE_CONFIRM, false) == true
}
