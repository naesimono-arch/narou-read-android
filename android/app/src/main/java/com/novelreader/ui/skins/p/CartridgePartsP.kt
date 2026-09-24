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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
// fontScale への追従規則（2026-09-07 裁定・P 圏の共通ルール）
//
// 2026-09-03 の lineHeight 掃討は「版面の正は dp 側が持つ」として P の器を `Modifier.height()` で dp 固定した。
// 目的（幽霊行箱で版面が間延びしない）は正しかったが、**dp は fontScale に追従しない**ため字面だけが sp で
// 伸びて器から出る。実測（Robolectric NATIVE・xhdpi・360dp 幅・fontScale 2.0）で didOverflowHeight=true:
//   .savebar 22dp ← 「42%」23.5dp ／ .save 21dp ← 「127/340 · 42%」26.0dp ／ .streak 24dp ← 「12日」
//   ／ 目次 .row 66dp ← 章題（2行→1行に潰れた上で切れる）
// ＝いずれも**字面が切られていた**（実機 PGEM10・fontScale 2.0 で再現）。
//
// 一方で「見出しが分断される」「銘板の語が割れる」の2件は **dp 固定とは無関係**だった（真因が別）。
// PixelFamily の英字刻印は letterSpacing が広く、fontScale 2.0 で1行の実測幅が 281.5dp（CARTRIDGE LIBRARY）
// ／271dp+（POCKET NOVEL · COLOR）に達し、残り幅を超えて**折り返した**のが正体＝縦ではなく横の問題。
//
// そこで P では次の2つを使い分ける。どちらでもない要素（純粋な dp 図形＝通気孔・リブ・ゲージ・ノード）は
// dp のまま＝意匠が高さ／幅を決めており、字面に追従する理由が無い。
// ============================================================

/**
 * **字面が高さを決める器**の高さ。[base] には fontScale 1.0 の版面（正本モックの px 算術＝
 * 上下 padding ＋ 子の最大行箱）をそのまま渡す。
 *
 * なぜ `heightIn(min = base)` でなく比例追従か: heightIn なら溢れは避けられるが、器の高さの決定権が
 * フォントの自然行高へ移る。P の等幅は正本 `--pixel`＝Consolas に対し Compose では Droid Sans Mono へ
 * 解決される別フォントで、しかも `lineHeight` は下限としてしか効かない
 * （`docs/knowledge/compose-lineheight-is-a-floor-not-css-line-height.md`）。つまり **fontScale 1.0 の
 * 版面が実装から読めなくなり、字面や行送りを触るたび静かに動く**＝掃討の目的がそこで薄まる。
 *
 * 比例追従なら両立する:
 *  ・F=1.0 では `base` そのもの＝掃討の成果は 1dp も損なわれない（既存 golden も不変）。
 *  ・F>1 では器も字面も同率で伸びるので必ず収まる。[base] は「伸びない padding ＋ 伸びる行箱」の和なので
 *    padding ぶん（.savebar なら 10dp）が丸ごと余裕になり、lineHeight が下限として押し戻すぶん
 *    （実測 0.1〜0.2dp）を吸収できる。
 *  ・F<1 へは**縮めない**（[coerceAtLeast]）。縮めると伸びない padding のぶんだけ器が足りなくなる。
 */
@Composable
internal fun cartridgeBoxHeight(base: Dp): Dp =
    base * LocalDensity.current.fontScale.coerceAtLeast(1f)

/**
 * **筐体の刻印**（PixelFamily の英字銘板）の文字サイズ。[size] の dp で描かれ、fontScale では伸びない。
 *
 * 線引き（どれが刻印か）: 隣接する dp 図形と一体で幾何を作り、かつ**情報を持たない**英字ラベルだけ。
 * 現状の適用先は2つで、どちらも fontScale 2.0 で実際に折り返して語が割れた:
 *  ・`.deck .mk`「POCKET NOVEL · COLOR」＝両脇の 5dp 通気孔と1行を成す機体銘板（＝成形文字）。
 *  ・`.lib-h .t`「CARTRIDGE LIBRARY」＝ラック見出しの英字チャンネル。同じ行の冊数「NN 本」は
 *    **情報なので伸ばしたまま**にし、刻印だけを止めて1行を維持する。
 *
 * 逆に、単独で伸びても行が割れない刻印（`.savebar` の SAVE・`.sysbar` の POCKET NOVEL・LCD の
 * NOW PLAYING / STAGE 等）は**伸ばしたまま**にしてある。固定は拡大の恩恵を捨てる代償を伴うので、
 * 幾何が実際に壊れる箇所にだけ払う。
 */
@Composable
internal fun engravedSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

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
            // 刻印＝両脇の 5dp 通気孔と一体の銘板なので fontScale で伸ばさない（→ engravedSp の KDoc）。
            // 伸ばすと fontScale 2.0 で1行の実測幅が残り幅を超え、「POCKET NOVEL ·」「COLOR」に割れて
            // 右の通気孔が押し出されていた（実機 PGEM10 で再現）。値そのものは正本 .deck .mk 9px のまま。
            fontSize = engravedSp(9.dp),
            letterSpacing = 0.18.em,
            color = InkSoftCartridge,
            maxLines = 1,                     // 銘板は成形文字＝折り返さない（万一入らなければ切る方が正）
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
