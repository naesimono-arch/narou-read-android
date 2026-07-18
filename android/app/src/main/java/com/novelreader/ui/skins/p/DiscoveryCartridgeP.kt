package com.novelreader.ui.skins.p

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.discovery.ChipKind
import com.novelreader.ui.discovery.ConditionChip
import com.novelreader.ui.discovery.conditionChipLabels
import com.novelreader.ui.discovery.novelStatusLabel
import com.novelreader.ui.discovery.pointLabel
import com.novelreader.ui.discovery.readTimeLabel
import com.novelreader.ui.theme.BlueCartridge
import com.novelreader.ui.theme.BlueInkCartridge
import com.novelreader.ui.theme.BoardCartridge
import com.novelreader.ui.theme.CartridgeGenreClay
import com.novelreader.ui.theme.CartridgeGenreSlate
import com.novelreader.ui.theme.CartridgeGenreTaupe
import com.novelreader.ui.theme.CartridgeGold
import com.novelreader.ui.theme.CartridgeGreen
import com.novelreader.ui.theme.HiScoreGoldCartridge
import com.novelreader.ui.theme.CartridgePlum
import com.novelreader.ui.theme.CartridgePurple
import com.novelreader.ui.theme.InkCartridge
import com.novelreader.ui.theme.InkMidCartridge
import com.novelreader.ui.theme.InkSoftCartridge
import com.novelreader.ui.theme.LineDiscCartridge
import com.novelreader.ui.theme.PanelCartridge
import com.novelreader.ui.theme.PhosCartridge
import com.novelreader.ui.theme.PhosDimCartridge
import com.novelreader.ui.theme.PlasticCartridge
import com.novelreader.ui.theme.PlasticHiCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.RedCartridge
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPreset
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import java.util.Locale

// ============================================================
// スキンP「カートリッジ」の発見＝レトロゲームショップ巡り（正本 discovery-P.html・ADR 0022 §1 の構造分岐先）。
//   ・発見ホーム DiscoveryHomeCartridgeP＝marquee（店の看板）＋見出し「見つける」＋気分＝ソフト棚＋
//     ジャンル＝カートリッジ色棚＋週間ランキング＝燐光ドットの HI-SCORE ボード（暗緑LCD＋CRT走査線）。
//   ・結果一覧 DiscoveryResultCartridgeP＝試遊台。back・demo見出し（燐光）＋条件チップ＋件数＋試遊台行。
//
// タイポ裁定（BookshelfCartridgeP 冒頭と同じ流儀）: 題名/本文＝ゴシック（既定サンセリフ）・英数HUD＝
//   monospace（PixelFamily）。明朝（MinchoFamily）は P では非適用＝モック忠実。字面 sp は正本モックの
//   font-size px を 1:1 で写し各行にモック由来コメントを付す。
//
// モーション: discovery-P.html は keyframes/transition/JS ゼロ（ADR 0022 §3「P はモックにモーションが
//   存在しない＝静止で実装開始」）。発見には ProcessingBanner のような機能フィードバックも無いため
//   この画面はアニメーションを一切持たない＝reduce-motion 分岐も不要（M 発見と同じ扱い）。
//
// 機能全数（ADR 0022＝D 経路と欠落なく結線・M の全数移植に倣う）: 検索導線・気分プリセット4・
//   ジャンルチップ（NarouGenres 駆動）・ジャンル一覧入口「すべて」・期間タブ6・ランキング行タップ・
//   Loading/Empty/Error・戻る。モックが省いた D 機能（ホームの戻る＝本棚へ／ジャンル一覧入口／状態分岐／
//   結果の条件ドロップダウン・ページング・0件 CTA）は欠落させず P 意匠へ写す（各所にコメント）。
//
// 色・不足値（近似禁止＝ADR 0022 §5「系統別 val を直接参照」）:
//   ・気分ソフト棚の箱色（mock #cdbe8f/#b8a0a8/#a7b391/#a9bcc4）はトークン化されていない装飾ラベル色＝
//     サンクションされた w1-w4 ラベル色（CartridgeLabelPalette 相当）を preset 序数で引く（発明でなく既存 val）。
//   ・試遊台カセット背/ジャンル棚の色は g1-g6（GenreSpinePalette）を genre/序数で引く（データ駆動パレット）。
//   ・HI-SCORE 王冠1位の金 #d9c27a はトークン不在＝**不足値**。近似せず 1 位も他行と同じ燐光で描く（TODO・報告）。
//   ・ボードの地グラデ端点（#33401d/#232c13）・inset ベゼル #1c240f はトークン不在＝代表単色 BoardCartridge に
//     畳む（BookshelfCartridgeP の LcdFrame 代表単色流儀）。ベゼルは省略（署名外の凹み）。
//   ・CRT ドット/走査線の rgba(0,0,0,α) は近黒の --ink（InkCartridge）copy-alpha で暗化に置換（BookshelfP の
//     label 枠 rgba(0,0,0,.14)→InkCartridge.copy と同技法・パレット内で暗化を担保）。
// ============================================================

