package com.novelreader.ui.skins.m

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.NewEpisodeNotificationMenuSection
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.FaintStarSeizu
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.OnStarSeizu
import com.novelreader.ui.theme.PanelSeizu
import com.novelreader.ui.theme.ResumeGradEndSeizu
import com.novelreader.ui.theme.ResumeGradStartSeizu
import com.novelreader.ui.theme.RubySeizu
import com.novelreader.ui.theme.SeizuIdGreen
import com.novelreader.ui.theme.SeizuIdPurple
import com.novelreader.ui.theme.SeizuIdRose
import com.novelreader.ui.theme.SeizuIdSlate
import com.novelreader.ui.theme.SkyGradEndSeizu
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarGlowInnerSeizu
import com.novelreader.ui.theme.StarGlowOuterSeizu
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

// ============================================================
// スキンM「星図」の本棚＝一枚の夜天図（正本 bookshelf-M.html・ADR 0022 §1 の構造分岐先）。
//
// モックとの構造差（固定4作×絶対座標 → N作×スクロール）の翻訳方針:
//   ・モックは 390×844 固定天球に4星座を手置きしている。実蔵書は N 冊なので「1作=1星座セル」の
//     LazyColumn に翻訳し、経緯線・星屑・境界線はセル単位で周期描画して連続する夜天に見せる
//     （水平経緯線の間隔=セル高≒150dp はモックの 150px 間隔と同周期）。
//   ・星座境界線の段差（x150/240 の縦区間）はテキスト回避のための手置き＝セル分割が同じ役割を
//     果たすため、セル境界の「弧の破線」1本に簡約する（図法の文法＝水平は中点 -6 の弧、は維持）。
//   ・「第127話 ·」の nowtag（先端星への座標依存注記）は可変レイアウトでは星と衝突するため省略
//     （話数はラベルの prog 行が持つ＝情報欠落なし）。
//   ・学名（rex umbrae 等の演出テキスト）は実データに存在しない＝捏造しない。desig 行は
//     識別色ドット＋ncode（実データのラテン文字）で翻訳し、ncode 未紐付けはドットのみ。
//   ・空状態はモック未定義＝最小限の一文のみ（人間検収でモック追補要否を判断）。
//
// 機能全数の所在: 選択削除・グリッドは「一覧」フォールバック（bookshelf-M.html の 星図⇄一覧 トグル）側が
// 担う（D 構造へトークン写像＝ADR 0021）。未取込 Web カードは地平の発見導線内カウント（モック .uncoll）。
//
// モーション: hero 先端星の脈動のみ（「現在地の脈動」類型＝ADR 0022 §3）。reduce-motion
// （アニメーター無効設定）では pulse=0.55 固定の静止描画＝モックの reduce 分岐と同値。
//
// 字面: 構造画面専用の px 値は正本モックの font-size を 1:1 で sp へ写す（各行にモック由来コメント）。
// 共有 Typography トークン化しないのは ADR 0022 §5 と同じ倹約則（このスキンのこの画面しか使わない）。
// ============================================================

// ---- 描画層の透過色（グラデ地の上へ層で載るため焼き込めず .copy(alpha=) で正本 α を付与）----
private val ChipOnBorder = StarSeizu.copy(alpha = 0.5f)          // .chip.on border rgba(233,221,180,.5)
private val LineAlpha = MoonSlateSeizu.copy(alpha = 0.2f)        // --line rgba(150,168,214,.2)
private val PanelTranslucent = Color(0xFF0E1634).copy(alpha = 0.5f)   // .banner rgba(14,22,52,.5)
private val DiscoverTranslucent = Color(0xFF0E1634).copy(alpha = 0.42f) // .discover rgba(14,22,52,.42)
private val TrackAlpha = MoonSlateSeizu.copy(alpha = 0.25f)      // .banner .track rgba(150,168,214,.25)

// ---- ラベルの較正色（bookshelf-M .const の直書き値。この画面専用＝ADR 0022 §5 の in-file 集約）----
private val BySeizu = Color(0xFF818BA6)          // .const .by
private val LinkSeizu = Color(0xFFAEB7D2)        // .const .link
private val UnreadTitleSeizu = Color(0xFFAEB6CE) // .const.unread .ttl
private val UnreadProgSeizu = Color(0xFF7B85A1)  // .const.unread .prog
private val BadgeBorderSeizu = Color(0xFF303B5C) // .const .badge border
private val WelcomeInkSeizu = Color(0xFFB7C0DB)  // .welcome color

