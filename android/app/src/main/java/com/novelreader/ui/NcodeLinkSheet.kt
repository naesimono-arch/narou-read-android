package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.discovery.model.SerialState
import com.novelreader.narou.isValidNcode
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.theme.FontActionLabel
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.viewmodel.NcodeSearchUiState
import com.novelreader.ui.theme.Spacing

/**
 * 手元の書籍（PDF）となろう上の作品を紐付けるためのボトムシート。
 *
 * なぜここで ModalBottomSheet を使うか:
 * 読書の流れを遮らず、現在のコンテキスト（書籍タイトル）を維持したまま、
 * シームレスになろう側の作品候補を検索・選択・入力できるようにするため。
 *
 * state-holder / UI 分割（依存注入漏れの解消）:
 * 以前はこのシートが NovelApiRepository を直接受け取り produceState で検索まで回していたが、
 * これは Composable への依存注入漏れ（テスト不能・関心の混在）だった。検索実行は VM
 * （BookshelfViewModel）へ吊り上げ、このシートは [searchState] を受け取って描画し、検索・再試行は
 * [onSearch]/[onRetry] コールバックで依頼するだけの葉にした。入力欄の文字・手動 N コード等の
 * 画面ローカルな UI 状態のみ、過剰な hoisting を避けてここに残す。
 *
 * @param searchState 候補検索の状態（VM が保持する単一正本を呼び出し側が collect して渡す）。
 * @param onSearch 検索実行の依頼（検索欄の確定文字列を渡す）。
 * @param onRetry 直近クエリでの再検索依頼（ネットワークエラー時のワンタップ復旧）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NcodeLinkSheet(
    bookTitle: String,
    searchState: NcodeSearchUiState,
    colors: ReadingColors,
    onSearch: (query: String) -> Unit,
    onRetry: () -> Unit,
    onConfirm: (ncode: Ncode) -> Unit,
    onDismiss: () -> Unit,
) {
    // なぜ rememberSaveable か（監査 2026-08-06 C3 の是正）: 開閉フラグ（NativeReadingScreen の
    // showLinkSheet）だけが Saveable 化されているため、素の remember だと構成変更・プロセス再生成で
    // 「シートは開いたまま戻るのに入力欄だけ空」になり、検索語と N コード 8 文字を打ち直させる。
    // WebReaderScreen/PdfImportScreen の custom Saver を移植しないのは、あちらの保存対象が
    // ライブの WebView から状態保存フェーズに吸い出す Bundle で既定 Saver が使えないためで、
    // ここは素の String＝autoSaver がそのまま Bundle に載る（余計な Saver を足さない）。
    // bookTitle をキーに取るのは、同じ呼び出し位置で別の本へ差し替わったときに前の本の検索語を
    // 引きずらせないため（同一本の構成変更ではキー不変＝編集分がそのまま復元される）。
    var inputText by rememberSaveable(bookTitle) { mutableStateOf(bookTitle) }
    var manualNcode by rememberSaveable { mutableStateOf("") }

    // なぜ skipPartiallyExpanded か（2026-08-26 実測・型＝docs/knowledge/
    // sheet-partial-expansion-hides-the-main-control.md「部分展開起動は主役が折り目の下」）:
    // 既定（false）だと中間アンカー＝画面ちょうど 50% で開き、このシートの主役である
    // 〈候補リスト＋「紐付け」〉が折り目の下に落ちる。エミュ実測（1080x2400 / density 420 / fontScale 1.0）＝
    // 起動直後の自動検索（候補1件）でも「紐付け」は画面下端 33px を残すだけで、検索欄を打ち直して
    // 候補が4件になると手動 N コード欄ごと完全に画面外へ出た（画面の上半分は素通しのスクリムのまま）。
    // 中身の verticalScroll があるので指で送れば到達はできるが、それは「到達できる」であって
    // 「開いた瞬間に見える」ではない——両者を混同すると到達性テストが真のまま緑で通り続ける（同知見）。
    // なぜ他2か所に揃えるか: ModalBottomSheet の呼び出しは本アプリに3か所しかなく、
    // ReadingSettingsSheet.kt と discovery/SearchConditionSheet.kt は既に同じ裁定で全高起動＝
    // ここだけ既定のままなのは「意匠が中間を選んだ」のではなく「誰も決めていない」状態だった。
    // このシートを縛る正本モックは docs/design-candidates/ に無く、拠り所は上記2先例と上の実測。
    // ⚠️ 全高が過大に思えても modifier に高さ上限を掛けないこと（draggableAnchors が読む constraints ごと
    // 縮んで上端に張り付く）。掛けるなら中身側の Column へ（機序＝SearchConditionSheet.kt の申し送り）。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.text,
    ) {
        NcodeLinkSheetContent(
            bookTitle = bookTitle,
            searchState = searchState,
            colors = colors,
            inputText = inputText,
            onInputTextChange = { inputText = it },
            manualNcode = manualNcode,
            onManualNcodeChange = { manualNcode = it },
            onSearch = onSearch,
            onRetry = onRetry,
            onConfirm = onConfirm,
        )
    }
}

/**
 * [NcodeLinkSheet] の中身（`ModalBottomSheet` の枠を剥がした版面）。
 *
 * なぜ枠と中身を分けるか: 枠（ModalBottomSheet）は Robolectric では別ウィンドウ＋部分展開で描かれ、
 * 下端（手動 N コード欄・紐付けボタン）が画面外に落ちて絵にも可視判定にも乗らない
 * （[com.novelreader.ui.NcodeLinkSheetStateRestorationTest] が assertExists へ落とした理由と同じ実測）。
 * 中身だけを stateless な合成子に切り出すと、既存の
 * [com.novelreader.ui.screenshot.ReadingSettingsSheetScreenshotTest] と同じ流儀で golden を撮れる。
 *
 * 入力状態（検索語・手動 N コード）を持たないのは、保存/復元の責務を呼び出し元
 * （[NcodeLinkSheet] の `rememberSaveable`）に残したままにするため——ここへ下ろすと構成変更の
 * 復元経路が絵のためだけに動き、監査 C3 で塞いだ穴の再発を招く。
 */
