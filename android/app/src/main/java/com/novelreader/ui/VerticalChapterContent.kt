package com.novelreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.TypesetConstraints
import com.novelreader.typeset.VerticalTypesetter
import com.novelreader.typeset.render.PaintFontMetrics
import com.novelreader.ui.compose.VerticalParagraph
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing
import kotlinx.collections.immutable.ImmutableList

// ルビは親文字の 0.5 倍（横書き RubyText.rubyFontSizeRatio=0.5f と同値。1か所で揃える）。
private const val RUBY_FONT_SIZE_RATIO = 0.5f
// 空段落＝幅 1.4em の空き列（モック .blank block-size:1.4em）。縦書きでは block 軸＝横幅なので em×fontSize を幅に置く。
private const val BLANK_COLUMN_EM = 1.4f
// シーン区切り hr: 列中央に立てる縦線の長さ＝列高の 42%（モック hr inline-size:42%）。
private const val HR_LENGTH_FRACTION = 0.42f
// hr の色は colors.rule を 50% で（モック hr{background:var(--rule);opacity:.5}）。横書きは colors.hr だが
// 縦書きは P3 仕様どおり rule 50%（藍の細ルールで静かに区切る D の思想）。
private const val HR_RULE_ALPHA = 0.5f
// 章見出しルールの不透明度（モック .chap-h .rule opacity:.85）。横書き ChapterHeader と同値。
private const val HEADER_RULE_ALPHA = 0.85f
// 章見出しルールの寸法（モック .chap-h .rule inline-size:48px×block-size:2px を縦線へ翻訳）。
private val HeaderRuleLength = 48.dp
private val HeaderRuleThickness = 2.dp

