package com.novelreader.ui.skins.p

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.NewEpisodeNotificationMenuSection
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfChrome
import com.novelreader.ui.skins.ShelfData
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.BlueCartridge
import com.novelreader.ui.theme.BlueInkCartridge
import com.novelreader.ui.theme.BlueLoCartridge
import com.novelreader.ui.theme.InkCartridge
import com.novelreader.ui.theme.InkMidCartridge
import com.novelreader.ui.theme.InkSoftCartridge
import com.novelreader.ui.theme.InslotHiCartridge
import com.novelreader.ui.theme.InslotLoCartridge
import com.novelreader.ui.theme.LcdCartridge
import com.novelreader.ui.theme.LcdFrameCartridge
import com.novelreader.ui.theme.LcdHiCartridge
import com.novelreader.ui.theme.LcdInkCartridge
import com.novelreader.ui.theme.LineCartridge
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.PanelCartridge
import com.novelreader.ui.theme.PlasticCartridge
import com.novelreader.ui.theme.PlasticHiCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.CartridgeGold
import com.novelreader.ui.theme.CartridgeGreen
import com.novelreader.ui.theme.CartridgePlum
import com.novelreader.ui.theme.CartridgePurple
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.MotionSpringBarSettle
import com.novelreader.ui.theme.RedCartridge
import com.novelreader.ui.theme.RedLoCartridge
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================
// スキンP「カートリッジ」の本棚＝携帯ゲーム機 POCKET NOVEL（正本 bookshelf-P.html・ADR 0022 §1 の構造分岐先）。
//
// 思想: 物語はカセット、本棚はカセットライブラリ、「続きから」はいまスロットに挿さっている1本。
// 署名要素: ①緑LCDの NOW PLAYING 画面 ②面取りしたカセット筐体（CutCornerShape 上7dp）＋リブ溝
//           ③7セグ/ビットマップ風の英数チャンネル（monospace）④退色プラ筐体 ⑤緑LCDのセーブ更新しるし。
//
// タイポ裁定（M の明朝と異なる点）: P モックは題名/本文＝ゴシック（--gothic）・英数HUD＝monospace（--pixel）と
//   明記し、明朝（MinchoFamily）は使わない。ゆえに本画面は題名＝既定サンセリフ（=ゴシック）・HUD＝
//   FontFamily.Monospace（=ピクセル/7セグの記号チャンネル）で翻訳する（Typography.kt の MinchoFamily は
//   P では非適用＝モック忠実。字面 sp は正本モックの px を 1:1 で写し各行にモック由来コメントを付す）。
//
// 機能全数の所在（ADR 0022 §1・M の流儀に倣う）: ラック表示（本画面）が続きから/カセット閲覧/取込中/
//   ラベル絞り込み/見つける導線/PDF追加/装い/メニューを担い、選択削除・Web カード操作・グリッドは
//   「一覧表示」＝D 構造フォールバック（トグルで到達・ADR 0021 の D 構造へトークン写像）が担う。
//
// モーション: P モックに keyframes/transition/JS は無い（ADR 0022 §3）＝静止で実装。取込中バナーの
//   出没だけ既存 Motion.kt スロット（reveal/dismiss）を流用する機能フィードバック（D/M と同型）。
//
// 省略した装飾ディテール（近似も発明もしないための明示）:
//   ・機体四隅のネジ（.screw）＝署名要素外の汎用筐体装飾＝省略。
//   ・LCD スタットの TIME（プレイ時間）＝実データに読書時間を持たない＝捏造せず STAGE/TOTAL/CLEAR の3値に絞る。
// 挿入中カセットの淡緑ボディ（.cart.inslot #e6ecd6→#d7e0c2）は Color.kt の Inslot*Cartridge へ実値昇格して配線済み。
// ============================================================

// PixelFamily は package 共有部品へ集約（CartridgePartsP.kt の internal val）＝当ファイルからは参照のみ。

// ラベル識別色（--w1..w4）: 表紙を持たないため作品 id ハッシュで安定した色を引く（並び替えで変わらない）。
// internal 昇格の理由: 一覧面の chip-lb（.li .chip-lb）が同じ作品識別色を引く＝ラックと一覧で同じ本が同色になる整合。
internal val CartridgeLabelPalette = listOf(CartridgeGold, CartridgePurple, CartridgeGreen, CartridgePlum)
internal fun labelColorFor(bookId: String): Color =
    CartridgeLabelPalette[(bookId.hashCode() and 0x7fffffff) % CartridgeLabelPalette.size]

// 描画層で層の上に載る透過色（LCD ドット地・ゲージ空セグ等＝焼き込めず .copy(alpha=) で正本 α を付与）。
private val LcdDot = LcdInkCartridge.copy(alpha = 0.16f)          // .lcd::before rgba(43,54,22,.16) ドットマトリクス
private val LcdGaugeOff = LcdInkCartridge.copy(alpha = 0.16f)     // .lcd-gauge i 空セグ rgba(43,54,22,.16)
private val LcdStatKey = LcdInkCartridge.copy(alpha = 0.7f)       // .lcd-stats .k opacity:.7
private val LcdStatRule = LcdInkCartridge.copy(alpha = 0.35f)     // .lcd-stats .st border-bottom rgba(43,54,22,.35)
private val CartRidge = PlasticLoCartridge.copy(alpha = 0.6f)     // .cart .ridge リブ溝 opacity:.6

// 遊び心P2（ラベル現像）: 取込中カセットのドット絵スプライト（8×8・モック SPRITES.crown を1:1で写経）。
// '#'＝点灯セル。取込フェーズの進行で上から N 段だけ焼き込む（下記 DevelopLabel）。
private val CrownSprite = listOf(
    "#.....#.",
    "#.#.#.#.",
    "#.#.#.##",
    "########",
    ".######.",
    ".######.",
    ".######.",
    "########",
)

