package com.novelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import java.util.Locale
import kotlin.math.roundToInt

/** 表示設定ボトムシート（テーマ切替・文字サイズ）。色は読書テーマ（colors）に追従させる */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsSheet(
    colors: ReadingColors,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    bodyMarginDp: Int,
    onBodyMarginChange: (Int) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "表示設定",
                style = MaterialTheme.typography.titleMedium,
                // モック settings-D .sheet h2: 明朝・weight600・字間.08em
                fontFamily = MinchoFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "テーマ",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // values() を使うのは Kotlin バージョン非依存のため（entries は 1.9+）
                ReadingTheme.values().forEach { theme ->
                    FilterChip(
                        selected = readingTheme == theme,
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
                        // 選択色をアクセント(朱)に統一する。
                        // M3 既定だと secondaryContainer（青鼠）になりアプリの主役色から外れるため。
                        // システムテーマではなく読書テーマの colors を使い、シート背景(colors.background)と調和させる。
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = colors.text,
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.background,
                        ),
                    )
                }
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
            Spacer(Modifier.height(24.dp))
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
                    valueRange = 14f..24f,
                    // steps = 9 で 14〜24sp を 1sp 刻みの離散値にする（中間刻み = 範囲幅 - 1）
                    steps = 9,
                    colors = sliderColors,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text("あ", fontSize = 24.sp, fontFamily = MinchoFamily)
            }
            Spacer(Modifier.height(24.dp))
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
                    valueRange = 2.3f..2.8f,
                    // steps = 4 で 2.3〜2.8em を 0.1em 刻みの離散値にする（中間刻み = 区切り数 - 1）
                    steps = 4,
                    colors = sliderColors,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
            }
            Spacer(Modifier.height(24.dp))
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
                    // 10〜40dp を 5dp 刻み（steps=5）。既定 15 がグリッドに乗るレンジ設計
                    valueRange = 10f..40f,
                    steps = 5,
                    colors = sliderColors,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text("広", style = MaterialTheme.typography.labelMedium, fontFamily = MinchoFamily)
            }
        }
    }
}