// 極微視差の係数（正本 R1 FACTOR 0.08＝0.03〜0.08 の上限＝知覚可能な最小）。遠景の天の川粒帯のみ連動。
private const val ParallaxFactor = 0.08f

/** 識別色（学名ドット）: 作品ごとに安定して同じ色が付くよう id ハッシュで引く（並び替えで変わらない）。 */
private val SeizuIdPalette = listOf(SeizuIdGreen, SeizuIdPurple, SeizuIdSlate, SeizuIdRose)
private fun idColorFor(bookId: String): Color =
    SeizuIdPalette[(bookId.hashCode() and 0x7fffffff) % SeizuIdPalette.size]

// Lcg（線形合同法）は星図スキン共通部品として ui/skins/m/SkyCanvas.kt へ抽出（目次 TocSkyM と共有・二重定義排除）。

@Composable
internal fun BookshelfSkyM(
    books: List<BookEntity>,
    progressMap: Map<String, ProgressEntity>,
    chapterCountMap: Map<String, Int>,
    newEpisodeNovelMap: Map<String, NarouNovel>,
    processingState: ProcessingState,
    webNovelCount: Int,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    onSelectStatus: (ReadingStatus?) -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onFabClick: () -> Unit,
    onToggleList: () -> Unit,
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
) {
    // reduce-motion: アニメーター無効（開発者設定/省電力のスケール0）を尊重して脈動を静止させる
    //（ADR 0022 §3 の必須条件。モックの prefers-reduced-motion 分岐＝pulse 0.55 固定と同値）。
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 脈動位相（周期 8796ms ≒ モック sin(ts/1400) の 2π×1400）。draw 段でだけ読ませるため
    // ラムダで渡す（コンポーズ再実行をフレーム毎に走らせない＝deferred read）。
    // reduce 時は無限アニメ自体を作らない（静止値のみ＝モックの rAF 停止と同値・電池も浪費しない）。
    val phase = if (reduceMotion) null else rememberInfiniteTransition(label = "skyPulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8796, easing = LinearEasing), RepeatMode.Restart),
        label = "skyPulsePhase",
    )
    val pulse: () -> Float = {
        if (phase == null) 0.55f else (sin(phase.value * 2f * PI.toFloat()) + 1f) / 2f
    }

    // 状態フィルタ適用後の可視作品（チップは D と同じ readingStatusFor を単一真実源に使う）。
    val visible = remember(books, progressMap, chapterCountMap, selectedStatus) {
        if (selectedStatus == null) books
        else books.filter { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == selectedStatus }
    }
    // hero＝いま読みかけの一作（二層ソート済みリストの先頭の「よみかけ」＝モックの「続きから」）。
    val heroId = remember(visible, progressMap, chapterCountMap) {
        visible.firstOrNull { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == ReadingStatus.READING }?.id
    }
    // 銘の meta「結ばれた星座 n / m」＝進捗が結ばれた（未読でない）作品数 / 全作品数。
    val boundCount = remember(books, progressMap, chapterCountMap) {
        books.count { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) != ReadingStatus.UNREAD }
    }

    // ---- 深空（R1）レイヤーの素材 ----
    // z0/z1 の固定フィールド（星雲・天の川粒帯・散開微星）は蔵書非依存の不変の地。remember{}（キー無し）で
    // 1 コンポジション 1 回だけ生成＝固定 seed と相まって再コンポーズでも星が一切踊らない（フレーム毎再計算もしない）。
    val deepSkyField = remember { buildDeepSkyField() }
    // 読了星の累積（z0）＝末尾到達実績（reachedEnd→FINISHED）の作品だけ深空へ着地星として静的に累積表示。
    // 位置は作品 id ハッシュから決定的に導く（＝同じ作品は常に同じ場所に着地・並び替えで動かない）。
    // TODO(監督): 昇華アニメ（読了イベントで先端星が深空へ昇る z2 演出）のトリガ配線。この Composable には
    //   「いま読了した」イベント流入が無いため未実装＝近似で嘘のアニメを出さず、静的累積のみで正直に留める。
    val finishedStars = remember(books, progressMap, chapterCountMap) {
        books.filter { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == ReadingStatus.FINISHED }
            .map { b ->
                val seed = b.id.hashCode() and 0x7fffffff
                val total = chapterCountMap[b.id] ?: 0
                FinishedStar(
                    fx = 0.08f + 0.84f * ((seed % 1000) / 1000f),
                    fy = 0.114f + 0.355f * (((seed / 1000) % 1000) / 1000f), // モック ty=96+seed*300 の割合帯（上部）
                    mag = (0.4f + total / 500f).coerceAtMost(1f),
                    color = idColorFor(b.id),
                )
            }
    }
    // 遠景の極微視差は LazyColumn のスクロールへ連動させる＝state を hoist して graphicsLayer 側でも読む。
    val listState = rememberLazyListState()

    // 固定地平バーの実高を測ってスクロール下端クリアランスに充てる（ナビバー高が機種で変わるため定数では
    // 足りず、最後の星座セルがバー裏に残った＝実機の指摘。測ってバー高そのぶん空ければ機種非依存で解決）。
    // 初回レイアウト前は Insets.SkyHorizonClearance を暫定値に（120dp ≒ 地平実高で見た目の跳ねなし）。
    val density = LocalDensity.current
    var horizonHeightPx by remember { mutableStateOf(0) }
    val horizonClearance = if (horizonHeightPx > 0) with(density) { horizonHeightPx.toDp() }
    else Insets.SkyHorizonClearance

    // 極微視差の translate 供給。LazyList は可変高セルゆえピクセル絶対オフセットを持たないので、代表セル高
    //（150dp）×index＋先頭可視セルのオフセットをスクロール信号にする（背景の最大 translate 40dp・hero=200 と
    // others=150 の差は知覚下＝背景としては十分。翻訳の割り切りは報告事項に記載）。上限 40dp でクランプ（遠景の
    // buffer 60dp 未満＝下端に隙間を出さない）。この lambda は graphicsLayer ブロック内でだけ呼ぶ＝スクロール state を
    // 描画フェーズで遅延読み＝スクロール毎の再コンポーズを起こさない（chrisbanes deferred-read）。
    val nominalCellPx = with(density) { 150.dp.toPx() }
    val maxParallaxPx = with(density) { 40.dp.toPx() }
    val parallaxProvider: () -> Float = {
        if (reduceMotion) 0f // reduce-motion: 視差 0（完全静止）
        else {
            val raw = listState.firstVisibleItemIndex * nominalCellPx + listState.firstVisibleItemScrollOffset
            (raw * ParallaxFactor).coerceAtMost(maxParallaxPx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // .phone 背景: 群青の夜天グラデ＋右上の青雲＋下辺の地平光（星図スキン共通＝SkyCanvas.drawNightSky）。
                drawNightSky()
            },
    ) {
        // ---- R1 深空レイヤー（前景 Column より前＝背後に敷く。いずれも pointer 非介入で下のスクロール/導線へ素通し）----
        // z0 深空（固定・スクロール非追従）＝星雲＋アクセント星＋読了星の累積。drawBehind へ一度確定描画（スクロール state を
        // 読まないのでスクロール中に再描画されない）。夜天グラデ（root drawBehind）の直上・粒帯や星座より背面。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawDeepSky(deepSkyField, finishedStars) },
        )
        // z1 遠景視差＝天の川の粒帯＋散開微星。graphicsLayer{translationY} で極微視差（translate 供給は描画フェーズ遅延読み）。
        // drawBehind の描画自体はスクロール state を読まない＝レイヤーへ一度記録され、以後は transform だけ動く（recomposition 増やさない）。
        // clip=true で buffer ぶん（size 下端より下）を隠し、上へずらしたときだけ滑り込ませる。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -parallaxProvider()
                    clip = true
                }
                .drawBehind { drawFarStars(deepSkyField) },
        )
        // z2 演出オーバーレイ＝まれな流れ星（30〜70秒に一度・一度に一筋・淡い遠景の一筋）。reduce-motion では非表示。
        // 前景 Column より背面＝銘/星座の背後を流れる遠景の一筋（モック .ascend z2 と同順）。
        MeteorOverlay(reduceMotion = reduceMotion, modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SkyPlate(
                boundCount = boundCount,
                totalCount = books.size,
                onOpenDiscovery = onOpenDiscovery,
                onToggleList = onToggleList,
                onOpenWardrobe = onOpenWardrobe,
            )
            // 取込中バナー（PDFを星に変換中）＝非スクロール・銘直下（モック .banner）。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                SkyProcessingBanner(processingState, onCancelProcessing)
            }
            SkyChips(selectedStatus, statusCounts, onSelectStatus)

            LazyColumn(
                state = listState, // 遠景視差が読むためスクロール state を hoist（上の parallaxProvider）。
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 下端は地平（発見導線＋迎える）ぶんを空ける＝スクロール末尾の星座が地平に沈まない。
                // クリアランスは固定バーの実測高（下記 onSizeChanged）＝バー高そのぶん確保。
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = horizonClearance),
            ) {
                items(visible, key = { it.id }) { book ->
                    val index = visible.indexOf(book)
                    ConstellationCell(
                        book = book,
                        progress = progressMap[book.id],
                        totalChaps = chapterCountMap[book.id] ?: 0,
                        novelDetail = book.ncode?.let { newEpisodeNovelMap[it] },
                        isHero = book.id == heroId,
                        labelOnLeft = index % 2 == 0,
                        zoneIndex = index,
                        pulse = pulse,
                        onOpen = { onOpenBook(book) },
                    )
                }
                if (!isLoading && visible.isEmpty()) {
                    item {
                        // 空状態はモック未定義＝最小の一文（発明を最小化・人間検収でモック追補要否を判断）。
                        Text(
                            text = if (selectedStatus == null) "夜空にまだ星がない" else "この空には該当する星がない",
                            fontFamily = MinchoFamily,
                            fontSize = 14.sp,
                            color = DimSeizu,
                            modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S40),
                        )
                    }
                }
            }
        }

        // 固定バー背後の夜天スクリム: 実データでは星座セルがスクロールで固定地平バーの裏を通過し、
        // 半透明地（.discover .42）越しに本文ラベルが透けて両方読みにくくなる（モックは絶対座標で衝突を
        // 避けるが実データ数では成立しない）。バー高＋上端の短いフェードぶんを夜天終端色へ沈めて透け重なりを
        // 断つ（色は背景下端と同色の SkyGradEndSeizu＝地平と地続きに見え、モックの「地平に沈む」語彙とも整合）。
        // スクリムはバー footprint（実測高）にちょうど重ねる＝バー直上の hero CTA 等は覆わず、
        // バー裏へ潜ったぶんだけを沈める。上端の稜線ぶん（≒S12 パディング帯）だけ短くフェードさせ、
        // 発見パネルが始まる位置ではもう不透明に達しておき本文の透けを完全に断つ。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(horizonClearance)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.14f to SkyGradEndSeizu.copy(alpha = 0.85f), // 稜線ぶんの短いソフトフェード
                            0.3f to SkyGradEndSeizu,                      // パネル帯以降は不透明
                            1f to SkyGradEndSeizu,
                        )
                    )
                },
        )

        SkyHorizon(
            webNovelCount = webNovelCount,
            onOpenDiscovery = onOpenDiscovery,
            onFabClick = onFabClick,
            // 実高を測ってスクロール下端クリアランス（上記）へ反映する。
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { horizonHeightPx = it.height },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = horizonClearance), // 地平の上へ逃がす構造クリアランス（実測高）
        )
    }
}

