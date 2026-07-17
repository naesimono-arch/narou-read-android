package com.novelreader.ui.skins.j

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.NewEpisodeNotificationMenuSection
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.theme.AmbDarkGoldPortal
import com.novelreader.ui.theme.AmbDarkMossPortal
import com.novelreader.ui.theme.GlyphDarkPortal
import com.novelreader.ui.theme.GoldPortal
import com.novelreader.ui.theme.GreenPortal
import com.novelreader.ui.theme.InkPortal
import com.novelreader.ui.theme.LinePortal
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.PagePortal
import com.novelreader.ui.theme.PanelPortal
import com.novelreader.ui.theme.PlumPortal
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.SoftPortal
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor
import java.time.LocalTime
import kotlin.math.roundToInt

// ============================================================
// スキンJ「ポータル」の本棚＝1作=1画面の没入扉を横スワイプでめくるポータル・デッキ
// （正本 bookshelf-J.html・ADR 0022 §1 の構造分岐先）。
//
// 思想: 「一覧＝グリッド/リスト」というナビゲーションを破壊し、本棚を捨てて 1作=1画面の没入ポータルを
//   横スワイプでめくる。各作＝その物語世界への「扉」。実画像が無い制約を、bookId 由来の大気(ambient)
//   グラデ＋象徴1文字(glyph)で埋める（捏造表紙より誠実・モック明記）。デッキ最後尾のポータルが発見への入口。
//
// 署名色(全画面不動・3系統以内): 金 GoldPortal（構造/強調・敷居）／森緑 GreenPortal（世界・続きあり・未取込／
//   右 peek）／宵紫 PlumPortal（他の扉＝左 peek）。material/本棚は theme 非依存の固定ダーク森面（ADR 0022 §2）。
//
// 色は Portal val のみ（直書き hex 禁止）。ambient はデータ駆動パレット（ADR 0022 §5）だが、本エージェントは
//   Color.kt へ値を追加できないため、既存 Portal val だけで森の大気を組む:
//     ・base = PanelPortal(森パネル #16211A)→PagePortal(外殻 #0C0E0B) の linear（森の内→外殻）
//     ・金の敷居 = AmbDarkGoldPortal（reading-J 森大気 rgba(214,196,120,.22)＝bookshelf amb の金 RGB と同値）
//     ・底の苔 = AmbDarkMossPortal（rgba(39,64,48,.72)＝bookshelf amb-yaku の森緑 RGB #274030 と同値）
//     ・作品ごとの色相差 = 署名3色（金/森緑/宵紫）を bookId ハッシュで割り当てた上部グロー（モック「3系統以内」に忠実）
//   モックが各扉に与える固有の森リニア中間色（amb-yaku/find/maou/en/kusa の #15241A/#181710/#241C2E 等）は
//   Portal val に無い＝本画面では発明も近似もせず、3系統グローで作品差を出す。厳密再現には値追加が要る（報告参照）。
//
// 機能全数の所在（ADR 0022 §1・M/P の流儀に倣う）: デッキ表示（本画面）が横スワイプ閲覧/続きから読む/
//   絞り込みチップ/取込中バナー/見つける導線/装い/メニュー(テーマ3択)/続きあり印/PDF追加(発見扉経由)を担い、
//   選択削除・Web カード操作・グリッド一覧は「一覧表示」＝D 構造フォールバック（グリッド操作で到達）が全数担う。
//   モックの J 意匠グリッド面は「一覧へ降格」の概念であり、その可読フォールバックが D 構造（ADR 0021 の
//   D 構造へトークン写像）＝選択/Web/グリッドを三重実装しない（ADR §1 の画面複製却下と同じ倹約則）。
//
// モーション: J モックに keyframes/transition/JS は無い（ADR 0022 §3）＝静止で実装。横スワイプは HorizontalPager
//   の既存フレームワーク挙動（機能モーション）、取込中バナーの出没だけ既存 Motion.kt スロットを流用（M/P と同型）。
//
// タイポ: 題名＝明朝(MinchoFamily・mock var(--mincho))・本文/UI＝既定ゴシック(mock var(--gothic))。
//   構造画面専用の px 値は正本モックの font-size を 1:1 で sp へ写す（各行にモック由来コメント・ADR 0022 §5 の in-file 集約）。
// ============================================================

