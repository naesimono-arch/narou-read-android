package com.novelreader.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

// ============================================================
// ShioriHighLoadChoreo — 栞アニメ高負荷モードの振り付けデータ正本（2026-08-06 ユーザー裁定＝モック全体GO）。
//
// 正本＝docs/design-candidates/bookshelf-shiori-highload-K.html の @keyframes t0〜t8（装飾層）と
// bf0〜bf8（線追従層・恒久規則 2026-08-06）。**数値はモックの値をそのまま写す**（周期・角度・スケールは
// 算術参加値＝勝手に丸めない）。keyframes 名は各トラックのコメントに引用する。
//
// なぜ「描画コード直書き」でなく data として持つか: モックは9種の非画一性を機械照合で担保している
// （①keyframes 非共有 ②プロパティ組合せ9通り相異 ③周期9種相異 ④イージング使い回し禁止）。
// Compose 側でも同じ照合をテスト（ShioriHighLoadChoreoTest）で回すため、周期・イージング・プロパティ・
// キー値を宣言的に列挙し、描画（ShioriHighLoadTips.kt）はこの表から値を引くだけにする
// （モック申し送り⑦「〈進行度→値〉表の差し替えで済む」の直訳）。
//
// 線追従層（bf・.flw）は「全 tip 共通の物理規則」のため非画一性照合の対象外（モック側の注記と同じ）。
// イージングが各 tip の主動作と同値なのも意図（連成して見せるため＝bf 系は重複制約なし）。
// ============================================================

/** 装飾層が動かすプロパティの語彙。モック②の内訳（transform 4種＋署名プロパティ5種）と1:1。 */
internal enum class ShioriChoreoProp { ROTATE, TRANSLATE_X, TRANSLATE_Y, SCALE, SKEW_X, STROKE_WIDTH, FILTER, OPACITY, DASH_OFFSET }

/**
 * CSS cubic-bezier の制御点。Easing 実体でなく座標を正本に持つのは、テストが「tip 間でイージングを
 * 使い回していない」を値比較で照合するため（CubicBezierEasing は equals 契約を保証しない）。
 */
internal data class ChoreoBezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    /** Compose 側の等価 easing（モック申し送り⑦: カスタム cubic-bezier は CubicBezierEasing へ写す）。 */
    val easing: Easing by lazy { CubicBezierEasing(x1, y1, x2, y2) }
}

/** キーフレーム1点。pct=CSS の % 位置（0..100）／a・b=値（scale(x,y)・filter(brightness,saturate) は2成分）。 */
internal class ChoreoKey(val pct: Float, val a: Float, val b: Float = a)

/**
 * 1本のアニメーション（CSS の「1 keyframes × 1 animation 指定」に対応）。
 *
 * サンプリングは CSS と同じ規則: animation-timing-function はキーフレーム**区間ごと**に効く
 * （全周期に1回ではない）＝区間内の局所進行度 u に easing を掛けて線形補間する。
 * delaySec は位相シフトとして扱う（負方向へ巻き戻しても mod で同位相＝infinite ループの定常状態と等価。
 * モックの静止値はすべて静的描画と同値なので、初回サイクルの見え方の差は生じない）。
 */
internal class ChoreoTrack(
    val prop: ShioriChoreoProp,
    val periodSec: Float,
    val bezier: ChoreoBezier,
    val delaySec: Float,
    val keys: List<ChoreoKey>,
) {
    /** 第1成分（rotate deg・translate px・scaleX・stroke-width・brightness 等）。 */
    fun aAt(tSec: Float): Float = sample(tSec, first = true)

    /** 第2成分（scaleY・saturate）。1成分トラックでは a と同値。 */
    fun bAt(tSec: Float): Float = sample(tSec, first = false)

    private fun sample(tSec: Float, first: Boolean): Float {
        // mod は負の t でも [0, period) に畳む（Kotlin の Float.mod は非負剰余）＝位相シフトの定常解。
        val f = (tSec - delaySec).mod(periodSec) / periodSec * 100f
        var i = 0
        while (i < keys.size - 1 && keys[i + 1].pct <= f) i++
        val k0 = keys[i]
        if (i >= keys.size - 1) return if (first) k0.a else k0.b
        val k1 = keys[i + 1]
        val span = k1.pct - k0.pct
        if (span <= 0f) return if (first) k1.a else k1.b
        val u = ((f - k0.pct) / span).coerceIn(0f, 1f)
        val e = bezier.easing.transform(u)
        val v0 = if (first) k0.a else k0.b
        val v1 = if (first) k1.a else k1.b
        return v0 + (v1 - v0) * e
    }
}

