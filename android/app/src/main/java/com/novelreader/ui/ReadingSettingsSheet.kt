package com.novelreader.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.skins.j.PortalThemeDoorChips
import com.novelreader.ui.skins.m.SeizuSheetBottom
import com.novelreader.ui.skins.m.SeizuSheetBrush
import com.novelreader.ui.skins.m.SeizuSliderThumb
import com.novelreader.ui.skins.m.SeizuSliderTrack
import com.novelreader.ui.skins.m.SettingsHeaderFragM
import com.novelreader.ui.skins.m.ThemeFixedRowM
import com.novelreader.ui.skins.p.CartridgeGrab
import com.novelreader.ui.skins.p.CartridgeSheetBottom
import com.novelreader.ui.skins.p.CartridgeSheetBrush
import com.novelreader.ui.skins.p.CartridgeSliderThumb
import com.novelreader.ui.skins.p.CartridgeSliderTrack
import com.novelreader.ui.skins.p.SettingsSysBarP
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.InkCartridge
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.MotionDurationSettingsPeekHide
import com.novelreader.ui.theme.MotionDurationSettingsPeekReturn
import com.novelreader.ui.theme.MotionEasingSettingsPeekHide
import com.novelreader.ui.theme.MotionEasingSettingsPeekReturn
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

