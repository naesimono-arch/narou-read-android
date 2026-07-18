package com.novelreader.ui.skins.m

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import com.novelreader.ui.theme.NebulaIndigoCoolSeizu
import com.novelreader.ui.theme.NebulaIndigoDeepSeizu
import com.novelreader.ui.theme.NebulaIndigoSeizu
import com.novelreader.ui.theme.NebulaMadderSeizu
import com.novelreader.ui.theme.NebulaMadderWarmSeizu
import com.novelreader.ui.theme.NebulaVioletSeizu
import com.novelreader.ui.theme.NebulaVioletWarmSeizu
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarNeutralSeizu
import com.novelreader.ui.theme.StarTempAmberSeizu
import com.novelreader.ui.theme.StarTempBlueSeizu
import com.novelreader.ui.theme.StarTempGoldSeizu
import com.novelreader.ui.theme.StarTempWhiteSeizu
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================
// スキンM「星図」本棚の【深空】背景レイヤー（正本 bookshelf-M-rich-R1s.html・承認済み R1s「深空（合成）」）。
//
// R1s の思想＝天の川改良3案の全合成【形は解像(c)・色は色彩(b)・場は構造(a)】:
//   ・形(c): 輝星を2層描画（鋭いコア＋分離した淡いハロー＝中心を凹ませた環）＋白 pip で芯を締める／
//     最輝星の数点に微細十字スパイク（完全静止）／帯粒を微細高密度化（粒径 r を絞りにじみを消す）。
//   ・色(b): 全粒に星の体温＝色温度4系統（青白→白→淡金→橙）を tempRGB で区分線形補間し grainCol で彩色
//     （輝度上位ほど色が乗る・暗星は無彩色 NEUTRAL）。ハロー微グロー・下地もや・ネビュラ・アクセント星・
//     流れ星も色温度で。pip・スパイクの鋭い芯だけ白のまま（＝解像の主役は白で締める）。
//   ・場(a): Great Rift を riftCenter 蛇行芯まわりの exp(-dr²) で深く疎化／暗黒星雲 darkNeb を重層疎化／
//     縁のほつれ fila で一様散布に見せない筋・ムラ／帯の輝度断面を強め中心軸を立て銀河核バルジを軸へ集中／
//     背景（帯外散開微星）を沈めダイナミックレンジ拡大。
// BookshelfSkyM.kt が本ファイルの部品を z0/z1/z2 の三層で敷く（一覧＝BookshelfLogM も z0 の drawDeepSky を再利用）。
//
// 座標系: すべて「正本モックの 390×844(viewport)／390×984(far) の名目座標を 0..1 の割合へ正規化」して持つ。
//   描画時に実 Canvas の size へ乗じる＝端末解像度非依存で帯の形状（芯曲線 bAxis／半幅 bHalf±／密度勾配 bDens）を保つ。
//
// 色: theme/Color.kt の Seizu 節のみ使用（直書き hex 禁止）。R1s の色温度4アンカー StarTemp*Seizu／ネビュラ多停止
//   Nebula*Seizu を Color.kt へ追加済み。区分線形補間・NEUTRAL からの引き上げは「値の算術」であり新色の直書きでない
//   （既存 tempColor(2色 lerp) と同型＝アンカーはトークン・中間は補間）。彩度は系統色（群青/温白/月光スレート）内。
//
// 性能規律（DeepSkyM 基準・不変）: フィールドは remember 1回・固定 Lcg seed の決定的生成／名目 0..1 正規化→描画時
//   size 乗算／z0 は drawBehind でスクロール state 非読・z1 視差/z2 流星の機構は不変／draw 内で遅延読み。
//   毎フレーム再生成は禁止（buildDeepSkyField は remember{} で 1 コンポジション 1 回）。
// ============================================================

/**
 * モック bookshelf-M-rich-R1s.html <script> と 1:1 同期する数値定数（帯・核・散開微星の本数、mag 指数、輝度上限）。
 *
 * なぜ1箇所に集約するか（再同期の機械化）: R1s モックは星数の増量改訂が繰り返される見込み。個々の formula 係数を
 * コードに散らすと再同期のたびに全走査が要る。粒数・α キャップなど「改訂で動きやすい値」をここへ集め、モック冒頭の
 * 「変更点一覧」に増量改訂が載っているのを最新版の目印にして本ブロックだけ差し替えれば同期が済むようにする。
 * （formula 内の座標係数＝bAxis/bright 式等は R1s の骨格＝安定ゆえ各関数へ残し、由来コメントを添える。）
 */
