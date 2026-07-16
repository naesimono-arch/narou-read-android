package com.novelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.FontMicroLabel
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.text,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24)
            .padding(bottom = Spacing.S32),
    ) {
        Text(
            text = "表示設定",
            style = MaterialTheme.typography.titleMedium,
            // モック settings-D .sheet h2: 明朝・weight600・字間.08em
            fontFamily = MinchoFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        )
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
        Spacer(Modifier.height(Spacing.S16))
        Text(
            text = "テーマ",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(Spacing.S8))
        // 選択色をアクセント(朱)に統一する（M3 既定の secondaryContainer=青鼠を避け主役色へ）。
        // システムテーマでなく読書テーマの colors を使い、シート背景(colors.background)と調和させる。
        val themeChipColors = FilterChipDefaults.filterChipColors(
            labelColor = colors.text,
            selectedContainerColor = colors.accent,
            selectedLabelColor = colors.background,
        )
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
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.roundToInt()) },
                // ドラッグ確定時に一度だけ永続化（ドラッグ中の毎値 prefs 書き込みを避ける）
                onValueChangeFinished = onFontSizePersist,
                valueRange = 14f..24f,
                // steps = 9 で 14〜24sp を 1sp 刻みの離散値にする（中間刻み = 範囲幅 - 1）
                steps = 9,
                colors = sliderColors,
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
            Slider(
                value = lineHeightEm,
                // 0.1em 刻みに丸める。前行とのルビ被りを避けるため狭めレンジ(2.3〜2.8)に固定。
                onValueChange = { onLineHeightChange((it * 10).roundToInt() / 10f) },
                // ドラッグ確定時に一度だけ永続化
                onValueChangeFinished = onLineHeightPersist,
                valueRange = 2.3f..2.8f,
                // steps = 4 で 2.3〜2.8em を 0.1em 刻みの離散値にする（中間刻み = 区切り数 - 1）
                steps = 4,
                colors = sliderColors,
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
            Slider(
                value = bodyMarginDp.toFloat(),
                onValueChange = { onBodyMarginChange(it.roundToInt()) },
                // ドラッグ確定時に一度だけ永続化
                onValueChangeFinished = onBodyMarginPersist,
                // 10〜40dp を 5dp 刻み（steps=5）。既定 15 がグリッドに乗るレンジ設計
                valueRange = 10f..40f,
                steps = 5,
                colors = sliderColors,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.S12),
            )
            Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
        }
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