// P の pixel 記号チャンネル（--pixel: ui-monospace 系）。HI-SCORE/SCORE/pt 等の英数 HUD に使う。
private val PixelFamily = FontFamily.Monospace

// ジャンル識別色 g1-g6（ADR 0022 §5 のデータ駆動パレット＝BIGGENRES 表示順と 1:1）。
// 恋愛→g1 / ファンタジー→g2 / 文芸→g3 / SF→g4 / その他→g5 / ノンジャンル→g6（mock spine と一致）。
private val GenreSpinePalette = listOf(
    CartridgePlum,        // --g1
    CartridgeGreen,       // --g2
    CartridgeGold,        // --g3
    CartridgeGenreSlate,  // --g4
    CartridgeGenreTaupe,  // --g5
    CartridgeGenreClay,   // --g6
)

// 気分ソフトのパッケージ色。mock の 4 箱色はトークン化されていない装飾色のため、サンクションされた
// ラベル色 w1-w4 を preset 序数で引く（BookshelfCartridgeP.CartridgeLabelPalette と同一 4 色・退色トーンの識別色）。
private val MoodPackagePalette = listOf(CartridgeGold, CartridgePurple, CartridgeGreen, CartridgePlum)

// 描画層で層の上に載る透過色（焼き込めず .copy(alpha=) で付与）。
private val CrtDot = InkCartridge.copy(alpha = 0.28f)   // .board::before radial dots rgba(0,0,0,.28)（近黒 --ink で暗化）
private val CrtScan = InkCartridge.copy(alpha = 0.10f)  // .board::before scanlines rgba(0,0,0,.10)
private val PhosTabBorder = PhosCartridge.copy(alpha = 0.30f)  // .tab border rgba(179,189,130,.3)
private val PhosRowRule = PhosCartridge.copy(alpha = 0.16f)    // .hs-row border-top rgba(179,189,130,.16)

// 燐光グロー（text-shadow:0 0 6/7px rgba(179,189,130,.35/.4)）。Compose Text の Shadow で忠実に翻訳する。
@Composable
private fun phosGlow(): Shadow {
    val blur = with(LocalDensity.current) { 6.dp.toPx() }
    return Shadow(color = PhosCartridge.copy(alpha = 0.4f), blurRadius = blur)
}

/** 現在 order のポイント数値（HI-SCORE .sc .v＝数値のみ）。DiscoveryCommon.pointLabel と同じ order→field
 *  写像だが、当該画面は編集不可のためそちら（"週間 12,345pt"）でなく数値だけが要る＝ここで最小複製する。 */
private fun boardPointValue(order: NarouOrder, novel: NarouNovel): String? {
    val v = when (order) {
        NarouOrder.DAILY -> novel.dailyPoint
        NarouOrder.WEEKLY -> novel.weeklyPoint
        NarouOrder.MONTHLY -> novel.monthlyPoint
        NarouOrder.QUARTER -> novel.quarterPoint
        NarouOrder.TOTAL, NarouOrder.NEW -> novel.globalPoint
    } ?: return null
    return String.format(Locale.JAPAN, "%,d", v)
}

