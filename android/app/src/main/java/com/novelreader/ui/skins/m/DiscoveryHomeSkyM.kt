package com.novelreader.ui.skins.m

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.discovery.ChipKind
import com.novelreader.ui.discovery.ConditionChip
import com.novelreader.ui.discovery.conditionChipLabels
import com.novelreader.ui.discovery.novelStatusLabel
import com.novelreader.ui.discovery.rememberOrderMetricLabel
import com.novelreader.ui.discovery.readTimeLabel
import com.novelreader.ui.theme.AuthorInkSeizu
import com.novelreader.ui.theme.BrightStarSeizu
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.GenreChipInkSeizu
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.RubySeizu
import com.novelreader.ui.theme.SkyGradMidSeizu
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import java.util.Locale

// ============================================================
// スキンM「星図」の発見＝夜空の観測（正本 discovery-M-rich-R1.html＝R1s 深空の型の「沈め版」・ADR 0022 §1 の構造分岐先）。
//   ・発見ホーム DiscoveryHomeSkyM＝見出し「見つける」＋気分プリセット（夜空を覗く窓）＋ジャンル入口＋
//     期間タブ＋観測ランキング（星表）。地の夜空は R1s 深空（超微星の海→天の川の粒帯→散開微星→ネビュラ→アクセント星）。
//   ・結果一覧 DiscoveryResultSkyM＝プリセット/ジャンル/検索の共通着地（back・文脈見出し・条件チップ・件数・星表）。
//
// 背景・Lcg は星図スキン共通の SkyCanvas.kt を参照。R1s 深空の共通部品（帯疎化 fila/darkNeb/riftCenter/bDens・
// 色温度 starTempColor/starColorAt/hash01・粒クラス）は DeepSkyM から internal 流用し二重実装を避ける。発見は
// drawNightSky（夜天3層）の直上へ発見固有の深空 drawDiscoveryDeepSky を静止1回敷く（parallax/流星なし＝モック無モーション）。
// 本棚 R1s との差＝カード/chip/ランキングが主役ゆえ一段沈める（粒α上限0.30・ネビュラ核≤.09・pip≤.28・スパイク3本）・
// 画面固有 seed 0x2A17F3D・帯の走向を反転（本棚=右上→左下／発見=左上→右下）・銀河核 y=540（本棚 y=300 と別空域）。
//
// モーション: discovery-M.html は canvas を1回だけ描く静止画（keyframes/rAF ゼロ）＝ADR 0022 §3 の
//   「P/J はモックにモーションが存在しない＝静止で実装開始」と同じ扱いで M 発見もモーションゼロ。
//   本棚/目次の脈動は「現在地の脈動」がある画面固有の承認であり、発見にはその対象が無い＝reduce 分岐も不要。
//
// 機能全数（ADR 0022＝D 経路と欠落なく結線）: 検索導線・気分プリセット4・ジャンルチップ（NarouGenres 駆動）・
//   ジャンル一覧入口「すべて」・期間タブ6・ランキング行タップ・Loading/Empty/Error・戻る。
//   モックが省いた D 機能（ホームの戻る＝本棚へ／ジャンル一覧入口「すべて」／状態分岐／結果の条件ドロップダウン・
//   ページング・0件 CTA）は欠落させず M 意匠へ写す（各所にコメント）。
//
// 字面: 構造画面専用の px 値は正本モックの font-size を 1:1 で sp へ写す（各行にモック由来コメント）。
// ============================================================

// ---- 描画層の透過色（グラデ地の上へ層で載るため焼き込めず .copy(alpha=) で正本 α を付与）----
// 枠強化: 深化した深空の地でカード枠/chip境界/タブ下線/条件調整chip の分離を確保するため、モック R1 は --line を
// rgba(150,168,214,.20) → rgba(154,172,218,.5) へ強化（約3:1・素地 #0D1636 に対し ~2.9:1）。この val は元から発見
// ファイル内 private＝発見スコープ限定（本棚/目次の MoonSlateSeizu 系境界は不変）。rgb 154,172,218 は基色 150,168,214
// との Δ4＝知覚下微差ゆえ MoonSlateSeizu へ吸収し（ADR 0022 §4 の色正規化）、変えるのは α のみ（.20→.5）。
private val LineAlpha = MoonSlateSeizu.copy(alpha = 0.5f)        // --line rgba(154,172,218,.5)＝枠強化（発見スコープ）
private val MoodWindowBg = Color(0xFF0E1634).copy(alpha = 0.28f)  // .md 背景 rgba(14,22,52,.28)＝夜空を覗く窓
private val CdStarBorder = StarSeizu.copy(alpha = 0.4f)          // .cd border rgba(233,221,180,.4)
private val CdStarInk = StarSeizu.copy(alpha = 0.9f)            // .cd（opacity .9＝星光文字を僅かに沈める）
private val WindowStar = StarSeizu.copy(alpha = 0.5f)          // .md .win 結線 rgba(233,221,180,.5)

