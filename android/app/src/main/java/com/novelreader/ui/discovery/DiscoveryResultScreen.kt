package com.novelreader.ui.discovery

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel
import java.util.Locale

// ============================================================
// 結果一覧＝検索・ジャンル・気分プリセットの共通着地
// （モック discovery-home-D.html フレーム2の翻訳）。
// 文脈ヘッダ（明朝見出し＋補足）＋条件チップ＋件数＋一覧行。
// ============================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoveryResultScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit,
    onOpenDetail: (ncode: String) -> Unit,
) {
    val context by viewModel.resultContext.collectAsState()
    val state by viewModel.resultState.collectAsState()

    // プロセス再生成などで文脈が失われてこの画面に着地した場合は戻る
    // （resultContext は VM のメモリ上にしか無く、復元して見せる意味のある状態ではないため）。
    val ctx = context
    if (ctx == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ctx.title,
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 21.sp,
                        letterSpacing = 1.5.sp,
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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ctx.subtitle?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            // 条件チップ（藍の細枠・モック .cd）
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
            ) {
                conditionChipLabels(ctx.query).forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is DiscoveryUiState.Loading -> DiscoveryStatusBox(isLoading = true)
                    is DiscoveryUiState.Empty -> DiscoveryStatusBox(
                        emptyMessage = "条件に合う作品が見つかりませんでした"
                    )
                    is DiscoveryUiState.Error -> DiscoveryStatusBox(
                        errorMessage = s.message,
                        onRetry = { viewModel.refreshResult() },
                    )
                    is DiscoveryUiState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                        ) {
                            item {
                                // 総件数（モック .cnt・青磁）
                                Text(
                                    text = "${String.format(Locale.JAPAN, "%,d", s.allcount)} 作品",
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(s.novels) { index, novel ->
                                NovelListRow(
                                    rank = index + 1,
                                    novel = novel,
                                    order = ctx.query.order,
                                    onClick = { novel.ncode?.let(onOpenDetail) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
