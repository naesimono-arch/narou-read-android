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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontResultTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import java.util.Locale

// ============================================================
// 結果一覧＝検索・ジャンル・気分プリセットの共通着地
// （モック discovery-home-D.html フレーム2の翻訳）。
// 文脈ヘッダ（明朝見出し＋補足）＋条件チップ＋件数＋一覧行。
// ============================================================

/**
 * 結果一覧のルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取りと文脈・状態の collect だけを担い、純粋な描画は [DiscoveryResultContent] に委ねる
 * （BookshelfScreen と同じ分割方針＝chrisbanes state-holder-ui-split）。並び順・ジャンル変更・再試行・
 * 追加読み込みといった VM 操作はコールバックで下ろす。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoveryResultScreen(
    viewModel: DiscoveryViewModel,
    onUp: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
) {
    val context by viewModel.resultContext.collectAsStateWithLifecycle()
    val state by viewModel.resultState.collectAsStateWithLifecycle()

    DiscoveryResultContent(
        ctx = context,
        state = state,
        onUp = onUp,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onChangeOrder = { viewModel.changeResultOrder(it) },
        onChangeGenreFilter = { biggenres, genres -> viewModel.changeResultGenreFilter(biggenres, genres) },
        onRefresh = { viewModel.refreshResult() },
        onLoadMore = { viewModel.loadMoreResults() },
    )
}

/**
 * 結果一覧の描画層（stateless / UI 分割の content）。DiscoveryResultScreen からの純移動。
 * VM を持たず [ctx]（結果文脈）＋[state]＋コールバックだけで文脈ヘッダ・条件チップ・件数・一覧・
 * ページングフッタを描画する葉。条件チップのドロップダウン開閉といった画面ローカル UI 状態は内部に残す。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryResultContent(
    // ctx は process death 復帰中に null になり得る。復元待ちの間だけ最小ローディングを描くため nullable で受ける。
    ctx: ResultContext?,
    state: DiscoveryUiState,
    onUp: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onChangeOrder: (NarouOrder) -> Unit,
    onChangeGenreFilter: (biggenres: Set<Int>, genres: Set<Int>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // F-C: process death 復帰中は VM の init が SavedStateHandle から文脈を復元する。旧実装はここで
    // onBack して前画面へ強制退去していた（公理6/9違反＝操作なしに一覧が消え1つ前へ飛ばされる）。
    // 復元されるまでは退去せず最小のローディングを描いて待つ（NovelDetail の ncode 復元と対称）。
    if (ctx == null) {
        DiscoveryStatusBox(DiscoveryStatus.Loading, modifier = Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // フィードバック#2: 複数キーワード検索だと title（「word1 word2 …」）が1行に収まらず
                    // 見切れていた。検索語は下の条件チップ（KEYWORD）へ個別に逃がしたので、見出し自体は
                    // 1行＋末尾省略で溢れを構造的に断つ（title 文言の要約形化は SearchDraft.resultTitle()
                    // 側の担当で、当該ファイルは本タスクの編集対象外＝監督への申し送り事項）。
                    Text(
                        text = ctx.title,
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = FontResultTitle,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    // F-D（公理1）: App bar の ← は経路に依らず「発見ホーム」への固定 Up にする。
                    // 旧実装は onBack(履歴 pop)で、検索/ジャンル経由だと検索画面・ジャンル画面へ落ち Up が
                    // 経路で別階層へ割れていた。履歴 Back は端末 Back に委ね、Up は常に一段上の親へ揃える。
                    IconButton(onClick = onUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            // 用語統一（監査 ia Minor・docs/patterns/discovery-terminology.md）: この Up の着地は
                            // 発見ホーム＝画面タイトルが「見つける」の画面。旧「発見に戻る」は同じ画面を別語で
                            // 呼び表層の語がぶれていた（発見／見つける）ため、着地画面の呼称「見つける」へ揃える。
                            contentDescription = "見つける画面に戻る"
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
                    fontSize = FontLabel,
                    lineHeight = 18.sp,
                    // 結果サブタイトルは情報を運ぶ文字＝infoText（AA 4.5:1・ADR 0014-D 裁定で装飾用と分離）。
                    color = LocalShelfColors.current.infoText,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            // 条件チップ（藍の細枠・モック .cd）
            // なぜ各子に .align(CenterVertically) を付けるか: この行にはクリック可チップ
            // （外側 Box に .minimumInteractiveComponentSize＝48dp のタップ枠）と、素の静的チップ
            // （border+padding で実高 約26dp）が高さ混在する。FlowRow の cross-axis 既定は Top 揃えのため、
            // 背の低い静的チップが上端に張り付き、ピルの縦センターが行内でずれて見える（当たり判定を確保する
            // 48dp 枠は外せないので、見た目は縦センター揃えで解消する）。
            // itemVerticalAlignment パラメータは foundation 1.8+ で、BOM 2025.02.00 の 1.7.8 には無いため、
            // FlowRowScope.align を各子へ付与する方式を採る。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
            ) {
                val baseChips = conditionChipLabels(ctx.query)
                val chips = if (ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty()) {
                    // なぜ: ジャンル未指定時は、並び順チップ（baseChips の最後）の直前に「ジャンル ⌄」チップを追加し、その場変更を可能にする。
                    val mutable = baseChips.toMutableList()
                    val placeholder = ConditionChip("ジャンル", ChipKind.GENRE_PLACEHOLDER)
                    if (mutable.isNotEmpty()) {
                        mutable.add(mutable.lastIndex, placeholder)
                    } else {
                        mutable.add(placeholder)
                    }
                    mutable
                } else {
                    baseChips
                }

                chips.forEach { chip ->
                    val label = chip.label
                    // なぜ key(kind, label) か: 下の clickable チップは expanded を remember する。remember は
                    // スロット位置に紐づくため、条件変更でチップ集合が増減・並び替わると、開いていた
                    // ドロップダウンの expanded が別チップへ誤流用される。label（並び順「〜順」・ジャンル名等）で
                    // 固定するのが基本だが、KEYWORD チップ導入で検索語が既存条件と同一文言になりうる
                    // （例: word="恋愛" と GENRE「恋愛」）。label 単独キーだと重複キーで Compose がクラッシュ
                    // するため、種別も鍵に含めて衝突を防ぐ（同種内は label が一意＝トークンは重複しない）。
                    key(chip.kind, label) {
                        // なぜ ChipKind で判定するか: 以前は表示文字列一致・末尾位置でチップ種別を推測していたが、
                        // 文言や並び順を変えると静かに壊れるため、生成時に付与した種別（型）で分岐する。
                        // 大／小ジャンルのクリック可否は「1件のみ選択時」に限る従来仕様を size 条件で維持する。
                        val isOrderChip = chip.kind == ChipKind.ORDER
                        val isBiggenreChip = chip.kind == ChipKind.BIG_GENRE && ctx.query.biggenres.size == 1
                        val isGenreChip = chip.kind == ChipKind.GENRE && ctx.query.genres.size == 1
                        val isGenrePlaceholderChip = chip.kind == ChipKind.GENRE_PLACEHOLDER

                        val isGenreFilterChip = isBiggenreChip || isGenreChip || isGenrePlaceholderChip
                        val isClickable = isOrderChip || isGenreFilterChip

                        if (isClickable) {
                            var expanded by remember { mutableStateOf(false) }
                            val displayLabel = "$label ⌄"

                            // F-P: 当たり判定を 48dp へ。clickable と minimumInteractiveComponentSize を
                            // 外側 Box に移し、ピル（枠+文字）は中央寄せで見た目のサイズを保つ
                            // （タップ領域だけ不可視に広がる。DropdownMenu もこの Box を基準に開く）。
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    // 行内で高さ混在する静的チップと縦センターを揃える（上のFlowRowコメント参照）
                                    .align(Alignment.CenterVertically)
                                    .minimumInteractiveComponentSize()
                                    .clickable { expanded = true },
                            ) {
                                Text(
                                    text = displayLabel,
                                    fontSize = FontMicroLabel,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(50),
                                        )
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
                                                        fontSize = FontBody
                                                    )
                                                },
                                                onClick = {
                                                    expanded = false
                                                    onChangeOrder(order)
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
                                                    fontSize = FontBody
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                onChangeGenreFilter(emptySet(), emptySet())
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
                                                        fontSize = FontBody
                                                    )
                                                },
                                                onClick = {
                                                    expanded = false
                                                    onChangeGenreFilter(setOf(bigCode), emptySet())
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
                                                            fontSize = FontSubTitle
                                                        )
                                                    },
                                                    onClick = {
                                                        expanded = false
                                                        onChangeGenreFilter(emptySet(), setOf(genreCode))
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
                                fontSize = FontMicroLabel,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier
                                    // 48dpタップ枠のクリック可チップと縦センターを揃える（上のFlowRowコメント参照）
                                    .align(Alignment.CenterVertically)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                if (ctx.source == ResultSource.SEARCH) {
                    // why: 「条件を変更」で戻る先はDiscoverySearchScreen(検索画面)。
                    // ジャンル・気分等で出すと戻り先に条件シートがなく騙し導線になるため、SEARCH のみに限定する。
                    // F-P: 当たり判定を 48dp へ（clickable を外側 Box に移し文言サイズは維持）。
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            // 静的チップと縦センターを揃える（上のFlowRowコメント参照）
                            .align(Alignment.CenterVertically)
                            .minimumInteractiveComponentSize()
                            .clickable { onBack() },
                    ) {
                        Text(
                            text = "条件を変更",
                            fontSize = FontMicroLabel,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is DiscoveryUiState.Loading ->
                        DiscoveryStatusBox(DiscoveryStatus.Loading, modifier = Modifier.fillMaxSize())
                    is DiscoveryUiState.Empty -> ResultEmpty(
                        source = ctx.source,
                        onAdjust = onBack,          // SEARCH: 条件シート（検索画面）へ戻す
                        onBackToDiscovery = onUp,   // GENRE/MOOD/KEYWORD: 戻り先に条件シートが無いので発見ホームへ
                        modifier = Modifier.fillMaxSize(),
                    )
                    is DiscoveryUiState.Error -> DiscoveryStatusBox(
                        DiscoveryStatus.Error(s.message, onRetry = onRefresh),
                        modifier = Modifier.fillMaxSize(),
                    )
                    is DiscoveryUiState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                        ) {
                            item {
                                // 総件数（モック .cnt・青磁）
                                // F-J（公理8）: allcount(総数)だけ大表示すると「全件見られる」と誤認させるため、
                                // 現在表示している shown 件を「N件中 上位M件を表示」で明示する。フルページング実装後は
                                // フッタ「さらに読み込む」で shown が総数へ近づき、全件到達で「N作品」表記へ自然に切り替わる
                                // （API 取得上限で全件に届かない場合はフッタが取得上限を明示する）。
                                val shown = s.novels.size
                                val allcountText = String.format(Locale.JAPAN, "%,d", s.allcount)
                                val countText = if (s.allcount > shown) {
                                    "$allcountText 件中 上位 $shown 件を表示"
                                } else {
                                    "$allcountText 作品"
                                }
                                Text(
                                    text = countText,
                                    fontSize = FontLabel,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(
                                s.novels,
                                // なぜ ncode をキーにするか: 並び順・ジャンル変更や再取得でリスト内容が入れ替わっても
                                // 各行の識別を安定させ、状態・アニメの誤流用を防ぐ（本棚 items(key = { it.id }) と同方針）。
                                // ncode はモデル上 null 許容だが発見結果には常に存在する。防御的に欠損時のみ index
                                // へ退避する（型が違うため ncode 文字列と index の衝突は起きない）。
                                key = { index, novel -> novel.ncode ?: index },
                            ) { index, novel ->
                                NovelListRow(
                                    rank = index + 1,
                                    novel = novel,
                                    order = ctx.query.order,
                                    // 境界: novel.ncode は Moshi 由来の String。詳細遷移の引数は型付き Ncode へ包む。
                                    onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            // F-J: フルページングのフッタ（追加読み込み／読み込み中／失敗再試行／取得上限）。
                            item {
                                PagingFooter(
                                    paging = s.paging,
                                    onLoadMore = onLoadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * フルページング（F-J）の一覧末尾フッタ。paging 状態に応じて出し分ける。
 * Complete のときは何も描かない（全件表示済み＝件数表示が「N作品」に切り替わっているため冗長）。
 * ApiLimitReached は「全件は見せられない」ことを正直に明示する（なろうAPIの st 上限で到達不能）。
 */