/** 試遊台カセット背の色（.try-row .cart＝ジャンル色）。novel.genre を g1-g6 へ安定写像（並び替えで変わらない）。 */
private fun cartColorFor(novel: NarouNovel): Color {
    val key = novel.genre ?: novel.ncode?.hashCode() ?: 0
    return GenreSpinePalette[(key and 0x7fffffff) % GenreSpinePalette.size]
}

/** 作者 ・ ジャンル ・ 状態 ・ 読了時間 を中黒でつないだメタ行（mock .hs-row .a／試遊台では分割表示）。 */
private fun rankMetaLine(novel: NarouNovel): String {
    val parts = buildList {
        novel.writer?.takeIf { it.isNotBlank() }?.let { add(it) }
        NarouGenres.genreLabel(novel.genre)?.let { add(it) }
        add(novelStatusLabel(novel))
        readTimeLabel(novel)?.let { add(it) }
    }
    return parts.joinToString(" ・ ")
}

// ============================================================
// 発見ホーム（モック左フレーム＝店）
// ============================================================
@Composable
internal fun DiscoveryHomeCartridgeP(
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // .phone 退色プラスチック筐体（linear-gradient(150deg, plastic-hi, plastic 22%, plastic-lo)）。
                drawRect(
                    Brush.linearGradient(
                        0f to PlasticHiCartridge,
                        0.22f to PlasticCartridge,
                        1f to PlasticLoCartridge,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Marquee()  // .marquee＝店の看板（POCKET NOVEL / ● OPEN）
            // .head: 見出し「見つける」＋検索。戻る（← 本棚へ）はモック省略の D 機能を欠落させず先頭へ写す。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlasticIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = InkCartridge, modifier = Modifier.size(20.dp))
                }
                Text(
                    "見つける",
                    fontSize = 25.sp,          // .head h1 25px（ゴシック）
                    fontWeight = FontWeight.Bold,
                    color = InkCartridge,
                    modifier = Modifier.weight(1f).padding(start = Spacing.S12),
                )
                PlasticIconButton(onClick = onOpenSearch) {
                    // 用語辞書（着地画面名「探す」）に合わせる＝D 発見の検索アイコンと同一 accessible name。
                    Icon(Icons.Filled.Search, contentDescription = "探す", tint = InkCartridge, modifier = Modifier.size(21.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = Spacing.S16, end = Spacing.S16, bottom = Spacing.S32),
            ) {
                item { SectionLabel("きょうの気分") }
                item { MoodShelf(onPickMood) }
                item { Ledge() }  // .ledge＝ソフト棚の棚板

                item { SectionLabel("ジャンルから") }
                item { GenreTrack(onOpenGenre, onPickBiggenre) }

                item { SectionLabel("${order.uiLabel}ランキング") }  // mock「週間ランキング」＝選択期間で真実化
                item {
                    HiScoreBoard(
                        order = order,
                        state = state,
                        onSelectOrder = onSelectOrder,
                        onOpenDetail = onOpenDetail,
                        onRefresh = onRefresh,
                    )
                }
            }
        }
    }
}