// 発見の可読スクリム（空の一枚化・2026-07-19）。常駐 backdrop（SkyBackdropM）は本棚R1s の満輝度（0.42）ゆえ、
// カード/chip/ランキングが主役の発見では空を一段沈める。旧・発見沈め版深空（SkyDiscR CAP=0.30）の「沈め」を、
// 地色 #0D1636（SkyGradMidSeizu）の α 掛け全面スクリムで再現する（直書き禁止＝トークン経由・α は体感同等で実機後詰め）。
private val DiscoverySkyScrim = SkyGradMidSeizu.copy(alpha = 0.30f)  // 全面を一段沈める（旧 CAP0.30 相当）

// K 形伝播の実検索フィールド地（モック discovery-M.html --field rgba(14,22,52,.55)＝夜天と同系の透過。濃色ダミー板
// を避ける狙い）。base 0x0E1634 は MoodWindowBg と同一 RGB（夜空を覗く窓と同系）で、α のみ .55（--field 指定値）。
private val SearchFieldBgSeizu = Color(0xFF0E1634).copy(alpha = 0.55f)

// ============================================================
// 発見ホーム（モック左フレーム）
// ============================================================
@Composable
internal fun DiscoveryHomeSkyM(
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
    // 空そのもの（夜天・深空・天の川粒帯）は常駐 backdrop（SkyBackdropM）が本棚R1s の形で敷く（空の一枚化・2026-07-19）。
    // 本画面は可読の全面スクリムを被せ、スクロール差分を backdrop の視差へ流すだけ（旧・発見固有の沈め版深空は撤去）。
    val skyParallax = LocalSkyParallax.current
    val parallaxNestedScroll = remember(skyParallax) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                skyParallax?.onScrollDelta(consumed.y)
                return Offset.Zero
            }
        }
    }
    // 期間タブ切替のスクロール位置リセット対策（実機報告 2026-07-19・キャッシュ無し時）。
    // 真因: loadHome() が再取得のたびに一旦 Loading を挟むため、当画面の単一 LazyColumn では Content の
    // ランキング行が Loading 中に status 行1件へ全置換され、総コンテンツ高が見出し＋1行まで縮む。すると
    // LazyListState は firstVisibleItem/offset を維持できず（縮んだ内容ではその位置に留まれない）先頭側へ
    // クランプされ、Content 復帰後もトップ付近のまま＝強制リセットに見える。VM は再取得中に旧一覧を保持
    // しない設計ゆえ、UI 側で「直近に描けた Content」を控え、再取得(Loading)中はそのランキング骨格を出し
    // 続けて一覧の identity（items key=ncode）とスクロールアンカーを保つ（stale-while-revalidate）。VM 非改変で
    // 発見M スコープ限定（他スキン／結果画面の同型は横断調査で別途報告）。
    var lastContent by remember { mutableStateOf<DiscoveryUiState.Content?>(null) }
    // 合成中の書き戻しを避け、Content を側効果で控える（次フレーム反映＝Content 分岐は s を直接描くため無遅延）。
    LaunchedEffect(state) { (state as? DiscoveryUiState.Content)?.let { lastContent = it } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(DiscoverySkyScrim) }, // backdrop の空を一段沈める（カード/chip の可読）
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // .top 上段: 見出し＋戻る（← 本棚へ＝モック省略の D 機能を M 意匠で欠落なく写した先頭導線）。
            // 検索はトップバーのアイコン1個から下の実検索フィールドへ分離・格上げした（K 形伝播）。
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S8, bottom = Spacing.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = DimSeizu)
                }
                Text(
                    "見つける",
                    fontFamily = MinchoFamily,
                    fontSize = 26.sp,            // .top h1 26px
                    letterSpacing = 0.16.em,
                    fontWeight = FontWeight.Medium,
                    color = TextSeizu,
                    modifier = Modifier.weight(1f).padding(start = Spacing.S8),
                )
            }

            // 固定トップの実検索フィールド（K 形伝播・モック .search）＝検索第一・常時可視。導線は onOpenSearch を再利用。
            SearchFieldSky(onOpenSearch)

            LazyColumn(
                // スクロール差分を backdrop の視差へ流す（本棚面と同じ onPostScroll consumed.y＝画面遷移で連続）。
                modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(parallaxNestedScroll),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S40, // .scroll padding 0 26 40
                ),
            ) {
                item { SkySectionLabel("きょうの気分", topSpace = Spacing.S16) } // 先頭 .sec margin-top 18px
                item { MoodGridSky(onPickMood) }
                item { SkySectionLabel("ジャンルから", topSpace = Spacing.S32) } // .sec margin 28px
                item { GenreChipsSky(onOpenGenre, onPickBiggenre) }
                item { OrderTabsSky(order, onSelectOrder) }

                // 再取得(Loading)中は直近 Content の骨格を出し続けてスクロール位置を保つ（初回ロードは骨格
                // 未確定＝status 行）。Empty/Error は一覧を畳んで良い（真に0件・失敗のためトップ表示が妥当）。
                val rowsContent = when (val s = state) {
                    is DiscoveryUiState.Content -> s
                    is DiscoveryUiState.Loading -> lastContent
                    else -> null
                }
                when {
                    rowsContent != null -> itemsIndexed(
                        rowsContent.novels,
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        SkyRankRow(
                            rank = index + 1,
                            novel = novel,
                            order = order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                    state is DiscoveryUiState.Loading -> item { SkyStatusLine("観測しています…") }
                    state is DiscoveryUiState.Empty -> item { SkyStatusLine("作品が見つかりませんでした") }
                    state is DiscoveryUiState.Error ->
                        item { SkyErrorLine(state.message, onRefresh) }
                }

                // 末尾: 公式サイトで探す逃げ道（K 形伝播・モック .official）。K の OfficialLinkK と同一導線を配線。
                item { OfficialLinkSky() }
            }
        }
    }
}