/**
 * 1 tip の振り付け一式。tracks=装飾層（9種相異の機械照合対象）／follow=線追従層 bf（照合対象外・
 * 恒久規則 2026-08-06: 付け根支点・先端と同周期・微遅相 BF_LAG は各 follow トラックの delaySec が持つ）。
 */
internal class TipChoreo(
    val tip: Int,
    val name: String,
    val tracks: List<ChoreoTrack>,
    val follow: List<ChoreoTrack>,
) {
    /** 非画一性照合②の対象＝装飾層が動かすプロパティ集合。 */
    val props: Set<ShioriChoreoProp> get() = tracks.map { it.prop }.toSet()

    /** 非画一性照合③の対象＝装飾層の周期集合（tip3 のみ左右 2 周期）。 */
    val periods: Set<Float> get() = tracks.map { it.periodSec }.toSet()

    /** 非画一性照合④の対象＝装飾層のイージング集合。 */
    val beziers: Set<ChoreoBezier> get() = tracks.map { it.bezier }.toSet()
}

private fun k(pct: Float, a: Float, b: Float = a) = ChoreoKey(pct, a, b)

// ── イージング正本（モックの cubic-bezier をそのまま）。tip を跨ぐ共有なし＝照合④。 ──
private val B0_MAIN = ChoreoBezier(0.85f, 0f, 0.12f, 1f)    // t0-L/R「溜めてから鋭く放つ」
private val B0_SUB = ChoreoBezier(0.3f, 0.78f, 0.42f, 1f)    // t0-flex/t0-G/bf0
private val B1_MAIN = ChoreoBezier(0.36f, 0f, 0.64f, 1f)    // t1（正弦近似＝本物の振り子）/bf1
private val B1_SUB = ChoreoBezier(0.2f, 0.7f, 0.4f, 1f)      // t1-w
private val B2_MAIN = ChoreoBezier(0.16f, 0.84f, 0.3f, 1f)   // t2/t2bar/bf2「急に効いて粘る」
private val B3_ROT_A = ChoreoBezier(0.42f, 0.02f, 0.4f, 1f)  // t3-a/bf3
private val B3_SKEW = ChoreoBezier(0.34f, 0.1f, 0.5f, 1f)    // t3-as/t3-bs
private val B3_ROT_B = ChoreoBezier(0.38f, 0.06f, 0.46f, 0.98f) // t3-b
private val B4_MAIN = ChoreoBezier(0.25f, 0.46f, 0.45f, 0.94f) // t4-s/t4-b/bf4
private val B4_G = ChoreoBezier(0.3f, 0.5f, 0.5f, 1f)        // t4-g
private val B5_MAIN = ChoreoBezier(0.32f, 0.06f, 0.24f, 1f)  // t5-s/t5-b/bf5
private val B5_G = ChoreoBezier(0.4f, 0.1f, 0.3f, 1f)        // t5-g
private val B6_MAIN = ChoreoBezier(0.5f, 0.02f, 0.5f, 0.98f)  // t6-L/R/bf6
private val B6_O = ChoreoBezier(0.45f, 0.15f, 0.55f, 0.85f)   // t6-oL/oR
private val B7_MAIN = ChoreoBezier(0.9f, 0.02f, 0.35f, 1f)   // t7-t/bf7「唐突なひと払い」
private val B7_SUB = ChoreoBezier(0.7f, 0f, 0.4f, 1f)       // t7-ink/t7-b
private val B8_MAIN = ChoreoBezier(0.55f, 0f, 0.28f, 1f)    // t8/bf8「読経の拍」
private val B8_G = ChoreoBezier(0.5f, 0.05f, 0.35f, 1f)      // t8-g

