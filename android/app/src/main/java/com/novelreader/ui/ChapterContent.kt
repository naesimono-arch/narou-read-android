package com.novelreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.ui.compose.RubyText
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** 章本文を LazyColumn でレンダリングする */
@Composable
internal fun ChapterContent(
    content: ChapterContent,
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    lazyListState: LazyListState = rememberLazyListState(),
    // 最終章の本文末尾に差し込む継続導線スロット（PDF↔Web継続読書）。null = 差し込まない。
    // なぜスロット渡しか: ChapterContent を narou 層（API・紐付け状態）へ依存させず、
    // 「何を出すか」の判断を呼び出し側（ChapterScreen）に残すため。
    continuation: (@Composable () -> Unit)? = null,
) {
    val paragraphs = remember(content) { content.segments.splitIntoParagraphs() }

    // 本文段落の TextStyle は全段落で共通（色・文字サイズ・行間のみに依存）なので、
    // LazyColumn の items 内（＝可視段落ごと）で作らず、ここで1回だけ生成して各 item へ渡す。
    // なぜ hoist するか: 以前は ParagraphItem 内で段落ごとに remember していたため、可視段落数ぶん
    // 同一 TextStyle インスタンスが並存していた。3入力（colors.text/fontSize/lineHeightEm）を key に
    // 章単位で1つに集約する。
    val bodyStyle = remember(colors.text, fontSize, lineHeightEm) {
        TextStyle(
            color = colors.text,
            // ユーザー設定の文字サイズ。lineHeight が em（相対値）のため行間も自動でスケールする
            fontSize = fontSize.sp,
            // ユーザー設定の行間（em）。RubyText も style=bodyStyle 経由でこの lineHeight を受け取るため、
            // ここ1か所の変更でルビ行にも反映される。可変幅は 2.3〜2.8em に絞って前行とのルビ被りを抑制。
            lineHeight = lineHeightEm.em,
            fontFamily = MinchoFamily,
            letterSpacing = 0.sp,
            // なぜ Trim.LastLineBottom か:
            // lineHeight を RubyText 内折り返しとParagraphItem 間で統一するため。
            // LastLineBottom のみ除去することで上 leading（ルビ描画領域）を保ちつつ
            // composable 高さを確定させる（em 指定のため文字サイズ・行間変更時も比率は維持される）。
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.LastLineBottom,
            ),
        )
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize(),
        // なぜ中央寄せか: widthIn(max=600dp) が効く広幅端末（タブレット等）で
        // 本文ブロックが左に張り付くのを防ぐ。スマホ幅では常に全幅なので影響しない。
        horizontalAlignment = Alignment.CenterHorizontally,
        // なぜ contentPadding で確保するか:
        // TopAppBar がオーバーレイ配置のため Scaffold の innerPadding にバー分が含まれない。
        // Box の padding にすると全画面（ローディング等）に影響しバー非表示時も常に隙間が残る。
        // contentPadding はスクロール領域内の余白なので、中盤では画面外に収まり本文位置に影響しない。
        // 章の最上部でのみバー高さ分のスペースが確保され、先頭行がバーに隠れなくなる。
        // なぜ statusBars を加算するか: Edge-to-Edge 表示では TopAppBar の実高が
        // 64dp + ステータスバーインセットになるため、64dp 固定だと先頭行がバーに隠れる。
        // bottom: オーバーレイ化したボトムバー（実高 ≒ 80dp + ナビバーインセット）の分を確保し、
        // 末尾行がバーに隠れないようにする。ナビバー実高は端末（ボタン/ジェスチャー）で異なるため実測値を加算。
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
        ),
    ) {
        // 章見出し（モック reading-D .chap-h）: 章タイトルを明朝で中央寄せ＋藍の短ルール。
        // なぜ本文先頭に置くか: 没入時はトップバーが隠れるため、ここが唯一の章タイトル表示になる。
        item {
            ChapterHeader(
                title = content.title,
                colors = colors,
                fontSize = fontSize,
                bodyMarginDp = bodyMarginDp,
            )
        }

        // 段落ごとにレンダリング。
        // なぜ contentType を付けるか: 段落は「空行・水平線・前後書きブロック・通常テキスト」の
        // 4種が混在し描画ノード構造が異なる。contentType を渡すと LazyColumn が同種アイテム間だけで
        // コンポジション/レイアウトノードを再利用するため、スクロール時に異種ノードへ流用しての
        // 再レイアウトを避けられる（通常テキスト段落同士の再利用が効くのが主効果）。
        // key は付けない: 段落は一意な安定IDを持たず（空段落・同一文が反復し得る）、位置 index が
        // 唯一の一意キー＝items のデフォルトと同じ。非一意キーは Compose の一意制約に反し状態を壊す。
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
            ParagraphItem(
                paragraph = paragraph,
                colors = colors,
                bodyStyle = bodyStyle,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    // ユーザー設定の左右余白（スマホ幅では実質これが行長を決める）
                    .padding(horizontal = bodyMarginDp.dp),
            )
        }

        // 継続導線（最終章のみ非null）。本文を読み切った位置に静かに現れる
        // （モック reading-continuation-D.html: 了マークの後に継続カード）。
        if (continuation != null) {
            item { continuation() }
        }
        // 旧 Spacer(80dp) は上の contentPadding.bottom へ移行（バー実高＋ナビバー実高で算出）
    }
}

