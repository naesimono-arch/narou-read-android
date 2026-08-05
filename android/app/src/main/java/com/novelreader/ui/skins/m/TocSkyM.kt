package com.novelreader.ui.skins.m

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.FaintStarSeizu
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.OnStarSeizu
import com.novelreader.ui.theme.ResumeGradEndSeizu
import com.novelreader.ui.theme.ResumeGradStartSeizu
import com.novelreader.ui.theme.SkyGradMidSeizu
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarCoreSeizu
import com.novelreader.ui.theme.StarGlowInnerSeizu
import com.novelreader.ui.theme.StarGlowOuterSeizu
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu
import com.novelreader.ui.theme.TocCurStarSeizu
import com.novelreader.ui.theme.TocInkSeizu
import com.novelreader.ui.tocInitialFirstVisibleIndex
import com.novelreader.ui.tocReadProgressPercent
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch

// ============================================================
// スキンM「星図」の目次＝この物語の星座を辿る図（正本 toc-M.html・ADR 0022 §1 の構造分岐先）。
//
// 思想: 本棚Mのその作品の星座（折れ線）を目次の地に大きく描き、既読章ぶんだけ金の星が灯り結線が伸びる
// （本棚と同一データ文法で同期）。章名は明朝で地の上に浮かべ、可読のまま保つ（沈めるのは星グリフのみ）。
//
// モックとの構造差の翻訳方針:
//   ・地の夜空（.deepsky）は本棚 R1s（DeepSkyM）と同じ深空の型を「読む一覧」向けに沈めた静的フィールド
//     （超微星の海→天の川の粒帯→散開微星→ネビュラ→アクセント星）。本棚との差＝面輝度の全体上限 0.28・題名保護帯
//     capAt・帯の走向を左ガター寄りの縦の川へ・銀河核 y=520・seed 611953087・スパイクなし（作品星座と競合させない）。
//     帯疎化 fila/darkNeb/riftCenter/bDens・色温度 starTempColor/starColorAt/hash01・粒クラスは DeepSkyM から流用。
//   ・緯線・大星座（.skybg）は 390×844 固定天球に絶対座標で手置きし、深空の上に据え置く＝最前面の輝度階層を保つ。
//     実画面は可変サイズなので mock 座標を (mx/390, my/844) で実サイズへ比例写像する（p()）。radii/線幅は
//     BookshelfSkyM に倣い dp（*d）か生 float でスケール（同一実装の作法＝C2 仕様書「完全に合わせる」）。
//   ・大星座は toc-M の固定 7点折れ線（PTS）を使う。目次は book id/seed を受け取らないため、seed 由来の
//     散らしはせずモックの固定配置を正本とする。点火長 frac＝(現在章+1)/全章数（現在章まで灯す）。
//   ・canvas 先端星は正本モックでは脈動するが、「一画面一強調」（原則4）で脈動は現在章行のドットに一本化し、
//     canvas 先端は静止＝モックの reduce 分岐と同じ pulse=0.5 固定で描く（逸脱でなく監督裁定＝ADR 0022 §3）。
//   ・空状態はモック .empty（小点＋「章が見つかりません」・明朝）に忠実。破線枠は toc-M.html に存在しない
//     ため付けない（発明しない）。Loading/Error は M 意匠のモック未定義＝夜天＋題字のみ＋最小の文言に留める。
//
// モーション: 現在章ドットの脈動のみ（CSS pulse 2.8s・ADR 0022 §3 の「現在地の脈動」承認類型）。
// reduce-motion（アニメーター無効）では固定輝度（box-shadow 14px α.95）＝モックの reduce 分岐と同値。
//
// 背景・Lcg は星図スキン共通の SkyCanvas.kt を参照（本棚と二重定義を残さない）。
// ============================================================

/** 章行の点火状態（現在章より前＝read／現在章＝cur／未読＝ahead）。値の正本＝toc-M.html .li.{read,cur,ahead}。 */
private enum class RowLit { READ, CUR, AHEAD }

