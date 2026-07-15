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

/**
 * 先端意匠の総数（正本＝ShioriCover.kt の SHIORI_TIPS.size と一致させる）。
 *
 * なぜ Compose 非依存の本ファイルに定数を置くか: 取込時に真の乱数で tipIndex を引く DefaultBookRepository は
 * ドメイン層で、描画専用の ShioriCover.kt（Compose 依存）を import してはならない。総数の唯一の正本を純ロジック側に
 * 置くことで、repository が Compose 非依存のまま [0,SHIORI_TIP_COUNT) の抽選をできる。
 * SHIORI_TIPS 配列に先端を足したら本定数も必ず更新する（乖離は ShioriGeneratorTest の突合で赤くなる）。
 */
internal const val SHIORI_TIP_COUNT = 31

/** 栞の棒の長さ（高さ比）の抽選レンジ。shioriParams の決定論導出と取込時の真の乱数抽選が同一レンジを共有する
 *  ための唯一の正本（Double なのは shioriParams の既存ゴールデン値をビット単位で不変に保つため＝下記 why）。 */
internal const val SHIORI_LEN_FRAC_MIN = 0.30
internal const val SHIORI_LEN_FRAC_MAX = 0.60

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

/** 取込時に真の乱数で1回だけ引き BookEntity へ永続化する、栞の個体差パラメータ。 */
internal data class PersistedShiori(val tipIndex: Int, val lenFrac: Float)

/**
 * 取込時に真の乱数で栞の先端種と棒長を1回だけ抽選する（純ロジック＝Compose 非依存）。
 * @param random 乱数源。本番は DefaultBookRepository が `kotlin.random.Random.Default`（真の乱数）を渡す。
 *               テストは種固定 Random で決定論化する。
 *
 * lenFrac は shioriParams の決定論導出と同一レンジ（SHIORI_LEN_FRAC_MIN..MAX）から引く＝抽選値でも従来の
 * 見た目分布に収まる。tipIndex は [0,SHIORI_TIP_COUNT) の一様分布。ここで発生源を repository に閉じつつ
 * 総数・レンジの正本を純ロジック側に一元化する（repository が Compose 依存の ShioriCover を触らないため）。
 */
internal fun drawPersistedShiori(random: kotlin.random.Random): PersistedShiori {
    val tipIndex = random.nextInt(SHIORI_TIP_COUNT)
    val lenFrac = (SHIORI_LEN_FRAC_MIN + (SHIORI_LEN_FRAC_MAX - SHIORI_LEN_FRAC_MIN) * random.nextDouble()).toFloat()
    return PersistedShiori(tipIndex = tipIndex, lenFrac = lenFrac)
}

/**
 * title から栞のパラメータを導く。永続値（取込時に抽選済み）を渡せば該当項目だけ差し替える。
 * @param tipCount 先端意匠の総数（呼び出し側 SHIORI_TIPS.size を渡す＝先端を足すだけで選択に反映される）。
 * @param persistedTipIndex 非 null なら tipIndex をこの永続値へ差し替える（null＝title 由来の決定論値へフォールバック）。
 * @param persistedLenFrac 非 null なら lenFrac をこの永続値へ差し替える（null＝同上）。
 *
 * 乱数系列は正本と同一: 色相は `mulberry32(hash(title))` の初回値（shioriHue と同一）、
 * 位置・長さ・先端は `mulberry32(hash(title+"|B"))` から順に x→len→tip の順で引く。
 *
 * なぜ永続値ありでも rng() を必ず同回数・同順で回してから差し替えるか（消費順序の保存）:
 * mulberry32 は逐次消費のストリームで、xFrac→lenFrac→tipIndex の順に3回引く。ここで「差し替える項目の
 * rng() 呼び出しを省略」すると後続の引き位置がずれ、対象外の xFrac や、片方だけ永続化された場合のもう一方の
 * フォールバック値が変わってしまう。抽選位置を1つも動かさず値だけ後段で上書きすることで、hue・xFrac は
 * 1ビットも変わらず（既存 ShioriGeneratorTest の固定値が担保）、tipIndex と lenFrac も互いに独立して
 * 「永続値なら差し替え／null なら従来値」を厳密に満たす。
 */
internal fun shioriParams(
    title: String,
    tipCount: Int,
    persistedTipIndex: Int? = null,
    persistedLenFrac: Float? = null,
): ShioriParams {
    val hue = shioriHue(title)
    val rng = mulberry32(shioriHash(title + "|B"))
    val xFrac = (0.14 + (0.36 - 0.14) * rng()).toFloat()
    // lenFrac・tipIndex の rng() は差し替えの有無に関わらず必ず消費する（上記 why＝消費順序の保存）。
    val drawnLenFrac = (SHIORI_LEN_FRAC_MIN + (SHIORI_LEN_FRAC_MAX - SHIORI_LEN_FRAC_MIN) * rng()).toFloat()
    val drawnTipIndex = floor(rng() * tipCount).toInt()
    return ShioriParams(
        hue = hue,
        xFrac = xFrac,
        lenFrac = persistedLenFrac ?: drawnLenFrac,
        tipIndex = persistedTipIndex ?: drawnTipIndex,
    )
}
