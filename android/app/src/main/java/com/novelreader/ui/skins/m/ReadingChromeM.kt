package com.novelreader.ui.skins.m

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.BaseSeizu
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu

// ============================================================
// スキンM「星図」の読書クローム部品（正本 reading-M.html・ADR 0022 §1「共通骨格＋スキン別部品」）。
//
// 本文エンジン（組版・ページング・章送り・状態機械）は共有のまま、M で替わるのは:
//   ①上端の結線進捗（ほぼ唯一の常設クローム）②章扉（星座片＋漢数字話数＋星線ルール）
//   ③シーン区切り（線-星点-線）④地の星屑（極淡・本文の下層）⑤没入時のゴースト題字行
// 読書 M はモーションゼロ（モックの意図＝読書の静けさ。先端星も静止・ADR 0022 §3）。
// 本文組版の規格層（明朝・ルビ・和文 letterSpacing 0）は不可侵＝一切触らない。
// ============================================================

/**
 * 上端 2dp の結線進捗（reading-M .prog）。読んだ分だけ金の線が伸び、先端に静止の星。
 * fraction は本全体の進捗（現在章/全章数）。トラックは月光スレート .18・線は金＋グロー近似。
 */
@Composable
fun ReadingProgressStarM(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        val d = 1.dp.toPx()
        // トラック（rgba(150,168,214,.18)）
        drawRect(MoonSlateSeizu.copy(alpha = 0.18f))
        val tipX = size.width * f
        // 金の結線（box-shadow 0 0 6px の代替＝太い淡金を下に敷く2層近似）
        drawLine(StarSeizu.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(tipX, size.height / 2), strokeWidth = 5 * d)
        drawLine(StarSeizu, Offset(0f, size.height / 2), Offset(tipX, size.height / 2), strokeWidth = 2 * d)
        // 【R4/R1 節度】進捗＝星を結ぶ弧: 先端星から淡い星 pip を尾（読了側）へ連ねる（.tip の box-shadow
        // -34px/-78px/-140px の写像。静的な点＝モーションなし）。左端を越える pip は描かない。
        val arcPips = listOf(34f to 0.42f, 78f to 0.34f, 140f to 0.26f) // (先端からの左オフセットdp, α)
        for ((dx, a) in arcPips) {
            val px = tipX - dx * d
            if (px < 0f) continue
            drawCircle(StarSeizu.copy(alpha = a), radius = 1.3f * d, center = Offset(px, size.height / 2))
        }
        // 先端の静止星（.tip 5px 白金＋グロー rgba(233,221,180,.9)。読書中は脈動しない＝静けさ）
        drawCircle(
            Brush.radialGradient(
                0f to StarSeizu.copy(alpha = 0.9f), 1f to StarSeizu.copy(alpha = 0f),
                center = Offset(tipX, size.height / 2), radius = 7 * d,
            ),
            radius = 7 * d, center = Offset(tipX, size.height / 2),
        )
        drawCircle(StarCoreSeizu, radius = 2.5f * d, center = Offset(tipX, size.height / 2))
    }
}

// ============================================================
// 透過の天の川スクリム（reading-M-rich-R4「透過の天の川（R1s実物）」の「強度・中」を採用・2026-07-19 ユーザー裁定）。
//
// 読書M表示中は常駐 backdrop（SkyBackdropM＝R1s 実物の天の川そのもの）を透かして見せ、読書面は「地色スクリムで
// 空を減光」する。旧・読書専用の別描画の夜空（drawSeizuReadingSky）は廃す＝アプリの統一空とピクセル同一の天の川が
// 読書用に淡く沈むだけ（同じ空の連続性・空の一枚化）。読書面自体は透明にし（Scaffold containerColor=Transparent）、
// その上へ地色 BaseSeizu を α で被せて空を沈める。
//
// モック R4（中）同期値: milkyway canvas を描画後 globalAlpha ×0.55 で透かし、本文帯を destination-out 0.40（＝×0.6）で
//   保護減衰する。等価な減算合成へ翻訳＝backdrop はフル強度で見えるので地色を α で被せて沈める:
//     全面スクリム α = 1−0.55 = 0.45 → 空を実効×0.55。
//     本文帯スクリム α = 0.40 を追加 → その帯は ×0.55×(1−0.40) = ×0.33（＝×0.55×0.6・destination-out 0.40 と体感等価）。
//   地色 BaseSeizu(#0B1330) は L≈0.008 とほぼ黒＝加算される地色成分は無視でき、実効は上式どおり空の輝度を縮小する。
// 可読性（モック R4 の計算をそのまま引用）: 帯芯の平均最悪ケースでも 中 s=0.55 で コントラスト≈10.3:1（AAA 通常 7:1 超）。
//   本文 #DCE3F2／地 #0B1330 素コントラスト≈13:1。ルビ #9AA4C0 ≈6.9:1。
// ============================================================
private const val SeizuSkyScrimAlpha = 0.45f         // 全面＝実効×0.55（1−0.55。R4 描画後 α×0.55 と等価）
private const val SeizuBodyScrimAlpha = 0.40f        // 本文帯の追加減衰（×0.6＝R4 本文帯保護 destination-out 0.40 と等価）
// 本文帯（reader）の縦域＝モック fillRect(0,120,W,660)／H=844 の比率。列端（上下）はグラデで軟らかくぼかす（R4 の 0.12/0.88 停止）。
private const val SeizuBodyBandTop = 120f / 844f     // ≈0.142（reader 上端）
private const val SeizuBodyBandBottom = 780f / 844f  // ≈0.924（reader 下端）
private const val SeizuBodyBandFadeIn = 0.12f        // 帯内で α が立ち上がる割合
private const val SeizuBodyBandFadeOut = 0.88f       // 帯内で α が落ち始める割合