// 章題可読を守る地色スクリム（空の一枚化・2026-07-19）。常駐 backdrop は本棚R1s の満輝度（0.42）ゆえ、明るい
// 天の川帯が章題列を横切り得る。旧 tocCapAt（章題列0.16・上部クローム0.12）が担っていた「沈め」を、地色
// #0D1636（SkyGradMidSeizu）の α 掛けスクリムで再現する（直書き禁止＝トークン経由）。α は体感同等で可・実機後詰め。
private val TocSkyScrim = SkyGradMidSeizu.copy(alpha = 0.45f)   // 全面ウォッシュ＝章題列の実効輝度を旧0.16相当へ沈める
private val TocChromeScrim = SkyGradMidSeizu.copy(alpha = 0.30f) // 上部クローム帯の追い沈め（見出し/sync のゴールド・旧0.12相当）

@Composable
internal fun TocSkyM(
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
    // 大星座の点火長＝(現在章+1)/全章数（現在章まで灯す。現在章 = 折れ線の脈動先端）。未読・空は 0（灯さない）。
    val frac = if (entries.isEmpty()) 0f else ((currentIndex + 1).coerceAtLeast(0)).toFloat() / entries.size

    // reduce-motion: BookshelfSkyM と同じアニメーター無効検出を流用（ADR 0022 §3 の必須条件）。
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 現在章ドットの脈動位相（CSS pulse 2.8s ease-in-out ≒ 周期 2800ms の sin）。draw 段でだけ読ませる
    // ため State のまま渡す（deferred read）。reduce 時は無限アニメを作らず null＝静止描画へ倒す。
    val phase: State<Float>? = if (reduceMotion) null else rememberInfiniteTransition(label = "tocCurPulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "tocCurPulsePhase",
    )

    // 地の空（夜天3層・深空）は常駐 backdrop（SkyBackdropM）が本棚R1s の形で1枚だけ敷く（空の一枚化・2026-07-19）。
    // 本画面が持つのは①章題の可読を守る地色スクリム②この物語の作品星座・緯線（画面固有コンテンツ）。旧・目次固有の
    // 沈め版深空（buildTocDeepSkyField/drawTocDeepSky）は撤去した（走向/seed が本棚と別だった＝差し戻しの原因）。
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
            .drawBehind {
                // backdrop の空は本棚R1s の満輝度（面輝度上限0.42）＝統一天球ゆえ明るい天の川帯が章題列を横切り得る。
                // 旧 tocCapAt（章題列0.16・上部クローム0.12）の「沈め」を、地色(#0D1636=SkyGradMidSeizu)のスクリムで再現し
                // 明朝の章題可読を守る（α は実機後詰め＝体感同等で可・ADR 0023 の実機先行探索）。作品星座はスクリムの上へ。
                drawRect(TocSkyScrim)                                     // 全面ウォッシュ＝章題列の実効輝度を旧0.16相当へ沈める
                drawRect(                                                 // 上部クローム帯の追い沈め（見出し/sync のゴールド保護＝旧0.12相当）
                    Brush.verticalGradient(
                        0f to TocChromeScrim,
                        (80.dp.toPx() / size.height).coerceIn(0f, 1f) to Color.Transparent,
                    )
                )
                drawTocConstellation(hasConstellation = entries.isNotEmpty(), frac = frac) // 緯線・大星座＝最前面の輝度階層
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TocSkyTopBar(
                // K形伝播（正本 toc-M.html .topbar）: 副題は作品名（.work）。進捗は下の現在地バー（.here）へ移した。
                workTitle = workTitle,
                onNavigateToBookshelf = onNavigateToBookshelf,
            )
            when (tocState) {
                is TocState.Content -> {
                    // listState を持ち上げる（現在地チップのタップで現在章へスクロールさせるため・D/K と同型）。
                    val listState = rememberLazyListState(
                        // 現在章付近を開いた瞬間から表示（D と同じ導出＝現在章の1つ手前・未読は先頭）。
                        initialFirstVisibleItemIndex = tocInitialFirstVisibleIndex(entries, currentChapterFile),
                    )
                    val scope = rememberCoroutineScope()
                    // 現在地バー（K形伝播・モック toc-M.html .here）: 現在話チップ（星光地・星光字）＋進捗。
                    TocHereBarM(
                        currentIndex = currentIndex,
                        total = entries.size,
                        onJumpToCurrent = {
                            scope.launch { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
                        },
                    )
                    LazyColumn(
                        // スクロール差分を backdrop の視差へ流す（本棚面と同じ onPostScroll consumed.y＝画面遷移で連続）。
                        modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(parallaxNestedScroll),
                        state = listState,
                        // .scroll padding:6px 0 24px → 上 S8・下 S24。
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = Spacing.S8, bottom = Spacing.S24,
                        ),
                    ) {
                        itemsIndexed(entries, key = { _, e -> e.fileName }) { index, entry ->
                            val lit = when {
                                index == currentIndex -> RowLit.CUR
                                currentIndex >= 0 && index < currentIndex -> RowLit.READ
                                else -> RowLit.AHEAD
                            }
                            TocChapterRow(
                                epLabel = "第${index + 1}話",
                                title = entry.title.ifEmpty { "第${index + 1}章" },
                                lit = lit,
                                phase = phase,
                                onClick = { onSelectChapter(entry.fileName) },
                            )
                        }
                    }
                }
                is TocState.Empty -> TocEmptyBody(Modifier.weight(1f))
                // Loading/Error は M 意匠のモック未定義＝最小限（夜天＋題字は既に描かれている）。
                is TocState.Loading -> Spacer(Modifier.weight(1f))
                is TocState.Error -> TocErrorBody(tocState.message, onRetry, Modifier.weight(1f))
            }
        }
    }
}