// ---- 描画層で層の上に載る透過色（グラデ地の上へ載るため .copy(alpha=) で正本 α を付与・base は名前付き Portal val）----
private val PortalIbBg = Color.Black.copy(alpha = 0.22f)       // .topbar .ib background rgba(0,0,0,.22)
private val PortalIbBorder = InkPortal.copy(alpha = 0.2f)      // .topbar .ib border rgba(233,240,228,.2)
private val PortalWardBorder = GoldPortal.copy(alpha = 0.4f)   // .topbar .ib.ward border rgba(226,200,120,.4)
private val ChipBg = Color.Black.copy(alpha = 0.18f)          // .lchip background rgba(0,0,0,.18)
private val ChipOnBorder = GoldPortal.copy(alpha = 0.6f)      // .lchip.on border rgba(226,200,120,.6)
private val ProcBg = PagePortal.copy(alpha = 0.6f)           // .proc background rgba(8,12,8,.6)＝外殻を薄く
private val ProgTrack = InkPortal.copy(alpha = 0.16f)        // .prog .pb background rgba(255,255,255,.16)
private val StepOff = LinePortal                             // .pdot/.pln 空セグ＝外殻ヘアライン --line
private val DotFaint = InkPortal.copy(alpha = 0.3f)          // .dots i background rgba(233,240,228,.3)
private val HintInk = InkPortal.copy(alpha = 0.5f)          // .hint color rgba(233,240,228,.5)
private val IdxInk = InkPortal.copy(alpha = 0.85f)          // .topbar .idx color rgba(233,240,228,.85)
private val PeekPlum = PlumPortal.copy(alpha = 0.9f)         // .peek.l 宵紫（他の扉）rgba(120,86,150,.9) を署名 plum で
private val PeekGreen = GreenPortal.copy(alpha = 0.75f)      // .peek.r rgba(159,207,169,.75)＝GreenPortal α.75

// 作品ごとの上部グロー tint（署名3色を id ハッシュで安定割当＝並び替えで変わらない・モック「3系統以内」）。
private val PortalAmbientTints = listOf(GoldPortal, GreenPortal, PlumPortal)
private fun ambientTintFor(bookId: String): Color =
    PortalAmbientTints[(bookId.hashCode() and 0x7fffffff) % PortalAmbientTints.size]

// ============================================================
// 〈遊び心〉J3「時を映す扉」＝大気の“地”を現実時刻で移ろわせる（直交2レイヤの下段＝地=時刻）。
//   mock の amb-morning / amb-yaku(既定=夕) / amb-night の 3クラスへの写像。新色相は足さず（mock J3
//   「金/森緑の2系統のまま温度と明るさだけ可変・新色相なし」）、金トップ warm・緑トップ cool・底の苔で温度と明るさだけ動かす。
// ============================================================
internal enum class PortalTimePhase { MORNING, EVENING, NIGHT }

/**
 * 時刻(hour 0..23)を朝/夕/夜へ写す純関数（時刻を引数化＝JVM テストで決定的に検証可能）。
 * 閾値はモックのコメント（amb-morning/amb-night）に時間境界の明示が無いため、一般的な体感で定義:
 *   5-11時=朝(澄んだ緑金)／11-17時=夕(既定・従来の正本の見え)／17-翌5時=夜(光が引き深い森へ沈む)。
 */
internal fun portalTimePhaseFor(hour: Int): PortalTimePhase = when (hour) {
    in 5..10 -> PortalTimePhase.MORNING   // 5:00–10:59
    in 11..16 -> PortalTimePhase.EVENING  // 11:00–16:59（既定＝従来の見え）
    else -> PortalTimePhase.NIGHT          // 17:00–翌4:59
}

/**
 * 時刻相ごとの大気パラメータ（mock の --warm/--cool/--floor を写す）。
 *   warm       = 金トップグローの基底α（mock --warm。J1 の読進 open ぶんはここへ加算＝直交合成）
 *   cool       = 緑トップグローα（mock --cool。朝のみ>0＝澄んだ冷光。夕夜は 0＝描かない）
 *   floorAlpha = 底の苔α（mock --floor のα。夕.9→夜.92 と沈む）
 *   floorDarken= 底の苔を外殻(PagePortal)へ寄せる係数（夜だけ>0＝「深い森へ沈む」明るさ低下。既存 val の lerp で表現＝新色発明なし）
 */