// ============================================================
// 結果一覧（モック右フレーム＝プリセット/ジャンル/検索の共通着地）
// ============================================================
@Composable
internal fun DiscoveryResultSkyM(
    ctx: ResultContext?,
    state: DiscoveryUiState,
    onUp: () -> Unit,
    // 「条件を変更」導線専用（検索画面へ）。Back/← は onUp（階層 up）で一本（2026-07-29 統一＝ADR 0026）。
    onEditConditions: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onChangeOrder: (NarouOrder) -> Unit,
    onChangeGenreFilter: (biggenres: Set<Int>, genres: Set<Int>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 空そのものは常駐 backdrop（SkyBackdropM）が敷く＝発見ホームと同じ可読スクリムを被せる（空の一枚化・2026-07-19）。
    val skyParallax = LocalSkyParallax.current
    val parallaxNestedScroll = remember(skyParallax) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                skyParallax?.onScrollDelta(consumed.y)
                return Offset.Zero
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(DiscoverySkyScrim) },
    ) {
        // process death 復帰中は文脈 null＝D 実装と同じく退去せず最小ローディングで待つ（DiscoveryResultContent と対称）。
        if (ctx == null) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                SkyStatusLine("観測しています…")
            }
            return@Box
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // .back（‹ 見つける）＝App bar の ← は経路に依らず発見ホームへ固定 Up（D の onUp と同型）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUp)
                    .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4, bottom = Spacing.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            ) {
                Text("‹", fontSize = 15.sp, color = DimSeizu)
                Text("見つける", fontSize = 12.sp, color = DimSeizu) // .back 12px
            }

            // .ctx: 明朝見出し＋補足。見出しは1行＋末尾省略（D と同じ溢れ対策）。
            Column(modifier = Modifier.fillMaxWidth().padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S16)) {
                Text(
                    ctx.title,
                    fontFamily = MinchoFamily,
                    fontSize = 22.sp,           // .ctx h2 22px
                    letterSpacing = 0.08.em,
                    fontWeight = FontWeight.Medium,
                    color = TextSeizu,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ctx.subtitle?.let {
                    Text(
                        it,
                        fontSize = 11.sp,       // .ctx .sub 11px
                        lineHeight = 18.sp,
                        color = DimSeizu,
                        modifier = Modifier.padding(top = Spacing.S12),
                    )
                }
            }

            // .conds: 条件チップ（星光枠 .cd）＋調整可チップ（.cd.adj）。D の条件ドロップダウン一式を M 意匠へ写す。
            ResultCondsSky(ctx, onChangeOrder, onChangeGenreFilter, onEditConditions)

            when (val s = state) {
                is DiscoveryUiState.Loading -> SkyStatusLine("観測しています…")
                is DiscoveryUiState.Empty -> ResultEmptySky(ctx.source, onAdjust = onEditConditions, onBackToDiscovery = onUp)
                is DiscoveryUiState.Error -> SkyErrorLine(s.message, onRefresh)
                is DiscoveryUiState.Content -> LazyColumn(
                    // スクロール差分を backdrop の視差へ流す（onPostScroll consumed.y＝画面遷移で連続）。
                    modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(parallaxNestedScroll),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.S40),
                ) {
                    item {
                        // .cnt 件数（総数だけ大表示せず shown 件を明示＝D と同じ F-J 表記）。
                        val shown = s.novels.size
                        val allcountText = String.format(Locale.JAPAN, "%,d", s.allcount)
                        val countText = if (s.allcount > shown) "$allcountText 件中 上位 $shown 件を表示" else "$allcountText 作品"
                        Text(
                            countText,
                            fontSize = 11.sp,       // .cnt 11px
                            letterSpacing = 0.08.em,
                            color = DimSeizu,
                            modifier = Modifier.padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S16, bottom = Spacing.S8),
                        )
                    }
                    itemsIndexed(
                        s.novels,
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        SkyRankRow(
                            rank = index + 1,
                            novel = novel,
                            order = ctx.query.order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                    item { SkyPagingFooter(s.paging, onLoadMore) }
                }
            }
        }
    }
}

