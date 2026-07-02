package com.novelreader.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.model.TextSegment
import com.novelreader.ui.theme.MinchoFamily
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp

/** AnnotatedString 上のルビアノテーション tag */
private const val RUBY_TAG = "ruby"

/**
 * ルビ付きテキストを描画する Composable。
 *
 * 描画方式: AnnotatedString + drawWithContent オーバーレイ。
 * 折り返し・禁則処理は Compose のテキストレイアウトエンジンに任せ、
 * ルビだけ Canvas で親文字の上に重ね描きする。
 *
 * なぜ FlowRow + カスタム Composable でなくこの方式か:
 * フロー要素方式ではブロック単位のレイアウトになるため禁則処理が効かず、
 * 読書アプリとして致命的な体験劣化を招くため。
 *
 * @param segments 1段落分のセグメントリスト（LineBreak を含まない想定）
 * @param style 基本テキストスタイル
 * @param rubyFontSizeRatio 親文字に対するルビのフォントサイズ比率
 * @param rubyColor ルビの文字色
 */
@Composable
fun RubyText(
    segments: List<TextSegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(
        fontSize = 18.sp,
        // ルビ分の余白を lineHeight で確保する
        // なぜ 2.5em か: 親文字(1em) + ルビ(~0.5em) + 上下余白で約 2.5em が最適
        lineHeight = 2.5.em,
        fontFamily = MinchoFamily,
        letterSpacing = 0.sp,
    ),
    rubyFontSizeRatio: Float = 0.5f,
    rubyColor: Color = Color(0xFF777777),
) {
    val (annotated, rubyRanges) = remember(segments) {
        buildRubyAnnotatedString(segments)
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        style = style,
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                // 先にテキスト本体を描画し、その上にルビを重ねる
                drawContent()
                val layout = textLayoutResult ?: return@drawWithContent

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = rubyColor.toArgb()
                        textSize = style.fontSize.value * rubyFontSizeRatio * density
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.SERIF
                    }
                    // 親文字の字面上端をベースラインから導出するためのメトリクス。
                    // なぜ別 Paint を作るか: TextLayoutResult は行ボックス座標しか持たず、
                    // lineHeight の余剰を含む行上端からは字面位置が分からないため（バグ#1 の根本原因）。
                    // MinchoFamily = FontFamily.Serif なので Typeface.SERIF でメトリクスが一致する。
                    val baseAscent = android.graphics.Paint().apply {
                        textSize = style.fontSize.value * density
                        typeface = android.graphics.Typeface.SERIF
                    }.ascent()

                    for ((start, end, reading) in rubyRanges) {
                        val positions = RubyLayoutHelper.calculateRubyPositions(
                            layout, start, end, reading,
                        )
                        for (info in positions) {
                            // baselineY(親文字ベースライン) + ascent(負値) = 親文字の字面上端。
                            // そこからルビの descent 分を引き、ルビの下端が字面上端に接するよう配置する
                            // （ascent はインク上端よりわずかに上を指すため、自然な隙間が残る）。
                            val baseGlyphTop = info.baselineY + baseAscent
                            val y = baseGlyphTop - paint.descent()
                            canvas.nativeCanvas.drawText(
                                info.rubyText,
                                info.centerX,
                                y,
                                paint,
                            )
                        }
                    }
                }
            },
        onTextLayout = { textLayoutResult = it },
        overflow = TextOverflow.Visible,
    )
}

/**
 * TextSegment リストから AnnotatedString とルビ範囲リストを構築する。
 * @return Pair(annotated, rubyRanges) — rubyRanges は Triple(start, end, reading)
 */
private fun buildRubyAnnotatedString(
    segments: List<TextSegment>,
): Pair<AnnotatedString, List<Triple<Int, Int, String>>> {
    val rubyRanges = mutableListOf<Triple<Int, Int, String>>()

    val annotated = buildAnnotatedString {
        for (segment in segments) {
            appendSegment(segment, rubyRanges)
        }
    }
    return annotated to rubyRanges
}

private fun AnnotatedString.Builder.appendSegment(
    segment: TextSegment,
    rubyRanges: MutableList<Triple<Int, Int, String>>,
) {
    when (segment) {
        is TextSegment.Plain -> append(segment.text)

        is TextSegment.Ruby -> {
            val start = length
            append(segment.base)
            val end = length
            rubyRanges.add(Triple(start, end, segment.reading))
            // アノテーションも記録（将来の用途向け）
            addStringAnnotation(RUBY_TAG, segment.reading, start, end)
        }

        is TextSegment.LineBreak -> {
            // 呼び出し元で段落分割済みのため、ここには来ない想定。
            // 万一来た場合は改行として扱う（空のアノテーションを追加しない）
            append("\n")
        }

        is TextSegment.HorizontalRule -> {
            // HorizontalRule は段落レベルで処理するため RubyText には来ない想定
        }

        is TextSegment.StyledBlock -> {
            // StyledBlock も段落レベルで処理するため RubyText には来ない想定
            for (child in segment.segments) {
                appendSegment(child, rubyRanges)
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFCFAF2)
@Composable
private fun RubyTextPreview_Normal() {
    RubyText(
        segments = listOf(
            TextSegment.Plain("この"),
            TextSegment.Ruby("物語", "ものがたり"),
            TextSegment.Plain("は始まる。"),
        ),
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFCFAF2)
@Composable
private fun RubyTextPreview_Bold() {
    RubyText(
        segments = listOf(
            TextSegment.Plain("通常テキスト、"),
            TextSegment.Ruby("漢字", "かんじ"),
            TextSegment.Plain("が続く。"),
        ),
        modifier = Modifier.padding(16.dp),
        style = TextStyle(
            fontSize = 18.sp,
            lineHeight = 2.5.em,
            fontFamily = MinchoFamily,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFCFAF2, widthDp = 200)
@Composable
private fun RubyTextPreview_LineWrap() {
    // 幅を狭くして行またぎルビを強制発生させる
    RubyText(
        segments = listOf(
            TextSegment.Plain("長い文章で"),
            TextSegment.Ruby("物語", "ものがたり"),
            TextSegment.Plain("が行をまたいで折り返す場合のプレビュー。"),
        ),
        modifier = Modifier.padding(8.dp),
    )
}
