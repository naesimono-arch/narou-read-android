package com.novelreader.ui.discovery

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.narouWorkUrl
import com.novelreader.ui.components.BookCover
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.NovelDetailUiState
import com.novelreader.viewmodel.NovelDetailViewModel
import java.util.Locale

/**
 * 作品詳細画面（モック discovery-detail-D.html）。
 * 書影ヒーロー表示、作者情報、作品ステータス、あらすじ、キーワード、評価項目などを
 * 和モダンの静謐なレイアウトで構築し、最下部に「なろうで読む」外部連携導線を常駐させる。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    ncode: String,
    viewModel: NovelDetailViewModel,
    onKeywordTap: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(ncode) {
        viewModel.load(ncode)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState is NovelDetailUiState.Content) {
                val uriHandler = LocalUriHandler.current
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Button(
                                onClick = {
                                    // なぜ外部ブラウザか: 暫定措置。B2「Webで読む」導線の実装時に
                                    // アプリ内 WebView へ統一する方針（STATUS-api-lab.md §0 の設計方針）。
                                    uriHandler.openUri(narouWorkUrl(ncode))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text(
                                    text = "なろうで読む",
                                    fontSize = 15.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NovelDetailUiState.Loading -> {
                    DiscoveryStatusBox(isLoading = true)
                }
                is NovelDetailUiState.NotFound -> {
                    DiscoveryStatusBox(emptyMessage = "作品が見つかりませんでした（削除または検索除外の可能性）")
                }
                is NovelDetailUiState.Error -> {
                    DiscoveryStatusBox(
                        errorMessage = state.message,
                        onRetry = { viewModel.retry() }
                    )
                }
                is NovelDetailUiState.Content -> {
                    val novel = state.novel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ヒーロー
                        BookCover(
                            bookId = ncode,
                            title = novel.title ?: "",
                            showTitle = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        // 作者・ジャンル行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = novel.writer ?: "",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            NarouGenres.genreLabel(novel.genre)?.let { label ->
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // ステータス表（2列グリッド）
                        val statusText = novelStatusLabel(novel)
                        val length = novel.length
                        val lengthText = if (length != null) {
                            if (length >= 10000) {
                                String.format(Locale.JAPAN, "（%.1f万字）", length / 10000.0)
                            } else {
                                String.format(Locale.JAPAN, "（%,d字）", length)
                            }
                        } else {
                            ""
                        }
                        val readTime = readTimeLabel(novel) ?: "—"
                        val readTimeVal = if (length != null) "$readTime$lengthText" else readTime

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "状態",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = statusText,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "読了目安",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = readTimeVal,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "会話率",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.kaiwaritu?.let { "$it%" } ?: "—",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "挿絵",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.sasieCnt?.let { "${it}枚" } ?: "—",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // あらすじセクション
                        if (!novel.story.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "あらすじ",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                Text(
                                    text = novel.story,
                                    fontFamily = MinchoFamily,
                                    fontSize = 14.sp,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // キーワードセクション
                        val keywords = remember(novel.keyword) {
                            novel.keyword?.split(Regex("[\\s　]+"))?.filter { it.isNotEmpty() } ?: emptyList()
                        }
                        if (keywords.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "キーワード",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    keywords.forEach { keyword ->
                                        Box(
                                            modifier = Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                                .clickable { onKeywordTap(keyword) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = keyword,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 評価セクション
                        val evalItems = remember(novel) {
                            listOf(
                                "総合評価" to novel.globalPoint?.let { String.format(Locale.JAPAN, "%,d pt", it) },
                                "ブックマーク" to novel.favNovelCnt?.let { String.format(Locale.JAPAN, "%,d 件", it) },
                                "評価者数" to novel.allHyokaCnt?.let { String.format(Locale.JAPAN, "%,d 人", it) },
                                "週間ポイント" to novel.weeklyPoint?.let { String.format(Locale.JAPAN, "%,d pt", it) }
                            ).filter { it.second != null }
                        }
                        if (evalItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "評価",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                evalItems.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pair.first,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pair.second!!,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (index < evalItems.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        // 最終更新表示
                        val lastupText = remember(novel.generalLastup) {
                            try {
                                val datePart = novel.generalLastup?.split(" ")?.firstOrNull() ?: ""
                                val ymd = datePart.split("-")
                                if (ymd.size >= 3) {
                                    val year = ymd[0].toInt()
                                    val month = ymd[1].toInt()
                                    val day = ymd[2].toInt()
                                    "${year}年${month}月${day}日 更新"
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (lastupText != null) {
                            Text(
                                text = lastupText,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 20.dp, bottom = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
