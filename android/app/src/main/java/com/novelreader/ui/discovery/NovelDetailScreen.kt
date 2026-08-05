package com.novelreader.ui.discovery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.components.BookCover
import com.novelreader.ui.theme.FontActionLabel
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.FontTopBarTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationCrossfade
import com.novelreader.viewmodel.NovelDetailUiState
import com.novelreader.viewmodel.NovelDetailViewModel
import com.novelreader.ui.theme.Spacing
import java.util.Locale

/**
 * 作品詳細の書影ヒーローの高さ（2026-07-31 ユーザー裁定・旧 200dp）。
 *
 * なぜ縮めたか: 実機で「あらすじがスクロールしないと出てこない」＝ファーストビューの押し下げが真因だった。
 * 内訳はヒーロー 200＋作者行 34＋ステータス表 101 でここまで 351dp、あらすじ見出しは 375dp 目に来る一方、
 * 固定バー（未取込×既読は4アクション＝240dp）と TopAppBar・システムバーを引いたビューポートは
 * 360×800dp 級で約 444dp しかなく、本文は 1.7 行しか覗かなかった。並び順（モック正本 discovery-detail-D.html の
 * ヒーロー→作者→ステータス→あらすじ）は意匠なので変えず、押し下げ量だけを 80dp 削る裁定。
 *
 * なぜ定数化したか: この値はヒーローの高さと「題字を App bar へ出す」スクロール閾値の**両方**が参照する。
 * 旧実装は 200 をリテラルで二重に書いており、片方だけ直せば閾値が静かにズレる（畳の目が合わなくなる）。
 *
 * 縦横比について: [BookCover] は比を持たず Modifier で与えた寸法をそのまま塗るグラデーション面で、
 * ここは `fillMaxWidth()` の帯として使う（モック正本も `.cv{aspect-ratio:2/3}` を `.hero-cv{aspect-ratio:auto}` で
 * 明示的に打ち消している）。よって高さを変えても幅は連動せず、比の破綻は起きない。
 */
private val DetailHeroHeight = 120.dp

/**
 * なろうAPIの日付文字列（`general_lastup`＝"2024-01-05 12:34:56" 形式想定）を
 * 「2024年1月5日 更新」ラベルへ整形する。整形できなければ null（呼び出し側は表示を省く）。
 *
 * なぜ手書きパースか: 表示は年月日だけで足り、SimpleDateFormat 等でパース→再フォーマットする必要が無く、
 * また時刻・タイムゾーンの解釈も不要なため、先頭の日付部を "-" で割って和暦風表記へ組むだけで済む。
 *
 * なぜ NumberFormatException を握り潰すか: なろうAPIはこの日付文字列の形式を公式に保証しておらず、
 * 想定外の形（数値でない・欠損・区切り違い）が来うる。ここは付帯的なメタ表示であり、
 * 解釈できない値で画面を落とすより表示スキップに倒すのが妥当なため、数値変換失敗のみを捕えて null にする。
 * 握り潰す範囲は toInt() の失敗（NumberFormatException）に限定し、他の想定外例外は覆い隠さない。
 */
