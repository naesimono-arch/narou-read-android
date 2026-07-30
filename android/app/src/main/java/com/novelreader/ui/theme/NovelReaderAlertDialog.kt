package com.novelreader.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

/**
 * 本文色だけを是正した [AlertDialog] のラッパ。アプリ内のダイアログはすべてこれを使う。
 *
 * ## 真因（2026-07-31）
 * Material3 は本文色を `AlertDialogDefaults.textContentColor` → `DialogTokens.SupportingTextColor`
 * ＝ **`onSurfaceVariant`** へ配線する（1.3.2 のバイトコードで確認）。ところが本プロジェクトは
 * ADR 0014-D で `onSurfaceVariant` を「**装飾専用スロット**（著者名・キャプション）」へ縮退させ、
 * 意味を運ぶ文字は InfoText 系トークンへ役割分離済み＝装飾スロットは AA(4.5:1) 未達のまま許容する
 * という裁定になっている（`CONTRAST_BASELINE` の `_DECO_ON_SV`）。
 * その結果、**削除確認の本文＝「取り消せません」という不可逆を伝える文字**が装飾スロットで描かれ、
 * ダイアログ面（`surfaceContainerHigh`＝各スキンの素地）の上で
 * D LIGHT `#7C808B` on `#FBFAF8` = **3.79:1**／D SEPIA 3.28:1／P 3.13:1 と AA を割っていた
 * （M3 baseline の紫面だった頃の 3.23:1 より改善しただけで未達は解消していない）。
 * ADR 0027 の初回公開スコープ＝明快K 単独（K は SkinD へ全委譲）なので、出荷する唯一の画面群に乗る実害。
 *
 * ## なぜ呼び出し側で `textContentColor` を渡す運用（案 a）でなくラッパ（案 b）か
 * これは「M3 の既定配線がプロジェクトのトークン規律の外へ漏れる」クラスの欠陥で、
 * 直前に同型を1度踏んでいる（`surfaceContainerHigh` が M3 baseline の紫へ落ちていた件）。
 * その是正も呼び出し側でなく **単一の結節点**（[com.novelreader.ui.theme.skins.withSkinContainerTiers]）で
 * 行った。案 a は「以後すべてのダイアログで `textContentColor` を渡すこと」という**宣言だけのルール**で、
 * ADR 0017 決定5「規約は機械の番人とセット。宣言だけのルールは数週間で崩れる」に真っ向から反する
 * ——15 箇所を直しても 16 箇所目（次に足されるダイアログ）は既定へ落ちる。
 * ラッパなら **新規ダイアログが自動で正しい色になる**（かつ raw `AlertDialog` の流入は
 * `tools/check_design_tokens.py` の移行台帳付き lint が止める）。
 *
 * ## 色の出所（発明しない）
 * [ShelfColors.infoText]＝「意味を運ぶ補助テキスト」の役割トークン（ADR 0014-D 裁定で
 * `onSurfaceVariant` から分離した先例そのもの）。本棚 data class に属するが実体はアプリ共通の
 * InfoText スロットで、発見系メタが既に本棚外で消費している。スキン×テーマが供給する（`SkinTokens.shelf`）
 * ため全スキンで自動追従し、ダイアログ面上の実測は
 * D LIGHT 6.01 / D SEPIA 4.97 / D・K DARK 5.70 / C 5.13 / M 5.81 / P 4.98 / J 6.75:1 ＝全スキン AA 充足。
 *
 * ## 触っていないスロット（既定のまま。実測して合格を確認済み）
 * - 題字 `titleContentColor` = `onSurface`：ダイアログ面上 9.77〜17.44:1（全スキン合格）。
 * - 操作ラベル（`TextButton` の `primary`＝`TextButtonTokens.LabelTextColor`）：P のみ
 *   `#B5564E` on `#DBD6C8` = **3.28:1** で未達。色の決定は意匠の裁定（人間）なのでここでは直さず、
 *   検査に可視化したまま残す（`CONTRAST_BASELINE` の `_P_DIALOG_ACTION` ＝【要裁定】）。
 */
@Composable
fun NovelReaderAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    // ここだけが M3 既定との差分。残りは既定を素通しし、呼び出し側は import 名の差し替えだけで移行できる
    // （引数の並び・既定値を M3 と1:1に保つ＝機械的な置換で意味が変わらないことを保証する）。
    textContentColor: Color = LocalShelfColors.current.infoText,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