// ============================================================
// H3「二画面ヒンジ」の配分モデル（正本 bookshelf-P-header-H3-hinge.html）。
// 上=NOW PLAYING（可変高）／下=ラック（残り全部）を、中央ヒンジバーの上下ドラッグで無段階配分し、
// 離すと 大/均衡/最小 の3ディテントへ吸着する。正本 DETENTS [56,220,340]px は「液晶＋つづきボタン」込みの高さだが、
// Compose では到達性要件（どの段でも「つづきから読む」に届く＝タスク要件）を守るため、この可変高は "液晶面" にだけ効かせ、
// つづきボタンは可変クリップ域の外に独立配置する（段が浅くてもボタンが切れない）＝正本のpx値からボタン分(~74dp)を差し引いた
// 液晶面の取り分 [56,180,260]dp を段とする。均衡/大＝フル液晶＋ボタン、最小＝1行ミニストリップ（自前の▶）へクロスフェード。
// 既定=均衡（index 1）。
// ============================================================
private val HingeDetentsDp = listOf(56f, 180f, 260f) // [最小, 均衡, 大]＝液晶面の取り分(dp)
private val HingeDetentNames = listOf("最小", "均衡", "大")
private const val HingeMinDp = 56f
private const val HingeMaxDp = 260f

// フル液晶⇔ミニのクロスフェード係数（正本 JS fk=(h-100)/60・0=ミニ 1=フル）。0.5 を境に操作対象（クリック可否）を入替。
private fun hingeFullAlpha(heightDp: Float): Float = ((heightDp - 100f) / 60f).coerceIn(0f, 1f)

// つづきボタンの表出係数（液晶がフル寄り＝fullAlpha>0.5 で立ち上げる）。0=畳む(高さ0) 1=全高。最小段では 0＝ミニの▶へ委ねる。
private fun hingeButtonReveal(heightDp: Float): Float = ((hingeFullAlpha(heightDp) - 0.5f) / 0.5f).coerceIn(0f, 1f)

// 現在高に最も近いディテント index（離したときの吸着先・段ラベルの真実源）。
private fun nearestDetentIndex(heightDp: Float): Int =
    HingeDetentsDp.indices.minByOrNull { abs(HingeDetentsDp[it] - heightDp) } ?: 1

@Composable
internal fun BookshelfCartridgeP(
    // 引数の束（2026-07-27 純構造リファクタ）: 没入面＝閲覧専用のため ShelfSelection/ShelfWebActions は
    // シグネチャに存在しない（コンパイル時制約）。theme＝「システムに従う」含む4択の単一真実源
    //（読書設定シート・D 本棚⋮と共有＝2026-07-17 ユーザー裁定②・別状態を新設せず二重管理を避ける）。
    data: ShelfData,
    chrome: ShelfChrome,
    actions: ShelfActions,
    theme: ThemeControl,
    snackbarHostState: SnackbarHostState,
    // ラック⇄一覧の面切替（旧 onToggleList）。状態は rememberShelfFace が所有し閉包で結線される。
    onToggleFace: () -> Unit,
) {
    // ── 束の展開（本体の参照名を変えない局所別名＝挙動・描画とも既存と同一） ──
    val books = data.books
    val progressMap = data.progressMap
    val chapterCountMap = data.chapterCountMap
    val newEpisodeNovelMap = data.newEpisodeNovelMap
    val processingState = chrome.processingState
    val selectedStatus = chrome.selectedStatus
    val statusCounts = chrome.statusCounts
    val onSelectStatus = chrome.onSelectStatus
    val isLoading = chrome.isLoading
    val appTheme = theme.appTheme
    val onThemeChange = theme.onThemeChange
    val followingSystem = theme.followingSystem
    val onFollowSystem = theme.onFollowSystem
    val onOpenBook = actions.onOpenBook
    val onOpenDiscovery = actions.onOpenDiscovery
    val onOpenWardrobe = actions.onOpenWardrobe
    val onFabClick = actions.onFabClick
    val onCancelProcessing = actions.onCancelProcessing
    // 状態フィルタ適用後の可視作品（チップは D と同じ readingStatusFor を単一真実源に使う）。
    val visible = remember(books, progressMap, chapterCountMap, selectedStatus) {
        if (selectedStatus == null) books
        else books.filter { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == selectedStatus }
    }
    // hero＝いま挿さっている1本＝先頭の「よみかけ」（モックの NOW PLAYING / IN SLOT）。
    val hero = remember(visible, progressMap, chapterCountMap) {
        visible.firstOrNull { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == ReadingStatus.READING }
    }

    // ===== H3 二画面ヒンジの状態（hero の有無に依らず順序安定のため無条件に宣言・使用は hero!=null 時のみ）=====
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember(context) { context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE) }
    // reduce-motion（アニメーター無効）: 吸着アニメを止め即時スナップ（正本 cap の prefers-reduced-motion 分岐・M/J と同判定）。
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // ヒンジの取り分を「設定」として尊重: 選んだディテント index を pref p_hinge_detent へ永続化（装い等と同じ app_prefs 流儀）。
    // rememberSaveable でプロセス内（回転・ダーク切替の Activity 再生成）も保持＝アプリ再起動でも取り分が戻る。既定=均衡(1)。
    var detentIndex by rememberSaveable { mutableStateOf(prefs.getInt(PrefKeys.P_HINGE_DETENT, 1).coerceIn(0, 2)) }
    // ライブ高さ(dp)。ドラッグ中は同期更新・離すと最寄りディテントへ animate。plain state のままレイアウト/描画位相で読み、
    // フレーム毎の recomposition を避ける（compose-state-deferred-reads：高さは layout、alpha は graphicsLayer 内で読む）。
    var hingeHeightDp by remember { mutableFloatStateOf(HingeDetentsDp[detentIndex]) }
    val hingeDrag = rememberDraggableState { deltaPx ->
        // ヒンジバーだけがこの縦ドラッグを受ける（下のラック LazyColumn は別 sibling＝スクロールと座を奪い合わない）。
        hingeHeightDp = (hingeHeightDp + deltaPx / density.density).coerceIn(HingeMinDp, HingeMaxDp)
    }
    // 離した瞬間の吸着（onDragStopped は suspend＝draggable のコルーチン内で animate を直接回す）。
    val onHingeSettle: suspend () -> Unit = {
        val nearest = nearestDetentIndex(hingeHeightDp)
        detentIndex = nearest
        prefs.edit().putInt(PrefKeys.P_HINGE_DETENT, nearest).apply()
        val target = HingeDetentsDp[nearest]
        // 吸着は「バーを段へ settle させる」＝Motion.kt の MotionSpringBarSettle（読書没入バーの吸着 spring）を流用。
        if (reduceMotion) hingeHeightDp = target
        else animate(hingeHeightDp, target, animationSpec = MotionSpringBarSettle) { v, _ -> hingeHeightDp = v }
    }

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
            BrandRow(
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                followingSystem = followingSystem,
                onFollowSystem = onFollowSystem,
                onOpenWardrobe = onOpenWardrobe,
                onToggleList = onToggleFace,
                // ラック面では表示切替ボタンは「一覧へ」＝List アイコン（既定）。
                inListMode = false,
            )

            // NOW PLAYING（続きから）＝いま挿さっている1本のときだけ出す（未読/空では続きは無い＝二画面ヒンジも出さない）。
            if (hero != null) {
                // 上画面（可変高）＝フル液晶⇔最小ミニストリップ。高さは下のヒンジバーのドラッグで配分。
                HingedNowPlaying(
                    book = hero,
                    progress = progressMap[hero.id],
                    totalChaps = chapterCountMap[hero.id] ?: 0,
                    heightProvider = { hingeHeightDp },
                    onOpen = { onOpenBook(hero) },
                )
                // 中央ヒンジバー（DSの蝶番＝ドラッグの把手・現在段を表示）。
                HingeBar(
                    heightProvider = { hingeHeightDp },
                    dragState = hingeDrag,
                    onSettle = onHingeSettle,
                )
            }

            // 取込中バナー＝緑LCDの「取り込み中」（実装 ProcessingBanner に対応・出没のみ Motion スロット）。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                WritingBanner(processingState, onCancelProcessing)
            }

            LibraryHeader(count = visible.size)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S24,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                // 見つける導線（.shop）＝新しい物語を見つける（発見ホームへ）。
                item { ShopBand(onClick = onOpenDiscovery) }
                // ラベル絞り込み（.chips）＝実データのフィルタは読書状態＝D と同一機能（M と同じ写像）。
                item { CartridgeChips(selectedStatus, statusCounts, onSelectStatus) }
                // contentType: 単一型（蔵書のみ）だが、先頭の item{}（導線/チップ）と再利用が混ざらないよう明示する（性能のみ・見た目不変）
                items(visible, key = { it.id }, contentType = { it::class }) { book ->
                    CartridgeCard(
                        book = book,
                        progress = progressMap[book.id],
                        totalChaps = chapterCountMap[book.id] ?: 0,
                        novelDetail = book.ncode?.let { newEpisodeNovelMap[it] },
                        isInSlot = book.id == hero?.id,
                        onOpen = { onOpenBook(book) },
                    )
                }
                if (!isLoading && visible.isEmpty()) {
                    item {
                        // 空状態はモック未定義＝最小の一文（発明を最小化・人間検収でモック追補要否を判断）。
                        Text(
                            text = if (selectedStatus == null) "カセットはまだ挿さっていない" else "この棚に該当するカセットは無い",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,
                            letterSpacing = 0.1.em,
                            color = InkSoftCartridge,
                            modifier = Modifier.padding(vertical = Spacing.S40),
                        )
                    }
                }
                // 空きスロット＝PDF追加（.slotadd）。
                item { SlotAdd(onClick = onFabClick) }
            }

            // 機体下端の意匠（.deck: 通気孔＋銘板）＝固定フッタ。
            Deck()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.S40), // 機体下端のデッキ意匠の上へ逃がす
        )
    }
}