// ============================================================
// 結果一覧（モック右フレーム＝試遊台。プリセット/ジャンル/検索の共通着地）
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryResultCartridgeP(
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        0f to PlasticHiCartridge,
                        0.22f to PlasticCartridge,
                        1f to PlasticLoCartridge,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                )
            },
    ) {
        // process death 復帰中は文脈 null＝D/M と同じく退去せず最小ローディングで待つ。
        if (ctx == null) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                PhosStatusLine("LOADING…", "読み込んでいます")
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // .back（‹ 見つける）＝App bar の ← は経路に依らず発見ホームへ固定 Up（D の onUp と同型）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUp)
                    .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = InkSoftCartridge, modifier = Modifier.size(16.dp))
                Text(
                    "見つける",
                    fontSize = 12.5.sp,        // .back 12.5px（ゴシック）
                    color = InkSoftCartridge,
                    modifier = Modifier.padding(start = Spacing.S4),
                )
            }

            // .demo＝試遊台の見出しパネル（暗緑ボード地・燐光の題と説明）。
            DemoPanel(ctx)

            // .conds＝条件チップ（青ink 枠 .cd／調整可 .cd.adj は退色レッド）。D の条件ドロップダウン一式を写す。
            ResultConds(ctx, onChangeOrder, onChangeGenreFilter, onBack)

            when (val s = state) {
                is DiscoveryUiState.Loading -> PhosStatusLine("LOADING…", "読み込んでいます")
                is DiscoveryUiState.Empty -> ResultEmptyCartridge(ctx.source, onAdjust = onBack, onBackToDiscovery = onUp)
                is DiscoveryUiState.Error -> InkErrorLine(s.message, onRefresh)
                is DiscoveryUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = Spacing.S32),
                ) {
                    item {
                        // .cnt 件数（総数だけ大表示せず shown 件を明示＝D と同じ F-J 表記）。
                        val shown = s.novels.size
                        val allcountText = String.format(Locale.JAPAN, "%,d", s.allcount)
                        val countText = if (s.allcount > shown) "$allcountText 件中 上位 $shown 件を表示" else "$allcountText 作品"
                        Text(
                            countText,
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,      // .cnt 11px
                            letterSpacing = 0.06.em,
                            color = InkMidCartridge,  // mock --ink-mid（意味メタの AA 値）
                            modifier = Modifier.padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S16, bottom = Spacing.S4),
                        )
                    }
                    itemsIndexed(
                        s.novels,
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        TryRow(
                            novel = novel,
                            order = ctx.query.order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                    item { PagingFooterCartridge(s.paging, onLoadMore) }
                }
            }
        }
    }
}

// ============================================================
// 店の看板（.marquee＝暗緑ボード地の看板バー・燐光の店名＋● OPEN）
// ============================================================
@Composable
private fun Marquee() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16, vertical = Spacing.S4)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(BoardCartridge)  // 地グラデ端点はトークン不在＝代表単色 BoardCartridge に畳む
            .padding(horizontal = Spacing.S12, vertical = Spacing.S8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "POCKET NOVEL",
            fontFamily = PixelFamily,
            fontSize = 10.sp,             // .marquee .shop 10px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.24.em,
            color = PhosCartridge,
            style = androidx.compose.ui.text.TextStyle(shadow = phosGlow()),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", fontFamily = PixelFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedCartridge)
            Text(
                " OPEN",
                fontFamily = PixelFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.24.em,
                color = PhosCartridge,
            )
        }
    }
}

/** プラスチックの角ボタン（.head .ib: plastic-hi 面・角丸11dp）。BookshelfP の PlasticSquareButton と同型。 */
@Composable
private fun PlasticIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    // アクセシブル名は中の Icon の contentDescription が担う（M/BookshelfP と同型）。
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(PlasticHiCartridge)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ============================================================
// 節見出し（.sec＝▸ 付きの字間広い pixel 小見出し）
// ============================================================
@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.S4, end = Spacing.S4, top = Spacing.S24, bottom = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▸", fontFamily = PixelFamily, fontSize = 10.sp, color = RedCartridge)  // .sec::before ▸（退色レッド）
        Text(
            text,
            fontFamily = PixelFamily,
            fontSize = 10.sp,             // .sec 10px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.em,
            color = InkMidCartridge,
            modifier = Modifier.padding(start = Spacing.S8),
        )
    }
}

// ============================================================
// きょうの気分＝ソフトのパッケージ棚（.shelf＝横スクロール・.pkg カード）
// ============================================================
@Composable
private fun MoodShelf(onPickMood: (MoodPreset) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Spacing.S4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),  // .shelf gap 13px → S12
    ) {
        MoodPreset.entries.forEach { preset ->
            MoodPackage(preset, color = MoodPackagePalette[preset.ordinal % MoodPackagePalette.size], onClick = { onPickMood(preset) })
        }
    }
}