internal data class PortalAmbientParams(
    val warm: Float,
    val cool: Float,
    val floorAlpha: Float,
    val floorDarken: Float,
)

internal fun portalAmbientParamsFor(phase: PortalTimePhase): PortalAmbientParams = when (phase) {
    // amb-morning: --warm:.16 --cool:.16 --floor rgba(30,58,40,.72)＝澄んだ緑金（金を弱め・緑の冷光を足す）。
    PortalTimePhase.MORNING -> PortalAmbientParams(warm = 0.16f, cool = 0.16f, floorAlpha = 0.72f, floorDarken = 0f)
    // amb-yaku（既定=夕）: --warm:.18 --cool:0 --floor rgba(20,46,30,.9)＝金が濃く暖まる（従来の正本の見えに一致）。
    PortalTimePhase.EVENING -> PortalAmbientParams(warm = 0.18f, cool = 0f, floorAlpha = 0.9f, floorDarken = 0f)
    // amb-night: --warm:.06 --cool:0 --floor rgba(12,26,18,.92)＝光が引き底が外殻へ沈む（floorDarken で苔を暗化）。
    PortalTimePhase.NIGHT -> PortalAmbientParams(warm = 0.06f, cool = 0f, floorAlpha = 0.92f, floorDarken = 0.3f)
}

@Composable
internal fun BookshelfPortalJ(
    books: List<BookEntity>,
    progressMap: Map<String, ProgressEntity>,
    chapterCountMap: Map<String, Int>,
    newEpisodeNovelMap: Map<String, NarouNovel>,
    processingState: ProcessingState,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
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
    // 状態フィルタ適用後の可視作品（チップは D と同じ readingStatusFor を単一真実源に使う＝M/P と同型）。
    val visible = remember(books, progressMap, chapterCountMap, selectedStatus) {
        if (selectedStatus == null) books
        else books.filter { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == selectedStatus }
    }
    // hero＝いま読みかけの先頭作＝デッキを最初に開く扉（モックの「続きから」＝1枚目）。無ければ先頭。
    val heroIndex = remember(visible, progressMap, chapterCountMap) {
        visible.indexOfFirst { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == ReadingStatus.READING }
            .coerceAtLeast(0)
    }

    // 〈遊び心〉J3「時を映す扉」: 現実時刻の時間帯を《起動時に1回だけ》読む。時間帯はゆっくりしか変わらず
    //   （セッション中に相境界を跨ぐことは稀）、かつフレーム毎/再コンポーズ毎に Clock を読むと再コンポーズが
    //   暴れるため、remember で1回だけ解決して固定する（＝副作用の正しい扱い）。時刻の取得は純関数
    //   portalTimePhaseFor(hour) に時刻を引数化してあり、JVM テストは now() を呼ばず決定的に検証できる。
    //   ADR 0022 §3（J はモーション無し）に従い、時間帯遷移も animateColor せず静的に解決＝reduce-motion でも同一。
    val timePhase = remember { portalTimePhaseFor(LocalTime.now().hour) }

    // ページ数＝可視作品数＋1（最後尾＝新しい扉を探す＝発見への入口。作品ゼロでも扉1枚は必ず残す）。
    val pageCount = visible.size + 1
    val pagerState = rememberPagerState(initialPage = heroIndex.coerceAtMost(pageCount - 1)) { pageCount }
    val current = pagerState.currentPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 外殻の地（--page #0C0E0B）。各ページが自前の ambient を全面描画するが、初期/端の余白の下地に。
            .background(PagePortal),
    ) {
        // ── ポータル・デッキ本体（横スワイプ・各ページが自前 ambient を全面描画＝スワイプで大気ごとめくれる）──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // ページの左右に隣扉の覗き（peek）。左＝前扉あり・右＝次扉あり（最後尾扉は右 peek なし＝モック実態）。
            val hasPrev = page > 0
            val hasNext = page < pageCount - 1
            if (page < visible.size) {
                val book = visible[page]
                PortalPage(
                    book = book,
                    progress = progressMap[book.id],
                    totalChaps = chapterCountMap[book.id] ?: 0,
                    novelDetail = book.ncode?.let { newEpisodeNovelMap[it] },
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    timePhase = timePhase,
                    onOpen = { onOpenBook(book) },
                )
            } else {
                // 最後尾＝新しい扉を探す（見つける導線・発見ホームへ）。
                FindPortalPage(hasPrev = hasPrev, timePhase = timePhase, onOpenDiscovery = onOpenDiscovery)
            }
        }

        // ── 上部の固定クローム（topbar / 絞り込みチップ / 取込中バナー）＝スワイプで動かず現在扉の大気の上に載る ──
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .wrapContentHeight(),
        ) {
            PortalTopBar(
                // .idx「n / N」＝発見扉(最後尾)では非表示（モック idx 空）。作品ページのみ位置を出す。
                indexLabel = if (current < visible.size) "${current + 1} / ${visible.size}" else "",
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                onOpenDiscovery = onOpenDiscovery,
                onOpenWardrobe = onOpenWardrobe,
                onToggleList = onToggleList,
                onFabClick = onFabClick,
            )
            // 絞り込みチップ（.chipbar＝読書状態フィルタへ写像・M/P と同機能。作品ゼロでも「すべて」導線は残す）。
            PortalChips(selectedStatus, statusCounts, onSelectStatus)
            // 取込中バナー（.proc＝扉を仕立てている＝ProcessingBanner の J 意匠。出没のみ Motion スロット）。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                PortalProcessingBanner(processingState, onCancelProcessing)
            }
        }

        // ── 下端の固定インジケータ（ページドット＋スワイプヒント）＝pager 状態を映す ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PortalDots(current = current, bookCount = visible.size)
            PortalHint(onDiscovery = current >= visible.size)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.S40), // ドット/ヒントの上へ逃がす
        )
    }
}

