package com.novelreader.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.narou.SearchHistory
import com.novelreader.narou.model.NarouCuratedKeywords
import com.novelreader.viewmodel.toggleWordToken
import com.novelreader.viewmodel.wordTokens
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.SearchDraft
import com.novelreader.viewmodel.SearchRange
import com.novelreader.viewmodel.withRangeToggled


/**
 * 検索ホーム画面（モック discovery-search-D.html のフレーム1）。
 * 静かな入力欄と検索範囲の複数選択チップを提供し、決定時に親へ検索条件を通知する。
 */
/**
 * 検索ホームのルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・ドラフト/履歴の collect・検索実行の判定と、「条件を調整」シートの表示を担い、
 * 純粋な描画は [DiscoverySearchContent] に委ねる（BookshelfScreen と同じ分割方針）。
 * なぜシート呼び出しだけ route に残すか: SearchConditionSheet は viewModel を直接受け取る確定物のため、
 * これを Content に置くと Content から VM 依存を排除しきれない。シート表示フラグ showSheet を route が所有し、
 * Content からは onOpenConditionSheet イベントだけ受けることで、描画層を VM 非依存の葉に保つ（テスト可能化）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverySearchScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit,
    onSearchExecuted: () -> Unit,
) {
    // なぜ VM 巻き上げか: 条件シートを閉じても・結果一覧から戻っても状態を残すため
    // （SearchDraft.kt の doc コメント参照）。
    val draft by viewModel.searchDraft.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    // F-F: 条件シートの開閉は構成変更（回転・ダーク切替）でも維持する。縦スクロール位置は残るのに
    // シートだけ閉じるのは不整合なため rememberSaveable 化する。シート呼び出しは route の責務。
    var showSheet by rememberSaveable { mutableStateOf(false) }

    val executeSearch = {
        if (viewModel.executeSearch()) {
            onSearchExecuted()
        }
    }

    DiscoverySearchContent(
        draft = draft,
        history = history,
        onBack = onBack,
        onSetDraft = { viewModel.setSearchDraft(it) },
        onExecuteSearch = executeSearch,
        // 履歴語タップは searchFromHistory が成功（＝送信可能）を返したときだけ結果一覧へ進む。
        onSearchHistoryWord = { word -> if (viewModel.searchFromHistory(word)) onSearchExecuted() },
        onPinWord = { viewModel.pinWord(it) },
        onUnpinWord = { viewModel.unpinWord(it) },
        onRemoveRecentWord = { viewModel.removeRecentWord(it) },
        onOpenConditionSheet = { showSheet = true },
    )

    if (showSheet) {
        // 「条件を調整」シートは SearchConditionSheet.kt へ純移動済み（god file 分割）。閉じる・確定は親が持つ
        // showSheet / executeSearch へ委譲する（sheetState 等シート内部状態は移動先が自前で保持）。
        // 分割方針上ここに残す＝Content から viewModel を排除するためのトレードオフ（doc 参照）。
        SearchConditionSheet(
            draft = draft,
            viewModel = viewModel,
            onDismiss = { showSheet = false },
            onSearch = executeSearch,
        )
    }
}

/**
 * 検索ホームの描画層（stateless / UI 分割の content）。DiscoverySearchScreen からの純移動。
 * VM を持たず [draft]＋[history]＋コールバックだけで入力欄・検索範囲チップ・条件調整導線・検索履歴・
 * キュレーションキーワードを描画する葉。フォーカス・カテゴリ展開・「ジャンル別を見る」開閉といった
 * 画面ローカル UI 状態は内部に残す（過剰 hoisting しない）。「条件を調整」は [onOpenConditionSheet] で
 * ルート層へ委譲し、シート自体（VM 依存）は route が描く。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverySearchContent(
    draft: SearchDraft,
    history: SearchHistory,
    onBack: () -> Unit,
    onSetDraft: (SearchDraft) -> Unit,
    onExecuteSearch: () -> Unit,
    onSearchHistoryWord: (String) -> Unit,
    onPinWord: (String) -> Unit,
    onUnpinWord: (String) -> Unit,
    onRemoveRecentWord: (String) -> Unit,
    onOpenConditionSheet: () -> Unit,
) {
    // isFocused は一過性（構成変更で入力欄が再フォーカスされ得る）ため素の remember のまま。
    var isFocused by remember { mutableStateOf(false) }

    // 選択中キーワードは独立 Set ではなく draft.word へ畳み込む方式のため、バー表示のたびに word を
    // トークン列挙する（単一真実源 draft.word から導く派生値＝別 state を持たない）。
    val selectedTokens = wordTokens(draft.word)
    // なぜ Set をメモ化するか: キーワードチップの選択判定は展開カテゴリで最大115チップぶん行われ、従来は
    // チップ毎に containsWordToken（word を毎回 split→線形探索）を呼んでいた。draft.word 変化時のみ Set を
    // 作り直し、判定を Set の O(1) メンバシップにする。判定結果は containsWordToken（＝wordTokens(word).contains）
    // と同一の分割規則・完全一致メンバシップのため同値。
    val selectedTokenSet = remember(draft.word) { wordTokens(draft.word).toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "探す",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                    )
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
            // 選択中キーワード追従バー: チップを下へスワイプしても「今何を選んだか」が画面下部に常駐する
            // （フィードバック1）。トークンが1つ以上あるときだけ出す。
            if (selectedTokens.isNotEmpty()) {
                SelectedKeywordsBar(
                    tokens = selectedTokens,
                    // 個別解除は toggleWordToken（選択済みトークンを渡す＝除去側に倒れる）。範囲・条件は維持。
                    onRemoveToken = { token ->
                        onSetDraft(draft.copy(word = toggleWordToken(draft.word, token)))
                    },
                    // すべて解除は word のみ空へ。検索範囲・その他フィルタは維持する（フィードバック3）。
                    onClearAll = { onSetDraft(draft.copy(word = "")) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 検索フィールド
            // なぜ BasicTextField を使うか: マテリアルデザイン標準の TextField では、
            // 背景や枠線の主張が強く、モックの「ヘアライン下線のみの静かな入力欄」を表現しづらいため。
            BasicTextField(
                value = draft.word,
                onValueChange = { onSetDraft(draft.copy(word = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                decorationBox = { innerTextField ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (draft.word.isEmpty()) {
                                    Text(
                                        text = "作品名・作者・キーワード",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                            // M2: 検索語も条件も空だと executeSearch は false を返し無反応になる（死んだ押下）。
                            // 送信不能を「押せなさ」で予告するため、canSearch が false のときはボタンを disabled 見た目にする。
                            IconButton(onClick = { onExecuteSearch() }, enabled = draft.canSearch) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "検索する",
                                    tint = if (draft.canSearch) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            )

            // 検索範囲セクション
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "検索範囲",
                    fontSize = 10.5.sp,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                )

                // 範囲チップ4つ
                // なぜ FilterChip の leadingIcon を使わないか: チェックマークなどの余計な装飾を省き、
                // モックの「枠と背景の反転のみで状態を示す静かなチップ」の意匠に合わせるため。
                // F-H: 検索範囲は最低1つ必要（全解除＝なろうAPI仕様で全項目対象となり不透明化する。SearchDraft
                // 側の withRangeToggled が最後の1つを保護する）。残り1つになったチップは onClick が無反応になり
                // 「死んだアフォーダンス」になるため、その最後の1つは selected+disabled にして制約を「押せなさ」で示す。
                val selectedRangeCount = listOf(draft.inTitle, draft.inKeyword, draft.inWriter, draft.inStory).count { it }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChipItem(
                        selected = draft.inTitle,
                        label = "タイトル",
                        enabled = !(draft.inTitle && selectedRangeCount == 1),
                        onClick = { onSetDraft(draft.withRangeToggled(SearchRange.TITLE)) }
                    )
                    FilterChipItem(
                        selected = draft.inKeyword,
                        label = "キーワード",
                        enabled = !(draft.inKeyword && selectedRangeCount == 1),
                        onClick = { onSetDraft(draft.withRangeToggled(SearchRange.KEYWORD)) }
                    )
                    FilterChipItem(
                        selected = draft.inWriter,
                        label = "作者名",
                        enabled = !(draft.inWriter && selectedRangeCount == 1),
                        onClick = { onSetDraft(draft.withRangeToggled(SearchRange.WRITER)) }
                    )
                    FilterChipItem(
                        selected = draft.inStory,
                        label = "あらすじ",
                        enabled = !(draft.inStory && selectedRangeCount == 1),
                        onClick = { onSetDraft(draft.withRangeToggled(SearchRange.STORY)) }
                    )
                }
                // なぜチップを押せないのかを明示する注記（disabled の理由提示）。
                if (selectedRangeCount == 1) {
                    Text(
                        text = "検索範囲は1つ以上必要です",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 「条件を調整」ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .border(
                            width = 1.dp,
                            color = if (draft.filters.activeCount() > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .clickable { onOpenConditionSheet() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val tint = if (draft.filters.activeCount() > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "条件調整",
                        tint = tint,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (draft.filters.activeCount() > 0) "条件を調整（${draft.filters.activeCount()}）" else "条件を調整",
                        fontSize = 12.5.sp,
                        color = tint
                    )
                }

                // ── 検索履歴（D1・モック「ピン留め」「最近の検索」節） ──
                // history はルート層が collect して渡す（Content は VM 非依存）。
                if (history.pinned.isNotEmpty()) {
                    Text(
                        text = "ピン留め",
                        fontSize = 10.5.sp,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        history.pinned.forEach { word ->
                            HistoryChip(
                                word = word,
                                pinned = true,
                                onWordClick = { onSearchHistoryWord(word) },
                                onPinClick = { onUnpinWord(word) },
                            )
                        }
                    }
                }

                if (history.recent.isNotEmpty()) {
                    Text(
                        text = "最近の検索",
                        fontSize = 10.5.sp,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        history.recent.forEach { word ->
                            HistoryChip(
                                word = word,
                                pinned = false,
                                onWordClick = { onSearchHistoryWord(word) },
                                onPinClick = { onPinWord(word) },
                                onDelete = { onRemoveRecentWord(word) },
                            )
                        }
                    }
                }

                Text(
                    text = "キーワードから選ぶ",
                    fontSize = 10.5.sp,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
                )

                // why: カテゴリ単位の展開状態を category.title をキーに保持する（basic/genre で
                // title は一意なので両ループで1つの map を共有できる）。未登録キーは false 扱い＝
                // 既定は全カテゴリ畳み。狙いは「要素」22語・「リプレイ（TRPG）」26語のような長大な
                // カテゴリで検索画面が縦に伸びるのを抑え、見出しだけの一覧まで圧縮すること。
                // F-F: カテゴリ展開状態も構成変更で維持する。SnapshotStateMap には既製 saver が無いため、
                // 展開中（value==true）のキー一覧だけを保存し復元する listSaver を付ける（false は既定なので保存不要）。
                val expandedCategories = rememberSaveable(
                    saver = listSaver(
                        save = { map -> map.filterValues { it }.keys.toList() },
                        restore = { keys ->
                            mutableStateMapOf<String, Boolean>().apply {
                                keys.forEach { put(it, true) }
                            }
                        }
                    )
                ) { mutableStateMapOf<String, Boolean>() }

                NarouCuratedKeywords.basicCategories.forEach { category ->
                    val expanded = expandedCategories[category.title] == true
                    CollapsibleCategoryHeader(
                        title = category.title,
                        expanded = expanded,
                        onToggle = { expandedCategories[category.title] = !expanded },
                        previewWords = category.words,
                    )
                    if (expanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            category.words.forEach { word ->
                                val selected = word in selectedTokenSet
                                FilterChipItem(
                                    selected = selected,
                                    label = word,
                                    onClick = {
                                        val nextWord = toggleWordToken(draft.word, word)
                                        val isAdding = !selected
                                        val nextInKeyword = if (isAdding) {
                                            // why: キュレーション語は作者タグの語彙のため、範囲に keyword を含めないとタイトル一致のみとなり大半を取りこぼす。範囲チップの状態変化として見える形で広げる（ADR 0007 原則2と両立）
                                            true
                                        } else {
                                            draft.inKeyword
                                        }
                                        onSetDraft(
                                            draft.copy(
                                                word = nextWord,
                                                inKeyword = nextInKeyword
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    // 案B: カテゴリ行はヘアラインで区切る「開ける行」（モック .kw-cat の border-bottom）
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // F-F: 「ジャンル別を見る」の展開も構成変更で維持する。
                var showGenreKeywords by rememberSaveable { mutableStateOf(false) }

                // why: 公式パネルは①作品内容と②ジャンル別の2段構成。②＋TRPG系は約80語あり常時表示すると検索画面が長大化するため、公式と同じ段構成のまま既定は畳む（全数収載と画面の静けさの両立）
                Text(
                    text = if (showGenreKeywords) "たたむ ⌃" else "ジャンル別のキーワードを見る ⌄",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showGenreKeywords = !showGenreKeywords }
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                if (showGenreKeywords) {
                    NarouCuratedKeywords.genreCategories.forEach { category ->
                        val expanded = expandedCategories[category.title] == true
                        CollapsibleCategoryHeader(
                            title = category.title,
                            expanded = expanded,
                            onToggle = { expandedCategories[category.title] = !expanded },
                            previewWords = category.words,
                        )
                        if (expanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                category.words.forEach { word ->
                                    val selected = word in selectedTokenSet
                                    FilterChipItem(
                                        selected = selected,
                                        label = word,
                                        onClick = {
                                            val nextWord = toggleWordToken(draft.word, word)
                                            val isAdding = !selected
                                            val nextInKeyword = if (isAdding) {
                                                // why: キュレーション語は作者タグの語彙のため、範囲に keyword を含めないとタイトル一致のみとなり大半を取りこぼす。範囲チップの状態変化として見える形で広げる（ADR 0007 原則2と両立）
                                                true
                                            } else {
                                                draft.inKeyword
                                            }
                                            onSetDraft(
                                                draft.copy(
                                                    word = nextWord,
                                                    inKeyword = nextInKeyword
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        // 案B: ジャンル別も同じ「開ける行」の区切り（モック .kw-cat の border-bottom）
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/**
 * 選択中キーワード追従バー（フィードバック1・3）。Scaffold の bottomBar に据え、選択トークンが
 * 1つ以上あるときだけ表示する。上辺ヘアライン＋背景 background で本文と地続きに見せ、ヘッダ行に件数と
 * 「すべて解除」、その下に丸ピルの解除チップを FlowRow で並べる。
 * なぜ navigationBarsPadding＋imePadding か: 本アプリは edge-to-edge（setDecorFitsSystemWindows=false）で
 * インセットが自動適用されないため、素のままだとバーがナビバー／キーボードに隠れる。二重持ち上げになら
 * ないのは自動リフトが無いためで、単一の imePadding でキーボード直上へ正しく1回だけ持ち上がる
 * （NcodeLinkSheet と同型の対処）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedKeywordsBar(
    tokens: List<String>,
    onRemoveToken: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 上辺 1dp ヘアライン（本文とバーの境目）。
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "選択中のキーワード ${tokens.size}件",
                    fontSize = 10.5.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // すべて解除（一括リセット）。テキストボタンで primary。
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "すべて解除",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // チップが多くてもバーが画面を覆わないよう、最大高さ96dpで内部スクロールに閉じ込める。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 96.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                tokens.forEach { token ->
                    SelectedKeywordChip(label = token, onRemove = { onRemoveToken(token) })
                }
            }
        }
    }
}

/**
 * 選択中キーワードの解除チップ（丸ピル）。primary の細枠＋primary 文字で「選択中」を示し、
 * 末尾の × と全体タップで個別解除する（× アイコンで「これは解除操作」を明示＝HistoryChip と同じ流儀）。
 */
