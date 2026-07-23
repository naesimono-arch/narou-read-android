package com.novelreader.ui.skins.k

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.Spacing

/**
 * 明快K: 恒常ボトムナビ（モック正本＝skins/bookshelf-K.html ほか3枚の共通 tabbar）。
 *
 * なぜ恒常ナビか: 第三者テスト「一目でどの画面か分からない」の真因を〈現在地・目的地一覧が常時見えない
 * 構造〉と裁定したため（plan default-ui-clarity-K・UX/15「同格3〜5目的地＋現在地常時表示」）。
 * 競合4機（カクヨム/なろう公式/ナローリーダー2種）すべてがこの型＝ユーザーの既習慣に乗る。
 * 読書・目次など深い画面には出さない（没入優先＝モック正本どおり）。
 */
enum class KTab(val label: String, val icon: ImageVector) {
    BOOKSHELF("本棚", Icons.Outlined.MenuBook),
    DISCOVER("さがす", Icons.Outlined.Search),
    SETTINGS("設定", Icons.Outlined.Settings),
}

@Composable
fun KBottomNav(
    current: KTab,
    onSelect: (KTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            // モックの上罫ヘアライン＝面の切れ目を1pxで示す（影を使わない静かな区切り・D系の流儀）。
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .selectableGroup(),
            ) {
                KTab.entries.forEach { tab ->
                    val selected = tab == current
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(tab) },
                            )
                            .padding(top = Spacing.S8),
                    ) {
                        // 選択中はアイコン背後に藍10%の横長ピル（モック .pill 56x32/r16）＝現在地の面表示。
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 56.dp, height = 32.dp)
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                    },
                                    shape = MaterialTheme.shapes.large,
                                ),
                        ) {
                            Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        }
                        // ラベル常時表示（選択中のみ太字）＝アイコン語彙に依存しない自己説明（自明性A0）。
                        Text(
                            tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
                            modifier = Modifier.padding(top = Spacing.S4),
                        )
                    }
                }
            }
        }
    }
}