// ── tip0 魚尾「水中の一閃」7.4s・瞬発（keyframes t0-L/t0-R/t0-flex/t0-G・bf0） ──
internal object ShioriT0 {
    /** t0-L: 長い静止→二枚の尾の交差（減衰）。 */
    val ROT_L = ChoreoTrack(ShioriChoreoProp.ROTATE, 7.4f, B0_MAIN, 0f,
        listOf(k(0f, 0f), k(66f, 0f), k(69f, 15f), k(73f, -5f), k(78f, 8f), k(84f, -2f), k(90f, 0f), k(100f, 0f)))
    /** t0-R: 左の逆相。 */
    val ROT_R = ChoreoTrack(ShioriChoreoProp.ROTATE, 7.4f, B0_MAIN, 0f,
        listOf(k(0f, 0f), k(66f, 0f), k(69f, -15f), k(73f, 5f), k(78f, -8f), k(84f, 2f), k(90f, 0f), k(100f, 0f)))
    /** t0-flex: 刃の撓み（.09s 遅れ）。静止値 2.6 は SHIORI_TIPS の線幅と同値＝静止意匠不変。 */
    val FLEX = ChoreoTrack(ShioriChoreoProp.STROKE_WIDTH, 7.4f, B0_SUB, 0.09f,
        listOf(k(0f, 2.6f), k(64f, 2.6f), k(70f, 3.25f), k(75f, 2.3f), k(82f, 2.75f), k(92f, 2.6f), k(100f, 2.6f)))
    /** t0-G: 推進の反動で全体が浮く。 */
    val GROUP_TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 7.4f, B0_SUB, 0f,
        listOf(k(0f, 0f), k(66f, 0f), k(70f, -1.1f), k(82f, -0.4f), k(96f, 0f), k(100f, 0f)))
    /** bf0: 交差は左右対称で横の合力が相殺＝snap の横波だけが紐を伝う微震（最小追従）。BF_LAG=.06。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 7.4f, B0_SUB, 0.06f,
        listOf(k(0f, 0f), k(66f, 0f), k(70f, 0.55f), k(74f, -0.4f), k(79f, 0.25f), k(85f, -0.1f), k(92f, 0f), k(100f, 0f)))
}

// ── tip1 一粒「振り子の余韻」9.5s・律儀（keyframes t1/t1-w・bf1） ──
internal object ShioriT1 {
    /** t1: 指数減衰9段の振り子（支点=アタッチ点）。 */
    val ROT = ChoreoTrack(ShioriChoreoProp.ROTATE, 9.5f, B1_MAIN, 0f,
        listOf(k(0f, 0f), k(6f, 11f), k(13f, -8f), k(20f, 5.6f), k(27f, -3.8f), k(34f, 2.5f),
            k(41f, -1.5f), k(48f, 0.8f), k(55f, -0.3f), k(62f, 0f), k(100f, 0f)))
    /** t1-w: 最下点通過（振れの節 9.5/16.5/23.5/30.5%）で重さに縦つぶれ（.05s 遅れ）。 */
    val SQUASH = ChoreoTrack(ShioriChoreoProp.SCALE, 9.5f, B1_SUB, 0.05f,
        listOf(k(0f, 1f, 1f), k(3f, 1f, 1f), k(9.5f, 0.93f, 1.08f), k(13f, 1f, 1f), k(16.5f, 0.955f, 1.05f),
            k(20f, 1f, 1f), k(23.5f, 0.972f, 1.03f), k(27f, 1f, 1f), k(30.5f, 0.985f, 1.016f), k(34f, 1f, 1f), k(100f, 1f, 1f)))
    /** bf1: 振り子の反力＝同符号・約1/9 振幅で同じ減衰をなぞる。BF_LAG=.12。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 9.5f, B1_MAIN, 0.12f,
        listOf(k(0f, 0f), k(6f, 1.3f), k(13f, -1f), k(20f, 0.7f), k(27f, -0.45f), k(34f, 0.3f),
            k(41f, -0.18f), k(48f, 0.1f), k(55f, -0.04f), k(62f, 0f), k(100f, 0f)))
}

// ── tip2 結び玉「締まり直す結び」8.8s・重い（keyframes t2/t2bar・bf2）。
//    t2 は translateY+rotate+scale の複合1本＝CSS の transform 成分は独立補間されるため3トラックへ分解
//    （キー位置・easing・周期は共有＝モックと同一の見え）。 ──
internal object ShioriT2 {
    val TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 8.8f, B2_MAIN, 0f,
        listOf(k(0f, 0f), k(55f, 0f), k(60f, 0.9f), k(64f, 0.7f), k(72f, -0.2f), k(80f, 0f), k(88f, 0f), k(100f, 0f)))
    val ROT = ChoreoTrack(ShioriChoreoProp.ROTATE, 8.8f, B2_MAIN, 0f,
        listOf(k(0f, 0f), k(55f, 0f), k(60f, -4f), k(64f, -4f), k(72f, 2f), k(80f, -0.6f), k(88f, 0f), k(100f, 0f)))
    val SC = ChoreoTrack(ShioriChoreoProp.SCALE, 8.8f, B2_MAIN, 0f,
        listOf(k(0f, 1f, 1f), k(55f, 1f, 1f), k(60f, 1.12f, 0.86f), k(64f, 1.09f, 0.89f), k(72f, 0.97f, 1.04f),
            k(80f, 1.01f, 0.99f), k(88f, 1f, 1f), k(100f, 1f, 1f)))
    /** t2bar: 唯一の「先端のみ可動」緩和＝締まりの張力が棒へ伝う scaleY（origin=棒の付け根・玉と同位相）。 */
    val BAR_SY = ChoreoTrack(ShioriChoreoProp.SCALE, 8.8f, B2_MAIN, 0f,
        listOf(k(0f, 1f), k(55f, 1f), k(60f, 1.02f), k(64f, 1.015f), k(72f, 0.995f), k(80f, 1f), k(88f, 1f), k(100f, 1f)))
    /** bf2: 座り直しの傾ぎに追従（縦張力は t2bar＝棒側が担当・別トラックで衝突しない）。BF_LAG=.08。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 8.8f, B2_MAIN, 0.08f,
        listOf(k(0f, 0f), k(55f, 0f), k(61f, -0.5f), k(66f, -0.45f), k(73f, 0.25f), k(81f, -0.08f), k(88f, 0f), k(100f, 0f)))
}

// ── tip3 二又房「すれ違う二本」3.9s/5.3s・気まぐれ（keyframes t3-a/t3-as/t3-b/t3-bs・bf3）。
//    周期比が整数でない左右＝重なりが長く繰り返さない（モックの本題「同系内の差別化」）。 ──
internal object ShioriT3 {
    val ROT_L = ChoreoTrack(ShioriChoreoProp.ROTATE, 3.9f, B3_ROT_A, 0f,
        listOf(k(0f, 0f), k(16f, 0f), k(30f, -3.8f), k(45f, 2.6f), k(60f, -1.3f), k(74f, 0.4f), k(86f, 0f), k(100f, 0f)))
    /** t3-as: 撓み（skewX）が一拍（.13s）遅れて追う。 */
    val SKEW_L = ChoreoTrack(ShioriChoreoProp.SKEW_X, 3.9f, B3_SKEW, 0.13f,
        listOf(k(0f, 0f), k(16f, 0f), k(32f, -1.1f), k(47f, 0.7f), k(62f, -0.3f), k(80f, 0f), k(100f, 0f)))
    val ROT_R = ChoreoTrack(ShioriChoreoProp.ROTATE, 5.3f, B3_ROT_B, 0f,
        listOf(k(0f, 0f), k(32f, 0f), k(44f, 3.4f), k(58f, -2.4f), k(71f, 1.2f), k(84f, -0.4f), k(94f, 0f), k(100f, 0f)))
    val SKEW_R = ChoreoTrack(ShioriChoreoProp.SKEW_X, 5.3f, B3_SKEW, 0.18f,
        listOf(k(0f, 0f), k(32f, 0f), k(46f, 1f), k(60f, -0.6f), k(75f, 0.25f), k(90f, 0f), k(100f, 0f)))
    /** bf3: 二周期干渉の合力は非周期＝支配的な左織り（3.9s）のみへ緩く追従する近似（モック注記どおり）。BF_LAG=.1。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 3.9f, B3_ROT_A, 0.1f,
        listOf(k(0f, 0f), k(18f, 0f), k(32f, -0.55f), k(47f, 0.35f), k(62f, -0.18f), k(76f, 0.06f), k(88f, 0f), k(100f, 0f)))
}

// ── tip4 三又房「波が渡る」6.8s・素直（keyframes t4-s/t4-b/t4-g・bf4） ──
internal object ShioriT4 {
    /** t4-s: 3本共通の波形。左→中→右の位相差は STRAND_DOFFS（モック JS の 0/.16/.32s）。 */
    val STRAND = ChoreoTrack(ShioriChoreoProp.ROTATE, 6.8f, B4_MAIN, 0f,
        listOf(k(0f, 0f), k(58f, 0f), k(64f, 7f), k(72f, -4.6f), k(80f, 2.4f), k(87f, -1f), k(92f, 0f), k(100f, 0f)))
    val STRAND_DOFFS = floatArrayOf(0f, 0.16f, 0.32f)
    /** t4-b: 頭玉は反動で逆相に振り返る（.05s 遅れ・支点=アタッチ点）。 */
    val HEAD = ChoreoTrack(ShioriChoreoProp.ROTATE, 6.8f, B4_MAIN, 0.05f,
        listOf(k(0f, 0f), k(58f, 0f), k(66f, -2.6f), k(76f, 1.4f), k(92f, 0f), k(100f, 0f)))
    /** t4-g: 房全体は波の運動量に引かれて横へ流れる（.1s 遅れ）。 */
    val GROUP_TX = ChoreoTrack(ShioriChoreoProp.TRANSLATE_X, 6.8f, B4_G, 0.1f,
        listOf(k(0f, 0f), k(56f, 0f), k(68f, -0.75f), k(82f, 0.4f), k(90f, -0.15f), k(96f, 0f), k(100f, 0f)))
    /** bf4: 波の運動量と同方向へひと撓みして減衰。BF_LAG=.12。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 6.8f, B4_MAIN, 0.12f,
        listOf(k(0f, 0f), k(58f, 0f), k(66f, 0.8f), k(75f, -0.5f), k(83f, 0.25f), k(89f, -0.08f), k(94f, 0f), k(100f, 0f)))
}

// ── tip5 総角「息を吸う房」11s・ゆったり（keyframes t5-s/t5-b/t5-g・bf5） ──
internal object ShioriT5 {
    /** t5-s: 値は係数（deg/k）＝描画側で k（-2..2）を掛ける（モックの calc(var(--k) * 4.5deg) の直訳）。 */
    val STRAND = ChoreoTrack(ShioriChoreoProp.ROTATE, 11f, B5_MAIN, 0f,
        listOf(k(0f, 0f), k(20f, 0f), k(38f, 4.5f), k(52f, 3.9f), k(60f, 4.2f), k(78f, 0f), k(100f, 0f)))
    /** 5本の位相差（モック JS の j*0.07s・j=0..4 は k=-2..2 の順）。 */
    val STRAND_DOFFS = floatArrayOf(0f, 0.07f, 0.14f, 0.21f, 0.28f)
    /** t5-b: 頭玉の浮き。 */
    val HEAD_TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 11f, B5_MAIN, 0f,
        listOf(k(0f, 0f), k(20f, 0f), k(40f, -0.5f), k(60f, -0.4f), k(78f, 0f), k(100f, 0f)))
    /** t5-g: 開く息に合わせた墨の濃淡（a=brightness・b=saturate・.2s 遅れ）。色相は変えず濃度だけ＝和モダンの品位。
     *  Compose に filter の等価物は無いため、描画側が HSL で色そのものを算出して渡す（モック申し送り⑦）。 */
    val FILTER = ChoreoTrack(ShioriChoreoProp.FILTER, 11f, B5_G, 0.2f,
        listOf(k(0f, 1f, 1f), k(18f, 1f, 1f), k(42f, 0.94f, 1.14f), k(62f, 0.97f, 1.08f), k(82f, 1f, 1f), k(100f, 1f, 1f)))
    /** bf5: 扇の開きは左右対称＝横の合力ゼロ→縦の張力のみ微伸び scaleY で追従（最小追従・例外ではない）。BF_LAG=.18。 */
    val BF = ChoreoTrack(ShioriChoreoProp.SCALE, 11f, B5_MAIN, 0.18f,
        listOf(k(0f, 1f), k(20f, 1f), k(40f, 1.012f), k(62f, 1.007f), k(80f, 1f), k(100f, 1f)))
}