// ============================================================
// 機体トップ（.brand＝topbar 相当。銘板＋装い/表示切替/メニューを機体ボタンとして載せる）
// ============================================================
@Composable
internal fun BrandRow(
    appTheme: com.novelreader.ui.theme.ReadingTheme,
    onThemeChange: (com.novelreader.ui.theme.ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onToggleList: () -> Unit,
    // 表示切替ボタンの向き: 一覧面では「ラックへ戻る」＝2x2グリッド（.btn.sq aria-label「ラックへ切替」）、
    // ラック面では「一覧へ」＝リスト線（.btn.sq aria-label「一覧へ切替」）。モックの2面で図柄が入れ替わる。
    inListMode: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 銘板 ▶ POCKET NOVEL（据置の機体銘＝固定チャンネル・.brand .name）。▶ は退色レッド。
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "▶",
                fontFamily = PixelFamily,
                fontSize = 14.sp,             // .brand .name 14px
                fontWeight = FontWeight.Bold,
                color = RedCartridge,
            )
            Text(
                " POCKET NOVEL",
                fontFamily = PixelFamily,
                fontSize = 14.sp,             // .brand .name 14px
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = InkMidCartridge,      // #4a473e 相当＝AA を満たす --ink-mid で受ける
            )
        }
        // 装い（.btn.dress）＝装いの間へ。緑LCD の押しボタン意匠でほのめかす。
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
                .clickable(onClick = onOpenWardrobe)
                .padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Checkroom,
                contentDescription = "着せ替え",
                tint = LcdInkCartridge,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "装い",
                fontFamily = PixelFamily,
                fontSize = 10.sp,             // .ctl .btn .lab 10px
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
                modifier = Modifier.padding(start = Spacing.S4),
            )
        }
        Spacer(Modifier.width(Spacing.S8))
        // 表示切替（.btn.sq）。ラック⇄一覧を往復＝両面とも P 自身の意匠（ADR 0022 追記その2＝D 構造フォールバック廃止）。
        PlasticSquareButton(onClick = onToggleList) {
            Icon(
                if (inListMode) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                contentDescription = if (inListMode) "ラック表示に切替" else "一覧表示に切替",
                tint = InkCartridge,
                modifier = Modifier.size(18.dp),
            )
        }
        // メニュー（⋮）は撤去した（系2）: 中身がテーマ4択＋新着通知＝いずれも設定タブ（SettingsScreenK）へ移行済みで
        // 重複していた。撤去すると⋮は空になるためボタンごと除く（装い・表示切替は別ボタンとして温存＝モック P の頭も⋮無し）。
    }
}

