package com.novelreader.viewmodel

import android.content.Intent
import androidx.core.content.ContextCompat
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.repository.BookRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class BookshelfViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: NovelReaderApplication
    private lateinit var mockRepository: BookRepository
    private lateinit var viewModel: BookshelfViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockApp = mockk(relaxed = true)

        every { mockApp.repository } returns mockRepository
        every { mockApp.processingState } returns MutableStateFlow<ProcessingState?>(null).asStateFlow()
        every { mockApp.errorEvents } returns emptyFlow()
        every { mockRepository.allBooks } returns flowOf(emptyList())

        viewModel = BookshelfViewModel(mockApp)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初期状態 ──────────────────────────────────────────────────────────
    // stateIn(WhileSubscribed) の初期値はサブスクライバー不要で .value から取得可能

    @Test
    fun `初期状態 - books が emptyList を返す`() {
        assertEquals(emptyList<BookEntity>(), viewModel.books.value)
    }

    @Test
    fun `初期状態 - processingState が ProcessingState() を返す`() {
        assertEquals(ProcessingState(), viewModel.processingState.value)
    }

    // ── DAO委譲 ───────────────────────────────────────────────────────────

    @Test
    fun `deleteBook - repository の deleteBook が呼ばれる`() = runTest {
        val book = BookEntity("id01", "テスト本", "/nonexistent/path")
        viewModel.deleteBook(book)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.deleteBook(book) }
    }

    @Test
    fun `getLastRead - repository の戻り値をそのまま返す`() = runTest {
        coEvery { mockRepository.getLastRead("id01") } returns "chapter_01.html"
        val result = viewModel.getLastRead("id01")
        assertEquals("chapter_01.html", result)
    }

    @Test
    fun `getLastRead - 未読の場合は null を返す`() = runTest {
        coEvery { mockRepository.getLastRead("id02") } returns null
        val result = viewModel.getLastRead("id02")
        assertNull(result)
    }

    // 進捗保存は単一チャネルに統合され、章移動・スクロール双方とも
    // repository.saveScrollPosition で書き込まれる（章移動はスクロール 0,0 = 章先頭）。
    @Test
    fun `saveProgress - 章移動はスクロール0で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveProgress("id01", "chapter_02.html")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition("id01", "chapter_02.html", 0, 0) }
    }

    @Test
    fun `saveScrollPosition - 指定位置で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveScrollPosition("id01", "chapter_02.html", 5, 120)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition("id01", "chapter_02.html", 5, 120) }
    }

    // ── addBook ───────────────────────────────────────────────────────────

    @Test
    fun `addBook - Intent の action と data と FLAG_GRANT_READ_URI_PERMISSION を検証する`() {
        // Android スタブは Intent のフィールドセットを実装しないため mockkConstructor で代替
        mockkConstructor(Intent::class)
        mockkStatic(ContextCompat::class)
        try {
            // apply ブロック内で呼ばれるメソッドを stub（setAction は Intent を返す）
            every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
            every { anyConstructed<Intent>().setData(any()) } returns mockk(relaxed = true)
            every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)
            justRun { ContextCompat.startForegroundService(any(), any()) }

            val uri = mockk<android.net.Uri>(relaxed = true)
            viewModel.addBook(uri)

            verify { ContextCompat.startForegroundService(any(), any()) }
            verify { anyConstructed<Intent>().setAction(PdfProcessingService.ACTION_START) }
            verify { anyConstructed<Intent>().setData(uri) }
            verify { anyConstructed<Intent>().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } finally {
            unmockkConstructor(Intent::class)
            unmockkStatic(ContextCompat::class)
        }
    }
}