internal fun formatLastupLabel(raw: String?): String? {
    val datePart = raw?.split(" ")?.firstOrNull() ?: return null
    val ymd = datePart.split("-")
    if (ymd.size < 3) return null
    return try {
        val year = ymd[0].toInt()
        val month = ymd[1].toInt()
        val day = ymd[2].toInt()
        "${year}年${month}月${day}日 更新"
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * 作品詳細画面（モック discovery-detail-D.html）。
 * 書影ヒーロー表示、作者情報、作品ステータス、あらすじ、キーワード、評価項目などを
 * 和モダンの静謐なレイアウトで構築し、最下部に「なろうで読む」外部連携導線を常駐させる。
 */
/**
 * 作品詳細のルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・詳細ロードの起動・状態の collect と、「なろうで読む」の外部ブラウザ起動という
 * プラットフォーム副作用（Custom Tabs＋二重起動ガード）を担い、純粋な描画は [NovelDetailContent] に委ねる
 * （BookshelfScreen と同じ分割方針。プラットフォーム副作用はルート層に置く＝描画層を VM/Context 非依存に保つ）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    ncode: Ncode,
    viewModel: NovelDetailViewModel,
    onSearchKeywords: (List<String>) -> Unit,
    onImportPdf: () -> Unit,
    // 機能②: なろう作品をアプリ内 WebView で読む（読書位置の自動記録・ADR 0012）。外部ブラウザ送客(Custom Tabs)は
    // 廃し、目次(初回)と続きから(記録話へ直接)の2着地をルート層のナビへ委ねる（描画層は callback を叩くだけ）。
    onReadFromToc: () -> Unit,
    onResumeReading: (episode: Int) -> Unit,
    // 作品詳細の ← は階層 up＝一段上の「直近の結果一覧」へ（発見ホーム直行入場だけは発見ホームへ）。
    // システム Back も同じ up で一本（MainActivity の BackHandler・分岐の機序は upFromDiscoveryDetail の KDoc）。
    // 旧「←＝発見ホーム固定 Up／Back＝履歴 pop」の二本立て（D 統一・2026-07-12）は 2026-07-29 ユーザー裁定
    // 「わかりやすく」で廃止＝読書側（章→目次→本棚）と同じ一規則（ADR 0026）。
    onUp: () -> Unit,
) {
    LaunchedEffect(ncode) {
        viewModel.load(ncode)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // (b) 固定バーのトグル表示状態（本棚に置く/外す・取込済みなら2アクション非表示）。
    val onShelf by viewModel.onShelf.collectAsStateWithLifecycle()
    val isImported by viewModel.isImported.collectAsStateWithLifecycle()
    // 機能②: この作品の WebView 読書位置（最後に開いた話。>0 なら「続きから読む」を出す）。
    val lastReadEpisode by viewModel.readingProgress.collectAsStateWithLifecycle()

    NovelDetailContent(
        ncode = ncode,
        uiState = uiState,
        onSearchKeywords = onSearchKeywords,
        onImportPdf = onImportPdf,
        onShelf = onShelf,
        isImported = isImported,
        onToggleShelf = { viewModel.toggleShelf() },
        onUp = onUp,
        onRetry = { viewModel.retry() },
        lastReadEpisode = lastReadEpisode,
        onReadOnNarou = onReadFromToc,
        onResumeReading = { onResumeReading(lastReadEpisode) },
    )
}

/**
 * 作品詳細の描画層（stateless / UI 分割の content）。NovelDetailScreen からの純移動。
 * VM や Context を持たず [ncode]＋[uiState]＋コールバックだけで Loading/NotFound/Error/Content の分岐と
 * ヒーロー・ステータス・あらすじ・キーワード・評価・外部連携導線を描画する葉。スクロール追従の題字表示
 * といった画面ローカル UI 状態のみ内部に残す。外部ブラウザ起動は [onReadOnNarou]、再試行は [onRetry] へ委譲。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NovelDetailContent(
    ncode: Ncode,
    uiState: NovelDetailUiState,
    onSearchKeywords: (List<String>) -> Unit,
    onImportPdf: () -> Unit,
    onUp: () -> Unit,
    onRetry: () -> Unit,
    // 機能②: onReadOnNarou＝目次(最初から)をアプリ内 WebView で開く。onResumeReading＝記録した話へ直接（続きから）。
    // lastReadEpisode>0 のとき「続きから読む（第N話）」を主導線に切り替える。既定値は既存テスト・プレビュー互換のため。
    onReadOnNarou: () -> Unit,
    lastReadEpisode: Int = 0,
    onResumeReading: () -> Unit = {},
    // (b) Web由来カードの入口（固定バーのトグル）。既定値は既存テスト・プレビューの互換のため。
    onShelf: Boolean = false,
    isImported: Boolean = false,
    onToggleShelf: () -> Unit = {},
) {
    // スクロール状態を最上位で保持する（M10/層②）。書影ヒーローと本文タイトルが画面外へ流れたら
    // App bar に作品名を常駐表示し、今どの作品を見ているかの手掛かりが消えないようにするため。
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // ヒーロー高さの6割ほどスクロールしたら、書影上に載る本文タイトルが上端へ抜ける頃合いと見なす。
    // 高さは [DetailHeroHeight] を単一情報源として引く（リテラル二重書きだと高さ変更で閾値だけ取り残される）。
    val heroThresholdPx = remember(density) { with(density) { DetailHeroHeight.toPx() * 0.6f } }
    val showBarTitle by remember {
        derivedStateOf { scrollState.value > heroThresholdPx }
    }
    // なぜフェードか: 既存の同型演出（BookCard の animateFloatAsState）に倣い、出没を滑らかにして
    // スクロールに追従する題字のちらつきを抑える。
    // なぜ animationSpec を明示するか: 既定 spring だと Motion.kt を経由せず野良曲線になる（Design/08 禁止則②
    // ＝duration/easing はトークン経由）。復帰ヒント等と同型の「そっと現れて消える」演出のため crossfade
    // トークン（MotionDurationCrossfade）の tween で一元化する（監査 d-motion Minor）。
    val barTitleAlpha by animateFloatAsState(
        targetValue = if (showBarTitle) 1f else 0f,
        animationSpec = tween(MotionDurationCrossfade),
        label = "detailBarTitle"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 書影が流れて本文タイトルが見えなくなったら作品名を出す（普段は透明で不可視）。
                    val barState = uiState
                    if (barState is NovelDetailUiState.Content) {
                        Text(
                            text = barState.novel.summary.title,
                            fontFamily = MinchoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = FontTopBarTitle,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(barTitleAlpha)
                        )
                    }
                },
                navigationIcon = {
                    // 階層 up（2026-07-29 一本化・ADR 0026）: 一段上＝直近の結果一覧へ（ホーム直行入場は発見ホームへ）。
                    // Back も同じ up（旧「固定 Up／履歴 pop」二本立て＝D 統一 2026-07-12 は廃止）。
                    IconButton(onClick = onUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState is NovelDetailUiState.Content) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.S24, vertical = Spacing.S16)
                        ) {
                            // 機能②: 固定バーの読む/取り込み導線。案A「完全一貫」（2026-07-16 ユーザー裁定）＝
                            // アプリの主目的は「手元に本を置く」＝PDF取り込み。未取込である限り取込を藍の主CTA
                            // 最上段に固定し、既読になっても降格させない（旧 2026-07-12 の「既読は続きからを主」裁定を
                            // 上書き＝状態依存で主従が入れ替わる一貫性欠如を解消）。取込済みなら取込は冗長で消え、
                            // 読む導線を藍の主CTAへ昇格。意匠正本＝discovery-detail-D.html（既読パネルと同期）。
                            // いずれもアプリ内 WebView でなろうページを **加工せず** 表示し話遷移から読書位置を記録（ADR 0012）。
                            if (!isImported) {
                                // 未取込（既読/未読問わず）: 取込を藍の主CTA・最上段に固定（案A）。
                                Button(
                                    onClick = onImportPdf,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.S8))
                                    Text(
                                        text = "縦書きPDFを取り込む",
                                        fontSize = FontActionLabel,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(Spacing.S8))
                                if (lastReadEpisode > 0) {
                                    // 既読: 「続きから読む」はゴースト（主CTAは取込を維持）。着地は記録話の「冒頭」で
                                    // 話内スクロールは復元しない（JS 注入なし＝ADR 0012）ため「第N話のはじめから」と明示。
                                    OutlinedButton(
                                        onClick = onResumeReading,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.S8))
                                        Text(
                                            text = "第${lastReadEpisode}話のはじめから読む",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.S8))
                                    // 「最初から（目次）」は枠も塗りも持たない最下位のテキストリンク（意匠正本 .btn-textlink）。
                                    TextButton(
                                        onClick = onReadOnNarou,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text(
                                            text = "最初から読み直す（目次）",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                } else {
                                    // 未読: 「なろうで読む」はゴースト。
                                    OutlinedButton(
                                        onClick = onReadOnNarou,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.S8))
                                        Text(
                                            text = "なろうで読む",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                }
                            } else {
                                // 取込済: 取込は冗長で非表示。読む導線を藍の主CTAへ昇格（読む導線が主で自然）。
                                if (lastReadEpisode > 0) {
                                    Button(
                                        onClick = onResumeReading,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.S8))
                                        Text(
                                            text = "第${lastReadEpisode}話のはじめから読む",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.S8))
                                    TextButton(
                                        onClick = onReadOnNarou,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text(
                                            text = "最初から読み直す（目次）",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onReadOnNarou,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.S8))
                                        Text(
                                            text = "なろうで読む",
                                            fontSize = FontActionLabel,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                }
                            }
                            // 表示先を明示（アプリ内 WebView でなろうのページを加工せずそのまま表示する＝ADR 0012・公理8）。
                            Text(
                                text = "なろう（syosetu.com）のページをそのまま表示します",
                                fontSize = FontMicroLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.S8),
                                textAlign = TextAlign.Center
                            )
                            // 取り込み済み（books.ncode 一致）なら以下は冗長のため出さない
                            // （モック discovery-detail-D の固定バー注記どおり。読む手段は蔵書カードが正）。
                            if (!isImported) {
                                // (b) Web由来・未取込カードの入口（モック .btn-ghost「本棚に置く」）。
                                // 置いた後は「本棚から外す」へトグルし、押し直しで取り消せる（確認ダイアログ無し
                                // ＝失うものが無く即座に戻せる操作のため）。取込はもう主CTAのためここには置かない（案A）。
                                Spacer(modifier = Modifier.height(Spacing.S8))
                                // 同じ .btn-ghost 系のため取り込みボタンと同一の淡色トークンで揃える。
                                OutlinedButton(
                                    onClick = onToggleShelf,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Icon(
                                        imageVector = if (onShelf) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.S8))
                                    Text(
                                        text = if (onShelf) "本棚から外す" else "本棚に置く",
                                        fontSize = FontActionLabel,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NovelDetailUiState.Loading -> {
                    DiscoveryStatusBox(DiscoveryStatus.Loading, modifier = Modifier.fillMaxSize())
                }
                is NovelDetailUiState.NotFound -> {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Empty("作品が見つかりませんでした（削除または検索除外の可能性）"),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is NovelDetailUiState.Error -> {
                    DiscoveryStatusBox(
                        DiscoveryStatus.Error(state.message, onRetry = onRetry),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is NovelDetailUiState.Content -> {
                    val novel = state.novel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // ヒーロー
                        BookCover(
                            // 境界: BookCover.bookId は String（書影キャッシュキー）。ncode を id として使う既存挙動を .value で維持。
                            bookId = ncode.value,
                            title = novel.summary.title,
                            showTitle = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DetailHeroHeight)
                        )

                        // 作者・ジャンル行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.S16)
                                .padding(horizontal = Spacing.S24),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = novel.summary.author,
                                fontSize = FontButtonLabel,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            NarouGenres.genreLabel(novel.summary.genreCode)?.let { label ->
                                Text(
                                    text = label,
                                    fontSize = FontChipLarge,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // ステータス表（2列グリッド）
                        val statusText = novelStatusLabel(novel.summary)
                        val length = novel.summary.lengthChars
                        val lengthText = if (length != null) {
                            if (length >= 10000) {
                                String.format(Locale.JAPAN, "（%.1f万字）", length / 10000.0)
                            } else {
                                String.format(Locale.JAPAN, "（%,d字）", length)
                            }
                        } else {
                            ""
                        }
                        val readTime = readTimeLabel(novel.summary) ?: "—"
                        val readTimeVal = if (length != null) "$readTime$lengthText" else readTime

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.S16)
                                .padding(horizontal = Spacing.S24)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "状態",
                                        fontSize = FontMicroLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = statusText,
                                        fontSize = FontSubTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = Spacing.S4)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "読了目安",
                                        fontSize = FontMicroLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = readTimeVal,
                                        fontSize = FontSubTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = Spacing.S4)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = Spacing.S12)
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "会話率",
                                        fontSize = FontMicroLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.kaiwaritu?.let { "$it%" } ?: "—",
                                        fontSize = FontSubTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = Spacing.S4)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "挿絵",
                                        fontSize = FontMicroLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.sasieCnt?.let { "${it}枚" } ?: "—",
                                        fontSize = FontSubTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = Spacing.S4)
                                    )
                                }
                            }
                        }

                        // あらすじセクション
                        if (!novel.story.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "あらすじ",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S8)
                                )
                                Text(
                                    text = novel.story,
                                    fontFamily = MinchoFamily,
                                    fontSize = FontBody,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // キーワードセクション
                        val keywords = remember(novel.keyword) {
                            novel.keyword?.split(Regex("[\\s　]+"))?.filter { it.isNotEmpty() } ?: emptyList()
                        }
                        if (keywords.isNotEmpty()) {
                            // 複数選択（フィードバック2）: チップをトグル選択式にする。選択状態は画面ローカルで足り、
                            // 画面離脱でのリセットは自然挙動として許容する。構成変更（回転・ダーク切替）では
                            // 維持したいので rememberSaveable。SnapshotStateList に既製 saver が無いため listSaver で
                            // トークン一覧を保存/復元する（Set 意味論は contains 判定で担保）。
                            val selectedKeywords = rememberSaveable(
                                saver = listSaver(
                                    save = { it.toList() },
                                    restore = { it.toMutableStateList() }
                                )
                            ) { emptyList<String>().toMutableStateList() }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "キーワード",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S8)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.S8),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    keywords.forEach { keyword ->
                                        val selected = keyword in selectedKeywords
                                        // A11y（F-P/Android §C）: 枠線チップの見た目は現寸のまま、
                                        // タップ判定だけ最小48dpへ拡げる。外側の透明Boxを clickable+sizeIn にし、
                                        // 内側の枠線チップは元の寸法で中央寄せする（外側Box分離＝NcodeLinkSheet と同型）。
                                        Box(
                                            modifier = Modifier
                                                .clickable {
                                                    // トグル。List を Set 意味論で扱うため contains で分岐し重複追加を防ぐ。
                                                    if (selected) selectedKeywords.remove(keyword)
                                                    else selectedKeywords.add(keyword)
                                                }
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    // 選択中は primary 反転（塗り）で示す。未選択は従来の secondary 枠線のまま。
                                                    .then(
                                                        if (selected) {
                                                            Modifier.background(
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(2.dp)
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                                        },
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                                    .padding(horizontal = Spacing.S12, vertical = Spacing.S4)
                                            ) {
                                                Text(
                                                    text = keyword,
                                                    fontSize = FontLabel,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.secondary
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                // 1件以上選択されたら、まとめて検索するアクションを出す（primary）。
                                if (selectedKeywords.isNotEmpty()) {
                                    // A11y（F-P/Android §C）: 上のキーワードチップと同様、見た目は文字行のまま
                                    // タップ判定を最小48dpへ確保する（外側Box分離＝同セクションのチップと同型）。
                                    Box(
                                        modifier = Modifier
                                            .padding(top = Spacing.S4)
                                            .fillMaxWidth()
                                            .clickable { onSearchKeywords(selectedKeywords.toList()) }
                                            .sizeIn(minHeight = 48.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "選択した ${selectedKeywords.size} 件のキーワードで検索",
                                            fontSize = FontButtonLabel,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }

                        // 評価セクション
                        val evalItems = remember(novel) {
                            listOf(
                                "総合評価" to novel.summary.points?.global?.let { String.format(Locale.JAPAN, "%,d pt", it) },
                                "ブックマーク" to novel.favNovelCnt?.let { String.format(Locale.JAPAN, "%,d 件", it) },
                                "評価者数" to novel.allHyokaCnt?.let { String.format(Locale.JAPAN, "%,d 人", it) },
                                "週間ポイント" to novel.summary.points?.weekly?.let { String.format(Locale.JAPAN, "%,d pt", it) }
                            ).filter { it.second != null }
                        }
                        if (evalItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.S24)
                            ) {
                                Text(
                                    text = "評価",
                                    fontSize = FontMicroLabel,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = Spacing.S24, bottom = Spacing.S8)
                                )
                                evalItems.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Spacing.S12),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pair.first,
                                            fontSize = FontChipLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pair.second!!,
                                            fontSize = FontSubTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (index < evalItems.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        // 最終更新表示
                        val lastupText = remember(novel.generalLastup) {
                            formatLastupLabel(novel.generalLastup)
                        }
                        // 取得時刻の表示（M6/公理5 SSOT）。
                        // なぜ出すか: この画面は一覧値の写しではなく詳細APIで取り直した最新値を単一の真実として表示している。
                        // 「いつ時点の情報か」を明示することで、別取得の一覧値と食い違って見えても出所を判別できるようにする。
                        val fetchedAtText = remember(state.fetchedAtMillis) {
                            val time = java.text.SimpleDateFormat("HH:mm", Locale.JAPAN)
                                .format(java.util.Date(state.fetchedAtMillis))
                            "$time 時点の情報"
                        }
                        val metaText = if (lastupText != null) {
                            "$lastupText ・ $fetchedAtText"
                        } else {
                            fetchedAtText
                        }
                        Text(
                            text = metaText,
                            fontSize = FontMicroLabel,
                            // 最終更新+取得時刻は情報を運ぶ文字＝infoText（AA 4.5:1・ADR 0014-D 裁定で装飾用と分離）。
                            color = LocalShelfColors.current.infoText,
                            modifier = Modifier
                                .padding(horizontal = Spacing.S24)
                                .padding(top = Spacing.S24, bottom = Spacing.S24)
                        )
                    }
                }
            }
        }
    }
}
