package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.model.TocEntry
import com.novelreader.ui.skins.j.TocPortalJ
import com.novelreader.ui.skins.k.TocK
import com.novelreader.ui.skins.m.TocSkyM
import com.novelreader.ui.skins.p.TocCartridgeP
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSheetTitle
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.Spacing

/**
 * 目次のロード状態。
 * なぜ sealed で4状態に分けるか: 以前は `List<TocEntry>` 1本で表現していたため、
 * 非同期パースが未完了（＝まだ空）の一瞬と「真に章が0件」を区別できず、パース中に
 * 「章が見つかりません」を誤表示していた（公理8＝状態の可視性）。Loading/Empty/Error/Content を
 * 明示的に持つことで、ロード中はスケルトン・例外時は再試行・真の空だけ CTA を出し分ける。
 */
sealed interface TocState {
    /** index.html を非同期パース中（初期状態）。スケルトンを表示する。 */
    data object Loading : TocState

    /** パースは完了したが章リンクが0件（真の空）。CTA を表示する。 */
    data object Empty : TocState

    /** パース中に例外が発生。メッセージと再試行導線を表示する。 */
    data class Error(val message: String) : TocState

    /** 章リンクを取得できた通常状態。 */
    data class Content(val entries: List<TocEntry>) : TocState
}

/**
 * 目次画面。index.html をパースした結果を状態別に表示する。
 *
 * @param tocState 目次のロード状態（Loading/Empty/Error/Content）
 * @param colors 読書テーマの色トークン（直書き色は禁止。正典は Theme.kt）
 * @param workTitle 作品名（明快K の目次ヘッダのサブタイトルに使う。他スキンは未使用＝既定 null で挙動不変）
 * @param currentChapterFile 最後に表示していた章のファイル名（null なら未読。ハイライト＋自動スクロールに使う）
 * @param onSelectChapter 章ファイル名を引数にして章選択時に呼ぶコールバック
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 * @param onRetry 目次パースを再試行するコールバック（Error 状態の再読込に使う）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeTableOfContentsScreen(
    tocState: TocState,
    colors: ReadingColors,
    // 作品名は明快K のヘッダ副題専用。既定 null＝D/C/M/P/J は受け取らず描画も不変（追加は加算のみ・挙動非変更）。
    workTitle: String? = null,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetry: () -> Unit,
) {
    // スキンM/P/J は目次を画面丸ごと各スキン構造へ委譲する薄いルーター（ADR 0022 §1）。
    // スキン追加時の書き忘れを compile error で捕捉するため else を書かない（無音Dフォールバック防止）。
    // D/C（WAMODERN_D/YAKO_C）は共通の default 経路（この下の Scaffold）で描く。
    when (LocalSkin.current) {
        // 星図構造へ委譲。
        Skin.SEIZU_M -> {
            TocSkyM(
                tocState = tocState,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = onRetry,
            )
            return
        }
        // ステージセレクト構造へ委譲。
        Skin.CARTRIDGE_P -> {
            TocCartridgeP(
                tocState = tocState,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = onRetry,
            )
            return
        }
        // 「廊下を進む道程」構造へ委譲。
        Skin.PORTAL_J -> {
            TocPortalJ(
                tocState = tocState,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = onRetry,
            )
            return
        }
        // 明快構造（目次＝ヘッダ作品名＋現在地チップ＋既読/現在/未読の語彙化）へ委譲。
        Skin.MEIKAI_K -> {
            TocK(
                tocState = tocState,
                colors = colors,
                workTitle = workTitle,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = onRetry,
            )
            return
        }
        Skin.WAMODERN_D, Skin.YAKO_C -> Unit // 既定描画へ（この下の共通実装が D/C を描く）
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    // モック toc-D .topbar h1: 明朝・やや大きめ・字間を開けた「和」の題字
                    Text(
                        "目次",
                        fontFamily = MinchoFamily,
                        fontSize = FontSheetTitle,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.12.em,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToBookshelf) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "本棚に戻る",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    // システムテーマ（MaterialTheme）ではなく読書テーマに追従させるため明示指定
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.topBarIcon,
                ),
            )
        },
    ) { innerPadding ->
        when (tocState) {
            // ロード中: 章リストの骨格だけを見せて「読み込んでいる」ことを伝える（白画面や誤空表示を防ぐ）
            is TocState.Loading -> TocSkeleton(
                colors = colors,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            // 例外時: 原因メッセージ＋再試行。本棚退避は上部の戻るボタンで可能なためここでは再試行に絞る
            is TocState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.S32),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "目次の読み込みに失敗しました",
                    color = colors.textSecondary,
                    fontFamily = MinchoFamily,
                    fontSize = FontSectionTitle,
                )
                Text(
                    text = tocState.message,
                    // なぜ infoText か: 失敗理由＝意味を運ぶ文字。textSecondary の alpha 沈めは実効 AA 割れ
                    //（ADR 0014-D・読書エラー画面と同じ裁定＝ReadingColors.infoText の素値を使う）。
                    color = colors.infoText,
                    fontFamily = MinchoFamily,
                    fontSize = FontCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.S4, bottom = Spacing.S16),
                )
                OutlinedButton(onClick = onRetry) {
                    Text("再試行", fontFamily = MinchoFamily)
                }
            }

            // 真に0件のときだけ CTA を出す（Loading と区別できるようになったため誤表示しない）
            is TocState.Empty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "章が見つかりません",
                    color = colors.textSecondary,
                    fontFamily = MinchoFamily,
                )
            }

            is TocState.Content -> TocList(
                entries = tocState.entries,
                colors = colors,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                innerPadding = innerPadding,
            )
        }
    }
}

/**
 * 目次を開いた瞬間に現在章付近を初期表示するための、LazyList の先頭可視 index を導出する純関数。
 * なぜ純関数に切り出すか: rememberLazyListState(initialFirstVisibleItemIndex=…) へ即時反映させたく
 *（LaunchedEffect+scrollToItem だと初回フレームで先頭が一瞬見えてから飛ぶ）、かつ導出規則
 *（現在章の1つ手前・未読は先頭・終端 clamp は LazyList 任せ）を UI から切り離して単体テストで固定するため。
 *
 * @param entries 目次の章リスト
 * @param currentChapterFile 最後に読んでいた章のファイル名（null／リスト未一致＝未読）
 * @return 初期の先頭可視 index。未読なら 0（従来どおり先頭）、既読なら現在章の1つ手前（>=0 に clamp）。
 */
