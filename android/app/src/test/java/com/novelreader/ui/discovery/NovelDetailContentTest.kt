package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.discovery.model.workDetail
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.Ncode
import com.novelreader.viewmodel.NovelDetailUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NovelDetailContent（作品詳細の stateless 描画層）の状態分岐＋コールバック結線テスト（ADR 0009）。
 * Loading と Content（外部連携導線）の分岐、外部ブラウザ起動の結線がサイレント退行しないことを固定する。
 * ブラウザ起動そのもの（プラットフォーム副作用）はルート層が持つため、ここでは onReadOnNarou の発火のみ検証。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovelDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: NovelDetailUiState,
        onReadOnNarou: () -> Unit = {},
        onImportPdf: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                NovelDetailContent(
                    ncode = Ncode("N1234AB"),
                    uiState = uiState,
                    onSearchKeywords = {},
                    onImportPdf = onImportPdf,
                    onUp = {},
                    onRetry = {},
                    onReadOnNarou = onReadOnNarou,
                )
            }
        }
    }

    @Test
    fun `Loading状態はプログレスインジケータを描画する`() {
        setContent(NovelDetailUiState.Loading)
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }

    @Test
    fun `Content状態は作者となろうで読む導線を描画する`() {
        setContent(
            NovelDetailUiState.Content(
                novel = workDetail(summary = workSummary(title = "詳細作品", author = "作者名テスト")),
                fetchedAtMillis = 0L,
            ),
        )
        composeTestRule.onNodeWithText("作者名テスト").assertExists()
        composeTestRule.onNodeWithText("なろうで読む").assertIsDisplayed()
    }

    @Test
    fun `なろうで読むの押下でonReadOnNarouが呼ばれる`() {
        var read = false
        setContent(
            NovelDetailUiState.Content(
                novel = workDetail(summary = workSummary(title = "詳細作品", author = "作者名テスト")),
                fetchedAtMillis = 0L,
            ),
            onReadOnNarou = { read = true },
        )
        composeTestRule.onNodeWithText("なろうで読む").performClick()
        assertTrue(read)
    }

    // 縦書きPDF取り込みボタン（ADR 0011・仮意匠）の描画とコールバック結線がサイレント退行しないことを固定する。
    @Test
    fun `縦書きPDF取り込みの押下でonImportPdfが呼ばれる`() {
        var imported = false
        setContent(
            NovelDetailUiState.Content(
                novel = workDetail(summary = workSummary(title = "詳細作品", author = "作者名テスト")),
                fetchedAtMillis = 0L,
            ),
            onImportPdf = { imported = true },
        )
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").performClick()
        assertTrue(imported)
    }
}