@Composable
private fun MoodPackage(preset: MoodPreset, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(118.dp)                // .pkg flex 118px
            .clickable(onClick = onClick),
    ) {
        // .box＝ソフト箱（左端 9px の背表紙帯＋SOFT タグ）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)            // .pkg .box 96px
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        ) {
            // 背表紙帯（.box::before＝左 9px の暗い帯）。
            Box(modifier = Modifier.width(9.dp).fillMaxHeight().background(InkCartridge.copy(alpha = 0.14f)))
            Text(
                "SOFT",
                fontFamily = PixelFamily,
                fontSize = 7.5.sp,        // .box .tag 7.5px
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = InkCartridge.copy(alpha = 0.5f),  // .tag color rgba(0,0,0,.5)
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.S4)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlasticHiCartridge.copy(alpha = 0.55f))  // .tag bg rgba(255,255,255,.35)（明面 hi で近似せず淡く）
                    .padding(horizontal = Spacing.S4),
            )
        }
        Text(
            preset.title,
            fontSize = 12.sp,             // .pkg .nm 12px（ゴシック）
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            color = InkCartridge,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.S8),
        )
        Text(
            preset.cardLabel,
            fontSize = 10.sp,             // .pkg .cd 10px
            lineHeight = 14.sp,
            color = InkMidCartridge,      // mock --ink-soft は AA 不足＝意味メタは --ink-mid
            modifier = Modifier.padding(top = Spacing.S4),
        )
    }
}

/** ソフト棚の棚板（.ledge＝プラ地の細い棚板）。 */
@Composable
private fun Ledge() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.S8, start = Spacing.S4, end = Spacing.S4)
            .height(9.dp)                 // .ledge 9px
            .clip(RoundedCornerShape(2.dp))
            .background(Brush.verticalGradient(listOf(PlasticLoCartridge, PanelCartridge))),
    )
}

// ============================================================
// ジャンル＝カートリッジの色棚（.cartrack＝横スクロール・.spine 背表紙）。
// NarouGenres.BIGGENRES 駆動＝D と同じデータ源。末尾に「すべて」＝ジャンル一覧入口（D 機能欠落禁止）。
// ============================================================
@Composable
private fun GenreTrack(onOpenGenre: () -> Unit, onPickBiggenre: (code: Int, label: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Spacing.S4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),  // .cartrack gap 9px → S8
    ) {
        NarouGenres.BIGGENRES.forEachIndexed { index, (code, label) ->
            GenreSpine(label, color = GenreSpinePalette[index % GenreSpinePalette.size], onClick = { onPickBiggenre(code, label) })
        }
        // ジャンル一覧入口（D の「すべて →」に相当）＝プラ地の空きカートリッジ背。
        GenreSpine("すべて", color = PanelCartridge, onClick = onOpenGenre, isEntry = true)
    }
}

@Composable
private fun GenreSpine(label: String, color: Color, onClick: () -> Unit, isEntry: Boolean = false) {
    val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Box(
        // clickable を最外へ置く＝clip(角丸)後にクリック判定が矩形外へ削れて子タップが素通りするのを防ぐ
        // （残り 8 テスト緑・本チップだけ落ちた実測の是正。Box 面全体を素直な当たり判定にする）。
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(66.dp)                 // .spine 66px
            .height(88.dp)                // .spine 88px
            .clip(shape)
            .background(color)
            .then(if (isEntry) Modifier.border(1.dp, LineDiscCartridge, shape) else Modifier)
            .padding(Spacing.S8),
    ) {
        // 背表紙のラベル溝（.spine::before＝上部の反復ストライプ）。
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.TopStart)) {
            val stripe = 2.dp.toPx()
            val gap = 3.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawRect(InkCartridge.copy(alpha = 0.18f), topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                x += stripe + gap
            }
        }
        Text(
            label,
            fontSize = 11.5.sp,           // .spine .gn 11.5px（ゴシック）
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            color = if (isEntry) InkMidCartridge else InkCartridge,  // .spine .gn #26251d 相当＝AA を満たす --ink で受ける
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

// ============================================================
// 週間ランキング＝HI-SCORE ボード（.board＝暗緑LCD・燐光ドット数字・CRTドット/走査線）
// ============================================================
@Composable
private fun HiScoreBoard(
    order: NarouOrder,
    state: DiscoveryUiState,
    onSelectOrder: (NarouOrder) -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.S4)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRect(BoardCartridge)  // 放射グラデ端点はトークン不在＝代表単色に畳む
                drawCrt(scanlines = true) // CRT ドット＋走査線（署名）
            }
            .padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S12, bottom = Spacing.S4),
    ) {
        // .tabs＝期間タブ（燐光ピル・選択= phos 地 + board ink 文字）。NarouOrder 駆動＝D と同じ並び。
        OrderTabsPhos(order, onSelectOrder)

        when (state) {
            is DiscoveryUiState.Loading -> PhosBoardLine("READING…")
            is DiscoveryUiState.Empty -> PhosBoardLine("該当なし")
            is DiscoveryUiState.Error -> {
                PhosBoardLine(state.message)
                Text(
                    "再試行",
                    fontFamily = PixelFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PhosCartridge,
                    modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = Spacing.S8),
                )
            }
            is DiscoveryUiState.Content -> state.novels.forEachIndexed { index, novel ->
                HiScoreRow(
                    rank = index + 1,
                    novel = novel,
                    order = order,
                    onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                )
            }
        }
    }
}