internal fun tocInitialFirstVisibleIndex(
    entries: List<TocEntry>,
    currentChapterFile: String?,
): Int {
    val currentIndex = entries.indexOfFirst { it.fileName == currentChapterFile }
    // 未読（現在章がリストに無い）は先頭から。既読は現在章の1つ手前を見せて前後の文脈を残す。
    return if (currentIndex >= 0) (currentIndex - 1).coerceAtLeast(0) else 0
}

/** 章リスト本体（Content 状態）。旧実装のリスト描画をそのまま切り出したもの。 */
@Composable
private fun TocList(
    entries: List<TocEntry>,
    colors: ReadingColors,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val currentIndex = entries.indexOfFirst { it.fileName == currentChapterFile }

    // 初期表示を現在章付近に「瞬間配置」する（開いた瞬間からその位置に居る）。
    // なぜ LaunchedEffect+scrollToItem をやめ initialFirstVisibleItemIndex にしたか: 前者は初回
    // コンポジション後に走るため 1 フレームだけ先頭が見えてから現在章へ飛ぶ（チラつき）。initial 指定なら
    // 最初のフレームから正位置に置ける。entries と currentChapterFile は Content 到達時点で確定済み
    // （同期パース結果＋rememberSaveable の lastChapterFile）なので初期値だけで導出できる。終端付近の
    // 空白抑制（clamp）は LazyList の標準挙動に委ねる。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = tocInitialFirstVisibleIndex(entries, currentChapterFile),
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // key に fileName を使う: TocEntry.fileName は目次内の各章 href（chap_1.html 等）で章ごとに一意。
        // 一意で安定なキーにより、目次の非同期ロード差し替え・現在章スクロール時もアイテムの同一性が
        // 保たれる。contentType は付けない: 全行が同一構造（左バー＋テキスト行）で1種類のため。
        itemsIndexed(entries, key = { _, entry -> entry.fileName }) { index, entry ->
            val isCurrent = index == currentIndex
            // 既読区別（監査 ia Minor 15-§E）: 現在章より前は読み終えた章なので淡色（textSecondary）へ落とし、
            // 「あとどれだけ」の見当識を与える。currentIndex から導出でき目次データ追加は不要。currentIndex<0
            // （未読・現在章が目次に無い）なら既読は無い＝全章を未読色のまま出す。
            // モック toc-D.html は現在章（.li.cur）しか区別せず既読表現を持たないため、この淡色化はモック非表現
            // ＝最終ユーザー確認バッチ行き。色は既存 ReadingColors トークンのみ使用（直書き禁止）。
            val isRead = currentIndex >= 0 && index < currentIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChapter(entry.fileName) },
                // 現在章は淡いアクセント背景で「面」として示し、文字色だけより見落としにくくする
                color = if (isCurrent) colors.accent.copy(alpha = 0.06f) else Color.Transparent,
            ) {
                Column {
                    // height(IntrinsicSize.Min) で左バーの fillMaxHeight をテキスト行高に一致させる
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // 現在章は行頭の左アクセントバーで一目で分かるようにする。
                        // 非現在章も透明な同幅バーを置き、全行のテキスト開始位置を揃える。
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    if (isCurrent) colors.accent else Color.Transparent,
                                ),
                        )
                        Text(
                            text = entry.title.ifEmpty { "第${index + 1}章" },
                            modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S16),
                            fontSize = FontSectionTitle,
                            fontFamily = MinchoFamily,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 24.sp,
                            // 現在章＝アクセント色＋太字／既読＝淡色（読み終えた見当識）／未読＝本文色。
                            color = when {
                                isCurrent -> colors.accent
                                isRead -> colors.textSecondary
                                else -> colors.text
                            },
                        )
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(Spacing.S32)) }
    }
}

