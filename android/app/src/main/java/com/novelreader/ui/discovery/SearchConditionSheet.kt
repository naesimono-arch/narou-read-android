package com.novelreader.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.LENGTH_STEPS
import com.novelreader.viewmodel.SearchDraft
import com.novelreader.viewmodel.SearchFilters
import com.novelreader.viewmodel.TIME_STEPS
import com.novelreader.viewmodel.buildCustomRange
import com.novelreader.viewmodel.selectedStepIndices
import com.novelreader.viewmodel.toggleLastup
import com.novelreader.viewmodel.toggleType

/**
 * 「条件を調整」シート（モック discovery-search-D.html の条件パネル）。
 * DiscoverySearchScreen（画面最大の god file）から純移動で切り出した部分＝ロジック不変。
 * 作品の形／更新時期／テーマ含む・除外／文字数／読了時間／会話率／挿絵の絞り込みを提供する。
 * 状態は VM の SearchDraft を単一真実源とし、確定は onSearch・閉じるは onDismiss で親へ委ねる。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchConditionSheet(
    draft: SearchDraft,
    viewModel: DiscoveryViewModel,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
) {
    // why: 中間アンカー(PartiallyExpanded)を消す。残すと ①高速フリックで中間を飛び越え/行き過ぎる
    // ②カスタム入力でIMEが出て adjustResize によりウィンドウが縮むと、アンカー再計算で全開から
    // 中間へ勝手に settle して「最上位まで開いたシートが真ん中まで落ちる」不具合になるため。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        // カスタム判定は「ステップ列に分解できない値」で行う。単段プリセット集合との不一致で判定すると、
        // 複数選択の合成レンジ（例 "10000-500000"）までカスタム入力扱いになりチップが点灯しなくなるため。
        val isLengthCustom = draft.filters.length != null && selectedStepIndices(draft.filters.length, LENGTH_STEPS).isEmpty()
        // F-F: カスタム入力欄の開閉も構成変更で維持する（isLengthCustom をキーに保持＝draft 側の変化では再初期化）。
        var lengthCustomActive by rememberSaveable(isLengthCustom) { mutableStateOf(isLengthCustom) }

        // 同上: 合成レンジをカスタム扱いにしないため、分解可能性で判定する。
        val isTimeCustom = draft.filters.time != null && selectedStepIndices(draft.filters.time, TIME_STEPS).isEmpty()
        // F-F: カスタム入力欄の開閉も構成変更で維持する。
        var timeCustomActive by rememberSaveable(isTimeCustom) { mutableStateOf(isTimeCustom) }

        // なぜ生入力テキストの remember＋LaunchedEffect 双方向同期を廃したか: カスタム文字数/読了時間の
        // 生テキストは SSOT として draft.lengthCustom*/timeCustom* が唯一の保持先になった（SearchDraft
        // のフィールド説明参照）。入力・段階トグル・すべて/カスタム切替は VM 更新関数と draft.copy 経由で
        // draft を直接更新するため、length/time→生テキストを追随させる LaunchedEffect は不要になった。

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // why: 文字数/読了時間のカスタム入力でIMEが出た際、IME inset分をシート内容側で吸収し
                // 入力欄がキーボードに隠れないようにする（NcodeLinkSheet と同じ対処。この画面だけ抜けていた）。
                .imePadding()
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

            // c-2. 除外語（なろうAPI notword）。テーマ属性の「除外する」と意味的に同じ「除外」文脈のため直後に置く。
            // 入力欄の意匠は本シートのカスタム文字数/読了時間欄（ヘアライン下線のみの静かな入力欄）に完全に合わせる。
            SectionHeader(text = "除外語")
            var isNotWordFocused by remember { mutableStateOf(false) }
            BasicTextField(
                value = draft.notWord,
                // 生入力をそのまま draft へ。trim→空→null 化は送出時（toQuery）に一括で行い、word と経路をそろえる。
                onValueChange = { viewModel.setSearchDraft(draft.copy(notWord = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .onFocusChanged { isNotWordFocused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { innerTextField ->
                    Column {
                        Box(
                            modifier = Modifier.padding(bottom = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.notWord.isEmpty()) {
                                Text(
                                    text = "含めたくない語（スペース区切り）",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = if (isNotWordFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            )

            // d. 文字数
            // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
            // F-I: 文字数と読了時間は併用不可（なろうAPIの制約。SearchFilters.withLength/withTime が
            // 一方を選ぶと他方を null にして排他を保証する）。だが排他が UI 上未提示だと「一方を選ぶと他方が
            // 黙って消える」不透明になるため、committed 値で「今どちらが有効か」を判定し、無効側の節をグレーアウト
            // ＋注記で事前提示する。判定を committed 値（time!=null / length!=null）に限るのは、両者が同時に
            // 非 null になり得ない不変条件があり、*CustomActive の残留 true では両節同時 disabled の詰みを招くため。
            val timeEngaged = draft.filters.time != null
            SectionHeader(text = "文字数")
            if (timeEngaged) {
                Text(
                    text = "読了時間と併用できません（なろうAPIの制約）",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val current = draft.filters.length
                val lengthStepIndices = selectedStepIndices(current, LENGTH_STEPS)
                FilterChipItem(
                    selected = current == null && !lengthCustomActive,
                    label = "すべて",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        // 生入力テキストも掃く（length を null に戻す経路の責務）。
                        viewModel.setSearchDraft(draft.copy(lengthCustomMin = "", lengthCustomMax = "", filters = draft.filters.withLength(null)))
                    }
                )
                FilterChipItem(
                    selected = !lengthCustomActive && !isLengthCustom && 0 in lengthStepIndices,
                    label = "〜1万字",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        viewModel.toggleLengthStep(0)
                    }
                )
                FilterChipItem(
                    selected = !lengthCustomActive && !isLengthCustom && 1 in lengthStepIndices,
                    label = "1万〜10万字",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        viewModel.toggleLengthStep(1)
                    }
                )
                FilterChipItem(
                    selected = !lengthCustomActive && !isLengthCustom && 2 in lengthStepIndices,
                    label = "10万〜50万字",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        viewModel.toggleLengthStep(2)
                    }
                )
                FilterChipItem(
                    selected = !lengthCustomActive && !isLengthCustom && 3 in lengthStepIndices,
                    label = "50万〜100万字",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        viewModel.toggleLengthStep(3)
                    }
                )
                FilterChipItem(
                    selected = !lengthCustomActive && !isLengthCustom && 4 in lengthStepIndices,
                    label = "100万字〜",
                    enabled = !timeEngaged,
                    onClick = {
                        lengthCustomActive = false
                        viewModel.toggleLengthStep(4)
                    }
                )
                FilterChipItem(
                    selected = lengthCustomActive || isLengthCustom,
                    label = "カスタム",
                    enabled = !timeEngaged,
                    onClick = {
                        if (lengthCustomActive) {
                            lengthCustomActive = false
                            // 生入力テキストも掃く（length を null に戻す経路の責務）。
                            viewModel.setSearchDraft(draft.copy(lengthCustomMin = "", lengthCustomMax = "", filters = draft.filters.withLength(null)))
                        } else {
                            lengthCustomActive = true
                            // draft に保持した生テキストからレンジを復元する（カスタム再開時の入力を維持）。
                            val range = buildCustomRange(draft.lengthCustomMin, draft.lengthCustomMax, 10000)
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withLength(range)))
                        }
                    }
                )
            }

            // 読了時間が有効なとき文字数節は無効なので、カスタム入力欄も隠す（残留 lengthCustomActive で
            // 入力欄だけ生き残り、グレーアウトを迂回して length を再設定できてしまうのを防ぐ）。
            if ((lengthCustomActive || isLengthCustom) && !timeEngaged) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    var isMinFocused by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = draft.lengthCustomMin,
                        onValueChange = { viewModel.setLengthCustomText(it, draft.lengthCustomMax) },
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
                                    if (draft.lengthCustomMin.isEmpty()) {
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
                        value = draft.lengthCustomMax,
                        onValueChange = { viewModel.setLengthCustomText(draft.lengthCustomMin, it) },
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
                                    if (draft.lengthCustomMax.isEmpty()) {
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
            // F-I: 文字数が有効なら読了時間節を無効化＋注記（上記 timeEngaged と対の排他提示）。
            val lengthEngaged = draft.filters.length != null
            SectionHeader(text = "読了時間")
            if (lengthEngaged) {
                Text(
                    text = "文字数と併用できません（なろうAPIの制約）",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val current = draft.filters.time
                val timeStepIndices = selectedStepIndices(current, TIME_STEPS)
                FilterChipItem(
                    selected = current == null && !timeCustomActive,
                    label = "すべて",
                    enabled = !lengthEngaged,
                    onClick = {
                        timeCustomActive = false
                        // 生入力テキストも掃く（time を null に戻す経路の責務）。
                        viewModel.setSearchDraft(draft.copy(timeCustomMin = "", timeCustomMax = "", filters = draft.filters.withTime(null)))
                    }
                )
                FilterChipItem(
                    selected = !timeCustomActive && !isTimeCustom && 0 in timeStepIndices,
                    label = "〜30分",
                    enabled = !lengthEngaged,
                    onClick = {
                        timeCustomActive = false
                        viewModel.toggleTimeStep(0)
                    }
                )
                FilterChipItem(
                    selected = !timeCustomActive && !isTimeCustom && 1 in timeStepIndices,
                    label = "30分〜2時間",
                    enabled = !lengthEngaged,
                    onClick = {
                        timeCustomActive = false
                        viewModel.toggleTimeStep(1)
                    }
                )
                FilterChipItem(
                    selected = !timeCustomActive && !isTimeCustom && 2 in timeStepIndices,
                    label = "2時間〜10時間",
                    enabled = !lengthEngaged,
                    onClick = {
                        timeCustomActive = false
                        viewModel.toggleTimeStep(2)
                    }
                )
                FilterChipItem(
                    selected = !timeCustomActive && !isTimeCustom && 3 in timeStepIndices,
                    label = "10時間〜",
                    enabled = !lengthEngaged,
                    onClick = {
                        timeCustomActive = false
                        viewModel.toggleTimeStep(3)
                    }
                )
                FilterChipItem(
                    selected = timeCustomActive || isTimeCustom,
                    label = "カスタム",
                    enabled = !lengthEngaged,
                    onClick = {
                        if (timeCustomActive) {
                            timeCustomActive = false
                            // 生入力テキストも掃く（time を null に戻す経路の責務）。
                            viewModel.setSearchDraft(draft.copy(timeCustomMin = "", timeCustomMax = "", filters = draft.filters.withTime(null)))
                        } else {
                            timeCustomActive = true
                            // draft に保持した生テキストからレンジを復元する（カスタム再開時の入力を維持）。
                            val range = buildCustomRange(draft.timeCustomMin, draft.timeCustomMax, 60)
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withTime(range)))
                        }
                    }
                )
            }

            // 文字数が有効なとき読了時間節は無効なので、カスタム入力欄も隠す（残留 timeCustomActive 対策）。
            if ((timeCustomActive || isTimeCustom) && !lengthEngaged) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    var isMinFocused by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = draft.timeCustomMin,
                        onValueChange = { viewModel.setTimeCustomText(it, draft.timeCustomMax) },
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
                                    if (draft.timeCustomMin.isEmpty()) {
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
                        value = draft.timeCustomMax,
                        onValueChange = { viewModel.setTimeCustomText(draft.timeCustomMin, it) },
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
                                    if (draft.timeCustomMax.isEmpty()) {
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
                        onDismiss()
                        onSearch()
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
