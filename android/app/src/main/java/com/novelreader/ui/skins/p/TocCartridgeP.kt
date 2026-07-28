package com.novelreader.ui.skins.p

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.FutureNodeCartridge
import com.novelreader.ui.theme.InkCartridge
import com.novelreader.ui.theme.InkMidCartridge
import com.novelreader.ui.theme.InkSoftCartridge
import com.novelreader.ui.theme.LcdBandCartridge
import com.novelreader.ui.theme.LcdCartridge
import com.novelreader.ui.theme.LcdHiCartridge
import com.novelreader.ui.theme.LcdInkCartridge
import com.novelreader.ui.theme.LineCartridge
import com.novelreader.ui.theme.PlasticCartridge
import com.novelreader.ui.theme.PlasticHiCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.tocInitialFirstVisibleIndex
import com.novelreader.ui.tocReadProgressPercent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ============================================================
// スキンP「カートリッジ」の目次＝携帯機のステージセレクト（正本 toc-P.html・はっちゃけ版・ADR 0022 §1 の構造分岐先）。
//
// 思想: 目次＝ステージセレクトのマップ。章は蛇行する道でつながったステージノード。
//   読了済み＝灯った道＋点いた緑ノード、現在の章＝道に立つドット絵の駒＋緑LCDの帯＋「▶ NOW」、未読＝暗い破線＋空ノード。
//   上部の緑LCD HUD が携帯機の現在地・全体進捗（STAGE/CLEAR）を出す。
//
// D 機能の全数移植（M の TocSkyM と同じ流儀）:
//   ・現在章ハイライト＝ .row.cur（緑バンド＋駒ノード＋▶NOW）。
//   ・既読/未読区別＝ done ノード（緑充填＋話数）/未読ノード（空＋話数）と、灯り道/暗い破線道で担う
//     （章題色は沈めない＝モック忠実。1ラベル1言葉の抑制）。
//   ・章タップで本文へ＝行 clickable → onSelectChapter(fileName)。戻る＝トップバー左の戻る → onNavigateToBookshelf。
//   ・話数カウンタ＝HUD の STAGE 現在/全 と CLEAR%。初期スクロール位置＝D/M と同じ tocInitialFirstVisibleIndex。
//   ・Loading/Empty/Error は M と同じく最小限（Empty はモック .empty に忠実・Loading/Error はモック未定義＝機体基調の最小文言）。
//
// 一画面一強調（ADR 0022 §3 相当の抑制）: 強調は現在章行の1点に集約する
//   ＝緑LCDバンド（.row.cur background）＋駒ノード＋▶NOW。done の緑ノードは「読了状態」であって強調ではない。
//   HUD は常時表示の機体クローム（現在地の読み取り値）で、強調として現在章と競合させない。
//
// モーション: P モックに keyframes/transition/JS は無い（ADR 0022 §3）＝完全静止で実装する。
//   脈動・自動再生を持たないため Motion.kt スロットの適用箇所も reduce-motion 分岐も無い
//   （現在地は「立つ駒」で示し点滅に頼らない＝モック cap の明示）。機能モーションが要るのは実機後詰め層（ADR 0005 §B）。
//
// タイポ裁定（本棚Pと同一）: 章題/ラベルは可読ゴシック（既定サンセリフ＝MinchoFamily 非適用）、
//   英数HUD・話数・状態タグは monospace（PixelFamily）。字面 sp はモック px を 1:1 で写す。
//
// 色: Cartridge val のみ（直書き禁止）。モックにあってトークン化されていない2値は近似せず、
//   最寄り val ＋ TODO で暫定し報告で列挙する（Color.kt はスコープ外＝当セッションでは追加しない）:
//     ①現在章バンド --lcd-band #ccd4a8 → LcdHiCartridge #b4be92 で暫定（知覚差あり・要トークン化）。
//     ②未読ノード地 #e6e2d6 → PlasticHiCartridge #e9e5da で暫定（Δ<3・知覚下）。
//   P の3テーマは読書面のみの変種＝目次クローム（筐体・緑LCD）はテーマ不変（ADR 0022 §2）ゆえ theme 非依存。
//
// 共通ヘルパー: ドット地（drawLcdDots）・機体下端（Deck）は CartridgePartsP.kt へ internal 集約済み＝当ファイルは参照のみ
//   （ドット色 LcdDotToc は当ファイルの正本 α を引数で渡す）。HUD ゲージは固定幅バー（.hud .g＝伸長型 SegGauge と別プリミティブ）で描く。
// ============================================================

