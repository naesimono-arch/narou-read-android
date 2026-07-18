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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.novelreader.ui.theme.DustSeizu
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.SkyCloudSeizu
import com.novelreader.ui.theme.SkyGradEndSeizu
import com.novelreader.ui.theme.SkyGradMidSeizu
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

/**
 * 読書面の地＝夜天グラデ＋極淡の星屑8点＋右上の青雲（reading-M .phone background の写像）。
 * 星屑の座標はモックの radial-gradient 8点（390×844 基準）を画面比率へ正規化した固定値＝
 * 乱数でないのは「本文の下層に置く静的な地」であり毎回同じ夜空であるべきため（モックと同思想）。
 */
fun DrawScope.drawSeizuReadingSky() {
    drawRect(Brush.verticalGradient(0f to BaseSeizu, 0.46f to SkyGradMidSeizu, 1f to SkyGradEndSeizu))
    drawRect(
        Brush.radialGradient(
            colors = listOf(SkyCloudSeizu.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * 0.78f, size.height * 0.06f),
            radius = size.width * 0.70f,
        )
    )
    // (x,y,r,α) = モックの8点（x/390・y/844 の比率、r は px→dp 等倍）
    val dust = listOf(
        floatArrayOf(52f / 390f, 150f / 844f, 1.4f, 0.5f),
        floatArrayOf(320f / 390f, 96f / 844f, 1.0f, 0.4f),
        floatArrayOf(120f / 390f, 300f / 844f, 1.2f, 0.32f),
        floatArrayOf(350f / 390f, 420f / 844f, 1.0f, 0.3f),
        floatArrayOf(40f / 390f, 520f / 844f, 1.3f, 0.34f),
        floatArrayOf(300f / 390f, 640f / 844f, 1.0f, 0.28f),
        floatArrayOf(90f / 390f, 720f / 844f, 1.2f, 0.3f),
        floatArrayOf(250f / 390f, 780f / 844f, 1.0f, 0.26f),
    )
    val d = 1.dp.toPx()
    for ((fx, fy, r, a) in dust) {
        drawCircle(DustSeizu.copy(alpha = a), radius = r * d, center = Offset(size.width * fx, size.height * fy))
    }
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
        // 星線ルール（.rule: 40dp の金線 .7＋4dp の星点）。左寄せ＝モックの flex 既定に忠実。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(40.dp).height(1.dp)
                    .background(colors.accent.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.width(Spacing.S8))
            Box(
                Modifier.size(4.dp).clip(CircleShape)
                    .background(colors.accent),
            )
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
        Box(Modifier.width(64.dp).height(1.dp).background(colors.hr))
        Spacer(Modifier.width(Spacing.S12))
        Box(Modifier.size(4.dp).clip(CircleShape).background(MoonSlateSeizu.copy(alpha = 0.5f)))
        Spacer(Modifier.width(Spacing.S12))
        Box(Modifier.width(64.dp).height(1.dp).background(colors.hr))
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
