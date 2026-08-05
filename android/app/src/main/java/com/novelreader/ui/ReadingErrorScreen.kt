package com.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.Spacing

/** エラー表示UI（ファイル欠損・パース失敗時）*/
@Composable
internal fun ReadingErrorScreen(
    message: String,
    colors: ReadingColors,
    onNavigateToBookshelf: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        // トップレベル（Scaffold 外）からも呼ばれるため自前で背景色を塗る
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "読み込みに失敗しました",
                fontFamily = MinchoFamily,
                fontSize = FontSectionTitle,
                // エラー見出しは意味を運ぶ文字＝装飾補助の textSecondary でなく AA を満たす infoText を使う（ADR 0014-D）。
                color = colors.infoText,
            )
            Text(
                text = message,
                fontFamily = MinchoFamily,
                fontSize = FontCaption,
                // なぜ copy(alpha=0.75) を撤去したか: 意味を運ぶエラー本文を alpha で沈めると実効色が
                // 素地上で AA(4.5:1) を割る（旧実測 L2.56/S2.31/D3.18）。ADR 0014-D「意味テキストは
                // alpha で作らない」に従い、素地上 4.5:1 を満たす infoText を素値で使う。
                color = colors.infoText,
                modifier = Modifier.padding(top = Spacing.S4, bottom = Spacing.S16),
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(bottom = Spacing.S8),
                ) {
                    Text("再試行")
                }
            }
            Button(onClick = onNavigateToBookshelf) {
                Text("本棚に戻る")
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ReadingErrorScreenPreview_WithRetry() {
    ReadingErrorScreen(
        message = "ファイルが見つかりません",
        colors = ReadingTheme.LIGHT.colors,
        onNavigateToBookshelf = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ReadingErrorScreenPreview_NoRetry() {
    ReadingErrorScreen(
        message = "index.html の解析に失敗しました",
        colors = ReadingTheme.DARK.colors,
        onNavigateToBookshelf = {},
        onRetry = null,
    )
}