private object SkyR1s {
    // 本数（改訂1 で天の川らしく大幅増量＝「無数の星の海」）。増量は密度で作り単点 α は上げない（下記 α 補償）。
    const val BAND_TARGET = 6500      // placed 上限（帯粒数＝改訂1 で 3200→6500・高負荷容認）
    const val BAND_TRIES = 45000      // 撒きの試行上限（帯増量に比例＝22000→45000）
    // 【トーラス化・2026-07-19】帯だけは走向 bAxis を持つ構造層＝縦タイル境界でぶつ切りにしないための橋渡し帯。
    // 生成 y を境界外へ ±BAND_BRIDGE 延長し格納時に fy=mod 1.0 で畳む＝境界を跨ぐ粒が両タイルに現れ帯が連続する。
    const val BAND_BRIDGE = 130f      // 境界の外側に足す橋渡し帯の名目高（片側。両タイルに橋渡し粒を供給）
    const val BAND_MAG_EXP = 2.8f     // 帯 mag のべき指数（絞りの効く c2.8＝輝星を絞る）
    const val CORE_COUNT = 1260       // 銀河中心バルジの本数（帯と同比率で増量＝620→1260）
    const val CORE_MAG_EXP = 1.9f     // 核 mag のべき指数（バルジ輝度は密度で作る思想＝a1.9）
    const val SCATTER_COUNT = 520     // 帯外散開微星（形 c の微細高密度・改訂1 で 260→520）
    const val MICRO_SEA_COUNT = 3200  // 超微星の海（最深・帯構造に従属＝無数の星の海感。改訂1 で追加）
    const val ACCENT_COUNT = 10       // 深空アクセント星
    const val SPIKE_MAX = 7           // 微細十字スパイクを付ける最輝星の上限（数点に限る）
    // 粒あたり α（base + scale*bright）。改訂1 の密度補償＝数は増やすが単点 α を下げ総輝度を暴れさせない。
    const val BAND_A_BASE = 0.07f;  const val BAND_A_SCALE = 0.40f  // 帯（旧 0.10+0.52*bright → 0.07+0.40*bright）
    const val CORE_A_BASE = 0.09f;  const val CORE_A_SCALE = 0.32f  // 核（旧 0.12+0.42*bright → 0.09+0.32*bright）
    const val SCATTER_A_BASE = 0.02f; const val SCATTER_A_SCALE = 0.12f // 散開微星 α=rnd*0.12+0.02（沈める＋補償）
    const val MICRO_A_BASE = 0.02f;  const val MICRO_A_SCALE = 0.03f    // 超微星 α=rnd*0.03+0.02（最も淡い海）
    const val MICRO_R_BASE = 0.08f;  const val MICRO_R_SCALE = 0.34f    // 超微星 半径=rnd*0.34+0.08（小径）
    // 可読域の輝度上限（面輝度キャップ）。R1s で帯・核とも 0.42 へ統一＝題名可読を全帯で担保する不可侵の規律。
    // 増量後も α base/scale の密度補償と本キャップの二段で総輝度を抑える。pip・スパイクは離散点（面でない）ゆえ
    // このキャップに非抵触＝白の鋭い芯で解像を締めてよい。
    const val ALPHA_CAP = 0.42f
}

// 深空・天の川の粒（正規化座標。fx=x/名目幅, fy=y/名目far高, fr=半径/名目幅）。
// R1s【形c】: halo=分離した淡いハロー環（中心を凹ませた環）を持つ輝星か／spike=微細十字スパイクを付す最輝星か。
// bright は spike の長さ・α と pip の締めに使う（生成時に確定＝描画で再計算しない）。
internal class BandParticle(
    val fx: Float,
    val fy: Float,
    val fr: Float,
    val alpha: Float,
    val color: Color, // grainCol＝この粒の色温度（暗星は無彩色 NEUTRAL 寄り）
    val halo: Boolean,
    val spike: Boolean,
    val bright: Float,
)

// 帯外の散開微星／超微星の海（正規化座標）。いずれも色は無彩色地（StarNeutralSeizu）で背景を沈める。
internal class ScatterStar(val fx: Float, val fy: Float, val fr: Float, val alpha: Float)

// 深空のアクセント星（viewport 名目 390×844 の正規化座標）。R1s【色b】: 生成時に色温度を確定して持つ。
internal class AccentStar(val fx: Float, val fy: Float, val color: Color)

/**
 * 深空フィールド（星雲＝drawDeepSky 直描き・天の川粒帯・散開微星・アクセント星）。蔵書に依存しない不変の「地」。
 * finishedStars（読了星の累積）だけは蔵書由来のため別に持たせる（BookshelfSkyM 側で算出）。
 */
internal class DeepSkyField(
    val microSea: List<ScatterStar>, // 最深の超微星の海（帯構造に従属＝Great Rift を残す）
    val band: List<BandParticle>,
    val scatter: List<ScatterStar>,
    val accent: List<AccentStar>,
)

internal const val NW = 390f    // モック名目幅（viewport／far 共通）。発見の深空 DiscoveryHomeSkyM も共有（同じ星の型）。
internal const val NH = 844f    // モック名目 viewport 高（.deepsky）。発見（parallax 無し＝単一 canvas）は帯粒もこの高で正規化。
private const val NFARH = 984f // モック名目 far 高（H+140＝視差の余白ぶんを含む .farstars）＝本棚専用（発見は視差なし）

// ===== 【場a】帯の形状・疎化フィールド（正本 R1s の bAxis/bHalf/bDens/riftCenter/fila/darkNeb をそのまま）=====
// 芯＝テキストと反相関にルート（可読性）: 上=右→中〜下=左。名目 far 座標。
private fun bAxis(y: Float): Float =
    115f + 205f * ((640f - y) / 290f).coerceIn(0f, 1f) + sin(y / 150f) * 14f
private fun bHalf(y: Float): Float = 52f + 18f * sin(y / 280f + 0.6f)      // 帯の半幅（細めのリボン）
// bDens/riftCenter/fila/darkNeb は本棚と発見（DiscoveryHomeSkyM の沈め版）で同一式＝共通部品として internal 共有
// （bAxis/bHalf/warmAt は走向・核位置が発見で異なる＝発見側に discBAxis/discBHalf/discWarmAt を別途持つ）。
internal fun bDens(y: Float): Float =
    0.5f + 0.5f * (sin(y * 0.021f) * 0.5f + sin(y * 0.047f + 1.1f) * 0.3f + sin(y * 0.009f + 2.3f) * 0.2f)
// Great Rift の芯＝帯に沿って蛇行する暗黒帯（低周波の蛇行＋高周波の揺れ＝ほつれた裂け目）。
internal fun riftCenter(y: Float): Float = 12f + 11f * sin(y / 95f) + 4f * sin(y / 47f + 1.3f)
// フィラメント場＝決定的な値ノイズ（trig和）。0..1。帯の縁のほつれ・沿軸の筋（一様散布に見せない）。
internal fun fila(x: Float, y: Float): Float {
    val v = sin(x * 0.11f + y * 0.043f) * 0.5f + sin(x * 0.27f - y * 0.10f + 1.7f) * 0.32f +
        sin(x * 0.061f + y * 0.17f + 3.1f) * 0.18f
    return v * 0.5f + 0.5f
}
// 暗黒星雲場＝別位相の低周波ノイズ。高いほど粒を疎化（Great Rift への濃淡の重ね）。負域は0クランプ。
internal fun darkNeb(x: Float, y: Float): Float {
    val v = sin(x * 0.035f - y * 0.028f + 0.4f) * 0.6f + sin(x * 0.017f + y * 0.052f + 2.2f) * 0.4f
    return max(0f, v)
}