// ============================================================
// 銘＋操作クラスタ（モック .plate: 見つける/一覧切替/装いの間/メニュー）
// ============================================================
@Composable
private fun SkyPlate(
    boundCount: Int,
    totalCount: Int,
    onOpenDiscovery: () -> Unit,
    onToggleList: () -> Unit,
    onOpenWardrobe: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S4),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.S4, top = Spacing.S4)) {
            Text(
                "本棚",
                fontFamily = MinchoFamily,
                fontSize = 20.sp,              // .plate .name 20px
                letterSpacing = 0.22.em,       // .22em
                fontWeight = FontWeight.Medium,
                color = TextSeizu,
            )
            Text(
                "結ばれた星座 $boundCount / $totalCount",
                fontSize = 10.sp,              // .plate .meta 10px
                letterSpacing = 0.12.em,
                color = DimSeizu,
                modifier = Modifier.padding(top = Spacing.S4),
            )
        }
        IconButton(onClick = onOpenDiscovery) {
            Icon(Icons.Filled.Search, contentDescription = "見つける", tint = DimSeizu)
        }
        IconButton(onClick = onToggleList) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "一覧表示に切替", tint = DimSeizu)
        }
        IconButton(onClick = onOpenWardrobe) {
            // 装いの間だけ星光でほのめかす（モック .ib.wardrobe＝着せ替え入口はこの画面の「別の空」への扉）。
            FourPointStar(color = StarSeizu, modifier = Modifier.size(19.dp))
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー", tint = DimSeizu)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // M は固定1変種＝テーマ節を出さない（settings-M のテーマ固定表示と同思想・ADR 0022 §2）。
                NewEpisodeNotificationMenuSection()
            }
        }
    }
}