// ── tip6 蝶結び「眠る蝶の呼吸」12s・ためらう（keyframes t6-L/t6-R/t6-oL/t6-oR・bf6） ──
internal object ShioriT6 {
    /** t6-L: 左羽の張り（交互）＋稀な同時羽ばたき（86/89%）。 */
    val WING_L = ChoreoTrack(ShioriChoreoProp.SCALE, 12f, B6_MAIN, 0f,
        listOf(k(0f, 1f, 1f), k(18f, 1.06f, 0.97f), k(36f, 1f, 1f), k(55f, 1f, 1f), k(72f, 1f, 1f),
            k(86f, 0.9f, 1.05f), k(89f, 1.04f, 0.99f), k(93f, 1f, 1f), k(100f, 1f, 1f)))
    val WING_R = ChoreoTrack(ShioriChoreoProp.SCALE, 12f, B6_MAIN, 0f,
        listOf(k(0f, 1f, 1f), k(18f, 1f, 1f), k(36f, 1f, 1f), k(55f, 1.06f, 0.97f), k(72f, 1f, 1f),
            k(86f, 0.9f, 1.05f), k(89f, 1.04f, 0.99f), k(93f, 1f, 1f), k(100f, 1f, 1f)))
    /** t6-oL: 張った羽の墨が薄らぐ（.28s 遅れ＝張力の可視化）。 */
    val ALPHA_L = ChoreoTrack(ShioriChoreoProp.OPACITY, 12f, B6_O, 0.28f,
        listOf(k(0f, 1f), k(8f, 1f), k(22f, 0.8f), k(44f, 1f), k(72f, 1f), k(86f, 0.72f), k(90f, 0.95f), k(93f, 1f), k(100f, 1f)))
    val ALPHA_R = ChoreoTrack(ShioriChoreoProp.OPACITY, 12f, B6_O, 0.28f,
        listOf(k(0f, 1f), k(8f, 1f), k(44f, 1f), k(59f, 0.8f), k(72f, 1f), k(86f, 0.72f), k(90f, 0.95f), k(93f, 1f), k(100f, 1f)))
    /** bf6: 左右交互の張りへ微傾ぎ＋稀な同時羽ばたきは左右対称＝縦張力 scaleY で応える（rotate と scaleY の2本）。BF_LAG=.15。 */
    val BF_ROT = ChoreoTrack(ShioriChoreoProp.ROTATE, 12f, B6_MAIN, 0.15f,
        listOf(k(0f, 0f), k(8f, 0f), k(18f, -0.3f), k(36f, 0f), k(55f, 0.3f), k(72f, 0f), k(86f, 0f), k(90f, 0f), k(96f, 0f), k(100f, 0f)))
    val BF_SY = ChoreoTrack(ShioriChoreoProp.SCALE, 12f, B6_MAIN, 0.15f,
        listOf(k(0f, 1f), k(8f, 1f), k(18f, 1f), k(36f, 1f), k(55f, 1f), k(72f, 1f), k(86f, 1.012f), k(90f, 0.997f), k(96f, 1f), k(100f, 1f)))
}