// ===== 【色b】星の色温度（4系統: 青白→白→淡金→橙）＝各粒に体温を宿す（正本 R1s の tempRGB/starCol/grainCol）=====
// アンカーはトークン（StarTemp*Seizu）＝直書き禁止を守りつつ、区分線形補間・NEUTRAL からの引き上げは「値の算術」。
private val StarTempStops: List<Pair<Float, Color>> = listOf(
    0.00f to StarTempBlueSeizu,   // 青白（寒色端）
    0.40f to StarTempWhiteSeizu,  // 白（中庸）
    0.72f to StarTempGoldSeizu,   // 淡金
    1.00f to StarTempAmberSeizu,  // 橙（暖色端）
)
/** 色温度 t（0=青白 → 1=橙）を4アンカーの区分線形補間で返す（R1s tempRGB）。発見の深空も共有（同一色温度系）。 */
internal fun starTempColor(t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    for (i in 1 until StarTempStops.size) {
        val (p0, c0) = StarTempStops[i - 1]
        val (p1, c1) = StarTempStops[i]
        if (tt <= p1) return lerp(c0, c1, ((tt - p0) / (p1 - p0)).coerceIn(0f, 1f))
    }
    return StarTempStops.last().second
}
/** 星の最終色: 熱 t の色を無彩色地 NEUTRAL から strength で引き上げる（暗い星ほど無彩色）。R1s starCol。発見も共有。 */
internal fun starColorAt(t: Float, strength: Float): Color =
    lerp(StarNeutralSeizu, starTempColor(t), strength.coerceIn(0f, 1f))
// 決定的ハッシュ（Math.sin ベース・rnd を消費しない＝粒生成ストリームを乱さない）。位置→スペクトル散らばり。
// Double で評価する（大引数 sin の精度を保ち R1s の散らばりを再現）。R1s hash01。発見の深空も共有。
internal fun hash01(a: Float, b: Float): Float {
    val v = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return (v - floor(v)).toFloat()
}
// 帯温度勾配: 核（銀河バルジ y≈300）で最も暖、末端で寒へ冷える（黄色い老いた星の集積＝天文的必然）。R1s warmAt。
private fun warmAt(y: Float): Float = exp(-((y - 300f) / 300f).pow(2))
// 生成時の明るさ bright と位置(x,y) から星色を返す（R1s grainCol＝冷寄り基調＋核で暖＋散らばり／輝度上位ほど色が乗る）。
private fun grainColor(x: Float, y: Float, bright: Float): Color {
    val spread = (hash01(x, y) - 0.5f) * 0.85f      // 星ごとのスペクトル散らばり（4系統の視認）
    val t = 0.16f + 0.60f * warmAt(y) + spread       // 冷寄り基調＋核で暖＋散らばり
    val strength = (bright - 0.30f) / 0.42f          // 輝度上位ほど色が乗る／暗星(≲0.30)は無彩色
    return starColorAt(t, strength)
}

