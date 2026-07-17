package com.novelreader.ui.skins.m

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.DustSeizu
import com.novelreader.ui.theme.NebulaVioletSeizu
import com.novelreader.ui.theme.SkyCloudSeizu
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarGlowInnerSeizu
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================
// スキンM「星図」本棚の【深空】背景レイヤー（正本 bookshelf-M-rich-R1.html・承認済み R1「深空」）。
//
// R1 の思想: 星野を独立した固定背景（深空）へ昇格＝星雲・粒の天の川・読了星の累積を全画面の「地」とし、
// 星座セルと銘はその上を等速で滑る前景に残す（前景から装飾星＝星屑を抜き軽くする）。
// BookshelfSkyM.kt が本ファイルの部品を z0/z1/z2 の三層で敷く（対応の詳細は呼び出し側コメント）。
//
// 座標系: すべて「正本モックの 390×844(viewport)／390×984(far) の名目座標を 0..1 の割合へ正規化」して持つ。
//   描画時に実 Canvas の size へ乗じる＝端末解像度非依存で帯の形状（芯曲線 bAxis／半幅 bHalf±／密度勾配 bDens）を保つ。
//
// 色: theme/Color.kt の Seizu 節のみ使用（直書き hex 禁止）。モックの一部色は凍結中の token 層で表現できないため
//   系統色へ正規化した（各所にコメント。彩度暴走なし・群青/温白/月光スレートの枠内）。特に:
//   ・天の川の色温度微差 tempCol(冷 rgb206,220,250 → 温 rgb238,232,206) は DustSeizu↔StarGlowInnerSeizu の lerp で写す
//     （両端が知覚下微差で一致＝#D2DEFA / #F0EBCD）。
//   ・第2星雲のモック値 rgb(96,84,150)（微紫）は NebulaVioletSeizu として正本昇格済み（監督裁定＝紫味を残す）。
// ============================================================

// 深空・天の川の粒（正規化座標。fx=x/名目幅, fy=y/名目高, fr=半径/名目幅）。
internal class BandParticle(
    val fx: Float,
    val fy: Float,
    val fr: Float,
    val alpha: Float,
    val color: Color,
    val glowFr: Float, // 輝星の微グロー半径（割合）。0 なら微グローなし。
)

// 帯外の散開微星（正規化座標）。
internal class ScatterStar(val fx: Float, val fy: Float, val fr: Float, val alpha: Float)

// 深空のアクセント星（viewport 名目 390×844 の正規化座標）。
internal class AccentStar(val fx: Float, val fy: Float)

/**
 * 深空フィールド（星雲・天の川粒帯・散開微星・アクセント星）。蔵書に依存しない不変の「地」。
 * finishedStars（読了星の累積）だけは蔵書由来のため別に持たせる（BookshelfSkyM 側で算出）。
 */
internal class DeepSkyField(
    val band: List<BandParticle>,
    val scatter: List<ScatterStar>,
    val accent: List<AccentStar>,
)

// tempCol の両端（系統色の冷／温）。DustSeizu=冷 rgb(210,222,250)≈モック冷端、StarGlowInnerSeizu=温 rgb(240,235,205)≈モック温端。
private val BandCool = DustSeizu
private val BandWarm = StarGlowInnerSeizu
private fun tempColor(k: Float): Color = lerp(BandCool, BandWarm, k.coerceIn(0f, 1f))

private const val NW = 390f    // モック名目幅（viewport／far 共通）
private const val NH = 844f    // モック名目 viewport 高（.deepsky）
private const val NFARH = 984f // モック名目 far 高（H+140＝視差の余白ぶんを含む .farstars）

/**
 * 深空フィールドを一度だけ決定的に生成する（正本 bookshelf-M-rich-R1.html の buildBand/drawFarStars/drawDeepSky）。
 *
 * なぜ固定 seed（蔵書非依存）か: 深空は「夜空という不変の地」＝蔵書の増減で銀河が組み変わるのは不自然。
 * さらに remember{}（キー無し）に入れて 1 コンポジション 1 回だけ生成する＝再コンポーズでも星は一切踊らない
 * （フレーム毎の再計算もしない）。読了星の位置だけは各作品の id ハッシュから決定的に導く（別関数）。
 */
