package com.novelreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.model.TocEntry
import com.novelreader.ui.theme.ReadingColors

/**
 * 目次画面。index.html をパースした TocEntry リストを表示する。
 *
 * @param tocEntries 目次エントリのリスト（空の場合は「章が見つかりません」を表示）
 * @param colors 読書テーマの色トークン（直書き色は禁止。正典は Theme.kt）
 * @param onSelectChapter 章ファイル名を引数にして章選択時に呼ぶコールバック
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeTableOfContentsScreen(
    tocEntries: List<TocEntry>,
    colors: ReadingColors,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
) {
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("目次", fontFamily = FontFamily.Serif) },
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
                    fontFamily = FontFamily.Serif,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                itemsIndexed(tocEntries) { index, entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(entry.fileName) },
                        color = Color.Transparent,
                    ) {
                        Column {
                            Text(
                                text = entry.title.ifEmpty { "第${index + 1}章" },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 24.sp,
                                color = colors.text,
                            )
                            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