@Composable
private fun SelectedKeywordChip(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onRemove)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50)
            )
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "「$label」を解除",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(13.dp)
        )
    }
}

/**
 * 検索履歴チップ（モック .pchip / .rchip-hist）。
 * ピンアイコン: ピン留め済み=藍（タップで解除）／未ピン=薄い補助色（タップでピン留め）。
 * 語タップ=その語で即検索。onDelete があれば右端に薄い×（履歴から削除）。
 */
@Composable
private fun HistoryChip(
    word: String,
    pinned: Boolean,
    onWordClick: () -> Unit,
    onPinClick: () -> Unit,
    // modifier は最初の任意引数に置く（ModifierParameter 規約）。onDelete は任意の後続スロット。
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(2.dp),
            )
            .padding(start = 8.dp, end = if (onDelete != null) 6.dp else 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = if (pinned) "ピン留めを解除" else "ピン留めする",
            tint = if (pinned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .clickable(onClick = onPinClick)
                .padding(vertical = 7.dp)
                .size(13.dp),
        )
        Text(
            text = word,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clickable(onClick = onWordClick)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        )
        if (onDelete != null) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "履歴から削除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(vertical = 7.dp)
                    .size(13.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        letterSpacing = 3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

// why: 「キーワードから選ぶ」の各カテゴリ見出しを開閉トグル化するための専用ヘッダ。
// 静的な SectionHeader はフィルターシート側の見出し（作品の形/文字数 等）でも使い回すため、
// そちらまで折りたたみ化しないよう別 composable に分ける。
// 意匠は案B（モック正本 docs/design-candidates/discovery/discovery-search-D.html .kw-cat）＝
// ヘアラインで区切る「開ける行」・濃色見出し・畳み時は代表語を淡色で1行プレビュー。
// なぜプレビューを出すか: 見出し語だけでは中身の語彙が想像できず「開けるだけの行」が並ぶため、
// 畳んだままでもカテゴリの中身を予告する（展開すればチップ群に置き換わるので二重表示にならない）。
@Composable
fun CollapsibleCategoryHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    previewWords: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "⌃" else "⌄",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!expanded && previewWords.isNotEmpty()) {
            // 代表語プレビュー＝先頭3語を「・」区切り・4語以上は「…」（モック .kw-cat-preview）
            Text(
                text = previewWords.take(3).joinToString("・") + if (previewWords.size > 3) "…" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // enabled=false: 制約（最後の1つ・排他）を「押せなさ」で示す。選択済みなら「選択のまま淡く」
    // 見せて「これが選ばれているが今は動かせない」を伝える（disabled 見た目でも選択の履歴を残す）。
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 11.5.sp) },
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.background,
            selectedContainerColor = MaterialTheme.colorScheme.background,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            // disabled は背景を保ちラベルだけ淡く（トークン由来色の透過で意匠発明を避ける）。
            disabledContainerColor = MaterialTheme.colorScheme.background,
            disabledSelectedContainerColor = MaterialTheme.colorScheme.background,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            // enabled を渡し、disabled 時は枠色も淡くする（選択中は淡い藍＝選択の履歴を保つ）。
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            disabledSelectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        ),
        leadingIcon = null
    )
}