/** プラスチックの角ボタン（.ctl .btn.sq: plastic-hi→plastic-lo・角丸8dp）。 */
@Composable
private fun PlasticSquareButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(PlasticHiCartridge, PlasticLoCartridge)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * ⋮メニューのテーマ節（P は LIGHT/SEPIA/DARK の3変種＝supportedThemes>1 のとき出す・D メニューと同機能）。
 * 先頭に「システムに従う」を足した4択で読書設定シート・D 本棚⋮と規則を統一する（2026-07-17 ユーザー裁定②）:
 * 一度でも明示テーマを押すと OS 追従へ戻せなかった不整合を、同じ単一真実源（followingSystem＝reading_theme
 * 未宣言・切替は onFollowSystem）を共有して塞ぐ。追従中はそれのみチェック・明示3択は !followingSystem 排他。
 */
@Composable
private fun ThemeMenuSection(
    appTheme: com.novelreader.ui.theme.ReadingTheme,
    onThemeChange: (com.novelreader.ui.theme.ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 1変種スキンでは無意味なので節ごと畳む（supportedThemes を単一真実源に＝D トップバーと同じ機構）。
    if (LocalSkinTokens.current.supportedThemes.size <= 1) return
    Text(
        "テーマ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
    )
    // システムに従う（先頭）。追従中のみチェック＝「何を宣言したか」を表す（明示3択とは排他・読書シートと同一規則）。
    DropdownMenuItem(
        text = { Text("システムに従う") },
        onClick = { onFollowSystem(); onDismiss() },
        leadingIcon = {
            if (followingSystem) {
                Icon(Icons.Filled.Check, contentDescription = "選択中", tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.width(Spacing.S24))
            }
        },
    )
    com.novelreader.ui.theme.ReadingTheme.values().forEach { theme ->
        DropdownMenuItem(
            text = {
                Text(
                    when (theme) {
                        com.novelreader.ui.theme.ReadingTheme.LIGHT -> "ライト"
                        com.novelreader.ui.theme.ReadingTheme.SEPIA -> "セピア"
                        com.novelreader.ui.theme.ReadingTheme.DARK -> "ダーク"
                    }
                )
            },
            onClick = { onThemeChange(theme); onDismiss() },
            leadingIcon = {
                // 追従中は明示3択をどれも未選択にする（宣言済みのときだけ該当テーマにチェック）。
                if (!followingSystem && appTheme == theme) {
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
// 緑LCD の NOW PLAYING（.lcdframe > .lcd＝続きから）
// ============================================================
@Composable
internal fun LcdNowPlaying(book: BookEntity, progress: ProgressEntity?, totalChaps: Int) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val pct = ((frac ?: 0f) * 100).roundToInt()

    // 暗いベゼル（.lcdframe＝#2b2d24→#3a3d33 のグラデを代表単色 --lcd-frame で表現＝SkinP の代表単色流儀）。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24)
            .clip(RoundedCornerShape(14.dp))
            .background(LcdFrameCartridge)
            .padding(Spacing.S8),
    ) {
        // 液晶面（.lcd＝lcd-hi→lcd の放射グラデ＋ドットマトリクス地）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
                    drawLcdDots(LcdDot)
                }
                .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(LcdInkCartridge))  // .lcd-top .bl 6px 角ブロック
                Text(
                    "NOW PLAYING",
                    fontFamily = PixelFamily,
                    fontSize = 10.sp,          // .lcd-top 10px
                    letterSpacing = 0.22.em,
                    color = LcdInkCartridge,
                    modifier = Modifier.padding(start = Spacing.S8),
                )
            }
            Text(
                book.title,
                fontSize = 15.sp,              // .lcd-title 15px（ゴシック）
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.S8),
            )
            // スタット（STAGE/TOTAL/CLEAR）。TIME＝読書時間は実データに無い＝捏造せず省く。
            Column(modifier = Modifier.padding(top = Spacing.S12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S16)) {
                    LcdStat("STAGE", (chapNum ?: 1).toString(), Modifier.weight(1f))
                    LcdStat("TOTAL", totalChaps.toString(), Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S16),
                    modifier = Modifier.padding(top = Spacing.S4),
                ) {
                    LcdStat("CLEAR", "$pct%", Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
            // 進捗ゲージ（.lcd-gauge＝20 セグ・充填＝%）。
            SegGauge(
                total = 20,
                filled = (pct / 100f * 20).roundToInt(),
                onColor = LcdInkCartridge,
                offColor = LcdGaugeOff,
                modifier = Modifier.fillMaxWidth().height(9.dp).padding(top = Spacing.S12),
            )
        }
    }
    Spacer(Modifier.height(Spacing.S12))
}

/** LCD スタット1件（.lcd-stats .st: k=キー・v=値＝ともに monospace・下辺に破線ルール）。 */
@Composable
private fun LcdStat(key: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.drawBehind {
            // 破線ルール（.st border-bottom:1px dashed）。
            val y = size.height
            drawLine(
                LcdStatRule,
                Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
            )
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            key,
            fontFamily = PixelFamily,
            fontSize = 9.5.sp,                // .lcd-stats .k 9.5px
            letterSpacing = 0.14.em,
            color = LcdStatKey,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontFamily = PixelFamily,
            fontSize = 15.sp,                 // .lcd-stats .v 15px
            fontWeight = FontWeight.Bold,
            color = LcdInkCartridge,
        )
    }
}

/** 続きから読む（.start＝退色レッドの厚みボタン）。0 4px 0 red-lo の段差は下地の一段で表す。 */
@Composable
internal fun StartButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24),
    ) {
        // 段差の下地（3D の縁＝box-shadow 0 4px 0 red-lo）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(RedLoCartridge),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(listOf(RedCartridge, RedLoCartridge)))
                .clickable(onClick = onClick)
                .padding(Spacing.S16),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                "つづきから読む",
                fontSize = 16.sp,             // .start .lab 16px（ゴシック）
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = Spacing.S8),
            )
        }
    }
    Spacer(Modifier.height(Spacing.S12))
}

