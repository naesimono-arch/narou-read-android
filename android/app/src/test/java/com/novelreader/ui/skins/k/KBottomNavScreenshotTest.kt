package com.novelreader.ui.skins.k

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K の恒常ボトムナビ（[KBottomNav]）のスクリーンショット回帰（ADR 0009 増補1）。
 *
 * なぜ画面本体とは別に撮るか: このバーは NavHost の外（MainActivity）に静止表示され、K の各画面
 * golden には**含まれない**＝本棚/さがす/設定のどの golden でも無検査になる。一方で K の設計の核
 * （第三者テスト「一目でどの画面か分からない」への回答＝現在地と目的地一覧の常時可視化）を担う
 * 部品であり、ラベルが消える・選択ピルが消えるといった退行は K の存在理由を壊す。
 *
 * 撮る状態と選定理由: current=本棚（既定の起動タブ）。1枚に選択タブ（藍ピル＋藍アイコン＋太字ラベル）と
 * 非選択タブ2つ（onSurfaceVariant・通常ラベル）が同居するため、選択/非選択の描き分けがこの1状態で
 * 全部そろう。他タブ選択時の絵はピルの水平位置が変わるだけで、新しい退行は捉えられない。
 *
 * このテストが赤くなる条件:
 *  ・ラベル（本棚/さがす/設定）の有無・字面・太さ・11sp の大きさ
 *  ・選択ピル（56x32・shapes.large・primary 10%）の有無/寸法/色
 *  ・アイコンの図柄（MenuBook/Search/Settings）と 24dp サイズ・選択/非選択の tint
 *  ・バー高 64dp・上罫ヘアライン（outlineVariant）・面色（surface）
 *  ・3タブの等幅配置（weight(1f)）
 *  ・fontScale 2.0 でラベルが 64dp 固定高からはみ出す/切れる変化（＝この構造の最大の破綻リスク）
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class KBottomNavScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureSkinK(theme, fontScale, goldenName("KBottomNav", "bookshelf", theme, fontScale)) { _ ->
            // バー自身は wrap 高＝画面素地の上に載る部品として撮る（全画面を敷くと余白ばかりの golden になる）。
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                KBottomNav(current = KTab.BOOKSHELF, onSelect = {})
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
