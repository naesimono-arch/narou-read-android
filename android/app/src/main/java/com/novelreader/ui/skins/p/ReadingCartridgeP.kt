package com.novelreader.ui.skins.p

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.BlueCartridge
import com.novelreader.ui.theme.BlueInkCartridge
import com.novelreader.ui.theme.BlueInkDarkCartridge
import com.novelreader.ui.theme.LcdCartridge
import com.novelreader.ui.theme.LcdHiCartridge
import com.novelreader.ui.theme.LcdInkCartridge
import com.novelreader.ui.theme.LineCartridge
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.PlasticCartridge
import com.novelreader.ui.theme.RedCartridge
import com.novelreader.ui.theme.RedLoCartridge
import com.novelreader.ui.theme.SheetCartridge
import com.novelreader.ui.theme.SheetLoCartridge
import com.novelreader.ui.theme.PlasticHiCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing
import kotlin.math.roundToInt

// ============================================================
// スキンP「カートリッジ」の読書クローム部品＋設定シート部品（正本 reading-P.html / settings-P.html・
// ADR 0022 §1「共通骨格＋スキン別部品」）。M の ReadingChromeM.kt がこの構成の見本。
//
// 本文エンジン（組版・ページング・章送り・状態機械・タップトグル・スワイプ覗き）は共有のまま。
// P で替わるのはクローム部品のみ:
//   ①上端の緑LCDセーブバー（没入中の唯一常設クローム＝随伴・一瞥・静けさ／reading-P .savebar）
//   ②クローム表示時 HUD の緑LCDセーブチップ（reading-P .hud .save）
//   ③章扉（ピクセル話数＋明朝題＋緑LCDの3点ルール／.chap-h）
//   ④シーン区切り（--rd-soft の破線バー／hr）
//   ⑤設定シート＝機体のシステムメニュー（プラ筐体面・緑LCDヘッダ・プラつまみ／settings-P）
// 本文組版の規格層（明朝・ルビ・和文 letterSpacing 0）は不可侵＝一切触らない（本文は主役＝ゲーム画面）。
//
// タイポ裁定（本棚Pの先例 BookshelfCartridgeP.kt 冒頭に同旨）: P は英数HUD＝monospace（--pixel）・
//   UI文言＝ゴシック（既定サンセリフ）・本文/章題＝明朝（--mincho＝規格層で不変）。M の明朝題字とは別系。
//
// モーション（ADR 0022 §3）: reading-P に keyframes/transition/JS は無い＝P 固有クロームは全て静止。
//   出没（セーブバーのフェード）はクローム表示状態＝共有機構の collapsedFraction に従うだけで、P 独自の
//   アニメは新設しない（reduce-motion 下でも共有トグルの spring に従い、静止画としての P 意匠は不動）。
//
// 色は Color.kt の Cartridge val のみ（直書き hex 禁止）。settings-P の --sheet #e4e0d3 / gradient 下端
//   #dcd8ca は Color.kt に未登録＝該当箇所に「TODO: 監督補充」を付し、近似で確定させず暫定プラ面で描画する。
// ============================================================

// PixelFamily は package 共有部品へ集約（CartridgePartsP.kt の internal val）＝当ファイルからは参照のみ。

/**
 * 上端の緑LCDセーブバー（reading-P .savebar）。没入中の唯一常設クローム＝「随伴・一瞥・静けさ」。
 * SAVE ラベル＋セグメント読み取りゲージ＋% を緑LCD面（テーマ不変）に載せる。
 * fraction は本全体の進捗（現在話/全話数）。クローム表示時は HUD（共有 TopAppBar＋SaveChipP）が代わりに
 * 出るため、呼び出し側で alpha=collapsedFraction を掛けて没入時のみ見せる（M のゴースト題字と同型）。
 */