/** 装いの間の4条星アイコン（モック .ib.wardrobe の SVG パスを DrawScope へ写像）。 */
@Composable
private fun FourPointStar(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        val p = Path().apply {
            moveTo(12f * s, 2.5f * s)
            lineTo(13.7f * s, 10.3f * s); lineTo(21.5f * s, 12f * s)
            lineTo(13.7f * s, 13.7f * s); lineTo(12f * s, 21.5f * s)
            lineTo(10.3f * s, 13.7f * s); lineTo(2.5f * s, 12f * s)
            lineTo(10.3f * s, 10.3f * s); close()
        }
        drawPath(p, color)
    }
}

// ============================================================
// 取込中バナー（モック .banner: 灯りかけの星＋題名＋進捗トラック）
// ============================================================
@Composable
private fun SkyProcessingBanner(state: ProcessingState, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8)
            .clip(RoundedCornerShape(12.dp))
            .background(PanelTranslucent)
            .border(1.dp, LineAlpha, RoundedCornerShape(12.dp))
            .padding(Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
    ) {
        // 灯りかけの星（.kindle）＝radial の光球。
        Box(
            modifier = Modifier.size(14.dp).drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(StarCoreSeizu, StarSeizu.copy(alpha = 0.2f), Color.Transparent),
                    )
                )
            }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (state.isStopping) "停止しています…" else "『${state.title}』を星に変換中",
                fontSize = 11.sp,              // .banner .l 11px
                color = TextSeizu,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val overall = ((state.stepIndex + state.stepLocalPercent) / state.stepTotal).coerceIn(0f, 1f)
            Text(
                state.phase + if (state.queueTotal > 1) " · ${state.queueCurrent}/${state.queueTotal}件" else "",
                fontSize = 9.5.sp,             // .banner .s 9.5px
                color = DimSeizu,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.S4),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.S8)
                    .height(2.dp)
                    .background(TrackAlpha, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(overall)
                        .height(2.dp)
                        .background(StarSeizu, RoundedCornerShape(2.dp)),
                )
            }
        }
        // 停止（モック外の機能ボタン＝D バナーの onStop と機能同数を守る。星図では沈めた文字リンクで）。
        if (!state.isStopping) {
            Text(
                "停止",
                fontSize = 11.sp,
                color = DimSeizu,
                modifier = Modifier.clickable(onClick = onCancel).padding(Spacing.S4),
            )
        }
    }
}