/** 表示設定ボトムシート（テーマ切替・文字サイズ）。色は読書テーマ（colors）に追従させる */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsSheet(
    colors: ReadingColors,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    // システム追従（未宣言）状態か。true のとき明示テーマ3択はどれも未選択で「システムに従う」を選択表示する。
    followingSystem: Boolean = false,
    // 「システムに従う」選択時のコールバック。呼び出し側で reading_theme prefs を remove して未宣言へ戻す。
    onFollowSystem: () -> Unit = {},
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    // 永続化コールバック（スライダー確定時のみ呼ぶ）。onXxxChange はドラッグ中の毎値で
    // 状態を更新して本文プレビューを追従させ、書き込みはこの確定時コールバックに集約する。
    onFontSizePersist: () -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    onLineHeightPersist: () -> Unit,
    bodyMarginDp: Int,
    onBodyMarginChange: (Int) -> Unit,
    onBodyMarginPersist: () -> Unit,
    // 縦書きモード（全書籍共通・app_prefs reading_vertical）。既定 false＝横書き。
    verticalMode: Boolean = false,
    onVerticalModeChange: (Boolean) -> Unit = {},
    // 案3ライブプレビュー: スライダー押下中（調整中）の変化を呼び出し側へ通知する。
    // 読書クローム（上下バー）はこのシートの外＝ChapterScreenContent 側にあり、そこでの透明化に使う。
    onAdjustingChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    // なぜ containerColor/contentColor を読書テーマで明示するか:
    // 未指定だとシート色がシステムテーマ（MaterialTheme.surface）に従うため、
    // 例えば「システム=ライト・読書テーマ=ダーク」で設定を開くと白いシートがフラッシュする。
    // 読書中の背景と一致させて違和感とフラッシュをなくす。
    // スキンM は「観測パネル」＝シート面が上下グラデ（settings-M .sheet）。container はグラデ終点色にし、
    // 面の描画とつまみ（.grab）は Content 側が担う（既定ハンドルを消してグラデの継ぎ目を作らない）。
    // スキンP は「機体のシステムメニュー」＝シート面がプラ筐体面の上下グラデ（テーマ不変・settings-P .sheet）。
    // container はグラデ終点色にし、面の描画と自前グラブは Content 側が担う（既定ハンドルを消して継ぎ目を作らない）。
    val isSeizu = LocalSkin.current == Skin.SEIZU_M
    val isCartridge = LocalSkin.current == Skin.CARTRIDGE_P
    // 案3「一行残し」ライブプレビュー（2026-07-29 裁定・reading-settings-livepreview-D.html VARIANT 3）:
    // スライダー押下中はシート面とスクリムを完全透明化する。押下検出は Content 側（スライダーの
    // interactionSource）が持ち、ここは Boolean だけ受けてシート枠（scrim/container/grab）の退避を担う。
    // なぜ ModalBottomSheet を自前オーバーレイへ置き換えないか: scrimColor/containerColor は毎コンポジ
    // ションで読まれる引数のため、値を退避割合でアニメ供給すれば「レイアウト不変のまま透明化」がモックの
    // opacity 方式と同型で成立する。自前化は開閉アニメ・Back/外タップ dismiss・フォーカス閉じ込め等の
    // 既存挙動を作り直す羽目になり、構造変更最小の裁定条件に反する。
    var adjusting by remember { mutableStateOf(false) }
    val peek = animateSettingsPeek(adjusting)
    val scrimBase = BottomSheetDefaults.ScrimColor
    val containerBase = when {
        isSeizu -> SeizuSheetBottom
        isCartridge -> CartridgeSheetBottom
        else -> colors.background
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 退避割合ぶん透明化（色引数は composition 読み＝退避/復帰の短尺遷移中だけ毎フレーム再合成される。
        // graphicsLayer の deferred read が使えない引数 API のための割り切り＝尺は 160/260ms のみ）。
        containerColor = containerBase.copy(alpha = containerBase.alpha * (1f - peek.value)),
        scrimColor = scrimBase.copy(alpha = scrimBase.alpha * (1f - peek.value)),
        // P のシート面はテーマ不変のプラ筐体＝文字も固定墨（settings-P --ink）。読書テーマの colors.text を
        // 使うと DARK 時に白系文字が明るいプラ面へ溶けて未選択チップ・見出しがほぼ不可視になる（実機検分[高]）。
        contentColor = if (isCartridge) InkCartridge else colors.text,
        dragHandle = if (isSeizu || isCartridge) null else ({
            // 既定グラブも退避対象（モック .sheet>* に .grab が含まれる）。graphicsLayer＝draw 段の deferred read。
            Box(Modifier.graphicsLayer { alpha = 1f - peek.value }) { BottomSheetDefaults.DragHandle() }
        }),
    ) {
        ReadingSettingsSheetContent(
            colors = colors,
            readingTheme = readingTheme,
            onThemeChange = onThemeChange,
            followingSystem = followingSystem,
            onFollowSystem = onFollowSystem,
            fontSize = fontSize,
            onFontSizeChange = onFontSizeChange,
            onFontSizePersist = onFontSizePersist,
            lineHeightEm = lineHeightEm,
            onLineHeightChange = onLineHeightChange,
            onLineHeightPersist = onLineHeightPersist,
            bodyMarginDp = bodyMarginDp,
            onBodyMarginChange = onBodyMarginChange,
            onBodyMarginPersist = onBodyMarginPersist,
            verticalMode = verticalMode,
            onVerticalModeChange = onVerticalModeChange,
            // Content の押下検出をシート枠の退避（上）とクローム退避（呼び出し側）へ配線する。
            onAdjustingRowChange = { row ->
                adjusting = row != null
                onAdjustingChange(row != null)
            },
        )
    }
}