/** 期間タブ（.tabs/.tab＝燐光ピル・on= phos 地/board 文字）。 */
@Composable
private fun OrderTabsPhos(selected: NarouOrder, onSelect: (NarouOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = Spacing.S8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),  // .tabs gap 6px → S8
    ) {
        NarouOrder.entries.forEach { o ->
            val isOn = o == selected
            Text(
                o.uiLabel,
                fontFamily = PixelFamily,
                fontSize = 10.sp,          // .tab 10px
                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 0.06.em,
                color = if (isOn) BoardCartridge else PhosDimCartridge,  // on=board 地文字／off=燐光暗
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isOn) PhosCartridge else Color.Transparent)
                    .then(if (isOn) Modifier else Modifier.border(1.dp, PhosTabBorder, RoundedCornerShape(5.dp)))
                    .clickable { onSelect(o) }
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S4),  // .tab padding 5px 10px
            )
        }
    }
}

/** HI-SCORE の1行（.hs-row＝順位・題・メタ・スコア）。1位王冠の金 #d9c27a はトークン不在＝燐光で描く（不足値）。 */
@Composable
private fun HiScoreRow(rank: Int, novel: NarouNovel, order: NarouOrder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // .hs-row border-top（燐光の淡ルール）。
                drawLine(PhosRowRule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.S12),  // .hs-row padding 11px 0 → S12
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),  // .hs-row gap 12px
    ) {
        // .rk＝燐光の大順位。1位のみ王冠の金（--gold 相当 #d9c27a＝中央トークン化済み）。
        Row(modifier = Modifier.width(42.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                rank.toString(),
                fontFamily = PixelFamily,
                fontSize = 20.sp,          // .hs-row .rk 20px
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) HiScoreGoldCartridge else PhosCartridge,
                style = androidx.compose.ui.text.TextStyle(shadow = phosGlow()),
            )
            Text(
                rankSuffix(rank),
                fontFamily = PixelFamily,
                fontSize = 10.sp,          // .rk .st 10px
                fontWeight = FontWeight.Bold,
                color = PhosCartridge,
                modifier = Modifier.padding(bottom = Spacing.S4),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title ?: "（無題）",
                fontSize = 13.5.sp,        // .hs-row .t 13.5px（ゴシック）
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                color = PhosCartridge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                rankMetaLine(novel),
                fontFamily = PixelFamily,
                fontSize = 9.5.sp,         // .hs-row .a 9.5px
                letterSpacing = 0.02.em,
                color = PhosDimCartridge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.S4),
            )
        }
        // .sc＝スコア（数値=燐光・pt=燐光暗）。数値が無ければ列ごと畳む。
        boardPointValue(order, novel)?.let { score ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    score,
                    fontFamily = PixelFamily,
                    fontSize = 15.sp,      // .sc .v 15px
                    fontWeight = FontWeight.Bold,
                    color = PhosCartridge,
                    style = androidx.compose.ui.text.TextStyle(shadow = phosGlow()),
                )
                Text(
                    "pt",
                    fontFamily = PixelFamily,
                    fontSize = 9.sp,       // .sc .u 9px
                    color = PhosDimCartridge,
                    modifier = Modifier.padding(top = Spacing.S4),
                )
            }
        }
    }
}

