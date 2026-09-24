package com.novelreader.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * 読書クロームの上下バーの地を〈**面＝半透明／システム帯＝不透明**〉の2段で塗る。
 *
 * なぜ `containerColor` に α を掛けないか（この形が要る理由）:
 * `TopAppBar`/`BottomAppBar` の `Surface` は **`windowInsets` ぶんのシステム帯まで同じ色で塗る**。
 * だから `containerColor` へ α を掛けると、ボタン行の下（ナビ inset 帯）や題字の上（ステータス帯）という
 * **指も触れず何も乗っていない無地部分**から本文が透けて見える。2026-07-29 実機で「上下バーが非対称に見える」
 * と報告された真因がこれで、当時は旧 WebView 期 `.nav-footer` から持ち越した `.copy(alpha = 0.95f)` だった。
 *
 * 2026-09-04 裁定（比較モック `docs/design-candidates/candidates/reading-bars-translucency-candidates.html` 案B）は
 * 「面は透かす・帯は透かさない」を**要件として**採っている。よって塗りを2枚に割り、帯側には α を渡さない。
 *
 * @param color バーの地色（スキントークン `navBackground` / `topBarBackground`）。
 * @param faceAlpha 面に載せる α（スキンごとの裁定＝`SkinTokens.readingBarSurfaceAlpha`）。
 * @param systemBandHeightPx システム帯の高さ（px）。実測 inset＝端末とジェスチャー設定で変わる。
 * @param bandAtTop 帯が上端側か（上バー＝ステータス帯 true／下バー＝ナビ帯 false）。
 */
internal fun Modifier.readingChromeBarSurface(
    color: Color,
    faceAlpha: Float,
    systemBandHeightPx: Float,
    bandAtTop: Boolean,
): Modifier = drawBehind {
    val bands = chromeBarBands(size.height, systemBandHeightPx)
    val faceTop = if (bandAtTop) bands.bandHeight else 0f
    val bandTop = if (bandAtTop) 0f else bands.faceHeight
    if (bands.faceHeight > 0f) {
        drawRect(
            color = color,
            alpha = faceAlpha.coerceIn(0f, 1f),
            topLeft = Offset(0f, faceTop),
            size = Size(size.width, bands.faceHeight),
        )
    }
    if (bands.bandHeight > 0f) {
        // 帯は**常に不透明**（α を渡さない）＝ここへ本文を出さないのがこの関数の存在理由。
        drawRect(
            color = color,
            topLeft = Offset(0f, bandTop),
            size = Size(size.width, bands.bandHeight),
        )
    }
}

/** [readingChromeBarSurface] の分割結果（面と帯は重ならず、合計はバー全高に一致する）。 */
internal data class ChromeBarBands(val faceHeight: Float, val bandHeight: Float)

/**
 * バー全高をシステム帯ぶんだけ削って〈面／帯〉へ割る純関数（描画から切り出して単体で検証できるようにした）。
 *
 * 実測 inset は端末・ジェスチャー設定・Robolectric（0 になる）で振れるので、`0 <= 帯 <= 全高` へ丸める。
 * 丸めないと、帯が全高を超えた瞬間に面の高さが負になり、負サイズの矩形は描画されず**帯だけが残る**
 * ＝「透過が黙って消える」形で壊れる（例外は出ないので気づけない）。
 */
internal fun chromeBarBands(totalHeight: Float, systemBandHeight: Float): ChromeBarBands {
    val total = totalHeight.coerceAtLeast(0f)
    val band = systemBandHeight.coerceIn(0f, total)
    return ChromeBarBands(faceHeight = total - band, bandHeight = band)
}