// ── tip7 玉と尾「気まぐれの尾」10.5s・唐突（keyframes t7-t/t7-ink/t7-b・bf7） ──
internal object ShioriT7 {
    /** t7-t: ふいの大払い→頂で一拍→二段で戻る。 */
    val TAIL = ChoreoTrack(ShioriChoreoProp.ROTATE, 10.5f, B7_MAIN, 0f,
        listOf(k(0f, 0f), k(30f, 0f), k(38f, 14f), k(48f, 12.5f), k(56f, -3f), k(63f, 4f), k(70f, 0f), k(100f, 0f)))
    /** t7-ink: 払い際に穂先の墨が細る（.12s 遅れ）。dasharray 11 11＝路長ちょうど＝offset 0 の静止時は隙間ゼロ＝意匠不変。 */
    val INK = ChoreoTrack(ShioriChoreoProp.DASH_OFFSET, 10.5f, B7_SUB, 0.12f,
        listOf(k(0f, 0f), k(32f, 0f), k(40f, 3.2f), k(52f, 2.4f), k(60f, 0.6f), k(72f, 0f), k(100f, 0f)))
    /** t7-b: 玉は釣り合いに沈む（.06s 遅れ）。 */
    val BALL_TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 10.5f, B7_SUB, 0.06f,
        listOf(k(0f, 0f), k(30f, 0f), k(40f, 0.55f), k(58f, -0.3f), k(78f, 0f), k(100f, 0f)))
    /** bf7: 大払いの反力＝払い側へ傾ぎ、頂の一拍を紐も保ってから戻る。BF_LAG=.1。 */
    val BF = ChoreoTrack(ShioriChoreoProp.ROTATE, 10.5f, B7_MAIN, 0.1f,
        listOf(k(0f, 0f), k(30f, 0f), k(39f, 1.2f), k(49f, 1f), k(57f, -0.35f), k(64f, 0.3f), k(74f, 0f), k(100f, 0f)))
}