/**
 * 透過の天の川スクリム（reading-M-rich-R4「中」）。読書M表示中に常駐 backdrop を透かすための地色減光を、本文列
 * 下層へ静的に一度だけ敷く（スワイプ追従は本文側の translationX のみ＝地は動かない）。M 以外では呼ばれない。
 * 縦横共通＝帯は screen 高で決まり orientation 非依存（縦書きでも重なりを新造しない）。モーションゼロ（描画一度きり）。
 */
fun DrawScope.drawSeizuReadingScrim() {
    // 全面スクリム＝空を実効×0.55 へ沈める（地色トークンの α 掛け）。
    drawRect(BaseSeizu.copy(alpha = SeizuSkyScrimAlpha))
    // 本文帯の追加スクリム＝帯芯が本文に重なる最悪ケースの余裕（×0.6）。上下端はグラデでぼかす（列端＝R4）。
    val top = size.height * SeizuBodyBandTop
    val bottom = size.height * SeizuBodyBandBottom
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            SeizuBodyBandFadeIn to BaseSeizu.copy(alpha = SeizuBodyScrimAlpha),
            SeizuBodyBandFadeOut to BaseSeizu.copy(alpha = SeizuBodyScrimAlpha),
            1f to Color.Transparent,
            startY = top, endY = bottom,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
    )
}

/**
 * 章扉（reading-M .chap-h）＝星座片＋漢数字の話数＋章題＋星線ルール。
 * 章題のサイズはユーザーの文字サイズ設定へ追従（D の ChapterHeader と同じ規格層追従）。
 */
@Composable
fun ChapterHeaderM(
    title: String,
    chapterNumber: Int?,
    colors: ReadingColors,
    fontSize: Int,
    bodyMarginDp: Int,
    bodyMaxWidth: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = bodyMarginDp.dp)
            .padding(top = Spacing.S16, bottom = Spacing.S24),
    ) {
        // 章扉の星座片（モックの静的 SVG 58×30 を DrawScope へ写像。線 .55・星4点の金）。
        Canvas(modifier = Modifier.size(58.dp, 30.dp).align(Alignment.CenterHorizontally)) {
            val sx = size.width / 58f
            val sy = size.height / 30f
            val line = Path().apply {
                moveTo(7 * sx, 22 * sy); lineTo(23 * sx, 11 * sy); lineTo(43 * sx, 17 * sy); lineTo(51 * sx, 7 * sy)
            }
            drawPath(line, StarSeizu.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))
            drawCircle(StarSeizu, 1.5f * sx, Offset(7 * sx, 22 * sy))
            drawCircle(StarSeizu, 2.1f * sx, Offset(23 * sx, 11 * sy))
            drawCircle(StarSeizu, 1.5f * sx, Offset(43 * sx, 17 * sy))
            drawCircle(StarSeizu, 2.4f * sx, Offset(51 * sx, 7 * sy))
        }
        if (chapterNumber != null) {
            Spacer(Modifier.height(Spacing.S12))
            Text(
                text = "第 ${kanjiNumber(chapterNumber)} 話",
                fontSize = 11.sp,              // .num 11px（ゴシック・字間 .32em・星光の金）
                letterSpacing = 0.32.em,
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.S12))
        Text(
            text = title,
            fontFamily = MinchoFamily,
            fontSize = (fontSize + 2).sp,      // 規格層追従（D と同じ「本文＋2」）
            fontWeight = FontWeight.Medium,    // .t font-weight 500
            lineHeight = 1.62.em,              // .t line-height
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.S16))
        // 星線ルール（.rule）＝【R4/R1 節度】結線風。左寄せ＝モックの flex 既定に忠実。
        // 線＝淡い弧のグラデ（transparent→星 30%→星・全体 α.7＝linear-gradient(90deg,transparent,--star 30%,--star)）／
        // 終端の星ノード＝4dp 芯＋微グロー（box-shadow 0 0 6px .75）＋13dp 右の小さな次星（同 .5・一回り小＝-1px spread）。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(40.dp).height(1.dp).background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to colors.accent.copy(alpha = 0.7f),
                        1f to colors.accent.copy(alpha = 0.7f),
                    ),
                ),
            )
            Spacer(Modifier.width(Spacing.S8))
            Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
                val d = 1.dp.toPx()
                val cy = size.height / 2f
                val cx = 3 * d
                drawCircle(
                    Brush.radialGradient(
                        0f to colors.accent.copy(alpha = 0.75f), 1f to colors.accent.copy(alpha = 0f),
                        center = Offset(cx, cy), radius = 6 * d,
                    ),
                    radius = 6 * d, center = Offset(cx, cy),
                )
                drawCircle(colors.accent, radius = 2 * d, center = Offset(cx, cy))
                drawCircle(colors.accent.copy(alpha = 0.5f), radius = 1.5f * d, center = Offset(cx + 13 * d, cy))
            }
        }
    }
}

