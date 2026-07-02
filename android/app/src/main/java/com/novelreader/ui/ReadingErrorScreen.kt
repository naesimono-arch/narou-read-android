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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors

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
                fontSize = 16.sp,
                color = colors.textSecondary,
            )
            Text(
                text = message,
                fontFamily = MinchoFamily,
                fontSize = 12.sp,
                color = colors.textSecondary.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(bottom = 8.dp),
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