// ============================================================
// H3 二画面ヒンジ 上画面（.tops＝可変高。フル液晶⇔最小ミニストリップをクロスフェード）
// ============================================================
/**
 * NOW PLAYING の上画面を [heightProvider]（=液晶面の取り分dp）へ可変表示する。正本 .tops（overflow:hidden で高さ可変）の翻訳。
 * 2要素を上下に積む:
 *  1) 液晶面ボックス（可変高・clip）＝フル液晶（LcdNowPlaying）⇔最小ミニストリップ をクロスフェード。
 *     高さは Modifier.layout（レイアウト位相）・alpha は graphicsLayer（描画位相）で読む＝ドラッグ連続更新でも recomposition ゼロ。
 *  2) つづきボタン（クリップ域の外・独立）＝フル寄り(fullAlpha>0.5)でのみ高さを立ち上げ表出。段が浅くても切れない＝
 *     「どの段でも つづきから読む に到達可」（タスク要件）を保証。最小段は 0 に畳み、ミニの▶へ到達を委ねる。
 */
@Composable
private fun HingedNowPlaying(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    heightProvider: () -> Float,
    onOpen: () -> Unit,
) {
    // ミニ用の進捗（フル LcdNowPlaying と同じ値源＝別値を捏造しない）。
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val pct = ((frac ?: 0f) * 100).roundToInt()

    // (1) 液晶面ボックス（可変高・はみ出しは clip＝正本 .tops overflow:hidden と同値）。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // ライブ高さはここ（レイアウト位相）で読む＝ドラッグ中も recomposition ゼロ。枠を h に固定しはみ出しは clip で切る。
                val h = heightProvider().dp.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .clipToBounds(),
    ) {
        // フル液晶（自然高・上寄せ）。alpha は graphicsLayer 内で読む＝描画位相の deferred read。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = hingeFullAlpha(heightProvider()) },
        ) {
            LcdNowPlaying(book = book, progress = progress, totalChaps = totalChaps)
        }
        // 最小段のミニストリップ。操作対象の入替（クリック可否）は閾値越えでのみ切替＝derivedState（フレーム毎の再構成を避ける）。
        val miniInteractive by remember { derivedStateOf { hingeFullAlpha(heightProvider()) < 0.5f } }
        MiniStrip(
            title = book.title,
            pct = pct,
            interactive = miniInteractive,
            onOpen = onOpen,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - hingeFullAlpha(heightProvider()) },
        )
    }

    // (2) つづきボタン（可変クリップ域の外）。フル寄りで高さを立ち上げ、最小段では 0 へ畳む＝ミニの▶へ委ねる。
    // 高さの畳み込み・フェードとも layout/描画位相で処理＝閾値越えの recomposition もラック(LazyColumn)へ波及しない。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val bh = (placeable.height * hingeButtonReveal(heightProvider())).roundToInt().coerceAtLeast(0)
                layout(placeable.width, bh) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .graphicsLayer { alpha = hingeButtonReveal(heightProvider()) },
    ) {
        StartButton(onClick = onOpen)
    }
}

/** 最小段の1行ストリップ（.mini＝題名＋微ゲージ＋▶）。液晶面として見せ、▶ が続きから到達口。 */
@Composable
private fun MiniStrip(
    title: String,
    pct: Int,
    interactive: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // 液晶面（.mini .strip＝inset 6px・lcd-hi→lcd 放射グラデ＋ドット地）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.S8)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
                    drawLcdDots(LcdDot)
                },
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NOW",
                fontFamily = PixelFamily,
                fontSize = 8.5.sp,            // .mini .np 8.5px
                letterSpacing = 0.2.em,
                color = LcdInkCartridge.copy(alpha = 0.65f), // .mini .np opacity:.65
            )
            Text(
                title,
                fontSize = 13.sp,            // .mini .tt 13px（ゴシック）
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.S8),
            )
            // 微ゲージ（.mini .mg＝10 セグ・充填＝実進捗%。62% 相当はデータ由来で捏造しない）。
            SegGauge(
                total = 10,
                filled = (pct / 100f * 10).roundToInt(),
                onColor = LcdInkCartridge,
                offColor = LcdGaugeOff,
                modifier = Modifier.width(44.dp).height(10.dp),
            )
            Text(
                "$pct%",
                fontFamily = PixelFamily,
                fontSize = 11.sp,            // .mini .pct 11px
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
                modifier = Modifier.padding(start = Spacing.S8),
            )
            // ▶ つづきから（.mini .play＝退色レッドの小ボタン）。最小段のときだけ操作対象（interactive）。
            Box(
                modifier = Modifier
                    .padding(start = Spacing.S8)
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(RedCartridge, RedLoCartridge)))
                    .clickable(enabled = interactive, onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "つづきから読む", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ============================================================
// H3 二画面ヒンジ 中央バー（.hinge＝ドラッグの把手・現在段ラベル）
// ============================================================
/**
 * 上下2画面の配分ハンドル。縦ドラッグ（[dragState]）で上画面高を無段階に更新し、離すと [onSettle] が最寄り段へ吸着。
 * このバーだけが縦ドラッグを受ける＝下のラック（LazyColumn）のスクロールと座を奪い合わない（競合処理はここで完結）。
 */
@Composable
private fun HingeBar(
    heightProvider: () -> Float,
    dragState: DraggableState,
    onSettle: suspend () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24, vertical = Spacing.S4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(PlasticHiCartridge, PlasticLoCartridge)))
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { onSettle() },
                )
                .semantics { contentDescription = "上下2画面の配分（ヒンジ）" },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 把手のグリップ（.hinge .grip＝横バー2本の縦積み）。
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(22.dp).height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PlasticLoCartridge),
                    )
                }
            }
        }
        // 現在段ラベル（.hinge .det＝右）。閾値をまたぐときだけ更新＝derivedState（フレーム毎の再構成を避ける）。
        val name by remember { derivedStateOf { HingeDetentNames[nearestDetentIndex(heightProvider())] } }
        Text(
            name,
            fontFamily = PixelFamily,
            fontSize = 8.5.sp,               // .hinge .det 8.5px
            letterSpacing = 0.14.em,
            color = InkSoftCartridge,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.S12),
        )
    }
}