// PixelFamily は package 共有部品へ集約（CartridgePartsP.kt の internal val）＝当ファイルからは参照のみ。

// ── 幾何定数（座標算術に参加＝Spacing スケール丸め対象外・mock px を 1:1 で dp 化。toc-P <script> の定数）──
private val RowHeight = 66.dp          // ROW_H＝行高固定（道・ノード・章題の縦位置を定数から算出）
private val LaneWidth = 72.dp          // LANE＝左レーン幅（道と駒は x<72・章題は右）
private val NodeColX = listOf(24.dp, 50.dp) // COLX＝ノード中心 x の2列（蛇行）
private val NodeRadius = 16.dp         // R＝read/未読ノード半径
private val CurNodeRadius = 19.dp      // RC＝現在ノード半径（大きい緑）
private val RoadLitWidth = 9.dp        // 灯り道の太さ
private val RoadCenterlineWidth = 1.6.dp // 灯り道の点線センターライン（道らしさ）
private val RoadUnreadWidth = 6.dp     // 未読道（暗い破線）の太さ
private val SpritePxCur = 3.1.dp       // 駒スプライトの1ドット（8×3.1=24.8<直径38）
private val SpritePxHud = 2.2.dp       // HUD アバターの駒スプライトの1ドット

// ドット絵の駒（HERO 8×8。'#'=塗り）。値の正本＝toc-P.html HERO。
private val HeroSprite = listOf(
    "..####..",
    ".######.",
    ".#.##.#.",
    "..####..",
    ".######.",
    "#.####.#",
    "..#..#..",
    ".##..##.",
)

// 層の上に載る透過色（焼き込めず .copy(alpha=) で正本 α を付与）。
private val LcdDotToc = LcdInkCartridge.copy(alpha = 0.15f)   // .hud::before rgba(43,54,22,.15) ドットマトリクス
private val HudGaugeOff = LcdInkCartridge.copy(alpha = 0.18f) // .hud .rt .g i 空セグ rgba(43,54,22,.18)
private val HudStatKey = LcdInkCartridge.copy(alpha = 0.72f)  // .hud .mid .k lcd-ink opacity:.72
private val HudAvBg = LcdInkCartridge.copy(alpha = 0.10f)     // .hud .av bg rgba(43,54,22,.10)
private val HudAvStroke = LcdInkCartridge.copy(alpha = 0.32f) // .hud .av inset stroke rgba(43,54,22,.32)
private val NowTagBorder = LcdInkCartridge.copy(alpha = 0.55f) // .row.cur .now border rgba(43,54,22,.55)

private val CurRowBand = LcdBandCartridge      // .row.cur バンド --lcd-band #ccd4a8（中央トークン化済み）
private val FutureNodeFill = FutureNodeCartridge // 未読ノード地 #e6e2d6（インライン実値の昇格トークン）