/** シート内容（見出し・テーマ3択・スライダー3本）。ReadingSettingsSheet からの純移動。
 *  なぜ ModalBottomSheet と分離するか: 内容は state+callback の純粋な葉で、シート枠
 *  （別ウィンドウ描画・開閉アニメ・部分展開で下部が画面外に出る）と切り離すことで
 *  Robolectric の JVM UI テスト（ADR 0009）が可視判定・クリック注入を安定検証できるため。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingSettingsSheetContent(
    colors: ReadingColors,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean = false,
    onFollowSystem: () -> Unit = {},
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontSizePersist: () -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    onLineHeightPersist: () -> Unit,
    bodyMarginDp: Int,
    onBodyMarginChange: (Int) -> Unit,
    onBodyMarginPersist: () -> Unit,
    // 縦書きモードのトグル（全書籍共通・app_prefs reading_vertical）。既定 false＝横書き。
    verticalMode: Boolean = false,
    onVerticalModeChange: (Boolean) -> Unit = {},
    // 案3ライブプレビュー: 押下中スライダー行の変化通知（null=非調整）。検出はこの Content が
    // 各スライダーの interactionSource から行い、シート枠・読書クロームの退避は通知先が担う。
    onAdjustingRowChange: (ReadingSettingsAdjustingRow?) -> Unit = {},
) {
    val isSeizu = LocalSkin.current == Skin.SEIZU_M
    val isCartridge = LocalSkin.current == Skin.CARTRIDGE_P
    // スキンJ（ポータル）＝「扉の前の身支度」。面/見出し/つまみは D 既定のまま（シート面 colors.background・明朝見出し・
    // 金つまみ＝J の colors.accent が金）で自然に J の署名になる。J 固有はテーマ3択を「扉の向こうの光」の
    // 小プレビュー（扉プレビューチップ）にする点のみ（settings-J）。
    val isPortal = LocalSkin.current == Skin.PORTAL_J

    // ── 案3「一行残し」ライブプレビュー（2026-07-29 裁定・モック VARIANT 3）──
    // スライダーを押している間だけ、触っている行以外のシート内容を透明化し、触っている行だけ
    // 白ピルで浮遊させる（本文への効き目を見ながら追い込むため）。押下検出は行ごとの
    // interactionSource（DragInteraction/PressInteraction）。
    val fontSizeInteraction = remember { MutableInteractionSource() }
    val lineHeightInteraction = remember { MutableInteractionSource() }
    val bodyMarginInteraction = remember { MutableInteractionSource() }
    val adjustingRow = resolveAdjustingRow(fontSizeInteraction, lineHeightInteraction, bodyMarginInteraction)
    // 退避割合（0=通常/1=退避）。値は draw 段でのみ読む（graphicsLayer/drawBehind の deferred read）＝
    // 遷移フレームごとに composition を再実行しない。
    val peek = animateSettingsPeek(adjustingRow != null)
    // 通知は副作用として合成外で行う（composition 中の親 state 書き戻しを避ける）。
    // rememberUpdatedState: 通知先ラムダが再合成で差し替わっても常に最新を呼ぶ。
    val currentOnAdjustingRowChange by rememberUpdatedState(onAdjustingRowChange)
    LaunchedEffect(adjustingRow) { currentOnAdjustingRowChange(adjustingRow) }
    // 押下中にシートごと破棄された場合（外タップ dismiss 等）に調整中通知が立ちっぱなしに
    // ならないよう、破棄時は必ず null を送る（防御的解除）。
    DisposableEffect(Unit) { onDispose { currentOnAdjustingRowChange(null) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // スキンM: シート面の上下グラデ（settings-M .sheet #182034→#111726。padding より先＝面全体を塗る）。
            // スキンP: プラ筐体面の上下グラデ（settings-P .sheet・テーマ不変＝機体のシステムメニュー面）。
            // background でなく drawBehind＋alpha なのは案3退避のため（面も退避対象。alpha=1 で background と同一描画）。
            .then(
                when {
                    isSeizu -> Modifier.drawBehind { drawRect(brush = SeizuSheetBrush, alpha = 1f - peek.value) }
                    isCartridge -> Modifier.drawBehind { drawRect(brush = CartridgeSheetBrush, alpha = 1f - peek.value) }
                    else -> Modifier
                }
            )
            .padding(horizontal = Spacing.S24)
            .padding(bottom = Spacing.S32),
    ) {
        // ── 退避グループ（スライダー3行以外の全内容）: 押下中は丸ごと透明化（モック .sheet>*:not(.hot)）──
        // fillMaxWidth は必須: 内側 Column が wrap 幅になると M/P グラブの CenterHorizontally が画面中央から
        // ずれ、レイアウト不変（golden 同一）の前提が崩れる。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - peek.value },
        ) {
            if (isSeizu) {
                // M の自前つまみ（settings-M .grab 38×4・月光スレート α.35）。既定ハンドルはシート側で消している。
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.S16, bottom = Spacing.S24)
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .background(MoonSlateSeizu.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                )
            } else if (isCartridge) {
                // P の自前グラブ（settings-P .grab 38×5・--plastic-lo）。既定ハンドルはシート側で消している。
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.S16, bottom = Spacing.S24)
                        .align(Alignment.CenterHorizontally),
                ) { CartridgeGrab() }
            }
            if (isCartridge) {
                // P のシステムメニューヘッダ＝緑LCDの起動画面感バー（settings-P .sysbar）。見出しの上に載せる。
                SettingsSysBarP()
                Spacer(Modifier.height(Spacing.S16))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // スキンM: 見出し脇に小星座片（settings-M h2 の SVG）。
                if (isSeizu) {
                    SettingsHeaderFragM()
                    Spacer(Modifier.width(Spacing.S8))
                }
                Text(
                    text = "表示設定",
                    style = MaterialTheme.typography.titleMedium,
                    // モック settings-D .sheet h2: 明朝・weight600・字間.08em。P はゴシック（--gothic＝既定サンセリフ）
                    //   で題字を組む署名のため明朝を使わない（settings-P h2 は gothic weight700）。
                    fontFamily = if (isCartridge) null else MinchoFamily,
                    fontWeight = if (isCartridge) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.08.em,
                )
            }
            Spacer(Modifier.height(Spacing.S4))
            // なぜ適用範囲を見出し直下に明示するか（T2ユーザビリティ所見の再発防止）:
            // このシートには設定スコープの予告が無く「この本だけの設定」と誤解された。
            // 実際は app_prefs の固定キー（reading_font_size / reading_line_height /
            // reading_body_margin / reading_theme）で全書籍共有＝どの本でも同じ値になるため、
            // 開いた時点で全書籍スコープであることを1行キャプションで先に伝える。
            // 色は装飾補助でなく意味を運ぶ文字＝AA を満たす infoText、寸法は極小メタ用の FontMicroLabel。
            Text(
                text = "配色や文字の設定は、すべての本に適用されます",
                fontSize = FontMicroLabel,
                color = colors.infoText,
            )
            // テーマ節はスキンが複数変種を持つときだけ出す（C夜行=DARK固定の1変種では選べるものが無く、
            // 出すと「押しても変わらないチップ」になる）。畳んでも "reading_theme" prefs には触れない＝
            // D へ復帰したとき前回のテーマ宣言（追従含む）がそのまま復元される（handover A2 P2 の要件）。
            val skinHasThemeChoice = LocalSkinTokens.current.supportedThemes.size > 1
            // 選択色をアクセント(朱)に統一する（M3 既定の secondaryContainer=青鼠を避け主役色へ）。
            // システムテーマでなく読書テーマの colors を使い、シート背景(colors.background)と調和させる。
            // テーマ3択（skinHasThemeChoice 節）と縦書きトグルの両方で共有するため、スキン節分岐の外＝この
            // スコープで1度だけ定義する（スキン機構でテーマ節が when(skin) 分岐へ入った結果、分岐の外に立つ
            // 縦書きトグルから旧・節内定義が見えなくなった統合時の是正。「新しい色を作らず流用」の意図は不変）。
            val themeChipColors = FilterChipDefaults.filterChipColors(
                // P はシート文字が固定墨（上の contentColor と同根＝テーマ不変プラ面に白系が溶ける対策）。
                // filterChipColors は LocalContentColor を継承しないため明示指定が要る。
                labelColor = if (isCartridge) InkCartridge else colors.text,
                selectedContainerColor = colors.accent,
                selectedLabelColor = colors.background,
            )
            if (skinHasThemeChoice && isPortal) {
                // J＝テーマ3択を「扉の向こうの光」を選ぶ小プレビューにする（settings-J .chips）。ロジックは共有
                // （supportedThemes 駆動の3択＋システムに従う）で、意匠だけ扉プレビュー化する。
                Spacer(Modifier.height(Spacing.S16))
                Text(
                    text = "テーマ",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(Spacing.S8))
                PortalThemeDoorChips(
                    currentTheme = readingTheme,
                    followingSystem = followingSystem,
                    onThemeChange = onThemeChange,
                    onFollowSystem = onFollowSystem,
                    sheetColors = colors,
                )
            } else if (skinHasThemeChoice) {
                Spacer(Modifier.height(Spacing.S16))
                Text(
                    text = "テーマ",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(Spacing.S8))
                // なぜ FlowRow か: チップが4つ（システムに従う＋ライト/セピア/ダーク）になり、狭幅端末で
                // 素の Row だと横に溢れて末尾チップが見切れる。溢れたら次行へ折り返して全チップの可視を保つ。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S8),
                ) {
                    // 「システムに従う」= 未宣言（reading_theme prefs 削除）へ戻す選択肢。
                    // なぜ必要か（監査 settings Minor 19-B/H）: 一度でも明示テーマを押すと prefs に固定され、
                    // 二度と「OS のライト/ダークに自動追従」へ戻せず、夜に暗くなる既定挙動を失っていた。この
                    // チップ選択で呼び出し側が prefs を remove して未宣言（＝追従）へ復帰させる（配線は MainActivity）。
                    FilterChip(
                        selected = followingSystem,
                        onClick = onFollowSystem,
                        label = { Text("システムに従う") },
                        colors = themeChipColors,
                    )
                    // values() を使うのは Kotlin バージョン非依存のため（entries は 1.9+）
                    ReadingTheme.values().forEach { theme ->
                        FilterChip(
                            // 追従中は明示3択をどれも未選択にする（「今どれで表示中か」ではなく「何を宣言したか」を表す。
                            // 追従中は宣言が無い＝3択は未選択で、上の「システムに従う」だけが選択状態になる）。
                            selected = !followingSystem && readingTheme == theme,
                            onClick = { onThemeChange(theme) },
                            label = {
                                Text(
                                    when (theme) {
                                        ReadingTheme.LIGHT -> "ライト"
                                        ReadingTheme.SEPIA -> "セピア"
                                        ReadingTheme.DARK -> "ダーク"
                                    }
                                )
                            },
                            colors = themeChipColors,
                        )
                    }
                }
            } else if (isSeizu) {
                // M（夜の相・固定1変種）: 3択の代わりに「何が装着されているか」と変種切替の所在＝装いの間を明示する
                //（settings-M .theme-fixed。C は節ごと畳む既裁定のまま＝M モックだけがこの固定表示を正本に持つ）。
                Spacer(Modifier.height(Spacing.S16))
                Text(
                    text = "テーマ",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(Spacing.S12))
                ThemeFixedRowM()
            }
            Spacer(Modifier.height(Spacing.S24))
            // 縦書きトグル。テーマ3択と同じ FilterChip＋themeChipColors を流用する（新しい部品・色を作らない）。
            // なぜテーマ直下（チップ群のそば）に置くか（並び順の判断）: (1) テーマと同じ離散チップ選択なので
            // 3本のスライダーの間に挟まず、チップ系設定をまとめると視覚リズムが揃う。(2) このシートの Column は
            // スクロールを持たず、末尾に足すと縦長端末で画面外に切れて到達不能になり得るため、常時可視な上部へ
            // 置いて確実に届かせる。見出し語（labelMedium）は他設定と同じ体裁。チップ選択(accent塗り)＝縦書きON。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本文の向き",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                // trailing の現在値（モック settings-D 案C・2026-08-06 裁定）: 単独チップだけでは
                // 「現在の状態を示す表示」か「押すと何かが起きるボタン」かが読めないため、節ラベル行の
                // 右端へ今の組み方向を常時出す（説明文は置かない＝説明レス。押した結果は現在値の変化として
                // ここに現れる）。色は意味を運ぶ文字＝AA を満たす infoText（スライダー3節の現在値と同色）。
                Text(
                    text = if (verticalMode) "縦書き" else "横書き",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.infoText,
                )
            }
            Spacer(Modifier.height(Spacing.S8))
            FilterChip(
                selected = verticalMode,
                onClick = { onVerticalModeChange(!verticalMode) },
                label = { Text("縦書き") },
                colors = themeChipColors,
            )
        }
        // スライダー共通色。モック settings-D は目盛りドットを持たない細線＋藍フィルのため、
        // steps のスナップは維持したまま tick 色だけ透明化して視覚的に消す。
        // フィル/つまみはシート全体と同じく読書テーマの藍（colors.accent）に追従させる。
        val sliderColors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
        Spacer(Modifier.height(Spacing.S24))
        // ── スライダー3節（モック .sec.sl）: 各節＝ラベル行＋スライダー行の Column ──
        // 案3の「触っている行だけ残す」単位。押下中は自節が白ピルで浮遊し（settingsPeekRow）、
        // 他節は退避グループと同じ規則で透明化する。節化は graphicsLayer/drawBehind のみ＝レイアウト不変。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPeekRow(
                    hot = adjustingRow == ReadingSettingsAdjustingRow.FONT_SIZE,
                    peek = peek,
                    pillColor = colors.background,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "文字サイズ",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                // 現在値は右端の trailing 数字（モック settings-D .val）。ラベル連結より視線移動が少ない。
                // 色は infoText（2026-08-06 案C裁定＝trailing 現在値は「意味を運ぶ文字」。旧・藍 accent は
                // 選択・アクション役割へ限定する二役分離。行間・本文余白・本文の向きの trailing も同色）。
                Text(
                    text = "${fontSize}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.infoText,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 両端の「あ」はスライダーの効果（最小・最大の文字サイズ）を視覚的に示す
                Text("あ", fontSize = 14.sp, fontFamily = MinchoFamily)
                SettingsSlider(
                    value = fontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.roundToInt()) },
                    // ドラッグ確定時に一度だけ永続化（ドラッグ中の毎値 prefs 書き込みを避ける）
                    onValueChangeFinished = onFontSizePersist,
                    valueRange = 14f..24f,
                    // steps = 9 で 14〜24sp を 1sp 刻みの離散値にする（中間刻み = 範囲幅 - 1）
                    steps = 9,
                    colors = sliderColors,
                    interactionSource = fontSizeInteraction,
                    isSeizu = isSeizu,
                    isCartridge = isCartridge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.S12),
                )
                Text("あ", fontSize = 24.sp, fontFamily = MinchoFamily)
            }
        }
        Spacer(Modifier.height(Spacing.S24))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPeekRow(
                    hot = adjustingRow == ReadingSettingsAdjustingRow.LINE_HEIGHT,
                    peek = peek,
                    pillColor = colors.background,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "行間",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // なぜ Locale.US を明示するか: 既定ロケールだと欧州端末等で小数点が
                    // 「2,5」のようにカンマ表記に化けるため、表示を一貫させる。
                    text = String.format(Locale.US, "%.1f", lineHeightEm),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.infoText,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 両端の「狭／広」で行間スライダーの効果を視覚的に示す
                Text("狭", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
                SettingsSlider(
                    value = lineHeightEm,
                    // 0.1em 刻みに丸める。前行とのルビ被りを避けるため狭めレンジ(2.3〜2.8)に固定。
                    onValueChange = { onLineHeightChange((it * 10).roundToInt() / 10f) },
                    // ドラッグ確定時に一度だけ永続化
                    onValueChangeFinished = onLineHeightPersist,
                    valueRange = 2.3f..2.8f,
                    // steps = 4 で 2.3〜2.8em を 0.1em 刻みの離散値にする（中間刻み = 区切り数 - 1）
                    steps = 4,
                    colors = sliderColors,
                    interactionSource = lineHeightInteraction,
                    isSeizu = isSeizu,
                    isCartridge = isCartridge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.S12),
                )
                Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
            }
        }
        Spacer(Modifier.height(Spacing.S24))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPeekRow(
                    hot = adjustingRow == ReadingSettingsAdjustingRow.BODY_MARGIN,
                    peek = peek,
                    pillColor = colors.background,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本文余白",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${bodyMarginDp}dp",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.infoText,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 両端の「狭／広」で余白スライダーの効果を視覚的に示す
                Text("狭", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
                SettingsSlider(
                    value = bodyMarginDp.toFloat(),
                    onValueChange = { onBodyMarginChange(it.roundToInt()) },
                    // ドラッグ確定時に一度だけ永続化
                    onValueChangeFinished = onBodyMarginPersist,
                    // 10〜40dp を 5dp 刻み（steps=5）。既定 15 がグリッドに乗るレンジ設計
                    valueRange = 10f..40f,
                    steps = 5,
                    colors = sliderColors,
                    interactionSource = bodyMarginInteraction,
                    isSeizu = isSeizu,
                    isCartridge = isCartridge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.S12),
                )
                Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
            }
        }
    }
}

/**
 * 設定スライダーの薄いラッパー（ロジック共通・意匠だけスキン分岐＝ADR 0022 §1）。
 * M ではつまみ＝きらめく星・トラック＝結線（settings-M .knob/.track）／P ではつまみ＝プラのノブ・
 * トラック＝青の値トラック（settings-P .knob/.track）。fraction は SliderState の内部 API に依存せず
 * value/valueRange から自前計算する（実験 API への接触面を最小に保つ）。
 * interactionSource は呼び出し側（行）が所有する: 案3ライブプレビューの押下検出（どの行を触っているか）を
 * 行単位で観測するため（旧・M/P 分岐内の内製 remember を hoisting。Slider への渡し方は従来と同一＝挙動不変）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    colors: androidx.compose.material3.SliderColors,
    interactionSource: MutableInteractionSource,
    isSeizu: Boolean,
    isCartridge: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isSeizu || isCartridge) {
        val f = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            interactionSource = interactionSource,
            thumb = { if (isCartridge) CartridgeSliderThumb() else SeizuSliderThumb() },
            track = {
                if (isCartridge) CartridgeSliderTrack(fraction = f) else SeizuSliderTrack(fraction = f)
            },
            modifier = modifier,
        )
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            interactionSource = interactionSource,
            modifier = modifier,
        )
    }
}

// ── 案3「一行残し」ライブプレビューの部品（2026-07-29 裁定・reading-settings-livepreview-D.html VARIANT 3）──

/** 案3ライブプレビューで押下中のスライダー行。null=非調整（テスト契約: 押下で該当行・解放で null）。 */
internal enum class ReadingSettingsAdjustingRow { FONT_SIZE, LINE_HEIGHT, BODY_MARGIN }