// ============================================================
// 1枚の扉（.phone＝ambient 全面＋peek＋下寄せの物語本体＋ドット/ヒント下端）
// ============================================================
@Composable
private fun PortalPage(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    novelDetail: NarouNovel?,
    hasPrev: Boolean,
    hasNext: Boolean,
    timePhase: PortalTimePhase,
    onOpen: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val pct = ((frac ?: 0f) * 100).roundToInt()
    val status = readingStatusFor(progress, totalChaps)
    val isUnread = status == ReadingStatus.UNREAD
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)
    val tint = ambientTintFor(book.id)
    // 〈遊び心〉J1「開く扉」: --open＝その作品の読了率（実データ progressFractionFor）。0%＝半ば閉じた扉／
    //   読むほど扉の奥の光と象徴文字が強まる。未読(frac=null)は 0＝薄暗く半ば閉じた扉。時刻(地)とは独立に効く直交軸。
    val open = (frac ?: 0f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize().drawAmbient(tint, timePhase, open)) {
        // 象徴1文字（.glyph＝題名頭文字を巨大・極淡で。実画像の不在を象徴で埋める）。
        Text(
            text = book.title.take(1),
            fontFamily = MinchoFamily,
            fontSize = 300.sp,                 // .glyph font-size:300px（1:1 写経）
            // 〈遊び心〉J1: glyph α＝mock rgba(233,240,228, .02 + .05*open)＝読み進むほど象徴が濃くなる。
            color = GlyphDarkPortal.copy(alpha = (0.02f + 0.05f * open).coerceIn(0f, 1f)),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.S40),
        )
        PeekEdges(hasPrev, hasNext)

        // 物語本体（.body＝下寄せ）＋ドット/ヒントは固定インジケータ側で描くため、ここは本体のみを下寄せ。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.S32), // .body padding 0 30px → 余白スケール最寄 S32
            verticalArrangement = Arrangement.Bottom,
        ) {
            // .arc「辺境編 · 第127話」→ 章位置のみ（arc 名＝データ無し。未読は「全N話」）。
            Text(
                text = if (isUnread) "全${totalChaps}話" else "第${chapNum ?: 1}話",
                fontSize = 11.sp,               // .arc 11px
                letterSpacing = 0.3.em,
                color = GoldPortal,
                modifier = Modifier.padding(bottom = Spacing.S12),
            )
            // .update「更新 · 続き N話」＝扉の奥で物語が進んだ印（森緑ドット＋金文字）。続きありのみ。
            if (newCount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.S12),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(GreenPortal)) // .update .udot 6px 森緑
                    Text(
                        text = "更新 · 続き${newCount}話",
                        fontSize = 11.sp,       // .update 11px
                        letterSpacing = 0.04.em,
                        color = GoldPortal,
                        modifier = Modifier.padding(start = Spacing.S8),
                    )
                }
            }
            // .ttl 題名（明朝・大・温白）。
            Text(
                text = book.title,
                fontFamily = MinchoFamily,
                fontSize = 37.sp,               // .ttl 37px
                lineHeight = 47.sp,             // line-height:1.28（37×1.28）
                fontWeight = FontWeight.SemiBold,
                color = InkPortal,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            // .by 著者。
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    fontSize = 12.5.sp,         // .by 12.5px
                    letterSpacing = 0.06.em,
                    color = SoftPortal,
                    modifier = Modifier.padding(top = Spacing.S12),
                )
            }
            // .incipit（冒頭一文）はモックの装飾的な本文抜粋＝本棚層に本文データが無い＝発明せず省く（報告参照）。

            // .prog 進捗（残り話数＋%＋バー）。未読は進捗が無い＝出さない。
            if (!isUnread && frac != null) {
                val remain = (totalChaps - (chapNum ?: 0)).coerceAtLeast(0)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S24, bottom = Spacing.S8),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("旅の途中", fontSize = 11.sp, color = SoftPortal) // .prog .pl 11px
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$pct%",
                            fontFamily = MinchoFamily,
                            fontSize = 13.sp,   // .prog .pl b 13px（明朝）
                            color = InkPortal,
                        )
                        Text(
                            " · 残り${remain}話",
                            fontSize = 11.sp,
                            color = SoftPortal,
                        )
                    }
                }
                // .pb バー（track rgba(255,255,255,.16)・fill 金→森緑グラデ）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)          // .pb height:3px
                        .clip(RoundedCornerShape(2.dp))
                        .background(ProgTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((frac).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(GoldPortal, GreenPortal))),
                    )
                }
            }
            // .resume「続きから読む」＝一画面唯一の強調（温白の実塗り・暗色文字）。未読は「読む」。
            ResumeButton(
                label = if (isUnread) "読む" else "続きから読む",
                ghost = false,
                onClick = onOpen,
                modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S40),
            )
        }
    }
}