@Composable
internal fun TocCartridgeP(
    tocState: TocState,
    workTitle: String?,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetry: () -> Unit,
) {
    val entries = (tocState as? TocState.Content)?.entries ?: emptyList()
    // 現在章の index（D 実装 tocInitialFirstVisibleIndex と同じ突合＝fileName 一致）。未読/不一致は -1。
    val currentIndex = remember(entries, currentChapterFile) {
        entries.indexOfFirst { it.fileName == currentChapterFile }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // .phone 退色プラスチック筐体（linear-gradient(150deg, plastic-hi, plastic 22%, plastic-lo)・本棚Pと同一）。
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
        // navigationBarsPadding: nav バー inset を root で処理（M/J の目次と同じ流儀）。これが無いと
        // 下端固定フッタ Deck（通気孔＋銘板）が nav 帯に食い込む（K のリスト重なりと同じ欠落クラス・
        // 2026-07-29）。筐体プラ地は外側 Box の drawBehind が担うため nav 帯まで塗られたまま。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // K形伝播: トップバーに作品名サブ（.work）を追加。
            TocTopBarP(workTitle, onNavigateToBookshelf)

            when (tocState) {
                is TocState.Content -> {
                    val total = entries.size
                    // CLEAR%＝読了済み（現在章より前＝done ノード）の割合。目次画面はスクロール進捗を受け取らないため、
                    // done ノード数（currentIndex）を単一真実源にする（捏造せず可視の緑ノード数と一致）。未読は 0。
                    val cleared = currentIndex.coerceAtLeast(0)
                    val pct = tocReadProgressPercent(cleared, total)
                    // listState を持ち上げる（HUD の現在地チップのタップで現在章へスクロールさせるため・D/M/K と同型）。
                    val listState = rememberLazyListState(
                        // 現在章付近を開いた瞬間から表示（D/M と同じ導出＝現在章の1つ手前・未読は先頭）。
                        initialFirstVisibleItemIndex = tocInitialFirstVisibleIndex(entries, currentChapterFile),
                    )
                    val scope = rememberCoroutineScope()
                    // HUD（緑LCD）: K形伝播で STAGE 数値を現在地チップへ置換（現在地チップ＋STAGE SELECT＋進捗ゲージ）。
                    CartridgeHud(
                        currentIndex = currentIndex,
                        total = total,
                        pct = pct,
                        onJumpToCurrent = {
                            scope.launch { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
                        },
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        // .scroll padding:4px 0 8px → 上 S4 / 下 S8。
                        contentPadding = PaddingValues(top = Spacing.S4, bottom = Spacing.S8),
                    ) {
                        itemsIndexed(entries, key = { _, e -> e.fileName }) { index, entry ->
                            TocChapterRowP(
                                index = index,
                                currentIndex = currentIndex,
                                lastIndex = entries.lastIndex,
                                title = entry.title.ifEmpty { "第${index + 1}話" },
                                chapterNo = index + 1,
                                onClick = { onSelectChapter(entry.fileName) },
                            )
                        }
                    }
                }
                is TocState.Empty -> TocEmptyBodyP(Modifier.weight(1f))
                // Loading/Error は P 意匠のモック未定義＝最小限（筐体・トップバーは既に描かれている）。
                is TocState.Loading -> Spacer(Modifier.weight(1f))
                is TocState.Error -> TocErrorBodyP(tocState.message, onRetry, Modifier.weight(1f))
            }

            // 機体下端の意匠（.deck: 通気孔＋銘板）＝固定フッタ（全状態共通の機体クローム）。
            Deck()
        }
    }
}

// ============================================================
// トップバー（.top: 戻る＋「目次」＋作品名サブ .work）
// ============================================================
@Composable
private fun TocTopBarP(workTitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // .top padding:2px 12px 8px → 上 S4 / 横 S12 / 下 S8。
            .padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S4, bottom = Spacing.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = InkCartridge)
        }
        Column(modifier = Modifier.padding(start = Spacing.S8)) { // .top gap 6px → S8
            Text(
                "目次",
                fontSize = 18.sp,             // .top h1 18px（ゴシック・weight 700）
                fontWeight = FontWeight.Bold,
                color = InkCartridge,
            )
            // 作品名サブ（.work ゴシック12.5px --ink-soft）。渡された場合のみ（捏造禁止＝未紐付けは行ごと出さない）。
            if (!workTitle.isNullOrBlank()) {
                Text(
                    workTitle,
                    fontSize = 12.5.sp,       // .work 12.5px
                    color = InkSoftCartridge, // .work color var(--ink-soft)
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.S4), // .work margin-top 2px → S4
                )
            }
        }
    }
}

