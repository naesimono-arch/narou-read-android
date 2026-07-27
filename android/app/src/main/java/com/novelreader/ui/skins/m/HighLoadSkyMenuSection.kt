// 星図M系スキンの⋮メニュー「開発」節。BookshelfScreen.kt の純移動分割（2026-07-27）で切り出した。
// なぜ skins.m へ置くか: 呼出元が BookshelfSkyM / BookshelfLogM の2つだけで、露出条件も星図M装着時に限られる
// ＝M スキン専用部品であり、本棚画面の route+描画層（BookshelfScreen.kt）とは役割が違うから。
// 中身は無改変の純移動＝名前・値・文言・ロジックは不変。
package com.novelreader.ui.skins.m

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.novelreader.BuildConfig
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing

/**
 * ⋮メニューの「高負荷スカイ（試作）」節（ADR 0023）＝debug ビルドかつ星図M装着時のみ露出する開発トグル。
 *
 * なぜ⋮メニュー（テーマ・通知の並ぶ設定面）へ置くか: 本アプリ唯一の常設設定面で、専用設定画面を新設しない
 * （UX/19: 設定面は増やさない）。試作の実機探索は debug 限定＝release では [BuildConfig.DEBUG]=false で節ごと消え、
 * 値も MainActivity 側で常に false 扱い（出荷時は現行の空）。星図M以外では無意味ゆえ [LocalSkin] で M に絞る。
 *
 * 状態は呼び出し側（MainActivity へ巻き上げた highLoadSkyM ＋prefs "sky_high_load_m"）にホイスト＝ON/OFF が即
 * 常駐 backdrop（SkyBackdropM の highLoad 引数）へ伝わり、その場で空が切り替わる（通知トグルと同じ stateless 流儀）。
 */
@Composable
internal fun HighLoadSkyMenuSection(
    highLoad: Boolean,
    onHighLoadChange: (Boolean) -> Unit,
    onDismissMenu: () -> Unit = {}, // 検分トリガーは押下時にメニューを閉じてから発火（DropdownMenu が空を隠すのを避ける）
) {
    if (!BuildConfig.DEBUG || LocalSkin.current != Skin.SEIZU_M) return
    HorizontalDivider()
    Text(
        "開発",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16)
            // TalkBack で「ラベル＋スイッチ」を1トラバーサル単位にまとめる（通知トグルと同流儀）。
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Spacing.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.S16)) {
            Text(text = "高負荷スカイ（試作）", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "多深度視差・加算合成の重い空（debug のみ）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = highLoad, onCheckedChange = onHighLoadChange)
    }
    // 高負荷 ON のときだけ「即時トリガー」行を出す（確率60s/3分・周期42日・33chに1度を待たずに実機検分するため）。
    // 各ボタンは onDismissMenu() でメニューを閉じてから HlSkyDebug を叩く＝発火が空で見える。彗星はトグル（押すたび表示/非表示）。
    if (highLoad) {
        Text(
            "即時トリガー（検分用）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S4, bottom = Spacing.S4),
        )
        // 各ラベルと発火をペアで持ち、RowScope 内で weight を効かせるため直接展開（weight は RowScope 受信者に紐付く）。
        val triggers = listOf<Pair<String, () -> Unit>>(
            "流星" to { HlSkyDebug.fireMeteor() },
            "衛星" to { HlSkyDebug.fireSatellite() },
            "彗星" to { HlSkyDebug.toggleComet() }, // トグル: 押すたび強制表示/非表示
            "BH" to { HlSkyDebug.fireBh() },
            "暗黒雲" to { HlSkyDebug.fireCloud() }, // 1発: 可視中央へ雲を湧かせ視差2.2×で流れ去らせる（流星等と同流儀）
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S8, vertical = Spacing.S4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
        ) {
            for ((label, onFire) in triggers) {
                TextButton(
                    onClick = { onDismissMenu(); onFire() }, // 先に閉じてから発火＝メニューが空を隠さない
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.S4, vertical = Spacing.S4),
                ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}