@Composable
internal fun NcodeLinkSheetContent(
    bookTitle: String,
    searchState: NcodeSearchUiState,
    colors: ReadingColors,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    manualNcode: String,
    onManualNcodeChange: (String) -> Unit,
    onSearch: (query: String) -> Unit,
    onRetry: () -> Unit,
    onConfirm: (ncode: Ncode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S24)
            .navigationBarsPadding()
            .imePadding() // キーボード立ち上がり時の隠れを防ぐ
            // なぜ verticalScroll か（2026-08-17）: 中身の総高が可視域を超えると Column は超過分を
            // 器の外へ置くだけで、手動 N コード欄・「紐付け」・「再試行」が画面外へ落ちて**送る手段が消える**
            //（候補リストだけは内側 LazyColumn で送れる）。fontScale 2.0＋なろう系の長書名で補足文が
            // 数行に膨らむと実際に踏む。監査 2026-08-06 根因④の heightIn(min=120.dp) は「領域が縮んで
            // 欠ける」側だけを直しており、押し出された先を受ける器が無いままだった＝真因は器の欠落。
            // 是正の型は既存2例と同一（ReadingSettingsSheetContent の監査 G-2・NovelReaderAlertDialog の
            // 2026-08-07 増補＝いずれも中身を verticalScroll で包んだ）。
            // なぜこの位置か: navigationBars/ime のインセットより後段＝可視域はインセットぶん縮み、
            // 内容だけが流れる。下端の bottom padding は後段に置いて「最後の要素の下の余白」として一緒に流す。
            // 入れ子スクロールは作らない: 唯一の縦スクロール子である候補 LazyColumn は外側 Box の
            // heightIn(max = 360.dp) で高さが有界のまま測られる（無限高で測られて落ちる組合せにならない）。
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.S32)
    ) {
        // 見出し: 明朝 16sp
        Text(
            text = "なろう作品と紐付け",
            fontFamily = MinchoFamily,
            fontSize = FontSectionTitle,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier.padding(bottom = Spacing.S8)
        )
        // 補足: ゴシック 12sp
        Text(
            text = "「${bookTitle}」の続きをなろうで読むための紐付けです。",
            fontSize = FontCaption,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.S16)
        )

        // 恒常ラベル: 入力後も残り「何の欄か」を示す（placeholder は例示専用に降格）。
        // なぜ手掛かりを常設するか: placeholder は入力すると消え TalkBack でも欄名が読まれないため
        // （UX/06㉑ 入力欄はラベルを持つ）。下の手動 Nコード欄「Nコードを直接入力」ラベルと対を成す。
        Text(
            text = "作品名で検索",
            fontSize = FontLabel,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.S8)
        )

        // 検索欄
        // 下線の色分けに使うフォーカス印は remember のまま: 復元後にフォーカスが戻るかは
        // フレームワークが決めるため、保存すると「フォーカスが無いのに下線だけ強調」の嘘が残る。
        // 実際のフォーカスが戻れば onFocusChanged が再発火して正しい値が入る。
        var isFocused by remember { mutableStateOf(false) }
        BasicTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = FontActionLabel,
                color = colors.text
            ),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(inputText) }),
            decorationBox = { innerTextField ->
                Column {
                    // なぜ Row の bottom padding を外したか:
                    // 検索アイコンの当たり判定を 48dp 化すると Row 高が 24→48dp に増える。
                    // 検索欄全体の見た目の高さを保つため、行の下 padding とフィールド縦 padding を相殺で削っている。
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "作品名を入力",
                                    fontSize = FontActionLabel,
                                    // 例示プレースホルダ＝専用シェード（alpha 二重帳簿を撤去・Design/10§9）
                                    color = colors.placeholder
                                )
                            }
                            innerTextField()
                        }
                        // A11y: 当たり判定を48dpに拡大（アイコン自体は24dpのまま中央描画）
                        IconButton(
                            onClick = { onSearch(inputText) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "検索",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = if (isFocused) colors.accent else colors.blockBorder
                    )
                }
            }
        )

        // 検索結果エリア
        // なぜ heightIn で上限を設定するか: 検索候補が多数見つかった場合でも、
        // リストが画面全体を占有してしまい、手動入力セクションなどが画面外に押し出されるのを防ぐため。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .padding(vertical = Spacing.S12)
        ) {
            when (val state = searchState) {
                is NcodeSearchUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "検索中...",
                            fontSize = FontCaption,
                            color = colors.textSecondary
                        )
                    }
                }
                is NcodeSearchUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // なぜ固定 height でなく min か（監査 2026-08-06 根因④）: fontScale 2.0 では
                            // エラー文の折返し＋間隔12dp＋再試行の当たり判定48dpが120dpを超え、固定高だと
                            // 中央寄せの上下均等クリップで再試行ボタンのタップ標的ごと画面から欠ける。
                            // min 指定なら 1.0 は従来の120dp帯のまま、拡大時だけ内容高へ伸びる
                            //（上限は外側 Box の heightIn(max=360dp) が既に持つ）。
                            .heightIn(min = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            fontSize = FontCaption,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.S12))
                        // なぜカスタム再試行ボタンか: ネットワークエラー時などに、
                        // シートを閉じ直すことなく、その場でワンタップで通信を復旧できるようにするため。
                        // A11y: 枠線ピルは現寸のまま、当たり判定だけ最小48dpへ拡大する。
                        // なぜ外側Boxをclickableにするか: ここは最小高120dpの中央寄せ領域内なので、
                        // 外側を48dpにしてもピル外観は変わらず、1.0 では周囲レイアウトも押し広げずに済むため
                        //（2.0 で内容が120dpを超えた分は上の heightIn(min) が領域ごと伸ばして受ける）。
                        Box(
                            modifier = Modifier
                                .clickable { onRetry() }
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = colors.blockBorder,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "再試行",
                                    fontSize = FontCaption,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
                is NcodeSearchUiState.Success -> {
                    val novels = state.result.novels
                    if (novels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "該当する作品が見つかりません",
                                fontSize = FontCaption,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            // key に ncode を使う: ncode はなろうの作品一意ID（検索結果に同一作品は重複しない）。
                            // 欠損(null)のみ常に一意な index にフォールバックする（Int と String は等価にならず
                            // 衝突しない）。contentType は付けない: 全行が同一構造（タイトル＋情報行）の1種類のため。
                            itemsIndexed(
                                novels,
                                key = { index, novel -> novel.ncode ?: index },
                            ) { index, novel ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val ncode = novel.ncode
                                            if (ncode != null) {
                                                // なろう公式の標準Nコード表記＝保存キー形（trim+大文字）へ正規化して確定させる。
                                                // 下流（linkNcode→Room）は値を素通しで永続化するため、包む時点で
                                                // Ncode.normalizedForStorage に正規化を集約する（Ncode の KDoc 参照）。
                                                onConfirm(Ncode.normalizedForStorage(ncode))
                                            }
                                        }
                                        .padding(vertical = Spacing.S12)
                                ) {
                                    Text(
                                        text = novel.title,
                                        fontSize = FontSubTitle,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.S4))

                                    // 状態ラベルの作成（短編/連載中/完結済）。serialState はマッパが novelType/end を吸収済み。
                                    // なぜ話数を条件付きにするか: chapterCount が欠損（null）のとき 0 で埋めると
                                    // 「全0話」という実在しない話数を捏造表示してしまうため、欠損時は話数を伏せて状態のみ出す。
                                    val episodeSuffix = novel.chapterCount?.let { "（全${it}話）" } ?: ""
                                    val typeLabel = when (novel.serialState) {
                                        SerialState.SHORT -> "短編"
                                        SerialState.ONGOING -> "連載中$episodeSuffix"
                                        else -> "完結済$episodeSuffix"
                                    }
                                    val writer = novel.author
                                    val infoText = if (writer.isNotEmpty()) {
                                        "$writer ・ $typeLabel"
                                    } else {
                                        typeLabel
                                    }
                                    Text(
                                        text = infoText,
                                        fontSize = FontLabel,
                                        color = colors.textSecondary
                                    )
                                }
                                if (index < novels.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = colors.divider
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 手動入力節（リスト下部）
        Text(
            text = "Nコードを直接入力",
            fontSize = FontLabel,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.S16, bottom = Spacing.S8)
        )

        // 検索欄と同じ理由でフォーカス印は remember のまま（保存すると復元後に嘘の強調が残る）。
        var isManualFocused by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = manualNcode,
                    onValueChange = onManualNcodeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isManualFocused = it.isFocused }
                        .padding(vertical = Spacing.S8),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = FontBody,
                        color = colors.text
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { innerTextField ->
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S8)) {
                                if (manualNcode.isEmpty()) {
                                    Text(
                                        text = "N1234AB",
                                        fontSize = FontBody,
                                        // 例示プレースホルダ＝専用シェード（alpha 二重帳簿を撤去・Design/10§9）
                                        color = colors.placeholder
                                    )
                                }
                                innerTextField()
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = if (isManualFocused) colors.accent else colors.blockBorder
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(Spacing.S12))

            // 紐付けボタン（isValidNcodeのときのみ有効）
            val isValid = isValidNcode(manualNcode)
            Box(
                // A11y: タップ高さを最小48dpに（背景・文字・余白は現状維持、文言は中央のまま）
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .background(
                        color = if (isValid) colors.accent else colors.blockBorder,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .clickable(enabled = isValid) {
                        // 手動入力も保存キー形（trim+大文字）へ正規化して確定（Ncode.normalizedForStorage に集約）。
                        onConfirm(Ncode.normalizedForStorage(manualNcode))
                    }
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "紐付け",
                    // 無効時の不活性文字＝専用シェード（alpha 二重帳簿を撤去・Design/10§9）
                    color = if (isValid) colors.background else colors.placeholder,
                    fontSize = FontCaption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
