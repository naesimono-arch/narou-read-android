package com.novelreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.domain.complementNumber
import com.novelreader.domain.displayLabel
import com.novelreader.domain.splitChapterTitle
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.TypesetConstraints
import com.novelreader.typeset.VerticalTypesetter
import com.novelreader.typeset.render.PaintFontMetrics
import com.novelreader.ui.compose.VerticalParagraph
import com.novelreader.ui.skins.m.kanjiNumber
import com.novelreader.ui.theme.GothicFamily
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
// 話数ラベル（モック .chap-h .num: ゴシック 11px）。縦書きでも固定 11sp（横書き D/M/J の .num と同値）。
private val HeaderNumFontSize = 11.sp
// .num letter-spacing:.3em の縦書き等価＝字（マス）と字の間の縦方向ギャップ。
private const val HEADER_NUM_LETTER_SPACING_EM = 0.3f
// ラベル内の空白（「第 百二十七 話」の区切り空白）1つぶんの追加送り。CSS の半角空白の advance（約1/4em）を
// 縦方向へ読み替えた自己判断値（モックに縦書き時の明示規定なし＝報告列挙対象）。
private const val HEADER_NUM_SPACE_GAP_EM = 0.25f

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
    // 目次順の話数（1始まり）。章見出しの話数ラベル用（横書き ChapterContent と同契約＝向きで見出しを
    // 変えないため同便で受ける）。null＝目次未ロードで不明（接頭辞なし章はラベルを出さないだけ）。
    chapterNumber: Int? = null,
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
        // top/bottom＝システムバー実測 inset のみ（IgnoringVisibility で没入出没時のリフロー抑止）。
        // 横書きの ReadingBodyTopExtra/BottomExtra（上下バーのクリアランス 64/80dp）は加えない——
        // LazyColumn では contentPadding の top/bottom がスクロール軸＝章頭/章末に一度だけ効く余白だが、
        // LazyRow では交差軸＝全列の列高から恒久的に差し引かれ、横画面（視野高 360dp）では列高が約 4 割まで
        // 潰れる（2026-07-17 実機。モック .reader は padding:20px＋没入時全面が正＝バー分の恒久確保は誤翻訳）。
        // モックの非没入時 margin-bottom:60px（列下端をバー上端で終える）は、バー可視状態への追従が
        // タップトグルのたびに全列の再組版（リフロー）を起こすため翻訳しない＝没入全面へ倒し、バー表示中は
        // 列端がバー下に重なるのを許容する（横書きが本文行のバー下通過を許容するのと同じ取引。列の呼吸は
        // itemModifier の bodyMarginDp＝ユーザー設定が担う）。
        // start/end＝読み進め方向の余白（モック .reader の横 padding）。左右へバー分を読み替えない。
        contentPadding = PaddingValues(
            top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding(),
            bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding(),
            start = Spacing.S24,
            end = Spacing.S24,
        ),
    ) {
        // [0]＝章見出し（横書き ChapterContent と同じく先頭アイテム。没入時は唯一の章タイトル表示）。
        item {
            VerticalChapterHeader(
                title = content.title,
                chapterNumber = chapterNumber,
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
 * 章見出し（モック .chap-h の縦書き翻訳）。話数ラベル（.num）→題（.t）→藍の短い縦ルールを右→左に積む
 * （縦書きの block 軸＝右→左。モック reading-vertical-scroll-D の margin-block-start 8px/16px と同間隔）。
 *
 * 話数ラベル（2026-08-06 裁定①・②③は推奨適用）: 原文接頭辞があれば分離してラベルに・無い章だけ index
 * から「第 N 話」（漢数字）を補完——横書き ChapterHeader と同じ規則（向きで同じ本の見出しを変えない）。
 * ラベルはゴシック小・アクセント色（モック .num 規定）。縦書きの置き方は「1文字1マスを縦に積む・
 * 連続半角英数は1マス（縦中横の慣行）」＝横書き規定の等価回転（モックの縦書き .num は漢数字例のみ）。
 */
@Composable
private fun VerticalChapterHeader(
    title: String,
    chapterNumber: Int?,
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

    // 分離規則は横書き ChapterHeader と同一（domain の純関数＝向きによる見出し差を構造的に防ぐ）。
    val parts = remember(title, chapterNumber) { splitChapterTitle(title, chapterNumber) }
    val numText = parts.displayLabel()
        ?: parts.complementNumber(chapterNumber)?.let { "第 ${kanjiNumber(it)} 話" }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = bodyMarginDp.dp),
    ) {
        val columnHeightPx = constraints.maxHeight.toFloat()
        // 見出しは字下げしない（indentFirstColumn=false）。題は縦書き明朝で1〜複数列に組む。
        val layout = remember(parts.body, columnHeightPx, headerFontPx, headerAdvancePx) {
            typesetter.typeset(
                listOf(TextSegment.Plain(parts.body)),
                TypesetConstraints(
                    columnHeightPx = columnHeightPx,
                    fontSizePx = headerFontPx,
                    rubyFontSizePx = headerRubyPx,
                    columnAdvancePx = headerAdvancePx,
                    indentFirstColumn = false,
                ),
            )
        }
        // Row（LTR）: 左から〈ルール・題・話数ラベル〉＝読み順（右→左）ではラベル→題→ルール。
        // 見出し全体を1つの heading ノードに束ねる（ラベルの1マス Text 群を文字単位で読ませない・
        // 読み上げ順もラベル→題の読み順に固定する）。
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = Spacing.S16)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = if (numText != null) "$numText　${parts.body}" else parts.body
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 藍の短い縦ルール（モック .chap-h .rule）。装飾のため個別 semantics は持たない。
            // colors.rule を使う（DARK で accent と乖離＝横書き ChapterHeader と同じ理由）。
            Box(
                modifier = Modifier
                    .width(HeaderRuleThickness)
                    .height(HeaderRuleLength)
                    .background(colors.rule.copy(alpha = HEADER_RULE_ALPHA)),
            )
            Spacer(Modifier.width(Spacing.S16)) // .rule margin-block-start:16px（題→ルールの横間隔）
            // 題（縦書き明朝・中央寄せ）。
            VerticalParagraph(
                layout = layout,
                fontSizePx = headerFontPx,
                rubyFontSizePx = headerRubyPx,
                textColor = colors.text,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (numText != null) {
                Spacer(Modifier.width(Spacing.S8)) // .t margin-block-start:8px（ラベル→題の横間隔）
                VerticalHeaderNum(numText = numText, colors = colors)
            }
        }
    }
}