/** 順位の序数接尾（1→st・2→nd・3→rd・他→th）。mock .rk .st の英語序数を踏襲。 */
private fun rankSuffix(rank: Int): String = when {
    rank % 100 in 11..13 -> "th"
    rank % 10 == 1 -> "st"
    rank % 10 == 2 -> "nd"
    rank % 10 == 3 -> "rd"
    else -> "th"
}

/** ボード内の状態一文（燐光）。 */
@Composable
private fun PhosBoardLine(text: String) {
    Text(
        text,
        fontFamily = PixelFamily,
        fontSize = 11.sp,
        color = PhosDimCartridge,
        modifier = Modifier.padding(vertical = Spacing.S24),
    )
}

// ============================================================
// 試遊台の見出しパネル（.demo＝暗緑ボード地・燐光の題と説明）
// ============================================================
@Composable
private fun DemoPanel(ctx: ResultContext) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16, vertical = Spacing.S4)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(BoardCartridge)
                drawCrt(scanlines = false)  // .demo::before はドットのみ（走査線なし）
            }
            .padding(horizontal = Spacing.S12, vertical = Spacing.S12),
    ) {
        Text(
            "TRY DEMO",
            fontFamily = PixelFamily,
            fontSize = 9.5.sp,             // .demo .try 9.5px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.16.em,
            color = PhosCartridge,
        )
        Text(
            ctx.title,
            fontSize = 19.sp,              // .demo h2 19px（ゴシック）
            fontWeight = FontWeight.Bold,
            color = PhosCartridge,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.S8),
        )
        ctx.subtitle?.let {
            Text(
                it,
                fontSize = 11.5.sp,        // .demo .sub 11.5px
                lineHeight = 18.sp,
                color = PhosDimCartridge,
                modifier = Modifier.padding(top = Spacing.S8),
            )
        }
    }
}

// ============================================================
// 条件チップ（.conds＝青ink 枠 .cd／調整可 .cd.adj は退色レッド）。D の条件ドロップダウン一式を写す（機能欠落禁止）。
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
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S12, bottom = Spacing.S4),
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
    // .cd＝青ink 文字/青枠（静的条件）、.cd.adj＝退色レッド文字/レッド枠（調整可）。
    val color = if (adjustable) RedCartridge else BlueInkCartridge
    Text(
        label,
        fontFamily = PixelFamily,
        fontSize = 10.5.sp,               // .cd 10.5px
        letterSpacing = 0.02.em,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (adjustable) RedCartridge else BlueCartridge, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.S12, vertical = Spacing.S4),  // .cd padding 5px 12px
    )
}

// ============================================================
// 試遊台の1行（.try-row＝カセット背＋題名＋作者/ジャンル＋メタ〔状態・読了・pt〕）
// ============================================================
@Composable
private fun TryRow(novel: NarouNovel, order: NarouOrder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // .try-row border-bottom（プラ地のヘアライン）。
                drawLine(LineDiscCartridge, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S16),  // .try-row padding 16px 0（横は画面マージン）
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),        // .try-row gap 14px → S12
    ) {
        // .cart＝カセット背（ジャンル色・上端リブ）。
        Box(
            modifier = Modifier
                .width(40.dp)             // .try-row .cart 40px
                .height(48.dp)            // .try-row .cart 48px
                .clip(CutCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(cartColorFor(novel)),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).padding(start = Spacing.S4, end = Spacing.S4, top = Spacing.S4)) {
                val stripe = 2.dp.toPx()
                val gap = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(InkCartridge.copy(alpha = 0.18f), topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                    x += stripe + gap
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title ?: "（無題）",
                fontSize = 14.sp,          // .try-row .t 14px（ゴシック）
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                color = InkCartridge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildList {
                    novel.writer?.takeIf { it.isNotBlank() }?.let { add(it) }
                    NarouGenres.genreLabel(novel.genre)?.let { add(it) }
                }.joinToString(" ・ "),
                fontSize = 10.5.sp,        // .try-row .a 10.5px
                color = InkMidCartridge,   // mock --ink-soft は AA 不足＝意味メタは --ink-mid
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.S4),
            )
            // .m＝メタ行（状態 ・ 読了 ・ pt〔青ink〕）。pt は pointLabel を再利用。
            Row(
                modifier = Modifier.padding(top = Spacing.S8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            ) {
                Text(novelStatusLabel(novel), fontFamily = PixelFamily, fontSize = 10.sp, color = InkMidCartridge)
                readTimeLabel(novel)?.let {
                    Text(it, fontFamily = PixelFamily, fontSize = 10.sp, color = InkMidCartridge)
                }
                pointLabel(order, novel)?.let {
                    Text(it, fontFamily = PixelFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlueInkCartridge)  // .m .pt 青ink
                }
            }
        }
    }
}

