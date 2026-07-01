package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.model.TocEntry
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors

/**
 * 目次画面。index.html をパースした TocEntry リストを表示する。
 *
 * @param tocEntries 目次エントリのリスト（空の場合は「章が見つかりません」を表示）
 * @param colors 読書テーマの色トークン（直書き色は禁止。正典は Theme.kt）
 * @param currentChapterFile 最後に表示していた章のファイル名（null なら未読。ハイライト＋自動スクロールに使う）
 * @param onSelectChapter 章ファイル名を引数にして章選択時に呼ぶコールバック
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeTableOfContentsScreen(
    tocEntries: List<TocEntry>,
    colors: ReadingColors,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
) {
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("目次", fontFamily = MinchoFamily) },
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
        if (tocEntries.isEmpty()) {
            // 章リンクが0件の場合は空状態を表示（例外を投げない）
            Box(
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
        } else {
            val listState = rememberLazyListState()
            val currentIndex = tocEntries.indexOfFirst { it.fileName == currentChapterFile }

            // 初期表示時に現在章まで自動スクロールする（長編で毎回先頭から探す手間をなくす）。
            // tocEntries は非同期ロードのため、空 → 充足のタイミングでも発火するよう key に含める。
            LaunchedEffect(tocEntries, currentChapterFile) {
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
                itemsIndexed(tocEntries) { index, entry ->
                    val isCurrent = index == currentIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(entry.fileName) },
                        // 現在章は淡いアクセント背景で「面」として示し、文字色だけより見落としにくくする
                        color = if (isCurrent) colors.accent.copy(alpha = 0.08f) else Color.Transparent,
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
    }
}
