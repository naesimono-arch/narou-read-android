package com.novelreader.ui.skins.j

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.novelreader.ui.discovery.pointLabel
import com.novelreader.ui.discovery.readTimeLabel
import com.novelreader.ui.theme.AmbDarkGoldPortal
import com.novelreader.ui.theme.AmbDarkMossPortal
import com.novelreader.ui.theme.GlyphDarkPortal
import com.novelreader.ui.theme.GoldPortal
import com.novelreader.ui.theme.GreenPortal
import com.novelreader.ui.theme.InkPortal
import com.novelreader.ui.theme.LinePortal
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.PagePortal
import com.novelreader.ui.theme.PanelPortal
import com.novelreader.ui.theme.PlumPortal
import com.novelreader.ui.theme.SoftPortal
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPreset
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import java.util.Locale

// ============================================================
// スキンJ「ポータル」の発見＝まだ開かぬ扉が並ぶ回廊（正本 discovery-J.html・ADR 0022 §1 の構造分岐先）。
//   ・発見ホーム DiscoveryHomePortalJ＝回廊の大気（上部の金＋森グラデ・左右端の扉の気配 peek）に
//     見出し「見つける」＋気分の扉（各扉に固有の大気＋象徴文字）＋ジャンル入口＋期間タブ＋
//     ランキング（"いま多くの人がくぐる扉"＝左の光条・順位明朝・上位3位のみ金）。
//   ・結果一覧 DiscoveryResultPortalJ＝プリセット/ジャンル/検索の共通着地（back・文脈見出し・条件チップ・件数・回廊行）。
//
// 署名色（全画面不動・3系統以内・ADR 0022 §2 発見クロームはテーマ不変）: 金 GoldPortal（順位＝唯一の強調・敷居）／
//   森緑 GreenPortal（世界・光条・pt・右 peek）／宵紫 PlumPortal（他の世界＝左 peek）。
//
// 色は Portal val のみ（直書き hex 禁止・近似禁止）。回廊/扉の大気はデータ駆動 ambient（ADR 0022 §5）だが
//   本画面は Color.kt へ値を追加できないため、既存 Portal val の重ねで森の大気を組む（BookshelfPortalJ.drawAmbient
//   と同流儀）。モックの扉固有の森リニア中間色（#1A2A1F/#101711 等）と回廊森 rgba(31,52,38,.55) は Portal val に
//   無い＝発明も近似もせず、森トークン PanelPortal/PagePortal/AmbDarkMossPortal と署名3色 radial で描き分ける
//   （厳密再現には値追加が要る＝報告参照）。
//
// モーション: discovery-J.html は keyframes/transition/JS ゼロ（ADR 0022 §3「J はモックにモーションが存在しない
//   ＝静止で実装開始」）。発見には ProcessingBanner のような機能フィードバックも無い＝この画面はアニメーションを
//   一切持たず reduce-motion 分岐も不要（M/P 発見と同じ扱い）。
//
// 機能全数（ADR 0022＝D 経路と欠落なく結線・M/P の全数移植に倣う）: 検索導線・気分プリセット4・
//   ジャンルチップ（NarouGenres 駆動）・ジャンル一覧入口「すべて」・期間タブ6・ランキング行タップ・
//   Loading/Empty/Error・戻る。モックが省いた D 機能（ホームの戻る＝本棚へ／ジャンル一覧入口／状態分岐／
//   結果の条件ドロップダウン・ページング全状態・0件 CTA・process death 復帰の最小ローディング）は欠落させず J 意匠へ写す。
//
// タイポ: 見出し/題名/順位＝明朝(MinchoFamily・mock var(--mincho))・本文/UI＝既定ゴシック(mock var(--gothic))。
//   構造画面専用の px 値は正本モックの font-size を 1:1 で sp へ写す（各行にモック由来コメント・ADR 0022 §5 の in-file 集約）。
// ============================================================