/**
 * 3本のスライダーの interactionSource から「いま触っている行」を解決する。
 * M3 1.3 の Slider はドラッグ開始で DragInteraction.Start を emit する（draggable 契約）。Press 系は
 * 現行実装のトラックタップでは emit されないが、内部実装差・将来版で来ても拾えるよう OR で見る
 * （仕様は「押している間」＝取りこぼし方向の縮退だけを防ぐ防御。裁定の検出手段も interactionSource
 * （DragInteraction/PressInteraction）と明記されている）。
 * 複数同時押下（マルチタッチ）は先頭優先の when＝モックの単一 .hot と同じく常に1行だけを残す。
 */
@Composable
private fun resolveAdjustingRow(
    fontSize: MutableInteractionSource,
    lineHeight: MutableInteractionSource,
    bodyMargin: MutableInteractionSource,
): ReadingSettingsAdjustingRow? {
    val fontSizeDragged by fontSize.collectIsDraggedAsState()
    val fontSizePressed by fontSize.collectIsPressedAsState()
    val lineHeightDragged by lineHeight.collectIsDraggedAsState()
    val lineHeightPressed by lineHeight.collectIsPressedAsState()
    val bodyMarginDragged by bodyMargin.collectIsDraggedAsState()
    val bodyMarginPressed by bodyMargin.collectIsPressedAsState()
    return when {
        fontSizeDragged || fontSizePressed -> ReadingSettingsAdjustingRow.FONT_SIZE
        lineHeightDragged || lineHeightPressed -> ReadingSettingsAdjustingRow.LINE_HEIGHT
        bodyMarginDragged || bodyMarginPressed -> ReadingSettingsAdjustingRow.BODY_MARGIN
        else -> null
    }
}