/**
 * シーン区切り（reading-M .scene）＝線-星点-線。D の中央短線に対する M の翻訳。
 * 線の色は colors.hr（M では --line 合成値）・星点は月光スレート .5＝モック宣言値。
 */
@Composable
fun SceneDividerM(colors: ReadingColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.S16),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 【R4/R1 節度】結線風: 線＝淡い弧のグラデ（両端 transparent・中央 45% で --line＝colors.hr）／
        // 中央星＝月光スレート .5 の芯＋微グロー（box-shadow 0 0 6px .6）。
        val sceneLine = Brush.horizontalGradient(
            0f to Color.Transparent, 0.45f to colors.hr, 1f to Color.Transparent,
        )
        Box(Modifier.width(64.dp).height(1.dp).background(sceneLine))
        Spacer(Modifier.width(Spacing.S12))
        Canvas(Modifier.size(14.dp)) {
            val d = 1.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                Brush.radialGradient(
                    0f to MoonSlateSeizu.copy(alpha = 0.6f), 1f to MoonSlateSeizu.copy(alpha = 0f),
                    center = c, radius = 6 * d,
                ),
                radius = 6 * d, center = c,
            )
            drawCircle(MoonSlateSeizu.copy(alpha = 0.5f), radius = 2 * d, center = c)
        }
        Spacer(Modifier.width(Spacing.S12))
        Box(Modifier.width(64.dp).height(1.dp).background(sceneLine))
    }
}

// ============================================================
// 表示設定シート（settings-M）の M 部品＝観測パネルの意匠（ADR 0022 §1「共通骨格＋スキン別部品」）。
// シートのロジック（テーマ択・スライダー・永続化）は共有のまま、意匠だけをここで供給する。
// ============================================================

/** シート面のグラデ（settings-M .sheet: linear-gradient(180deg,#182034,#111726)）。 */
val SeizuSheetBrush: Brush
    get() = Brush.verticalGradient(listOf(Color(0xFF182034), Color(0xFF111726)))

/** シート下端色（ModalBottomSheet の containerColor 用＝グラデ終点と同値で角丸外周を揃える）。 */
val SeizuSheetBottom = Color(0xFF111726)

/** 見出し脇の小星座片（settings-M h2 の SVG 22×16 を写像。線 .6・星3点の金）。 */
@Composable
fun SettingsHeaderFragM(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp, 16.dp)) {
        val sx = size.width / 22f
        val sy = size.height / 16f
        val line = Path().apply {
            moveTo(3 * sx, 11 * sy); lineTo(10 * sx, 5 * sy); lineTo(19 * sx, 9 * sy)
        }
        drawPath(line, StarSeizu.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))
        drawCircle(StarSeizu, 1.3f * sx, Offset(3 * sx, 11 * sy))
        drawCircle(StarSeizu, 1.9f * sx, Offset(10 * sx, 5 * sy))
        drawCircle(StarSeizu, 1.3f * sx, Offset(19 * sx, 9 * sy))
    }
}

