package com.novelreader.ui.skins.k

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.novelreader.BuildConfig
import com.novelreader.NewEpisodeNotificationPreference
import com.novelreader.NovelReaderApplication
import com.novelreader.ui.AdapterHealthBoardDialog
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.tokens

/**
 * 設定画面（モック正本＝skins/settings-{D,M,P,J,K}.html の共通節構成）。
 *
 * なぜ新設か: 現行アプリは独立した設定画面を持たず、テーマ・通知・きせかえが本棚⋮と読書シートに
 * 分散していた＝「どこに何があるか」を記憶で補わせる構造（第三者テストの分かりにくさの一因）。
 * K で恒常ナビの3目的地の一つとして設定を昇格し、〈見出し＋説明文つきの行〉で全項目を自己説明させた。
 *
 * 2026-07-23 に恒常ナビを全スキンへ伝播したのに伴い本画面も全スキン共用へ拡張。意匠は MaterialTheme の
 * colorScheme/typography（NovelReaderTheme がスキンごとに供給）へ追従して自然に染まる＝per-skin の深化
 * （P の液晶スウォッチ等）は次ラウンド。名称の K は歴史的経緯（初出が明快K）でそのまま残す。
 * 各スキンの⋮メニュー内の設定相当は当面残置（重複解消は第2波・別班所有）。
 */