@Composable
fun ReadingSaveBarP(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    val pct = (f * 100).roundToInt()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // .savebar background: linear-gradient(--lcd-hi,--lcd)（緑LCD面・テーマ不変）
            .background(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
            // padding 5px 13px → 離散スケール最近傍（縦 S4/横 S12）
            .padding(horizontal = Spacing.S12, vertical = Spacing.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // gap 9px → S8
    ) {
        Text(
            text = "SAVE",
            fontFamily = PixelFamily,
            fontSize = 9.sp,                         // .savebar .lb 9px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
            color = LcdInkCartridge.copy(alpha = 0.85f), // .lb opacity .85
        )
        // ゲージ（.savebar .g）＝地 --lcd-ink α.16／フィル＝セグメント（4px 点灯・1px 消灯の反復）。
        Canvas(modifier = Modifier.weight(1f).height(6.dp)) { // .g height 6px
            drawRect(LcdInkCartridge.copy(alpha = 0.16f))     // .g background rgba(43,54,22,.16)=--lcd-ink α.16
            val seg = 4.dp.toPx()                             // repeating 0..4px 点灯
            val gap = 1.dp.toPx()                             // 4..5px 消灯（薄ヘアラインで区切る）
            val fillW = size.width * f
            var x = 0f
            while (x < fillW) {
                drawRect(
                    LcdInkCartridge,
                    topLeft = Offset(x, 0f),
                    size = Size(seg.coerceAtMost(fillW - x), size.height),
                )
                x += seg + gap
            }
        }
        Text(
            text = "$pct%",
            fontFamily = PixelFamily,
            fontSize = 10.sp,                        // .savebar .pc 10px
            fontWeight = FontWeight.Bold,
            color = LcdInkCartridge,
        )
    }
}

/**
 * クローム表示時 HUD の緑LCDセーブチップ（reading-P .hud .save）。共有 TopAppBar の actions に載せる。
 * ピクセル数字「N/全数 · %」を緑LCD面に囲む（テーマ不変＝P の署名）。
 */
@Composable
fun SaveChipP(chapterNumber: Int, totalChapters: Int, fraction: Float, modifier: Modifier = Modifier) {
    val pct = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
    Text(
        text = "$chapterNumber/$totalChapters · $pct%",
        fontFamily = PixelFamily,
        fontSize = 11.sp,                            // .save .n 11px
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.03.em,
        color = LcdInkCartridge,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))          // .save border-radius 5px
            .background(LcdCartridge)                // .save background --lcd
            .border(1.dp, LcdInkCartridge.copy(alpha = 0.25f), RoundedCornerShape(5.dp)) // inset 0 0 0 1px rgba(43,54,22,.25)
            .padding(horizontal = Spacing.S8, vertical = Spacing.S4), // .save padding 4px 8px → S4/S8
    )
}

/**
 * 遊び心P3（連続プレイの炎）: 連続読書日数のピクセル炎コンボ（reading-P .streak/.flame）。HUD 右端＝
 * クローム表示時のみ・テーマ不変（署名の退色レッドに閉じる）。日数ぶんに育ち、途切れると1ドットの種火に戻る。
 *
 * 描画（モック実値の写経）: 5本のピクセル柱（幅2.5dp・gap1.5dp・下揃え・器高16dp）。通常の高さ [5,9,16,9,5]、
 *   中央柱のみ red-lo→red の縦グラデ、他は red。種火（streakDays<=1）は両脇 [3,3,_,3,3] を α.4 で沈め中央を7dp。
 *   数字「N日」は pixel 11sp/bold/red-lo/letter-spacing .02em。
 *
 * TODO(監督/データ源＋配線): 連続読書日数（readingStreakDays）を保持するデータ源がアプリに存在しない
 *   （DB/Repository/prefs を調査＝該当なし）。ゆえに本部品は「部品と描画のみ用意し未配線・非表示」とし、
 *   ダミー数値は出さない（捏造禁止）。将来 DataStore 等に「最終読書日＋連続日数」を実装し、当日初回の HUD 表示で
 *   prevDays<days の時だけ「＋1」バーストを一度再生する配線を、共有骨格（NativeReadingScreen の HUD）側へ足す
 *   （本ファイルは HUD へ配線できない編集境界のため呼び出しは骨格側の責務）。バースト（flameUp .4s・pop）は
 *   その配線時に Motion.kt スロット＋reduce-motion 静止で足す（静止画としての現在値の炎は本描画がそのまま結果状態）。
 */