/**
 * 縦書き章本文を LazyRow(reverseLayout=true) でレンダリングする（P3）。
 *
 * 横書き [ChapterContent] の鏡写し: item 構成（[0]=章見出し → 段落 items[同種4分類の contentType] →
 * 継続スロット）と位置保存 (index, offset) を完全一致させ、[com.novelreader.typeset.ReadingPositionMapper]
 * が横書き LazyColumn と縦書き LazyRow の両方へ同じ式で効くようにする（P0-4 実測で reverseLayout でも
 * (index,offset) は「#0 が右端・scrollBy(+) で読み進め」＝横書き同型と確認済み）。
 *
 * 本文だけが縦書きで、topbar/bottombar は横のまま（モック reading-vertical-scroll-D の骨格）。よって:
 * - contentPadding の top/bottom はバー分（横書きと同じく status/navbar インセット＋バー実高）を全列の
 *   上下に確保する（縦書きでは左右へ読み替えない＝バーは物理 top/bottom に在る）。
 * - contentPadding の start/end は読み進め方向（横軸）の余白（モック .reader の横 padding 相当）。
 * - ユーザー設定の本文余白 [bodyMarginDp] は横書きでは左右（行長）だったが、縦書きでは列の上下（列高）へ
 *   翻訳する＝各段落アイテムの vertical padding。列が縮み上下に呼吸が生まれる（横書きの左右余白と同義）。
 *
 * a11y: 段落ごとに clearAndSetSemantics で spoken（当て字は著者読みへ置換）を与える。読み上げ順は
 * LazyRow の item 順＝右→左の読み順なので段落単位で自然に整う（横書き RubyText.kt:132-133,238-248 の移植）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerticalChapterContent(
    content: ChapterContent,
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    lazyListState: LazyListState = rememberLazyListState(),
    // 最終章末尾の継続導線スロット（横書きと同契約＝判断は呼び出し側）。null = 差し込まない。
    continuation: (@Composable () -> Unit)? = null,
) {
    val paragraphs = remember(content) { content.segments.splitIntoParagraphs() }

    val density = LocalDensity.current
    // sp→px（fontScale 込み＝WCAG 1.4.4）。RubyText/VerticalParagraph と同じ実寸源。
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val rubyFontSizePx = fontSizePx * RUBY_FONT_SIZE_RATIO
    // 行間設定 lineHeightEm を「列送り」＝本文列中心の間隔（ルビ帯込み）へ読み替える（TypesetConstraints の契約）。
    val columnAdvancePx = fontSizePx * lineHeightEm

    // 純組版器は入力に依らず不変＝章単位で1つ使い回す（PaintFontMetrics の Paint 生成を抑える）。
    val typesetter: VerticalTypesetter = remember { DefaultVerticalTypesetter(PaintFontMetrics()) }

    // 各段落アイテム共通の modifier: 列高いっぱい＋ユーザー余白を上下（列の呼吸）へ。
    val itemModifier = Modifier
        .fillMaxHeight()
        .padding(vertical = bodyMarginDp.dp)

    LazyRow(
        state = lazyListState,
        reverseLayout = true, // 右→左の連続横スクロール（#0＝右端＝先頭列）。
        modifier = Modifier.fillMaxSize(),
        // top/bottom＝バー分（横書き ChapterContent と同じ算出。IgnoringVisibility で没入出没時のリフロー抑止）。
        // start/end＝読み進め方向の余白（モック .reader の横 padding）。左右へバー分を読み替えない。
        contentPadding = PaddingValues(
            top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() + Insets.ReadingBodyTopExtra,
            bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + Insets.ReadingBodyBottomExtra,
            start = Spacing.S24,
            end = Spacing.S24,
        ),
    ) {
        // [0]＝章見出し（横書き ChapterContent と同じく先頭アイテム。没入時は唯一の章タイトル表示）。
        item {
            VerticalChapterHeader(
                title = content.title,
                colors = colors,
                fontSize = fontSize,
                lineHeightEm = lineHeightEm,
                bodyMarginDp = bodyMarginDp,
                typesetter = typesetter,
            )
        }

        // 段落ごとにレンダリング。contentType は横書きと同種4分類（同種アイテム間のノード再利用）。
        // key は付けない（横書きと同じ理由＝段落は一意な安定 ID を持たず位置 index が唯一のキー）。
        items(
            paragraphs,
            contentType = { paragraph ->
                when {
                    paragraph.isEmpty() -> "empty"
                    paragraph.size == 1 && paragraph[0] is TextSegment.HorizontalRule -> "hr"
                    paragraph.size == 1 && paragraph[0] is TextSegment.StyledBlock -> "block"
                    else -> "text"
                }
            },
        ) { paragraph ->
            VerticalParagraphItem(
                paragraph = paragraph,
                colors = colors,
                fontSizePx = fontSizePx,
                rubyFontSizePx = rubyFontSizePx,
                columnAdvancePx = columnAdvancePx,
                typesetter = typesetter,
                itemModifier = itemModifier,
            )
        }

        // 継続導線（最終章のみ非 null）。横書きと同じく末尾アイテム。
        if (continuation != null) {
            item { continuation() }
        }
    }
}

/**
 * 章見出し（モック .chap-h の縦書き翻訳）。題を縦書き明朝で組版し、その左（＝読み順で題の後）に藍の短い縦ルール。
 *
 * なぜ話数ラベル（モック .num ゴシック小）を出さないか: [ChapterContent.title] は話数と題を分離した
 * データを持たず（横書き [ChapterContent] の ChapterHeader も title 全体を1つに描く）、鏡写しのため
 * 同じく title 全体を1列として組む。ラベル書体も横書き同様に明朝で揃える（GothicFamily トークンは未定義＝
 * ゴシック化は意匠の自己判断になるため避け、横書きと一致させる）。
 */
@Composable
private fun VerticalChapterHeader(
    title: String,
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    typesetter: VerticalTypesetter,
) {
    val density = LocalDensity.current
    // 本文よりわずかに大きい見出しサイズ（横書き ChapterHeader の +2 と同値・fontScale 追従）。
    val headerFontPx = with(density) { (fontSize + 2).sp.toPx() }
    val headerRubyPx = headerFontPx * RUBY_FONT_SIZE_RATIO
    val headerAdvancePx = headerFontPx * lineHeightEm

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = bodyMarginDp.dp),
    ) {
        val columnHeightPx = constraints.maxHeight.toFloat()
        // 見出しは字下げしない（indentFirstColumn=false）。題は縦書き明朝で1〜複数列に組む。
        val layout = remember(title, columnHeightPx, headerFontPx, headerAdvancePx) {
            typesetter.typeset(
                listOf(TextSegment.Plain(title)),
                TypesetConstraints(
                    columnHeightPx = columnHeightPx,
                    fontSizePx = headerFontPx,
                    rubyFontSizePx = headerRubyPx,
                    columnAdvancePx = headerAdvancePx,
                    indentFirstColumn = false,
                ),
            )
        }
        // Row（LTR）: 左＝ルール・右＝題。読み進めは右→左なので右端の題が先頭に来る（モックの right→left 積み）。
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = Spacing.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 藍の短い縦ルール（モック .chap-h .rule）。装飾のため semantics は付けない。
            // colors.rule を使う（DARK で accent と乖離＝横書き ChapterHeader と同じ理由）。
            Box(
                modifier = Modifier
                    .width(HeaderRuleThickness)
                    .height(HeaderRuleLength)
                    .background(colors.rule.copy(alpha = HEADER_RULE_ALPHA)),
            )
            Spacer(Modifier.width(Spacing.S16))
            // 題（縦書き明朝・中央寄せ）。見出しジャンプ対象＝heading()、読み上げは題そのもの。
            VerticalParagraph(
                layout = layout,
                fontSizePx = headerFontPx,
                rubyFontSizePx = headerRubyPx,
                textColor = colors.text,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clearAndSetSemantics {
                        heading()
                        contentDescription = title
                    },
            )
        }
    }
}

