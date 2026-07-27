package com.novelreader.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.novelreader.typeset.CharClassifier
import com.novelreader.typeset.render.VertGlyphRenderer
import com.novelreader.ui.theme.LocalShioriColors
import kotlin.math.floor
import kotlin.math.roundToInt

// ============================================================
// ShioriCover — 「栞」書影の Compose 描画（意匠正本 bookshelf-shiori-final-D.html の翻訳）。
//
// 紙地に「天から引いた色の細棒＋その先端のワンポイント意匠（174種から title 種で1つ）」を置く。
// 決定論パラメータ（色相・x・長さ・先端）は ShioriGenerator（純ロジック・テスト済み）が算出。
// 本ファイルはその描画のみ＝棒=drawLine／先端=drawPath/drawArc/drawCircle／題字=nativeCanvas。
// 先端意匠データ本体（SHIORI_TIPS 174種＋描画語彙）は ShioriTips.kt へ純移動済み（2026-07-27 分割）。
//
// 先端は「配列に1つ足すだけ」で拡張できる（正本の TIPS 設計を踏襲）＝オーナー要望「都度増やす」を
// 構造で担保。先端を足すと選択分布が変わるが「同じ本＝同じ絵」は保たれる（tipCount 依存の決定論）。
// ============================================================

/**
 * 栞アクセント色の共有ヘルパー（純関数）。書架の栞の棒／先端色と、目録リストの左端色帯を
 * この1関数へ集約し「同じ本＝同じ色相・同じ明度」を書架⇔目録で保証する（整合の要）。
 *
 * 明度はスキンが変種ごとに供給する（正本 consistency-D の THEMES.accL）: D はライト L=0.52／
 * セピア L=0.48／ダーク L=0.62（[com.novelreader.ui.theme.skins.SkinD.shiori] の accentLightness）。彩度 S=0.48 固定。
 *
 * なぜ Skin から明示供給（accentLightness を受け取る）か: 旧実装は呼び出し側が渡す surface の
 * luminance()／`surface == BackgroundSepia` 一致で明度を推定していたが、これは D の surface 値前提の
 * 暗黙結合で、スキン導入（surface 値がスキンごとに変わる）で必ず壊れる。明度は現在スキン×変種の
 * ShioriColors.accentLightness を呼び出し側が渡す（推定を根絶・プラン 2026-07-17 裁定）。
 */
internal fun shioriAccentFor(hue: Int, accentLightness: Float): Color =
    hslToColor(hue.toFloat(), 0.48f, accentLightness)

// 栞表紙の内枠（正本の 5% 罫）の不透明度。紙のふちを表紙の墨/白で微かに締めるだけの飾り罫。
// 旧値 0x0D（=13/255≈0.051）を明示定数化し、色は生 ARGB でなく表紙の墨 ink から生成する。
private const val ShioriInnerBorderAlpha = 0.05f

/**
 * 栞書影。title から決定論生成した「棒＋先端＋縦組み明朝題字」を紙地に描く。
 *
 * @param accentOverride 識別色を差し替える（Web由来・未取込カードの青磁署名など・将来用）。null=title 生成色。
 * @param persistedTipIndex 取込時に抽選し BookEntity に永続化した先端種。null=未抽選（旧蔵書）＝title 由来の決定論値へフォールバック。
 * @param persistedLenFrac 取込時に抽選し永続化した棒長。null=同上。両者とも「非 null なら固定・null なら従来の見た目」。
 */