// ============================================================
// 固定トップの実検索フィールド（K 形伝播・モック .search）＋末尾の公式サイト逃げ道（.official）。
//   検索フィールドのタップ先＝onOpenSearch、公式サイト起動＝なろう公式 ACTION_VIEW。いずれも K 実装
//   （DiscoveryHomeK の SearchHeaderK / OfficialLinkK）と同一導線を再利用する（新規機能は発明しない）。
// ============================================================
@Composable
private fun SearchFieldSky(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            // .search（margin-top 14px）。見出しは上段 Row が担うため横 S24＋上下の呼吸のみ。
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4, bottom = Spacing.S12)
            .fillMaxWidth()
            .height(52.dp)                    // .search 52px（構造値）
            .clip(RoundedCornerShape(14.dp))  // .search border-radius 14px
            .background(SearchFieldBgSeizu)    // --field rgba(14,22,52,.55)
            .border(1.dp, LineAlpha, RoundedCornerShape(14.dp)) // --line
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = Spacing.S16), // .search padding 0 16px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,        // 隣接プレースホルダ文が読み上げを担う
            tint = DimSeizu,                  // .search svg stroke --dim
            modifier = Modifier.size(20.dp),  // .search svg 20px
        )
        Text(
            "作品名・作者名・キーワードで探す",
            fontSize = 14.sp,                 // .search span 14px
            color = DimSeizu,                 // --dim
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.S12), // .search gap 11px → S12
        )
    }
}

@Composable
private fun OfficialLinkSky() {
    val context = LocalContext.current
    Column {
        // border-top 1px var(--line)（.official）＝月光スレートの区切り。
        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.S12).height(1.dp).background(LineAlpha))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yomou.syosetu.com/")))
                    }
                }
                .padding(top = Spacing.S16, bottom = Spacing.S4), // .official padding 18px 2px 4px
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "小説家になろう公式サイトで探す",
                fontSize = 13.sp, // .official 13px
                color = DimSeizu, // --dim
            )
            Icon(
                Icons.Filled.NorthEast, // .official ↗（外部リンク＝右上矢印）
                contentDescription = null,
                tint = DimSeizu,
                modifier = Modifier.size(15.dp), // .official svg 15px
            )
        }
    }
}

