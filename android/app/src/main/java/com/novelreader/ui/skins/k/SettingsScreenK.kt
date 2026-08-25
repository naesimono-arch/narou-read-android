package com.novelreader.ui.skins.k

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Title
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.BuildConfig
import com.novelreader.NewEpisodeNotificationPreference
import com.novelreader.NovelReaderApplication
import com.novelreader.ui.AdapterHealthBoardDialog
import com.novelreader.ui.components.shioriDebugTipStep
import com.novelreader.ui.theme.NovelReaderAlertDialog
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
    // 公開スコープ機能ゲート（ADR 0027 適用点1）。BuildConfig を本画面から直接読まず引数で受けるのは、
    // JVM テストが debug の BuildConfig しか見ず「行が消えている」側を固定できないため（ADR 0027 決定4）。
    // 本番の値は呼び出し元（MainActivity）が Features から供給する。
    skinSwitchingEnabled: Boolean,
    // 栞アニメ高負荷（ADR 0023 の明快K展開・2026-08-06 裁定）。導線はモック申し送り②「設定面の開発節に1行」＝
    // 星図M「高負荷スカイ（試作）」トグルと同型（debug 限定・release 常時 OFF）。露出可否を引数で受ける理由は
    // 上の skinSwitchingEnabled と同じ（ADR 0027 決定4＝release 側の不在を JVM テストで固定する）。
    // 既定 false / no-op＝既存呼び出し・設定画面 golden は1pxも変わらない（露出は MainActivity の本番配線のみ）。
    shioriHighLoadRowVisible: Boolean = false,
    shioriHighLoadK: Boolean = false,
    onShioriHighLoadChange: (Boolean) -> Unit = {},
    // 栞先端 tip の固定（debug 限定の観察器・2026-08-25）。露出可否は上の shioriHighLoadRowVisible を兼用する
    //（同じ開発節の中の2行目＝節の可否は1つで足りる）。null＝固定しない。
    // 既定 null / no-op＝既存呼び出し・設定画面 golden は1pxも変わらない。
    shioriDebugTipIndex: Int? = null,
    onShioriDebugTipChange: (Int?) -> Unit = {},
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
        // 副文は per-skin（[singleThemeNoticeK]＝各スキンの設定モックが正本。2026-08-17 裁定の翻訳）。
        // 行名「テーマ」と右端の値は共通のまま＝モック M の primary「星図 ・ 夜の相」（装い名つき）への
        // 置換は別件（モック冒頭 .chev-hole の注記どおり、この行だけモック構造と1対1に写らない）。
        // この畳んだ側の副文は隠したはずの「きせかえ」を案内するが、公開ビルドでは到達しない＝
        // 単一変種スキンは M/C だけで、そこへ着くには装いの間が要り、skinFromName のクランプで明快K（3変種）に固定されるため。
        val supportedThemes = currentSkin.tokens.supportedThemes
        KSettingsGroupLabel("表示")
        KSettingsCard {
            if (supportedThemes.size <= 1) {
                KSettingsRow(
                    icon = Icons.Outlined.Contrast,
                    title = "テーマ",
                    description = currentSkin.singleThemeNoticeK(),
                    trailing = {
                        KSettingsValue(supportedThemes.firstOrNull()?.displayNameK() ?: appTheme.displayNameK())
                        // 畳んだ側は叩けないので山括弧が無い＝占位が無いと値だけ右へせり出す（[KSettingsChevronHole]）。
                        KSettingsChevronHole()
                    },
                    onClick = null,
                )
            } else {
                KSettingsRow(
                    icon = Icons.Outlined.Contrast,
                    title = "テーマ",
                    description = null,
                    trailing = {
                        KSettingsValue(if (followingSystem) "システムに従う" else appTheme.displayNameK())
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { showThemeDialog = true },
                )
            }
            // 公開ビルドではスキン軸ごと隠す（ADR 0027）＝この行が装いの間への唯一の入口なので、
            // 出さないことが「装いの間へ人が辿り着けない」ことと同義になる（ルート未登録の関門は MainActivity 側）。
            // テーマ行（上）は Skin と独立した軸なので残す——ここまで消すとダークモードが失われ明確な後退になる。
            if (skinSwitchingEnabled) {
                KSettingsRow(
                    icon = Icons.Outlined.Checkroom,
                    title = "きせかえ",
                    // 現在の装い名は副文へ混ぜず行の右端へ出す（モック settings-J.html の `.rr > .rv`＝
                    // テーマ行・バージョン行と同じ「値は右端・説明は副文」の分担）。2026-08-14 人間裁定。
                    // なぜ副文へ混ぜないか: 「…装いを変える（現在: X）」は実機 360dp では一文が必ず折り返し、
                    // 既定の貪欲改行は値の途中で割る（`）` を行頭に置かない禁則が働き、閉じ括弧だけがこぼれて見える）。
                    // 値は trailing の独立ノード＝副文とは別の Text なので、副文は折り返さず値も割れない。
                    // ＝装い名がどれだけ長くても、値へ分割禁止（WORD JOINER）を挟む組版の手当ては要らない。
                    description = "本棚や画面の装いを変える",
                    trailing = {
                        KSettingsValue(currentSkin.displayName)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = onOpenWardrobe,
                )
            }
            // 文字サイズ等は読書中の表示設定が正本＝ここからは変えられない事実をそのまま案内する
            //（行を隠すと「文字設定はどこ？」の疑問符が残る。場所を教える行として置く＝自明性A0）。
            // アイコンはモック正本（settings-K/M ほか）が .ri に T グリフを置く規定の翻訳＝Title が等価
            //（icon=null は翻訳漏れ。この行だけ無アイコンで題字の開始位置も揃っていなかった＝2026-07-30 実機観察）。
            KSettingsRow(
                icon = Icons.Outlined.Title,
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

        // 開発節（栞アニメ高負荷・2026-08-06 裁定）。明快K 装着時のみ＝この試作は K 本棚の栞にしか効かない
        //（星図M のトグルが LocalSkin で M に絞るのと同じ理由＝他スキンでは意味の無いノブを出さない）。
        if (shioriHighLoadRowVisible && currentSkin == Skin.MEIKAI_K) {
            KSettingsGroupLabel("開発")
            KSettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // TalkBack で「ラベル＋説明＋スイッチ」を1トラバーサル単位に（通知トグル・星図M 開発節と同流儀）。
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
                ) {
                    Column(Modifier.weight(1f).padding(end = Spacing.S16)) {
                        Text("栞アニメ（試作）", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "高負荷モード: 本棚の栞先端 0〜8 が動く（開発版のみ）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = shioriHighLoadK, onCheckedChange = onShioriHighLoadChange)
                }
                // 栞先端の固定（観察器・2026-08-25）。なぜ要るか: tip は書影ごとに決まるため、
                // 高負荷アニメ tip 0〜8 を実機で順に見るには蔵書の並びを操作で戻し続けるしかなく目視不能だった
                //（2026-08-21 実機ツアー）。1種に固定すれば棚の全冊が同じ先端になり 0→8 を送って裁定できる。
                // なぜ数値入力でなくステッパーか: 実機で片手のまま隣の tip へ送れることが観察の本体で、
                // キーボードを出すと本棚へ戻るまでの手数が増える（0〜8 は1タップずつ・遠い番号は ±10 で寄せる）。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
                ) {
                    Text("栞先端を固定（観察）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        shioriDebugTipDescription(shioriDebugTipIndex),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.S4),
                    ) {
                        // 送りは「解除→0→…→173→解除」の巡回（shioriDebugTipStep）＝どちら向きでも解除へ戻れる。
                        ShioriDebugTipButton("−10") { onShioriDebugTipChange(shioriDebugTipStep(shioriDebugTipIndex, -10)) }
                        ShioriDebugTipButton("−1") { onShioriDebugTipChange(shioriDebugTipStep(shioriDebugTipIndex, -1)) }
                        ShioriDebugTipButton("＋1") { onShioriDebugTipChange(shioriDebugTipStep(shioriDebugTipIndex, 1)) }
                        ShioriDebugTipButton("＋10") { onShioriDebugTipChange(shioriDebugTipStep(shioriDebugTipIndex, 10)) }
                        ShioriDebugTipButton("解除") { onShioriDebugTipChange(null) }
                    }
                }
            }
        }

        KSettingsGroupLabel("このアプリ")
        KSettingsCard {
            KSettingsRow(
                icon = Icons.Outlined.Info,
                title = "バージョン",
                description = null,
                // 情報行＝叩けないので山括弧が無い。占位を置いて値の右端をテーマ・きせかえ行と揃える
                //（モック settings-K.html:162 の `<span class="chev-hole">`。理由は [KSettingsChevronHole]）。
                trailing = {
                    KSettingsValue(BuildConfig.VERSION_NAME)
                    KSettingsChevronHole()
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
    NovelReaderAlertDialog(
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
 * 新着話通知のトグル行。ここが通知オプトインの単一入口（本棚⋮の通知節は撤去済み＝2026-07-24 の K形伝播）。
 * 配線は状態源＝NewEpisodeNotificationPreference・切替＝Application 経由・33+ は権限ダイアログ。
 * 見た目は K の説明文つき行。旧・共通トグル部品（NewEpisodeNotificationToggle）は本行が引き取ったことで
 * 呼び出しがゼロになり 2026-07-27 に削除した＝通知 UI はこの1実装だけ（復元するなら git 履歴から）。
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

/**
 * 設定行の**値**（trailing に出る現在値の文字）。幅の上限を持つことがこの部品の存在理由。
 *
 * なぜ上限が要るか（真因）: `Row` は非加重の子（値の Text・山括弧 Icon）を実測幅で先に確定し、残りだけを
 * `weight(1f)` の題字列へ渡す＝**値が伸びるほど削られるのは行名の側**になる。fontScale 2.0 では
 * 「システムに従う」が行幅をほぼ占め、行名「テーマ」が「…」だけに潰れて何の行か読めなくなっていた
 * （実測 2026-08-17: 行名に残った幅 85px＝可視0文字）。型＝`docs/knowledge/unweighted-trailing-steals-row-width.md`。
 * ⚠️ `maxLines` は**行数**の上限であって幅の上限ではないので効かない（題字側には既に付いていて潰れていた）。
 *
 * 上限値の出どころ（モック正本の案B＝`docs/design-candidates/skins/candidates/settings-K-row-width-candidates.html`。
 * 規定は `224dp − 16sp×4字`）:
 *  ・[K_ROW_TEXT_BUDGET] 224dp＝行内容 296dp（画面 360dp − 画面横 16×2 − 行の横 16×2）− アイコン 24
 *    − 題字列の padding(16+8) − 山括弧 24 ＝**行名と値が分け合う予算**。
 *  ・[K_ROW_TITLE_FONT_SIZE]×[K_ROW_TITLE_RESERVE_CHARS]＝そのうち行名に予約する分（bodyLarge 4字）。
 *    sp なので fontScale に追従して伸び、値の取り分は自動で縮む。
 * 結果（実測 2026-08-17・360dp/xhdpi）: 1.0 は上限 160dp に対し最長の値「システムに従う」が 98dp＝
 * **届かないので効かず版面は不変**。2.0 は 16sp→28dp なので上限 112dp となり値は「システ…」へ縮み、
 * 行名には 112dp（「テーマ」に要る 84dp）が残る。どちらも1行のままなので行高も骨格も変わらない。
 * 失う側を値にしたのは、値は叩けば復元できる（ダイアログが現在値を選択状態で見せる）のに対し
 * 行名には復元手段が無いため（2026-08-17 人間裁定）。
 *
 * 幅を持たない素の `Text` で値を書くとこの穴へ戻るので、**値は必ずこの部品を通す**こと。
 */
@Composable
private fun KSettingsValue(text: String) {
    val maxWidth = with(LocalDensity.current) {
        // ⚠️ 予約は「1字ぶんを dp へ換算してから字数倍」でなければならない（`(16.sp * 4).toDp()` は誤り）。
        // API 34 の sp→dp は**非線形**で、大きな sp ほど倍率が落ちる＝実測（fontScale 2.0）で
        // 64sp→68.1dp に対し 16sp→28dp×4=112dp。まとめて換算すると予約が痩せ、行名が1字しか残らなかった。
        // 「bodyLarge 4字」という意匠の規定を守るには、1字（16sp）の実換算値を字数倍する。
        val titleReserve = K_ROW_TITLE_FONT_SIZE.toDp() * K_ROW_TITLE_RESERVE_CHARS
        // 予約が予算を食い切る領域（極端な fontScale）では負になる。widthIn に負値は渡せないため 0 で止める
        // （値は消えるが行名は残る＝失う順序の裁定どおり）。
        (K_ROW_TEXT_BUDGET - titleReserve).coerceAtLeast(0.dp)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 上限まで縮めた結果を折り返させない（折り返すと行高が変わり版面が動く＝案Bの条件を破る）。
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = maxWidth),
    )
}

/** 行名と値が分け合う幅の予算（モック案B の 224dp＝360dp 幅の版面で較正）。 */
private val K_ROW_TEXT_BUDGET = 224.dp

/** 予約の単位＝行名の字送り（bodyLarge の 16sp。全角1字＝1em で数える）。Typography.bodyLarge と一致させる。 */
private val K_ROW_TITLE_FONT_SIZE = 16.sp

/** 予算のうち行名へ先取りで予約する字数（モック案B の「4字」）。行名の最長は「テーマ」「きせかえ」＝4字。 */
private const val K_ROW_TITLE_RESERVE_CHARS = 4

/**
 * 山括弧を持たない行の右端に置く**不可視の占位**（モック正本 `skins/settings-K.html` の `.chev-hole`＝
 * 同 71-77・162行の規定の翻訳。2026-08-17 人間裁定「値の右端は全行で揃える」）。
 *
 * なぜ空の箱が要るか: [KSettingsRow] の trailing は〈値 → 山括弧〉を素直に横へ並べるだけなので、
 * 山括弧の無い行では値がその幅ぶん右へせり出し、**値の右端が行ごとに食い違う**
 * （実機実測 2026-08-17: 96px＝24dp のズレ）。占位を1つ置けば山括弧の有無に依らず右端が一致し、
 * 値だけを縦に読める。トグル行（通知・開発節）には置かない——操作部品は行の右端に接するのが正で、
 * 値の列とは別の役（モック同注記）。値を持たない行（文字と組版）にも置かない＝揃える対象の値が無く、
 * 置けば副文の幅だけが痩せる（モックもその行には `.rr` を持たない）。
 */
@Composable
private fun KSettingsChevronHole() {
    Spacer(Modifier.width(K_ROW_CHEVRON_WIDTH))
}

/**
 * 占位の幅＝山括弧アイコンが実際に占める幅。M3 `Icon` は寸法指定が無いとき ImageVector の既定寸
 * （24dp）で置かれ、trailing 内に間隔は入らないので、ズレ量はこの1つの寸法そのもの（上の実測 24dp と一致）。
 * 由来はアイコン寸だが値が余白スケールの S24 と同値なのでトークン参照で書ける
 *（生の dp 直書きは Spacing lint＝ADR 0014 §C で落ちる）。⚠️ 山括弧の寸法を変えるならここも動かす。
 */
private val K_ROW_CHEVRON_WIDTH = Spacing.S24

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

/**
 * 単一変種スキン（supportedThemes=[DARK]）のテーマ行に出す副文。文言の正本は**各スキンの設定モック**。
 *
 * なぜ per-skin か: 「相がひとつ」の説明はその装いの語彙で書かれる（星図Mなら夜天）＝全スキン共通の汎用文だと
 * 装いの語彙体系から浮く（2026-08-17 人間裁定。モック側は同日反映済みで、ここはその Compose 翻訳）。
 *
 * なぜ `else` を置かず全スキンを並べるか: 単一変種スキンが増えたとき、文言を決めないまま汎用文へ黙って
 * 落ちるのを防ぐため＝スキン追加が必ずコンパイルエラーになり、正本モックの参照を強制できる。
 * 3変種スキンはこの行に到達しない（呼び出し側が supportedThemes.size <= 1 でしか引かない）。
 *
 * ⚠️ 夜行C は設定モック（settings-C.html）が未作成＝文言の正本が存在しない。近似で創作せず従来の汎用文のまま
 * 置く（ここで埋めると「正本より先にコードが決めた文言」になり、モック作成時に逆同期の負債が生まれる）。
 */
private fun Skin.singleThemeNoticeK(): String = when (this) {
    // 正本: docs/design-candidates/skins/settings-M.html のテーマ行（.relay > .rt2 > .s）
    Skin.SEIZU_M -> "この装いは夜天ひとつ。ほかの相は「きせかえ」から"
    // 正本モック未作成のため従来の汎用文（上記 KDoc の ⚠️）。3変種スキンは到達しないが分岐は残す。
    Skin.YAKO_C, Skin.MEIKAI_K, Skin.WAMODERN_D, Skin.CARTRIDGE_P, Skin.PORTAL_J ->
        "この装いはひとつの相のみです。ほかの装いは「きせかえ」から選べます"
}

/** テーマの表示名（K 設定・ダイアログ共用）。既存 enum に表示名が無いためここで写像する。 */
private fun ReadingTheme.displayNameK(): String = when (this) {
    ReadingTheme.LIGHT -> "ライト"
    ReadingTheme.SEPIA -> "セピア"
    ReadingTheme.DARK -> "ダーク"
}

/**
 * 栞先端の固定行に出す現在値の説明（開発節・debug 限定）。
 * 「高負荷アニメは 0〜8」を併記するのは、9 以上へ送ると**動かないのが正常**（対象外 tip は静止パス）で、
 * それを知らずに送ると「アニメが壊れた」と誤読するため（判定は shioriHighLoadActive）。
 */
private fun shioriDebugTipDescription(index: Int?): String =
    if (index == null) "固定しない（書影ごとの先端）／高負荷アニメの対象は 0〜8"
    else "tip $index に固定中（全書影が同じ先端）／高負荷アニメの対象は 0〜8"

/**
 * 固定行のステッパー1個（開発節・debug 限定）。
 * contentPadding を詰めるのは、5 個を 360dp 幅の1行へ収めるため（既定の TextButton は横 24dp ずつ食い、
 * 5 個で画面幅を超えて最後の「解除」が押せなくなる＝観察器としての行き止まりになる）。
 */
@Composable
private fun ShioriDebugTipButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.S8, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
