package com.novelreader.ui.components

import kotlin.math.floor

// ============================================================
// ShioriGenerator — 「栞」書影のタイトル駆動・決定論生成（純ロジック）
//
// 本棚 書架(グリッド)ビューの表紙は、紙地に引いた「色の棒＋その先端のワンポイント意匠」で
// 表す（意匠正本＝docs/design-candidates/bookshelf-shiori-final-D.html／ADR 0005）。
// 棒の色相・位置・長さ・先端の種類はすべて title から決定論的に導く＝同じ本は常に同じ絵。
//
// なぜ Compose から切り出して純関数にするか: 生成の決定論性（同じ title→同じ棒・先端）は
// 意匠の生命線なので、描画（Canvas）と分離して JVM 単体テストで固定する（ShioriGeneratorTest）。
// なぜ HTML 正本と同じ乱数系列（FNV-1a + mulberry32）を移植するか: title→絵の対応を
// /design で承認したモックと一致させ、実機とモックで「同じ本が同じ絵」になることを保証するため
// （kotlin の hashCode でも決定論は満たせるが、承認済みモックとは別の絵になり突き合わせ検証ができない）。
// ============================================================

/** カバー色相環（低彩度の和トーン一周・正本 variants/cover.html 準拠）。1冊＝この中の1色相。 */
internal val SHIORI_PALETTE = intArrayOf(20, 70, 140, 175, 200, 210, 260, 330)

/** 生成された栞のパラメータ。hue=色相(度)／xFrac=棒のx位置(幅比)／lenFrac=棒の長さ(高さ比)／tipIndex=先端の種類。 */
internal data class ShioriParams(
    val hue: Int,
    val xFrac: Float,
    val lenFrac: Float,
    val tipIndex: Int,
)

/**
 * FNV-1a ハッシュ（32bit）。JS 正本 `hashStr` の移植。
 * JS の charCodeAt は UTF-16 コードユニット、Kotlin の Char.code も同じ＝同一系列になる。
 * Int の乗算は 32bit で自動的に wrap し JS の Math.imul と等価。>>>0（符号なし化）は
 * mulberry32 側で Long マスクして扱うためここでは Int のビット列のまま返す。
 */
internal fun shioriHash(s: String): Int {
    var h = 0x811C9DC5.toInt() // 2166136261（FNV offset basis）を Int ビット列で
    for (ch in s) {
        h = h xor ch.code
        h *= 0x01000193 // 16777619（FNV prime）
    }
    return h
}

/**
 * mulberry32 疑似乱数。JS 正本の移植。seed から呼ぶたびに [0,1) を返すクロージャを生成。
 * JS の `>>>`（符号なし右シフト）は Kotlin の ushr、最終の `>>>0` は Long マスクで対応する。
 */
private fun mulberry32(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6D2B79F5 // 1831565813。Int 加算は 32bit で wrap ＝ JS の |0 と等価
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/**
 * title → 栞の色相（度）。`mulberry32(shioriHash(title))` の**初回**値で SHIORI_PALETTE を引く。
 *
 * なぜ shioriParams から切り出すか: 目録リスト（表紙を持たない行）の左端色帯や、書架⇔目録で共有する
 * アクセント（shioriAccentFor）は「色相だけ」を要し、棒の位置・長さ・先端は不要。ただし色相の導出は
 * shioriParams の hue と必ず同一系列でなければならない（同じ本が書架と目録で同じ色になる整合の生命線）。
 * そこで shioriParams 側も本関数を呼ぶことで、両者が構造的に同一値になることを保証する
 * （ShioriGeneratorTest で shioriHue(t) == shioriParams(t, N).hue として固定）。
 */
internal fun shioriHue(title: String): Int =
    SHIORI_PALETTE[floor(mulberry32(shioriHash(title))() * SHIORI_PALETTE.size).toInt()]

/**
 * title から栞のパラメータを決定論的に導く。
 * @param tipCount 先端意匠の総数（呼び出し側 SHIORI_TIPS.size を渡す＝先端を足すだけで選択に反映される）。
 *
 * 乱数系列は正本と同一: 色相は `mulberry32(hash(title))` の初回値（shioriHue と同一）、
 * 位置・長さ・先端は `mulberry32(hash(title+"|B"))` から順に x→len→tip の順で引く。
 */
internal fun shioriParams(title: String, tipCount: Int): ShioriParams {
    val hue = shioriHue(title)
    val rng = mulberry32(shioriHash(title + "|B"))
    val xFrac = (0.14 + (0.36 - 0.14) * rng()).toFloat()
    val lenFrac = (0.30 + (0.60 - 0.30) * rng()).toFloat()
    val tipIndex = floor(rng() * tipCount).toInt()
    return ShioriParams(hue = hue, xFrac = xFrac, lenFrac = lenFrac, tipIndex = tipIndex)
}
