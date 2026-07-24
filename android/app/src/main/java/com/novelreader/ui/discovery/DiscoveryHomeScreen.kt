package com.novelreader.ui.discovery

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontPresetCaption
import com.novelreader.ui.theme.FontPresetTitle
import com.novelreader.ui.theme.FontScreenTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.skins.j.DiscoveryHomePortalJ
import com.novelreader.ui.skins.k.DiscoveryHomeK
import com.novelreader.ui.skins.m.DiscoveryHomeSkyM
import com.novelreader.ui.skins.p.DiscoveryHomeCartridgeP
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import com.novelreader.ui.theme.Spacing

// 独自タブの縦パディング（拡張7段スケール外の較正値）。Material 既定 48dp だとタブ帯が背高で画面を
// 過剰占有した実機フィードバック#1を受け、モックの ~40px 高へ詰めた値＝10dp。S12(12) へ丸めると
// 上下 +2dp で詰めた高さを押し戻し較正を壊すため、離散化せず 10dp を保持する（横は 12=S12 でオンスケール）。
private val TabVerticalPadding = 10.dp

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
    // スキンM/P/J は発見ホームを画面丸ごと各スキン構造へ委譲する薄いルーター（ADR 0022 §1）。
    // exhaustive（else 不使用＝新スキンを無音でDに落とさない）。D/C はこの下の共通 Scaffold で描く。
    when (LocalSkin.current) {
        // 星図構造へ委譲。
        Skin.SEIZU_M -> {
            DiscoveryHomeSkyM(
                order = order,
                state = state,
                onBack = onBack,
                onOpenDetail = onOpenDetail,
                onOpenGenre = onOpenGenre,
                onPickBiggenre = onPickBiggenre,
                onOpenSearch = onOpenSearch,
                onPickMood = onPickMood,
                onSelectOrder = onSelectOrder,
                onRefresh = onRefresh,
            )
            return
        }
        // 店構造へ委譲。
        Skin.CARTRIDGE_P -> {
            DiscoveryHomeCartridgeP(
                order = order,
                state = state,
                onBack = onBack,
                onOpenDetail = onOpenDetail,
                onOpenGenre = onOpenGenre,
                onPickBiggenre = onPickBiggenre,
                onOpenSearch = onOpenSearch,
                onPickMood = onPickMood,
                onSelectOrder = onSelectOrder,
                onRefresh = onRefresh,
            )
            return
        }
        // 扉の回廊構造へ委譲。
        Skin.PORTAL_J -> {
            DiscoveryHomePortalJ(
                order = order,
                state = state,
                onBack = onBack,
                onOpenDetail = onOpenDetail,
                onOpenGenre = onOpenGenre,
                onPickBiggenre = onPickBiggenre,
                onOpenSearch = onOpenSearch,
                onPickMood = onPickMood,
                onSelectOrder = onSelectOrder,
                onRefresh = onRefresh,
            )
            return
        }
        // 明快構造（さがす＝実検索＋自己説明見出し＋公式サイト逃げ道）へ委譲。
        Skin.MEIKAI_K -> {
            DiscoveryHomeK(
                order = order,
                state = state,
                onBack = onBack,
                onOpenDetail = onOpenDetail,
                onOpenGenre = onOpenGenre,
                onPickBiggenre = onPickBiggenre,
                onOpenSearch = onOpenSearch,
                onPickMood = onPickMood,
                onSelectOrder = onSelectOrder,
                onRefresh = onRefresh,
            )
            return
        }
        Skin.WAMODERN_D, Skin.YAKO_C -> Unit // 既定描画へ（この下の共通実装が D/C を描く）
    }

    // 期間タブ切替のスクロール位置リセット対策（実機報告 2026-07-19・キャッシュ無し時／M の横展開）。
    // 真因: refreshHome() が再取得のたびに一旦 Loading を挟むため、この単一 LazyColumn では Content の
    // ランキング行が Loading 中に status ボックス1件へ全置換され、総コンテンツ高が見出し＋1ボックスまで縮む。
    // すると LazyListState は firstVisibleItem/offset を維持できず先頭側へクランプされ強制リセットに見える。
    // 対処: 直近に描けた Content を控え、再取得(Loading)中はそのランキング骨格（同 key=ncode）を出し続けて
    // スクロールアンカーを保つ（stale-while-revalidate）。Empty/Error は真に0件・失敗ゆえ畳んで良い。VM 非改変。
    var lastContent by remember { mutableStateOf<DiscoveryUiState.Content?>(null) }
    // 合成中の書き戻しを避け Content を側効果で控える（Content 分岐は s を直接描くため表示に遅延はない）。
    LaunchedEffect(state) { (state as? DiscoveryUiState.Content)?.let { lastContent = it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "見つける",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = FontScreenTitle,
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
                // トップバーの検索アイコン（1個）は撤去し、常時可視の実検索フィールド（下の SearchFieldD＝検索第一）へ
                // 格上げする（K 形伝播・モック discovery-D.html のトップバー表現に従う）。
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
            // 固定トップ（モック .top）: 実検索フィールド＝検索第一（K 形伝播）。見出し「見つける」は上の TopAppBar が
            // 担い、検索はトップバーのアイコン1個から常時可視の実フィールドへ格上げ（モック discovery-D.html に従う）。
            SearchFieldD(onOpenSearch = onOpenSearch)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

                // 再取得(Loading)中は直近 Content の骨格を出し続けてスクロールアンカーを保つ（初回ロードは
                // 骨格未確定＝status ボックス）。Empty/Error は一覧を畳んで良い（真に0件・失敗ゆえ status が妥当）。
                val rowsContent = when (val s = state) {
                    is DiscoveryUiState.Content -> s
                    is DiscoveryUiState.Loading -> lastContent
                    else -> null
                }
                when {
                    rowsContent != null -> {
                        itemsIndexed(
                            rowsContent.novels,
                            // なぜ ncode をキーにするか: order 切替や再取得でリスト内容が入れ替わっても各行の
                            // 識別を安定させ、状態・アニメの誤流用を防ぐ（本棚 items(key = { it.id }) と同方針）。
                            // ncode はモデル上 null 許容だが発見結果には常に存在する。防御的に欠損時のみ index
                            // へ退避する（型が違うため ncode 文字列と index の衝突は起きない）。
                            key = { index, novel -> novel.ncode ?: index },
                        ) { index, novel ->
                            Column(modifier = Modifier.padding(horizontal = Spacing.S24)) {
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
                    state is DiscoveryUiState.Loading -> item {
                        // fillMaxWidth は旧 DiscoveryStatusBox 内部の fillMaxSize が担っていた横いっぱい＝
                        // 中央寄せの前提。box からサイズ固定を外したので呼び出し側で明示する（見た目維持）。
                        DiscoveryStatusBox(
                            DiscoveryStatus.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.5f),
                        )
                    }
                    state is DiscoveryUiState.Empty -> item {
                        DiscoveryStatusBox(
                            DiscoveryStatus.Empty("作品が見つかりませんでした"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.5f),
                        )
                    }
                    state is DiscoveryUiState.Error -> item {
                        DiscoveryStatusBox(
                            DiscoveryStatus.Error(state.message, onRetry = onRefresh),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.5f),
                        )
                    }
                }

                // 末尾: 公式サイトで探す逃げ道（K 形伝播・モック .official）。K の OfficialLinkK と同一導線を配線。
                item { OfficialLinkD() }
            }
        }
    }
}