/** 1段落分を縦書きで描画する。空段落・hr・前後書きブロック・通常テキストの4種を横書き [ChapterContent] と対応させて分岐。 */
@Composable
private fun VerticalParagraphItem(
    paragraph: ImmutableList<TextSegment>,
    colors: ReadingColors,
    fontSizePx: Float,
    rubyFontSizePx: Float,
    columnAdvancePx: Float,
    typesetter: VerticalTypesetter,
    itemModifier: Modifier,
) {
    val density = LocalDensity.current
    when {
        paragraph.isEmpty() -> {
            // 空段落＝幅 1.4em の空き列（モック .blank）。なろう系のシーン転換演出を保持（横書きと同旨）。
            val blankWidth: Dp = with(density) { (fontSizePx * BLANK_COLUMN_EM).toDp() }
            Spacer(modifier = Modifier.fillMaxHeight().width(blankWidth))
        }

        paragraph.size == 1 && paragraph[0] is TextSegment.HorizontalRule -> {
            // シーン区切り（モック hr）: 1列幅の中央に、列高 42% の短い縦線を立てる。
            val columnWidth: Dp = with(density) { columnAdvancePx.toDp() }
            Box(
                modifier = itemModifier.width(columnWidth),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(HR_LENGTH_FRACTION)
                        .background(colors.rule.copy(alpha = HR_RULE_ALPHA)),
                )
            }
        }

        paragraph.size == 1 && paragraph[0] is TextSegment.StyledBlock -> {
            VerticalStyledBlock(
                block = paragraph[0] as TextSegment.StyledBlock,
                colors = colors,
                fontSizePx = fontSizePx,
                rubyFontSizePx = rubyFontSizePx,
                columnAdvancePx = columnAdvancePx,
                typesetter = typesetter,
                itemModifier = itemModifier,
            )
        }

        else -> {
            // 通常段落。clearAndSetSemantics で当て字を著者読みへ置換した spoken を段落1本の音声にする
            //（横書き RubyText.kt:132-133,238-248 の移植＝縦書き経路でも二重読み・無音落ちを防ぐ）。
            val spoken = remember(paragraph) { spokenTextOf(paragraph) }
            BoxWithConstraints(
                modifier = itemModifier.clearAndSetSemantics { contentDescription = spoken },
            ) {
                val columnHeightPx = constraints.maxHeight.toFloat()
                val layout = remember(paragraph, columnHeightPx, fontSizePx, rubyFontSizePx, columnAdvancePx) {
                    typesetter.typeset(
                        paragraph,
                        TypesetConstraints(
                            columnHeightPx = columnHeightPx,
                            fontSizePx = fontSizePx,
                            rubyFontSizePx = rubyFontSizePx,
                            columnAdvancePx = columnAdvancePx,
                            // なろう原文は地の文の段落頭に全角空白（U+3000）を自前で持つ（会話「…」には無し）
                            // ＝合成インデントを足すと二重字下げになる（2026-07-17 実機フィードバック
                            // 「段落が毎回2空白分空く」の真因。横書き経路も原文の空白に任せて何も足していない）。
                            indentFirstColumn = false,
                        ),
                    )
                }
                VerticalParagraph(
                    layout = layout,
                    fontSizePx = fontSizePx,
                    rubyFontSizePx = rubyFontSizePx,
                    textColor = colors.text,
                )
            }
        }
    }
}

