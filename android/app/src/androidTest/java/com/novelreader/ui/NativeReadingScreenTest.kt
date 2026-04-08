package com.novelreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novelreader.NovelReaderApplication
import com.novelreader.viewmodel.BookshelfViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * NativeReadingScreen の Instrumented テスト。
 *
 * テストケース:
 *  - 章ナビゲーション（次へ・前へ）
 *  - startFile 防御（存在しないファイル → 目次フォールバック）
 *  - エラーUI（index.html なし / パース例外）
 *  - Activity 再生成による rememberSaveable の状態復元
 *
 * ViewModel レベルの saveProgress 検証は BookshelfViewModelTest で担保済みのため
 * ここでは UI の状態遷移（章タイトルの表示変化）をもって間接的に確認する。
 */
@RunWith(AndroidJUnit4::class)
class NativeReadingScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var htmlDir: File
    private lateinit var viewModel: BookshelfViewModel

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // テストごとに独立したディレクトリを使い、並行実行時の衝突を防ぐ
        htmlDir = File(ctx.filesDir, "test_html_${System.currentTimeMillis()}")
        htmlDir.mkdirs()
        writeTestFixtures(htmlDir)
        viewModel = BookshelfViewModel(ctx.applicationContext as NovelReaderApplication)
    }

    @After
    fun tearDown() {
        htmlDir.deleteRecursively()
    }

    // ── 章ナビゲーション ──────────────────────────────────────────────────

    @Test
    fun 次へボタンで次の章に遷移する() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_1.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        // 非同期パース完了を待機
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("次の章").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第二章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第二章").assertIsDisplayed()
    }

    @Test
    fun 最初の章で前へボタンを押すと目次に遷移する() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_1.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章").fetchSemanticsNodes().isNotEmpty()
        }
        // currentIndex = 0 のとき prevFile = "index.html"（仕様通り）
        composeTestRule.onNodeWithContentDescription("前の章").performClick()
        // 目次画面: TOC エントリが表示される（非同期ロードを待機）
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章タイトル").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第一章タイトル").assertIsDisplayed()
    }

    @Test
    fun 目次ボタンで目次画面に遷移する() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_1.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("目次").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章タイトル").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第一章タイトル").assertIsDisplayed()
        composeTestRule.onNodeWithText("第二章タイトル").assertIsDisplayed()
    }

    @Test
    fun 目次から章を選択すると章画面に遷移する() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "index.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章タイトル").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第一章タイトル").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第一章").assertIsDisplayed()
    }

    // ── startFile 防御 ────────────────────────────────────────────────────

    @Test
    fun 存在しないstartFileは目次にフォールバックする() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "nonexistent_999.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        // index.html へフォールバック → 目次画面が表示される
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章タイトル").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第一章タイトル").assertIsDisplayed()
    }

    // ── エラーUI ─────────────────────────────────────────────────────────

    @Test
    fun index_htmlも存在しない場合はエラーUIが表示される() {
        val emptyDir = File(htmlDir, "empty_${System.currentTimeMillis()}").also { it.mkdirs() }
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_1.html",
                htmlDirPath = emptyDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        // resolvedFile == null → 即時エラーUI（非同期処理なし）
        composeTestRule.onNodeWithText("読み込みに失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("WebView版で開く").assertIsDisplayed()
        composeTestRule.onNodeWithText("本棚に戻る").assertIsDisplayed()
    }

    @Test
    fun パース例外でエラーUIが表示される() {
        // なぜディレクトリを使うか: startFile防御は candidate.exists() が true のとき通過するが、
        // Jsoup.parse(File) にディレクトリを渡すと IsADirectoryException が発生する。
        // これにより try-catch → ParseResult.Error → エラーUI のパスをテストできる。
        File(htmlDir, "chap_3.html").mkdir()

        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_3.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("読み込みに失敗しました").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("読み込みに失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("WebView版で開く").assertIsDisplayed()
    }

    // ── プロセス再生成（rememberSaveable） ────────────────────────────────

    @Test
    fun Activity再生成後に表示中の章が保持される() {
        composeTestRule.setContent {
            NativeReadingScreen(
                bookId = "test",
                startFile = "chap_1.html",
                htmlDirPath = htmlDir.absolutePath,
                viewModel = viewModel,
                onNavigateToBookshelf = {},
            )
        }
        // 第一章を表示後、次へで第二章に遷移
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第一章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("次の章").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第二章").fetchSemanticsNodes().isNotEmpty()
        }

        // Activity 再生成（回転・プロセス再生成のシミュレーション）
        composeTestRule.activityRule.scenario.recreate()

        // rememberSaveable(key = "currentFile_test") により第二章が復元される
        // なぜ startFile = "chap_1.html" でも第二章が復元されるか:
        // rememberSaveable は初期値（startFile）より SavedStateRegistry の値を優先するため
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("第二章").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("第二章").assertIsDisplayed()
    }

    // ── テスト用HTMLフィクスチャ ──────────────────────────────────────────

    /**
     * テスト用HTML一式を dir に書き出す。
     * - index.html: ul.index-list 形式の目次（parseToc が期待する構造）
     * - chap_1.html, chap_2.html: h1 + div.content を持つ章HTML
     */
    private fun writeTestFixtures(dir: File) {
        File(dir, "index.html").writeText(
            """
            <!DOCTYPE html><html lang="ja"><head><title>テスト書籍</title></head>
            <body><div class="container">
            <ul class="index-list">
              <li><a href="chap_1.html">第一章タイトル</a></li>
              <li><a href="chap_2.html">第二章タイトル</a></li>
            </ul>
            </div></body></html>
            """.trimIndent()
        )
        File(dir, "chap_1.html").writeText(chapterHtml("第一章", "第一章の本文テキストです。"))
        File(dir, "chap_2.html").writeText(chapterHtml("第二章", "第二章の本文テキストです。"))
    }

    private fun chapterHtml(title: String, body: String) =
        """
        <!DOCTYPE html><html lang="ja"><head><title>$title</title></head>
        <body><div class="container">
        <h1>$title</h1>
        <div class="content">$body</div>
        </div></body></html>
        """.trimIndent()
}
