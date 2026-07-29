package com.novelreader.ui.skins.k

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.discovery.NovelListRow
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontListItemTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import java.time.LocalDate

// ============================================================
// 明快K: さがす（発見ホーム）＝正本モック discovery-K.html の忠実翻訳（ADR 0022 §1 の構造分岐先）。
//
// 核（plan default-ui-clarity-K 確定事項5）: ①画面タイトル「さがす」を明示（タブと同語彙）
//   ②最上部に実検索フィールドを第一強調 ③きょうの気分→ジャンル→ランキング ④末尾に公式サイトへの逃げ道。
// D の淡色字間見出し（.28em）を廃し、セクション見出しは gothic bold ink で自己説明させる（モック .sec）。
//
// 色は D トークン土台（SkinK は SkinD へ委譲）: base→background・ink→onSurface・藍→primary・
//   line→outlineVariant・検索フィールド地→surfaceVariant（薄地）。メタ文字は AA の infoText（LocalShelfColors）。
//   モック --ink-soft #6A6E78（AA 引き上げ値）は Compose 側の正規 AA メタトークン infoText(#5C606D・より高
//   コントラスト)で受ける（K=D 字面/色の共有・ADR 0014-D）。字面はゴシック（既定）・気分見出し/順位数字/作品名=明朝。
//
// ボトムナビ（KBottomNav）はこの画面には含めない＝K 最上位3画面を束ねる上位シェルが搭載する（没入層と分離。
//   本画面は M/P/J 発見と同型の「発見コンテンツ全画面」を描く）。onBack は K では未使用（タブ画面＝戻る無し）。
// モーション: モックに keyframes/JS 無し＝静止で実装（M/P 発見と同じ扱い）。
// ============================================================

@Composable
internal fun DiscoveryHomeK(
    order: NarouOrder,
    state: DiscoveryUiState,
    onBack: () -> Unit, // K はタブ画面ゆえ戻るを持たない＝受けるが未使用（when 分岐を M/P/J と同型に保つため署名は共有）
    onOpenDetail: (ncode: Ncode) -> Unit,
    onOpenGenre: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    onOpenSearch: () -> Unit,
    onPickMood: (MoodPreset) -> Unit,
    onSelectOrder: (NarouOrder) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 固定トップ（モック .top）: 画面タイトル＋実検索フィールド（常時可視・第一強調）。
        SearchHeaderK(onOpenSearch)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            // .scroll padding:6px 20px 20px → 横 S24（D 発見の横マージンと同じ）・下 S24。
            contentPadding = PaddingValues(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S24),
        ) {
            item { MoodSectionK(onPickMood) }
            item { GenreSectionK(onOpenGenre, onPickBiggenre) }
            item { SectionHeadingK("ランキング") }
            item { OrderTabsK(order, onSelectOrder) }
            item { RankingStaleRows(state, order, onOpenDetail, onRefresh) }
            item { OfficialLinkK() }
        }
    }
}

/** 固定トップ: h1「さがす」＋実検索フィールド（タップで検索画面へ）。モック .top / .search。 */
@Composable
private fun SearchHeaderK(onOpenSearch: () -> Unit) {
    Column(
        // .top padding:2px 20px 14px → 上 S4 / 横 S24 / 下 S16。
        modifier = Modifier.padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4, bottom = Spacing.S16),
    ) {
        Text(
            "さがす",
            // タブと同語彙の画面タイトル（モック h1 22px ゴシック bold）＝22sp の M3 titleLarge を bold 化。
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .padding(top = Spacing.S16) // .search margin-top 14px → S16
                .fillMaxWidth()
                .height(52.dp)             // .search 52px 固定（高さ＝構造値・スケール外）
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // 薄地の沈めた面（--field）
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = Spacing.S16), // .search padding 0 16px → 横 S16
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null, // 隣接プレースホルダ文が読み上げを担う
                tint = LocalShelfColors.current.infoText,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "作品名・作者名・キーワードで探す",
                fontSize = FontBody, // .search span 14px（＝検索入力欄の字面トークン）
                color = LocalShelfColors.current.infoText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.S12), // .search gap 10px → S12
            )
        }
    }
}

