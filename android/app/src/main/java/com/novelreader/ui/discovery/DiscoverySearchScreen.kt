package com.novelreader.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import com.novelreader.narou.model.NarouAttr
import com.novelreader.viewmodel.buildCustomRange
import com.novelreader.viewmodel.parseCustomRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.narou.model.NarouCuratedKeywords
import com.novelreader.viewmodel.containsWordToken
import com.novelreader.viewmodel.toggleWordToken
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.SearchFilters
import com.novelreader.viewmodel.SearchRange
import com.novelreader.viewmodel.withRangeToggled
import com.novelreader.viewmodel.toggleType
import com.novelreader.viewmodel.toggleLastup

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
    val draft by viewModel.searchDraft.collectAsState()
    var isFocused by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

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
                            IconButton(onClick = { executeSearch() }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "検索する",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChipItem(
                        selected = draft.inTitle,
                        label = "タイトル",
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.TITLE)) }
                    )
                    FilterChipItem(
                        selected = draft.inKeyword,
                        label = "キーワード",
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.KEYWORD)) }
                    )
                    FilterChipItem(
                        selected = draft.inWriter,
                        label = "作者名",
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.WRITER)) }
                    )
                    FilterChipItem(
                        selected = draft.inStory,
                        label = "あらすじ",
                        onClick = { viewModel.setSearchDraft(draft.withRangeToggled(SearchRange.STORY)) }
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
                val history by viewModel.searchHistory.collectAsState()

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

                NarouCuratedKeywords.categories.forEach { category ->
                    SectionHeader(text = category.title)
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

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        ) {
            val lengthPresets = setOf("-10000", "10000-100000", "100000-500000", "500000-1000000", "1000000-")
            val isLengthCustom = draft.filters.length != null && draft.filters.length !in lengthPresets
            var lengthCustomActive by remember(isLengthCustom) { mutableStateOf(isLengthCustom) }

            var minLengthText by remember { mutableStateOf("") }
            var maxLengthText by remember { mutableStateOf("") }

            LaunchedEffect(draft.filters.length) {
                val len = draft.filters.length
                if (len != null && len !in lengthPresets) {
                    val (min, max) = parseCustomRange(len, 10000)
                    val currentBuild = buildCustomRange(minLengthText, maxLengthText, 10000)
                    if (currentBuild != len) {
                        minLengthText = min
                        maxLengthText = max
                    }
                } else if (len == null && !lengthCustomActive) {
                    minLengthText = ""
                    maxLengthText = ""
                }
            }

            fun onLengthTextsChanged(min: String, max: String) {
                minLengthText = min
                maxLengthText = max
                val range = buildCustomRange(min, max, 10000)
                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(range)))
            }

            val timePresets = setOf("-30", "30-120", "120-600", "600-")
            val isTimeCustom = draft.filters.time != null && draft.filters.time !in timePresets
            var timeCustomActive by remember(isTimeCustom) { mutableStateOf(isTimeCustom) }

            var minTimeText by remember { mutableStateOf("") }
            var maxTimeText by remember { mutableStateOf("") }

            LaunchedEffect(draft.filters.time) {
                val t = draft.filters.time
                if (t != null && t !in timePresets) {
                    val (min, max) = parseCustomRange(t, 60)
                    val currentBuild = buildCustomRange(minTimeText, maxTimeText, 60)
                    if (currentBuild != t) {
                        minTimeText = min
                        maxTimeText = max
                    }
                } else if (t == null && !timeCustomActive) {
                    minTimeText = ""
                    maxTimeText = ""
                }
            }

            fun onTimeTextsChanged(min: String, max: String) {
                minTimeText = min
                maxTimeText = max
                val range = buildCustomRange(min, max, 60)
                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(range)))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "条件",
                    fontFamily = MinchoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // a. 作品の形
                SectionHeader(text = "作品の形")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.types
                    FilterChipItem(
                        selected = current.isEmpty(),
                        label = "すべて",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(types = emptySet())))
                        }
                    )
                    FilterChipItem(
                        selected = NarouNovelType.SHORT in current,
                        label = "短編",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(types = toggleType(current, NarouNovelType.SHORT))))
                        }
                    )
                    FilterChipItem(
                        selected = NarouNovelType.RENSAI in current,
                        label = "連載中",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(types = toggleType(current, NarouNovelType.RENSAI))))
                        }
                    )
                    FilterChipItem(
                        selected = NarouNovelType.KANKETSU in current,
                        label = "完結済",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(types = toggleType(current, NarouNovelType.KANKETSU))))
                        }
                    )
                }

                // b. 更新された時期
                SectionHeader(text = "更新された時期")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.lastups
                    FilterChipItem(
                        selected = current.isEmpty(),
                        label = "すべて",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(lastups = emptySet())))
                        }
                    )
                    FilterChipItem(
                        selected = NarouLastup.SEVENDAY in current,
                        label = "7日以内",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(lastups = toggleLastup(current, NarouLastup.SEVENDAY))))
                        }
                    )
                    FilterChipItem(
                        selected = NarouLastup.THISMONTH in current,
                        label = "今月",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(lastups = toggleLastup(current, NarouLastup.THISMONTH))))
                        }
                    )
                    FilterChipItem(
                        selected = NarouLastup.LASTMONTH in current,
                        label = "先月",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(lastups = toggleLastup(current, NarouLastup.LASTMONTH))))
                        }
                    )
                }

                // c. 属性
                SectionHeader(text = "テーマ（含める）")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NarouAttr.values().forEach { attr ->
                        val selected = attr in draft.filters.attrsInclude
                        FilterChipItem(
                            selected = selected,
                            label = attr.uiLabel,
                            onClick = {
                                val nextFilters = if (selected) {
                                    draft.filters.copy(attrsInclude = draft.filters.attrsInclude - attr)
                                } else {
                                    draft.filters.copy(
                                        attrsInclude = draft.filters.attrsInclude + attr,
                                        attrsExclude = draft.filters.attrsExclude - attr
                                    )
                                }
                                viewModel.setSearchDraft(draft.copy(filters = nextFilters))
                            }
                        )
                    }
                }

                SectionHeader(text = "除外する")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NarouAttr.values().forEach { attr ->
                        val selected = attr in draft.filters.attrsExclude
                        FilterChipItem(
                            selected = selected,
                            label = attr.uiLabel,
                            onClick = {
                                val nextFilters = if (selected) {
                                    draft.filters.copy(attrsExclude = draft.filters.attrsExclude - attr)
                                } else {
                                    draft.filters.copy(
                                        attrsExclude = draft.filters.attrsExclude + attr,
                                        attrsInclude = draft.filters.attrsInclude - attr
                                    )
                                }
                                viewModel.setSearchDraft(draft.copy(filters = nextFilters))
                            }
                        )
                    }
                }

                // d. 文字数
                // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
                SectionHeader(text = "文字数")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.length
                    FilterChipItem(
                        selected = current == null && !lengthCustomActive,
                        label = "すべて",
                        onClick = {
                            lengthCustomActive = false
                            minLengthText = ""
                            maxLengthText = ""
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(null)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "-10000",
                        label = "〜1万字",
                        onClick = {
                            lengthCustomActive = false
                            val next = if (current == "-10000") null else "-10000"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "10000-100000",
                        label = "1万〜10万字",
                        onClick = {
                            lengthCustomActive = false
                            val next = if (current == "10000-100000") null else "10000-100000"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "100000-500000",
                        label = "10万〜50万字",
                        onClick = {
                            lengthCustomActive = false
                            val next = if (current == "100000-500000") null else "100000-500000"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "500000-1000000",
                        label = "50万〜100万字",
                        onClick = {
                            lengthCustomActive = false
                            val next = if (current == "500000-1000000") null else "500000-1000000"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "1000000-",
                        label = "100万字〜",
                        onClick = {
                            lengthCustomActive = false
                            val next = if (current == "1000000-") null else "1000000-"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(next)))
                        }
                    )
                    FilterChipItem(
                        selected = lengthCustomActive || isLengthCustom,
                        label = "カスタム",
                        onClick = {
                            if (lengthCustomActive) {
                                lengthCustomActive = false
                                minLengthText = ""
                                maxLengthText = ""
                                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(null)))
                            } else {
                                lengthCustomActive = true
                                val range = buildCustomRange(minLengthText, maxLengthText, 10000)
                                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(range)))
                            }
                        }
                    )
                }

                if (lengthCustomActive || isLengthCustom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        var isMinFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = minLengthText,
                            onValueChange = { onLengthTextsChanged(it, maxLengthText) },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isMinFocused = it.isFocused },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (minLengthText.isEmpty()) {
                                            Text(
                                                text = "下限なし",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = if (isMinFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )

                        Text(
                            text = "〜",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        var isMaxFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = maxLengthText,
                            onValueChange = { onLengthTextsChanged(minLengthText, it) },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isMaxFocused = it.isFocused },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (maxLengthText.isEmpty()) {
                                            Text(
                                                text = "上限なし",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = if (isMaxFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )

                        Text(
                            text = "万字",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // e. 読了時間
                // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
                SectionHeader(text = "読了時間")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.time
                    FilterChipItem(
                        selected = current == null && !timeCustomActive,
                        label = "すべて",
                        onClick = {
                            timeCustomActive = false
                            minTimeText = ""
                            maxTimeText = ""
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(null)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "-30",
                        label = "〜30分",
                        onClick = {
                            timeCustomActive = false
                            val next = if (current == "-30") null else "-30"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "30-120",
                        label = "30分〜2時間",
                        onClick = {
                            timeCustomActive = false
                            val next = if (current == "30-120") null else "30-120"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "120-600",
                        label = "2時間〜10時間",
                        onClick = {
                            timeCustomActive = false
                            val next = if (current == "120-600") null else "120-600"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(next)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "600-",
                        label = "10時間〜",
                        onClick = {
                            timeCustomActive = false
                            val next = if (current == "600-") null else "600-"
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(next)))
                        }
                    )
                    FilterChipItem(
                        selected = timeCustomActive || isTimeCustom,
                        label = "カスタム",
                        onClick = {
                            if (timeCustomActive) {
                                timeCustomActive = false
                                minTimeText = ""
                                maxTimeText = ""
                                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(null)))
                            } else {
                                timeCustomActive = true
                                val range = buildCustomRange(minTimeText, maxTimeText, 60)
                                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(range)))
                            }
                        }
                    )
                }

                if (timeCustomActive || isTimeCustom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        var isMinFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = minTimeText,
                            onValueChange = { onTimeTextsChanged(it, maxTimeText) },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isMinFocused = it.isFocused },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (minTimeText.isEmpty()) {
                                            Text(
                                                text = "下限なし",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = if (isMinFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )

                        Text(
                            text = "〜",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        var isMaxFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = maxTimeText,
                            onValueChange = { onTimeTextsChanged(minTimeText, it) },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isMaxFocused = it.isFocused },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (maxTimeText.isEmpty()) {
                                            Text(
                                                text = "上限なし",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = if (isMaxFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )

                        Text(
                            text = "時間",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // f. 会話率
                // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
                SectionHeader(text = "会話率")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.kaiwaritu
                    FilterChipItem(
                        selected = current == null,
                        label = "すべて",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(kaiwaritu = null)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "60-",
                        label = "会話多め・60%〜",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(kaiwaritu = if (current == "60-") null else "60-")))
                        }
                    )
                    FilterChipItem(
                        selected = current == "-40",
                        label = "地の文多め・〜40%",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(kaiwaritu = if (current == "-40") null else "-40")))
                        }
                    )
                }

                // g. 挿絵
                // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
                SectionHeader(text = "挿絵")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = draft.filters.sasie
                    FilterChipItem(
                        selected = current == null,
                        label = "すべて",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(sasie = null)))
                        }
                    )
                    FilterChipItem(
                        selected = current == "1-",
                        label = "あり",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.copy(sasie = if (current == "1-") null else "1-")))
                        }
                    )
                }

                // ボタン群
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Button(
                        onClick = {
                            showSheet = false
                            executeSearch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(2.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "この条件で探す",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = SearchFilters()))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "リセット",
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .height(24.dp)
                            .navigationBarsPadding()
                    )
                }
            }
        }
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
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        letterSpacing = 3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.5.sp) },
        shape = RoundedCornerShape(2.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.background,
            selectedContainerColor = MaterialTheme.colorScheme.background,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        ),
        leadingIcon = null
    )
}
