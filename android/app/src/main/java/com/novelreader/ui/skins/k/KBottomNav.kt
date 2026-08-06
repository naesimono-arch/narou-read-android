package com.novelreader.ui.skins.k

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
                    // モックの帯高64px（.tabbar height:64px）は fontScale 1.0 の寸法＝下限（min）として保つ。
                    // 固定 height にしないのは、2.0 ではラベル(11sp→22sp相当)がピル32dp+上余白の残り≒20dpに
                    // 収まらず尻切れになる破綻が golden（KBottomNav_bookshelf_*_2.0）に焼かれていたため
                    // （監査 2026-08-06 根因④）。min なら拡大時だけ内容高へ追従して帯が伸びる。
                    // 1.0 で帯が 64dp のままである根拠は下の Text の lineHeight 明示（内容自然高 60dp < 64dp）。
                    .heightIn(min = 64.dp)
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
                        // なぜ lineHeight を明示するか（帯高の真因）: 未指定だと MaterialTheme が既定として流す
                        // bodyLarge の行送り 28sp（16sp 本文用の値）を 11sp のラベルが相続し、1タブの内容自然高が
                        // 8(上余白)+32(ピル)+4(ラベル上余白)+28(行ボックス)=72dp とモックの帯 64dp を 8dp 超える。
                        // 旧 height(64.dp) 固定はこの超過をクリップして隠していたため、heightIn(min) へ変えた
                        // 途端に 1.0 でも帯が 72dp へ伸びた（golden 720x130→720x146 の真因＝heightIn 自体ではない）。
                        // 16sp はモック .lb（font-size:11px・line-height 指定なし＝normal 相当）の行送りであり
                        // M3 labelSmall（11sp/16sp）とも一致する。これで自然高は 8+32+4+16=60dp となり、
                        // 1.0 は heightIn(min=64) が効いてモックどおり帯 64dp（golden 720x130）、2.0 は
                        // 8+32+4+32=76dp 相当（実測 帯76.5dp・ヘアライン込み golden 720x155）へ伸びて収まる。
                        Text(
                            tab.label,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
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