// ---- 描画層で層の上に載る透過色（グラデ地の上へ載るため .copy(alpha=) で正本 α を付与・base は名前付き Portal val）----
private val Soft2Portal = InkPortal.copy(alpha = 0.40f)         // --soft2 rgba(233,240,228,.40)
private val PeekPlumCorridor = PlumPortal.copy(alpha = 0.55f)   // .peek.l 宵紫（他の世界）rgba(120,86,150,.55) を署名 plum で
private val PeekGreenCorridor = GreenPortal.copy(alpha = 0.45f) // .peek.r rgba(159,207,169,.45)＝GreenPortal α.45
private val LightBarDim = GreenPortal.copy(alpha = 0.35f)       // .rk::before 光条 opacity.35（森緑で淡く）
private val Top3BarTail = GreenPortal.copy(alpha = 0.6f)        // .rk.top3::before gradient 末端 rgba(159,207,169,.6)
private val CondGreenBorder = GreenPortal.copy(alpha = 0.45f)   // .cd border rgba(159,207,169,.45)

// K 形伝播の実検索フィールド地（モック discovery-J.html --field rgba(233,240,228,.06)＝地色を僅かに持ち上げる。
// 濃色ダミー板を避ける狙い）。InkPortal は温白 rgb(233,240,228)（Soft2Portal と同 base）で、α のみ .06（--field 指定値）。
// mock の backdrop-filter:blur(6px) は Compose の標準 Modifier に対応が無く省略（地の透過で近い印象を出す）。
private val SearchFieldBgPortal = InkPortal.copy(alpha = 0.06f)

// 気分の扉の固有大気（ADR 0022 §5 のデータ駆動 ambient＝MoodPreset entries と 1:1）。
// モックの扉色（森リニア中間色）は Portal val 不在＝発明せず、署名3色 radial の角配置で「行き先」を描き分ける。
// tint/角/α はモック .amb-tabi/.amb-yoru/.amb-hana/.amb-sasi の radial 指定（色系統・at 位置・α）を写す。
private data class DoorAmbient(val tint: Color, val cx: Float, val cy: Float, val alpha: Float, val glyph: String)
private val DoorAmbients = listOf(
    DoorAmbient(GreenPortal, 0.25f, 0.12f, 0.24f, "旅"), // .amb-tabi 森緑 at 25% 12% .24
    DoorAmbient(PlumPortal, 0.78f, 0.14f, 0.34f, "夜"),  // .amb-yoru 宵紫 at 78% 14% .34
    DoorAmbient(GoldPortal, 0.30f, 0.12f, 0.26f, "話"),  // .amb-hana 金   at 30% 12% .26
    DoorAmbient(GreenPortal, 0.76f, 0.14f, 0.20f, "絵"), // .amb-sasi 森緑 at 76% 14% .20
)
private fun doorAmbientFor(ordinal: Int) = DoorAmbients[ordinal % DoorAmbients.size]

// ============================================================
// 発見ホーム（モック左フレーム＝扉の回廊）
// ============================================================
@Composable
internal fun DiscoveryHomePortalJ(
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
    // 期間タブ切替のスクロール位置リセット対策（実機報告 2026-07-19・M の横展開）。真因: 再取得のたびに
    // 一旦 Loading を挟むため、この単一 LazyColumn では Content のランキング行が status 行1件へ全置換され
    // 総コンテンツ高が縮み、LazyListState が先頭へクランプされる。対処: 直近 Content を控え、再取得(Loading)
    // 中はそのランキング骨格（同 key=ncode）を出し続けてアンカーを保つ（stale-while-revalidate）。VM 非改変。
    var lastContent by remember { mutableStateOf<DiscoveryUiState.Content?>(null) }
    LaunchedEffect(state) { (state as? DiscoveryUiState.Content)?.let { lastContent = it } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawCorridor() }, // 回廊の外殻＋上部の金/森グラデ（.phone::before・静止1回）
    ) {
        CorridorPeeks() // 左右端の扉の気配（.peek.l 宵紫／.peek.r 森緑）＝回廊に並ぶ扉

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // .top 上段: 見出し＋戻る（← 本棚へ＝モック省略の D 機能を J 意匠で欠落なく写した先頭導線・M/P と同型）。
            // 検索はトップバーのアイコン1個から下の実検索フィールドへ分離・格上げした（K 形伝播）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S12, bottom = Spacing.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = SoftPortal)
                }
                Text(
                    "見つける",
                    fontFamily = MinchoFamily,
                    fontSize = 28.sp,           // .top h1 28px
                    letterSpacing = 0.14.em,
                    fontWeight = FontWeight.Medium,
                    color = InkPortal,
                    modifier = Modifier.weight(1f).padding(start = Spacing.S8),
                )
            }

            // 固定トップの実検索フィールド（K 形伝播・モック .search）＝検索第一・常時可視。導線は onOpenSearch を再利用。
            SearchFieldPortal(onOpenSearch)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S40, // .scroll padding 0 24 40
                ),
            ) {
                item { PortalSectionLabel("きょうの気分", topSpace = Spacing.S8) } // 先頭 .sec margin 8px
                item { MoodDoors(onPickMood) }
                item { PortalSectionLabel("ジャンルから", topSpace = Spacing.S32) } // .sec.gap margin-top 36px
                item { GenreChipsPortal(onOpenGenre, onPickBiggenre) }
                item { OrderTabsPortal(order, onSelectOrder) }

                // 再取得(Loading)中は直近 Content の骨格を出し続けてアンカーを保つ（初回は骨格未確定＝status 行）。
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
                        PortalRankRow(
                            rank = index + 1,
                            novel = novel,
                            order = order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                    state is DiscoveryUiState.Loading -> item { PortalStatusLine("扉をひらいています…") }
                    state is DiscoveryUiState.Empty -> item { PortalStatusLine("作品が見つかりませんでした") }
                    state is DiscoveryUiState.Error -> item { PortalErrorLine(state.message, onRefresh) }
                }

                // 末尾: 公式サイトで探す逃げ道（K 形伝播・モック .official）。K の OfficialLinkK と同一導線を配線。
                item { OfficialLinkPortal() }
            }
        }
    }
}