/**
 * 前書き・後書きブロック（モック .block）。枠クロームは Compose（Surface＋枠）、中身は呼び出し側で
 * segments を展開して縦書き組版する（VerticalTypesetter に StyledBlock を渡すと契約違反で例外になるため）。
 *
 * 読み順は右→左（縦書き）: ラベルを右端に、続く本文段落をその左へ積む（LayoutDirection.Rtl の Row で表現）。
 */
@Composable
private fun VerticalStyledBlock(
    block: TextSegment.StyledBlock,
    colors: ReadingColors,
    fontSizePx: Float,
    rubyFontSizePx: Float,
    columnAdvancePx: Float,
    typesetter: VerticalTypesetter,
    itemModifier: Modifier,
) {
    val inner = remember(block) { block.segments.splitIntoParagraphs() }
    // spoken＝ラベル＋中身の読み（当て字は著者読み）。ブロック全体を1音声にする。
    val spoken = remember(block) { block.label + spokenTextOf(block.segments) }

    Box(modifier = itemModifier.clearAndSetSemantics { contentDescription = spoken }) {
        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = colors.blockBackground,
            border = BorderStroke(1.dp, colors.blockBorder),
            shape = RoundedCornerShape(0.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.padding(Spacing.S16)) {
                val columnHeightPx = constraints.maxHeight.toFloat()
                val labelLayout = remember(block.label, columnHeightPx, fontSizePx, columnAdvancePx) {
                    typesetter.typeset(
                        listOf(TextSegment.Plain(block.label)),
                        TypesetConstraints(
                            columnHeightPx = columnHeightPx,
                            fontSizePx = fontSizePx,
                            rubyFontSizePx = rubyFontSizePx,
                            columnAdvancePx = columnAdvancePx,
                            indentFirstColumn = false,
                        ),
                    )
                }
                // Rtl の Row: 先頭子（ラベル）が右端＝読み順の起点。以降の本文段落がその左へ並ぶ。
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        // ラベルはアクセント色（モック .block .lbl＝藍。横書きも accent で小見出し化）。
                        VerticalParagraph(
                            layout = labelLayout,
                            fontSizePx = fontSizePx,
                            rubyFontSizePx = rubyFontSizePx,
                            textColor = colors.accent,
                        )
                        inner.forEach { innerPara ->
                            if (innerPara.isNotEmpty()) {
                                Spacer(Modifier.width(Spacing.S8))
                                val innerLayout = remember(innerPara, columnHeightPx, fontSizePx, rubyFontSizePx, columnAdvancePx) {
                                    typesetter.typeset(
                                        innerPara,
                                        TypesetConstraints(
                                            columnHeightPx = columnHeightPx,
                                            fontSizePx = fontSizePx,
                                            rubyFontSizePx = rubyFontSizePx,
                                            columnAdvancePx = columnAdvancePx,
                                            // 通常段落と同じく合成インデントは足さない（原文の全角空白が正）。
                                            indentFirstColumn = false,
                                        ),
                                    )
                                }
                                VerticalParagraph(
                                    layout = innerLayout,
                                    fontSizePx = fontSizePx,
                                    rubyFontSizePx = rubyFontSizePx,
                                    textColor = colors.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TTS 読み置換の全文を作る（横書き RubyText.buildRubyAnnotatedString の spoken 構築＝RubyText.kt:238-248 の移植）。
 * Plain はそのまま・Ruby は reading（著者指定の読み）を積む＝当て字を親漢字の既定読みでなく著者読みで
 * 読み上げさせる（charter F 急所②）。StyledBlock は中身を再帰。LineBreak は改行・HorizontalRule は無音。
 */
private fun spokenTextOf(segments: List<TextSegment>): String {
    val sb = StringBuilder()
    fun append(segment: TextSegment) {
        when (segment) {
            is TextSegment.Plain -> sb.append(segment.text)
            is TextSegment.Ruby -> sb.append(segment.reading)
            is TextSegment.StyledBlock -> segment.segments.forEach { append(it) }
            TextSegment.LineBreak -> sb.append("\n")
            TextSegment.HorizontalRule -> Unit
        }
    }
    segments.forEach { append(it) }
    return sb.toString()
}
