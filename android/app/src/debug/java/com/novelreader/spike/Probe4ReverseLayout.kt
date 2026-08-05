package com.novelreader.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * プローブ4: LazyRow(reverseLayout=true) の座標系の実挙動確認。
 *
 * なぜ必要か: 縦書きは「行が右→左に進む」＝横スクロールを反転（reverseLayout=true）で表現する案がある。
 * その際 firstVisibleItemScrollOffset の符号・原点（どちらの端を 0 とするか）と、scrollToItem/animateScrollBy の
 * offset 符号が RTL 反転でどう振る舞うかは公式ドキュメントだけでは確信が持てない。ボタンで実操作し、
 * オーバーレイの index/offset を目視して仕様を確定させる（列インデックス⇔スクロール位置の対応表を作る土台）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Probe4ReverseLayoutScreen() {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        // リアルタイム座標オーバーレイ（state 読取＝再コンポーズで追従）。
        Text(
            text = "reverseLayout=true\n" +
                "firstVisibleItemIndex=${state.firstVisibleItemIndex}\n" +
                "firstVisibleItemScrollOffset=${state.firstVisibleItemScrollOffset}",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { scope.launch { state.scrollToItem(10, 0) } }) { Text("scrollTo(10,0)") }
            Button(onClick = { scope.launch { state.scrollToItem(10, 100) } }) { Text("scrollTo(10,+100)") }
            Button(onClick = { scope.launch { state.animateScrollBy(500f) } }) { Text("scrollBy(+500)") }
            Button(onClick = { scope.launch { state.animateScrollBy(-500f) } }) { Text("scrollBy(-500)") }
        }

        LazyRow(
            state = state,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 8.dp),
        ) {
            // 幅可変の番号付きアイテム 50 個（幅が index で変わる＝offset の効き方を目視しやすく）。
            items(50) { i ->
                val w = 80 + (i % 5) * 30
                Box(
                    modifier = Modifier
                        .width(w.dp)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(if (i % 2 == 0) Color(0xFF334155) else Color(0xFF475569)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#$i\n${w}dp", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
