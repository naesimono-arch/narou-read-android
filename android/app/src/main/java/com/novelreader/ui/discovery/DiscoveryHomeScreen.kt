package com.novelreader.ui.discovery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.MoodPreset

// ============================================================
// 発見ホーム（モック discovery-home-D.html の翻訳）。
// ジャンル入口チップ＋order切替タブ＋ランキング一覧。
// モック同様ホーム全体が1本のスクロール（タブは stickyHeader で切替可能性を保つ）。
// ============================================================

/**
 * 発見ホームのルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・状態の collect・初回ロードのトリガといった VM 依存だけを担い、
 * 純粋な描画は [DiscoveryHomeContent] に委ねる（BookshelfScreen と同じ分割方針＝chrisbanes
 * state-holder-ui-split）。なぜ分割するか: 描画層を state+callback の葉にして VM 非依存にし、
 * Robolectric の JVM UI テスト（ADR 0009）で状態分岐・コールバック結線を検証できるようにするため。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoveryHomeScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onOpenGenre: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    onOpenSearch: () -> Unit,
    onPickMood: (MoodPreset) -> Unit,
) {
    val order by viewModel.homeOrder.collectAsStateWithLifecycle()
    val state by viewModel.homeState.collectAsStateWithLifecycle()

    // 画面を開いたときに初回ロード（VM は上位共有のため init ロードしない設計）＝VM 依存はルート層の責務
    LaunchedEffect(Unit) { viewModel.ensureHomeLoaded() }

    DiscoveryHomeContent(
        order = order,
        state = state,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onOpenGenre = onOpenGenre,
        onPickBiggenre = onPickBiggenre,
        onOpenSearch = onOpenSearch,
        onPickMood = onPickMood,
        onSelectOrder = { viewModel.setHomeOrder(it) },
        onRefresh = { viewModel.refreshHome() },
    )
}

/**
 * 発見ホームの描画層（stateless / UI 分割の content）。DiscoveryHomeScreen からの純移動。
 * VM を持たず [order]＋[state]＋コールバックだけでランキング一覧の Loading/Empty/Error/Content 分岐と
 * 各導線の結線を描画する葉。order タブ切替は [onSelectOrder]、Error 再試行は [onRefresh] へ委譲する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoveryHomeContent(
    order: NarouOrder,
    state: DiscoveryUiState,
    onBack: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onOpenGenre: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    onOpenSearch: () -> Unit,
    onPickMood: (MoodPreset) -> Unit,
    onSelectOrder: (NarouOrder) -> Unit,
    onRefresh: () -> Unit,
) {
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
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "検索"
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { MoodSection(onPickMood = onPickMood) }

            item { GenreEntrySection(onOpenGenre = onOpenGenre, onPickBiggenre = onPickBiggenre) }

            // タブはスクロールしても上端に残す（長いランキングの途中でも order を切替できるように）
            stickyHeader {
                OrderTabRow(
                    selected = order,
                    onSelect = onSelectOrder,
                )
            }

            when (val s = state) {
                is DiscoveryUiState.Loading -> item {
                    // fillMaxWidth は旧 DiscoveryStatusBox 内部の fillMaxSize が担っていた横いっぱい＝
                    // 中央寄せの前提。box からサイズ固定を外したので呼び出し側で明示する（見た目維持）。
                    DiscoveryStatusBox(
                        DiscoveryStatus.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.5f),
                    )
                }
                is DiscoveryUiState.Empty -> item {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Empty("作品が見つかりませんでした"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.5f),
                    )
                }
                is DiscoveryUiState.Error -> item {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Error(s.message, onRetry = onRefresh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.5f),
                    )
                }
                is DiscoveryUiState.Content -> {
                    itemsIndexed(
                        s.novels,
                        // なぜ ncode をキーにするか: order 切替や再取得でリスト内容が入れ替わっても各行の
                        // 識別を安定させ、状態・アニメの誤流用を防ぐ（本棚 items(key = { it.id }) と同方針）。
                        // ncode はモデル上 null 許容だが発見結果には常に存在する。防御的に欠損時のみ index
                        // へ退避する（型が違うため ncode 文字列と index の衝突は起きない）。
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            NovelListRow(
                                rank = index + 1,
                                novel = novel,
                                order = order,
                                // 境界: novel.ncode は Moshi 由来の String。詳細遷移の引数は型付き Ncode へ包む。
                                onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 気分プリセット（モック「きょうの気分」節）: 2列カード。
 * ヘアライン枠＋左に青磁2pxルール＋明朝タイトル＝モック .md の翻訳。
 */
@Composable
private fun MoodSection(
    onPickMood: (MoodPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)) {
        Text(
            text = "きょうの気分",
            fontSize = 10.5.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val presets = MoodPreset.entries
        // 4プリセット固定の2列（LazyGrid をネストしない: 親が LazyColumn のため固定 Row で組む）
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { preset ->
                    MoodCard(
                        preset = preset,
                        onClick = { onPickMood(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodCard(
    preset: MoodPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        // 左の青磁ルール（モック .md::before）
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(2.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
            Text(
                text = preset.title,
                fontFamily = MinchoFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = preset.cardLabel,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** ジャンル入口（モック「ジャンルから」節）: 大ジャンルの丸チップ＋ジャンル画面への「すべて →」。 */
@Composable
private fun GenreEntrySection(
    onOpenGenre: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ジャンルから",
                fontSize = 10.5.sp,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "すべて →",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenGenre),
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(NarouGenres.BIGGENRES) { (code, label) ->
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .clickable { onPickBiggenre(code, label) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
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
    modifier: Modifier = Modifier,
) {
    val orders = NarouOrder.entries
    val selectedIndex = orders.indexOf(selected)
    // stickyHeader として背後のリストが透けないよう素地色を敷く
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
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
                val isSelected = order == selected
                // モック .tab（padding 10px 12px / font 12.5px / letter-spacing .06em）準拠の独自タブ。
                // なぜ素の Material3 Tab をやめたか: 素の Tab は既定で最小高さ 48dp を強制し、モックの
                // 約40px よりタブ帯が背高になって画面を過剰占有していた（実機フィードバック#1）。高さを
                // パディングで決める独自タブへ回帰してモック寸法へ揃える。選択下線（2dp）は下の
                // ScrollableTabRow の indicator にそのまま残し、配色（primary/onSurfaceVariant）も維持する。
                Text(
                    text = order.uiLabel,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.75.sp, // モック .tab の letter-spacing .06em（12.5px×0.06）
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    // 未選択タブラベルも意味を運ぶ文字＝infoText（AA 4.5:1）。選択時 primary は据え置き。
                    // Material 標準は onSurfaceVariant だが、ADR 0014-D（意味を運ぶ文字は 4.5:1 ＞ 美学）が
                    // 上位審級のため装飾用トークンでなく情報テキストトークンを採る。
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else LocalShelfColors.current.infoText,
                    modifier = Modifier
                        .clickable { onSelect(order) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