/**
 * テーマ固定表示行（settings-M .theme-fixed）。M は夜の相の1変種＝3択を出す代わりに
 * 「何が装着されているか」と「変種切替の所在＝装いの間」を明示する（機能は消さず所在を示す）。
 */
@Composable
fun ThemeFixedRowM(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MoonSlateSeizu.copy(alpha = 0.22f), RoundedCornerShape(12.dp)) // --line α.22（settings 家系）
            .padding(horizontal = Spacing.S16, vertical = Spacing.S16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 灯る星ドット（.st 6px 金＋グロー）。
        Box(
            Modifier.size(6.dp).drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        0f to StarSeizu, 0.6f to StarSeizu.copy(alpha = 0.7f), 1f to StarSeizu.copy(alpha = 0f),
                        radius = size.minDimension * 1.6f,
                    ),
                    radius = size.minDimension * 1.6f,
                )
                drawCircle(StarSeizu)
            }
        )
        Column(Modifier.padding(start = Spacing.S12)) {
            Text(
                "星図 ・ 夜の相",
                fontFamily = MinchoFamily,
                fontSize = 13.5.sp,            // .tf-b 13.5px
                color = TextSeizu,
            )
            Text(
                "ほかの装いは本棚の「装いの間」から",
                fontSize = 10.5.sp,            // .tf-s 10.5px
                color = DimSeizu,
                modifier = Modifier.padding(top = Spacing.S4),
            )
        }
    }
}

/** スライダーつまみ＝きらめく星（settings-M .knob: 芯 #F7F3E1＋4方向の光条＋グロー）。 */
@Composable
fun SeizuSliderThumb(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val d = 1.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        // グロー（box-shadow 0 0 12px rgba(233,221,180,.9)）
        drawCircle(
            Brush.radialGradient(
                0f to StarSeizu.copy(alpha = 0.9f), 1f to StarSeizu.copy(alpha = 0f),
                center = c, radius = 12 * d,
            ),
            radius = 12 * d, center = c,
        )
        // 4方向の光条（::after の十字グラデ＝縦横 16dp の細線）
        drawLine(StarSeizu.copy(alpha = 0.85f), Offset(c.x, c.y - 8 * d), Offset(c.x, c.y + 8 * d), strokeWidth = 1.2f * d)
        drawLine(StarSeizu.copy(alpha = 0.85f), Offset(c.x - 8 * d, c.y), Offset(c.x + 8 * d, c.y), strokeWidth = 1.2f * d)
        // 芯（::before inset5px ＝ 6dp 円・最輝 #F7F3E1）
        drawCircle(Color(0xFFF7F3E1), radius = 3 * d, center = c)
    }
}

/**
 * スライダートラック＝結線（settings-M .track: 2dp・地=月光スレート .25・fill=金＋グロー）。
 * fraction は呼び出し側で (value-min)/(max-min) を計算して渡す（SliderState の内部 API へ依存しない）。
 */
@Composable
fun SeizuSliderTrack(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        val d = 1.dp.toPx()
        val y = size.height / 2f
        drawLine(MoonSlateSeizu.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2 * d, cap = StrokeCap.Round)
        if (f > 0f) {
            val end = Offset(size.width * f, y)
            drawLine(StarSeizu.copy(alpha = 0.3f), Offset(0f, y), end, strokeWidth = 5 * d, cap = StrokeCap.Round)
            drawLine(StarSeizu, Offset(0f, y), end, strokeWidth = 2 * d, cap = StrokeCap.Round)
        }
    }
}

/**
 * 漢数字変換（1..9999）。章扉の「第 百二十七 話」表示用（reading-M .num の様式）。
 * 位取り記法（一二七でなく百二十七）＝モック表記に一致させる。範囲外・0以下は算用数字へ縮退
 * （表示が壊れるより正確な情報を出す防御）。
 */
fun kanjiNumber(n: Int): String {
    if (n <= 0 || n > 9999) return n.toString()
    val digits = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val units = listOf("", "十", "百", "千")
    val sb = StringBuilder()
    var rest = n
    for (power in 3 downTo 0) {
        val base = intArrayOf(1, 10, 100, 1000)[power]
        val d = rest / base
        rest %= base
        if (d == 0) continue
        // 「一十」「一百」「一千」は「十」「百」「千」と書く（一の位のみ「一」を残す）。
        if (!(d == 1 && power > 0)) sb.append(digits[d])
        sb.append(units[power])
    }
    return sb.toString()
}
