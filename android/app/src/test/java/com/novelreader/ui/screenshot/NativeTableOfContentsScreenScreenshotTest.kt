package com.novelreader.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.model.TocEntry
import com.novelreader.ui.NativeTableOfContentsScreen
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NativeTableOfContentsScreen（Content 状態・現在章ハイライトあり）の 3テーマ × fontScale{1.0,2.0}
 * スクリーンショット回帰（ADR 0009 増補1）。章リスト・区切り線・accent（現在章ハイライト）が
 * テーマトークンから引かれるため、テーマ退行とフォントスケール破綻の双方を捉えやすい。
 * ゲート非同乗の理由は ScreenshotTestSupport.kt 参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NativeTableOfContentsScreenScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tocContent = TocState.Content(
        listOf(
            TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
            TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
            TocEntry(title = "第三章 再会", fileName = "chap_3.html"),
            TocEntry(title = "第四章 決別", fileName = "chap_4.html"),
        )
    )

    @Test
    fun capture() {
        val name = "NativeTableOfContentsScreen_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"
        composeTestRule.captureThemed(theme, fontScale, name) { colors ->
            NativeTableOfContentsScreen(
                tocState = tocContent,
                colors = colors,
                // 第二章を現在章にして accent ハイライトを描画対象に含める。
                currentChapterFile = "chap_2.html",
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