/**
 * 案3退避の共有アニメ仕様（0=通常/1=退避）。押下=引き 160ms ease-out／解放=復帰 260ms ease
 * （Motion.kt の SettingsPeek トークン）。シート内容・シート枠・読書クロームの3層が
 * それぞれ本関数で自層の割合を持つ＝同一トークン・同一フレームクロックで視覚同期する
 * （単一 State の配線を3層へ引き回すより各層が自己完結する構造を採る）。
 */
@Composable
internal fun animateSettingsPeek(adjusting: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (adjusting) 1f else 0f,
    animationSpec = if (adjusting) {
        tween(MotionDurationSettingsPeekHide, easing = MotionEasingSettingsPeekHide)
    } else {
        tween(MotionDurationSettingsPeekReturn, easing = MotionEasingSettingsPeekReturn)
    },
    label = "settingsPeek",
)

// 白ピルの較正値（モック .c3.peek .sec.hot の写経。px→dp は既存モック規約の 1:1 写像）。
// スプレッド12: box-shadow 0 0 0 12px＝padding を変えず面だけ行の外へ張る（配置ジャンプさせない）。
// 角丸16: 行自身の border-radius 4px がスプレッド 12px ぶん外側へ膨らんだ外周半径（4+12）。
// 影: 0 10px 30px rgba(0,0,0,.18)＝浮遊感の落ち影。
private val SettingsPeekPillSpread = 12.dp
private val SettingsPeekPillCorner = 16.dp
private val SettingsPeekPillShadowBlur = 30.dp
private val SettingsPeekPillShadowOffsetY = 10.dp
private const val SETTINGS_PEEK_PILL_ALPHA = 0.92f
private const val SETTINGS_PEEK_PILL_SHADOW_ALPHA = 0.18f

