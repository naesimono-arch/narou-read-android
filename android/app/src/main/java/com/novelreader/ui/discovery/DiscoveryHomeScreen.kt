package com.novelreader.ui.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel

// ============================================================
// 発見ホーム（モック discovery-home-D.html の翻訳）。
// C1: order切替タブ＋ランキング一覧。タブはヘアライン上に藍の下線（モック .tabs/.tab.on）。
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHomeScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit,
    onOpenDetail: (ncode: String) -> Unit,
) {
    val order by viewModel.homeOrder.collectAsState()
    val state by viewModel.homeState.collectAsState()

    // 画面を開いたときに初回ロード（VM は上位共有のため init ロードしない設計）
    LaunchedEffect(Unit) { viewModel.ensureHomeLoaded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "見つける",
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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OrderTabRow(
                selected = order,
                onSelect = { viewModel.setHomeOrder(it) },
            )
            Box(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is DiscoveryUiState.Loading -> DiscoveryStatusBox(isLoading = true)
                    is DiscoveryUiState.Empty -> DiscoveryStatusBox(emptyMessage = "作品が見つかりませんでした")
                    is DiscoveryUiState.Error -> DiscoveryStatusBox(
                        errorMessage = s.message,
                        onRetry = { viewModel.refreshHome() },
                    )
                    is DiscoveryUiState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                        ) {
                            itemsIndexed(s.novels) { index, novel ->
                                NovelListRow(
                                    rank = index + 1,
                                    novel = novel,
                                    order = order,
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

/**
 * orderタブ（モック .tabs）: 素地と同色の下地にヘアライン、選択タブに藍2dpの下線。
 * なぜ ScrollableTabRow か: 6タブは狭幅端末で均等割りすると文字が窮屈になるため、
 * モックどおり左寄せ・横スクロールにする。
 */
@Composable
private fun OrderTabRow(
    selected: NarouOrder,
    onSelect: (NarouOrder) -> Unit,
) {
    val orders = NarouOrder.entries
    val selectedIndex = orders.indexOf(selected)
    Column {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 24.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            orders.forEach { order ->
                Tab(
                    selected = order == selected,
                    onClick = { onSelect(order) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = order.uiLabel,
                            fontSize = 12.5.sp,
                            fontWeight = if (order == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
        // タブ全幅のヘアライン（モックはタブ列の下に1px線・選択下線がその上に重なる）
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