/**
 * 深空フィールドを一度だけ決定的に生成する（正本 bookshelf-M-rich-R1s.html の buildBand/drawFarStars/drawDeepSky）。
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
        return sqrt(-2f * ln(u)) * cos(6.2831853f * v)
    }

    // 【トーラス化】生成 y を境界外へ ±BAND_BRIDGE 延長し fy=mod 1.0 で畳む＝縦タイル境界で帯が連続する（橋渡し）。
    // 走向式 bAxis/bHalf は不変＝帯の形は再設計せず、境界近傍にだけ橋渡し粒を足すだけ（生成は remember 1回・決定的のまま）。
    // 単位yあたり密度を視野域で不変に保つため target/tries を延長率ぶんスケール（視界内の帯の見えは従来どおり）。
    val bandExt = (NFARH + 2f * SkyR1s.BAND_BRIDGE) / NFARH
    val bandTarget = (SkyR1s.BAND_TARGET * bandExt).toInt()
    val bandTries = (SkyR1s.BAND_TRIES * bandExt).toInt()
    val band = ArrayList<BandParticle>(bandTarget + SkyR1s.CORE_COUNT)
    // 天の川本体＝微星の粒（芯へ沿ったガウス散布・べき分布・輝度上限）。【場a】で疎化・断面を立て、【色b】で彩色、【形c】で粒径を絞る。
    var placed = 0
    var tries = 0
    var spikeN = 0 // 微細スパイクを付与した最輝星の数（数点に限る＝SPIKE_MAX 上限）
    while (placed < bandTarget && tries < bandTries) {
        tries++
        val y = r() * (NFARH + 2f * SkyR1s.BAND_BRIDGE) - SkyR1s.BAND_BRIDGE // 境界外へ延長（橋渡し）
        val dens = bDens(y)
        if (r() > 0.30f + 0.64f * dens) continue         // 沿軸の密度勾配（濃淡ムラ）
        val off = gauss() * bHalf(y) * 0.5f              // 芯からの垂直分布＝ガウス（裾＝縁のほつれ）
        val x = bAxis(y) + off
        if (x < -4f || x > NW + 4f) continue
        val d = (abs(off) / bHalf(y)).coerceAtMost(1f)   // 0核..1縁
        // 【場a】(1) Great Rift を深く彫る＝riftCenter 蛇行芯まわりを exp(-dr²) で強疎化（削らず確率・最大93%）
        val dr = (off - riftCenter(y)) / 13f
        val rift = exp(-dr * dr)
        if (r() < rift * 0.93f) continue
        // 【場a】(2) 暗黒星雲の重ね＝低周波の暗部でさらに疎化
        if (r() < darkNeb(x, y) * 0.5f) continue
        // 【場a】(3) 縁のほつれ＝縁(d大)かつフィラメントの谷(f小)ほど間引き＝一様散布に見せない
        val f = fila(x, y)
        if (r() < d * (1f - f) * 0.7f) continue
        val mag = r().pow(SkyR1s.BAND_MAG_EXP)           // べき分布＝輝星を絞る
        // 【場a】断面(1-d*0.82)を強め中心軸を立て＋f で縞（沿軸のムラ）。
        val bright = (1f - d * 0.82f) * (0.26f + 0.74f * mag) * (0.5f + 0.5f * dens) * (0.78f + 0.22f * f)
        val rad = 0.20f + mag * 0.62f                    // 【形c】粒径を絞り「にじみ」を消す
        val a = (SkyR1s.BAND_A_BASE + SkyR1s.BAND_A_SCALE * bright).coerceAtMost(SkyR1s.ALPHA_CAP) // 密度補償＋輝度上限0.42
        // 【形c】最輝星の数点だけに微細スパイク（bright>0.95・最大7点・完全静止）。r()の消費順は spike→halo（モック同順）。
        val spike = bright > 0.95f && spikeN < SkyR1s.SPIKE_MAX && r() < 0.4f
        if (spike) spikeN++
        band += BandParticle(
            x / NW, (y / NFARH).mod(1f), rad / NW, a, // fy はトーラス座標（境界外の橋渡し粒は mod で [0,1) へ畳む）
            grainColor(x, y, bright),            // 色=b
            halo = bright > 0.80f && r() < 0.45f, // 形=c（分離ハローを持つ輝星か）
            spike = spike, bright = bright,
        )
        placed++
    }
    // 核（銀河中心バルジ）＝【場a】軸へ密に集中（散布σ縮小 gauss*62/off*38）。輝度集中は密度と本数で（単点alphaを上げない）。
    repeat(SkyR1s.CORE_COUNT) {
        val y = 300f + gauss() * 62f
        val off = gauss() * 38f
        val x = bAxis(y) + off
        if (x < -4f || x > NW + 4f || y < 0f || y > NFARH) return@repeat
        val dr = (off - riftCenter(y)) / 13f             // Rift を核まで貫通（核はやや弱め0.8で芯質量は保つ）
        val rift = exp(-dr * dr)
        if (r() < rift * 0.8f) return@repeat
        val d = (abs(off) / 42f).coerceAtMost(1f)
        val mag = r().pow(SkyR1s.CORE_MAG_EXP)
        val bright = (1f - d * 0.62f) * (0.42f + 0.58f * mag)
        val a = (SkyR1s.CORE_A_BASE + SkyR1s.CORE_A_SCALE * bright).coerceAtMost(SkyR1s.ALPHA_CAP) // 密度補償＋核 a 上限も 0.42 統一
        band += BandParticle(
            x / NW, (y / NFARH).mod(1f), (0.22f + mag * 0.6f) / NW, a, // fy はトーラス座標
            grainColor(x, y, bright),
            halo = bright > 0.84f && r() < 0.4f, spike = false, bright = bright,
        )
    }
    // 超微星の海（最深・帯構造に従属）＝無数の星の海感。小径・極淡。帯域内（|off|<bHalf*1.2）だけは Great Rift の
    // 疎化に従属させ溝を埋めない（帯域外は一様に散らす＝夜空の広がり）。色は無彩色（描画時 StarNeutralSeizu）。
    val microSea = ArrayList<ScatterStar>(SkyR1s.MICRO_SEA_COUNT)
    repeat(SkyR1s.MICRO_SEA_COUNT) {
        val x = r() * NW
        val y = r() * NFARH
        val off = x - bAxis(y)
        if (abs(off) < bHalf(y) * 1.2f) {
            val dr = (off - riftCenter(y)) / 13f
            if (r() < exp(-dr * dr) * 0.9f) return@repeat // 帯域内は Great Rift に従属（溝を保つ）
        }
        val rad = r() * SkyR1s.MICRO_R_SCALE + SkyR1s.MICRO_R_BASE
        val a = r() * SkyR1s.MICRO_A_SCALE + SkyR1s.MICRO_A_BASE
        microSea += ScatterStar(x / NW, y / NFARH, rad / NW, a)
    }
    // 帯外の散開微星（形c の微細高密度・520点）。α は場a の「背景を沈める」思想＋密度補償で控えめ側。色は無彩色。
    val scatter = ArrayList<ScatterStar>(SkyR1s.SCATTER_COUNT)
    repeat(SkyR1s.SCATTER_COUNT) {
        val x = r() * NW
        val y = r() * NFARH
        val rad = r() * 0.42f + 0.12f
        val a = r() * SkyR1s.SCATTER_A_SCALE + SkyR1s.SCATTER_A_BASE
        scatter += ScatterStar(x / NW, y / NFARH, rad / NW, a)
    }
    // 深空のアクセント星（10点・viewport 帯 y=60..H-60）。【色b】生成時に色温度を確定（strength高＝色が視認）。
    val accent = ArrayList<AccentStar>(SkyR1s.ACCENT_COUNT)
    repeat(SkyR1s.ACCENT_COUNT) {
        val x = r() * NW
        val y = 60f + r() * (NH - 120f)
        val t = 0.14f + 0.60f * warmAt(y) + (hash01(x, y) - 0.5f) * 1.0f // rnd()非消費の hash で熱を決定
        accent += AccentStar(x / NW, y / NH, starColorAt(t, 0.9f))
    }
    return DeepSkyField(microSea, band, scatter, accent)
}

/** 読了星（深空へ恒久着地した先端星・累積）。位置は作品 id から決定的に導く。 */
internal class FinishedStar(val fx: Float, val fy: Float, val mag: Float, val color: Color)

