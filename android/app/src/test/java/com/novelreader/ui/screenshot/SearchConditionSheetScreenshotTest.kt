package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.novelreader.domain.SearchDraft
import com.novelreader.domain.SearchFilters
import com.novelreader.ui.discovery.SearchConditionSheetContent
import com.novelreader.ui.skins.k.captureRoot
import com.novelreader.ui.skins.k.setSkinKContent
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「条件を調整」シート（`discovery/search` から開く [SearchConditionSheetContent]）のスクリーンショット回帰。
 * **監査 2026-08-06 G-8 で 0枚だったさがす配下**の最後の1面で、前便（2026-08-07）が
 * 「本番が `viewModel` を直接受ける形のため撮れない・監督への申し送り」として残していた穴。
 * 本便で本番側を枠（VM 受け）／中身（コールバック受け）へ分けたので撮れるようになった。
 *
 * K 素地で包む理由は [DiscoverySearchScreenScreenshotTest] と同じ（出荷時に載る素地が K）。
 *
 * ### 撮る case と選定理由
 *  - `top`（3テーマ×2スケール全数）＝シートを開いた直後に見える範囲。中身は
 *    `heightIn(max = 画面高85%)` ＋ `verticalScroll` で、**開いた瞬間に見えるのはジャンル節まで**
 *    ＝ここがこの面の「版面」。大ジャンル小見出し＋ FlowRow のチップ群が 2.0 で何段に折り返し、
 *    どこまで押し出されるかを固定する。
 *  - `exclusive`（ライトのみ×2スケール）＝文字数を選んで読了時間節が無効化された状態を、
 *    確定ボタンまでスクロールして撮る。**上端固定では絵に一切写らない領域**（排他注記・カスタム
 *    範囲入力・会話率・挿絵・「この条件で探す」／「リセット」）をこの1枚が受け持つ。
 *    テーマ全数を撮らないのは色トークンが `top` と共通で、テーマ退行はそちらの束が張るため。
 *
 * 撮っていないもの（既知の穴）: ジャンル・テーマ属性チップの選択状態（点灯色は
 * [DiscoverySearchScreenScreenshotTest] の検索範囲チップが同じ `FilterChipItem` で張る）。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SearchConditionSheetScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val draft = if (caseId == CASE_EXCLUSIVE) EXCLUSIVE_DRAFT else SearchDraft()
        composeTestRule.setSkinKContent(theme, fontScale) { _ ->
            // シートの中身は自前で背景を持たない（本番では ModalBottomSheet の containerColor が敷く）。
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SearchConditionSheetContent(
                    draft = draft,
                    onSetDraft = {},
                    onToggleLengthStep = {},
                    onToggleTimeStep = {},
                    onSetLengthCustomText = { _, _ -> },
                    onSetTimeCustomText = { _, _ -> },
                    onDismiss = {},
                    onSearch = {},
                )
            }
        }
        if (caseId == CASE_EXCLUSIVE) {
            // 下端の確定ボタンまでスクロールしてから撮る（撮影前の操作が要る画面のために
            // KScreenshotSupport が setSkinKContent と captureRoot を分けて公開している形）。
            composeTestRule.onNodeWithText("この条件で探す").performScrollTo()
        }
        composeTestRule.captureRoot(goldenName("SearchConditionSheet", caseId, theme, fontScale))
    }

    companion object {
        private const val CASE_TOP = "top"
        private const val CASE_EXCLUSIVE = "exclusive"

        /**
         * 文字数をカスタム値で持たせる＝①読了時間節が無効化され注記が出る（F-I の排他提示）
         * ②文字数側のカスタム範囲入力欄が開く（`selectedStepIndices` で分解できない値なので
         * `isLengthCustom` が真になる）。この2つが同時に写る唯一の draft 形。
         */
        private val EXCLUSIVE_DRAFT = SearchDraft(
            word = "廃鉱山",
            lengthCustomMin = "12",
            lengthCustomMax = "48",
            filters = SearchFilters(length = "120000-480000"),
        )

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_TOP, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_EXCLUSIVE, ReadingTheme.LIGHT, s))
            }
        }
    }
}