// ============================================================
// 結果一覧（モック右フレーム＝プリセット/ジャンル/検索の共通着地）
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryResultPortalJ(
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
    // process death 復帰中は文脈 null＝D/M/P と同じく退去せず最小ローディングで待つ。
    // 文脈判定は Box の外で行い早期 return する（Box content ラムダ内の return@Box は Compose のグループ整合を崩す＝IntStack 不整合クラッシュ）。
    if (ctx == null) {
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawCorridor() }) {
            CorridorPeeks()
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                PortalStatusLine("扉をひらいています…")
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawCorridor() },
    ) {
        CorridorPeeks()

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // .back（‹ 見つける）＝App bar の ← は経路に依らず発見ホームへ固定 Up（D の onUp と同型）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUp)
                    .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S8, bottom = Spacing.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = SoftPortal, modifier = Modifier.size(16.dp))
                Text(
                    "見つける",
                    fontSize = 12.sp,           // .back 12px（ゴシック）
                    color = SoftPortal,
                    modifier = Modifier.padding(start = Spacing.S8),
                )
            }

            // .ctx: 明朝見出し＋補足。見出しは1行＋末尾省略（D と同じ溢れ対策）。
            Column(modifier = Modifier.fillMaxWidth().padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S12)) {
                Text(
                    ctx.title,
                    fontFamily = MinchoFamily,
                    fontSize = 22.sp,           // .ctx h2 22px
                    letterSpacing = 0.1.em,
                    fontWeight = FontWeight.Medium,
                    color = InkPortal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ctx.subtitle?.let {
                    Text(
                        it,
                        fontSize = 11.sp,       // .ctx .csub 11px
                        lineHeight = 19.sp,
                        color = SoftPortal,
                        modifier = Modifier.padding(top = Spacing.S8),
                    )
                }
            }

            // .conds: 条件チップ（森緑枠 .cd／調整可 .cd.adj は soft 枠）。D の条件ドロップダウン一式を J 意匠へ写す。
            ResultConds(ctx, onChangeOrder, onChangeGenreFilter, onBack)

            when (val s = state) {
                is DiscoveryUiState.Loading -> PortalStatusLine("扉をひらいています…")
                is DiscoveryUiState.Empty -> ResultEmptyPortal(ctx.source, onAdjust = onBack, onBackToDiscovery = onUp)
                is DiscoveryUiState.Error -> PortalErrorLine(s.message, onRefresh)
                is DiscoveryUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = Spacing.S40),
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
                            color = GreenPortal,    // .cnt var(--green)
                            modifier = Modifier.padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S12, bottom = Spacing.S8),
                        )
                    }
                    itemsIndexed(
                        s.novels,
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        PortalRankRow(
                            rank = index + 1,
                            novel = novel,
                            order = ctx.query.order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                    item { PortalPagingFooter(s.paging, onLoadMore) }
                }
            }
        }
    }
}

