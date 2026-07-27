// 削除確認ダイアログ共通パーツ。BookshelfScreen.kt の純移動分割（2026-07-27）で切り出した。
// なぜ分離したか: この行は本棚Dだけでなく K/M/P/J 各スキンのダイアログからも呼ばれる全画面共通ヘルパーで、
// 本棚画面の route+描画層とは別の役割だから（BookshelfScreen.kt を本棚画面の責務だけに戻す）。
// 中身は無改変の純移動＝名前・値・文言・ロジックは不変。
package com.novelreader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.novelreader.ui.theme.Spacing

/**
 * 削除確認ダイアログ内の「取込元のPDFファイルも削除する」オプション行（本棚D構造＋M/P/J 全スキン共通）。
 *
 * なぜ共通ヘルパーか: 削除確認ダイアログは意匠上「OS 面（素の Material AlertDialog）」＝スキンで塗り分けない
 * 約束（各スキンのダイアログ実装コメント参照）。このチェック行も同じく全画面で単一実装にし、文言・件数表示・
 * 表示条件を1箇所に集約する。deletableCount==0（＝取込元 URI を持つ本が選択に無い＝消せる取込元PDFが無い）なら
 * 何も描かない。checked/onCheckedChange は呼び出し側の remember 状態へ接続（ダイアログを開くたび既定 OFF）。
 * 行全体を toggleable にして Checkbox とラベルのどちらをタップしても切り替わる（Checkbox 自体は onCheckedChange=null
 * ＝行のトグルへ委譲する Material 標準の a11y マージ形）。
 */
@Composable
internal fun DeleteSourcePdfOption(
    deletableCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (deletableCount <= 0) return
    Spacer(Modifier.height(Spacing.S12))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Spacing.S8))
        Text("取込元のPDFファイルも削除する（${deletableCount}件）")
    }
}