// ── tip8 数珠「繰る手」8.4s・律動（keyframes t8/t8-g・bf8）。
//    t8 は translateY+scale の複合1本＝tip2 と同じ理由で2トラックへ分解（キー位置・easing・周期は共有）。 ──
internal object ShioriT8 {
    val BEAD_TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 8.4f, B8_MAIN, 0f,
        listOf(k(0f, 0f), k(50f, 0f), k(58f, 1.1f), k(66f, 0f), k(82f, 0f), k(100f, 0f)))
    val BEAD_SC = ChoreoTrack(ShioriChoreoProp.SCALE, 8.4f, B8_MAIN, 0f,
        listOf(k(0f, 1f), k(50f, 1f), k(58f, 1.14f), k(66f, 1f), k(82f, 1f), k(100f, 1f)))
    /** 3珠の順送り位相差（モック JS の 0/.22/.44s・上→下）。 */
    val BEAD_DOFFS = floatArrayOf(0f, 0.22f, 0.44f)
    /** t8-g: 送り終えた紐全体が一拍（.3s）遅れて沈み込む。 */
    val GROUP_TY = ChoreoTrack(ShioriChoreoProp.TRANSLATE_Y, 8.4f, B8_G, 0.3f,
        listOf(k(0f, 0f), k(54f, 0f), k(70f, 0.45f), k(84f, 0.15f), k(94f, 0f), k(100f, 0f)))
    /** bf8: 繰りは縦のみで横動なし→縦張力の微伸び scaleY（最小追従・順送りの節に同期）。BF_LAG=.12。 */
    val BF = ChoreoTrack(ShioriChoreoProp.SCALE, 8.4f, B8_MAIN, 0.12f,
        listOf(k(0f, 1f), k(50f, 1f), k(59f, 1.01f), k(67f, 1.003f), k(74f, 1.006f), k(84f, 1f), k(100f, 1f)))
}

