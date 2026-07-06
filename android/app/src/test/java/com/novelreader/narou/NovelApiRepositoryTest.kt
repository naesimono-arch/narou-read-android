package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.network.NarouApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NovelApiRepositoryTest {

    private lateinit var service: NarouApiService
    private lateinit var repository: NovelApiRepository
    private var currentTime = 1000L

    @Before
    fun setUp() {
        service = mockk(relaxed = true)
        repository = NovelApiRepository(service = service, timeSource = { currentTime })
    }

    private fun createMockResponse(): List<NarouNovel> {
        return listOf(
            NarouNovel(allcount = 100),
            NarouNovel(title = "作品1", ncode = "N1111A", novelType = 1, end = 1),
            NarouNovel(title = "作品2", ncode = "N2222A", novelType = 2, end = 0)
        )
    }

    @Test
    fun `weeklyRanking - レスポンスの先頭要素から allcount を分離し、残りを novels リストとして返すこと`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        val result = repository.weeklyRanking()

        assertEquals(100, result.allcount)
        assertEquals(2, result.novels.size)
        assertEquals("作品1", result.novels[0].title)
        assertEquals("作品2", result.novels[1].title)
    }

    @Test
    fun `weeklyRanking - キャッシュ有効期間内であれば API は 1 回しか呼ばれず、キャッシュから値が返されること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        // 1回目の取得（キャッシュ無し、API呼ぶ）
        val result1 = repository.weeklyRanking()
        // 2回目の取得（キャッシュヒット、API呼ばない）
        val result2 = repository.weeklyRanking()

        assertEquals(result1, result2)
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any()) }
    }

    @Test
    fun `weeklyRanking - キャッシュ有効期限 TTL (6時間) 経過後は API が再呼び出しされ、新しいデータが取得されること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        // 1回目の取得
        repository.weeklyRanking()

        // 時間を 6時間 (21600000 ミリ秒) 進める
        currentTime += 6 * 60 * 60 * 1000L

        // 2回目の取得（キャッシュ有効期限切れ、API呼ぶ）
        repository.weeklyRanking()

        coVerify(exactly = 2) { service.search(of = any(), order = any(), lim = any()) }
    }

    @Test
    fun `weeklyRanking - API が IOException を投げた場合、ユーザーメッセージを保持した NarouApiException に変換されて再スローされること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws IOException("No route to host")

        // assertThrows は suspend 関数を直接受けられず、runTest を入れ子にすると IllegalStateException になるため、
        // 外側の runTest スコープ内で直接呼び出して捕捉する。
        var thrown: NarouApiException? = null
        try {
            repository.weeklyRanking()
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull("IOException は NarouApiException に正規化されて throw される", thrown)
        assertEquals("ネットワークに接続できません。通信環境を確認して再試行してください。", thrown!!.userMessage)
        assertEquals("No route to host", thrown.cause?.message)
    }
}
