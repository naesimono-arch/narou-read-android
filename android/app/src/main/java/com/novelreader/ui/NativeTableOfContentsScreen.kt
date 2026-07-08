package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.model.TocEntry
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors

/**
 * 目次のロード状態。
 * なぜ sealed で4状態に分けるか: 以前は `List<TocEntry>` 1本で表現していたため、
 * 非同期パースが未完了（＝まだ空）の一瞬と「真に章が0件」を区別できず、パース中に
 * 「章が見つかりません」を誤表示していた（公理8＝状態の可視性）。Loading/Empty/Error/Content を
 * 明示的に持つことで、ロード中はスケルトン・例外時は再試行・真の空だけ CTA を出し分ける。
 */
sealed interface TocState {
    /** index.html を非同期パース中（初期状態）。スケルトンを表示する。 */
    data object Loading : TocState

    /** パースは完了したが章リンクが0件（真の空）。CTA を表示する。 */
    data object Empty : TocState

    /** パース中に例外が発生。メッセージと再試行導線を表示する。 */
    data class Error(val message: String) : TocState

    /** 章リンクを取得できた通常状態。 */
    data class Content(val entries: List<TocEntry>) : TocState
}

/**
 * 目次画面。index.html をパースした結果を状態別に表示する。
 *
 * @param tocState 目次のロード状態（Loading/Empty/Error/Content）
 * @param colors 読書テーマの色トークン（直書き色は禁止。正典は Theme.kt）
 * @param currentChapterFile 最後に表示していた章のファイル名（null なら未読。ハイライト＋自動スクロールに使う）
 * @param onSelectChapter 章ファイル名を引数にして章選択時に呼ぶコールバック
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 * @param onRetry 目次パースを再試行するコールバック（Error 状態の再読込に使う）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeTableOfContentsScreen(
    tocState: TocState,
    colors: ReadingColors,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    // モック toc-D .topbar h1: 明朝・やや大きめ・字間を開けた「和」の題字
                    Text(
                        "目次",
                        fontFamily = MinchoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.12.em,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToBookshelf) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "本棚に戻る",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    // システムテーマ（MaterialTheme）ではなく読書テーマに追従させるため明示指定
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.topBarIcon,
                ),
            )
        },
    ) { innerPadding ->
        when (tocState) {
            // ロード中: 章リストの骨格だけを見せて「読み込んでいる」ことを伝える（白画面や誤空表示を防ぐ）
            is TocState.Loading -> TocSkeleton(
                colors = colors,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            // 例外時: 原因メッセージ＋再試行。本棚退避は上部の戻るボタンで可能なためここでは再試行に絞る
            is TocState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "目次の読み込みに失敗しました",
                    color = colors.textSecondary,
                    fontFamily = MinchoFamily,
                    fontSize = 16.sp,
                )
                Text(
                    text = tocState.message,
                    color = colors.textSecondary.copy(alpha = 0.75f),
                    fontFamily = MinchoFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                OutlinedButton(onClick = onRetry) {
                    Text("再試行", fontFamily = MinchoFamily)
                }
            }

            // 真に0件のときだけ CTA を出す（Loading と区別できるようになったため誤表示しない）
            is TocState.Empty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "章が見つかりません",
                    color = colors.textSecondary,
                    fontFamily = MinchoFamily,
                )
            }

            is TocState.Content -> TocList(
                entries = tocState.entries,
                colors = colors,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                innerPadding = innerPadding,
            )
        }
    }
}

/** 章リスト本体（Content 状態）。旧実装のリスト描画をそのまま切り出したもの。 */
@Composable
private fun TocList(
    entries: List<TocEntry>,
    colors: ReadingColors,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val listState = rememberLazyListState()
    val currentIndex = entries.indexOfFirst { it.fileName == currentChapterFile }

    // 初期表示時に現在章まで自動スクロールする（長編で毎回先頭から探す手間をなくす）。
    // entries は非同期ロードのため、現在章が変わったタイミングでも発火するよう key に含める。
    LaunchedEffect(entries, currentChapterFile) {
        if (currentIndex >= 0) {
            // 1つ手前まで見せることで「現在章が先頭に張り付いて前後関係が分からない」のを防ぐ
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        itemsIndexed(entries) { index, entry ->
            val isCurrent = index == currentIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChapter(entry.fileName) },
                // 現在章は淡いアクセント背景で「面」として示し、文字色だけより見落としにくくする
                color = if (isCurrent) colors.accent.copy(alpha = 0.06f) else Color.Transparent,
            ) {
                Column {
                    // height(IntrinsicSize.Min) で左バーの fillMaxHeight をテキスト行高に一致させる
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // 現在章は行頭の左アクセントバーで一目で分かるようにする。
                        // 非現在章も透明な同幅バーを置き、全行のテキスト開始位置を揃える。
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    if (isCurrent) colors.accent else Color.Transparent,
                                ),
                        )
                        Text(
                            text = entry.title.ifEmpty { "第${index + 1}章" },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontSize = 16.sp,
                            fontFamily = MinchoFamily,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 24.sp,
                            // 現在読んでいる章はアクセント色＋太字で示す
                            color = if (isCurrent) colors.accent else colors.text,
                        )
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * 目次ロード中のスケルトン。章リストと同じ行構成（左バー幅＋テキスト行＋区切り線）の
 * プレースホルダを並べ、充足後のレイアウト飛びを抑える。
 * なぜ静的（アニメ無し）か: 過剰演出を避け、既存画面に無いシマー等の新規視覚要素を持ち込まないため。
 */
@Composable
private fun TocSkeleton(colors: ReadingColors, modifier: Modifier = Modifier) {
    // プレースホルダ帯の色は本文色を薄く落として素地に馴染ませる（テーマトークン経由・直書き禁止）
    val barColor = colors.text.copy(alpha = 0.07f)
    Column(modifier = modifier) {
        // 章題の長さに揺らぎを持たせて「文章の目次」らしく見せる（0.55〜0.9 の幅）
        val widthFractions = listOf(0.85f, 0.6f, 0.9f, 0.7f, 0.8f, 0.55f, 0.88f, 0.65f)
        widthFractions.forEach { fraction ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 実リストの左アクセントバーと同じ 4dp 分のインデントを確保して整列させる
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
                )
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        }
    }
}
