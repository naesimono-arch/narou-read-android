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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.isValidNcode
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import java.util.Locale

private sealed interface SearchState {
    object Loading : SearchState
    data class Success(val result: DiscoveryResult) : SearchState
    data class Error(val message: String) : SearchState
}

/**
 * 手元の書籍（PDF）となろう上の作品を紐付けるためのボトムシート。
 *
 * なぜここで ModalBottomSheet を使うか:
 * 読書の流れを遮らず、現在のコンテキスト（書籍タイトル）を維持したまま、
 * シームレスになろう側の作品候補を検索・選択・入力できるようにするため。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NcodeLinkSheet(
    bookTitle: String,
    repository: NovelApiRepository,
    colors: ReadingColors,
    onConfirm: (ncode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf(bookTitle) }
    var activeQuery by remember { mutableStateOf(bookTitle) }
    var retryKey by remember { mutableIntStateOf(0) }
    var manualNcode by remember { mutableStateOf("") }

    // なぜ produceState を使うか:
    // 検索クエリや再試行キー(retryKey)の変更を自動で検知して非同期のAPIコールを再トリガーし、
    // ローディング/エラー/結果表示のステートマシンを簡潔に表現するため。
    val searchState by produceState<SearchState>(
        initialValue = SearchState.Loading,
        key1 = activeQuery,
        key2 = retryKey
    ) {
        if (activeQuery.isBlank()) {
            value = SearchState.Success(DiscoveryResult(0, emptyList()))
            return@produceState
        }
        value = SearchState.Loading
        // NarouApiException のみ捕捉する（NovelDetailViewModel と同じ方針）。
        // なぜ Exception 全捕捉にしないか: produceState のキー変更・破棄時の
        // CancellationException まで飲み込み、一瞬エラー表示が挟まる原因になるため。
        value = try {
            val res = repository.discover(
                DiscoveryQuery(
                    word = activeQuery,
                    inTitle = true,
                    order = NarouOrder.TOTAL,
                    limit = 20
                )
            )
            SearchState.Success(res)
        } catch (e: NarouApiException) {
            SearchState.Error(e.userMessage)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding() // キーボード立ち上がり時の隠れを防ぐ
                .padding(bottom = 32.dp)
        ) {
            // 見出し: 明朝 16sp
            Text(
                text = "なろう作品と紐付け",
                fontFamily = MinchoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            // 補足: ゴシック 12sp
            Text(
                text = "「${bookTitle}」の続きをなろうで読むための紐付けです。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 検索欄
            var isFocused by remember { mutableStateOf(false) }
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = colors.text
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { activeQuery = inputText }),
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
                                        fontSize = 15.sp,
                                        color = colors.textSecondary.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                            // A11y: 当たり判定を48dpに拡大（アイコン自体は24dpのまま中央描画）
                            IconButton(
                                onClick = { activeQuery = inputText },
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
                    .padding(vertical = 12.dp)
            ) {
                when (val state = searchState) {
                    is SearchState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "検索中...",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    is SearchState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // なぜカスタム再試行ボタンか: ネットワークエラー時などに、
                            // シートを閉じ直すことなく、その場でワンタップで通信を復旧できるようにするため。
                            // A11y: 枠線ピルは現寸のまま、当たり判定だけ最小48dpへ拡大する。
                            // なぜ外側Boxをclickableにするか: ここは固定高120dpの中央寄せ領域内なので、
                            // 外側を48dpにしてもピル外観も周囲レイアウトも一切押し広げずに済むため。
                            Box(
                                modifier = Modifier
                                    .clickable { retryKey++ }
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
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "再試行",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                    is SearchState.Success -> {
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
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(novels) { index, novel ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val ncode = novel.ncode?.trim()
                                                if (ncode != null) {
                                                    // なろう公式の標準Nコード表記に合わせて大文字化して確定させる
                                                    onConfirm(ncode.uppercase(Locale.ROOT))
                                                }
                                            }
                                            .padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = novel.title.orEmpty(),
                                            fontSize = 13.sp,
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // 状態ラベルの作成（短編/連載中/完結済）
                                        val typeLabel = when {
                                            novel.novelType == 2 -> "短編"
                                            novel.end == 1 -> "連載中（全${novel.generalAllNo ?: 0}話）"
                                            else -> "完結済（全${novel.generalAllNo ?: 0}話）"
                                        }
                                        val writer = novel.writer.orEmpty()
                                        val infoText = if (writer.isNotEmpty()) {
                                            "$writer ・ $typeLabel"
                                        } else {
                                            typeLabel
                                        }
                                        Text(
                                            text = infoText,
                                            fontSize = 11.sp,
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
                fontSize = 11.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
            )

            var isManualFocused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = manualNcode,
                        onValueChange = { manualNcode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isManualFocused = it.isFocused }
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = colors.text
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    if (manualNcode.isEmpty()) {
                                        Text(
                                            text = "N1234AB",
                                            fontSize = 14.sp,
                                            color = colors.textSecondary.copy(alpha = 0.6f)
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

                Spacer(modifier = Modifier.width(12.dp))

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
                            onConfirm(manualNcode.trim().uppercase(Locale.ROOT))
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "紐付け",
                        color = if (isValid) colors.background else colors.textSecondary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