// ============================================================
// 節見出し（モック .sec: 字間の広い月光スレートの小見出し）
// ============================================================
@Composable
private fun SkySectionLabel(text: String, topSpace: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        fontSize = 10.5.sp,     // .sec 10.5px
        letterSpacing = 0.28.em,
        color = DimSeizu,
        modifier = Modifier.padding(top = topSpace, bottom = Spacing.S16),
    )
}

// ============================================================
// 気分プリセット＝夜空を覗く窓（モック .mood: 2列・小星座片つき）
// ============================================================
@Composable
private fun MoodGridSky(onPickMood: (MoodPreset) -> Unit) {
    val presets = MoodPattern.CLASSIC.presets // 12件へ増えた全entriesでなく従来4件の組に固定（K以外のページャ化は未裁定・2026-07-24）
    Column {
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S16), // .mood gap 14px
                horizontalArrangement = Arrangement.spacedBy(Spacing.S16),
            ) {
                rowPresets.forEach { preset ->
                    MoodCardSky(preset, onClick = { onPickMood(preset) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoodCardSky(preset: MoodPreset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // モック .md は「星座片＝右上に絶対配置(top:12/right:12・position:absolute で flow 外)」「本文＝justify-content:
    // flex-end で下寄せ」の縦分離設計。旧実装は Box の align(TopEnd) を Box 全体の padding(16) 内に置き、星座片が
    // 本文と同一座標系で右上/左下に重なる構図だった。真因: この構図はカード幅に依存し、幅が縮む狭幅端末では
    // 単行タイトル「30分の小さな旅」の右端が星座片の x 帯へ到達して重なる（モック 390px 幅では 2px 差で辛うじて
    // 回避＝可搬でない前提）。対処: 星座片を上段の独立バンド（右寄せ）へ置き、weight スペーサで本文を下段へ
    // 押し下げて縦方向で分離する（flex-end の意図を保ちつつ全幅・全フォント倍率で重なりを排除）。バンド確保で
    // カード高は約 91dp となり min 82dp を上回るが、82dp 固定は狭幅で重なりを招く脆い前提ゆえ可搬性を優先する。
    Column(
        modifier = modifier
            .heightIn(min = 82.dp)      // .md min-height 82px（呼吸ぶんの下限）
            .clip(RoundedCornerShape(12.dp))
            .background(MoodWindowBg)
            .border(1.dp, LineAlpha, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S12, bottom = Spacing.S16), // .md padding 18/16/16＋.win top:12
    ) {
        // 小星座片（.md .win）＝上段右寄せの独立バンド。プリセット序数ごとに正本の3点配置を写す。
        MoodWindow(preset.ordinal, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.weight(1f)) // .md justify-content:flex-end（本文を下段へ押し下げる）
        Text(
            preset.title,
            fontFamily = MinchoFamily,
            fontSize = 14.sp,       // .md b 14px
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSeizu,
        )
        Text(
            preset.cardLabel,
            fontSize = 10.5.sp,     // .md span 10.5px
            letterSpacing = 0.03.em,
            color = DimSeizu,
            modifier = Modifier.padding(top = Spacing.S8), // margin-top 7px → S8
        )
    }
}

/** 夜空を覗く窓の小星座片（モック .md .win の SVG＝3点2線）。序数で正本の座標セットを引く（viewBox 34×20）。 */
@Composable
private fun MoodWindow(index: Int, modifier: Modifier = Modifier) {
    // discovery-M.html の4枚の .win svg 座標（[x,y] ×3・中央点だけ r1.8・両端 r1.3）。
    val pts = when (index) {
        0 -> floatArrayOf(4f, 14f, 14f, 6f, 28f, 10f)
        1 -> floatArrayOf(5f, 6f, 16f, 14f, 29f, 7f)
        2 -> floatArrayOf(4f, 10f, 17f, 5f, 30f, 13f)
        else -> floatArrayOf(5f, 13f, 15f, 7f, 29f, 9f)
    }
    Canvas(modifier = modifier.size(width = 34.dp, height = 20.dp)) {
        val sx = size.width / 34f
        val sy = size.height / 20f
        fun p(i: Int) = Offset(pts[i * 2] * sx, pts[i * 2 + 1] * sy)
        val path = Path().apply {
            moveTo(p(0).x, p(0).y); lineTo(p(1).x, p(1).y); lineTo(p(2).x, p(2).y)
        }
        drawPath(path, WindowStar, style = Stroke(width = 1f))
        drawCircle(StarSeizu, radius = 1.3f * sx, center = p(0))
        drawCircle(StarSeizu, radius = 1.8f * sx, center = p(1))
        drawCircle(StarSeizu, radius = 1.3f * sx, center = p(2))
    }
}

// ============================================================
// ジャンル入口チップ（モック .gchips: 横スクロール）。NarouGenres 駆動＝D 発見と同じデータ源。
// 末尾に「すべて」＝ジャンル一覧画面入口（モック省略の D 機能を欠落させず写す）。
// ============================================================
@Composable
private fun GenreChipsSky(onOpenGenre: () -> Unit, onPickBiggenre: (code: Int, label: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = Spacing.S4), // .gchips padding-bottom 4px
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // gap 9px → S8
    ) {
        NarouGenres.BIGGENRES.forEach { (code, label) ->
            GenreChip(label, onClick = { onPickBiggenre(code, label) })
        }
        // ジャンル一覧入口（D の「すべて →」に相当。星図では夜天の余韻を保つ月光スレートのチップ）。
        GenreChip("すべて", onClick = onOpenGenre)
    }
}

@Composable
private fun GenreChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp,     // .gc 11.5px
        color = GenreChipInkSeizu,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, LineAlpha, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .gc padding 9px 17px
    )
}

// ============================================================
// 期間タブ（モック .tabs: 横スクロール・選択= --star 文字＋下線）。NarouOrder 駆動＝D と同じ並び。
// ============================================================
@Composable
private fun OrderTabsSky(selected: NarouOrder, onSelect: (NarouOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .drawBehind {
                // border-bottom 1px var(--line)（選択下線はその上に重なる）。
                drawLine(LineAlpha, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            }
            .padding(top = Spacing.S32), // .tabs margin-top 28px → S32
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // .tabs gap 6px → S8
    ) {
        NarouOrder.entries.forEach { o ->
            val isOn = o == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(o) },
            ) {
                Text(
                    o.uiLabel,
                    fontSize = 12.5.sp,     // .tab 12.5px
                    letterSpacing = 0.06.em,
                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isOn) StarSeizu else DimSeizu,
                    modifier = Modifier.padding(horizontal = Spacing.S12, vertical = Spacing.S12), // .tab padding 12px 12px
                )
                // .tab.on::after（左右 10px を空けた 2px の星光下線）。未選択は透明で高さを揃える。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.S8)
                        .height(2.dp)
                        .background(if (isOn) StarSeizu else Color.Transparent),
                )
            }
        }
    }
}

