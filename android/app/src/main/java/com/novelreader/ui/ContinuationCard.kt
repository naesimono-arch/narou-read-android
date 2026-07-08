package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors

/**
 * なろうで続きが公開されている場合に、章末尾に表示する継続案内カード。
 *
 * なぜ D 意匠を忠実に翻訳するか:
 * 「和モダン・余白」の統一された美的基準を崩さず、読書の没入感を維持しつつも
 * 次のアクションへ自然に促す視覚的ヒエラルキーを形成するため。
 */
@Composable
internal fun ContinuationCard(
    info: ContinuationInfo,
    colors: ReadingColors,
    bodyMarginDp: Int,
    onReadContinuation: () -> Unit,
    onOpenWorkPage: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // なぜ widthIn(max=600.dp) と padding にするか:
    // 本文を描画する ParagraphItem と完全に版面幅および左右余白を揃えることで、
    // 読書画面全体の整合性を美しく保つため。
    Box(
        modifier = modifier
            .widthIn(max = 600.dp)
            .padding(horizontal = bodyMarginDp.dp)
            .padding(top = 16.dp, bottom = 60.dp)
    ) {
        // カード本体
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = colors.blockBorder, shape = RoundedCornerShape(2.dp))
                .padding(20.dp)
        ) {
            // 見出し: 明朝・16sp・SemiBold。モック h4 相当
            Text(
                text = "ここから先は、なろうで",
                fontFamily = MinchoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                letterSpacing = 0.02.em,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // 説明文
            val description = when (info) {
                is ContinuationInfo.NewEpisodes -> {
                    "手元のPDFは第${info.pdfEpisodes}話まで。なろうには第${info.nextEpisode}〜${info.totalEpisodes}話（新着${info.newCount}話）が公開されています。"
                }
                is ContinuationInfo.UpToDate -> {
                    "手元のPDFは、なろうの公開分（全${info.totalEpisodes}話）に追いついています。"
                }
            }
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = colors.textSecondary,
                lineHeight = 1.8.em,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // 主ボタン (NewEpisodes のときのみ表示。モック .btn-primary)
            if (info is ContinuationInfo.NewEpisodes) {
                Box(
                    // A11y: タップ高さを最小48dpに（背景・文字・余白は現状維持、文言は中央のまま）
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(color = colors.accent, shape = RoundedCornerShape(2.dp))
                        .clickable(onClick = onReadContinuation)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 外部（なろう）へ開くボタンには open-in-new アイコンを添え、別画面へ遷移することを
                    // 図示する（M9/公理8・NovelDetailScreen の外部連携ボタンと同流儀）。当たり判定48dpは
                    // 外側 Box の heightIn(min=48dp) が担うため、中身を Row 化してもタップ域は不変。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = colors.background, // ボタン文字色（藍背景に対するベース色）に揃える
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "第${info.nextEpisode}話から続きを読む",
                            color = colors.background, // 藍背景に対してベース（背景）の文字色
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.em
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ゴーストボタン (常時表示。モック .btn-ghost)
            Box(
                // A11y: タップ高さを最小48dpに（枠線・文字・余白は現状維持、文言は中央のまま）
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(width = 1.dp, color = colors.blockBorder, shape = RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenWorkPage)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // 作品ページも外部（なろう）遷移なので open-in-new アイコンを添える（主ボタンと同流儀）。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colors.textSecondary, // ゴーストボタンの文字色に揃える
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "作品ページを見る",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.em
                    )
                }
            }

            // 解除導線（常時。モック外だが誤紐付け救済に必須）
            // なぜ極小テキストボタンにするか: 邪魔にならない控えめなデザインで、静けさを壊さずに
            // ユーザーの間違いをリセットする救済パスを提供するため。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // A11y: 表示テキストは現寸のまま、当たり判定だけ最小48dpに拡大する
                // （外側Boxをclickable＋最小48dpにし、文字は中央に据え置く）。
                Box(
                    modifier = Modifier
                        .clickable(onClick = onUnlink)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "紐付けを解除",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        // 左端の藍アクセント（モック ::before: left:-1px top:18 bottom:18 の縦帯）
        // なぜ matchParentSize＋内側 Box の二段構えか: matchParentSize が作る固定制約の下では
        // 後続の width(2.dp) が効かず全面塗りになるため、外側でカード高さにだけ追従させ、
        // 幅 2dp の帯は固定制約を受けない内側の子で塗る。
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 18.dp), // 上下 18dp インセット
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(color = colors.accent)
            )
        }
    }
}

/**
 * 未紐付け時に、章末尾に表示する静かな紐付け促し導線。
 */
@Composable
internal fun ContinuationLinkPrompt(
    colors: ReadingColors,
    bodyMarginDp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 余白設計は Card と全く同じにして、同一位置に同じリズムで表示されるようにする。
    Box(
        modifier = modifier
            .widthIn(max = 600.dp)
            .padding(horizontal = bodyMarginDp.dp)
            .padding(top = 16.dp, bottom = 60.dp)
    ) {
        // ゴーストボタン様式1個
        Box(
            // A11y: タップ高さを最小48dpに（枠線・文字・余白は現状維持、文言は中央のまま）
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(width = 1.dp, color = colors.blockBorder, shape = RoundedCornerShape(2.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "なろうで続きを探す",
                color = colors.textSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.1.em
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF8)
@Composable
private fun ContinuationCardPreview_NewEpisodes() {
    ContinuationCard(
        info = ContinuationInfo.NewEpisodes(
            ncode = Ncode("n1234ab"),
            totalEpisodes = 130,
            pdfEpisodes = 127,
            nextEpisode = 128,
            newCount = 3,
        ),
        colors = ReadingTheme.LIGHT.colors,
        bodyMarginDp = 15,
        onReadContinuation = {},
        onOpenWorkPage = {},
        onUnlink = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF8)
@Composable
private fun ContinuationCardPreview_UpToDate() {
    ContinuationCard(
        info = ContinuationInfo.UpToDate(ncode = Ncode("n1234ab"), totalEpisodes = 130),
        colors = ReadingTheme.LIGHT.colors,
        bodyMarginDp = 15,
        onReadContinuation = {},
        onOpenWorkPage = {},
        onUnlink = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF8)
@Composable
private fun ContinuationLinkPromptPreview() {
    ContinuationLinkPrompt(
        colors = ReadingTheme.LIGHT.colors,
        bodyMarginDp = 15,
        onClick = {},
    )
}