// ============================================================
// 最後尾＝新しい扉を探す（.amb-find＝見つける導線・発見への入口）
// ============================================================
@Composable
private fun FindPortalPage(hasPrev: Boolean, timePhase: PortalTimePhase, onOpenDiscovery: () -> Unit) {
    // 発見扉は読了率を持たない（作品でない）ため J1 の open は既定値 .62（mock 既定の見え）で固定。
    //   地は他扉と同じ timePhase で移ろわせ、デッキ全体の時刻感を揃える（スワイプで大気が不連続にならない）。
    val findOpen = 0.62f
    Box(modifier = Modifier.fillMaxSize().drawAmbient(GoldPortal, timePhase, findOpen)) {
        Text(
            text = "扉",
            fontFamily = MinchoFamily,
            fontSize = 300.sp,                  // .glyph 300px
            color = GlyphDarkPortal.copy(alpha = (0.02f + 0.05f * findOpen).coerceIn(0f, 1f)),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.S40),
        )
        PeekEdges(hasPrev = hasPrev, hasNext = false)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.S32),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "新しい物語を\n見つける",
                fontFamily = MinchoFamily,
                fontSize = 37.sp,               // .ttl 37px
                lineHeight = 47.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPortal,
            )
            Text(
                text = "ランキング・ジャンル・検索から、次の物語へ。",
                fontFamily = MinchoFamily,
                fontSize = 13.sp,               // .incipit 13px
                lineHeight = 25.sp,             // line-height:1.9
                color = InkPortal.copy(alpha = 0.78f), // .incipit rgba(233,240,228,.78)
                modifier = Modifier.padding(top = Spacing.S16),
            )
            ResumeButton(
                label = "見つける",
                ghost = true,                   // .resume.ghost（枠線のみ）
                onClick = onOpenDiscovery,
                modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S40),
            )
        }
    }
}

/** 扉本体の共通 CTA（.resume＝温白の実塗り／.ghost＝枠線のみ）。強調は実塗り版が一画面一箇所。 */
@Composable
private fun ResumeButton(
    label: String,
    ghost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (ghost) Modifier.border(1.dp, InkPortal.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) // .ghost border rgba(233,240,228,.5)
                else Modifier.background(InkPortal) // .resume background #E9F0E4≒温白 InkPortal
            )
            .clickable(onClick = onClick)
            .padding(Spacing.S16),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ghost) Icons.Filled.Search else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (ghost) InkPortal else PagePortal, // ghost=温白／実塗り=暗色（#15241A≒外殻）
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            fontSize = 16.sp,                   // .resume 16px
            fontWeight = FontWeight.ExtraBold,
            color = if (ghost) InkPortal else PagePortal,
            modifier = Modifier.padding(start = Spacing.S8),
        )
    }
}