/**
 * スライダー節の案3退避修飾: 非押下節は退避割合ぶん透明化し、押下節（hot）は白ピル
 * （読書地色 α.92 の面＋落ち影）で浮遊させる。graphicsLayer/drawBehind のみ＝レイアウト不変で、
 * peek は draw 段の deferred read（遷移フレームで composition を再実行しない）。
 */
private fun Modifier.settingsPeekRow(
    hot: Boolean,
    peek: State<Float>,
    pillColor: Color,
): Modifier {
    // 落ち影用 Paint（framework 直・ShioriCover と同型）。draw 毎の生成を避けるため
    // Modifier 生成時に1度だけ作る（hot/pillColor 変化時のみ作り直し）。
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    return this
        .graphicsLayer { alpha = if (hot) 1f else 1f - peek.value }
        .drawBehind {
            val p = peek.value
            if (!hot || p <= 0f) return@drawBehind
            val spread = SettingsPeekPillSpread.toPx()
            val corner = SettingsPeekPillCorner.toPx()
            // 落ち影（モック 0 10px 30px rgba(0,0,0,.18)）。graphicsLayer.shadowElevation は行の実寸境界に
            // しか落ちず、スプレッドで膨らんだピル面と一致しないため、BlurMaskFilter で面と同形に敷く。
            drawIntoCanvas { canvas ->
                shadowPaint.color = Color.Black.copy(alpha = SETTINGS_PEEK_PILL_SHADOW_ALPHA * p).toArgb()
                shadowPaint.maskFilter =
                    BlurMaskFilter(SettingsPeekPillShadowBlur.toPx(), BlurMaskFilter.Blur.NORMAL)
                val dy = SettingsPeekPillShadowOffsetY.toPx()
                canvas.nativeCanvas.drawRoundRect(
                    -spread,
                    -spread + dy,
                    size.width + spread,
                    size.height + spread + dy,
                    corner,
                    corner,
                    shadowPaint,
                )
            }
            // ピル面（読書地色 α.92）。退避割合 p に追従してモックの transition と同期で現れる。
            drawRoundRect(
                color = pillColor.copy(alpha = pillColor.alpha * SETTINGS_PEEK_PILL_ALPHA * p),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(corner),
            )
        }
}

// ── Preview ──────────────────────────────────────────────
// なぜ Content を直接 Preview するか: シート内容（テーマ3択・各スライダーと値ラベル）の意匠を
// 確認するのが目的で、シート枠（別ウィンドウ・開閉アニメ）は Preview 対象外のため。
@Preview(showBackground = true)
@Composable
private fun ReadingSettingsSheetPreview() {
    ReadingSettingsSheetContent(
        colors = ReadingTheme.LIGHT.colors,
        readingTheme = ReadingTheme.LIGHT,
        onThemeChange = {},
        fontSize = 18,
        onFontSizeChange = {},
        onFontSizePersist = {},
        lineHeightEm = 2.5f,
        onLineHeightChange = {},
        onLineHeightPersist = {},
        bodyMarginDp = 20,
        onBodyMarginChange = {},
        onBodyMarginPersist = {},
    )
}