/**
 * z0 深空（固定・スクロール非追従）: 星雲2片（各々多停止 群青核→菫→茜＋副葉）＋アクセント星を描く。
 * 一度確定描画で足りる＝スクロール state を一切読まないので、スクロール中に再描画されない。
 *
 * 読了星（蔵書依存）はここに含めない: 深空は蔵書非依存の「不変の地」＝常駐 backdrop（SkyBackdropM）が全 M 画面へ
 * 敷く1枚（ユーザー裁定2026-07-19「空の一枚化」）。蔵書由来の読了星は本棚コンテンツ側で drawFinishedStars を
 * オーバーレイする（backdrop に蔵書状態を持ち込まない＝backdrop は蔵書の増減で再描画されない性能規律も満たす）。
 */
internal fun DrawScope.drawDeepSky(field: DeepSkyField) {
    val w = size.width
    val h = size.height
    // 【色b】星雲＝彩度を一段上げ＋色相変化（群青核→菫→茜）の多段グラデ＋対比色温度の副葉。alpha は可読維持（彩度暴走なし）。
    // R1s は R1/従前実装の面グロー多葉を廃し、多停止1枚＋副葉1枚の素直な radial に戻す（構造でなく色相で奥行きを作る）。
    // 径は名目 px（viewport 割合）で端末非依存。星雲は帯・題名の外側に配置＝局所ピークでも題名可読は不変。
    run { // 第1星雲（300,210,162）
        val c = Offset(300f / NW * w, 210f / NH * h); val rad = 162f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to NebulaIndigoSeizu.copy(alpha = 0.14f),
                0.42f to NebulaVioletSeizu.copy(alpha = 0.095f),
                0.78f to NebulaMadderSeizu.copy(alpha = 0.045f),
                1f to Color.Transparent, center = c, radius = rad,
            ), radius = rad, center = c,
        )
    }
    run { // 第1星雲 副葉（232,258,96）＝茜のにじみ（暖側へ色相を伸ばす）
        val c = Offset(232f / NW * w, 258f / NH * h); val rad = 96f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to NebulaMadderWarmSeizu.copy(alpha = 0.05f),
                1f to Color.Transparent, center = c, radius = rad,
            ), radius = rad, center = c,
        )
    }
    run { // 第2星雲（92,600,172）
        val c = Offset(92f / NW * w, 600f / NH * h); val rad = 172f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to NebulaIndigoDeepSeizu.copy(alpha = 0.12f),
                0.40f to NebulaVioletWarmSeizu.copy(alpha = 0.085f),
                0.76f to NebulaMadderWarmSeizu.copy(alpha = 0.05f),
                1f to Color.Transparent, center = c, radius = rad,
            ), radius = rad, center = c,
        )
    }
    run { // 第2星雲 副葉（150,556,104）＝群青の寒色サブ（茜との色温度対比）
        val c = Offset(150f / NW * w, 556f / NH * h); val rad = 104f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to NebulaIndigoCoolSeizu.copy(alpha = 0.055f),
                1f to Color.Transparent, center = c, radius = rad,
            ), radius = rad, center = c,
        )
    }
    // アクセント星（数点の微光球）＝色温度を乗せた radial。
    for (a in field.accent) {
        val c = Offset(a.fx * w, a.fy * h)
        val rad = 2.8f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to a.color.copy(alpha = 0.55f),
                1f to a.color.copy(alpha = 0f),
                center = c, radius = rad,
            ),
            radius = rad, center = c,
        )
    }
}

/**
 * 読了星の累積（深空へ恒久着地＝読了ぶんだけ地に星が増える）。作品固有色（--id）＋温白の芯。
 * 蔵書依存ゆえ backdrop でなく本棚コンテンツ側でオーバーレイする（drawDeepSky から分離＝空の一枚化・2026-07-19）。
 * z 順の注記: 従前は z0（drawDeepSky 末尾＝天の川粒帯 drawFarStars より背面）だったが、一枚化で backdrop（粒帯を含む）が
 * NavHost 背後へ回るため、本棚コンテンツ側の本オーバーレイは粒帯より前面になる。読了星は上部の局所グロー・粒帯は極淡ゆえ
 * 知覚差はほぼ無いが、厳密には z 順が変わる（報告事項）。
 */
