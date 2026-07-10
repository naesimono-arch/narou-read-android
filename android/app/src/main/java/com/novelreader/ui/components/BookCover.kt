package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import kotlin.math.abs

// ============================================================
// BookCover — タイトルハッシュから書影を動的生成するコンポーザブル
//
// 画像データなしで書影を表現する。book.id のハッシュから色相を決定するため、
// 同じ書籍は常に同じ色になる（ランダムではなく決定的）。
//
// UI-n: 視覚言語 D「和モダン・余白」へ作り替え（モック ui-n-phase0/bookshelf-D.html）。
// なぜ低彩度・暗色スラブにするか: D は寒色×藍ヘアラインの静謐な構図で、
// 旧 HSL の高彩度カラフル書影は世界観に合わないため。色相は残しつつ彩度・明度を抑え、
// 左に藍の縦ルールを引いて「実画像を捏造しない静かな書影」という D の署名を与える。
// ============================================================

/** HSL → RGB 変換（Jetpack Compose は HSL を直接持たないため手動実装） */
private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = hue / 360f
    val s = saturation
    val l = lightness

    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q

    fun hue2rgb(t: Float): Float {
        var t2 = t
        if (t2 < 0f) t2 += 1f
        if (t2 > 1f) t2 -= 1f
        return when {
            t2 < 1f / 6f -> p + (q - p) * 6f * t2
            t2 < 1f / 2f -> q
            t2 < 2f / 3f -> p + (q - p) * (2f / 3f - t2) * 6f
            else          -> p
        }
    }

    return Color(
        red   = hue2rgb(h + 1f / 3f),
        green = hue2rgb(h),
        blue  = hue2rgb(h - 1f / 3f),
    )
}

/**
 * @param bookId   書籍ID（ハッシュのシードに使用）
 * @param title    書影下部に明朝で焼き込むタイトル（showTitle=true のとき）
 * @param modifier Modifier（縦横比はこのModifierで制御する）
 * @param showTitle グリッド=true（書影下部に明朝タイトル）／リスト=false（文字なしスラブ）。
 *   モック bookshelf-D.html ではグリッド書影に明朝タイトルを焼き込み、リスト書影は色面のみ。
 */
@Composable
fun BookCover(
    bookId: String,
    title: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    ruleColor: Color? = null,
) {
    // ハッシュから色相を決定（同じIDなら毎回同じ色になる）
    val colors = remember(bookId) {
        val hash = bookId.hashCode()
        val hue  = abs(hash) % 360f
        // D 様式: 色相だけ振り、彩度・明度は低く抑えた暗色スラブにする。
        // 彩度 12〜21%（旧 38〜49%）／明度 上 26〜34%・下はさらに 8% 暗く（旧 46〜61%）。
        // これで全書影が「静かな寒色寄りの暗い面」に揃い、藍の縦ルールと文字が映える。
        val saturation = 0.12f + (abs(hash / 360) % 10) / 100f    // 12〜21%
        val lightnessTop = 0.26f + (abs(hash / 8640) % 9) / 100f  // 26〜34%
        val lightnessBot = lightnessTop - 0.08f

        val topColor    = hslToColor(hue, saturation, lightnessTop)
        val bottomColor = hslToColor(hue, saturation, lightnessBot)
        Pair(topColor, bottomColor)
    }

    val (topColor, bottomColor) = colors

    Box(
        modifier = modifier
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor),
                ),
            ),
    ) {
        // ────── 左の藍の縦ルール（D の署名要素）──────
        // なぜ start を固定 dp にするか: グリッド(幅大)・リスト(幅46dp)の双方で
        // おおむね左端 7〜16% に収まり、サイズ非依存で破綻しないため。
        // 暗色スラブ上で沈まないよう、トークン藍より明るめの藍を使う。
        // なぜ ruleColor を可能にするか: (b) Web由来・未取込カードはモック正本で縦ルールが青磁＝『未取込』の視覚署名。書影生成ロジックは共通のためルール色のみ注入可能にする。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .width(2.dp)
                .fillMaxHeight(0.82f)
                .background(ruleColor ?: Color(0xFF6E96B8)),
        )
        // ────── 書影下部の明朝タイトル（グリッドのみ）──────
        // モック .cv .ttl-in: 下寄せ・左 padding は藍ルール(左10dp)を避けて 22dp、明朝 14sp・3行省略。
        // 暗色スラブ上の白文字を確実に読ませるため text-shadow（モック相当）を載せる。
        if (showTitle) {
            Text(
                text = title.ifEmpty { "（無題）" },
                fontFamily = MinchoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, end = 14.dp, bottom = 16.dp, top = 14.dp),
            )
        }
    }
}

// ============================================================
// coverBarColor — 文字目録（骨格3・表紙レス）の左端色帯に使う、作品ごとの識別色。
//
// なぜ書影（暗色スラブ）と別関数か: 書影は彩度・明度を大きく落とした「静かな暗い面」だが、
// 幅 4dp の細帯では暗色だと地に沈んで作品を見分けられない。色相は書影と同じハッシュ由来
// （＝同一作品は同一系統色で書影/目録が視覚的に呼応）に保ったまま、彩度・明度だけ中程度へ上げて、
// 細帯でも識別できる中彩度の和色にする。決定的（同じ seed なら常に同じ色＝ランダムではない）。
// ============================================================
fun coverBarColor(seed: String): Color {
    val hash = seed.hashCode()
    val hue = abs(hash) % 360f
    // 書影と同じ hue。彩度・明度は細帯で沈まない中彩度へ（わずかにハッシュで振り単調さを避ける）。
    val saturation = 0.30f + (abs(hash / 360) % 8) / 100f    // 30〜37%
    val lightness = 0.40f + (abs(hash / 8640) % 7) / 100f    // 40〜46%
    return hslToColor(hue, saturation, lightness)
}