@Composable
fun SettingsScreenK(
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    currentSkin: Skin,
    onOpenWardrobe: () -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHealthBoard by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // タイトルがステータスバー裏に潜らないよう system bar 分を確保（実機検分 2026-07-23 で欠落発覚）。
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.S16),
    ) {
        // 画面名＝タブと同語彙「設定」（You Are Here の二重化。モック h1 22px bold）。
        Text(
            "設定",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Spacing.S16, bottom = Spacing.S8),
        )

        // 単一変種スキン（星図M・夜行C＝supportedThemes=[DARK]）はテーマ節を「現在の相の固定表示」に畳む。
        // なぜ: これらの装いは相を1つしか持たず NovelReaderTheme が supportedThemes.first() へクランプする＝
        // 3択ダイアログを出しても選んで変わらない嘘のUIになるため（モック settings-M.html の注記どおり）。
        // per-skin の文言（「星図 ・ 夜の相」等の装い名つき表現）は次ラウンドの深化＝ここは汎用文で割り切る。
        val supportedThemes = currentSkin.tokens.supportedThemes
        KSettingsGroupLabel("表示")
        KSettingsCard {
            if (supportedThemes.size <= 1) {
                KSettingsRow(
                    icon = Icons.Outlined.Contrast,
                    title = "テーマ",
                    description = "この装いはひとつの相のみです。ほかの装いは「きせかえ」から選べます",
                    trailing = {
                        Text(
                            supportedThemes.firstOrNull()?.displayNameK() ?: appTheme.displayNameK(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = null,
                )
            } else {
                KSettingsRow(
                    icon = Icons.Outlined.Contrast,
                    title = "テーマ",
                    description = null,
                    trailing = {
                        Text(
                            if (followingSystem) "システムに従う" else appTheme.displayNameK(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { showThemeDialog = true },
                )
            }
            KSettingsRow(
                icon = Icons.Outlined.Checkroom,
                title = "きせかえ",
                description = "本棚や画面の装いを変える（現在: ${currentSkin.displayName}）",
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onOpenWardrobe,
            )
            // 文字サイズ等は読書中の表示設定が正本＝ここからは変えられない事実をそのまま案内する
            //（行を隠すと「文字設定はどこ？」の疑問符が残る。場所を教える行として置く＝自明性A0）。
            KSettingsRow(
                icon = null,
                title = "文字と組版",
                description = "文字サイズ・行間・余白は、読書中の「表示設定」から変えられます",
                trailing = {},
                onClick = null,
            )
        }

        KSettingsGroupLabel("通知")
        KSettingsCard {
            NewEpisodeNotificationRowK()
        }

        // 開発版のみの診断面（release では節ごと消える＝既存ヘルスボードの露出規約と同じ）。
        if (BuildConfig.DEBUG) {
            KSettingsGroupLabel("データ")
            KSettingsCard {
                KSettingsRow(
                    icon = Icons.Outlined.MonitorHeart,
                    title = "取り込み状態の診断",
                    description = "Web取込の健全性を確認（開発版のみ）",
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { showHealthBoard = true },
                )
            }
        }

        KSettingsGroupLabel("このアプリ")
        KSettingsCard {
            KSettingsRow(
                icon = Icons.Outlined.Info,
                title = "バージョン",
                description = null,
                trailing = {
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = null,
            )
        }
    }

    if (showThemeDialog) {
        KThemeDialog(
            appTheme = appTheme,
            followingSystem = followingSystem,
            onThemeChange = onThemeChange,
            onFollowSystem = onFollowSystem,
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showHealthBoard) {
        AdapterHealthBoardDialog(onDismiss = { showHealthBoard = false })
    }
}

/** テーマ4択（システムに従う＋ライト/セピア/ダーク）。本棚⋮の4択と同じ状態源を素通しした radio ダイアログ。 */
@Composable
private fun KThemeDialog(
    appTheme: ReadingTheme,
    followingSystem: Boolean,
    onThemeChange: (ReadingTheme) -> Unit,
    onFollowSystem: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("テーマ") },
        text = {
            Column {
                KThemeOption("システムに従う", followingSystem) {
                    onFollowSystem(); onDismiss()
                }
                ReadingTheme.entries.forEach { theme ->
                    KThemeOption(theme.displayNameK(), !followingSystem && appTheme == theme) {
                        onThemeChange(theme); onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun KThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.S8),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.S8))
    }
}

/**
 * 新着話通知のトグル行。配線は本棚⋮の NewEpisodeNotificationMenuSection と同一
 * （状態源＝NewEpisodeNotificationPreference・切替＝Application 経由・33+ は権限ダイアログ）。
 * 見た目だけ K の説明文つき行に置き換える＝二重管理になるのは見た目のみで状態は単一。
 */
@Composable
private fun NewEpisodeNotificationRowK() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NewEpisodeNotificationPreference.isEnabled(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒否されても Worker は動かす＝バッジ側の提示は生きる（通知だけ出ない）。⋮側と同じ扱い */ }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // TalkBack で「ラベル＋説明＋スイッチ」を1トラバーサル単位に（⋮側トグルと同流儀）。
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.S16)) {
            Text("新着話の通知", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Web作品の新しい話を1日1回確認して通知します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                enabled = on
                (context.applicationContext as NovelReaderApplication).setNewEpisodeNotificationEnabled(on)
                if (on && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

/** グループ見出し（モック .glabel 12px 字間広め）。 */
@Composable
private fun KSettingsGroupLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S8),
    )
}

/** グループの面（モック .card ヘアライン枠の白面）。影に頼らず枠線1本で沈める（Design/10「沈めて立てる」）。 */
@Composable
private fun KSettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
    }
}

/** 設定の1行（アイコン＋主ラベル＋説明＋trailing）。onClick=null は情報行（非活性・案内のみ）。 */
@Composable
private fun KSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    description: String?,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)?,
) {
    val base = if (onClick != null) {
        Modifier.selectable(selected = false, role = Role.Button, onClick = onClick)
    } else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = base
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                // アイコン無し行はアイコン幅24+間隔16=40 を Spacing.S40 で揃える（テキスト開始位置を全行で一致させる）。
                .padding(start = if (icon != null) Spacing.S16 else Spacing.S40, end = Spacing.S8),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** テーマの表示名（K 設定・ダイアログ共用）。既存 enum に表示名が無いためここで写像する。 */
private fun ReadingTheme.displayNameK(): String = when (this) {
    ReadingTheme.LIGHT -> "ライト"
    ReadingTheme.SEPIA -> "セピア"
    ReadingTheme.DARK -> "ダーク"
}