// ============================================================
// 観測ランキング行＝星表（モック .rk）。ホーム・結果一覧の共通部品。
//   順位 .no＝明朝19sp・上位3位は星光＋上部の一星。pt は星光・その他メタは月光スレート。
// ============================================================
@Composable
internal fun SkyRankRow(
    rank: Int,
    novel: WorkSummary,
    order: NarouOrder,
    onClick: () -> Unit,
) {
    val isTop3 = rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S4, vertical = Spacing.S24), // .rk padding 22px 2px
        horizontalArrangement = Arrangement.spacedBy(Spacing.S16),      // .rk gap 16px
        verticalAlignment = Alignment.Top,
    ) {
        // .no（星等数字）。上位3位は数字の上に一星（::after top -9px を上方スタックで写す）。
        Box(modifier = Modifier.width(26.dp)) { // .rk .no width 26px
            if (isTop3) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-9).dp)   // ::after top:-9px
                        .size(5.dp)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    0f to StarSeizu.copy(alpha = 0.85f), // box-shadow rgba(233,221,180,.85)
                                    1f to StarSeizu.copy(alpha = 0f),
                                    radius = size.maxDimension,
                                ),
                                radius = size.maxDimension,
                            )
                            drawCircle(BrightStarSeizu) // #F5F1DE の星芯
                        },
                )
            }
            Text(
                rank.toString(),
                fontFamily = MinchoFamily,
                fontSize = 19.sp,           // .rk .no 19px
                color = if (isTop3) StarSeizu else DimSeizu,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = Spacing.S4), // padding-top 2px
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                fontFamily = MinchoFamily,
                fontSize = 14.5.sp,         // .rk .t 14.5px
                lineHeight = 22.sp,
                color = TextSeizu,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.S8), // .rk .a margin-top 6px
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    novel.author,
                    fontSize = 11.sp,       // .rk .a 11px
                    color = AuthorInkSeizu,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NarouGenres.genreLabel(novel.genreCode)?.let { genre ->
                    Text(
                        genre,
                        fontSize = 11.sp,   // .rk .a em（ジャンル）
                        letterSpacing = 0.06.em,
                        color = DimSeizu,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = Spacing.S8), // em margin-left 8px
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = Spacing.S12), // .rk .m margin-top 10px
                horizontalArrangement = Arrangement.spacedBy(Spacing.S16), // .m gap 14px
            ) {
                Text(novelStatusLabel(novel), fontSize = 10.5.sp, color = RubySeizu) // .rk .m 10.5px #9AA4C0
                readTimeLabel(novel)?.let { Text(it, fontSize = 10.5.sp, color = RubySeizu) }
                // 並び順を決めた指標をひとつだけ（pt 系の期間は pt・新着は更新日時）。D 共通の
                // NovelListRow と同じ関数を通す＝スキン間で「順位の根拠」の規則を割らない。
                rememberOrderMetricLabel(order, novel)?.let {
                    Text(it, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = StarSeizu) // .pt var(--star)
                }
            }
        }
    }
}