/** セクション見出し（モック .sec）: 淡色字間装飾でなく gothic bold ink＝自己説明性優先。 */
@Composable
private fun SectionHeadingK(text: String, topSpace: androidx.compose.ui.unit.Dp = Spacing.S24) {
    Text(
        text,
        fontSize = FontSubTitle, // .sec 13px
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        // .sec margin 20px 0 12px（先頭の きょうの気分 のみ上 8px＝呼び出し側で topSpace を S8 に）。
        modifier = Modifier.padding(top = topSpace, bottom = Spacing.S12),
    )
}

/**
 * きょうの気分（モック .mood → 2026-07-24 ページャ化・正本 discovery-K.html）: 4件1組×[MoodPattern] 3組を
 * 横スワイプで行き来し、初期表示の組だけが日替わり（決定的＝MoodPattern.forEpochDay）。
 * 可視代替の義務（隠しスワイプ禁止）＝下のドットインジケータと日替わり注記が「他の組がある」ことを常時可視化する。
 * 2026-07-26 循環化: 端で止まらず右端→先頭・左端→末尾へ続く（仮想大カウント＋剰余写像。意匠・寸法は不変）。
 */
@Composable
private fun MoodSectionK(onPickMood: (MoodPreset) -> Unit) {
    // remember＝セッション中は固定（日付を跨いでも表示中の画面を勝手に差し替えない。次回の composition から新しい日の組）。
    val todayPattern = remember { MoodPattern.forEpochDay(LocalDate.now().toEpochDay()) }
    val moodPagerState = rememberPagerState(
        // 循環スワイプ: Pager にネイティブ循環が無いため仮想大カウント＋剰余写像で実現。
        // 中央帯から開始（loopInitialPage）＝初日組を保ったまま左右どちらへも実用上無限にスワイプできる。
        initialPage = MoodPattern.loopInitialPage(todayPattern),
        pageCount = { MoodPattern.LOOP_PAGE_COUNT },
    )
    Column {
        SectionHeadingK("きょうの気分", topSpace = Spacing.S8) // .sec:first-child margin-top 8px
        // 高さの安定枠（2026-07-29 実機報告「表示が2行を超えると下の描画ががくんと動く」の真因対処）:
        // Pager は wrap-content＝表示中ページの高さへ都度スナップする一方、ページ高は文言の折返し行数
        //（端末幅・フォントスケール依存）で組ごとに違う→組の切替や日替わり初期組のたび下部が段差で動く。
        // 正本モック discovery-K.html は .mp-track（flex・align-items 既定 stretch）で「全ページ＝最高
        // ページと同高」を構造で規定している。その翻訳として全3組の格子を不可視・操作不可で重ね、
        // 枠高＝最大組高をその場の実測で予約する（幅・フォントスケールに追従＝固定 dp の発明をしない）。
        Box {
            MoodPattern.entries.forEach { pattern ->
                MoodPageGridK(
                    pattern = pattern,
                    onPickMood = null, // 計測専用ゴースト＝タップ配線なし（短いページの下で誤タップさせない）
                    modifier = Modifier
                        // Pager の contentPadding(end=S24) と同幅に合わせ、折返し行数の計算を実ページと一致させる。
                        .padding(end = Spacing.S24)
                        .alpha(0f)
                        // 不可視の計測専用ゆえ TalkBack へ幻のカード群を読ませない。
                        .clearAndSetSemantics {},
                )
            }
            HorizontalPager(
                state = moodPagerState,
                // 右端に次ページの頭を覗かせる＝「まだ横にある」のシグニファイア（モックの左右覗きの Compose 翻訳）。
                contentPadding = PaddingValues(end = Spacing.S24),
                pageSpacing = Spacing.S12,
            ) { page ->
                // 仮想ページ→実3組の剰余写像（循環）。
                MoodPageGridK(pattern = MoodPattern.forPage(page), onPickMood = onPickMood)
            }
        }
        // ドットインジケータ（モック .dots）＝現在組を可視化。寸法は構造値ゆえスケール外の raw dp。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.S4, bottom = Spacing.S4),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 仮想ページを論理組へ戻してから照合＝ドットは従来どおり実3組を指す（循環化でも見た目不変）。
            val logicalPage = MoodPattern.forPage(moodPagerState.currentPage).ordinal
            MoodPattern.entries.forEachIndexed { i, _ ->
                val active = i == logicalPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = Spacing.S4)
                        // 現在組は横長ピル＝方向のあるインジケータ（Material の pager 慣習）。
                        .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        // 日替わり注記（モックの1行）: 初期組が日で変わることの自己説明。
        Text(
            "日替わり・きょうは「${todayPattern.displayName}」から",
            fontSize = FontLabel,
            color = LocalShelfColors.current.infoText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 1組4プリセットの2列格子（親が LazyColumn ゆえ LazyGrid をネストせず chunked(2) の Row で組む）。
 * [onPickMood] null＝高さ計測専用ゴースト（MoodSectionK の安定枠）としてタップを配線しない。
 */
@Composable
private fun MoodPageGridK(
    pattern: MoodPattern,
    onPickMood: ((MoodPreset) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        pattern.presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S12),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // .mood gap 12px
            ) {
                rowPresets.forEach { preset ->
                    MoodCardK(
                        preset,
                        onClick = onPickMood?.let { pick -> { pick(preset) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodCardK(preset: MoodPreset, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            // onClick null＝計測専用ゴースト。clickable を積まない＝クリック・フォーカスの標的にしない。
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.S12), // .md padding 14px → 縦 S12
    ) {
        // 左の藍ルール（モック .md::before＝3px 藍の縦帯）。高さ・幅は構造値ゆえスケール外の raw dp。
        Box(
            modifier = Modifier
                .padding(top = Spacing.S4)
                .width(3.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(start = Spacing.S12, end = Spacing.S8)) {
            Text(
                preset.title,
                fontFamily = MinchoFamily,
                fontSize = FontListItemTitle, // .md b 14.5px（明朝）
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                preset.cardLabel,
                fontSize = FontLabel, // .md span 11px
                color = LocalShelfColors.current.infoText,
                modifier = Modifier.padding(top = Spacing.S4),
            )
        }
    }
}

/** ジャンルから（モック .chips）: 大ジャンルの横スクロールチップ＋末尾「すべて→」（ジャンル一覧入口）。 */
@Composable
private fun GenreSectionK(onOpenGenre: () -> Unit, onPickBiggenre: (code: Int, label: String) -> Unit) {
    Column {
        SectionHeadingK("ジャンルから")
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // .chips gap 8px
        ) {
            // key＝大ジャンルコード（安定・全件で一意）。key 無しだと Lazy の既定＝位置キーになり、
            // 将来この列の並びが変わったとき再利用が位置に貼り付いて別ジャンルへ状態が付いて回る。
            items(NarouGenres.BIGGENRES, key = { it.first }) { (code, label) ->
                GenreChipK(label, accent = false, onClick = { onPickBiggenre(code, label) })
            }
            // 「すべて→」＝ジャンル一覧入口（D の「すべて →」に相当・藍枠藍字）。
            item { GenreChipK("すべて→", accent = true, onClick = onOpenGenre) }
        }
    }
}

@Composable
private fun GenreChipK(label: String, accent: Boolean, onClick: () -> Unit) {
    // accent=true（すべて→）は藍枠・藍字、通常チップは line 枠・ink 字（モック .chip / .chip.all）。
    val borderColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        fontSize = FontButtonLabel, // .chip 12.5px
        color = textColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .chip padding 8px 16px
    )
}

/** ランキングの期間タブ（モック .rtabs）: 選択タブは藍 bold＋2dp 下線、列全体の下端にヘアライン。 */
@Composable
private fun OrderTabsK(order: NarouOrder, onSelectOrder: (NarouOrder) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S16), // .rtabs gap 18px → S16
        ) {
            NarouOrder.entries.forEach { o ->
                val selected = o == order
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        o.uiLabel,
                        fontSize = FontSubTitle, // .rtab 13px
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        // 未選択タブも意味を運ぶ文字＝infoText（AA・ADR 0014-D）。選択は藍（primary）据え置き。
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else LocalShelfColors.current.infoText,
                        modifier = Modifier
                            .clickable { onSelectOrder(o) }
                            .padding(vertical = Spacing.S8), // .rtab padding 10px 0 → 縦 S8
                    )
                    // 選択下線（モック .rtab.on::after＝藍 2px）。未選択は透明で高さを揃える。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) // .rtabs border-bottom 1px
    }
}

/**
 * ランキング一覧（モック .rk）。行は D 共通の [NovelListRow] を再利用する＝順位数字は明朝・上位3位のみ藍・
 * 以降 infoText・行タップで詳細（K の意匠要件と D 実装が一致するデータ駆動行のため意匠を再発明しない・ADR 0014-D）。
 *
 * 期間タブ切替時のスクロール位置リセット対策（D/P と同じ stale-while-revalidate）: 再取得は一旦
 * Loading を挟み一覧が status 1件へ全置換されて総高が崩れると LazyListState が先頭へクランプされる。
 * 直近 Content を控え、再取得中はその行群（同 key=ncode）を出し続けてスクロールアンカーを保つ。
 * Empty/Error は真に0件・失敗ゆえ status を出して良い。VM は非改変。
 */
@Composable
private fun RankingStaleRows(
    state: DiscoveryUiState,
    order: NarouOrder,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    var lastContent by remember { mutableStateOf<DiscoveryUiState.Content?>(null) }
    // 合成中の書き戻しを避け Content を側効果で控える（Content 分岐は state を直接描くため表示遅延なし）。
    LaunchedEffect(state) { (state as? DiscoveryUiState.Content)?.let { lastContent = it } }
    val rowsContent = when (state) {
        is DiscoveryUiState.Content -> state
        is DiscoveryUiState.Loading -> lastContent
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            rowsContent != null -> rowsContent.novels.forEachIndexed { index, novel ->
                NovelListRow(
                    rank = index + 1,
                    novel = novel,
                    order = order,
                    // 境界: novel.ncode は Moshi 由来の String。詳細遷移の引数は型付き Ncode へ包む。
                    onClick = { novel.ncode?.let { onOpenDetail(Ncode(it)) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            state is DiscoveryUiState.Loading -> RankingStatus("読み込んでいます")
            state is DiscoveryUiState.Empty -> RankingStatus("作品が見つかりませんでした")
            state is DiscoveryUiState.Error -> {
                RankingStatus(state.message)
                Text(
                    "再試行",
                    fontSize = FontCaption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onRefresh)
                        .padding(vertical = Spacing.S8),
                )
            }
        }
    }
}

/** ランキング領域の状態一文（読込中／空／失敗理由＝意味テキストゆえ infoText）。 */
@Composable
private fun RankingStatus(text: String) {
    Text(
        text,
        fontSize = FontCaption,
        color = LocalShelfColors.current.infoText,
        modifier = Modifier.padding(vertical = Spacing.S24),
    )
}

/**
 * 公式サイトへの逃げ道（handover ★A 要件・モック .official）: ヘアラインで区切った外部リンク行。
 * なろう公式（yomou.syosetu.com）を外部ブラウザで開く＝Blocked 送客と同じ素の ACTION_VIEW 流儀
 *（BookshelfScreen 参照）。ブラウザ不在の稀ケースは ActivityNotFoundException を握って無害化する
 *（案内リンクゆえ症状隠しではない＝逃げ道が塞がるより無反応の方が害が小さい）。
 */
@Composable
private fun OfficialLinkK() {
    val context = LocalContext.current
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = Spacing.S8), // .official margin-top 8px
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yomou.syosetu.com/")))
                    }
                }
                .padding(top = Spacing.S16, bottom = Spacing.S4), // .official padding 16px 2px 4px（横は .scroll マージン）
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "小説家になろう公式サイトで探す",
                fontSize = FontSubTitle, // .official 13px
                color = LocalShelfColors.current.infoText,
            )
            Icon(
                Icons.Filled.NorthEast, // .official ↗（外部リンク＝右上矢印）
                contentDescription = null,
                tint = LocalShelfColors.current.infoText,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