// ============================================================
// 固定トップの実検索フィールド（K 形伝播・モック .search）＋末尾の公式サイト逃げ道（.official）。
//   検索タップ＝onOpenSearch、公式起動＝なろう公式 ACTION_VIEW。いずれも K 実装（DiscoveryHomeK の
//   SearchHeaderK / OfficialLinkK）と同一導線を再利用する（新規機能は発明しない）。
// ============================================================
@Composable
private fun SearchFieldPortal(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            // .search（margin-top 16px）。見出しは上段 Row が担うため横 S24＋上下の呼吸のみ。
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S8, bottom = Spacing.S16)
            .fillMaxWidth()
            .height(52.dp)                    // .search 52px（構造値）
            .clip(RoundedCornerShape(14.dp))  // .search border-radius 14px
            .background(SearchFieldBgPortal)   // --field rgba(233,240,228,.06)＝地色を僅かに持ち上げる
            .border(1.dp, LinePortal, RoundedCornerShape(14.dp)) // --line
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = Spacing.S16), // .search padding 0 16px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,        // 隣接プレースホルダ文が読み上げを担う
            tint = GoldPortal,                // .search svg stroke --gold（モックが検索アイコンに金を指定＝正本準拠）
            modifier = Modifier.size(20.dp),  // .search svg 20px
        )
        Text(
            "作品名・作者名・キーワードで探す",
            fontSize = 13.5.sp,               // .search span 13.5px
            color = SoftPortal,               // --soft
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.S12), // .search gap 11px → S12
        )
    }
}

@Composable
private fun OfficialLinkPortal() {
    val context = LocalContext.current
    Column {
        // .official border-top 1px var(--line)＝回廊のヘアライン。
        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.S12).height(1.dp).background(LinePortal))
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
                // mock は :hover で森緑へ変わるが Compose は静止＝--soft のまま（発見クロームのテーマ不変・ADR 0022 §2）。
                color = SoftPortal,
            )
            Icon(
                Icons.Filled.NorthEast, // .official ↗（外部リンク＝右上矢印）
                contentDescription = null,
                tint = SoftPortal,
                modifier = Modifier.size(15.dp), // .official svg 15px
            )
        }
    }
}

// ============================================================
// 節見出し（モック .sec: 字間の広い soft2 の小見出し）
// ============================================================
@Composable
private fun PortalSectionLabel(text: String, topSpace: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        fontSize = 10.5.sp,     // .sec 10.5px
        letterSpacing = 0.28.em,
        color = Soft2Portal,
        modifier = Modifier.padding(top = topSpace, bottom = Spacing.S12),
    )
}