// ============================================================
// 取込中バナー（.writing＝緑LCD の「取り込み中」＝ProcessingBanner の P 意匠）
// ============================================================
@Composable
internal fun WritingBanner(state: ProcessingState, onStop: () -> Unit) {
    // モックのステップ名（4段＝題名/本文/分割/HTML）。実パイプラインの stepTotal は 4（ProcessingState 既定）。
    val stepLabels = listOf("題名", "本文", "分割", "HTML")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24, vertical = Spacing.S4)
            .clip(RoundedCornerShape(11.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge)))
                drawLcdDots(LcdDot)
            }
            .padding(horizontal = Spacing.S12, vertical = Spacing.S12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 遊び心P2（ラベル現像）: 取込フェーズ（stepIndex/stepTotal＝実パイプライン進捗）を 0..8 段へ
            // 量子化し、カセットのラベルを上から焼き込む。モックの独立「取込中カセット」は Compose では
            // 実 BookEntity が未生成のため描けない＝データ源のある本バナーへ現像ラベルを織り込む（届く範囲で最大限）。
            DevelopLabel(
                stepIndex = state.stepIndex,
                stepTotal = state.stepTotal,
                modifier = Modifier.padding(end = Spacing.S8),
            )
            Text(
                "取り込み中",
                fontFamily = PixelFamily,
                fontSize = 9.5.sp,            // .writing .wtag 9.5px
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.16.em,
                color = LcdInkCartridge,
            )
            Text(
                if (state.isStopping) "停止しています…" else state.title.ifEmpty { "PDF" },
                fontSize = 12.5.sp,           // .writing .wt 12.5px
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.S8),
            )
            if (state.queueTotal > 1) {
                Text(
                    "${state.queueCurrent}/${state.queueTotal}件",
                    fontFamily = PixelFamily,
                    fontSize = 10.sp,         // .writing .wcount 10px
                    color = LcdInkCartridge,
                )
            }
            // 停止（.wstop＝ラベル1語「停止」・LCD 枠ボタン）。停止処理中は連打防止で隠す（D と同機能）。
            if (!state.isStopping) {
                Text(
                    "停止",
                    fontFamily = PixelFamily,
                    fontSize = 10.sp,         // .writing .wstop 10px
                    fontWeight = FontWeight.Bold,
                    color = LcdInkCartridge,
                    modifier = Modifier
                        .padding(start = Spacing.S8)
                        .border(1.dp, LcdStatRule, RoundedCornerShape(4.dp))
                        .clickable(onClick = onStop)
                        .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                )
            }
        }
        // フェーズ詳細（.wphase＝ページ進捗など。生成元の phase 文字列をそのまま出す）。
        if (state.phase.isNotEmpty()) {
            Text(
                state.phase,
                fontSize = 10.5.sp,           // .writing .wphase 10.5px
                color = LcdStatKey,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.S8),
            )
        }
        // ステップドット（.wsteps/.wlabels＝stepIndex/stepTotal 駆動＝実パイプラインの進捗）。
        WritingSteps(
            stepIndex = state.stepIndex,
            stepTotal = state.stepTotal,
            labels = stepLabels,
            modifier = Modifier.padding(top = Spacing.S8),
        )
    }
}

/**
 * 遊び心P2（ラベル現像）: 取込中カセットのラベルが上から一段ずつ焼き込まれる現像（.clabel.burn の Compose 翻訳）。
 * ラベル＝8×8 ドット絵（CrownSprite）を、取込フェーズの進行 (stepIndex+1)/stepTotal を 0..8 段へ量子化して
 * 上から N 段だけ点灯する。値の正本＝ProcessingState.stepIndex/stepTotal（実パイプライン進捗）＝捏造なし。
 * 単調性: stepIndex は取込中に増える一方＝段は下へ伸びるだけで戻らない（モックの「進むと単調に増え戻さない」）。
 * 色は署名の LCD 緑に閉じる（点灯＝LcdInk／地＝LCD 面上に載るので暗めの LcdFrame で沈めコントラストを確保）。
 *
 * モーションの裁定: モックの走査線 scanBlink は装飾の無限ループ＝ADR 0022 §3 の「P は静止」/静謐則（motion は
 *   フィードバックのみ）に抵触するため採らない。現像そのもの（段が実ステップで増える）が状態変化の feedback。
 */
@Composable
private fun DevelopLabel(stepIndex: Int, stepTotal: Int, modifier: Modifier = Modifier) {
    // (stepIndex+1)/stepTotal を 0..8 段へ量子化（本文=index1/total4 → 0.5 → 4段＝モック注記と一致）。
    val rows = if (stepTotal <= 0) 0
    else (((stepIndex + 1).toFloat() / stepTotal) * 8f).roundToInt().coerceIn(0, 8)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LcdFrameCartridge)                                        // 暗い焼込み面（.clabel.burn 暗地）
            .border(1.dp, LcdInkCartridge.copy(alpha = 0.6f), RoundedCornerShape(4.dp)), // inset 1.5px rgba(43,54,22,.6)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(Spacing.S4)) {
            val cell = size.width / 8f
            CrownSprite.forEachIndexed { y, line ->
                if (y < rows) {  // 上から rows 段だけ焼き込む（未到達段は暗地のまま＝現像途中）
                    line.forEachIndexed { x, ch ->
                        if (ch == '#') {
                            drawRect(
                                LcdInkCartridge,  // 点灯セル＝署名の LCD 緑インク
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 取込ステップのドット列＋ラベル（.wsteps/.wlabels）。値の正本＝ProcessingState.stepIndex/stepTotal。 */
@Composable
private fun WritingSteps(stepIndex: Int, stepTotal: Int, labels: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            repeat(stepTotal) { i ->
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (i <= stepIndex) LcdInkCartridge else LcdGaugeOff)
                )
                if (i < stepTotal - 1) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (i < stepIndex) LcdInkCartridge else LcdGaugeOff)
                    )
                }
            }
        }
        // ラベルは stepTotal==4（既定パイプライン）のときだけモック語（題名/本文/分割/HTML）を出す。
        if (stepTotal == 4) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.S4)) {
                labels.forEachIndexed { i, label ->
                    Text(
                        label,
                        fontFamily = PixelFamily,
                        fontSize = 8.5.sp,   // .wlabels span 8.5px
                        fontWeight = if (i == stepIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == stepIndex) LcdInkCartridge else LcdStatKey,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ============================================================
// カセットライブラリ見出し（.lib-h）
// ============================================================
@Composable
internal fun LibraryHeader(count: Int, countPrefix: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S24, vertical = Spacing.S8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "CARTRIDGE LIBRARY",
            fontFamily = PixelFamily,
            fontSize = 11.sp,                 // .lib-h .t 11px
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.16.em,
            color = InkMidCartridge,
        )
        Text(
            // 一覧面は .lib-h .n = 「一覧 · 04 本」＝ countPrefix で「一覧 · 」を前置（ラック面は空）。
            "$countPrefix%02d 本".format(count),
            fontFamily = PixelFamily,
            fontSize = 11.sp,                 // .lib-h .n 11px
            letterSpacing = 0.1.em,
            color = InkMidCartridge,          // モック --ink-soft は AA 不足のため意味メタは --ink-mid
        )
    }
}

// ============================================================
// 見つける導線（.shop）
// ============================================================
@Composable
internal fun ShopBand(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(PlasticHiCartridge, PanelCartridge)))
            .border(1.dp, LineCartridge, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S12, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 緑LCD の検索アイコン枠（.shop .si）。
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(LcdHiCartridge, LcdCartridge))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = LcdInkCartridge, modifier = Modifier.size(19.dp))
        }
        Text(
            "新しい物語を見つける",
            fontSize = 13.5.sp,               // .shop .l1 13.5px（ゴシック）
            fontWeight = FontWeight.Bold,
            color = InkCartridge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.S12),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InkSoftCartridge,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ============================================================