/** 隣扉の覗き（.peek＝左=宵紫の他扉／右=森緑の次扉・幅14dp全高）。端の扉は該当方向を出さない。 */
@Composable
private fun BoxScope.PeekEdges(hasPrev: Boolean, hasNext: Boolean) {
    if (hasPrev) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(14.dp)               // .peek width:14px
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(PeekPlum, Color.Transparent))), // .peek.l 90deg 宵紫→透明
        )
    }
    if (hasNext) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(14.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, PeekGreen))), // .peek.r 270deg 森緑→透明
        )
    }
}

/**
 * 森の大気を既存 Portal val だけで描く（ADR 0022 §5・値追加禁止のため近似/発明せず名前付き val の重ね）。
 * 直交2レイヤ設計（mock J1×J3）:
 *   地(=時刻 phase)   → base 森グラデ＋金/緑トップの温度＋底の苔の沈み（portalAmbientParamsFor）。
 *   上乗せ(=読進 open)→ 金トップグローへ .20*open を加算（扉の奥が明るくなる）。glyph α は呼び側で加算。
 * 両者は互いに非干渉（warm は phase、加算分は open）＝mock の `calc(var(--warm) + .20*var(--open))` を1:1で写す。
 */
private fun Modifier.drawAmbient(tint: Color, phase: PortalTimePhase, open: Float): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    val p = portalAmbientParamsFor(phase)
    // base: 森の内(PanelPortal)→外殻(PagePortal) の斜めグラデ。時刻の“地”色相(mock #2C4739 等)は Portal val に
    //   無いため発明せず森トークン固定。時刻差は上乗せの金/緑グロー＋底の苔で出す（mock J3「2系統のまま温度と明るさだけ可変」）。
    drawRect(
        Brush.linearGradient(
            0f to PanelPortal,
            0.55f to PagePortal,
            1f to PagePortal,
            start = Offset(w * 0.2f, 0f),
            end = Offset(w * 0.5f, h),
        )
    )
    // J3×J1: 金の敷居＝時刻 warm を基底に読進 open ぶんを加算（mock rgba(214,196,120, var(--warm)+.20*var(--open))）。
    //   AmbDarkGoldPortal は RGB(214,196,120) 同値＝α だけ差し替えて再利用（発明なし）。
    drawRect(
        Brush.radialGradient(
            colors = listOf(AmbDarkGoldPortal.copy(alpha = (p.warm + 0.20f * open).coerceIn(0f, 1f)), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.08f),
            radius = w * 0.9f,
        )
    )
    // J3: 朝だけの澄んだ緑の冷光（mock rgba(159,207,169, var(--cool))＝GreenPortal 同 RGB。cool=0 の夕夜は描かない）。
    if (p.cool > 0f) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(GreenPortal.copy(alpha = p.cool), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.02f),
                radius = w * 0.9f,
            )
        )
    }
    // 作品ごとの色相グロー（署名3色を id で割当・上寄り。モック「3系統以内」・J1/J3 と非干渉の作品差）。
    drawRect(
        Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.14f),
            radius = w * 0.75f,
        )
    )
    // J3: 底の苔＝時刻で沈む（floorAlpha 夕.9→夜.92／夜は floorDarken で外殻 PagePortal へ寄せ暗化＝既存 val の lerp）。
    val floor = lerp(AmbDarkMossPortal, PagePortal, p.floorDarken).copy(alpha = p.floorAlpha)
    drawRect(
        Brush.radialGradient(
            colors = listOf(floor, Color.Transparent),
            center = Offset(w * 0.5f, h * 1.15f),
            radius = w * 1.1f,
        )
    )
}