// ============================================================
// topbar（モック .topbar: 戻る＋「目次」＋作品名サブ .work）
// ============================================================
@Composable
private fun TocSkyTopBar(
    workTitle: String?,
    onNavigateToBookshelf: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)               // .topbar height 54px
            .drawBehind {
                // border-bottom 1px var(--line) rgba(150,168,214,.20)。
                drawLine(
                    MoonSlateSeizu.copy(alpha = 0.20f),
                    Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f,
                )
            }
            .padding(horizontal = Spacing.S12),  // .topbar padding 0 12px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateToBookshelf) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "本棚に戻る", tint = TextSeizu)
        }
        Column(modifier = Modifier.padding(start = Spacing.S8)) {  // .topbar gap 8px
            Text(
                "目次",
                fontFamily = MinchoFamily,
                fontSize = 19.sp,          // .topbar h1 19px
                letterSpacing = 0.14.em,
                fontWeight = FontWeight.Medium,
                color = TextSeizu,
            )
            // 作品名サブ（.work 明朝13px --dim）。渡された場合のみ（捏造禁止＝未紐付けは行ごと出さない）。
            if (!workTitle.isNullOrBlank()) {
                Text(
                    workTitle,
                    fontFamily = MinchoFamily,
                    fontSize = 13.sp,      // .work 13px（明朝）
                    color = DimSeizu,      // .work color var(--dim)
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================================================
// 現在地バー（モック .here: 現在話チップ〔星光地・星光字・先頭に灯る星〕＋進捗）
// ============================================================
@Composable
private fun TocHereBarM(
    currentIndex: Int,
    total: Int,
    onJumpToCurrent: () -> Unit,
) {
    Row(
        // .here padding 12px 18px → 縦 S12・横 S16（18px は S16 へ丸め＝ADR 0014 スケール）。
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentIndex >= 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(StarSeizu.copy(alpha = 0.12f)) // --star-tint（星光10%地）
                    .clickable(onClick = onJumpToCurrent)
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .herechip 6px 14px
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // gap 7px → S8
            ) {
                // ::before 6px の灯る金の星（box-shadow 0 0 7px α.8）。既存の星グロー描画を流用する。
                Box(Modifier.size(6.dp).drawBehind { drawDotGlow(blurDp = 7f, alpha = 0.8f); drawCircle(StarSeizu) })
                Text(
                    "いま読んでいる 第${currentIndex + 1}話",
                    fontSize = 12.5.sp, // .herechip 12.5px
                    fontWeight = FontWeight.Bold,
                    color = StarSeizu,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val progress = buildString {
            append("全${total}話")
            // 読了率は現在章が既知のときのみ（未読は分母だけ＝捏造禁止）。可視の金の星（既読）数と一致。
            if (currentIndex >= 0 && total > 0) append(" ・ 読了率 ${tocReadProgressPercent(currentIndex, total)}%")
        }
        Text(progress, fontSize = 11.5.sp, color = DimSeizu) // .prog 11.5px var(--dim)
    }
}

// ============================================================
// 章行（モック .li: 左ガター〔縦結線＋星点〕＋話数ラベル＋章名。現在章は行末に「ここから再開」）
// ============================================================
@Composable
private fun TocChapterRow(
    epLabel: String,
    title: String,
    lit: RowLit,
    phase: State<Float>?,
    onClick: () -> Unit,
) {
    // --seg（縦結線色）: read/cur は金 α.5・ahead は月光スレート α.18。
    val seg = when (lit) {
        RowLit.READ, RowLit.CUR -> StarSeizu.copy(alpha = 0.5f)
        RowLit.AHEAD -> MoonSlateSeizu.copy(alpha = 0.18f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // .li.cur 行背景 rgba(233,221,180,.07)。
            .then(if (lit == RowLit.CUR) Modifier.background(StarSeizu.copy(alpha = 0.07f)) else Modifier)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ガター（.gut width 50px）: 中央に縦結線（::before 1px）と星点（.dot）。
        Box(
            modifier = Modifier.width(50.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(seg))
            TocDot(lit = lit, phase = phase)
        }
        // 話数ラベル（.ep width 52px・ゴシック11px --dim。K形伝播で追加）。
        Text(
            text = epLabel,
            fontSize = 11.sp,
            letterSpacing = 0.02.em,
            color = DimSeizu,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = title,
            fontFamily = MinchoFamily,
            fontSize = 15.sp,              // .tx 15px
            lineHeight = 24.sp,            // line-height 1.6 × 15
            // 現在章＝金 Bold。それ以外（read/ahead とも）＝章名は沈めず #C9D0E1（モック cap「沈めるのは星グリフのみ」）。
            color = if (lit == RowLit.CUR) StarSeizu else TocInkSeizu,
            fontWeight = if (lit == RowLit.CUR) FontWeight.Bold else FontWeight.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            // .tx padding 16px 16px 16px 0 → 縦 S16・右 S16・左 0（左のアキは .ep 幅が担う）。
            modifier = Modifier
                .weight(1f)
                .padding(top = Spacing.S16, end = Spacing.S16, bottom = Spacing.S16),
        )
        // 行末（.end padding-right 16px）: 現在章のみ唯一の実アクション「ここから再開」（この画面の強調）。
        // 既読マークは行末✓でなくガターの金の星（星座点火が M 署名＝既に TocDot が担う）。
        if (lit == RowLit.CUR) {
            Row(
                modifier = Modifier
                    .padding(end = Spacing.S16)
                    .clip(RoundedCornerShape(999.dp))
                    // .resume linear-gradient(135deg,#EbdFb4,#D8C68C)＝金のグラデ pill。
                    .background(Brush.linearGradient(listOf(ResumeGradStartSeizu, ResumeGradEndSeizu)))
                    .padding(horizontal = Spacing.S12, vertical = Spacing.S8), // .resume 6px 12px
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // gap 6px → S4
            ) {
                // ▶ 再生アイコン（fill #141B33＝夜天上の暗インク OnStar）。
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnStarSeizu, modifier = Modifier.size(11.dp))
                Text(
                    "ここから再開",
                    fontSize = 11.sp,             // .resume 11px
                    fontWeight = FontWeight.ExtraBold, // font-weight 800
                    color = OnStarSeizu,          // color #141B33
                )
            }
        }
    }
}

/** 章行の星点（.dot）＝状態別の色・径・グロー。cur のみ脈動（グローは常に金＝StarSeizu）。 */
@Composable
private fun TocDot(lit: RowLit, phase: State<Float>?) {
    // .dot 既定 7px／.li.cur .dot 11px。
    val dotDp = if (lit == RowLit.CUR) 11.dp else 7.dp
    // --dotc: read=金／cur=最輝 #F7F3E1／ahead=月光スレート α.45。
    val dotColor = when (lit) {
        RowLit.READ -> StarSeizu
        RowLit.CUR -> TocCurStarSeizu
        RowLit.AHEAD -> MoonSlateSeizu.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier.size(dotDp).drawBehind {
            when (lit) {
                // read: box-shadow 0 0 7px rgba(233,221,180,.65)。
                RowLit.READ -> drawDotGlow(blurDp = 7f, alpha = 0.65f)
                // cur: 脈動 blur 10→18 / α .7→1（reduce は静止 14 / .95＝モック非アニメ値）。
                RowLit.CUR -> {
                    val blur: Float
                    val a: Float
                    if (phase == null) {
                        blur = 14f; a = 0.95f
                    } else {
                        val p = (sin(phase.value * 2f * PI.toFloat()) + 1f) / 2f
                        blur = 10f + p * 8f; a = 0.7f + p * 0.3f
                    }
                    drawDotGlow(blurDp = blur, alpha = a)
                }
                // ahead: グローなし（淡い星点のみ）。
                RowLit.AHEAD -> Unit
            }
            drawCircle(dotColor) // 星点本体（size いっぱいの円）。
        },
    )
}

/** 星点の box-shadow（0 0 blur 金）近似＝金の放射グロー。芯（dot）の縁まで実色・そこから blur ぶんで 0 へ。 */
private fun DrawScope.drawDotGlow(blurDp: Float, alpha: Float) {
    val d = 1.dp.toPx()
    val dotR = size.minDimension / 2f
    val r = dotR + blurDp * d
    drawCircle(
        Brush.radialGradient(
            0f to StarSeizu.copy(alpha = alpha),
            (dotR / r) to StarSeizu.copy(alpha = alpha),
            1f to StarSeizu.copy(alpha = 0f),
            center = center, radius = r,
        ),
        radius = r, center = center,
    )
}

// ============================================================
// 空状態（モック .empty: 小点＋「章が見つかりません」・明朝・破線枠なし）
// ============================================================
@Composable
private fun TocEmptyBody(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // .empty .st 6px rgba(150,168,214,.4)。
        Box(Modifier.size(6.dp).drawBehind { drawCircle(MoonSlateSeizu.copy(alpha = 0.4f)) })
        Spacer(Modifier.height(Spacing.S16)) // gap 14px → S16（丸め）。
        Text(
            "章が見つかりません",
            fontFamily = MinchoFamily,
            fontSize = 15.sp,
            color = DimSeizu,          // .empty color var(--dim)
        )
    }
}

// ============================================================
// エラー（モック未定義＝夜天の上に最小の文言＋再試行。D の再試行導線を M 意匠で欠落なく残す）
// ============================================================
@Composable
private fun TocErrorBody(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "目次の読み込みに失敗しました",
            fontFamily = MinchoFamily,
            fontSize = 15.sp,
            color = DimSeizu,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S8))
        Text(
            message,
            fontFamily = MinchoFamily,
            fontSize = 12.sp,
            color = DimSeizu,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S16))
        Text(
            "再試行",
            fontFamily = MinchoFamily,
            fontSize = 15.sp,
            color = StarSeizu,
            modifier = Modifier.clickable(onClick = onRetry).padding(Spacing.S8),
        )
    }
}