// ============================================================
// 状態分岐の P 意匠（モック未定義＝最小の一文。M と同じく発明を最小化）
// ============================================================
/** 燐光の状態一文＋副文（ボード外・プラ地）。 */
@Composable
private fun PhosStatusLine(head: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S40)) {
        Text(head, fontFamily = PixelFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.16.em, color = InkMidCartridge)
        Text(body, fontSize = 13.sp, color = InkMidCartridge, modifier = Modifier.padding(top = Spacing.S8))
    }
}

@Composable
private fun InkErrorLine(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S24)) {
        Text(message, fontSize = 13.sp, color = InkMidCartridge)
        Text(
            "再試行",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RedCartridge,
            modifier = Modifier.clickable(onClick = onRetry).padding(top = Spacing.S8),
        )
    }
}

/** 結果0件の「次の一手」（D の ResultEmpty を P 意匠へ写す。検索発は条件へ・他発は発見ホームへ）。 */
@Composable
private fun ResultEmptyCartridge(source: ResultSource, onAdjust: () -> Unit, onBackToDiscovery: () -> Unit) {
    val isSearch = source == ResultSource.SEARCH
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S24)) {
        Text("条件に合う作品が見つかりませんでした", fontSize = 13.sp, color = InkMidCartridge)
        Text(
            if (isSearch) "検索条件を変える" else "ほかの条件で探す",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RedCartridge,
            modifier = Modifier.clickable(onClick = if (isSearch) onAdjust else onBackToDiscovery).padding(top = Spacing.S8),
        )
    }
}

/** ページングフッタ（D の PagingFooter を P 意匠へ写す。Complete は描かない）。 */
@Composable
private fun PagingFooterCartridge(paging: PagingState, onLoadMore: () -> Unit) {
    when (paging) {
        PagingState.Idle -> FooterAction("さらに読み込む", onLoadMore)
        PagingState.LoadingMore -> FooterText("読み込んでいます…")
        is PagingState.LoadMoreError -> Column {
            FooterText(paging.message)
            FooterAction("再試行", onLoadMore)
        }
        PagingState.ApiLimitReached -> FooterText("これ以上は取得できません（APIの取得上限に達しました）")
        PagingState.Complete -> Unit
    }
}

@Composable
private fun FooterAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = RedCartridge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.S16, vertical = Spacing.S16),
    )
}

@Composable
private fun FooterText(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = InkMidCartridge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S16),
    )
}

// ============================================================
// CRT テクスチャ（.board::before / .demo::before＝微ドット＋走査線）。近黒 --ink の copy-alpha で暗化に置換。
// ============================================================
private fun DrawScope.drawCrt(scanlines: Boolean) {
    // 微ドット（3px 間隔）。
    val step = 3.dp.toPx()
    val r = 0.6.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(CrtDot, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
    // 走査線（3px ごとの水平暗線）。
    if (scanlines) {
        var ly = 0f
        while (ly < size.height) {
            drawLine(CrtScan, Offset(0f, ly), Offset(size.width, ly), strokeWidth = 1.dp.toPx())
            ly += step
        }
    }
}
