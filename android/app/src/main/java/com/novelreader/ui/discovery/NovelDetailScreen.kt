package com.novelreader.ui.discovery

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.narouWorkUrl
import com.novelreader.ui.components.BookCover
import com.novelreader.ui.openInAppBrowser
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.NovelDetailUiState
import com.novelreader.viewmodel.NovelDetailViewModel
import java.util.Locale

/**
 * 作品詳細画面（モック discovery-detail-D.html）。
 * 書影ヒーロー表示、作者情報、作品ステータス、あらすじ、キーワード、評価項目などを
 * 和モダンの静謐なレイアウトで構築し、最下部に「なろうで読む」外部連携導線を常駐させる。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    ncode: String,
    viewModel: NovelDetailViewModel,
    onKeywordTap: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(ncode) {
        viewModel.load(ncode)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // スクロール状態を最上位で保持する（M10/層②）。書影ヒーローと本文タイトルが画面外へ流れたら
    // App bar に作品名を常駐表示し、今どの作品を見ているかの手掛かりが消えないようにするため。
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // ヒーロー高さ(200dp)の6割ほどスクロールしたら、書影上に載る本文タイトルが上端へ抜ける頃合いと見なす。
    val heroThresholdPx = remember(density) { with(density) { 200.dp.toPx() * 0.6f } }
    val showBarTitle by remember {
        derivedStateOf { scrollState.value > heroThresholdPx }
    }
    // なぜフェードか: 既存の同型演出（BookCard の animateFloatAsState）に倣い、出没を滑らかにして
    // スクロールに追従する題字のちらつきを抑える。
    val barTitleAlpha by animateFloatAsState(
        targetValue = if (showBarTitle) 1f else 0f,
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
                            text = barState.novel.title ?: "",
                            fontFamily = MinchoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(barTitleAlpha)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                val context = LocalContext.current
                // なぜ再入ガードが要るか（M1/公理3）: Custom Tabs は別プロセスのブラウザ起動待ちがあり、
                // 反応が無いと利用者が連打しやすい。その間 launchUrl が複数回走るとなろうページが
                // 2枚重なって開くため、直近起動から一定時間内のタップは無視して二重起動を防ぐ。
                var lastLaunchAt by remember { mutableStateOf(0L) }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Button(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastLaunchAt >= 1000L) {
                                        lastLaunchAt = now
                                        // なろう作品ページを Custom Tabs で表示する。ツールバー色は明示指定せず
                                        // 既定（ブラウザのサイト識別色）に委ねる（M2/M9・公理8）＝外部サイトへ
                                        // 遷移した事実を隠さず、利用者が今どこに居るかを判別できるようにするため。
                                        openInAppBrowser(context, narouWorkUrl(ncode))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                // open-in-new アイコンで「外部（別画面）へ開く」ことを図示する（公理8）。
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "なろうで読む",
                                    fontSize = 15.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            // 遷移先ドメインを明示し、外部サイトへ出ることを正直に示す（M9/公理8）。
                            Text(
                                text = "外部サイト syosetu.com へ移動します",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
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
                        DiscoveryStatus.Error(state.message, onRetry = { viewModel.retry() }),
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
                            bookId = ncode,
                            title = novel.title ?: "",
                            showTitle = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        // 作者・ジャンル行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = novel.writer ?: "",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            NarouGenres.genreLabel(novel.genre)?.let { label ->
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // ステータス表（2列グリッド）
                        val statusText = novelStatusLabel(novel)
                        val length = novel.length
                        val lengthText = if (length != null) {
                            if (length >= 10000) {
                                String.format(Locale.JAPAN, "（%.1f万字）", length / 10000.0)
                            } else {
                                String.format(Locale.JAPAN, "（%,d字）", length)
                            }
                        } else {
                            ""
                        }
                        val readTime = readTimeLabel(novel) ?: "—"
                        val readTimeVal = if (length != null) "$readTime$lengthText" else readTime

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "状態",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = statusText,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "読了目安",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = readTimeVal,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "会話率",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.kaiwaritu?.let { "$it%" } ?: "—",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "挿絵",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = novel.sasieCnt?.let { "${it}枚" } ?: "—",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // あらすじセクション
                        if (!novel.story.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "あらすじ",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                Text(
                                    text = novel.story,
                                    fontFamily = MinchoFamily,
                                    fontSize = 14.sp,
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "キーワード",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    keywords.forEach { keyword ->
                                        Box(
                                            modifier = Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                                .clickable { onKeywordTap(keyword) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = keyword,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 評価セクション
                        val evalItems = remember(novel) {
                            listOf(
                                "総合評価" to novel.globalPoint?.let { String.format(Locale.JAPAN, "%,d pt", it) },
                                "ブックマーク" to novel.favNovelCnt?.let { String.format(Locale.JAPAN, "%,d 件", it) },
                                "評価者数" to novel.allHyokaCnt?.let { String.format(Locale.JAPAN, "%,d 人", it) },
                                "週間ポイント" to novel.weeklyPoint?.let { String.format(Locale.JAPAN, "%,d pt", it) }
                            ).filter { it.second != null }
                        }
                        if (evalItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "評価",
                                    fontSize = 10.5.sp,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                evalItems.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pair.first,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pair.second!!,
                                            fontSize = 13.sp,
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
                            try {
                                val datePart = novel.generalLastup?.split(" ")?.firstOrNull() ?: ""
                                val ymd = datePart.split("-")
                                if (ymd.size >= 3) {
                                    val year = ymd[0].toInt()
                                    val month = ymd[1].toInt()
                                    val day = ymd[2].toInt()
                                    "${year}年${month}月${day}日 更新"
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
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
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 20.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}