// ============================================================
// topbar（.topbar＝メニュー⋮ ／ 位置 idx ／ 見つける・装いの間・グリッド）
// ============================================================
@Composable
private fun PortalTopBar(
    indexLabel: String,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onToggleList: () -> Unit,
    onFabClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .topbar padding 0 18px→S16
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // メニュー⋮（左）＝テーマ3択＋新着通知（J は3変種）。
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            PortalIconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー", tint = InkPortal, modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // PDF追加＝モックは扉クロームに追加導線を持たない（発見扉は「新しい物語＝発見」で、手元 PDF 取込とは別）。
                // メニュー導線（モックの topbar 三点＝本メニュー）へ移植して全数担保する（M の SkyHorizon・P の SlotAdd と同趣旨）。
                DropdownMenuItem(
                    text = { Text("PDFを追加") },
                    onClick = { menuOpen = false; onFabClick() },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
                HorizontalDivider()
                PortalThemeMenuSection(appTheme, onThemeChange) { menuOpen = false }
                NewEpisodeNotificationMenuSection()
            }
        }
        // 位置 idx「n / N」（中央・明朝・字間広め。発見扉では空）。
        Text(
            text = indexLabel,
            fontFamily = MinchoFamily,
            fontSize = 13.sp,                   // .topbar .idx 13px
            letterSpacing = 0.12.em,
            color = IdxInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.S8),
        )
        // 見つける（🔍）。
        PortalIconButton(onClick = onOpenDiscovery) {
            Icon(Icons.Filled.Search, contentDescription = "見つける", tint = InkPortal, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(Spacing.S4))
        // 装いの間（金縁でほのめかす＝スキン切替入口・ADR 0021 決定7）。
        PortalIconButton(onClick = onOpenWardrobe, ward = true) {
            Icon(Icons.Filled.Checkroom, contentDescription = "着せ替え", tint = GoldPortal, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(Spacing.S4))
        // 全体をグリッドで見る＝一覧＝D 構造フォールバックへ（デッキ⇄一覧トグルの機能維持）。
        PortalIconButton(onClick = onToggleList) {
            Icon(Icons.Filled.GridView, contentDescription = "一覧表示に切替", tint = InkPortal, modifier = Modifier.size(19.dp))
        }
    }
}

/** topbar のアイコンボタン（.ib＝38dp・角丸11・薄暗地＋温白ヘアライン。ward は金縁）。 */
@Composable
private fun PortalIconButton(
    onClick: () -> Unit,
    ward: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(PortalIbBg)
            .border(1.dp, if (ward) PortalWardBorder else PortalIbBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** ⋮メニューのテーマ節（J は DARK/LIGHT/SEPIA の3変種＝supportedThemes>1 のとき出す・D/P メニューと同機能）。 */
@Composable
private fun PortalThemeMenuSection(
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    if (LocalSkinTokens.current.supportedThemes.size <= 1) return
    Text(
        "テーマ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
    )
    ReadingTheme.values().forEach { theme ->
        DropdownMenuItem(
            text = {
                Text(
                    when (theme) {
                        ReadingTheme.LIGHT -> "ライト"
                        ReadingTheme.SEPIA -> "セピア"
                        ReadingTheme.DARK -> "ダーク"
                    }
                )
            },
            onClick = { onThemeChange(theme); onDismiss() },
            leadingIcon = {
                if (appTheme == theme) {
                    Icon(Icons.Filled.Check, contentDescription = "選択中", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Spacer(Modifier.width(Spacing.S24))
                }
            },
        )
    }
    HorizontalDivider()
}

// ============================================================
// 絞り込みチップ（.chipbar＝読書状態フィルタへ写像・M/P と同機能）
// ============================================================
@Composable
private fun PortalChips(
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
            .padding(horizontal = Spacing.S24, vertical = Spacing.S4), // .chipbar padding 12px 22px 4px→S24/S4
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        entries.forEach { (status, label) ->
            val isOn = selected == status
            val isEmpty = status != null && (counts[status] ?: 0) == 0
            Text(
                text = label,
                fontSize = 11.5.sp,             // .lchip 11.5px
                fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isOn) GoldPortal else SoftPortal, // .lchip.on color:gold ／ off:soft
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(ChipBg)
                    .border(
                        1.dp,
                        if (isOn) ChipOnBorder else LinePortal, // on=金縁 ／ off=--line
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(enabled = !isEmpty) { onSelect(status) }
                    .padding(horizontal = Spacing.S12, vertical = Spacing.S8), // .lchip padding 7px 14px→S8/S12
            )
        }
    }
}

// ============================================================
// 取込中バナー（.proc＝扉を仕立てている＝ProcessingBanner の J 意匠）
// ============================================================
@Composable
private fun PortalProcessingBanner(state: ProcessingState, onStop: () -> Unit) {
    val stepLabels = listOf("タイトル", "本文", "分割", "HTML")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24, vertical = Spacing.S4) // .proc margin 10px 22px→S24/S4
            .clip(RoundedCornerShape(12.dp))
            .background(ProcBg)
            .border(1.dp, LinePortal, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.S12, vertical = Spacing.S12), // .proc padding 12px 14px→S12
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // スピナー跡（.sp＝金トップの環。静止＝J はモーション無し・回転させない）。
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(2.dp, GoldPortal, CircleShape),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.S8)) {
                Text(
                    if (state.isStopping) "停止しています…" else state.title.ifEmpty { "PDF" },
                    fontSize = 12.sp,           // .proc .pt 12px
                    fontWeight = FontWeight.Medium,
                    color = InkPortal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (state.phase.isNotEmpty()) {
                    Text(
                        state.phase,
                        fontSize = 10.5.sp,     // .proc .ph 10.5px
                        color = SoftPortal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                }
            }
            if (state.queueTotal > 1) {
                Text(
                    "${state.queueCurrent}/${state.queueTotal}件",
                    fontSize = 10.5.sp,         // .proc .ct 10.5px
                    color = InkPortal,
                )
            }
            // 停止（.stop＝金文字）。停止処理中は連打防止で隠す（D/P と同機能）。
            if (!state.isStopping) {
                Text(
                    "停止",
                    fontSize = 12.sp,           // .proc .stop 12px
                    color = GoldPortal,
                    modifier = Modifier
                        .padding(start = Spacing.S4)
                        .clickable(onClick = onStop)
                        .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                )
            }
        }
        // 4段ステッパー（.steps/.labels＝stepIndex/stepTotal 駆動＝実パイプラインの進捗）。
        PortalSteps(
            stepIndex = state.stepIndex,
            stepTotal = state.stepTotal,
            labels = stepLabels,
            modifier = Modifier.padding(top = Spacing.S12),
        )
    }
}