// ============================================================
// 状態フィルタチップ（モック .chips: すべて既定・横スクロール。実データのフィルタは読書状態＝D と同一機能）
// ============================================================
@Composable
private fun SkyChips(
    selected: ReadingStatus?,
    counts: Map<ReadingStatus, Int>,
    onSelect: (ReadingStatus?) -> Unit,
) {
    val entries: List<Pair<ReadingStatus?, String>> = listOf(
        null to "すべて",
        ReadingStatus.READING to "よみかけ",
        ReadingStatus.UNREAD to "未読",
        ReadingStatus.FINISHED to "読了",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        entries.forEach { (status, label) ->
            val isOn = selected == status
            val isEmpty = status != null && (counts[status] ?: 0) == 0
            Text(
                text = label,
                fontSize = 11.sp,              // .chip 11px
                color = when {
                    isOn -> StarSeizu
                    isEmpty -> DimSeizu.copy(alpha = 0.5f) // 0件はさらに沈める（D の 0件 dim と同機能）
                    else -> DimSeizu
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, if (isOn) ChipOnBorder else LineAlpha, RoundedCornerShape(999.dp))
                    .clickable { onSelect(status) }
                    .padding(horizontal = Spacing.S12, vertical = Spacing.S8),
            )
        }
    }
}