internal fun DrawScope.drawFinishedStars(finished: List<FinishedStar>) {
    val w = size.width
    val h = size.height
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

/**
 * z1 遠景（天の川粒帯＋散開微星）。呼び出し側で graphicsLayer{translationY=…} の中に置き、極微視差で滑らせる。
 * 描画自体はスクロール state を読まない（＝graphicsLayer のレイヤーへ一度記録され、以後は transform だけ動く）。
 * far は viewport より下へ余白ぶん（buffer）伸ばして描く＝上へずらしても下端に隙間が出ない。
 */
internal fun DrawScope.drawFarStars(field: DeepSkyField) {
    val w = size.width
    // 縦トーラスのタイル高＝canvas 高そのもの。呼び出し側が本関数を2タイル記録（[0,h] と [h,2h]）＝上下に隙間なく無限タイル。
    // 旧「farH=h+60dp のオーバースクロール余白」はクランプ前提の逃げ＝トーラス化（無限スクロール）で不要になり撤去。
    val h = size.height
    val scale = w / NW
    // 最深＝超微星の海（帯本体の下＝帯が主役）。無彩色地の極淡な点の海で「無数の星」を敷き、この上に下地もや・粒帯を載せる。
    for (m in field.microSea) {
        drawCircle(StarNeutralSeizu.copy(alpha = m.alpha), radius = m.fr * w, center = Offset(m.fx * w, m.fy * h))
    }
    // 【色b/場a】核だけの極薄下地もや（半径 104・暖色 tempStr(0.82)＝バルジは淡金〜橙／中間ストップで裾を締める）。
    // R1s は面グローもや群を廃し核の一枚に絞る＝奥行きの下支え（帯の輝きは粒の集積で作る＝面 alpha を上げない）。
    run {
        val c = Offset(bAxis(300f) / NW * w, 300f / NFARH * h)
        val rad = 104f / NW * w
        drawCircle(
            Brush.radialGradient(
                0f to starTempColor(0.82f).copy(alpha = 0.045f),
                0.6f to starTempColor(0.5f).copy(alpha = 0.018f),
                1f to Color.Transparent, center = c, radius = rad,
            ), radius = rad, center = c,
        )
    }
    for (p in field.band) {
        val c = Offset(p.fx * w, p.fy * h)
        if (p.halo) {
            // 【形c】分離した淡いハロー＝中心を凹ませた環（コアと離れて読める＝にじみでなく深み）／【色b】その星の色温度で tint。
            val rr = 3.0f / NW * w
            drawCircle(
                Brush.radialGradient(
                    0f to p.color.copy(alpha = 0f),      // 中心は空＝下の鋭いコアと分離
                    0.45f to p.color.copy(alpha = 0.16f), // 環のピーク（淡い・星の体温で色付く）
                    1f to Color.Transparent, center = c, radius = rr,
                ), radius = rr, center = c,
            )
        }
        // 鋭いコア（1px級の点・にじませない）＝解像の主役。
        drawCircle(p.color.copy(alpha = p.alpha), radius = p.fr * w, center = c)
        if (p.halo) {
            // 輝星の芯を白の微小 pip で締める（r0.7・alpha≤0.40＝離散点ゆえ面輝度規律 0.42 に抵触しない）。
            drawCircle(StarCoreSeizu.copy(alpha = (p.alpha + 0.14f).coerceAtMost(0.40f)), radius = 0.7f / NW * w, center = c)
        }
        if (p.spike) drawSpike(c, p.bright, scale)
    }
    // 帯外の散開微星＝無彩色地で背景を沈める（α は控えめ側＝ダイナミックレンジ拡大）。
    for (s in field.scatter) {
        drawCircle(StarNeutralSeizu.copy(alpha = s.alpha), radius = s.fr * w, center = Offset(s.fx * w, s.fy * h))
    }
}

/**
 * 【形c】微細な十字スパイク（完全静止・控えめ）。最輝星の芯を鋭く見せる＝解像。派手にしない（薄い細線・短い）。
 * 芯の鋭さは白で締める＝R1s の rgba(240,244,255) は StarCoreSeizu(#F5F8FF=245,248,255) と知覚下微差ゆえ同色を使う。
 * scale=w/NW で名目長を実 Canvas へ。
 */
private fun DrawScope.drawSpike(center: Offset, bright: Float, scale: Float) {
    val len = (3.2f + bright * 2.2f) * scale
    val a = (0.13f + bright * 0.16f).coerceAtMost(0.30f)
    val col = StarCoreSeizu.copy(alpha = a)
    val lw = 0.6f * scale
    drawLine(col, Offset(center.x - len, center.y), Offset(center.x + len, center.y), strokeWidth = lw, cap = StrokeCap.Round)
    drawLine(col, Offset(center.x, center.y - len), Offset(center.x, center.y + len), strokeWidth = lw, cap = StrokeCap.Round)
}

// ============================================================
// z2 演出オーバーレイ: まれな流れ星。
//
// 【規律の分離＝B/C の真因対処・2026-07-19 裁定】
//   フィールド（星の地）は"静的絵画"ゆえ決定的生成が正（remember 1回・固定 seed）。対して流星は"時間イベント"ゆえ
//   非決定が正——出現時刻・種類はナビ/スクロールと無相関の実時間抽選でなければならない。両者の規律を混ぜない。
//
// 旧実装が「特定操作で確定出現」していた真因（B）:
//   ① MeteorOverlay を controller.hidden の早期 return より内側で構成していた＝読書へ出入り（hidden 切替）のたびに
//      LaunchedEffect(Unit) が破棄→再起動し、固定 delay(2600) で「読書から戻ると必ず約2.6秒後に流星」＝操作相関の確定出現。
//   ② 固定 LCG seed（192837465）で毎起動まったく同じ時刻・同じ順に流れた（=再現＝確定）。
//   是正: (1) スケジューラを hidden の外＝rememberMeteorHost として早期 return より前へ出し、hidden 切替で再起動させない。
//        (2) 実エントロピー seed の kotlin.random.Random（毎起動で列が変わる）。(3) 初回も抽選 delay＝固定初動を廃す。
//
// パターン(C): 4種の重み付き抽選（微流星/標準/長尺ゆっくり/二連）で1パターンへ固まらせない。二連はごく稀。
//   角度・進入位置・長さ・尾αに抽選幅。輝度は可読域規律に従属（tailAlpha 上限 0.42＝本文上を横切っても眩しすぎない）。
// reduce-motion では流星無効（不変）。造形は正本モックの spawnMeteor/drawMeteor を写す（名目 390×844→size へスケール）。
// ============================================================

/** 出現率・パターン・流星群のチューニング定数を1ブロックへ集約（再同期の機械化＝散らさない）。 */
internal object MeteorTuning {
    // 間隔＝指数分布の inter-arrival（MIN + Exp(mean)・上限打ち切り）。
    // 【平均60s裁定・2026-07-19 差し戻し③】旧値（MIN40s+mean78s・実測≒108s）は「流星が貴重すぎる」。画面に長く
    //   留まる演出でもないため平均を約60sへ引き下げる。MIN20s + Exp(mean42s) で非打ち切り平均は 62s、上限150sの裾切り
    //   込みで実測平均 ≒60s（MeteorSchedulingTest で検算）。1本ごと独立抽選ゆえ次がいつ来るか読めない性質は不変。
    const val INTERVAL_MIN_MS = 20_000L    // 最短間隔（詰まりすぎ防止）
    const val INTERVAL_MEAN_MS = 42_000L   // 指数分布の平均（合成平均は上限打ち切り込みで ≒60s）
    const val INTERVAL_MAX_MS = 150_000L   // 指数の裾を切る上限（間延びしすぎ防止）

    // 【流星群イベント・2026-07-19 差し戻し④】ごく低確率で「とんでも流星群」を混ぜる（面白さの当たり枠）。
    //   スポーン1回ごとに SHOWER_PROB で群へ分岐＝全スポーンの ~2.5%（数十分〜数時間に1度の当たり）。
    //   群は 5〜9本を 3〜6秒へ密集させ、パターン混在・進入位置/角度をばらす。視認性は通常規律を一段だけ譲る
    //   （尾α上限を通常0.42→0.52へ+0.1緩和）。ただし芯は白でMeteorCanvas側が上限0.6キャップ＝テキスト直上の眩惑は避ける節度。
    const val SHOWER_PROB = 0.025f         // 群への分岐確率（全スポーンの ~2.5%＝ごく稀）
    const val SHOWER_MIN_COUNT = 5         // 群の最小本数
    const val SHOWER_MAX_COUNT = 9         // 群の最大本数
    const val SHOWER_DURATION_MIN_MS = 3_000 // 密集の最短掃過（この間に全本が流れ切る）
    const val SHOWER_DURATION_MAX_MS = 6_000 // 密集の最長掃過
    const val SHOWER_TAIL_ALPHA_CAP = 0.52f  // 群だけ尾α上限を +0.1 緩和（通常0.42＝可読規律より一段譲る）
}

/** 流星の4パターン（重み付き抽選）。weight は相対比（合計自由・pick 側で正規化）。 */
internal enum class MeteorPattern(val weight: Float) {
    FAINT(0.52f),      // 微流星: 短く・かすか・やや速い（最頻＝「ふと流れた」日常感）
    STANDARD(0.30f),   // 標準の一筋
    LONG_SLOW(0.13f),  // 長尺: 長い尾・低速（ゆっくり流れる大物）
    DOUBLE(0.05f),     // 二連: 近接2本（ごく稀）
}

/** u∈[0,1) を重み累積で1パターンへ写す（純関数＝JVMテストでパターン網羅・希少性を検証）。 */
internal fun pickMeteorPattern(u: Float): MeteorPattern {
    val total = MeteorPattern.entries.fold(0f) { a, p -> a + p.weight }
    val uu = u.coerceIn(0f, 0.999999f) * total
    var acc = 0f
    for (p in MeteorPattern.entries) {
        acc += p.weight
        if (uu < acc) return p
    }
    return MeteorPattern.STANDARD
}

/** 次の流星までの待機（指数分布 inter-arrival・純関数＝間隔範囲/平均をJVMテストで検証）。u は (0,1] の一様乱数。 */
internal fun nextMeteorDelayMs(u: Float): Long {
    val uu = u.coerceIn(1e-6f, 1f)
    val expMs = (-ln(uu)) * MeteorTuning.INTERVAL_MEAN_MS // 指数分布（平均 INTERVAL_MEAN_MS）
    return (MeteorTuning.INTERVAL_MIN_MS + expMs.toLong()).coerceAtMost(MeteorTuning.INTERVAL_MAX_MS)
}

// 一筋の流星（名目座標・生成時確定）。phase＝イベント内の時間オフセット（二連の2本目を僅かに遅らせる／単発は 0）。
internal class MeteorStreak(
    val nx: Float, val ny: Float,
    val vx: Float, val vy: Float,
    val len: Float,
    val tailAlpha: Float, // 尾の基準α（通常は可読域上限 0.42・流星群のみ +0.1 緩和の 0.52。芯は MeteorCanvas 側で 0.6 キャップ＝眩惑抑止）
    val color: Color,
    val phase: Float,
)
internal class MeteorEvent(val streaks: List<MeteorStreak>, val durationMs: Int)

/**
 * 1筋を組む（角度/位置/長さ/速度/尾αをパターン別レンジで抽選）。単発・二連・流星群で共用。
 * tailAlphaCap で尾αの上限を可変にする＝通常は可読域 0.42、群だけ +0.1 緩和した 0.52 を渡す（芯は MeteorCanvas 側で別キャップ）。
 */
private fun buildStreak(pattern: MeteorPattern, phase: Float, tailAlphaCap: Float, r: () -> Float): MeteorStreak {
    val fromLeft = r() < 0.5f
    val nx = if (fromLeft) 10f + r() * 150f else 230f + r() * 150f // 進入位置に抽選幅
    val ny = 60f + r() * 300f
    val dir = if (fromLeft) 1f else -1f
    // パターン別の 速度 / 尾長 / 尾α。尾αは呼び出し側のキャップに従属（芯は離散点ゆえ別＝面輝度規律に非抵触）。
    val speed: Float; val len: Float; val ta: Float
    when (pattern) {
        MeteorPattern.FAINT     -> { speed = 3.2f + r() * 1.4f; len = 22f + r() * 14f; ta = 0.14f + r() * 0.10f } // 短・速・かすか
        MeteorPattern.STANDARD  -> { speed = 2.8f + r() * 1.2f; len = 40f + r() * 20f; ta = 0.26f + r() * 0.14f }
        MeteorPattern.LONG_SLOW -> { speed = 1.7f + r() * 0.8f; len = 70f + r() * 36f; ta = 0.20f + r() * 0.10f } // 長・低速
        MeteorPattern.DOUBLE    -> { speed = 3.0f + r() * 1.0f; len = 34f + r() * 16f; ta = 0.20f + r() * 0.10f }
    }
    val angle = 0.5f + r() * 0.5f // 進入角の幅（下向き基調＝vy=speed*angle）
    return MeteorStreak(
        nx = nx, ny = ny, vx = dir * speed, vy = speed * angle, len = len,
        tailAlpha = ta.coerceAtMost(tailAlphaCap),  // 可読域上限（群のみ +0.1 緩和・本文直上の眩惑は芯キャップで抑止）
        color = starTempColor(0.3f + r() * 0.4f),   // 【色b】暖寄りの一筋（芯だけ後で白）
        phase = phase,
    )
}

/** パターン＋乱数から通常の流星イベント（1〜2筋）を組む。尾αは可読域上限 0.42。 */
internal fun buildMeteorEvent(pattern: MeteorPattern, r: () -> Float): MeteorEvent {
    return if (pattern == MeteorPattern.DOUBLE) {
        // 二連＝近接2本（2本目を phase ぶん遅らせる）。掃過はやや長めに取り2本が重なる間を作る。
        MeteorEvent(
            listOf(buildStreak(pattern, 0f, 0.42f, r), buildStreak(pattern, 0.14f + r() * 0.12f, 0.42f, r)),
            durationMs = 1200,
        )
    } else {
        val dur = when (pattern) { MeteorPattern.LONG_SLOW -> 1700; MeteorPattern.FAINT -> 850; else -> 1050 }
        MeteorEvent(listOf(buildStreak(pattern, 0f, 0.42f, r)), durationMs = dur)
    }
}

/** u∈[0,1) を群/通常の分岐へ写す（純関数＝JVMテストで群の希少性を検証）。true で流星群。 */
internal fun isShowerSpawn(u: Float): Boolean = u.coerceIn(0f, 1f) < MeteorTuning.SHOWER_PROB

/**
 * 【流星群・差し戻し④】5〜9本を 3〜6秒へ密集させる「とんでも流星群」。パターン混在・進入位置/角度ばらけ。
 * phase を [0,0.8) に散らして掃過の全域で入れ替わり流れる（同時刻に固まらせない）。尾αは +0.1 緩和（0.52）。
 * reduce-motion 無効は呼び出し元スケジューラの早期 return が担保（通常流星と同じ規律）。
 */
internal fun buildMeteorShower(r: () -> Float): MeteorEvent {
    val span = MeteorTuning.SHOWER_MAX_COUNT - MeteorTuning.SHOWER_MIN_COUNT
    val count = MeteorTuning.SHOWER_MIN_COUNT + (r() * (span + 1)).toInt().coerceAtMost(span) // 5..9 本
    val durSpan = MeteorTuning.SHOWER_DURATION_MAX_MS - MeteorTuning.SHOWER_DURATION_MIN_MS
    val dur = MeteorTuning.SHOWER_DURATION_MIN_MS + (r() * durSpan).toInt() // 3000..6000ms
    val streaks = (0 until count).map {
        // パターンは各本 独立抽選＝微流星〜長尺が入り混じる（速度・尾長がばらけて「群れ」感）。
        buildStreak(pickMeteorPattern(r()), phase = r() * 0.8f, tailAlphaCap = MeteorTuning.SHOWER_TAIL_ALPHA_CAP, r = r)
    }
    return MeteorEvent(streaks, durationMs = dur)
}

// 流星の状態＋スケジューラ本体を保持（rememberMeteorHost が生成）。@Stable＝read 追跡は event/progress の各 State に委ねる。
@Stable
internal class MeteorHost {
    val progress = Animatable(0f)
    var event by mutableStateOf<MeteorEvent?>(null)
}

/**
 * 流星スケジューラ（実時間抽選・ナビ無相関）。SkyBackdropM の `if (hidden) return` より前で呼ぶ＝読書往復で破棄→
 * 再起動しない（B の真因対処）。LaunchedEffect のキーは reduceMotion のみ＝hidden 切替で再起動しない。
 */
@Composable
internal fun rememberMeteorHost(reduceMotion: Boolean): MeteorHost {
    val host = remember { MeteorHost() }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect // reduce-motion: 流星無効（不変）
        val rnd = Random(System.nanoTime())     // 実エントロピー seed＝毎起動で列が変わる（決定性を持ち込まない）
        fun r(): Float = rnd.nextFloat()
        while (isActive) {
            delay(nextMeteorDelayMs(r()))        // 初回も抽選＝固定初動の確定出現を廃す（ナビ・スクロールと無相関）
            // ごく低確率（~2.5%）で流星群へ分岐＝当たり枠。大半は通常の単発/二連。
            val ev = if (isShowerSpawn(r())) buildMeteorShower(::r) else buildMeteorEvent(pickMeteorPattern(r()), ::r)
            host.event = ev
            host.progress.snapTo(0f)
            host.progress.animateTo(1f, tween(ev.durationMs, easing = LinearEasing))
            host.event = null
        }
    }
    return host
}

