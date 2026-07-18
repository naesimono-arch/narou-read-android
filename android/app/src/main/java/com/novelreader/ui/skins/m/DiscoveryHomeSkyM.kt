package com.novelreader.ui.skins.m

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
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
import com.novelreader.ui.theme.AuthorInkSeizu
import com.novelreader.ui.theme.BrightStarSeizu
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.DustSeizu
import com.novelreader.ui.theme.GenreChipInkSeizu
import com.novelreader.ui.theme.MilkyWaySeizu
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.RubySeizu
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPreset
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import java.util.Locale

// ============================================================
// スキンM「星図」の発見＝夜空の観測（正本 discovery-M.html・ADR 0022 §1 の構造分岐先）。
//   ・発見ホーム DiscoveryHomeSkyM＝見出し「見つける」＋気分プリセット（夜空を覗く窓）＋ジャンル入口＋
//     期間タブ＋観測ランキング（星表）。地の夜空を大胆に（星屑110・天の川の淡帯・見出し背後の大星座）。
//   ・結果一覧 DiscoveryResultSkyM＝プリセット/ジャンル/検索の共通着地（back・文脈見出し・条件チップ・件数・星表）。
//
// 背景・Lcg は星図スキン共通の SkyCanvas.kt（本棚/目次と同一部品）を参照。discovery-M の背景は
// bookshelf/toc と同じ夜天3層（drawNightSky）に、この画面固有の天の川帯＋大星座（drawDiscoverySky）を重ねる。
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
private val LineAlpha = MoonSlateSeizu.copy(alpha = 0.20f)        // --line rgba(150,168,214,.20)
private val MoodWindowBg = Color(0xFF0E1634).copy(alpha = 0.28f)  // .md 背景 rgba(14,22,52,.28)＝夜空を覗く窓
private val CdStarBorder = StarSeizu.copy(alpha = 0.4f)          // .cd border rgba(233,221,180,.4)
private val CdStarInk = StarSeizu.copy(alpha = 0.9f)            // .cd（opacity .9＝星光文字を僅かに沈める）
private val WindowStar = StarSeizu.copy(alpha = 0.5f)          // .md .win 結線 rgba(233,221,180,.5)

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
    // 背景フィールド（星屑110＋大星座）は draw 段で毎回 Lcg/座標再生成せず 1 回だけ決定的に生成し remember
    // で保持する（DeepSkyM の「決定的1回生成」基準に統一。座標は 0..1 正規化で size 非依存）。
    val skyField = remember { buildDiscoverySkyField() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawNightSky()            // 夜天3層（星図スキン共通）
                drawDiscoverySky(skyField) // 天の川の淡帯＋星屑110＋見出し背後の大星座（静止1回）
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // .top: 見出し＋検索。戻る（← 本棚へ）はモック省略の D 機能を欠落させず M 意匠で先頭へ写す。
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
                IconButton(onClick = onOpenSearch) {
                    // 用語辞書（着地画面名「探す」）に合わせる＝D 発見の検索アイコンと同一 accessible name。
                    Icon(Icons.Filled.Search, contentDescription = "探す", tint = TextSeizu)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S40, // .scroll padding 0 26 40
                ),
            ) {
                item { SkySectionLabel("きょうの気分", topSpace = Spacing.S16) } // 先頭 .sec margin-top 18px
                item { MoodGridSky(onPickMood) }
                item { SkySectionLabel("ジャンルから", topSpace = Spacing.S32) } // .sec margin 28px
                item { GenreChipsSky(onOpenGenre, onPickBiggenre) }
                item { OrderTabsSky(order, onSelectOrder) }

                when (val s = state) {
                    is DiscoveryUiState.Loading -> item { SkyStatusLine("観測しています…") }
                    is DiscoveryUiState.Empty -> item { SkyStatusLine("作品が見つかりませんでした") }
                    is DiscoveryUiState.Error -> item { SkyErrorLine(s.message, onRefresh) }
                    is DiscoveryUiState.Content -> itemsIndexed(
                        s.novels,
                        key = { index, novel -> novel.ncode ?: index },
                    ) { index, novel ->
                        SkyRankRow(
                            rank = index + 1,
                            novel = novel,
                            order = order,
                            onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                        )
                    }
                }
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
    onBack: () -> Unit,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onChangeOrder: (NarouOrder) -> Unit,
    onChangeGenreFilter: (biggenres: Set<Int>, genres: Set<Int>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val skyField = remember { buildDiscoverySkyField() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawNightSky()
                drawDiscoverySky(skyField)
            },
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
            ResultCondsSky(ctx, onChangeOrder, onChangeGenreFilter, onBack)

            when (val s = state) {
                is DiscoveryUiState.Loading -> SkyStatusLine("観測しています…")
                is DiscoveryUiState.Empty -> ResultEmptySky(ctx.source, onAdjust = onBack, onBackToDiscovery = onUp)
                is DiscoveryUiState.Error -> SkyErrorLine(s.message, onRefresh)
                is DiscoveryUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
    val presets = MoodPreset.entries
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
    Box(
        modifier = modifier
            .heightIn(min = 82.dp)      // .md min-height 82px（呼吸ぶんの下限）
            .clip(RoundedCornerShape(12.dp))
            .background(MoodWindowBg)
            .border(1.dp, LineAlpha, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.S16),      // .md padding 18px 16px 16px → S16
    ) {
        // 小星座片（.md .win）＝右上に浮かべる。プリセット序数ごとに正本の3点配置を写す。
        MoodWindow(preset.ordinal, modifier = Modifier.align(Alignment.TopEnd))
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
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
    novel: NarouNovel,
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
                novel.title ?: "（無題）",
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
                    novel.writer ?: "",
                    fontSize = 11.sp,       // .rk .a 11px
                    color = AuthorInkSeizu,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                NarouGenres.genreLabel(novel.genre)?.let { genre ->
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
                pointLabel(order, novel)?.let {
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
            // 「条件を変更」で戻る先は検索画面＝SEARCH のみ（他発だと戻り先に条件シートが無く騙し導線・D と同判定）。
            CondChip("条件を変更", adjustable = true, onClick = onBack)
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

// ============================================================
// 発見固有の夜空（モック .skybg <script>: 天の川の淡帯＋星屑110＋見出し背後の大星座）。
// 静止1回描画。座標は mock 390×844 を実サイズへ比例写像（TocSkyM.drawTocSky と同作法）。
// ============================================================
private fun DrawScope.drawDiscoverySky(field: DiscoverySkyField) {
    val w = size.width
    val h = size.height
    val d = 1.dp.toPx()
    fun p(mx: Float, my: Float) = Offset(mx / 390f * w, my / 844f * h)

    // 天の川の淡い帯（linearGradient(0,80→390,300)・.5 で α .06・fillRect(0,60,390,240)）。
    val bandTL = p(0f, 60f)
    val bandBR = p(390f, 300f)
    drawRect(
        Brush.linearGradient(
            0f to MilkyWaySeizu.copy(alpha = 0f),
            0.5f to MilkyWaySeizu.copy(alpha = 0.06f),
            1f to MilkyWaySeizu.copy(alpha = 0f),
            start = p(0f, 80f), end = p(390f, 300f),
        ),
        topLeft = bandTL,
        size = androidx.compose.ui.geometry.Size(bandBR.x - bandTL.x, bandBR.y - bandTL.y),
    )

    // 星屑110点（seed 13579）＝remember 済みの正規化座標を size/d で復元して描く（生成時と等価）。
    for (s in field.dust) {
        drawCircle(DustSeizu.copy(alpha = s.alpha), radius = s.rMul * d, center = Offset(s.fx * w, s.fy * h))
    }

    // 見出し背後の大きな淡い星座（CONST 5点＝結線 rgba(150,168,214,.12)＋各点に淡いグロー r4 α.18）。
    // 正規化 5 点は remember 済み・Path は size 依存のため draw で復元（toc の litPath/basePath と同作法）。
    val cpts = field.constellation.map { Offset(it.x * w, it.y * h) }
    val cpath = Path().apply {
        moveTo(cpts[0].x, cpts[0].y)
        for (i in 1 until cpts.size) lineTo(cpts[i].x, cpts[i].y)
    }
    drawPath(cpath, MoonSlateSeizu.copy(alpha = 0.12f), style = Stroke(width = 1f))
    cpts.forEach { drawDiscoveryGlow(it, 4f * d, 0.18f) }
}

/** 発見固有の夜空フィールド（星屑110＋大星座5点）。draw 内の Lcg/座標再生成を避け remember で1回だけ生成する（DeepSkyM と同型）。 */
internal class DiscoveryStarDust(val fx: Float, val fy: Float, val rMul: Float, val alpha: Float)
internal class DiscoverySkyField(val dust: List<DiscoveryStarDust>, val constellation: List<Offset>)

/**
 * 発見の夜空フィールドを決定的に1回だけ生成（正本 discovery-M.html・seed 13579）。星屑の Lcg 消費順（band→x→y→r→a）を
 * 保ち座標等価。位置は Canvas size 依存のため 0..1 正規化で持つ（fx=x/390＝rnd, fy=y/844）＝端末非依存。半径係数 rMul は draw で *d。
 * 大星座 CONST 5点は固定座標を 0..1 正規化で保持（draw で size 乗算・Path 復元）。
 */
internal fun buildDiscoverySkyField(): DiscoverySkyField {
    val rnd = Lcg(13579)
    val dust = List(110) {
        val band = rnd.next() < 0.5f
        val fx = rnd.next()                                                          // x=rnd*390 → 描画時 fx*w
        val fy = (if (band) 60f + rnd.next() * 230f else 44f + rnd.next() * (844f - 70f)) / 844f
        val rMul = rnd.next() * 1f + 0.3f                                            // 描画時 rMul*d
        val alpha = rnd.next() * (if (band) 0.4f else 0.28f) + 0.05f
        DiscoveryStarDust(fx, fy, rMul, alpha)
    }
    // CONST 5点（正本固定座標）を 0..1 正規化で保持（mx/390, my/844）。
    val constellation = listOf(
        Offset(70f / 390f, 150f / 844f), Offset(130f / 390f, 120f / 844f),
        Offset(200f / 390f, 168f / 844f), Offset(268f / 390f, 132f / 844f),
        Offset(320f / 390f, 180f / 844f),
    )
    return DiscoverySkyField(dust, constellation)
}

/** 見出し背後の星グロー（モック glow(): radial 2停止＝星光 α→0 ＋星芯 1.4px）。bookshelf の starGlow と別式。 */
private fun DrawScope.drawDiscoveryGlow(center: Offset, radius: Float, a: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to StarSeizu.copy(alpha = a),   // rgba(233,221,180,a)
            1f to StarSeizu.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
    drawCircle(StarCoreSeizu.copy(alpha = (a + 0.2f).coerceAtMost(1f)), radius = 1.4f * 1.dp.toPx(), center = center)
}
