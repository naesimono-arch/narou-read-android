// スクレイパー健全性の debug ヘルスボード。BookshelfScreen.kt の純移動分割（2026-07-27）で切り出した。
// なぜ分離したか: 唯一の呼出元が設定タブ（SettingsScreenK）で、本棚画面の route+描画層とは無関係の部品だから。
// 旧 AdapterHealthMenuSection（⋮メニューの診断入口）は撤去済み: 本棚⋮の撤去（系2・2026-07-24）で呼出元が
// ゼロになった dead code のため（診断入口は SettingsScreenK が単一正本）。下の本体は現役＝巻き添え削除しないこと。
// 中身は無改変の純移動＝名前・値・文言・ロジックは不変。
package com.novelreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novelreader.BuildConfig
import com.novelreader.scrape.AdapterHealthCheck
import com.novelreader.scrape.SiteAdapterRegistry
import com.novelreader.ui.theme.Spacing

/**
 * 破損監視・層3 の debug ヘルスボード。開いた瞬間に全登録アダプタの自己診断（[AdapterHealthCheck]）を
 * 実ネットワークで1回だけ回し、アダプタ名＋緑/赤（OK/NG）＋理由テキストを一覧表示する。結果は永続化しない
 * （その場診断・P4 スコープ）。緑=colorScheme.primary・赤=colorScheme.error の意味色で表す（新規トークンは足さない）。
 *
 * defensive に [BuildConfig.DEBUG] を再ガードする（入口も debug 限定だが二重で release 到達を断つ）。
 */
@Composable
internal fun AdapterHealthBoardDialog(onDismiss: () -> Unit) {
    if (!BuildConfig.DEBUG) return
    // null=診断中／非 null=結果。LaunchedEffect(Unit) で開いた1回だけ実行する（再コンポーズで再取得しない）。
    var reports by remember { mutableStateOf<List<AdapterHealthCheck.Report>?>(null) }
    LaunchedEffect(Unit) {
        // registry の既定アダプタ（本番の KakuyomuAdapter＝実 OkHttp）をそのまま診断する。取得は各アダプタ内蔵の
        // ScrapeHttpClient 経由で Crawl-delay を守るため、章1件でも数秒かかる（debug の手動診断ゆえ許容）。
        reports = AdapterHealthCheck(SiteAdapterRegistry().registeredAdapters).runAll()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        title = { Text("スクレイパー健全性") },
        text = {
            val current = reports
            if (current == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(Spacing.S24))
                    Spacer(Modifier.width(Spacing.S16))
                    Text("診断中…（Crawl-delay を守るため数秒かかります）")
                }
            } else {
                Column {
                    current.forEach { r ->
                        Column(Modifier.padding(vertical = Spacing.S4)) {
                            Text(
                                text = "${if (r.healthy) "OK" else "NG"}  ${r.displayName}（${r.siteKey}）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (r.healthy) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = r.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
