package com.novelreader.ui.discovery

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.ResultSource
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
                val baseLabels = conditionChipLabels(ctx.query)
                val labels = if (ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty()) {
                    // なぜ: ジャンル未指定時は、並び順チップ（baseLabels の最後）の直前に「ジャンル ⌄」チップを追加し、その場変更を可能にする。
                    val mutable = baseLabels.toMutableList()
                    if (mutable.isNotEmpty()) {
                        mutable.add(mutable.lastIndex, "ジャンル")
                    } else {
                        mutable.add("ジャンル")
                    }
                    mutable
                } else {
                    baseLabels
                }
                val biggenreLabel = ctx.query.biggenres.firstOrNull()?.let { NarouGenres.biggenreLabel(it) }
                val genreLabel = ctx.query.genres.firstOrNull()?.let { NarouGenres.genreLabel(it) }

                labels.forEachIndexed { index, label ->
                    // why: conditionChipLabels は末尾に必ず並び順を add するため、最後が並び順チップである。
                    val isOrderChip = index == labels.lastIndex
                    val isBiggenreChip = ctx.query.biggenres.size == 1 && label == biggenreLabel
                    val isGenreChip = ctx.query.genres.size == 1 && label == genreLabel
                    val isGenrePlaceholderChip = ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty() && label == "ジャンル"

                    val isGenreFilterChip = isBiggenreChip || isGenreChip || isGenrePlaceholderChip
                    val isClickable = isOrderChip || isGenreFilterChip

                    if (isClickable) {
                        var expanded by remember { mutableStateOf(false) }
                        val displayLabel = "$label ⌄"

                        Box {
                            Text(
                                text = displayLabel,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable { expanded = true }
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            )

                            if (isOrderChip) {
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    NarouOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = order.uiLabel,
                                                    fontWeight = if (ctx.query.order == order) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                viewModel.changeResultOrder(order)
                                            }
                                        )
                                    }
                                }
                            } else if (isGenreFilterChip) {
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    // 1. すべてのジャンル
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "すべてのジャンル",
                                                fontWeight = if (ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            expanded = false
                                            viewModel.changeResultGenreFilter(emptySet(), emptySet())
                                        }
                                    )
                                    // 2. 大ジャンル＋配下小ジャンル
                                    NarouGenres.BIGGENRES.forEach { (bigCode, bigName) ->
                                        val isCurrentBig = ctx.query.biggenres.size == 1 && ctx.query.biggenres.first() == bigCode
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = bigName,
                                                    fontWeight = if (isCurrentBig) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isCurrentBig) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                viewModel.changeResultGenreFilter(setOf(bigCode), emptySet())
                                            }
                                        )
                                        NarouGenres.GENRES_BY_BIG[bigCode]?.forEach { (genreCode, genreName) ->
                                            val isCurrentGenre = ctx.query.genres.size == 1 && ctx.query.genres.first() == genreCode
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = genreName,
                                                        modifier = Modifier.padding(start = 16.dp),
                                                        fontWeight = if (isCurrentGenre) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrentGenre) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                                                        fontSize = 13.sp
                                                    )
                                                },
                                                onClick = {
                                                    expanded = false
                                                    viewModel.changeResultGenreFilter(emptySet(), setOf(genreCode))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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

                if (ctx.source == ResultSource.SEARCH) {
                    // why: 「条件を変更」で戻る先はDiscoverySearchScreen(検索画面)。
                    // ジャンル・気分等で出すと戻り先に条件シートがなく騙し導線になるため、SEARCH のみに限定する。
                    Text(
                        text = "条件を変更",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { onBack() }
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