// ============================================================
// 携帯機HUD（.hud＝緑LCD の現在地チップ・全体進捗）
// ============================================================
@Composable
private fun CartridgeHud(currentIndex: Int, total: Int, pct: Int, onJumpToCurrent: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // .hud margin:2px 20px 12px → 上 S4 / 横 S24 / 下 S12。
            .padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4, bottom = Spacing.S12)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                // 液晶面（lcd-hi→lcd の縦グラデを代表＝本棚Pと同じ流儀）＋ドットマトリクス地。
                drawRect(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
                drawLcdDots(LcdDotToc)  // ドット色は目次面の正本 α（rgba(43,54,22,.15)）を渡す。
            }
            // .hud padding:11px 13px → 縦 S12 / 横 S12。
            .padding(horizontal = Spacing.S12, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // アバター（.av＝駒スプライトの小窓）。
        Box(
            modifier = Modifier
                .size(36.dp)                       // .av 36x36
                .clip(RoundedCornerShape(7.dp))
                .background(HudAvBg)
                .border(1.dp, HudAvStroke, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawSprite(size.width / 2f, size.height / 2f, SpritePxHud.toPx(), LcdInkCartridge)
            }
        }
        Spacer(Modifier.width(Spacing.S12))        // .hud gap 11px → S12
        // 現在地（.mid＝現在地チップ〔K形〕＋STAGE SELECT ラベル）。
        Column(modifier = Modifier.weight(1f)) {
            // 現在地チップ（.herechip＝LCD刻印風の枠チップ・タップで現在章へ）。現在章が無い（未読）ときは出さない。
            if (currentIndex >= 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.4.dp, NowTagBorder, RoundedCornerShape(999.dp)) // border rgba(43,54,22,.55)
                        .clickable(onClick = onJumpToCurrent)
                        .padding(horizontal = Spacing.S12, vertical = Spacing.S4), // .herechip 5px 12px
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // gap 6px
                ) {
                    Text("▶", fontFamily = PixelFamily, fontSize = 10.sp, color = LcdInkCartridge) // .pin
                    Text(
                        "いま読んでいる ・ 第${currentIndex + 1} / 全${total}話",
                        fontSize = 12.5.sp,           // .herechip 12.5px（ゴシック・weight 700）
                        fontWeight = FontWeight.Bold,
                        color = LcdInkCartridge,
                    )
                }
            }
            Text(
                "STAGE SELECT",
                fontFamily = PixelFamily,
                fontSize = 8.5.sp,                 // .mid .k 8.5px
                letterSpacing = 0.22.em,
                color = HudStatKey,
                // .k margin-top 7px → S8（チップがある時だけ・未読は詰める）。
                modifier = if (currentIndex >= 0) Modifier.padding(top = Spacing.S8) else Modifier,
            )
        }
        Spacer(Modifier.width(Spacing.S12))        // .hud gap 11px → S12
        // 全体進捗（.rt＝20分割ゲージ＋CLEAR%）。
        Column(horizontalAlignment = Alignment.End) {
            HudGauge(filled = (pct / 100f * 20f).roundToInt(), total = 20)
            Text(
                "CLEAR $pct%",
                fontFamily = PixelFamily,
                fontSize = 10.5.sp,                // .rt .pc 10.5px
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
                color = LcdInkCartridge,
                modifier = Modifier.padding(top = Spacing.S4), // .pc margin-top 5px → S4
            )
        }
    }
}

/** HUD の進捗ゲージ（.hud .rt .g＝4px 固定幅バー×20・gap 1.5px。伸長型 SegGauge と別プリミティブ）。 */
@Composable
private fun HudGauge(filled: Int, total: Int) {
    Canvas(
        modifier = Modifier
            // バー幅4dp×total＋ヘアライン gap1.5dp×(total-1) の固定幅（右寄せ）。
            .width(4.dp * total + 1.5.dp * (total - 1))
            .height(10.dp),                         // .g i height 10px
    ) {
        val barW = 4.dp.toPx()
        val gap = 1.5.dp.toPx()
        for (i in 0 until total) {
            drawRect(
                if (i < filled) LcdInkCartridge else HudGaugeOff,
                topLeft = Offset(i * (barW + gap), 0f),
                size = Size(barW, size.height),
            )
        }
    }
}

