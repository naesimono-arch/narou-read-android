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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSheetTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.LENGTH_STEPS
import com.novelreader.viewmodel.LENGTH_STEPS_DEF
import com.novelreader.viewmodel.SearchDraft
import com.novelreader.viewmodel.SearchFilters
import com.novelreader.viewmodel.TIME_STEPS
import com.novelreader.viewmodel.TIME_STEPS_DEF
import com.novelreader.viewmodel.buildCustomRange
import com.novelreader.viewmodel.selectedStepIndices
import com.novelreader.viewmodel.toggleLastup
import com.novelreader.viewmodel.toggleType
import com.novelreader.ui.theme.Spacing

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
    // why: 内容が長くシートが画面いっぱいまで伸びると「全画面ダイアログ」に見え、ボトムシートの
    // 文脈（裏に検索画面が居る）が消える。意匠正本 discovery-search-D.html の .sheet は
    // max-height:85% を明示しており、その追従として上限を85%に制限する（それ未満の内容なら wrap のまま）。
    // screenHeightDp はシステムバー除きの概算だが、モックの%指定に対する翻訳として十分。
    // ⚠️ この上限は ModalBottomSheet の modifier に渡してはならない（内容側 Column に掛けること）:
    // modifier は内部チェーン先頭に合成され、draggableAnchors が読む constraints.maxHeight 自体を
    // 縮めるため fullHeight=85% かつシート実高=85% → Expanded アンカー=0 となり、align(TopCenter)
    // 基準のままシートが画面上端に張り付く（2026-07-12 実機で発現。機序は task_diary #57）。
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = Spacing.S8)
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
                // 85%上限は内容側に掛ける（シート modifier に掛けるとアンカー計算が壊れる＝上の⚠️参照）。
                .heightIn(max = sheetMaxHeight)
                // why: 文字数/読了時間のカスタム入力でIMEが出た際、IME inset分をシート内容側で吸収し
                // 入力欄がキーボードに隠れないようにする（NcodeLinkSheet と同じ対処。この画面だけ抜けていた）。
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.S24, vertical = Spacing.S16)
        ) {
            Text(
                text = "条件",
                fontFamily = MinchoFamily,
                fontSize = FontSheetTitle,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ジャンル（なろうの一次分類のため最上段に置く）。
            // 大ジャンルごとに「すべて」チップ（＝その大ジャンル全体＝biggenre）と詳細ジャンルチップ（＝genre）を並べる。
            // なぜ折りたたまないか: このシートに折りたたみの慣行はなく（キーワード選択画面の CollapsibleCategoryHeader は
            // 別画面の意匠）、過剰実装を避けて素の縦並びにする。長さは verticalScroll で吸収する。
            // 選択セマンティクス（相互排他）は SearchFilters.withBiggenreToggled/withGenreToggled が単一真実源。
            SectionHeader(text = "ジャンル")
            NarouGenres.BIGGENRES.forEach { (bigCode, bigName) ->
                GenreGroupLabel(text = bigName)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 「すべて」＝この大ジャンル全体（biggenre コード）。選択中は biggenres にコードが入っている状態。
                    // 未選択（biggenres/genres とも空）のときは点灯しない＝「ジャンル指定なし＝全ジャンル」を表す
                    // （他節の「すべて＝空で点灯」とは意味が異なる。ここでは大ジャンル単位の選択なので、
                    // 各グループの「すべて」がその大ジャンルの選択有無を表す）。
                    FilterChipItem(
                        selected = bigCode in draft.filters.biggenres,
                        label = "すべて",
                        onClick = {
                            viewModel.setSearchDraft(draft.copy(filters = draft.filters.withBiggenreToggled(bigCode)))
                        }
                    )
                    NarouGenres.GENRES_BY_BIG[bigCode]?.forEach { (genreCode, genreName) ->
                        FilterChipItem(
                            selected = genreCode in draft.filters.genres,
                            label = genreName,
                            onClick = {
                                viewModel.setSearchDraft(draft.copy(filters = draft.filters.withGenreToggled(genreCode)))
                            }
                        )
                    }
                }
            }

            // a. 作品の形
            SectionHeader(text = "作品の形")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                    .padding(top = Spacing.S4)
                    .onFocusChanged { isNotWordFocused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = FontBody,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { innerTextField ->
                    Column {
                        Box(
                            modifier = Modifier.padding(bottom = Spacing.S4),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.notWord.isEmpty()) {
                                Text(
                                    text = "含めたくない語（スペース区切り）",
                                    fontSize = FontBody,
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
                    fontSize = FontMicroLabel,
                    // なぜ InfoText か: 排他制約の理由＝意味を運ぶ文字。onSurfaceVariant の alpha 沈めは
                    // ADR 0014-D の暗化トークン裁定を打ち消す退行（サブAA地の重ね沈め）のため素値で使う。
                    color = LocalShelfColors.current.infoText,
                    modifier = Modifier.padding(bottom = Spacing.S12)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                // 段階チップは値とラベルの対（LENGTH_STEPS_DEF）から生成する。ラベルを直書きせず対定義に一本化し、
                // 段の増減・境界変更でレンジ列とラベルがずれる平行定義事故を防ぐ。
                LENGTH_STEPS_DEF.forEachIndexed { index, step ->
                    FilterChipItem(
                        selected = !lengthCustomActive && !isLengthCustom && index in lengthStepIndices,
                        label = step.label,
                        enabled = !timeEngaged,
                        onClick = {
                            lengthCustomActive = false
                            viewModel.toggleLengthStep(index)
                        }
                    )
                }
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
                CustomRangeInput(
                    minValue = draft.lengthCustomMin,
                    maxValue = draft.lengthCustomMax,
                    onMinChange = { viewModel.setLengthCustomText(it, draft.lengthCustomMax) },
                    onMaxChange = { viewModel.setLengthCustomText(draft.lengthCustomMin, it) },
                    unitLabel = "万字"
                )
            }

            // e. 読了時間
            // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
            // F-I: 文字数が有効なら読了時間節を無効化＋注記（上記 timeEngaged と対の排他提示）。
            val lengthEngaged = draft.filters.length != null
            SectionHeader(text = "読了時間")
            if (lengthEngaged) {
                Text(
                    text = "文字数と併用できません（なろうAPIの制約）",
                    fontSize = FontMicroLabel,
                    // なぜ InfoText か: 上の「読了時間と併用できません」と同じ（ADR 0014-D）。
                    color = LocalShelfColors.current.infoText,
                    modifier = Modifier.padding(bottom = Spacing.S12)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                // 段階チップは値とラベルの対（TIME_STEPS_DEF）から生成する（文字数節と同じく平行定義事故の防止）。
                TIME_STEPS_DEF.forEachIndexed { index, step ->
                    FilterChipItem(
                        selected = !timeCustomActive && !isTimeCustom && index in timeStepIndices,
                        label = step.label,
                        enabled = !lengthEngaged,
                        onClick = {
                            timeCustomActive = false
                            viewModel.toggleTimeStep(index)
                        }
                    )
                }
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
                CustomRangeInput(
                    minValue = draft.timeCustomMin,
                    maxValue = draft.timeCustomMax,
                    onMinChange = { viewModel.setTimeCustomText(it, draft.timeCustomMax) },
                    onMaxChange = { viewModel.setTimeCustomText(draft.timeCustomMin, it) },
                    unitLabel = "時間"
                )
            }

            // f. 会話率
            // なぜモックのレンジスライダーでなく段階チップか: 文字数・読了時間はダイナミックレンジが広く線形スライダーは実用に耐えないため、段階選択に置き換える（見た目の節構成・チップ様式はモック準拠。操作系の差分は ADR 0005 のスコープ外規定＝実機フィードバックで後詰め）。
            SectionHeader(text = "会話率")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
                verticalArrangement = Arrangement.spacedBy(Spacing.S8),
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
                    .padding(top = Spacing.S24)
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
                        fontSize = FontSubTitle,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(vertical = Spacing.S4)
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.setSearchDraft(draft.copy(filters = SearchFilters()))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.S8),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "リセット",
                        fontSize = FontCaption,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(Spacing.S24)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

/**
 * ジャンル節の大ジャンル小見出し（モック .sheet の大ジャンル小見出し＝sheet-sec-ttl より一段濃い行）。
 * SectionHeader（節見出し）より下位の階層を示すため、字間を詰め onSurface 寄りで区別する
 * （色は既存トークンのみ・意匠発明はしない）。
 */
@Composable
private fun GenreGroupLabel(text: String) {
    Text(
        text = text,
        fontSize = FontChipLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.S16, bottom = Spacing.S8)
    )
}

/**
 * カスタム範囲入力欄（下限〜上限＋単位）。文字数節・読了時間節でほぼ同型に2箇所コピペされていた約90行を部品化。
 * なぜ部品化: min/max の値・更新関数・単位ラベルだけが差分で、意匠（ヘアライン下線のみの静かな入力欄・
 * プレースホルダ配色・"〜"区切り・数値キーボード・フォーカス下線色）は完全に同一だったため。
 * 見た目・挙動は元の2ブロックと同一（モック準拠の意匠をそのまま踏襲）。
 */
@Composable
private fun CustomRangeInput(
    minValue: String,
    maxValue: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    unitLabel: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S12)
    ) {
        var isMinFocused by remember { mutableStateOf(false) }
        BasicTextField(
            value = minValue,
            onValueChange = onMinChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isMinFocused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = FontBody,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            decorationBox = { innerTextField ->
                Column {
                    Box(
                        modifier = Modifier.padding(bottom = Spacing.S4),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (minValue.isEmpty()) {
                            Text(
                                text = "下限なし",
                                fontSize = FontBody,
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
            fontSize = FontBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var isMaxFocused by remember { mutableStateOf(false) }
        BasicTextField(
            value = maxValue,
            onValueChange = onMaxChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isMaxFocused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = FontBody,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                Column {
                    Box(
                        modifier = Modifier.padding(bottom = Spacing.S4),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (maxValue.isEmpty()) {
                            Text(
                                text = "上限なし",
                                fontSize = FontBody,
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
            text = unitLabel,
            fontSize = FontBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.S4)
        )
    }
}