/** 取込ステップのドット列＋ラベル（.steps/.labels）。値の正本＝ProcessingState.stepIndex/stepTotal。 */
@Composable
private fun PortalSteps(stepIndex: Int, stepTotal: Int, labels: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            repeat(stepTotal) { i ->
                Box(
                    Modifier
                        .size(9.dp)                 // .pdot 9px
                        .clip(CircleShape)
                        .background(if (i <= stepIndex) GoldPortal else StepOff),
                )
                if (i < stepTotal - 1) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)           // .pln 2px
                            .background(if (i < stepIndex) GoldPortal else StepOff),
                    )
                }
            }
        }
        // ラベルは既定パイプライン（stepTotal==4）のときだけモック語を出す。
        if (stepTotal == 4) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.S4)) {
                labels.forEachIndexed { i, label ->
                    Text(
                        label,
                        fontSize = 10.sp,           // .labels span 10px
                        fontWeight = if (i == stepIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == stepIndex) GoldPortal else SoftPortal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ============================================================
// ページドット（.dots＝作品ドット＋最後尾の発見扉ドット disc）＋スワイプヒント（.hint）
// ============================================================
@Composable
private fun PortalDots(current: Int, bookCount: Int) {
    Row(
        modifier = Modifier.padding(bottom = Spacing.S16), // .dots padding-bottom:20px→S16
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 作品ドット（現在＝伸長した温白／他＝淡い温白）。
        repeat(bookCount) { i ->
            val on = i == current
            Box(
                modifier = Modifier
                    .then(if (on) Modifier.width(22.dp) else Modifier.width(7.dp)) // .dots i.on width:22px
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) InkPortal else DotFaint),
            )
        }
        // 発見扉ドット（.disc＝金の環／現在は金の実丸）。
        val discOn = current >= bookCount
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .then(
                    if (discOn) Modifier.background(GoldPortal)
                    else Modifier.border(1.5.dp, GoldPortal, CircleShape)
                ),
        )
    }
}

@Composable
private fun PortalHint(onDiscovery: Boolean) {
    Text(
        text = if (onDiscovery) "← スワイプで本棚へ戻る" else "← スワイプで次の物語へ →",
        fontSize = 10.sp,                       // .hint 10px
        letterSpacing = 0.1.em,
        color = HintInk,
        modifier = Modifier.padding(bottom = Spacing.S16), // .hint padding-bottom:16px→S16
    )
}