/** 流星の描画（draw フェーズ遅延読み＝event/progress を Canvas ラムダ内で読む・再コンポーズを起こさない）。 */
@Composable
internal fun MeteorCanvas(host: MeteorHost, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ev = host.event ?: return@Canvas
        val t = host.progress.value
        val sx = size.width / NW
        val sy = size.height / NH
        for (s in ev.streaks) {
            val lt = if (s.phase >= 1f) 1f else ((t - s.phase) / (1f - s.phase)) // イベント内の各筋のローカル進行
            if (lt <= 0f || lt >= 1f) continue
            val frames = 62f * lt // 掃過（≒62フレーム）。head は速度×経過フレームで進む。
            val hx = (s.nx + s.vx * frames) * sx
            val hy = (s.ny + s.vy * frames) * sy
            val life = 1f - lt // 淡くフェードアウト
            val vlen = hypot(s.vx, s.vy).coerceAtLeast(1e-4f)
            val tailX = hx - s.vx / vlen * s.len * sx
            val tailY = hy - s.vy / vlen * s.len * sy
            drawLine(
                Brush.linearGradient(
                    listOf(s.color.copy(alpha = s.tailAlpha * life), Color.Transparent),
                    start = Offset(hx, hy), end = Offset(tailX, tailY),
                ),
                start = Offset(hx, hy), end = Offset(tailX, tailY),
                strokeWidth = 1.3f * sx, cap = StrokeCap.Round,
            )
            // 芯（離散点）＝白。尾α+0.2 を上限 0.6 でキャップ（可読域＝テキスト上でも眩しすぎない）。
            drawCircle(
                StarCoreSeizu.copy(alpha = (s.tailAlpha + 0.2f).coerceAtMost(0.6f) * life),
                radius = 1.2f * sx, center = Offset(hx, hy),
            )
        }
    }
}