/** 高負荷アニメの対象は tip 0〜8 のみ（2026-08-06 裁定＝9〜173 への展開は合否後に別途）。 */
internal const val SHIORI_HIGHLOAD_TIP_COUNT = 9

/**
 * 9振り付けの正本一覧（モック FEAT 配列の Compose 側対応物）。順序は tipIndex と一致。
 * 構造テスト（ShioriHighLoadChoreoTest）が「周期・イージング・プロパティ集合が9種相異」をここで照合する。
 */
internal val SHIORI_HIGHLOAD_CHOREOS: List<TipChoreo> = listOf(
    TipChoreo(0, "魚尾", listOf(ShioriT0.ROT_L, ShioriT0.ROT_R, ShioriT0.FLEX, ShioriT0.GROUP_TY), listOf(ShioriT0.BF)),
    TipChoreo(1, "一粒", listOf(ShioriT1.ROT, ShioriT1.SQUASH), listOf(ShioriT1.BF)),
    TipChoreo(2, "結び玉", listOf(ShioriT2.TY, ShioriT2.ROT, ShioriT2.SC, ShioriT2.BAR_SY), listOf(ShioriT2.BF)),
    TipChoreo(3, "二又房", listOf(ShioriT3.ROT_L, ShioriT3.SKEW_L, ShioriT3.ROT_R, ShioriT3.SKEW_R), listOf(ShioriT3.BF)),
    TipChoreo(4, "三又房", listOf(ShioriT4.STRAND, ShioriT4.HEAD, ShioriT4.GROUP_TX), listOf(ShioriT4.BF)),
    TipChoreo(5, "総角", listOf(ShioriT5.STRAND, ShioriT5.HEAD_TY, ShioriT5.FILTER), listOf(ShioriT5.BF)),
    TipChoreo(6, "蝶結び", listOf(ShioriT6.WING_L, ShioriT6.WING_R, ShioriT6.ALPHA_L, ShioriT6.ALPHA_R), listOf(ShioriT6.BF_ROT, ShioriT6.BF_SY)),
    TipChoreo(7, "玉と尾", listOf(ShioriT7.TAIL, ShioriT7.INK, ShioriT7.BALL_TY), listOf(ShioriT7.BF)),
    TipChoreo(8, "数珠", listOf(ShioriT8.BEAD_TY, ShioriT8.BEAD_SC, ShioriT8.GROUP_TY), listOf(ShioriT8.BF)),
)

