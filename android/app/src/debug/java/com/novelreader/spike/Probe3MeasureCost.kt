package com.novelreader.spike

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * プローブ3: 「1段落=1アイテム」を模した巨大縦組み Canvas の measure/draw コスト実測。
 *
 * なぜ必要か: 縦書き読書を LazyRow で「1段落=1列群アイテム」として組む案では、長い段落 1 個が
 * 数千〜数万文字を 1 アイテム内で drawText 連打することになる。この 1 アイテムの初回コンポーズ〜初回描画の所要時間と、
 * スクロール中のフレーム時間が実用域に収まるかを実機で測る（組版品質は不問＝drawText 呼び出し回数が本物相当であればよい）。
 * 計測は画面表示に加え logcat(tag=SpikeMeasure) にも出す（adb logcat で後追いできるように）。
 */
private const val TAG = "SpikeMeasure"

// N 文字のダミー日本語文字列（1文字ずつ縦組み配置するので内容は不問・句読点も混ぜて描画分岐を本物寄りに）。
private fun buildDummy(n: Int): String {
    val base = "吾輩は猫である名前はまだ無い。どこで生れたか頓と見当がつかぬ、"
    val sb = StringBuilder(n)
    while (sb.length < n) sb.append(base)
    return sb.substring(0, n)
}

@Composable
fun Probe3MeasureCostScreen() {
    var nFloat by remember { mutableFloatStateOf(12000f) } // 既定 12000（1000〜30000）
    val n = nFloat.toInt()
    var lastComposeToDrawMs by remember { mutableStateOf("-") }
    var frameStats by remember { mutableStateOf("スクロールするとフレーム時間を集計") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "N(1段落の文字数)=$n\n初回compose→初回draw: $lastComposeToDrawMs\n$frameStats",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(6.dp),
        )
        Slider(
            value = nFloat,
            onValueChange = { nFloat = it },
            valueRange = 1000f..30000f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        val state = rememberLazyListState()

        // スクロール中のフレーム時間集計（withFrameNanos の連続差分）。isScrollInProgress の間だけ計測する。
        LaunchedEffect(state) {
            var prev = 0L
            var maxMs = 0.0
            var sum = 0.0
            var count = 0
            while (true) {
                withFrameNanos { now ->
                    if (state.isScrollInProgress) {
                        if (prev != 0L) {
                            val dtMs = (now - prev) / 1_000_000.0
                            maxMs = maxOf(maxMs, dtMs)
                            sum += dtMs
                            count++
                            frameStats = "frame avg=%.1fms max=%.1fms (n=%d)".format(sum / count, maxMs, count)
                        }
                        prev = now
                    } else {
                        prev = 0L // スクロール停止でリセット（次のスクロールを独立に測る）
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val density = LocalDensity.current
            val heightPx = with(density) { maxHeight.toPx() }
            val text = remember(n) { buildDummy(n) }

            LazyRow(state = state, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                // 段落アイテム 15 個。可視分だけが compose/draw される＝LazyRow の遅延評価コストを本物相当で測る。
                items(15) { paragraphIndex ->
                    VerticalParagraphItem(
                        text = text,
                        heightPx = heightPx,
                        onFirstDrawMs = { ms ->
                            lastComposeToDrawMs = "%.1fms (para#%d, N=%d)".format(ms, paragraphIndex, n)
                            Log.i(TAG, "compose->firstDraw ${"%.2f".format(ms)}ms para=$paragraphIndex N=$n")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalParagraphItem(
    text: String,
    heightPx: Float,
    onFirstDrawMs: (Double) -> Unit,
) {
    // compose 開始時刻（この Composable がツリーに入った瞬間）。初回 draw との差分が「compose→初回描画」。
    val composeStartNs = remember(text) { System.nanoTime() }
    // 初回描画のみ報告するガード。draw ラムダは毎フレーム走るため compareAndSet で 1 回に絞る
    // （text 変化＝別アイテム再生成で新しい AtomicBoolean になり、入り直したアイテムを独立に計測できる）。
    val firstDrawReported = remember(text) { java.util.concurrent.atomic.AtomicBoolean(false) }

    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(0x1a, 0x1a, 0x1a)
            textSize = 42f
            typeface = android.graphics.Typeface.SERIF
        }
    }

    // 列高＝画面高相当。1列に入る行数と総列数からアイテム幅を先に確定させる（draw 前に width を決める必要がある）。
    val rowH = 46f
    val colW = 52f
    val rows = maxOf(1, (heightPx / rowH).toInt())
    val cols = (text.length + rows - 1) / rows
    val widthPx = cols * colW
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }

    Box(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .padding(horizontal = 2.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val drawStart = System.nanoTime()
                var idx = 0
                var col = 0
                // 列は右→左（縦書きの行送り）。文字は上→下。組版品質は不問＝drawText 回数が本物相当であればよい。
                while (idx < text.length) {
                    val x = size.width - colW * (col + 0.5f)
                    var row = 0
                    while (row < rows && idx < text.length) {
                        val y = rowH * (row + 1)
                        native.drawText(text, idx, idx + 1, x, y, paint)
                        idx++
                        row++
                    }
                    col++
                }
                if (firstDrawReported.compareAndSet(false, true)) {
                    val drawMs = (System.nanoTime() - drawStart) / 1_000_000.0
                    val composeToDrawMs = (System.nanoTime() - composeStartNs) / 1_000_000.0
                    Log.i(TAG, "drawLoop ${"%.2f".format(drawMs)}ms chars=${text.length}")
                    onFirstDrawMs(composeToDrawMs)
                }
            }
        }
    }
}
