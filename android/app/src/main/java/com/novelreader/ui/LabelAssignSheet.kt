package com.novelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.data.LabelEntity
import com.novelreader.ui.theme.MinchoFamily

/**
 * U2: 本1冊へのラベル付与シート（⋮メニュー「ラベル」から起動）。
 *
 * NcodeLinkSheet と同じ「VM 状態＋callback を受け取るだけの葉」の構成。ラベルの作成・削除・付与は
 * すべて callback で上位（VM）へ委譲し、シート自身は表示とローカル入力（新規ラベル名）だけを持つ。
 * 本体を [LabelAssignSheetContent] に分離するのは Robolectric で ModalBottomSheet 内の
 * assert/performClick が不安定なため（task_diary #50＝Content 分離でテストする既定パターン）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabelAssignSheet(
    bookTitle: String,
    labels: List<LabelEntity>,
    assignedLabelIds: Set<String>,
    onToggle: (labelId: String, assigned: Boolean) -> Unit,
    onCreateLabel: (name: String) -> Unit,
    onDeleteLabel: (label: LabelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LabelAssignSheetContent(
            bookTitle = bookTitle,
            labels = labels,
            assignedLabelIds = assignedLabelIds,
            onToggle = onToggle,
            onCreateLabel = onCreateLabel,
            onDeleteLabel = onDeleteLabel,
        )
    }
}

@Composable
internal fun LabelAssignSheetContent(
    bookTitle: String,
    labels: List<LabelEntity>,
    assignedLabelIds: Set<String>,
    onToggle: (labelId: String, assigned: Boolean) -> Unit,
    onCreateLabel: (name: String) -> Unit,
    onDeleteLabel: (label: LabelEntity) -> Unit,
) {
    // 新規ラベル名の入力（シートを閉じれば消えてよい画面ローカル状態＝NcodeLinkSheet の inputText と同型）。
    var newLabelName by remember { mutableStateOf("") }
    // ラベル削除の確認対象。付与と違い他の本の紐付けも一括で消える操作のため、一段だけ確認を挟む。
    var labelToDelete by remember { mutableStateOf<LabelEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        // 題字（モック D の静かな見出し様式＝明朝・中肉）
        Text(
            text = "ラベル",
            fontFamily = MinchoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = bookTitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (labels.isEmpty()) {
            Text(
                text = "ラベルはまだありません。下の入力欄から作成できます。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            // ラベル行（チェック＝この本への付与状態）。行数が育っても入力欄が画面外へ
            // 追い出されないよう上限を設けて内部スクロールにする。
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp).padding(top = 8.dp)) {
                items(labels, key = { it.id }) { label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = label.id in assignedLabelIds,
                            onCheckedChange = { checked -> onToggle(label.id, checked) },
                        )
                        Text(
                            text = label.name,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // ラベル自体の削除（この本からの解除ではない）。誤タップに備え確認を挟む。
                        IconButton(onClick = { labelToDelete = label }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "ラベル「${label.name}」を削除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 新規ラベル作成。作成は**この本への付与を伴う**（上位の onCreateLabel 配線が行う）。
        // なぜ: シートは本の⋮/長押しから開く＝作成の動機は「この本に付けたい」だから、作成→手動チェックの
        // 2手に分けない（2026-07-10 ユーザー要望。外したい場合はチェックを外すだけでよい）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            OutlinedTextField(
                value = newLabelName,
                onValueChange = { newLabelName = it },
                placeholder = { Text("新しいラベル名", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onCreateLabel(newLabelName)
                    newLabelName = ""
                },
                enabled = newLabelName.isNotBlank(),
            ) { Text("作成") }
        }
    }

    // ラベル削除の確認ダイアログ（削除は全蔵書からの一括解除を伴うため文言で明示する）。
    labelToDelete?.let { label ->
        AlertDialog(
            onDismissRequest = { labelToDelete = null },
            title = { Text("ラベルの削除") },
            text = { Text("ラベル「${label.name}」を削除しますか？\nすべての本からこのラベルが外れます。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLabel(label)
                    labelToDelete = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { labelToDelete = null }) { Text("キャンセル") }
            },
        )
    }
}