/** 章見出し（モック reading-D .chap-h）。章タイトルを明朝で中央寄せし、下に藍の短いルールを引く。 */
@Composable
private fun ChapterHeader(
    title: String,
    colors: ReadingColors,
    fontSize: Int,
    bodyMarginDp: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            // 本文と同じユーザー設定余白に追従させ、見出しと本文の版面を揃える
            .padding(horizontal = bodyMarginDp.dp)
            .padding(top = 14.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontFamily = MinchoFamily,
            // 本文よりわずかに大きい見出しサイズ。ユーザーの文字サイズ設定にも追従させる。
            fontSize = (fontSize + 2).sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 1.6.em,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(15.dp))
        // 藍の短いルール（48dp×2dp）。モック .chap-h .rule（--rule 藍 opacity .85）。
        // colors.hr は素地に溶けた淡い区切り用のため使わない。--rule 専用の colors.rule を使う。
        // なぜ accent でなく rule か: DARK では --rule #5E7E9C ≠ accent #6E96B8 で乖離するため。
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(colors.rule.copy(alpha = 0.85f)),
        )
    }
}

/** 1段落分を描画する。空段落は Spacer、StyledBlock は背景付き Surface で描画 */
@Composable
private fun ParagraphItem(
    paragraph: ImmutableList<TextSegment>,
    colors: ReadingColors,
    // 全段落共通の本文スタイル。呼び出し側（ChapterContent）で章単位に1つ生成して渡す（hoist 済み）。
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    when {
        paragraph.isEmpty() -> {
            // 空段落: なろう系小説のシーン転換・演出として意図的な空行を保持する
            // なぜフィルタリングしないか: 削除すると原作者の意図が失われるため
            // 空行 = 20dp Spacer + 次アイテムの上 leading 13.5dp = 計 47.5dp ≈ WebView の空行
            Spacer(modifier = Modifier.height(20.dp))
        }
        paragraph.size == 1 && paragraph[0] is TextSegment.HorizontalRule -> {
            // 水平線（html_exporter.py の <hr> に対応＝シーン区切り）
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
                    .height(1.dp),
            ) {
                // D 様式: 旧・全幅破線をやめ、中央寄せの短い実線にする。
                // なぜ短い実線か: D「和モダン・余白」は藍の細ルールで静かに区切る思想で、
                // 全幅破線は主張が強すぎるため。モック reading-D.html の hr(width:42%) に対応。
                // 色 colors.hr は藍を素地に溶かした青灰のため、これ自体が控えめな区切りになる。
                val lineWidth = size.width * 0.42f
                val startX = (size.width - lineWidth) / 2f
                drawLine(
                    color = colors.hr,
                    start = Offset(startX, 0f),
                    end = Offset(startX + lineWidth, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        paragraph.size == 1 && paragraph[0] is TextSegment.StyledBlock -> {
            // 前書き・後書きブロック（背景色付き領域）
            val block = paragraph[0] as TextSegment.StyledBlock
            val innerParagraphs = block.segments.splitIntoParagraphs()
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                color = colors.blockBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.blockBorder),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text(
                        text = block.label,
                        // モック .block .lbl: ラベルは藍（accent）。本文インク色ではなくアクセントで小見出し化する。
                        style = bodyStyle.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    innerParagraphs.forEach { innerPara ->
                        if (innerPara.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                        } else {
                            // padding(bottom=14.dp): 下 padding + 次アイテムの上 leading = 27.5dp ≈ 折り返し行間
                            RubyText(
                                segments = innerPara,
                                style = bodyStyle,
                                rubyColor = colors.ruby,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // 通常の段落
            // padding(bottom=14.dp): 下 padding + 次アイテムの上 leading = 27.5dp ≈ 折り返し行間
            RubyText(
                segments = paragraph,
                style = bodyStyle,
                rubyColor = colors.ruby,
                modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
        }
    }
}

/**
 * TextSegment リストを LineBreak で段落分割する。
 * 空段落（LineBreak 連続）はフィルタリングせず保持する。
 * なぜか: なろう系小説では連続空行によるシーン転換演出が頻出するため。
 */
private fun List<TextSegment>.splitIntoParagraphs(): List<ImmutableList<TextSegment>> {
    val result = mutableListOf<ImmutableList<TextSegment>>()
    val current = mutableListOf<TextSegment>()

    for (segment in this) {
        when {
            segment is TextSegment.LineBreak -> {
                result.add(current.toImmutableList())
                current.clear()
            }
            segment is TextSegment.HorizontalRule -> {
                // 水平線は独立した段落として扱う
                if (current.isNotEmpty()) {
                    result.add(current.toImmutableList())
                    current.clear()
                }
                result.add(listOf(segment).toImmutableList())
            }
            segment is TextSegment.StyledBlock -> {
                // 前書き・後書きも独立した段落として扱う
                if (current.isNotEmpty()) {
                    result.add(current.toImmutableList())
                    current.clear()
                }
                result.add(listOf(segment).toImmutableList())
            }
            else -> current.add(segment)
        }
    }
    if (current.isNotEmpty()) result.add(current.toImmutableList())

    return result
}
