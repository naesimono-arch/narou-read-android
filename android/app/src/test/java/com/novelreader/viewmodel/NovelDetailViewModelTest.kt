package com.novelreader.viewmodel

import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class NovelDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: NovelReaderApplication
    private lateinit var mockRepo: NovelApiRepository
    private lateinit var viewModel: NovelDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)
        every { mockApp.novelApiRepository } returns mockRepo
        viewModel = NovelDetailViewModel(mockApp)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load - 取得成功で Content に遷移し、同一ncodeの再loadは再取得しないこと`() = runTest {
        val novel = NarouNovel(title = "詳細作品", ncode = "N1234AB", story = "あらすじ")
        coEvery { mockRepo.novelDetail(Ncode("N1234AB")) } returns novel

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NovelDetailUiState.Content)
        assertEquals(novel, (state as NovelDetailUiState.Content).novel)

        // 再コンポーズ相当の再呼び出し
        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockRepo.novelDetail(Ncode("N1234AB")) }
    }

    @Test
    fun `load - 作品が存在しない場合は NotFound に遷移すること`() = runTest {
        coEvery { mockRepo.novelDetail(any()) } returns null

        viewModel.load(Ncode("N9999ZZ"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NovelDetailUiState.NotFound)
    }

    @Test
    fun `load - 通信失敗で Error に遷移し、retry で再取得されること`() = runTest {
        coEvery { mockRepo.novelDetail(any()) } throws NarouApiException("通信エラー", Exception())

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NovelDetailUiState.Error)
        assertEquals("通信エラー", (state as NovelDetailUiState.Error).message)

        val novel = NarouNovel(title = "復帰作品", ncode = "N1234AB")
        coEvery { mockRepo.novelDetail(any()) } returns novel
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NovelDetailUiState.Content)
        coVerify(exactly = 2) { mockRepo.novelDetail(Ncode("N1234AB")) }
    }
}
