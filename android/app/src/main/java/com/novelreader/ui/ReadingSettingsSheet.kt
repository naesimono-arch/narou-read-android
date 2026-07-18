package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.MoonSlateSeizu
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = when {
            isSeizu -> SeizuSheetBottom
            isCartridge -> CartridgeSheetBottom
            else -> colors.background
        },
        contentColor = colors.text,
        dragHandle = if (isSeizu || isCartridge) null else ({ BottomSheetDefaults.DragHandle() }),
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
) {
    val isSeizu = LocalSkin.current == Skin.SEIZU_M
    val isCartridge = LocalSkin.current == Skin.CARTRIDGE_P
    // スキンJ（ポータル）＝「扉の前の身支度」。面/見出し/つまみは D 既定のまま（シート面 colors.background・明朝見出し・
    // 金つまみ＝J の colors.accent が金）で自然に J の署名になる。J 固有はテーマ3択を「扉の向こうの光」の
    // 小プレビュー（扉プレビューチップ）にする点のみ（settings-J）。
    val isPortal = LocalSkin.current == Skin.PORTAL_J
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // スキンM: シート面の上下グラデ（settings-M .sheet #182034→#111726。padding より先＝面全体を塗る）。
            // スキンP: プラ筐体面の上下グラデ（settings-P .sheet・テーマ不変＝機体のシステムメニュー面）。
            .then(
                when {
                    isSeizu -> Modifier.background(SeizuSheetBrush)
                    isCartridge -> Modifier.background(CartridgeSheetBrush)
                    else -> Modifier
                }
            )
            .padding(horizontal = Spacing.S24)
            .padding(bottom = Spacing.S32),
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
            labelColor = colors.text,
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
        Text(
            text = "本文の向き",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(Spacing.S8))
        FilterChip(
            selected = verticalMode,
            onClick = { onVerticalModeChange(!verticalMode) },
            label = { Text("縦書き") },
            colors = themeChipColors,
        )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "文字サイズ",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            // 現在値は右端の藍数字（モック settings-D の値表示）。ラベル連結より視線移動が少ない
            Text(
                text = "${fontSize}sp",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
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
                isSeizu = isSeizu,
                isCartridge = isCartridge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.S12),
            )
            Text("あ", fontSize = 24.sp, fontFamily = MinchoFamily)
        }
        Spacer(Modifier.height(Spacing.S24))
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
                color = colors.accent,
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
                isSeizu = isSeizu,
                isCartridge = isCartridge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.S12),
            )
            Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
        }
        Spacer(Modifier.height(Spacing.S24))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本文余白",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${bodyMarginDp}dp",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
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

/**
 * 設定スライダーの薄いラッパー（ロジック共通・意匠だけスキン分岐＝ADR 0022 §1）。
 * M ではつまみ＝きらめく星・トラック＝結線（settings-M .knob/.track）／P ではつまみ＝プラのノブ・
 * トラック＝青の値トラック（settings-P .knob/.track）。fraction は SliderState の内部 API に依存せず
 * value/valueRange から自前計算する（実験 API への接触面を最小に保つ）。
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
    isSeizu: Boolean,
    isCartridge: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isSeizu || isCartridge) {
        val interactionSource = remember { MutableInteractionSource() }
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
            modifier = modifier,
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