internal fun buildDeepSkyField(): DeepSkyField {
    val rnd = Lcg(987654321) // モック s=987654321 と同じ固定 seed（線形合同法・SkyCanvas.Lcg を共用）
    fun r(): Float = rnd.next()
    // Box–Muller の正規乱数（モック gauss() を写す）。
    fun gauss(): Float {
        val u = r().let { if (it <= 0f) 1e-6f else it }
        val v = r()
        return (sqrt(-2f * ln(u)) * cos(6.2831853f * v))
    }
    // 帯の芯・半幅・沿軸密度（名目 far 座標。正本 bAxis/bHalf/bDens をそのまま）。
    fun bAxis(y: Float): Float = 115f + 205f * ((640f - y) / 290f).coerceIn(0f, 1f) + sin(y / 150f) * 14f
    fun bHalf(y: Float): Float = 52f + 18f * sin(y / 280f + 0.6f)
    fun bDens(y: Float): Float =
        0.5f + 0.5f * (sin(y * 0.021f) * 0.5f + sin(y * 0.047f + 1.1f) * 0.3f + sin(y * 0.009f + 2.3f) * 0.2f)

    val band = ArrayList<BandParticle>(3000)
    // 天の川本体＝微星の粒（面グロー主体を廃し粒主体へ。芯へ沿ったガウス散布・べき分布・輝度上限）。
    var placed = 0
    var tries = 0
    while (placed < 2400 && tries < 16000) {
        tries++
        val y = r() * NFARH
        val dens = bDens(y)
        if (r() > 0.32f + 0.62f * dens) continue          // 沿軸の密度勾配（濃淡ムラ）
        val off = gauss() * bHalf(y) * 0.5f
        val x = bAxis(y) + off
        if (x < -4f || x > NW + 4f) continue
        val lo = 12f + 10f * sin(y / 95f)                 // Great Rift（蛇行する暗黒帯の芯）
        // 実機後詰め(ADR 0005 §B): 実機では裂け目が周囲の粒に埋もれ判別不能だったため、幅8→14f・間引き確率0.82→0.96f
        // へ強化し「帯に構造が生まれる」実在の暗黒帯感を出す（粒は削らず確率で疎化＝天の川の粒密度自体は不変）。
        if (abs(off - lo) < 14f && r() < 0.96f) continue  // 裂け目は「粒を確率で間引く」（削り取らない）
        val d = (abs(off) / bHalf(y)).coerceAtMost(1f)    // 0核..1縁
        val mag = r().pow(2.6f)                            // べき分布＝多数の微光＋少数の輝星
        val bright = (1f - d * 0.75f) * (0.28f + 0.72f * mag) * (0.55f + 0.45f * dens)
        val rad = 0.28f + mag * 0.85f
        val a = (0.09f + 0.5f * bright).coerceAtMost(0.42f) // 輝度上限 0.42（題名可読の担保）
        val glow = if (bright > 0.82f && r() < 0.5f) 2.4f / NW else 0f
        band += BandParticle(x / NW, y / NFARH, rad / NW, a, tempColor(0.28f + 0.5f * bright), glow)
        placed++
    }
    // 銀河中心の膨らみ（核＝一掴み密に）。
    repeat(460) {
        val y = 300f + gauss() * 70f
        val off = gauss() * 42f
        val x = bAxis(y) + off
        if (x < -4f || x > NW + 4f || y < 0f || y > NFARH) return@repeat
        val d = (abs(off) / 46f).coerceAtMost(1f)
        val mag = r().pow(2.0f)
        val bright = (1f - d * 0.6f) * (0.4f + 0.6f * mag)
        val a = (0.12f + 0.45f * bright).coerceAtMost(0.46f)
        val glow = if (bright > 0.84f && r() < 0.45f) 2.4f / NW else 0f
        band += BandParticle(x / NW, y / NFARH, (0.3f + mag * 0.8f) / NW, a, tempColor(0.4f + 0.45f * bright), glow)
    }
    // 帯外の散開微星（150点）。
    val scatter = ArrayList<ScatterStar>(150)
    repeat(150) {
        scatter += ScatterStar(r(), r(), (r() * 0.6f + 0.16f) / NW, r() * 0.24f + 0.04f)
    }
    // 深空のアクセント星（10点・viewport 帯 y=60..H-60）。
    val accent = ArrayList<AccentStar>(10)
    repeat(10) {
        accent += AccentStar(r(), (60f + r() * (NH - 120f)) / NH)
    }
    return DeepSkyField(band, scatter, accent)
}