// ============================================================
// 結果一覧の条件チップ（モック .conds）。D の条件ドロップダウン一式を M 意匠へ写す（機能欠落禁止）。
//   静的条件＝.cd（星光枠）。並び順/ジャンルの変更＝.cd.adj（月光スレート枠・クリックでドロップダウン）。
//   検索発は「条件を変更」（検索画面へ戻す）を D と同じく SEARCH のみに限定。
// ============================================================
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ResultCondsSky(
    ctx: ResultContext,
    onChangeOrder: (NarouOrder) -> Unit,
    onChangeGenreFilter: (biggenres: Set<Int>, genres: Set<Int>) -> Unit,
    onEditConditions: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S16, bottom = Spacing.S4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
        verticalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        val baseChips = conditionChipLabels(ctx.query)
        // ジャンル未指定時は並び順チップの直前に「ジャンル」プレースホルダを差し込む（D と同じ即時変更導線）。
        val chips = if (ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty()) {
            baseChips.toMutableList().apply {
                val placeholder = ConditionChip("ジャンル", ChipKind.GENRE_PLACEHOLDER)
                if (isNotEmpty()) add(lastIndex, placeholder) else add(placeholder)
            }
        } else baseChips

        chips.forEach { chip ->
            val isOrderChip = chip.kind == ChipKind.ORDER
            val isBiggenreChip = chip.kind == ChipKind.BIG_GENRE && ctx.query.biggenres.size == 1
            val isGenreChip = chip.kind == ChipKind.GENRE && ctx.query.genres.size == 1
            val isGenrePlaceholderChip = chip.kind == ChipKind.GENRE_PLACEHOLDER
            val isGenreFilterChip = isBiggenreChip || isGenreChip || isGenrePlaceholderChip

            if (isOrderChip || isGenreFilterChip) {
                var expanded by remember(chip.kind, chip.label) { mutableStateOf(false) }
                Box {
                    CondChip("${chip.label} ⌄", adjustable = true, onClick = { expanded = true })
                    if (isOrderChip) {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            NarouOrder.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.uiLabel, fontWeight = if (ctx.query.order == o) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { expanded = false; onChangeOrder(o) },
                                )
                            }
                        }
                    } else {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("すべてのジャンル", fontWeight = if (ctx.query.biggenres.isEmpty() && ctx.query.genres.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { expanded = false; onChangeGenreFilter(emptySet(), emptySet()) },
                            )
                            NarouGenres.BIGGENRES.forEach { (bigCode, bigName) ->
                                val isCurrentBig = ctx.query.biggenres.size == 1 && ctx.query.biggenres.first() == bigCode
                                DropdownMenuItem(
                                    text = { Text(bigName, fontWeight = if (isCurrentBig) FontWeight.Bold else FontWeight.SemiBold) },
                                    onClick = { expanded = false; onChangeGenreFilter(setOf(bigCode), emptySet()) },
                                )
                                NarouGenres.GENRES_BY_BIG[bigCode]?.forEach { (genreCode, genreName) ->
                                    val isCurrentGenre = ctx.query.genres.size == 1 && ctx.query.genres.first() == genreCode
                                    DropdownMenuItem(
                                        text = { Text(genreName, modifier = Modifier.padding(start = Spacing.S16), fontWeight = if (isCurrentGenre) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { expanded = false; onChangeGenreFilter(emptySet(), setOf(genreCode)) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                CondChip(chip.label, adjustable = false, onClick = null)
            }
        }

        if (ctx.source == ResultSource.SEARCH) {
            // 「条件を変更」で戻る先は検索画面＝SEARCH のみ（他発だと戻り先に条件シートが無く騙し導線・D と同判定）。
            CondChip("条件を変更", adjustable = true, onClick = onEditConditions)
        }
    }
}

@Composable
private fun CondChip(label: String, adjustable: Boolean, onClick: (() -> Unit)?) {
    Text(
        label,
        fontSize = 10.5.sp,     // .cd 10.5px
        color = if (adjustable) DimSeizu else CdStarInk,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (adjustable) LineAlpha else CdStarBorder, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.S12, vertical = Spacing.S4), // .cd padding 5px 13px
    )
}

// ============================================================
// 状態分岐の M 意匠（モック未定義＝最小の一文。星図の夜天は既に地に描かれている）
// ============================================================
@Composable
private fun SkyStatusLine(text: String) {
    Text(
        text,
        fontFamily = MinchoFamily,
        fontSize = 14.sp,
        color = DimSeizu,
        modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S40),
    )
}

@Composable
private fun SkyErrorLine(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S24)) {
        Text(message, fontFamily = MinchoFamily, fontSize = 14.sp, color = DimSeizu)
        Text(
            "再試行",
            fontFamily = MinchoFamily,
            fontSize = 14.sp,
            color = StarSeizu,
            modifier = Modifier.clickable(onClick = onRetry).padding(top = Spacing.S8),
        )
    }
}

/** 結果0件の「次の一手」（D の ResultEmpty を M 意匠へ写す。検索発は条件シートへ・他発は発見ホームへ）。 */
@Composable
private fun ResultEmptySky(source: ResultSource, onAdjust: () -> Unit, onBackToDiscovery: () -> Unit) {
    val isSearch = source == ResultSource.SEARCH
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S24)) {
        Text("条件に合う作品が見つかりませんでした", fontFamily = MinchoFamily, fontSize = 14.sp, color = DimSeizu)
        Text(
            if (isSearch) "検索条件を変える" else "ほかの条件で探す",
            fontFamily = MinchoFamily,
            fontSize = 14.sp,
            color = StarSeizu,
            modifier = Modifier.clickable(onClick = if (isSearch) onAdjust else onBackToDiscovery).padding(top = Spacing.S8),
        )
    }
}

/** ページングフッタ（D の PagingFooter を M 意匠へ写す。Complete は描かない）。 */
@Composable
private fun SkyPagingFooter(paging: PagingState, onLoadMore: () -> Unit) {
    when (paging) {
        PagingState.Idle -> SkyFooterAction("さらに読み込む", onLoadMore)
        PagingState.LoadingMore -> SkyFooterText("読み込んでいます…")
        is PagingState.LoadMoreError -> Column {
            SkyFooterText(paging.message)
            SkyFooterAction("再試行", onLoadMore)
        }
        PagingState.ApiLimitReached -> SkyFooterText("これ以上は取得できません（APIの取得上限に達しました）")
        PagingState.Complete -> Unit
    }
}

@Composable
private fun SkyFooterAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = MinchoFamily,
        fontSize = 12.sp,
        color = StarSeizu,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.S24, vertical = Spacing.S16),
    )
}

@Composable
private fun SkyFooterText(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = DimSeizu,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S16),
    )
}