// ============================================================
// 気分の扉（モック .doors: 2列・各扉に固有の大気＋象徴文字＋明朝の行き先名）
// ============================================================
@Composable
private fun MoodDoors(onPickMood: (MoodPreset) -> Unit) {
    val presets = MoodPreset.entries
    Column {
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S12), // .doors gap 14px → S12
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                rowPresets.forEach { preset ->
                    MoodDoor(preset, onClick = { onPickMood(preset) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoodDoor(preset: MoodPreset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val amb = doorAmbientFor(preset.ordinal)
    Box(
        modifier = modifier
            .heightIn(min = 112.dp)          // .door min-height 112px
            .clip(RoundedCornerShape(12.dp))
            .drawDoorAmbient(amb)            // 固有の大気（森 base＋署名色 radial）
            .border(1.dp, LinePortal, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S16, bottom = Spacing.S16), // .door padding 20 18 18
    ) {
        // 象徴文字（.door .g＝右上に巨大・極淡の明朝。行き先を絵で見せる＝遊び）。
        Text(
            amb.glyph,
            fontFamily = MinchoFamily,
            fontSize = 96.sp,               // .door .g 96px
            color = GlyphDarkPortal,        // .door .g rgba(233,240,228,.06)＝GlyphDarkPortal と同値
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                preset.title,
                fontFamily = MinchoFamily,
                fontSize = 14.5.sp,         // .door b 14.5px
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPortal,          // .door b #EDF2E7≒温白 InkPortal
            )
            Text(
                preset.cardLabel,
                fontSize = 10.sp,           // .door span 10px
                letterSpacing = 0.03.em,
                color = SoftPortal,
                modifier = Modifier.padding(top = Spacing.S4), // span margin-top 6px → S4
            )
        }
    }
}

// ============================================================
// ジャンル入口チップ（モック .gchips: 横スクロールの淡いチップ）。NarouGenres 駆動＝D と同じデータ源。
// 末尾に「すべて」＝ジャンル一覧画面入口（モック省略の D 機能を欠落させず写す・M/P と同型）。
// ============================================================
@Composable
private fun GenreChipsPortal(onOpenGenre: () -> Unit, onPickBiggenre: (code: Int, label: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = Spacing.S4), // .gchips padding-bottom 4px
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // .gchips gap 8px
    ) {
        NarouGenres.BIGGENRES.forEach { (code, label) ->
            GenreChip(label, onClick = { onPickBiggenre(code, label) })
        }
        // ジャンル一覧入口（D の「すべて →」に相当。回廊では他チップと同じ淡い月森のチップ）。
        GenreChip("すべて", onClick = onOpenGenre)
    }
}

@Composable
private fun GenreChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp,     // .gc 11.5px
        color = SoftPortal,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, LinePortal, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .gc padding 9px 16px
    )
}

// ============================================================
// 期間タブ（モック .tabs: 横スクロール・選択= ink 太字＋森緑の細い下線。強調色の金は順位に温存）。
// NarouOrder 駆動＝D と同じ並び。
// ============================================================
@Composable
private fun OrderTabsPortal(selected: NarouOrder, onSelect: (NarouOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .drawBehind {
                // .tabs border-bottom 1px var(--line)（選択下線はその上に重なる）。
                drawLine(LinePortal, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(top = Spacing.S16), // .tabs margin-top 20px → S16
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
                    color = if (isOn) InkPortal else SoftPortal, // on=ink 太字／off=soft
                    modifier = Modifier.padding(horizontal = Spacing.S12, vertical = Spacing.S12), // .tab padding 13px 12px
                )
                // .tab.on::after（左右 10px を空けた 2px の森緑下線）。未選択は透明で高さを揃える。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.S8)
                        .height(2.dp)
                        .background(if (isOn) GreenPortal else Color.Transparent),
                )
            }
        }
    }
}

// ============================================================
// ランキング行＝いま人がくぐる扉（モック .rk）。ホーム・結果一覧の共通部品。
//   左に扉の光条（上位3位は金→森緑・以降は森緑で淡く）。順位＝明朝・上位3位のみ金（画面唯一の強調）。
// ============================================================
@Composable
internal fun PortalRankRow(
    rank: Int,
    novel: WorkSummary,
    order: NarouOrder,
    onClick: () -> Unit,
) {
    val isTop3 = rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // .rk::before 光条（left:0・top/bottom 16px inset・width 3px／top3 は 4px・金→森緑グラデ）。
                val top = 16.dp.toPx()
                val bottom = size.height - 16.dp.toPx()
                if (bottom <= top) return@drawBehind
                val barW = if (isTop3) 4.dp.toPx() else 3.dp.toPx()
                if (isTop3) {
                    drawRect(
                        Brush.verticalGradient(listOf(GoldPortal, Top3BarTail), startY = top, endY = bottom),
                        topLeft = Offset(0f, top),
                        size = Size(barW, bottom - top),
                    )
                } else {
                    drawRect(LightBarDim, topLeft = Offset(0f, top), size = Size(barW, bottom - top))
                }
            }
            .clickable(onClick = onClick)
            .padding(start = Spacing.S16, top = Spacing.S16, bottom = Spacing.S16), // .rk padding 18px 0 18px 16px
        horizontalArrangement = Arrangement.spacedBy(Spacing.S16), // .rk gap 16px
        verticalAlignment = Alignment.Top,
    ) {
        // .no 順位（明朝・soft2／top3 は金・大きめ）。
        Text(
            rank.toString(),
            fontFamily = MinchoFamily,
            fontSize = if (isTop3) 23.sp else 20.sp, // .rk .no 20px／.top3 .no 23px
            color = if (isTop3) GoldPortal else Soft2Portal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp).padding(top = Spacing.S4), // .rk .no width 28px・padding-top 3px
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                fontFamily = MinchoFamily,
                fontSize = 15.sp,           // .rk .t 15px
                lineHeight = 23.sp,
                color = InkPortal,          // .rk .t #EDF2E7≒温白 InkPortal
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.S4), // .rk .a margin-top 6px
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    novel.author,
                    fontSize = 11.sp,       // .rk .a 11px
                    color = SoftPortal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NarouGenres.genreLabel(novel.genreCode)?.let { genre ->
                    Text(
                        genre,
                        fontSize = 11.sp,   // .rk .a em（ジャンル）
                        letterSpacing = 0.06.em,
                        color = GreenPortal, // .rk .a em var(--green)
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = Spacing.S8), // em margin-left 8px
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = Spacing.S8), // .rk .m margin-top 9px
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // .m gap 14px → S12
            ) {
                Text(novelStatusLabel(novel), fontSize = 10.5.sp, color = SoftPortal) // .rk .m 10.5px var(--soft)
                readTimeLabel(novel)?.let { Text(it, fontSize = 10.5.sp, color = SoftPortal) }
                pointLabel(order, novel)?.let {
                    Text(it, fontSize = 10.5.sp, color = GreenPortal) // .rk .m .pt var(--green)
                }
            }
        }
    }
}