@Composable
internal fun ShioriCover(
    title: String,
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
    persistedTipIndex: Int? = null,
    persistedLenFrac: Float? = null,
) {
    // 紙／墨／識別色明度は現在スキン×変種から明示供給（旧 luminance/surface 推定を根絶）。
    // D では ライト・セピアは surface/onSurface と、ダークは cover 専用トークンと同値（SkinD.shiori）。
    val shiori = LocalShioriColors.current
    val paper = shiori.paper
    val ink = shiori.ink
    // 永続値も remember キーに含める（null→非 null の差し替え時に確実に再計算させる）。
    val params = remember(title, persistedTipIndex, persistedLenFrac) {
        shioriParams(title, SHIORI_TIPS.size, persistedTipIndex, persistedLenFrac)
    }
    // 棒・先端の識別色＝生成色。共有ヘルパー shioriAccentFor に集約し、目録リストの色帯と同一色にする
    // （S=0.48・L は現在スキン×変種の accentLightness＝D はライト0.52/セピア0.48/ダーク0.62）。
    val computedAccent = remember(params.hue, shiori.accentLightness) {
        shioriAccentFor(params.hue, shiori.accentLightness)
    }
    val accent = accentOverride ?: computedAccent
    // 内枠（正本の 5% 罫）＝紙のふちを微かに締める。
    // なぜ生 ARGB でなく ink 由来か: 旧 Color(0x0DFFFFFF)/Color(0x0D1C1F26) はテーマ改訂でこの罫だけ
    // 取り残される（ADR 0014 charter(a) theme/外の生 Color 禁止）。表紙の墨 ink（スキン×変種が供給する
    // ShioriColors.ink＝D はライト/セピア=onSurface・ダーク=ShioriCoverInkDark）を名前付き alpha で薄める。
    val borderColor = ink.copy(alpha = ShioriInnerBorderAlpha)

    // 題字は Canvas 描画で text ノードを持たないため、表紙自体に contentDescription=題名 を与える。
    // なぜ必須か: これが無いとスクリーンリーダーがグリッドの作品名を読めない（a11y 退行）。
    Canvas(modifier.semantics { contentDescription = title }) {
        val w = size.width
        val h = size.height
        drawRect(paper) // 紙地
        drawRect(borderColor, topLeft = Offset(0.5f, 0.5f), size = Size(w - 1f, h - 1f), style = Stroke(1f))
        // 固定px意匠のスケール補正。なぜ必要か: 正本 grid-D の canvas は「幅150pxのカバー」を基準に
        // した固定px座標で棒・先端を描く（Node検証時 clientWidth=0→フォールバック150 で描画＝これが
        // 設計基準）。実機は density 倍の device px（例 592px＝density4）で同じ数値をそのまま描くと、
        // w比例の題字は正しくても、固定pxの棒の太さ・先端が 1/density に潰れ、先端31種の意匠がほぼ
        // 消える（実測: density4 で先端が針の点大に縮小）。基準150に対する実幅比 s を固定px意匠に掛け、
        // 相対サイズをモックと一致させる（棒位置 barX・長さ barLen は w/h 比例なので s を掛けない）。
        val s = w / 150f
        // 棒＝天から色の細線。cap=Butt（正本 lineCap:'butt'＝天端を角で切る）。太さは固定px→s倍。
        val barX = (w * params.xFrac).roundToInt() + 0.5f
        val barLen = (h * params.lenFrac).roundToInt().toFloat()
        drawLine(accent, Offset(barX, 0f), Offset(barX, barLen), 2.5f * s, StrokeCap.Butt)
        // 先端＝種で1つ。棒先端(barX,barLen)を pivot に s 倍拡大し、モックの相対サイズを再現する
        // （先端は固定px座標で描かれるため scale 変換で一括拡大＝各座標を書き換えず正本ロジックを保つ）。
        // coerceIn は防御（tipCount とインデックスの不整合が起きても落とさない）。
        scale(s, s, pivot = Offset(barX, barLen)) {
            SHIORI_TIPS[params.tipIndex.coerceIn(0, SHIORI_TIPS.size - 1)](this, barX, barLen, accent, paper)
        }
        drawShioriTitle(title, w, h, ink)
    }
}

/**
 * 縦組み題字の 1 字描画に使う共有レンダラ。読書画面の縦書き（P1〜P3）と同一の分類→描画経路に通し、
 * 「（）」「～」「ー」等が正立のまま死ぬ既存バグ（全字 drawText 直描画）を解消する。
 * なぜ関数外の private val か: drawShioriTitle は DrawScope 拡張で remember が使えず、毎回 new すると
 * 描画のたびにインスタンスを捨てる。状態を持たない純描画実装なので単一インスタンスを使い回す。
 */
private val shioriTitleGlyphRenderer = VertGlyphRenderer()

/**
 * 表紙内の縦組み明朝題字（右列から左へ最大3列・溢れは末尾を ⋮）。
 * なぜ nativeCanvas + Serif Typeface で px 直描画か: 正本 canvas の `ctx.fillText`（px 指定・
 * textBaseline='middle'）を最も忠実に翻訳でき、かつフォントスケールに依存しない固定構図の
 * 表紙グラフィックにするため（sp 換算だと端末の文字拡大で題字だけ崩れる）。Serif は当端末で
 * Noto Serif CJK（＝明朝）へ解決＝Typography の MinchoFamily(FontFamily.Serif) と同系。
 *
 * 1 字は CharClassifier.classify → VertGlyphRenderer.drawGlyph 経由で置く（読書画面と同一部品）。
 * なぜ連続リーダー結合（LeaderJoin）を使わないか: 題字は固定グリッド（1字=1セル）で ⋮省略と列割りが
 * セル数を前提にするため、複数字を1ユニットへ束ねる結合は列の勘定を壊す。1字=1セルのまま分類だけ通す。
 */
private fun DrawScope.drawShioriTitle(text: String, w: Float, h: Float, ink: Color) {
    val fs = (w * 0.088f).roundToInt().toFloat()
    if (fs < 1f || text.isEmpty()) return
    val lineGap = fs * 1.16f
    val colGap = (w * 0.108f).roundToInt().toFloat()
    val yTop = fs + 5f
    val yBottom = h - 11f
    val x0 = w - fs - 3f
    val maxCols = 3
    val perCol = floor((yBottom - yTop) / lineGap).toInt()
    if (perCol < 1) return
    // 正本の [...text] 相当＝コードポイント単位（サロゲート対応）。
    val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            color = ink.toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) // 明朝・太字（正本 font-weight 600 相当）
            textSize = fs
            textAlign = Paint.Align.CENTER
        }
        for (col in 0 until maxCols) {
            val start = col * perCol
            if (start >= chars.size) break
            val cxp = x0 - col * colGap
            for (i in 0 until perCol) {
                val idx = start + i
                if (idx >= chars.size) break
                // 3列目末尾でまだ続くなら ⋮ で省略（正本と同じ）。⋮ も classify を通す（UPRIGHT で正しい）。
                val ch = if (col == maxCols - 1 && i == perCol - 1 && idx < chars.size - 1) "⋮" else chars[idx]
                // セルの天 y = 従来のセル中心(cy) − lineGap/2。renderer が中心合わせ（旧 baseAdj 手計算）を担う。
                val cellTop = yTop + i * lineGap
                val cls = CharClassifier.classify(ch)
                shioriTitleGlyphRenderer.drawGlyph(canvas.nativeCanvas, ch, cls, cxp, cellTop, lineGap, paint)
            }
        }
    }
}
