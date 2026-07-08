package com.novelreader.ui.discovery

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.narou.model.NarouCuratedKeywords
import com.novelreader.viewmodel.containsWordToken
import com.novelreader.viewmodel.toggleWordToken
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.SearchRange
import com.novelreader.viewmodel.withRangeToggled


/**
 * 検索ホーム画面（モック discovery-search-D.html のフレーム1）。
 * 静かな入力欄と検索範囲の複数選択チップを提供し、決定時に親へ検索条件を通知する。
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
    // isFocused は一過性（構成変更で入力欄が再フォーカスされ得る）ため素の remember のまま。
    var isFocused by remember { mutableStateOf(false) }
    // F-F: 条件シートの開閉は構成変更（回転・ダーク切替）でも維持する。縦スクロール位置は残るのに
    // シートだけ閉じるのは不整合なため rememberSaveable 化する。
    var showSheet by rememberSaveable { mutableStateOf(false) }

    val executeSearch = {
        if (viewModel.executeSearch()) {
            onSearchExecuted()
        }
    }

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
                onValueChange = { viewModel.setSearchDraft(draft.copy(word = it)) },
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
                keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
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
                            IconButton(onClick = { executeSearch() }, enabled = draft.canSearch) {
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
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.TITLE)) }
                    )
                    FilterChipItem(
                        selected = draft.inKeyword,
                        label = "キーワード",
                        enabled = !(draft.inKeyword && selectedRangeCount == 1),
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.KEYWORD)) }
                    )
                    FilterChipItem(
                        selected = draft.inWriter,
                        label = "作者名",
                        enabled = !(draft.inWriter && selectedRangeCount == 1),
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.WRITER)) }
                    )
                    FilterChipItem(
                        selected = draft.inStory,
                        label = "あらすじ",
                        enabled = !(draft.inStory && selectedRangeCount == 1),
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.STORY)) }
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
                        .clickable { showSheet = true }
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
                val history by viewModel.searchHistory.collectAsStateWithLifecycle()

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
                                onWordClick = { if (viewModel.searchFromHistory(word)) onSearchExecuted() },
                                onPinClick = { viewModel.unpinWord(word) },
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
                                onWordClick = { if (viewModel.searchFromHistory(word)) onSearchExecuted() },
                                onPinClick = { viewModel.pinWord(word) },
                                onDelete = { viewModel.removeRecentWord(word) },
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
                    )
                    if (expanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            category.words.forEach { word ->
                                val selected = containsWordToken(draft.word, word)
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
                                        viewModel.setSearchDraft(
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
                        )
                        if (expanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                category.words.forEach { word ->
                                    val selected = containsWordToken(draft.word, word)
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
                                            viewModel.setSearchDraft(
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
                    }
                }
            }
        }
    }

    if (showSheet) {
        // 「条件を調整」シートは SearchConditionSheet.kt へ純移動（god file 分割）。閉じる・確定は親が持つ
        // showSheet / executeSearch へ委譲する（sheetState 等シート内部状態は移動先が自前で保持）。
        SearchConditionSheet(
            draft = draft,
            viewModel = viewModel,
            onDismiss = { showSheet = false },
            onSearch = executeSearch,
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
// そちらまで折りたたみ化しないよう別 composable に分ける。見た目トークン（型・字間・色）は
// SectionHeader に揃え、開閉記号は同画面の「ジャンル別を見る」トグルと同じ ⌄/⌃ で統一する。
@Composable
fun CollapsibleCategoryHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 22.dp, bottom = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.5.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "⌃" else "⌄",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
