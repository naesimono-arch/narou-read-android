// 左右スワイプ章送り「引っ張りプレビュー」の部品（覗き素材 ChapterPeek＋覗きパネル ChapterPeekPanel）。
// なぜ分離したか: NativeReadingScreen.kt の純移動分割（2026-07-27）＝画面骨格だけを残し、部品として
// 自己完結した宣言を役割単位のファイルへ移すため。中身は無改変の純移動（可視性昇格のみ）。
package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.novelreader.model.ChapterContent as ChapterContentModel
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme

/**
 * スワイプ覗きの表示素材＝隣章のパース済み本文と、着地と同一規則（resolveInitialScroll）で解決した
 * 初期スクロール位置。位置を焼き込むのは「覗いた表示＝遷移後の表示」を構造的に保証するため。
 */
internal data class ChapterPeek(
    val content: ChapterContentModel,
    val initialScrollIndex: Int,
    val initialScrollOffset: Int,
)

/**
 * スワイプ引っ張りで端から覗く隣章パネル。
 * なぜ実物の [ChapterContent] を使うか: 覗きの内容を遷移後の初期表示と完全一致させ、
 * 確定スライド→章切替が連続して見えるようにするため（専用の軽量プレビューだと書体・版面の再現が
 * 二重管理になる）。LazyListState は peek の初期位置＝その章を読んでいた場所から表示する
 *（章ごとの位置記憶＝親 ReadingScreen の sessionScrollByFile。2026-07-16 実機フィードバック）。
 * @param translationX 覗き位置（px）。draw 段で読む deferred read（ドラッグ毎フレームの recompose 回避）。
 * @param verticalMode true で覗きの中身を縦書き [VerticalChapterContent] で描く（覗き＝遷移後表示の完全一致の
 *   原則。位置保存 (index, offset) の意味は縦横同型＝そのまま渡す）。
 */
// internal（旧 private）: 純移動のファイル分割で呼び出し元 ChapterScreenContent（NativeReadingScreen.kt）と
// 別ファイルになったため、跨ファイル参照できる最小可視性へ昇格する。
@Composable
internal fun ChapterPeekPanel(
    translationX: () -> Float,
    peek: ChapterPeek,
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
    verticalMode: Boolean = false,
    // スキンJ の章扉 ambient/glyph の変種選択に使う（覗きも本表示と同じテーマ面で描く）。既定 DARK は既存互換。
    readingTheme: ReadingTheme = ReadingTheme.DARK,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.translationX = translationX() }
            // 不透明の紙面で現章を覆う（ChapterContent は背景を塗らず Scaffold 任せのため、ここで明示する）。
            .background(colors.background),
    ) {
        val peekListState = remember(peek) {
            LazyListState(peek.initialScrollIndex, peek.initialScrollOffset)
        }
        // 覗き＝遷移後の初期表示と完全一致させる（本文と同じ分岐）。縦書きは横スクロールの LazyRow。
        if (verticalMode) {
            VerticalChapterContent(
                content = peek.content,
                colors = colors,
                fontSize = fontSize,
                lineHeightEm = lineHeightEm,
                bodyMarginDp = bodyMarginDp,
                lazyListState = peekListState,
            )
        } else {
            ChapterContent(
                content = peek.content,
                colors = colors,
                fontSize = fontSize,
                lineHeightEm = lineHeightEm,
                bodyMarginDp = bodyMarginDp,
                lazyListState = peekListState,
                readingTheme = readingTheme,
            )
        }
    }
}