/**
 * アニメ合成の可否を1点で判定する純関数（テストで全組合せを固定するための切り出し）。
 * 動くのは〈高負荷トグル ON〉×〈生成色（accentOverride なし＝Web未取込の青磁署名は対象外）〉×
 * 〈tip 0..8〉×〈アニメ非低減〉のときだけ。false なら ShioriCover は従来の完全静止パスを通る。
 */
internal fun shioriHighLoadActive(
    enabled: Boolean,
    hasAccentOverride: Boolean,
    tipIndex: Int,
    reduceMotion: Boolean,
): Boolean = enabled && !hasAccentOverride && tipIndex in 0 until SHIORI_HIGHLOAD_TIP_COUNT && !reduceMotion

/**
 * カードごとの初期位相（秒）。title から決定論で導く＝同じ本は常に同じ位相・棚全体が同期して動かない。
 *
 * なぜモックの data-dl を写さないか: あれは「見せ場が順に来る」棚見本専用の手置き較正値で、動的な蔵書には
 * 対応が存在しない。目的（カード間の同期を崩す）だけを、栞生成と同じ決定論ハッシュ系列で引き継ぐ。
 * レンジ 0..16s は最長周期 12s を覆う任意値（位相として mod されるため上限の厳密性は不要）。
 */
internal fun shioriHighLoadPhaseSec(title: String): Float =
    ((shioriHash(title + "|HL") ushr 8) and 0xFFFF) / 65535f * 16f