// ============================================================
// 章行＝ステージ（.row: 左レーンの道＋ノード＋右の章題。現在章は緑バンド＋▶NOW）
// ============================================================
@Composable
private fun TocChapterRowP(
    index: Int,
    currentIndex: Int,
    lastIndex: Int,
    title: String,
    chapterNo: Int,
    onClick: () -> Unit,
) {
    val isCur = index == currentIndex
    // 既読＝現在章より前（done ノード）。K形伝播でモック toc-P .row.done＝題名を沈めて行末に✓。
    val isRead = currentIndex >= 0 && index < currentIndex
    // ノード話数の描画に使う計測器（Canvas 内で drawText＝ノード中心へ厳密センタリング）。
    val measurer = rememberTextMeasurer()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)                     // .row height 66px 固定
            // 現在章＝緑LCDの帯（.row.cur background）。バンドは行全幅（右端の余白域も含む）。
            .then(if (isCur) Modifier.background(CurRowBand) else Modifier)
            .clickable(onClick = onClick)
            .padding(end = Spacing.S24),            // .row padding-right 20px → S24
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左レーン（道＋ノード＋話数/駒）。
        Canvas(modifier = Modifier.width(LaneWidth).fillMaxHeight()) {
            val cx = NodeColX[index % 2].toPx()
            val cy = size.height / 2f
            // ① 道（ノードの下＝先に描く）。区間 i→i+1 は (i+1)<=currentIndex で灯る（toc-P.html の条件）。
            //    行境界で対辺の半分どうしが接続する（境界 x=隣接列の中点＝直線上）ため蛇行が途切れない。
            if (index > 0) {
                val prevCx = NodeColX[(index - 1) % 2].toPx()
                drawRoad(Offset((prevCx + cx) / 2f, 0f), Offset(cx, cy), lit = currentIndex >= 0 && index <= currentIndex)
            }
            if (index < lastIndex) {
                val nextCx = NodeColX[(index + 1) % 2].toPx()
                drawRoad(Offset(cx, cy), Offset((cx + nextCx) / 2f, size.height), lit = currentIndex >= 0 && (index + 1) <= currentIndex)
            }
            // ② ノード（道の上）。read=緑充填＋話数／現在=大きい緑＋駒／未読=空＋話数。
            when {
                index == currentIndex -> {
                    drawCircle(LcdHiCartridge, CurNodeRadius.toPx(), Offset(cx, cy))
                    drawCircle(LcdInkCartridge, CurNodeRadius.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    drawSprite(cx, cy, SpritePxCur.toPx(), LcdInkCartridge) // 道に立つ駒（1点強調の中核）
                }
                currentIndex >= 0 && index < currentIndex -> {
                    drawCircle(LcdCartridge, NodeRadius.toPx(), Offset(cx, cy))
                    drawNodeNumber(measurer, chapterNo, cx, cy, LcdInkCartridge)
                }
                else -> {
                    drawCircle(FutureNodeFill, NodeRadius.toPx(), Offset(cx, cy))
                    drawCircle(LineCartridge, NodeRadius.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                    drawNodeNumber(measurer, chapterNo, cx, cy, InkMidCartridge)
                }
            }
        }
        // 章題（右カラム＝一定の開始位置で可読）。現在章＝lcd-ink 太字／既読＝--ink-soft へ沈める（K形伝播・モック .row.done）／未読＝ink。
        Text(
            text = title,
            fontSize = 14.5.sp,                    // .rtitle 14.5px（ゴシック）
            lineHeight = 20.6.sp,                  // line-height 1.42 × 14.5
            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isCur -> LcdInkCartridge
                isRead -> InkSoftCartridge         // .row.done .rtitle color var(--ink-soft)
                else -> InkCartridge
            },
            maxLines = 2,                          // line-clamp 2
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.S16),      // レーン端(72)→章題左(88) の 16px → S16
        )
        // 行末（K形）: 現在章＝唯一の実アクション「▶ ここから再開」／既読＝✓／未読＝なし。
        when {
            isCur -> Text(
                "▶ ここから再開",
                fontSize = 11.5.sp,                // .resume 11.5px（ゴシック・weight 800）
                fontWeight = FontWeight.ExtraBold,
                color = LcdInkCartridge,           // lcd-ink（緑LCD上の暗文字）
                modifier = Modifier
                    .padding(start = Spacing.S12)   // 章題との間隔
                    .clip(RoundedCornerShape(999.dp))
                    .background(LcdCartridge)       // .resume background var(--lcd)
                    .border(1.dp, LcdInkCartridge.copy(alpha = 0.5f), RoundedCornerShape(999.dp)) // border rgba(43,54,22,.5)
                    .padding(horizontal = Spacing.S12, vertical = Spacing.S8), // .resume 6px 12px
            )
            isRead -> Icon(
                Icons.Filled.Check,
                contentDescription = null,         // 既読の意味は行全体の見え（沈めた題名＋灯った緑ノード）が担う（装飾）
                tint = LcdInkCartridge.copy(alpha = 0.85f), // .rcheck color lcd-ink opacity .85
                modifier = Modifier
                    .padding(start = Spacing.S8)
                    .size(18.dp),                  // .rcheck 18x18
            )
        }
    }
}