// ラベル絞り込みチップ（.chips＝読書状態フィルタへ写像・M と同機能）
// ============================================================
@Composable
internal fun CartridgeChips(
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
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        entries.forEach { (status, label) ->
            val isOn = selected == status
            val isEmpty = status != null && (counts[status] ?: 0) == 0
            Text(
                text = label,
                fontSize = 12.sp,             // .chip 12px（ゴシック）
                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isOn -> LcdInkCartridge   // .chip.on color:lcd-ink
                    isEmpty -> InkSoftCartridge // 0件はさらに沈める（D の 0件 dim と同機能）
                    else -> InkMidCartridge   // .chip color:ink-mid
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isOn) LcdCartridge else PlasticHiCartridge) // on=lcd 地／off=plastic-hi
                    .border(
                        1.dp,
                        if (isOn) Color.Transparent else LineCartridge,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(enabled = !isEmpty) { onSelect(status) }
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
            )
        }
    }
}

// ============================================================
// 1本のカセット（.cart＝面取り筐体＋リブ＋ラベル＋題名＋セーブデータ）
// ============================================================
@Composable
private fun CartridgeCard(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    novelDetail: WorkSummary?,
    isInSlot: Boolean,
    onOpen: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val status = readingStatusFor(progress, totalChaps)
    val isUnread = status == ReadingStatus.UNREAD
    // 読了＝reachedEnd の実績（progressFractionFor が高%でも reachedEnd 無しは FINISHED にしない＝嘘の100%を出さない）。
    // 遊び心P1: この実績があるときだけ進捗表示を CLEAR‼ 刻印へ差し替える（近似の pct>=100 では判定しない＝正直さ）。
    val isFinished = status == ReadingStatus.FINISHED
    val pct = ((frac ?: 0f) * 100).roundToInt()
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)
    val labelColor = labelColorFor(book.id)
    // 面取り筐体（clip-path polygon 上7px＝CutCornerShape 上端7dp。署名④）。
    val cartShape = CutCornerShape(topStart = 7.dp, topEnd = 7.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cartShape)
            // 挿入中は淡緑ボディ地（.cart.inslot #e6ecd6→#d7e0c2）、通常は退色プラ（.cart plastic-hi→panel）。
            .background(
                if (isInSlot) Brush.linearGradient(listOf(InslotHiCartridge, InslotLoCartridge))
                else Brush.linearGradient(listOf(PlasticHiCartridge, PanelCartridge))
            )
            .clickable(onClick = onOpen),
    ) {
        Column {
            // リブ（グリップ溝・.cart .ridge＝反復ストライプ）。
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S4),
            ) {
                val stripe = 2.dp.toPx()
                val gap = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(CartRidge, topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                    x += stripe + gap
                }
            }
            Row(
                modifier = Modifier.padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S8, bottom = Spacing.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ラベル（.clabel＝識別色の面。ピクセルスプライトは実データに無いため id 色の無地面で代替）。
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(labelColor)
                        .border(1.dp, InkCartridge.copy(alpha = 0.14f), RoundedCornerShape(5.dp)),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.S12)) {
                    Text(
                        book.title,
                        fontSize = 13.5.sp,       // .cmeta .ct 13.5px（ゴシック）
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        color = InkCartridge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (book.author.isNotBlank()) {
                        Text(
                            book.author,
                            fontSize = 11.sp,     // .cmeta .cb 11px
                            color = InkMidCartridge, // モック --ink-soft は AA 不足＝意味メタは --ink-mid
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.S4),
                        )
                    }
                }
                // セーブデータ（.csave＝STAGE/ゲージ/%/更新しるし）。右寄せ固定幅。
                Column(
                    modifier = Modifier.width(82.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (isInSlot) {
                        Text(
                            "▶ IN SLOT",
                            fontFamily = PixelFamily,
                            fontSize = 8.5.sp,    // .slot 8.5px
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em,
                            color = LcdInkCartridge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(LcdCartridge)
                                .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                        )
                        Spacer(Modifier.height(Spacing.S4))
                    }
                    if (isUnread) {
                        Text(
                            "全${totalChaps}話",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,     // .csave .stage 11px
                            color = InkSoftCartridge, // .csave.nodata .stage
                        )
                        Text(
                            "未読",
                            fontFamily = PixelFamily,
                            fontSize = 9.sp,      // .csave .new 9px
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em,
                            color = Color.White,
                            modifier = Modifier
                                .padding(top = Spacing.S8)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RedCartridge)
                                .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                        )
                    } else if (isFinished) {
                        // 遊び心P1（CLEAR封印）: 読了カセットは stage=全話数・ゲージ満充填・進捗表示を CLEAR‼ 刻印へ。
                        // 100% と CLEAR は意味重複ゆえ「同じ場所」を CLEAR‼ が占める（モック④ .csave 構造に忠実）。
                        Text(
                            "全${totalChaps}話",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,     // .csave .stage 11px
                            color = InkCartridge,
                        )
                        SegGauge(
                            total = 10,
                            filled = 10,          // モック data-f="10"＝読了は満充填（reachedEnd が真＝到達済み）
                            onColor = BlueCartridge,
                            offColor = BlueLoCartridge,
                            modifier = Modifier.width(63.dp).height(8.dp).padding(top = Spacing.S4),
                        )
                        ClearMark(modifier = Modifier.padding(top = Spacing.S4))
                    } else {
                        Text(
                            "第${chapNum ?: 1}話",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,     // .csave .stage 11px
                            color = InkCartridge,
                        )
                        SegGauge(
                            total = 10,
                            filled = (pct / 100f * 10).roundToInt(),
                            onColor = BlueCartridge,
                            offColor = BlueLoCartridge,
                            modifier = Modifier.width(63.dp).height(8.dp).padding(top = Spacing.S4),
                        )
                        Text(
                            "$pct%",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,     // .csave .pct 11px
                            fontWeight = FontWeight.Bold,
                            color = BlueInkCartridge,
                            modifier = Modifier.padding(top = Spacing.S4),
                        )
                        if (newCount != null) {
                            // 続きあり＝セーブの更新しるし（.csave .upd＝緑地）。
                            Text(
                                "＋続き${newCount}話",
                                fontFamily = PixelFamily,
                                fontSize = 9.5.sp, // .csave .upd 9.5px
                                fontWeight = FontWeight.Bold,
                                color = LcdInkCartridge,
                                modifier = Modifier
                                    .padding(top = Spacing.S8)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(LcdCartridge)
                                    .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 遊び心P1: CLEAR‼ 刻印チップ（.csave .clearmark）。読了カセットの進捗表示の位置に常時・水平で載る
 * LCD 緑の deboss（刻印）表記。字面は pixel 10sp/weight700/letter-spacing .04em、地=--lcd、
 * 刻印質感は inset 1px rgba(43,54,22,.5)＝LcdInk α.5 の枠で表す（SkinP の代表単色流儀＝二重 inset の
 * 暗部影は省く）。
 *
 * TODO(監督/トリガ配線): モックの「初読了の瞬間に一度きり押印」（reachedEnd false→true の scale 1.15→1.0
 *   スタンプ）は、本コンポーネントが progress のスナップショットしか受けず権威ある遷移イベントを持たない
 *   ため未実装＝常時静的表示にとどめる（近似の in-composition 遷移検出は誤発火＝二度と再生しない要件を
 *   壊すため採らない）。BookCard の playSealStamp/onSealStamped と同型のラッチを骨格（BookshelfContent
 *   経由）から本カードへ配線できれば、MotionDurationSeal/MotionEasingSeal で一度だけ押印できる。
 */
@Composable
private fun ClearMark(modifier: Modifier = Modifier) {
    Text(
        "CLEAR‼",
        fontFamily = PixelFamily,
        fontSize = 10.sp,                 // .clearmark 10px
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,          // .clearmark letter-spacing .04em
        color = LcdInkCartridge,          // .clearmark color --lcd-ink
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))                                   // .clearmark border-radius 3px
            .background(LcdCartridge)                                         // .clearmark background --lcd
            .border(1.dp, LcdInkCartridge.copy(alpha = 0.5f), RoundedCornerShape(3.dp)) // inset 0 0 0 1px rgba(43,54,22,.5)＝刻印枠
            .padding(horizontal = Spacing.S8, vertical = Spacing.S4),        // .clearmark padding 2px 6px（upd と同写像）
    )
}

// ============================================================
// 空きスロット＝PDF追加（.slotadd＝破線の空きスロット）
// ============================================================
@Composable
internal fun SlotAdd(onClick: () -> Unit) {
    val slotShape = CutCornerShape(topStart = 7.dp, topEnd = 7.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(slotShape)
            .border(2.dp, LineCartridge, slotShape)  // .slotadd .in 破線枠（面取り筐体の空きスロット）
            .clickable(onClick = onClick)
            .padding(Spacing.S16),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "＋",
            fontFamily = PixelFamily,
            fontSize = 22.sp,                 // .slotadd .pl 22px
            fontWeight = FontWeight.Bold,
            color = InkSoftCartridge,
        )
        Text(
            "PDFを追加",
            fontSize = 13.sp,                 // .slotadd .l1 13px（ゴシック）
            fontWeight = FontWeight.Bold,
            color = InkMidCartridge,          // #575349 相当＝AA を満たす --ink-mid で受ける
            modifier = Modifier.padding(start = Spacing.S8),
        )
    }
}

// Deck/DeckHoles（機体下端の意匠）は package 共有部品へ集約＝CartridgePartsP.kt の internal Deck() を参照。

// ============================================================
// 本棚P専用の描画ヘルパー（SegGauge は本棚のみ使用＝複製なしゆえ private のまま。ドット地/Deck は CartridgePartsP へ集約）
// ============================================================

/** セグメントゲージ（.lcd-gauge / .csave .gauge＝細セグの並び・gap 1.5px はヘアライン）。 */
@Composable
private fun SegGauge(
    total: Int,
    filled: Int,
    onColor: Color,
    offColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val gap = 1.5.dp.toPx()                          // モック 1.5px の区切りヘアライン
        val segW = ((size.width - gap * (total - 1)) / total).coerceAtLeast(0f)
        for (i in 0 until total) {
            val x = i * (segW + gap)
            drawRect(
                if (i < filled) onColor else offColor,
                topLeft = Offset(x, 0f),
                size = Size(segW, size.height),
            )
        }
    }
}