/**
 * 固定トップの実検索フィールド（モック discovery-D.html .search）: 和紙地（surfaceVariant）＋2px 直角＋ヘアライン。
 * タップで検索画面へ（onOpenSearch＝D 経路の既存導線を再利用・新規機能は発明しない）。K の SearchHeaderK と
 * 同じ「検索第一」の役割だが、意匠は D モックに忠実（K の 12dp 角丸でなく D 署名の 2dp 直角・枠線あり）。
 */
@Composable
private fun SearchFieldD(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            // .top 内の .search（margin-top 14px）。見出しは TopAppBar が担うため横 S24＋上下の呼吸のみ。
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S8, bottom = Spacing.S12)
            .fillMaxWidth()
            .height(50.dp)                       // .search 50px（構造値＝スケール外）
            .clip(RoundedCornerShape(2.dp))      // .search border-radius 2px（D 署名の直角）
            .background(MaterialTheme.colorScheme.surfaceVariant) // --field（和紙地系の薄地）
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)) // --line ヘアライン
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = Spacing.S16),  // .search padding 0 16px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,           // 隣接プレースホルダ文が読み上げを担う（K と同型）
            tint = LocalShelfColors.current.infoText, // .search svg stroke --ink-soft（意味メタは AA の infoText で受ける）
            modifier = Modifier.size(19.dp),     // .search svg 19px
        )
        Text(
            "作品名・作者名・キーワードで探す",
            fontSize = FontSubTitle,             // .search span 13.5px≈FontSubTitle 13sp（ヒント文＝検索プレースホルダ字面）
            color = LocalShelfColors.current.infoText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.S12), // .search gap 10px → S12
        )
    }
}