// ============================================================
// 結果一覧の条件チップ（モック .conds）。D の条件ドロップダウン一式を J 意匠へ写す（機能欠落禁止）。
//   静的条件＝.cd（森緑枠）。並び順/ジャンルの変更＝.cd.adj（soft 枠・クリックでドロップダウン）。
//   検索発は「条件を調整」（検索画面へ戻す）を D と同じく SEARCH のみに限定。
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultConds(
    ctx: ResultContext,
    onChangeOrder: (NarouOrder) -> Unit,
    onChangeGenreFilter: (biggenres: Set<Int>, genres: Set<Int>) -> Unit,
    onBack: () -> Unit,
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
            // 「条件を調整」で戻る先は検索画面＝SEARCH のみ（他発だと戻り先に条件シートが無く騙し導線・D と同判定）。
            CondChip("条件を調整", adjustable = true, onClick = onBack)
        }
    }
}

@Composable
private fun CondChip(label: String, adjustable: Boolean, onClick: (() -> Unit)?) {
    // .cd＝森緑文字/森緑枠（静的条件）、.cd.adj＝soft 文字/--line 枠（調整可）。
    Text(
        label,
        fontSize = 10.5.sp,               // .cd 10.5px
        color = if (adjustable) SoftPortal else GreenPortal,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (adjustable) LinePortal else CondGreenBorder, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.S12, vertical = Spacing.S4), // .cd padding 5px 12px
    )
}

// ============================================================
// 状態分岐の J 意匠（モック未定義＝最小の一文。回廊の大気は既に地に描かれている・M/P と同じく発明を最小化）
// ============================================================
@Composable
private fun PortalStatusLine(text: String) {
    Text(
        text,
        fontFamily = MinchoFamily,
        fontSize = 14.sp,
        color = SoftPortal,
        modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S40),
    )
}

@Composable
private fun PortalErrorLine(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S24)) {
        Text(message, fontFamily = MinchoFamily, fontSize = 14.sp, color = SoftPortal)
        Text(
            "再試行",
            fontFamily = MinchoFamily,
            fontSize = 14.sp,
            color = GoldPortal,
            modifier = Modifier.clickable(onClick = onRetry).padding(top = Spacing.S8),
        )
    }
}

/** 結果0件の「次の一手」（D の ResultEmpty を J 意匠へ写す。検索発は条件シートへ・他発は発見ホームへ）。 */
@Composable
private fun ResultEmptyPortal(source: ResultSource, onAdjust: () -> Unit, onBackToDiscovery: () -> Unit) {
    val isSearch = source == ResultSource.SEARCH
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S24)) {
        Text("条件に合う作品が見つかりませんでした", fontFamily = MinchoFamily, fontSize = 14.sp, color = SoftPortal)
        Text(
            if (isSearch) "検索条件を変える" else "ほかの条件で探す",
            fontFamily = MinchoFamily,
            fontSize = 14.sp,
            color = GoldPortal,
            modifier = Modifier.clickable(onClick = if (isSearch) onAdjust else onBackToDiscovery).padding(top = Spacing.S8),
        )
    }
}