// ============================================================
// 星座セル（1作=1星座）: 経緯線・星屑・境界線・結線・星光を canvas に、題字ラベルを傍らに。
// ============================================================
@Composable
private fun ConstellationCell(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    novelDetail: NarouNovel?,
    isHero: Boolean,
    labelOnLeft: Boolean,
    zoneIndex: Int,
    pulse: () -> Float,
    onOpen: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val status = readingStatusFor(progress, totalChaps)
    val isUnread = status == ReadingStatus.UNREAD
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)
    val idColor = idColorFor(book.id)
    val cellHeight = if (isHero) 200.dp else 150.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .clickable(onClick = onOpen)
            .drawBehind {
                drawSkyCell(
                    seed = book.id.hashCode(),
                    frac = (frac ?: 0f).coerceIn(0f, 1f),
                    isUnread = isUnread,
                    isHero = isHero,
                    labelOnLeft = labelOnLeft,
                    fillZone = zoneIndex % 2 == 1,
                    pulse = pulse(),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .align(if (labelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .width(216.dp)
                .padding(horizontal = Spacing.S24),
            horizontalAlignment = if (labelOnLeft) Alignment.Start else Alignment.End,
        ) {
            if (isHero) {
                // 続きから＝画面唯一の強調（.const.hero .eyebrow）。
                Text(
                    "◈ 続きから",
                    fontSize = 10.5.sp,        // .eyebrow 10.5px
                    letterSpacing = 0.34.em,
                    color = StarSeizu,
                    modifier = Modifier.padding(bottom = Spacing.S8),
                )
            } else {
                // desig＝識別色ドット＋ncode（学名の演出テキストは実データに無いため捏造しない）。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(idColor))
                    book.ncode?.let {
                        Text(
                            it.lowercase(),
                            fontSize = 9.5.sp, // .desig 9.5px
                            letterSpacing = 0.16.em,
                            fontStyle = FontStyle.Italic,
                            color = idColor,
                            modifier = Modifier.padding(start = Spacing.S8),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.S8))
            }
            Text(
                book.title,
                fontFamily = MinchoFamily,
                fontSize = if (isHero) 17.sp else 14.sp,   // hero .ttl 17px / .ttl 14px
                lineHeight = if (isHero) 24.sp else 21.sp, // 1.42 / 1.5 行送りの近似
                fontWeight = if (isHero) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isUnread) UnreadTitleSeizu else TextSeizu,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    fontSize = 10.5.sp,        // .by 10.5px
                    color = BySeizu,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.S4),
                )
            }
            Text(
                text = when {
                    isUnread -> "未読 · 全${totalChaps}話　まだ星は結ばれていない"
                    else -> "第${chapNum ?: 1}話 / 全${totalChaps}話 · ${((frac ?: 0f) * 100).toInt()}%"
                },
                fontSize = 10.5.sp,            // .prog 10.5px
                color = if (isUnread) UnreadProgSeizu else RubySeizu, // .prog #9AA4C0＝RubySeizu と同値
                modifier = Modifier.padding(top = Spacing.S8),
            )
            if (newCount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.S8),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(StarSeizu))
                    Text(
                        "新着 · 続き${newCount}話",
                        fontSize = 10.sp,      // .upd 10px
                        color = StarSeizu,
                        modifier = Modifier.padding(start = Spacing.S4),
                    )
                }
            }
            when {
                isHero -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = Spacing.S12)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(ResumeGradStartSeizu, ResumeGradEndSeizu)))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnStarSeizu, modifier = Modifier.size(14.dp))
                    Text(
                        "この星から読む",
                        fontSize = 12.sp,      // .resume 12px
                        fontWeight = FontWeight.ExtraBold,
                        color = OnStarSeizu,
                        modifier = Modifier.padding(start = Spacing.S4),
                    )
                }
                isUnread -> Text(
                    "最初の星を灯す",
                    fontSize = 9.sp,           // .badge 9px
                    letterSpacing = 0.18.em,
                    color = DimSeizu,
                    modifier = Modifier
                        .padding(top = Spacing.S8)
                        .border(1.dp, BadgeBorderSeizu, RoundedCornerShape(3.dp))
                        .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                )
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.S8),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = LinkSeizu, modifier = Modifier.size(10.dp))
                    Text(
                        "続きを結ぶ",
                        fontSize = 10.5.sp,    // .link 10.5px
                        color = LinkSeizu,
                        modifier = Modifier.padding(start = Spacing.S4),
                    )
                }
            }
        }
    }
}

