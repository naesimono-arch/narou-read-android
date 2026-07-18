package com.novelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.Spacing

/**
 * 「新着話を通知する」オプトイン・トグル（UX監査 C3・公理13）。
 *
 * 状態は呼び出し側にホイストする（stateless）: enabled の正本は
 * [com.novelreader.NewEpisodeNotificationPreference]、実際のスケジュール切替は
 * [com.novelreader.NovelReaderApplication.setNewEpisodeNotificationEnabled] が担う。
 *
 * 配線メモ（別エージェント担当ファイルへの依頼）: 本アプリには専用のアプリ設定画面が無く、既存の
 * 設定面は読書中の見た目専用シート（ReadingSettingsSheet）のみ。通知トグルは見た目設定と性格が違うため
 * 置き場は要判断（アプリ設定画面の新設が本筋）。ON 切替時は POST_NOTIFICATIONS の priming＋権限要求を
 * 呼び出し側（BookshelfScreen 側の launcher）に噛ませること（権限が無いと通知は静かに出ない）。
 */
@Composable
internal fun NewEpisodeNotificationToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // TalkBack で「ラベル＋スイッチ」を1トラバーサル単位にまとめる。
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Spacing.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 説明テキストは weight で Switch 幅ぶんを差し引いた残り幅に閉じ込める。weight なしだと
        // Row が重みなし子（Column）を全幅で先に測り、後から測る Switch に幅が残らず説明文の
        // 右端に重なる（M/P/J 共通の被り不具合の真因）。end 余白で Switch との間隔を確保。
        Column(Modifier.weight(1f).padding(end = Spacing.S16)) {
            Text(
                text = "新着話を通知する",
                style = MaterialTheme.typography.bodyLarge,
            )
            // 既定 OFF・無音である旨と、代替（本棚バッジ）が既に機能することを添え、
            // OFF が不利益でないと伝える（公理13）。
            Text(
                text = "紐付け作品の更新を1日1回チェックして知らせます（OFFでも本棚に「続き」バッジは出ます）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}
