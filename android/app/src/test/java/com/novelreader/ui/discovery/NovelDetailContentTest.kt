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
import com.novelreader.viewmodel.NovelDetailUiState
import org.junit.Assert.assertFalse
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
        isImported: Boolean = false,
        hasLocalBookmark: Boolean = false,
        lastReadEpisode: Int = 0,
        onResumeReading: () -> Unit = {},
        onOpenImportedBook: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                NovelDetailContent(
                    uiState = uiState,
                    onSearchKeywords = {},
                    onImportPdf = onImportPdf,
                    onUp = {},
                    onRetry = {},
                    onReadOnNarou = onReadOnNarou,
                    lastReadEpisode = lastReadEpisode,
                    onResumeReading = onResumeReading,
                    isImported = isImported,
                    hasLocalBookmark = hasLocalBookmark,
                    onOpenImportedBook = onOpenImportedBook,
                )
            }
        }
    }

    private fun content() = NovelDetailUiState.Content(
        novel = workDetail(summary = workSummary(title = "詳細作品", author = "作者名テスト")),
        fetchedAtMillis = 0L,
    )

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

    // ---- 案A（2026-09-04 裁定）: 取込済みの主CTA は手元の蔵書 ----
    // なぜ固定するか: 取込済みの分岐は golden が1枚も撮っておらず（既知の穴）、ここが唯一の回帰網。
    // 真因（作品詳細が bookId を持たず、取込済みでも全ボタンがなろうへ出る）が戻ったら落ちる形にする。
    // ⚠️ 栞は2つある＝**主＝手元（hasLocalBookmark）／副＝なろう（lastReadEpisode）**。
    // 以下は2軸が独立に効くことを4通りで固定する（片方の軸がもう片方を汚したら落ちる）。

    @Test
    fun `取込済みで手元に栞が無ければ主CTAはアプリで読む`() {
        setContent(content(), isImported = true)
        composeTestRule.onNodeWithText("アプリで読む").assertIsDisplayed()
        // なろう系は**降ろすが消さない**（アプリ自身の新着通知の着地がなろうの WebView だけのため）。
        composeTestRule.onNodeWithText("なろうで読む").assertIsDisplayed()
        // 取込済みでは取込とその周辺（本棚に置く）は冗長で消える＝スロットは2つのまま（総高137dp）。
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").assertDoesNotExist()
        composeTestRule.onNodeWithText("本棚に置く").assertDoesNotExist()
    }

    @Test
    fun `取込済みで手元に栞があれば主CTAはアプリで続きからになる`() {
        setContent(content(), isImported = true, hasLocalBookmark = true, lastReadEpisode = 12)
        composeTestRule.onNodeWithText("アプリで続きから").assertIsDisplayed()
        composeTestRule.onNodeWithText("アプリで読む").assertDoesNotExist()
    }

    // ⚠️ 2軸が独立であることの本丸: なろうを一度も訪れていなくても手元の栞は「続きから」になる。
    // 旧実装はここでなろうの位置を見ていたため「未読の顔で続きへ着地する」食い違いが出ていた。
    @Test
    fun `手元に栞がありなろう未訪でも主は続きからで副は行ごと消えない`() {
        setContent(content(), isImported = true, hasLocalBookmark = true, lastReadEpisode = 0)
        composeTestRule.onNodeWithText("アプリで続きから").assertIsDisplayed()
        // 副はなろうの栞だけで決まる＝位置が無いので「なろうで読む」。行を消すと 81dp へ落ちて
        // 「4状態すべて137dp」が壊れるため、消さずに1つ出す。
        composeTestRule.onNodeWithText("なろうで読む").assertIsDisplayed()
        composeTestRule.onNodeWithText("なろうの目次").assertDoesNotExist()
    }

    @Test
    fun `取込済みでなろう既訪なら副に「なろうで」が前置される`() {
        setContent(content(), isImported = true, lastReadEpisode = 12)
        composeTestRule.onNodeWithText("アプリで読む").assertIsDisplayed()
        // 主が蔵書側へ移った以上、前置が無いと「第12話」が何の話数か言えない（手元の栞となろうの位置は別物）。
        composeTestRule.onNodeWithText("なろうで第12話から").assertIsDisplayed()
        composeTestRule.onNodeWithText("なろうの目次").assertIsDisplayed()
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").assertDoesNotExist()
    }

    @Test
    fun `アプリで読むの押下でonOpenImportedBookが呼ばれる`() {
        var opened = false
        var narou = false
        setContent(
            content(),
            isImported = true,
            lastReadEpisode = 12,
            onReadOnNarou = { narou = true },
            onOpenImportedBook = { opened = true },
        )
        composeTestRule.onNodeWithText("アプリで読む").performClick()
        assertTrue(opened)
        // 主CTA がなろう側へ結線し直る退行（＝真因そのものの再発）を落とす。
        assertFalse(narou)
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
