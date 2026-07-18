package com.novelreader.ui.skins.p

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.InkSoftCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.Spacing

// ============================================================
// スキンP「カセット」の画面横断で共有する筐体部品（本棚/一覧/目次/発見/読書＝package p で共用）。
// 各 P 画面ファイルが同名で持っていた最小複製（PixelFamily・drawLcdDots・Deck 系）を internal へ集約する
// dedup 先（ADR 0022 §1 の構造分岐で増えた P 画面の重複を1本化）。値・見た目は複製元と完全等価。
// ※ SegGauge（伸長型セグゲージ）は本棚Pのみが使う専用プリミティブ＝複製が無いため BookshelfCartridgeP に private のまま残す。
// ============================================================

// P の pixel 記号チャンネル（--pixel: ui-monospace 系）。7セグ/STAGE/CLEAR/SCORE 等の英数 HUD・話数に使う。
internal val PixelFamily = FontFamily.Monospace

// ============================================================
// 機体下端の意匠（.deck＝通気孔＋銘板）＝固定フッタ
// ============================================================
@Composable
internal fun Deck() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S8, bottom = Spacing.S12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckHoles()
        Text(
            "POCKET NOVEL · COLOR",
            fontFamily = PixelFamily,
            fontSize = 9.sp,                  // .deck .mk 9px
            letterSpacing = 0.18.em,
            color = InkSoftCartridge,
        )
        DeckHoles()
    }
}

/** 通気孔（.deck .holes＝小さな凹み5個）。 */
@Composable
private fun DeckHoles() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        repeat(5) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(PlasticLoCartridge))
        }
    }
}

/**
 * 液晶のドットマトリクス地（.lcd::before / .hud::before ＝3px 間隔の微ドット・署名①）。
 * ドット色は面ごとに α が僅かに異なる（本棚 .16／目次 .15）ため呼び出し側が渡す＝色以外は全面共有。
 */
internal fun DrawScope.drawLcdDots(dotColor: Color) {
    val step = 3.dp.toPx()
    val r = 0.6.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(dotColor, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