@Composable
fun StreakFlameP(streakDays: Int, modifier: Modifier = Modifier) {
    val isSeed = streakDays <= 1  // .streak.seed＝途切れ後の種火（1ドットへ戻った状態）
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))          // .streak border-radius 5px
            .background(PlasticHiCartridge)          // .streak background --plastic-hi
            .border(1.dp, LineCartridge, RoundedCornerShape(5.dp)) // inset 0 0 0 1px var(--line)
            .padding(start = Spacing.S8, end = Spacing.S8, top = Spacing.S4, bottom = Spacing.S4), // padding 4px 8px 4px 6px→近傍離散
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // gap 5px→S4
    ) {
        Canvas(modifier = Modifier.size(width = 19.dp, height = 16.dp)) { // 5柱×2.5+4×gap1.5≒18.5・.flame height16px
            val barW = 2.5.dp.toPx()
            val gap = 1.5.dp.toPx()
            // 高さ（下揃え）。通常 [5,9,16,9,5]／種火 [3,3,7,3,3]。単位 dp→px。
            val hs = if (isSeed) listOf(3f, 3f, 7f, 3f, 3f) else listOf(5f, 9f, 16f, 9f, 5f)
            hs.forEachIndexed { i, hDp ->
                val h = hDp.dp.toPx()
                val x = i * (barW + gap)
                val top = size.height - h
                // 種火の両脇柱は α.4 で沈める（.streak.seed の nth-child 1/2/4/5）。
                val dim = isSeed && i != 2
                if (i == 2) {
                    // 中央柱＝red-lo(上)→red(下) のグラデ（.flame i:nth-child(3)）。
                    drawRect(
                        Brush.verticalGradient(listOf(RedLoCartridge, RedCartridge), startY = top, endY = size.height),
                        topLeft = Offset(x, top), size = Size(barW, h),
                    )
                } else {
                    drawRect(
                        if (dim) RedCartridge.copy(alpha = 0.4f) else RedCartridge, // .flame i background --red
                        topLeft = Offset(x, top), size = Size(barW, h),
                    )
                }
            }
        }
        Text(
            text = "${streakDays}日",
            fontFamily = PixelFamily,
            fontSize = 11.sp,                        // .streak .d 11px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.02.em,                 // .d letter-spacing .02em
            color = RedLoCartridge,                  // .d color --red-lo
        )
    }
}

/**
 * 章扉（reading-P .chap-h）＝ピクセルの話数「第N話 ／ 全M話」＋明朝の章題＋緑LCDの3点ルール。
 * 章題のサイズはユーザーの文字サイズ設定へ追従（D/M の ChapterHeader と同じ規格層追従＝本文＋2sp）。
 */
@Composable
fun ChapterHeaderP(
    title: String,
    chapterNumber: Int?,
    totalChapters: Int?,
    colors: ReadingColors,
    fontSize: Int,
    bodyMarginDp: Int,
    bodyMaxWidth: Dp,
) {
    // 話数の色 --rd-num は LIGHT/SEPIA=--blue-ink（#3f5a70）・DARK のみ明化（#8fb3cd）。
    // なぜ colors.isLight で分けるか: SEPIA も明面（isLight=true）で blue-ink 続投・DARK だけ暗面で明化＝
    // isLight が light/sepia と dark を正しく二分するため、theme enum を持ち込まずに再現できる。
    val numColor = if (colors.isLight) BlueInkCartridge else BlueInkDarkCartridge
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = bodyMaxWidth)             // 本文と同じ版面幅に揃える（広幅端末）
            .padding(horizontal = bodyMarginDp.dp)
            .padding(top = Spacing.S8, bottom = Spacing.S24), // .chap-h margin 6px..26px → S8/S24
    ) {
        if (chapterNumber != null) {
            Text(
                text = if (totalChapters != null) "第${chapterNumber}話 ／ 全${totalChapters}話" else "第${chapterNumber}話",
                fontFamily = PixelFamily,
                fontSize = 12.sp,                    // .chap-h .num 12px
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                color = numColor,
            )
            Spacer(Modifier.height(Spacing.S12))     // .t margin-top 10px → S12
        }
        Text(
            text = title,
            fontFamily = MinchoFamily,               // 本文組版＝明朝（規格層・不変）
            fontSize = (fontSize + 2).sp,            // 規格層追従（D/M と同じ「本文＋2」）
            fontWeight = FontWeight.SemiBold,        // .t font-weight 600
            lineHeight = 1.6.em,                     // .t line-height 1.6
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.S16))         // .rule margin-top 16px → S16
        // ルール＝緑LCDの3点（.rule i: 6px 四方・--lcd・inset 1px rgba(43,54,22,.25)）。左寄せ＝モック flex 既定。
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) { // gap 3px → S4
            repeat(3) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(colors.rule)     // reading-P .rule i=--lcd（＝accent/rule・テーマ不変）
                        .border(1.dp, LcdInkCartridge.copy(alpha = 0.25f)), // inset 0 0 0 1px rgba(43,54,22,.25)
                )
            }
        }
    }
}