/** 道の1区間（灯り＝実緑＋点線センターライン／未読＝暗い破線）。値の正本＝toc-P.html の道描画。 */
private fun DrawScope.drawRoad(from: Offset, to: Offset, lit: Boolean) {
    if (lit) {
        drawLine(LcdCartridge, from, to, strokeWidth = RoadLitWidth.toPx(), cap = StrokeCap.Round)
        drawLine(
            LcdInkCartridge, from, to,
            strokeWidth = RoadCenterlineWidth.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.5.dp.toPx())),
        )
    } else {
        drawLine(
            PlasticLoCartridge, from, to,
            strokeWidth = RoadUnreadWidth.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 6.dp.toPx())),
        )
    }
}

/** ドット絵の駒（HERO 8×8）を中心 (cx,cy) に ps ドットで描く。値の正本＝toc-P.html drawSprite。 */
private fun DrawScope.drawSprite(cx: Float, cy: Float, ps: Float, color: Color) {
    val ox = cx - 4f * ps
    val oy = cy - 4f * ps
    HeroSprite.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            if (ch == '#') drawRect(color, topLeft = Offset(ox + x * ps, oy + y * ps), size = Size(ps, ps))
        }
    }
}

/** ノード内の話数（done/未読ノード）。ノード中心へ厳密センタリング（mono・11px・状態別色）。 */
private fun DrawScope.drawNodeNumber(measurer: TextMeasurer, n: Int, cx: Float, cy: Float, color: Color) {
    val layout = measurer.measure(
        AnnotatedString(n.toString()),
        style = TextStyle(fontFamily = PixelFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color),
    )
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

// ============================================================
// 空状態（.empty＝破線の空きスロット箱＋「章が見つかりません」）
// ============================================================
@Composable
private fun TocEmptyBodyP(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // .empty .box 56x56 dashed --line radius 8。
        Box(
            modifier = Modifier.size(56.dp).drawBehind {
                drawRoundRect(
                    LineCartridge,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            },
        )
        Spacer(Modifier.height(Spacing.S16))       // .empty gap 14px → S16
        Text(
            "章が見つかりません",
            fontSize = 15.sp,                      // .empty .t1 15px（ゴシック）
            color = InkSoftCartridge,              // .empty color var(--ink-soft)
        )
    }
}

// ============================================================
// エラー（モック未定義＝機体基調の最小文言＋再試行。D の再試行導線を P 意匠で欠落なく残す）
// ============================================================
@Composable
private fun TocErrorBodyP(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "目次の読み込みに失敗しました",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = InkCartridge,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            fontSize = 12.sp,
            color = InkMidCartridge,               // 意味テキストは AA を満たす --ink-mid（--ink-soft は不足）
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.S4, bottom = Spacing.S16),
        )
        Text(
            "再試行",
            fontFamily = PixelFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkMidCartridge,
            modifier = Modifier
                .border(1.dp, LineCartridge, RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
        )
    }
}

// 機体下端の意匠（Deck）は CartridgePartsP.kt へ internal 集約済み＝当ファイルは Deck() を参照。