/** 読了星（深空へ恒久着地した先端星・累積）。位置は作品 id から決定的に導く。 */
internal class FinishedStar(val fx: Float, val fy: Float, val mag: Float, val color: Color)

/**
 * z0 深空（固定・スクロール非追従）: 星雲2片＋アクセント星＋読了星の累積を描く。
 * 一度確定描画で足りる＝スクロール state を一切読まないので、スクロール中に再描画されない。
 */
internal fun DrawScope.drawDeepSky(field: DeepSkyField, finished: List<FinishedStar>) {
    val w = size.width
    val h = size.height
    // 星雲2片（radial・色→透明）。第1=SkyCloudSeizu(=rgb58,78,150 と同値)／第2=NebulaVioletSeizu（モック実値の微紫）。
    // 実機後詰め(ADR 0005 §B): 実機では両片が淡すぎ「靄」で存在感が出なかったため、芯 alpha を 0.12→0.18／0.10→0.16 へ微増し
    // 中間ストップ（芯 alpha の 0.42 倍を 45% 地点まで保持）で外周へ body を持たせ「雲」の塊感を出す。輝度上限外＝題名可読は不変。
    drawNebula(Offset(300f / NW * w, 210f / NH * h), 150f / NW * w, SkyCloudSeizu.copy(alpha = 0.18f))
    drawNebula(Offset(92f / NW * w, 600f / NH * h), 168f / NW * w, NebulaVioletSeizu.copy(alpha = 0.16f))
    // アクセント星（数点の微光球）。
    for (a in field.accent) {
        val c = Offset(a.fx * w, a.fy * h)
        val rad = 2.8f / NW * w
        drawCircle(
            Brush.radialGradient(
                listOf(StarCoreSeizu.copy(alpha = 0.55f), Color.Transparent),
                center = c, radius = rad,
            ),
            radius = rad, center = c,
        )
    }
    // 読了星の累積（深空へ恒久着地＝読了ぶんだけ地に星が増える）。作品固有色（--id）＋温白の芯。
    for (f in finished) {
        val c = Offset(f.fx * w, f.fy * h)
        val rad = (3.6f * f.mag + 1.6f) / NW * w
        drawCircle(
            Brush.radialGradient(
                listOf(f.color.copy(alpha = 0.5f * f.mag + 0.2f), Color.Transparent),
                center = c, radius = rad,
            ),
            radius = rad, center = c,
        )
        drawCircle(StarCoreSeizu.copy(alpha = 0.55f * f.mag + 0.2f), radius = 1.1f / NW * w, center = c)
    }
}