/**
 * シーン区切り（reading-P hr）＝--rd-soft の破線バー（4px 点灯・4px 消灯・opacity .5・幅 60px・高 6px）。
 * colors.hr は破線を単色へ焼き込んだ代表値だが、モック hr は破線リズムそのものが意匠のため、実 alpha の
 * 破線で再現する。素材色は --rd-soft＝colors.textSecondary（読書面に追従＝テーマ変種）。
 */
@Composable
fun SceneDividerP(colors: ReadingColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.S16), // hr margin 16px → S16
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(60.dp).height(6.dp)) { // hr width 60px / height 6px
            val h = size.height
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.5f), // --rd-soft opacity .5
                start = Offset(0f, h / 2f),
                end = Offset(size.width, h / 2f),
                strokeWidth = h,                                 // 6px 高の帯を破線で刻む
                cap = StrokeCap.Butt,
                // 4px 点灯・4px 消灯（repeating-linear-gradient 0..4px soft / 4..8px transparent）
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
            )
        }
    }
}

// ============================================================
// 表示設定シート（settings-P）の P 部品＝機体のシステムメニューの意匠（ADR 0022 §1）。
// シートのロジック（テーマ3択・スライダー・永続化）は共有のまま、意匠だけをここで供給する。
// P は3テーマ＝テーマ選択は supportedThemes 駆動の標準3択（M の固定表示行とは別＝壊さない）。
// ============================================================

/** シート面のグラデ（settings-P .sheet: linear-gradient(#e4e0d3,#dcd8ca)＝中央トークン化済み）。 */
val CartridgeSheetBrush: Brush
    get() = Brush.verticalGradient(listOf(SheetCartridge, SheetLoCartridge))

/** シート下端色（ModalBottomSheet の containerColor 用＝グラデ終点と揃える）。 */
val CartridgeSheetBottom = SheetLoCartridge

/**
 * システムメニューヘッダ（settings-P .sysbar）＝緑LCDの起動画面感バー「● POCKET NOVEL」。
 * M の見出し脇小星座片（SettingsHeaderFragM）に対応する P のヘッダ意匠。テーマ不変（緑LCD＝署名）。
 */
@Composable
fun SettingsSysBarP(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))          // .sysbar border-radius 7px
            .background(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge))) // linear-gradient(--lcd-hi,--lcd)
            .border(1.dp, LcdInkCartridge.copy(alpha = 0.25f), RoundedCornerShape(7.dp)) // inset 0 0 0 1px rgba(43,54,22,.25)
            .padding(horizontal = Spacing.S12, vertical = Spacing.S8), // padding 8px 12px → S8/S12
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // gap 8px
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(LcdInkCartridge)) // .dot 7px --lcd-ink
        Text(
            text = "POCKET NOVEL",
            fontFamily = PixelFamily,
            fontSize = 10.sp,                        // .sysbar 10px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
            color = LcdInkCartridge,
        )
    }
}

/** シートのグラブハンドル（settings-P .grab: 38×5・--plastic-lo）。既定ハンドルはシート側で消している。 */
@Composable
fun CartridgeGrab(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(38.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))          // .grab border-radius 3px
            .background(PlasticLoCartridge),
    )
}

/**
 * スライダーつまみ＝プラスチックのノブ（settings-P .track .knob: 22×22・角丸6px・
 * radial-gradient(35% 30%, --plastic-hi, --plastic-lo)）。
 */
@Composable
fun CartridgeSliderThumb(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {        // .knob 22×22
        // radial-gradient(circle at 35% 30%, plastic-hi, plastic-lo)
        val brush = Brush.radialGradient(
            colors = listOf(PlasticHiCartridge, PlasticLoCartridge),
            center = Offset(size.width * 0.35f, size.height * 0.30f),
            radius = size.maxDimension * 0.85f,
        )
        val r = 6.dp.toPx()                          // border-radius 6px
        drawRoundRect(brush, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
    }
}

/**
 * スライダートラック（settings-P .track: 地=--plastic-lo・フィル=--blue の値トラック・高 5px・角丸 3px）。
 * fraction は呼び出し側で (value-min)/(max-min) を計算して渡す（SliderState の内部 API へ依存しない）。
 */
@Composable
fun CartridgeSliderTrack(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(5.dp)) { // .track height 5px
        val r = 3.dp.toPx()                          // border-radius 3px
        val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
        drawRoundRect(PlasticLoCartridge, cornerRadius = cr)             // .track background --plastic-lo
        if (f > 0f) {
            drawRoundRect(
                BlueCartridge,                                          // .fill background --blue
                size = Size(size.width * f, size.height),
                cornerRadius = cr,
            )
        }
    }
}