/** ページングフッタ（D の PagingFooter を J 意匠へ写す。Complete は描かない）。 */
@Composable
private fun PortalPagingFooter(paging: PagingState, onLoadMore: () -> Unit) {
    when (paging) {
        PagingState.Idle -> PortalFooterAction("さらに読み込む", onLoadMore)
        PagingState.LoadingMore -> PortalFooterText("読み込んでいます…")
        is PagingState.LoadMoreError -> Column {
            PortalFooterText(paging.message)
            PortalFooterAction("再試行", onLoadMore)
        }
        PagingState.ApiLimitReached -> PortalFooterText("これ以上は取得できません（APIの取得上限に達しました）")
        PagingState.Complete -> Unit
    }
}

@Composable
private fun PortalFooterAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = MinchoFamily,
        fontSize = 12.sp,
        color = GoldPortal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.S24, vertical = Spacing.S16),
    )
}

@Composable
private fun PortalFooterText(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = SoftPortal,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S16),
    )
}

// ============================================================
// 回廊の大気（モック .phone::before＝上部の淡い森金グラデ）＋左右の扉の気配（.peek）。
//   静止1回描画。森 rgba(31,52,38,.55) はトークン不在＝発明せず森トークン AmbDarkMossPortal で沈める。
// ============================================================
private fun DrawScope.drawCorridor() {
    val w = size.width
    val h = size.height
    // 外殻の地（--page #0C0E0B）。
    drawRect(PagePortal)
    // 上部の金の敷居（.phone::before radial 90% 60% at 50% -8%・金 rgba(214,196,120,.11)＝AmbDarkGoldPortal と同 RGB）。
    drawRect(
        Brush.radialGradient(
            colors = listOf(AmbDarkGoldPortal, Color.Transparent),
            center = Offset(w * 0.5f, -h * 0.08f),
            radius = w * 0.9f,
        )
    )
    // 上部の森の気配（.phone::before radial 森 rgba(31,52,38,.55)＝トークン不在。森トークン AmbDarkMossPortal で近い森色に沈める）。
    drawRect(
        Brush.radialGradient(
            colors = listOf(AmbDarkMossPortal, Color.Transparent),
            center = Offset(w * 0.5f, -h * 0.2f),
            radius = w * 1.2f,
        )
    )
}

/** 回廊の左右端に並ぶ扉の気配（.peek.l 宵紫＝他の世界／.peek.r 森緑＝いま人がくぐる扉・幅12dp全高）。 */
@Composable
private fun BoxScope.CorridorPeeks() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(12.dp)                   // .peek width 12px
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(PeekPlumCorridor, Color.Transparent))), // .peek.l 90deg 宵紫→透明
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(12.dp)
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Color.Transparent, PeekGreenCorridor))), // .peek.r 270deg 森緑→透明
    )
}

/**
 * 気分の扉の固有大気を既存 Portal val だけで描く（ADR 0022 §5・値追加禁止のため近似/発明せず名前付き val の重ね）。
 * base(森パネル→外殻の斜めグラデ＝mock linear 165deg の代替) + 署名色 radial(行き先＝mock .amb-* の角配置)。
 */
private fun Modifier.drawDoorAmbient(amb: DoorAmbient): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    // base: 森の内(PanelPortal)→外殻(PagePortal) の斜めグラデ（mock 各扉の森リニア中間色の代替＝実在の森トークン）。
    drawRect(
        Brush.linearGradient(
            listOf(PanelPortal, PagePortal),
            start = Offset(w * 0.2f, 0f),
            end = Offset(w * 0.5f, h),
        )
    )
    // 固有の大気（署名色 radial・角＝mock .amb-* の at 位置と色系統・α）。
    drawRect(
        Brush.radialGradient(
            colors = listOf(amb.tint.copy(alpha = amb.alpha), Color.Transparent),
            center = Offset(w * amb.cx, h * amb.cy),
            radius = w * 0.9f,
        )
    )
}