/** 星座セルの canvas 描画（経緯線・星屑・境界線・結線・星光）。値の正本＝bookshelf-M.html <script>。 */
private fun DrawScope.drawSkyCell(
    seed: Int,
    frac: Float,
    isUnread: Boolean,
    isHero: Boolean,
    labelOnLeft: Boolean,
    fillZone: Boolean,
    pulse: Float,
) {
    val rnd = Lcg(seed)
    val w = size.width
    val h = size.height
    val d = 1.dp.toPx()

    // ゾーン塗り（.026）＝1セルおきに極淡のリフト（モック ZONEFILL の帯を1セル単位へ写像）。
    if (fillZone) drawRect(MoonSlateSeizu.copy(alpha = 0.026f))

    // 経緯線: 縦線は x=60+i*90（モック grid()と同 x・全高直線）。水平弧はセル下端に1本＝間隔150dp 周期。
    for (i in 0 until 4) {
        val x = (60 + i * 90) * d
        if (x < w) drawLine(MoonSlateSeizu.copy(alpha = 0.055f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
    }
    val gridPath = Path().apply {
        moveTo(6 * d, h - 2 * d)
        quadraticBezierTo(w / 2f, h - 2 * d - 16 * d, w - 6 * d, h - 2 * d)
    }
    drawPath(gridPath, MoonSlateSeizu.copy(alpha = 0.055f), style = Stroke(width = 1f))

    // 星座境界線＝セル境界の弧の破線（水平は中点 -6 の弧＝モック boundsPath の文法。段差はセル分割が代替）。
    val boundPath = Path().apply {
        moveTo(0f, h - 1f)
        quadraticBezierTo(w / 2f, h - 1f - 6 * d, w, h - 1f)
    }
    drawPath(
        boundPath, MoonSlateSeizu.copy(alpha = 0.16f),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * d, 5 * d))),
    )

    // R1（深空）: 従前ここでセル毎に撒いていた星屑（DUST 12点）は撤去した。星屑・天の川・微星は
    // 深空レイヤー（DeepSkyM の z0/z1）へ整理統合済み＝前景から装飾星を抜き軽くする R1 の思想であり、
    // 深空フィールドと二重に撒くのを避ける（正本モック cap「前景から装飾星を抜き軽く」）。

    // 星座点列: ラベルと反対側の領域に seed 決定論で 5〜7点のジグザグを張る。
    val n = 5 + (rnd.next() * 3f).toInt().coerceAtMost(2)
    val marginY = 20 * d
    val regionLeft = if (labelOnLeft) w - 176 * d else 16 * d
    val regionRight = if (labelOnLeft) w - 16 * d else 176 * d
    val regionW = regionRight - regionLeft
    val pts = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val jitter = (rnd.next() - 0.5f) * 0.5f / (n - 1f)
        val x = regionLeft + ((t + jitter).coerceIn(0f, 1f)) * regionW
        val y = marginY + rnd.next() * (h - marginY * 2f - 8 * d)
        pts += Offset(x, y)
    }

    // 弧長を積み、frac ぶんだけ金の結線を先端補間で点灯（drawConst と同ロジック）。
    val segLens = FloatArray(pts.size - 1)
    var totalLen = 0f
    for (i in 1 until pts.size) {
        segLens[i - 1] = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        totalLen += segLens[i - 1]
    }
    val litLen = frac * totalLen

    // 下描き＝淡い結線（未読は破線 2,4・さらに淡く）。
    val basePath = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(
        basePath,
        FaintStarSeizu.copy(alpha = if (isUnread) 0.16f else 0.2f),
        style = Stroke(
            width = 1f,
            pathEffect = if (isUnread) PathEffect.dashPathEffect(floatArrayOf(2 * d, 4 * d)) else null,
        ),
    )

    var tip: Offset? = null
    if (litLen > 0f) {
        val litPath = Path().apply { moveTo(pts[0].x, pts[0].y) }
        var acc = 0f
        var tipPt = pts[0]
        for (i in 1 until pts.size) {
            if (acc + segLens[i - 1] <= litLen) {
                litPath.lineTo(pts[i].x, pts[i].y); acc += segLens[i - 1]; tipPt = pts[i]
            } else {
                val r = (litLen - acc) / segLens[i - 1]
                tipPt = Offset(
                    pts[i - 1].x + (pts[i].x - pts[i - 1].x) * r,
                    pts[i - 1].y + (pts[i].y - pts[i - 1].y) * r,
                )
                litPath.lineTo(tipPt.x, tipPt.y); break
            }
        }
        // shadowBlur の代替＝太→細の3層ストローク（外側ほど淡い金）でグローを近似する。
        drawPath(litPath, StarSeizu.copy(alpha = 0.15f), style = Stroke(4 * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(litPath, StarSeizu.copy(alpha = 0.35f), style = Stroke(2.4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(litPath, StarSeizu.copy(alpha = 0.72f), style = Stroke(1.4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        tip = tipPt
    }

    // 星点: 点灯済みはグロー・未点灯は淡星（faintStar rgba(150,166,206,.34)）。
    var acc2 = 0f
    for (i in pts.indices) {
        if (i > 0) acc2 += segLens[i - 1]
        val lit = litLen > 0f && acc2 <= litLen + 0.5f
        if (lit) drawStarGlow(pts[i], 4.4f * d, 0.9f) else {
            drawCircle(FaintStarSeizu.copy(alpha = 0.34f), radius = 1.5f * d, center = pts[i])
        }
    }
    // 先端星: hero は脈動（7+pulse*3.2）＝現在地の脈動類型（ADR 0022 §3）。他は静止 5.2。
    tip?.let {
        if (isHero) drawStarGlow(it, (7f + pulse * 3.2f) * d, 0.85f + pulse * 0.15f)
        else drawStarGlow(it, 5.2f * d, 0.8f)
    }
}

/** 星光グロー（starGlow: radial 3停止＋星芯 1.7px）。 */
private fun DrawScope.drawStarGlow(center: Offset, radius: Float, glow: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to StarGlowInnerSeizu.copy(alpha = glow.coerceAtMost(1f)),
            0.42f to StarGlowOuterSeizu.copy(alpha = (glow * 0.42f).coerceAtMost(1f)),
            1f to StarGlowOuterSeizu.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
    drawCircle(StarCoreSeizu.copy(alpha = (glow + 0.15f).coerceAtMost(1f)), radius = 1.7f * 1.dp.toPx(), center = center)
}

// ============================================================
// 下辺の地平（モック .horizon: 発見導線＋未取込カウント＋新しい星を迎える）
// ============================================================
@Composable
private fun SkyHorizon(
    webNovelCount: Int,
    onOpenDiscovery: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 地平線＝左右へ消えるヘアライン（.horizon::before）。
                val y = 0f
                drawLine(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to MoonSlateSeizu.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                    ),
                    Offset(20.dp.toPx(), y), Offset(size.width - 20.dp.toPx(), y),
                )
            }
            .navigationBarsPadding()
            .padding(top = Spacing.S12, bottom = Spacing.S8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.S16)
                .clip(RoundedCornerShape(14.dp))
                .background(DiscoverTranslucent)
                .border(1.dp, LineAlpha, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenDiscovery)
                .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
        ) {
            // 方位磁針（モック .cmp の円＋針を簡約した円＋4条星で「観測」を示す）。
            FourPointStar(color = StarSeizu, modifier = Modifier.size(19.dp))
            Text(
                "まだ知らない星を探しに",
                fontSize = 12.5.sp,            // .discover .dt .l 12.5px
                color = TextSeizu,
                modifier = Modifier.weight(1f),
            )
            if (webNovelCount > 0) {
                // なろう・未取込＝未収蔵の星のカウント（モック .uncoll。Web カード操作は一覧側が担う）。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(MoonSlateSeizu.copy(alpha = 0.5f)))
                    Text(
                        "なろう・未取込 $webNovelCount",
                        fontSize = 9.5.sp,     // .uncoll 9.5px
                        color = DimSeizu,
                        modifier = Modifier.padding(start = Spacing.S4),
                    )
                }
            }
            Text("›", fontSize = 14.sp, color = DimSeizu)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = Spacing.S4)
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onFabClick)
                .padding(horizontal = Spacing.S24, vertical = Spacing.S8),
        ) {
            // 光る＋（.welcome .plus）。
            androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                val d = 1.dp.toPx()
                drawLine(StarSeizu, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2 * d, cap = StrokeCap.Round)
                drawLine(StarSeizu, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2 * d, cap = StrokeCap.Round)
            }
            Text(
                "新しい星を迎える",
                fontSize = 11.5.sp,            // .welcome 11.5px
                letterSpacing = 0.1.em,
                color = WelcomeInkSeizu,
                modifier = Modifier.padding(start = Spacing.S8),
            )
        }
    }
}
