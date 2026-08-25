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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.novelreader.domain.ChapterTitleParts
import com.novelreader.domain.complementNumber
import com.novelreader.domain.displayLabel
import com.novelreader.domain.splitChapterTitle
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.typeset.ChapterTypesetStore
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.TypesetConstraints
import com.novelreader.typeset.TypesetRequest
import com.novelreader.typeset.TypesetSlotId
import com.novelreader.typeset.render.PaintFontMetrics
import com.novelreader.ui.compose.VerticalParagraph
import com.novelreader.ui.skins.m.kanjiNumber
import com.novelreader.ui.theme.GothicFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

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

    // 章見出しの分離規則は横書き ChapterHeader と同一（domain の純関数＝向きによる見出し差を構造的に防ぐ）。
    // 見出しを親で解くのは、題も段落と同じ「組版スロット」として先行組版の対象に載せるため。
    val titleParts = remember(content.title, chapterNumber) { splitChapterTitle(content.title, chapterNumber) }
    // 話数ラベル（2026-08-06 裁定①）: 原文接頭辞があれば分離してラベルに・無い章だけ index から補完。
    val headerNumText = titleParts.displayLabel()
        ?: titleParts.complementNumber(chapterNumber)?.let { "第 ${kanjiNumber(it)} 話" }

    val density = LocalDensity.current
    // sp→px（fontScale 込み＝WCAG 1.4.4）。RubyText/VerticalParagraph と同じ実寸源。
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val rubyFontSizePx = fontSizePx * RUBY_FONT_SIZE_RATIO
    // 行間設定 lineHeightEm を「列送り」＝本文列中心の間隔（ルビ帯込み）へ読み替える（TypesetConstraints の契約）。
    val columnAdvancePx = fontSizePx * lineHeightEm
    // 見出しは本文よりわずかに大きい（横書き ChapterHeader の +2 と同値・fontScale 追従）。
    val headerFontPx = with(density) { (fontSize + 2).sp.toPx() }
    val headerRubyPx = headerFontPx * RUBY_FONT_SIZE_RATIO
    val headerAdvancePx = headerFontPx * lineHeightEm

    // 組版の貯蔵庫と「組版すべきもの」の一覧。どちらも**章スコープ**＝内容だけに依存し、フォント/行間を
    // 変えても作り直さない（寸法は TypesetConstraints 側だけが持つ）。版面キャッシュの寿命を LazyRow の
    // item 寿命からここへ移したのが改善 A の本体＝根拠は ChapterTypesetStore の KDoc。
    // 純組版器は入力に依らず不変＝章単位で1つ使い回す（PaintFontMetrics の Paint 生成を抑える）。
    val store = remember(content) { ChapterTypesetStore(DefaultVerticalTypesetter(PaintFontMetrics())) }
    val plan = remember(content, titleParts) { buildTypesetPlan(titleParts.body, paragraphs) }

    // 各段落アイテム共通の modifier: 列高いっぱい＋ユーザー余白を上下（列の呼吸）へ。
    val itemModifier = Modifier
        .fillMaxHeight()
        .padding(vertical = bodyMarginDp.dp)

    val contentPaddingTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val contentPaddingBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // ────── C: 列高を「親で一度だけ」確定する ──────
        // 旧構造は段落 item ごとに BoxWithConstraints を置き、その constraints.maxHeight を列高にしていた。
        // だが列高は全段落で同一（下の LazyRow と itemModifier が全 item に同じ modifier 連鎖を掛ける）＝
        // item ごとの subcomposition は**同じ数字を段落数ぶん測り直していただけ**。ここで同じ連鎖を辿る:
        //   item のスロット高 ＝ 視野高 − contentPadding.top − contentPadding.bottom
        //     （LazyRow は交差軸の contentPadding を item の制約から差し引く。この向きは下の contentPadding
        //       コメントにある 2026-07-17 実機＝「横画面で列高が約4割に潰れる」で実測済み）
        //   通常段落／章見出しの列高 ＝ スロット高 − 2×bodyMarginDp（itemModifier の padding(vertical)）
        //   前後書きブロック内の列高 ＝ 上 − 2×Spacing.S16（ブロック内 Box の padding。Surface の border は
        //     寸法を食わないので差し引かない）
        // ⚠️ 丸めは Compose と同じ Dp→px の roundToPx で行う（丸め方が違うと版面が 1px ずれて golden が割れる）。
        val slotHeightPx = with(density) {
            (constraints.maxHeight - contentPaddingTop.roundToPx() - contentPaddingBottom.roundToPx())
                .coerceAtLeast(0)
        }
        val columnHeightPx = with(density) {
            (slotHeightPx - 2 * bodyMarginDp.dp.roundToPx()).coerceAtLeast(0)
        }
        val blockColumnHeightPx = with(density) {
            (columnHeightPx - 2 * Spacing.S16.roundToPx()).coerceAtLeast(0)
        }

        // 段落頭に合成インデントを足さないのは全経路共通（なろう原文が地の文の頭に全角空白を持つため。
        // 足すと二重字下げになる＝2026-07-17 実機フィードバック「段落が毎回2空白分空く」の真因。
        // 横書き経路も原文の空白に任せて何も足していない）。
        val bodyConstraints = remember(columnHeightPx, fontSizePx, rubyFontSizePx, columnAdvancePx) {
            TypesetConstraints(
                columnHeightPx = columnHeightPx.toFloat(),
                fontSizePx = fontSizePx,
                rubyFontSizePx = rubyFontSizePx,
                columnAdvancePx = columnAdvancePx,
                indentFirstColumn = false,
            )
        }
        val blockConstraints = remember(blockColumnHeightPx, fontSizePx, rubyFontSizePx, columnAdvancePx) {
            TypesetConstraints(
                columnHeightPx = blockColumnHeightPx.toFloat(),
                fontSizePx = fontSizePx,
                rubyFontSizePx = rubyFontSizePx,
                columnAdvancePx = columnAdvancePx,
                indentFirstColumn = false,
            )
        }
        val headerConstraints = remember(columnHeightPx, headerFontPx, headerRubyPx, headerAdvancePx) {
            TypesetConstraints(
                columnHeightPx = columnHeightPx.toFloat(),
                fontSizePx = headerFontPx,
                rubyFontSizePx = headerRubyPx,
                columnAdvancePx = headerAdvancePx,
                indentFirstColumn = false,
            )
        }

        // ────── A: 再組版を composition の外へ出す ──────
        // 可視 item を中心に前後を Dispatchers.Default で先行して組む。設定（＝constraints）が変わると
        // この効果ごと再起動し、collectLatest が走行中の組版を取り消す＝スライダーのドラッグ中に途中値の
        // 版面を作り切らない（改善案 B「ドラッグ中の再組版抑制」を別仕掛けにせずここで閉じる）。
        //
        // ⚠️ 窓の源に **layoutInfo.visibleItemsInfo** を使うのが要点（firstVisibleItemIndex ではなく）。
        // 理由は2つあり、どちらも「先行組版が初回表示と競争しない」ための構造:
        // 1. visibleItemsInfo は**レイアウトが済むまで空**＝空の間は1件も依頼しない。よって初回表示の
        //    可視段落は必ず composition 側の同期組版が先に確保し、背景が同じ版面を二重に作って
        //    state を書き換える（＝見た目が変わらないのに再コンポーズが増え、確定タイミングがぶれる）
        //    ことが起きない。firstVisibleItemIndex は未レイアウトでも 0 を返すので、この保証が無い。
        // 2. 可視範囲の**両端が分かる**＝窓が可視 item を必ず内包することが式の上で保証される
        //    （見込み値で広めに取る、という当て推量が要らない）。
        // なお layoutInfo はスクロール中に毎フレーム変わるが、snapshotFlow は**値が変わったときだけ**
        // 流すので、依頼が飛ぶのは窓（可視 index の範囲）が動いたときに限られる。
        LaunchedEffect(store, plan, bodyConstraints, blockConstraints, headerConstraints) {
            snapshotFlow {
                val visible = lazyListState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) IntRange.EMPTY else visible.first().index..visible.last().index
            }.collectLatest { visible ->
                if (visible.isEmpty()) return@collectLatest
                withContext(Dispatchers.Default) {
                    store.typeset(
                        plan.requestsAround(visible, bodyConstraints, blockConstraints, headerConstraints),
                    )
                }
            }
        }

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
                    parts = titleParts,
                    numText = headerNumText,
                    colors = colors,
                    bodyMarginDp = bodyMarginDp,
                    store = store,
                    constraints = headerConstraints,
                )
            }

            // 段落ごとにレンダリング。contentType は横書きと同種4分類（同種アイテム間のノード再利用）。
            // key は付けない（横書きと同じ理由＝段落は一意な安定 ID を持たず位置 index が唯一のキー）。
            // index を取るのは組版スロットの同定に要るため＝item index が [TypesetSlotId] の主キーで、
            // 先行組版の窓（可視 index の前後）をそのまま LazyRow の座標系で表せる。
            itemsIndexed(
                paragraphs,
                contentType = { _, paragraph ->
                    when {
                        paragraph.isEmpty() -> "empty"
                        paragraph.size == 1 && paragraph[0] is TextSegment.HorizontalRule -> "hr"
                        paragraph.size == 1 && paragraph[0] is TextSegment.StyledBlock -> "block"
                        else -> "text"
                    }
                },
            ) { index, paragraph ->
                VerticalParagraphItem(
                    paragraph = paragraph,
                    itemIndex = index + FIRST_PARAGRAPH_ITEM_INDEX,
                    colors = colors,
                    fontSizePx = fontSizePx,
                    columnAdvancePx = columnAdvancePx,
                    store = store,
                    bodyConstraints = bodyConstraints,
                    blockConstraints = blockConstraints,
                    itemModifier = itemModifier,
                )
            }

            // 継続導線（最終章のみ非 null）。横書きと同じく末尾アイテム。
            if (continuation != null) {
                item { continuation() }
            }
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
    parts: ChapterTitleParts,
    numText: String?,
    colors: ReadingColors,
    bodyMarginDp: Int,
    store: ChapterTypesetStore,
    constraints: TypesetConstraints,
) {
    // 見出しは字下げしない（indentFirstColumn=false は constraints 側で立てる）。題は縦書き明朝で1〜複数列に組む。
    // C: 列高は親が一度だけ確定して constraints に載せてある＝ここに BoxWithConstraints は要らない。
    val slot = remember(store) { store.slot(TypesetSlotId(HEADER_ITEM_INDEX)) }
    val segments = remember(parts.body) { listOf(TextSegment.Plain(parts.body)) }
    val result = slot.resolve(segments, constraints)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = bodyMarginDp.dp),
    ) {
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
            // 題（縦書き明朝・中央寄せ）。寸法は「今の設定」でなく**この版面を組んだときの寸法**を渡す
            //（設定変更直後の数フレームは意図的に旧版面を描くため。TypesetResult の KDoc が根拠）。
            VerticalParagraph(
                layout = result.layout,
                fontSizePx = result.constraints.fontSizePx,
                rubyFontSizePx = result.constraints.rubyFontSizePx,
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
    itemIndex: Int,
    colors: ReadingColors,
    fontSizePx: Float,
    columnAdvancePx: Float,
    store: ChapterTypesetStore,
    bodyConstraints: TypesetConstraints,
    blockConstraints: TypesetConstraints,
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
                itemIndex = itemIndex,
                colors = colors,
                store = store,
                constraints = blockConstraints,
                itemModifier = itemModifier,
            )
        }

        else -> {
            // 通常段落。clearAndSetSemantics で当て字を著者読みへ置換した spoken を段落1本の音声にする
            //（横書き RubyText.kt:132-133,238-248 の移植＝縦書き経路でも二重読み・無音落ちを防ぐ）。
            val spoken = remember(paragraph) { spokenTextOf(paragraph) }
            // C: 旧構造はここが BoxWithConstraints で、列高を測るためだけに段落ごとの subcomposition を
            // 起こしていた（列高は全段落共通なので親で一度取れる）。素の Box なら同じ配置（TopStart）のまま。
            // A: 版面は章スコープの貯蔵庫から受け取る＝item が視界を出入りしても組み直さない。
            val slot = remember(store, itemIndex) { store.slot(TypesetSlotId(itemIndex)) }
            val result = slot.resolve(paragraph, bodyConstraints)
            Box(modifier = itemModifier.clearAndSetSemantics { contentDescription = spoken }) {
                VerticalParagraph(
                    layout = result.layout,
                    fontSizePx = result.constraints.fontSizePx,
                    rubyFontSizePx = result.constraints.rubyFontSizePx,
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
    itemIndex: Int,
    colors: ReadingColors,
    store: ChapterTypesetStore,
    constraints: TypesetConstraints,
    itemModifier: Modifier,
) {
    // ⚠️ この段落分割は [buildTypesetPlan] 側と**同じ純関数を同じ順で**呼んでいる＝subIndex の採番が一致する
    // （先行組版が同じスロットを指すのはこの一致に依る。片方だけ数え方を変えないこと）。
    val inner = remember(block) { block.segments.splitIntoParagraphs() }
    // spoken＝ラベル＋中身の読み（当て字は著者読み）。ブロック全体を1音声にする。
    val spoken = remember(block) { block.label + spokenTextOf(block.segments) }

    val labelSlot = remember(store, itemIndex) { store.slot(TypesetSlotId(itemIndex, BLOCK_LABEL_SUB_INDEX)) }
    val labelSegments = remember(block.label) { listOf(TextSegment.Plain(block.label)) }
    val labelResult = labelSlot.resolve(labelSegments, constraints)

    Box(modifier = itemModifier.clearAndSetSemantics { contentDescription = spoken }) {
        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = colors.blockBackground,
            border = BorderStroke(1.dp, colors.blockBorder),
            shape = RoundedCornerShape(0.dp),
        ) {
            // C: 旧構造はここも BoxWithConstraints だった（列高は親の列高 − この padding ぶん＝式で辿れる）。
            Box(modifier = Modifier.padding(Spacing.S16)) {
                // Rtl の Row: 先頭子（ラベル）が右端＝読み順の起点。以降の本文段落がその左へ並ぶ。
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        // ラベルはアクセント色（モック .block .lbl＝藍。横書きも accent で小見出し化）。
                        VerticalParagraph(
                            layout = labelResult.layout,
                            fontSizePx = labelResult.constraints.fontSizePx,
                            rubyFontSizePx = labelResult.constraints.rubyFontSizePx,
                            textColor = colors.accent,
                        )
                        inner.forEachIndexed { subIndex, innerPara ->
                            if (innerPara.isNotEmpty()) {
                                Spacer(Modifier.width(Spacing.S8))
                                val innerSlot = remember(store, itemIndex, subIndex) {
                                    store.slot(TypesetSlotId(itemIndex, BLOCK_LABEL_SUB_INDEX + 1 + subIndex))
                                }
                                val innerResult = innerSlot.resolve(innerPara, constraints)
                                VerticalParagraph(
                                    layout = innerResult.layout,
                                    fontSizePx = innerResult.constraints.fontSizePx,
                                    rubyFontSizePx = innerResult.constraints.rubyFontSizePx,
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

// ────── 先行組版の下ごしらえ（改善 A の駆動部）──────
// 章見出しは LazyRow の item[0]、段落は item[1] から（横書き ChapterContent と同じ item 構成）。
// TypesetSlotId.itemIndex をこの座標系に合わせてあるので、先行組版の窓を firstVisibleItemIndex から直に作れる。
private const val HEADER_ITEM_INDEX = 0
private const val FIRST_PARAGRAPH_ITEM_INDEX = 1

// 前後書きブロック内の subIndex 採番: 0＝ラベル、1+j＝内側 j 番目の段落。
private const val BLOCK_LABEL_SUB_INDEX = 0

// 先行組版で可視範囲の外へどれだけ伸ばすか（item 単位）。可視範囲そのものは常に窓に含まれる
// （窓は「可視の両端 ± ここ」で作るため＝見込みで広く取る必要がない）。
// AHEAD > BEHIND なのは読み進め方向が前だから。実測の可視段落数は 5〜6（2026-08-25・フォント全振り
// 1ドラッグ 55回 ÷ 離散10値）なので、前 8 でおよそ1画面半ぶんを先に用意できる。
private const val PREFETCH_AHEAD = 8
private const val PREFETCH_BEHIND = 4

/**
 * 章1つぶんの「組版すべきもの」一覧。**内容だけで決まる**＝フォント/行間を変えても作り直さない
 * （寸法は [TypesetConstraints] 側だけが持つ）。
 */
private class ChapterTypesetPlan(val slots: List<PlannedSlot>)

/** 組版1件ぶんの素材。[inBlock]＝前後書きブロックの中身か（列高が本文と違う＝別の制約で組む）。 */
private class PlannedSlot(
    val id: TypesetSlotId,
    val segments: List<TextSegment>,
    val inBlock: Boolean,
)

/**
 * 段落の並びから組版スロットを起こす。
 * ⚠️ 分岐は [VerticalParagraphItem] の when と**同じ形**に保つこと（空段落と hr は字面を持たない＝
 * 組版対象にならない）。ここが食い違うと、先行組版が実際には描かれないスロットを組み、
 * 描かれるスロットが窓から漏れる。
 */
private fun buildTypesetPlan(
    headerBody: String,
    paragraphs: List<ImmutableList<TextSegment>>,
): ChapterTypesetPlan {
    val slots = ArrayList<PlannedSlot>()
    slots.add(
        PlannedSlot(TypesetSlotId(HEADER_ITEM_INDEX), listOf(TextSegment.Plain(headerBody)), inBlock = false),
    )
    paragraphs.forEachIndexed { index, paragraph ->
        val itemIndex = index + FIRST_PARAGRAPH_ITEM_INDEX
        val first = paragraph.firstOrNull()
        when {
            paragraph.isEmpty() -> Unit
            paragraph.size == 1 && first is TextSegment.HorizontalRule -> Unit
            paragraph.size == 1 && first is TextSegment.StyledBlock -> {
                slots.add(
                    PlannedSlot(
                        TypesetSlotId(itemIndex, BLOCK_LABEL_SUB_INDEX),
                        listOf(TextSegment.Plain(first.label)),
                        inBlock = true,
                    ),
                )
                // 採番は VerticalStyledBlock 側の forEachIndexed と一致させる（同じ純関数・同じ順）。
                first.segments.splitIntoParagraphs().forEachIndexed { subIndex, innerPara ->
                    if (innerPara.isNotEmpty()) {
                        slots.add(
                            PlannedSlot(
                                TypesetSlotId(itemIndex, BLOCK_LABEL_SUB_INDEX + 1 + subIndex),
                                innerPara,
                                inBlock = true,
                            ),
                        )
                    }
                }
            }
            else -> slots.add(PlannedSlot(TypesetSlotId(itemIndex), paragraph, inBlock = false))
        }
    }
    return ChapterTypesetPlan(slots)
}

/**
 * 可視範囲 [visible] を内包する窓ぶんの組版依頼を、**読み進め方向を先に**並べて返す。
 *
 * 順序に意味がある: 背景の組版は途中で取り消される前提（設定変更・スクロール）なので、手前
 * （＝先に目に入る側）から揃うように並べる。これが在るから途中打ち切りが「見えない欠け」にならない。
 * また、返り値の先頭側が必ず可視範囲になることが [ChapterTypesetStore.typeset] の
 * 「依頼中のスロットは予算超過でも手放さない」の前提を満たす。
 */
private fun ChapterTypesetPlan.requestsAround(
    visible: IntRange,
    bodyConstraints: TypesetConstraints,
    blockConstraints: TypesetConstraints,
    headerConstraints: TypesetConstraints,
): List<TypesetRequest> {
    fun constraintsFor(slot: PlannedSlot): TypesetConstraints = when {
        slot.id.itemIndex == HEADER_ITEM_INDEX -> headerConstraints
        slot.inBlock -> blockConstraints
        else -> bodyConstraints
    }
    val ahead = slots.filter { it.id.itemIndex in visible.first..(visible.last + PREFETCH_AHEAD) }
    val behind = slots.filter {
        it.id.itemIndex in (visible.first - PREFETCH_BEHIND) until visible.first
    }.asReversed()
    return (ahead + behind).map { TypesetRequest(it.id, it.segments, constraintsFor(it)) }
}