/**
 * 目次ロード中のスケルトン。章リストと同じ行構成（左バー幅＋テキスト行＋区切り線）の
 * プレースホルダを並べ、充足後のレイアウト飛びを抑える。
 * なぜ静的（アニメ無し）か: 過剰演出を避け、既存画面に無いシマー等の新規視覚要素を持ち込まないため。
 */
@Composable
private fun TocSkeleton(colors: ReadingColors, modifier: Modifier = Modifier) {
    // プレースホルダ帯の色は本文色を薄く落として素地に馴染ませる（テーマトークン経由・直書き禁止）
    val barColor = colors.text.copy(alpha = 0.07f)
    Column(modifier = modifier) {
        // 章題の長さに揺らぎを持たせて「文章の目次」らしく見せる（0.55〜0.9 の幅）
        val widthFractions = listOf(0.85f, 0.6f, 0.9f, 0.7f, 0.8f, 0.55f, 0.88f, 0.65f)
        widthFractions.forEach { fraction ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 実リストの左アクセントバーと同じ 4dp 分のインデントを確保して整列させる
                Spacer(modifier = Modifier.width(Spacing.S4))
                Box(
                    modifier = Modifier
                        .padding(horizontal = Spacing.S24, vertical = Spacing.S24)
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
                )
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun NativeTocPreview_Content() {
    NativeTableOfContentsScreen(
        tocState = TocState.Content(
            listOf(
                TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
                TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
                TocEntry(title = "第三章 決戦", fileName = "chap_3.html"),
            )
        ),
        colors = ReadingTheme.LIGHT.colors,
        currentChapterFile = "chap_2.html",
        onSelectChapter = {},
        onNavigateToBookshelf = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun NativeTocPreview_Loading() {
    NativeTableOfContentsScreen(
        tocState = TocState.Loading,
        colors = ReadingTheme.LIGHT.colors,
        currentChapterFile = null,
        onSelectChapter = {},
        onNavigateToBookshelf = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun NativeTocPreview_Empty() {
    NativeTableOfContentsScreen(
        tocState = TocState.Empty,
        colors = ReadingTheme.LIGHT.colors,
        currentChapterFile = null,
        onSelectChapter = {},
        onNavigateToBookshelf = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun NativeTocPreview_Error() {
    NativeTableOfContentsScreen(
        tocState = TocState.Error("index.html が見つかりません"),
        colors = ReadingTheme.DARK.colors,
        currentChapterFile = null,
        onSelectChapter = {},
        onNavigateToBookshelf = {},
        onRetry = {},
    )
}
