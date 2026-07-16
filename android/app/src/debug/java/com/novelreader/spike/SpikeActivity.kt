package com.novelreader.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 縦書きモード実装スパイク（P0-1/3/4）の実機プローブ画面。
 *
 * なぜ存在するか: 縦書きレイアウトの実装方式を決める前に、Android/端末（特に OPPO/ColorOS）での
 * 実挙動を「机上の推測」でなく「実機の機械判定」で確定させるための使い捨て計測器。
 * 本アプリのプロダクション経路とは完全に隔離するため debug ソースセットに閉じ、
 * `am start -n com.novelreader/com.novelreader.spike.SpikeActivity` で直接起動する。
 *
 * 3つのプローブ:
 *  - プローブ1【最重要】: fontFeatureSettings="vert" が字形を実際に差し替えるか（句読点・括弧・小書き仮名等）を
 *    オフスクリーン Bitmap のピクセル一致で機械判定し、結果を JSON Lines で吐く（Probe1FontFeature.kt）。
 *  - プローブ3: 「1段落=1アイテム」の巨大縦組み Canvas の measure/draw コスト実測（Probe3MeasureCost.kt）。
 *  - プローブ4: LazyRow(reverseLayout=true) の座標系（offset の符号・原点）実挙動確認（Probe4ReverseLayout.kt）。
 *
 * UI の意匠は不問（計測が目的・/visual-language ゲート対象外）。
 */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpikeRoot()
                }
            }
        }
    }
}

private val TABS = listOf("P1 vert判定", "P4 reverseLayout", "P3 measureコスト")

@Composable
private fun SpikeRoot() {
    // なぜタブ切替か: 3プローブは独立した計測で、同時表示すると P3 の描画負荷が P1/P4 の計測を汚す。
    // 表示中のプローブだけを合成する（未選択タブは composition から外れる）。
    var selected by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            when (selected) {
                0 -> Probe1FontFeatureScreen()
                1 -> Probe4ReverseLayoutScreen()
                2 -> Probe3MeasureCostScreen()
            }
        }
    }
}