private fun DrawScope.drawNebula(center: Offset, radius: Float, color: Color) {
    // 実機後詰め(ADR 0005 §B): 純 color→透明の2ストップは中心だけ濃い「靄」に見えたため、
    // 中間 45% まで色を（芯の 0.42 倍で）保持する3ストップに変え、雲としての body／ふわりとした縁を与える。
    drawCircle(
        Brush.radialGradient(
            0f to color,
            0.45f to color.copy(alpha = color.alpha * 0.42f),
            1f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

/**
 * z1 遠景（天の川粒帯＋散開微星）。呼び出し側で graphicsLayer{translationY=…} の中に置き、極微視差で滑らせる。
 * 描画自体はスクロール state を読まない（＝graphicsLayer のレイヤーへ一度記録され、以後は transform だけ動く）。
 * far は viewport より下へ余白ぶん（buffer）伸ばして描く＝上へずらしても下端に隙間が出ない。
 */
internal fun DrawScope.drawFarStars(field: DeepSkyField) {
    val w = size.width
    val farH = size.height + 60.dp.toPx() // 視差のオーバースクロール余白（呼び出し側の最大 translate 40dp > これ未満）
    for (p in field.band) {
        val c = Offset(p.fx * w, p.fy * farH)
        if (p.glowFr > 0f) {
            val gr = p.glowFr * w
            drawCircle(
                Brush.radialGradient(
                    listOf(StarCoreSeizu.copy(alpha = 0.4f), Color.Transparent),
                    center = c, radius = gr,
                ),
                radius = gr, center = c,
            )
        }
        drawCircle(p.color.copy(alpha = p.alpha), radius = p.fr * w, center = c)
    }
    for (s in field.scatter) {
        drawCircle(DustSeizu.copy(alpha = s.alpha), radius = s.fr * w, center = Offset(s.fx * w, s.fy * farH))
    }
}

// ============================================================
// z2 演出オーバーレイ: まれな流れ星（30〜70秒に一度・一度に一筋・淡い遠景の一筋）。
// 実装は LaunchedEffect＋確率待機。reduce-motion では無効（オーバーレイ自体を出さない）。
// 造形は正本モックの spawnMeteor/drawMeteor を写す（名目 390×844 座標→描画時に size へスケール）。
// ============================================================
private class MeteorState(
    val nx: Float, val ny: Float, // 発生点（名目座標）
    val vx: Float, val vy: Float, // 速度（名目座標/フレーム）
    val len: Float,               // 尾の長さ（名目幅基準）
    val color: Color,
)

@Composable
internal fun MeteorOverlay(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (reduceMotion) return // reduce-motion: 流れ星は無効（静謐則の承認済み例外を止める）
    val progress = remember { Animatable(0f) }
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    LaunchedEffect(Unit) {
        val rnd = Lcg(192837465)
        fun r(): Float = rnd.next()
        delay(2600L) // 正本モックのデモに倣い、最初の一本だけやや早め（以後は 30〜70 秒間隔）
        while (isActive) {
            val fromLeft = r() < 0.5f
            val nx = if (fromLeft) 20f + r() * 120f else 250f + r() * 120f
            val ny = 70f + r() * 260f
            val dir = if (fromLeft) 1f else -1f
            meteor = MeteorState(
                nx = nx, ny = ny,
                vx = dir * (3.0f + r() * 1.2f), vy = 2.0f + r() * 1.0f,
                len = 40f + r() * 22f,
                color = tempColor(0.35f + r() * 0.35f),
            )
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1000, easing = LinearEasing)) // 一筋の掃過（≒モックの life 減衰ぶん）
            meteor = null
            delay((30000L + (r() * 40000f).toLong())) // 次は 30〜70 秒後・一度に一本のみ
        }
    }
    // Canvas の draw ラムダ内で meteor/progress.value を読む＝描画フェーズの遅延読み取り（再コンポーズを起こさない）。
    Canvas(modifier) {
        val m = meteor ?: return@Canvas
        val t = progress.value
        val sx = size.width / NW
        val sy = size.height / NH
        val frames = 62f * t // モックは life 1→0（≒62フレーム）で掃過。head は速度×経過フレームで進む。
        val hx = (m.nx + m.vx * frames) * sx
        val hy = (m.ny + m.vy * frames) * sy
        val life = 1f - t // 淡くフェードアウト
        val vlen = hypot(m.vx, m.vy).coerceAtLeast(1e-4f)
        val tailX = hx - m.vx / vlen * m.len * sx
        val tailY = hy - m.vy / vlen * m.len * sy
        drawLine(
            Brush.linearGradient(
                listOf(m.color.copy(alpha = 0.5f * life), Color.Transparent),
                start = Offset(hx, hy), end = Offset(tailX, tailY),
            ),
            start = Offset(hx, hy), end = Offset(tailX, tailY),
            strokeWidth = 1.3f * sx, cap = StrokeCap.Round,
        )
        drawCircle(StarCoreSeizu.copy(alpha = 0.7f * life), radius = 1.2f * sx, center = Offset(hx, hy))
    }
}
