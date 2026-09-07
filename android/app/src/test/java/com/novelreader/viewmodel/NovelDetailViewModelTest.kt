package com.novelreader.viewmodel

import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.discovery.model.workDetail
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.Ncode
import com.novelreader.repository.FakeBookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var fakeBookRepo: FakeBookRepository
    private lateinit var viewModel: NovelDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)
        // 蔵書側は relaxed mock でなく Fake を挿す: importedBook は allBooks/allProgress の
        // **中身**で決まるので、値を差し替えられる実物の Flow でないと栞の有無を作り分けられない。
        // ⚠️ VM は init で app.repository を掴むため、構築より前に stub する必要がある。
        fakeBookRepo = FakeBookRepository()
        every { mockApp.novelApiRepository } returns mockRepo
        every { mockApp.repository } returns fakeBookRepo
        viewModel = NovelDetailViewModel(mockApp)
    }

    /** WhileSubscribed の StateFlow は購読者が居ないと上流を回さない＝value を読む前に購読を張る。 */
    private fun TestScope.subscribe(vararg flows: StateFlow<*>) {
        flows.forEach { flow -> backgroundScope.launch(testDispatcher) { flow.collect { } } }
    }

    /** 取込済み（ncode 紐付け済み）の蔵書1冊と、その本の**手元の栞**を仕込む。null＝進捗行なし。 */
    private fun seedImportedBook(lastReadFilename: String?) {
        fakeBookRepo.setBooks(
            listOf(BookEntity(id = "b1", title = "取込済みの本", htmlDirPath = "/tmp/b1", ncode = "N1234AB"))
        )
        fakeBookRepo.setProgress(
            lastReadFilename?.let { listOf(ProgressEntity(bookId = "b1", lastReadFilename = it)) } ?: emptyList()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load - 取得成功で Content に遷移し、同一ncodeの再loadは再取得しないこと`() = runTest {
        val novel = workDetail(summary = workSummary(title = "詳細作品", ncode = "N1234AB"), story = "あらすじ")
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

        val novel = workDetail(summary = workSummary(title = "復帰作品", ncode = "N1234AB"))
        coEvery { mockRepo.novelDetail(any()) } returns novel
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NovelDetailUiState.Content)
        coVerify(exactly = 2) { mockRepo.novelDetail(Ncode("N1234AB")) }
    }

    // ---- 固定バーの「顔」と主CTA の「着地」の一致（2026-09-04 裁定・2026-09-07 に判定軸を明示して再確認）----
    // 軸＝「バーの顔（ラベル）と、押したときの着地が食い違わないこと」。表示が事実として正しいかは第二義。
    // ⚠️ **なぜ描画層のテストでは足りないか**: NovelDetailContentTest は hasLocalBookmark を**引数で受け取る**ため、
    // その真偽値が何から出たかを検証できない。真因は〈顔＝なろうの位置／着地＝手元の栞〉と**源が2つに割れて
    // いた**ことなので、「顔と着地が同じ1値（ImportedBook）から出る」ことはこの層でしか落とせない。

    @Test
    fun `importedBook - 手元の栞が章なら続きからの顔と章への着地が同じ1値から出る`() = runTest {
        seedImportedBook("chap_9.html")
        subscribe(viewModel.importedBook, viewModel.readingProgress)

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        val book = viewModel.importedBook.value
        assertNotNull("ncode 紐付け済みの蔵書が取込済みとして拾われること", book)
        assertEquals("b1", book!!.bookId)
        // 顔（主CTA が「アプリで続きから」になる条件）と着地（reading ルートへ渡すファイル）が一致する。
        assertTrue(book.hasBookmark)
        assertEquals("chap_9.html", book.startFile)
        // ⚠️ ここが報告された非対称〈アプリで8割読了・なろう未訪〉そのもの: なろうの栞が 0 でも顔は「続きから」。
        // 旧実装はこの 0 を見て「未読」の顔にしたまま 8割地点へ着地していた。
        assertEquals(0, viewModel.readingProgress.value)
    }

    @Test
    fun `importedBook - 進捗行が目次を指すなら未読の顔で目次へ着地する`() = runTest {
        // 進捗行はあるが章を開いていない（目次で離脱）状態。顔は「アプリで読む」・着地も目次で一致する。
        seedImportedBook("index.html")
        subscribe(viewModel.importedBook)

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        val book = viewModel.importedBook.value!!
        assertFalse(book.hasBookmark)
        assertEquals("index.html", book.startFile)
    }

    @Test
    fun `importedBook - 進捗行が無ければ未読の顔で目次へ着地する`() = runTest {
        seedImportedBook(null)
        subscribe(viewModel.importedBook)

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        val book = viewModel.importedBook.value!!
        assertFalse(book.hasBookmark)
        assertEquals("index.html", book.startFile)
    }

    @Test
    fun `importedBook - なろう側だけ進んでいても手元の顔と着地は冒頭のまま`() = runTest {
        // 逆向きの非対称〈なろうで第120話・アプリ未読〉。副アクションは第120話を出すが、主CTA の顔と
        // 着地は手元の本の冒頭（目次）で揃う＝なろうの位置が主CTA を汚さないことを固定する。
        seedImportedBook(null)
        fakeBookRepo.setWebReadingProgress(
            listOf(WebReadingProgressEntity(ncode = "N1234AB", lastReadEpisode = 120, lastReadAt = 0L))
        )
        subscribe(viewModel.importedBook, viewModel.readingProgress)

        viewModel.load(Ncode("N1234AB"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(120, viewModel.readingProgress.value)
        val book = viewModel.importedBook.value!!
        assertFalse(book.hasBookmark)
        assertEquals("index.html", book.startFile)
    }
}