// ============================================================
// canvas 描画（この物語の作品星座＝緯線・大星座）。値の正本＝toc-M-rich-R1.html <script> の .skybg 相当。
// 地の空（夜天・深空・天の川粒帯）は常駐 backdrop（SkyBackdropM）が本棚R1s の形で敷く＝空の一枚化（2026-07-19）ゆえ、
// 本ファイルは目次固有コンテンツ（緯線4本＋この物語の大星座の点火）だけを最前面の輝度階層として描く。旧・目次沈め版
// 深空（SkyTocR/tocBAxis/tocCapAt/buildTocDeepSkyField/drawTocDeepSky）は参照ごと撤去した（走向/seed が本棚と別＝差し戻し原因）。
// ============================================================

/** 緯線4本＋大星座（点火）を描く＝最前面の輝度階層（モック .skybg 相当・深空の上に据え置き）。座標は mock 390×844 → 実サイズへ比例写像。 */
private fun DrawScope.drawTocConstellation(hasConstellation: Boolean, frac: Float) {
    val w = size.width
    val h = size.height
    val d = 1.dp.toPx()
    // mock 座標 (390×844) → 実サイズへの比例写像。
    fun p(mx: Float, my: Float) = Offset(mx / 390f * w, my / 844f * h)

    // 緯線4本（y=170+i*180・moveTo(6,y) quad(195,y-14,384,y)・rgba(150,168,214,.05)）。
    for (i in 0 until 4) {
        val y = 170f + i * 180f
        val start = p(6f, y)
        val ctrl = p(195f, y - 14f)
        val end = p(384f, y)
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(ctrl.x, ctrl.y, end.x, end.y)
        }
        drawPath(path, MoonSlateSeizu.copy(alpha = 0.05f), style = Stroke(width = 1f))
    }

    if (!hasConstellation) return

    // 大星座（PTS 固定7点）。弧長を積み frac ぶんだけ金で点火（先端補間）＝本棚と同一文法。
    val mock = arrayOf(
        300f to 150f, 248f to 252f, 318f to 344f, 250f to 452f,
        302f to 556f, 240f to 646f, 292f to 742f,
    )
    val pts = mock.map { p(it.first, it.second) }
    val segLens = FloatArray(pts.size - 1)
    var totalLen = 0f
    for (i in 1 until pts.size) {
        segLens[i - 1] = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        totalLen += segLens[i - 1]
    }
    val litLen = frac.coerceIn(0f, 1f) * totalLen

    // 下描き＝淡い結線（rgba(150,168,214,.16) 幅1.2）。toc は未読破線を持たず常に実線1本。
    val basePath = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(basePath, MoonSlateSeizu.copy(alpha = 0.16f), style = Stroke(width = 1.2f))

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
        // shadowBlur 6 の代替＝太→細の3層ストローク（外側ほど淡い金）。芯は toc-M lit の実値（α.5・幅1.6）、
        // 外2層は shadowBlur ぶんのにじみ＝BookshelfSkyM と同一の広がり（4*d/2.4*d・α.15/.35）。
        drawPath(litPath, StarSeizu.copy(alpha = 0.15f), style = Stroke(4 * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(litPath, StarSeizu.copy(alpha = 0.35f), style = Stroke(2.4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(litPath, StarSeizu.copy(alpha = 0.5f), style = Stroke(1.6f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        tip = tipPt
    }

    // 星点: 点灯済みはグロー（半径5・glow .6）・未点灯は淡星（rgba(150,166,206,.32) 半径1.6）。
    var acc2 = 0f
    for (i in pts.indices) {
        if (i > 0) acc2 += segLens[i - 1]
        val isLit = litLen > 0f && acc2 <= litLen + 0.5f
        if (isLit) drawTocStarGlow(pts[i], 5f * d, 0.6f)
        else drawCircle(FaintStarSeizu.copy(alpha = 0.32f), radius = 1.6f * d, center = pts[i])
    }
    // 先端星: 静止（脈動は現在章行のドットへ一本化＝ADR 0022 §3）。モック reduce と同じ pulse=0.5 固定
    //（半径 6+0.5*3=7.5・glow .7+0.5*.2=.8）。
    tip?.let { drawTocStarGlow(it, 7.5f * d, 0.8f) }
}

/** 星光グロー（starGlow: radial 3停止＋星芯）。toc-M は中間停止 α×.4・芯 1.8px（bookshelf は ×.42・1.7px）。 */
private fun DrawScope.drawTocStarGlow(center: Offset, radius: Float, glow: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to StarGlowInnerSeizu.copy(alpha = glow.coerceAtMost(1f)),
            0.42f to StarGlowOuterSeizu.copy(alpha = (glow * 0.4f).coerceAtMost(1f)),
            1f to StarGlowOuterSeizu.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
    drawCircle(StarCoreSeizu.copy(alpha = (glow + 0.15f).coerceAtMost(1f)), radius = 1.8f * 1.dp.toPx(), center = center)
}