/**
 * 縦書きの話数ラベル（モック .num のゴシック小・アクセント色を縦組みへ等価回転）。
 * 1文字1マス（連続半角英数は1マス＝縦中横の慣行）を縦に積み、マス間に letter-spacing .3em の等価
 * ギャップを置く。本文組版器を使わないのは、組版器が字間（letter-spacing）を持たず .3em の「ゆとり」
 * ＝モック .num の署名を落とすため（短い1列固定のラベルに折り返し・ルビは不要＝Column で足りる）。
 */
@Composable
private fun VerticalHeaderNum(numText: String, colors: ReadingColors) {
    val density = LocalDensity.current
    val letterGap = with(density) { (HeaderNumFontSize * HEADER_NUM_LETTER_SPACING_EM).toDp() }
    val spaceGap = with(density) { (HeaderNumFontSize * HEADER_NUM_SPACE_GAP_EM).toDp() }
    // 行箱を 1em に刈り込む（Trim.Both）＝spacedBy のギャップが正確に .3em になる（既定の行間 leading が
    // 乗ると字間が font 依存で膨らむため）。
    val numStyle = remember(colors.accent) {
        TextStyle(
            color = colors.accent,
            fontSize = HeaderNumFontSize,
            fontFamily = GothicFamily,
            lineHeight = HeaderNumFontSize,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(letterGap),
    ) {
        numLabelUnits(numText).forEach { unit ->
            if (unit.isBlank()) {
                // ラベル内の空白（「第 百二十七 話」の区切り）＝空マスでなく小さめの送り（冒頭定数の why）。
                Spacer(Modifier.height(spaceGap))
            } else {
                Text(
                    text = unit,
                    style = numStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * ラベルを縦書きの1マス単位へ割る。連続する半角英数字は1マスに束ねる（`第127話` の `127` を縦中横として
 * 1マスに横置き＝本文組版と同じ慣行）。空白は空白のまま返す（呼び出し側でギャップへ変換）。
 */
private fun numLabelUnits(label: String): List<String> {
    val units = mutableListOf<String>()
    val run = StringBuilder()
    for (ch in label) {
        if (ch in '0'..'9' || ch in 'A'..'Z' || ch in 'a'..'z') {
            run.append(ch)
        } else {
            if (run.isNotEmpty()) {
                units.add(run.toString())
                run.clear()
            }
            units.add(ch.toString())
        }
    }
    if (run.isNotEmpty()) units.add(run.toString())
    return units
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