/**
 * 末尾「公式サイトで探す」逃げ道（K 形伝播・モック discovery-D.html .official）: ヘアラインで区切った外部リンク行。
 * なろう公式（yomou.syosetu.com）を外部ブラウザで開く導線は K の [com.novelreader.ui.skins.k] OfficialLinkK と
 * 同一＝素の ACTION_VIEW（BookshelfScreen の Blocked 送客と同流儀）。ブラウザ不在の稀ケースは
 * ActivityNotFoundException を握って無害化する（案内リンクゆえ逃げ道が塞がるより無反応の方が害が小さい）。
 */
@Composable
private fun OfficialLinkD() {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = Spacing.S24)) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = Spacing.S16), // .official margin-top 16px
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yomou.syosetu.com/")))
                    }
                }
                .padding(top = Spacing.S16, bottom = Spacing.S8), // .official padding 16px 2px 6px（横は列マージン）
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "小説家になろう公式サイトで探す",
                fontSize = FontButtonLabel, // .official 12.5px＝FontButtonLabel 12.5sp
                color = LocalShelfColors.current.infoText,
            )
            Icon(
                Icons.Filled.NorthEast, // .official ↗（外部リンク＝右上矢印）
                contentDescription = null,
                tint = LocalShelfColors.current.infoText,
                modifier = Modifier.size(15.dp), // .official svg 15px
            )
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
    Column(modifier = modifier.padding(top = Spacing.S8, start = Spacing.S24, end = Spacing.S24)) {
        Text(
            text = "きょうの気分",
            fontSize = FontMicroLabel,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val presets = MoodPattern.CLASSIC.presets // 12件へ増えた全entriesでなく従来4件の組に固定（K以外のページャ化は未裁定・2026-07-24）
        // 4プリセット固定の2列（LazyGrid をネストしない: 親が LazyColumn のため固定 Row で組む）
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.S12),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
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
            .padding(vertical = Spacing.S12),
    ) {
        // 左の青磁ルール（モック .md::before）
        Box(
            modifier = Modifier
                .padding(top = Spacing.S4)
                .width(2.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Column(modifier = Modifier.padding(start = Spacing.S12, end = Spacing.S8)) {
            Text(
                text = preset.title,
                fontFamily = MinchoFamily,
                fontSize = FontPresetTitle,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = preset.cardLabel,
                fontSize = FontPresetCaption,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.S4),
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
    Column(modifier = modifier.padding(top = Spacing.S8)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.S24),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ジャンルから",
                fontSize = FontMicroLabel,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "すべて →",
                fontSize = FontLabel,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenGenre),
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = Spacing.S12, bottom = Spacing.S8),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.S24),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
        ) {
            items(NarouGenres.BIGGENRES) { (code, label) ->
                Text(
                    text = label,
                    fontSize = FontChipLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .clickable { onPickBiggenre(code, label) }
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
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
                    fontSize = FontButtonLabel,
                    letterSpacing = 0.75.sp, // モック .tab の letter-spacing .06em（12.5px×0.06）
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    // 未選択タブラベルも意味を運ぶ文字＝infoText（AA 4.5:1）。選択時 primary は据え置き。
                    // Material 標準は onSurfaceVariant だが、ADR 0014-D（意味を運ぶ文字は 4.5:1 ＞ 美学）が
                    // 上位審級のため装飾用トークンでなく情報テキストトークンを採る。
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else LocalShelfColors.current.infoText,
                    modifier = Modifier
                        .clickable { onSelect(order) }
                        .padding(horizontal = Spacing.S12, vertical = TabVerticalPadding),
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