@Composable
private fun PagingFooter(
    paging: PagingState,
    onLoadMore: () -> Unit,
) {
    when (paging) {
        PagingState.Idle -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedButton(onClick = onLoadMore) { Text("さらに読み込む") }
        }
        PagingState.LoadingMore -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        is PagingState.LoadMoreError -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = paging.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedButton(onClick = onLoadMore) { Text("再試行") }
        }
        PagingState.ApiLimitReached -> Text(
            text = "これ以上は取得できません（APIの取得上限に達しました）",
            fontSize = FontLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // 全件表示済み＝フッタなし。
        PagingState.Complete -> Unit
    }
}

/**
 * 結果0件の「次の一手」つき空状態（M7）。DiscoveryStatusBox.Empty はメッセージのみで CTA が無いため、
 * 結果画面固有の到達導線をここで添える。検索発（SEARCH）は条件シートへ戻すのが最短の是正、
 * ジャンル/気分/キーワード発は戻り先に条件シートが無いため発見ホームへ導く。
 * 意匠は Error 状態（中央 Column＋メッセージ＋ボタン）に揃え、色はトークン経由（発明しない）。
 */
@Composable
private fun ResultEmpty(
    source: ResultSource,
    onAdjust: () -> Unit,
    onBackToDiscovery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSearch = source == ResultSource.SEARCH
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "条件に合う作品が見つかりませんでした",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedButton(onClick = if (isSearch) onAdjust else onBackToDiscovery) {
                Text(if (isSearch) "検索条件を変える" else "ほかの条件で探す")
            }
        }
    }
}
