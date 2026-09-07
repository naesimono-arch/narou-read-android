package com.novelreader

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novelreader.ui.ReadingBackStack
import com.novelreader.ui.skins.k.KTab
import com.novelreader.ui.tabs.TabPagerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 読書フロー脱出（[popToTab] / [upFromReading]）の契約テスト。
 *
 * 固定する契約（階層 up への復帰 ADR 0047 × K タブ構造 2026-07-24）:
 *   ① 読書内スタックを使い切った後の脱出が「実際に有効な pop」であり、本棚入場ならタブ層＝本棚ページへ着地する。
 *   ② deep link 入場等で Pager が他タブに居ても、本棚入場の脱出は本棚ページへスナップする（pop だけだと
 *      「目次→さがす/設定」に化ける）。
 *   ③ バグ機序の固定: スタックに無いルート名への popBackStack は黙って無視される（false・現在地不動）
 *      ＝2026-07-25 実機バグ（旧 "bookshelf" への pop が黙殺され目次に幽閉）の再発防波堤。
 *      pop 先とルート登録は [TAB_HOST_ROUTE] の単一正本共有で乖離を封じる。
 *   ④ **入場元2種 × 操作2種の4通り**: 本棚から直行入場／作品詳細の「アプリで読む」から入場 の各々で、
 *      システム Back と左上 ← が同じ画面へ着地する（本棚入場→本棚・詳細入場→その詳細）。
 *      2026-09-04 まで終端は [popToTab] 固定で、詳細から入場しても本棚へ落ちていた＝これが是正点。
 *      ⚠️ 是正は [upFromReading]（親を入場元から引く）が担い、**戻り規則は階層 up のまま**
 *      ＝失効していたのは「読書の親＝本棚」という親の固定だけで階層モデル自体ではなかった（ADR 0047）。
 *      ∴ 直行入場でも脱出は「章→目次→脱出」の**2回**で、1回目は読書ルートに留まる（ADR 0046 は
 *      1発で出ていた＝2026-07-19 裁定「← も Back も必ず一つ上の階層へ」に対する退行だった）。
 *   ⑤ 内部スタックが残っている間は読書ルートを出ない（目次経由入場の1回目の Back は目次へ）。
 *
 * NavHost は MainActivity と同型の最小トポロジ（TAB_HOST_ROUTE 起点＋detail/reading が上に積まれる）を
 * 共有定数で組む＝プロダクションのルート名が変わればこのテストも同時に追従する。
 * 読書ルートの中身は MainActivity＋ReadingScreen の配線ミラー（Back も ← も同一の performBack を叩き、
 * [ReadingBackStack.back] が null＝現在地が目次のときだけ [upFromReading] へ抜ける）＝本物の終端実装を叩く
 * （[DiscoveryUpNavigationTest] と同じ手法。ViewModel を要求する ReadingScreen 実体は JVM で組めないため）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingEscapeNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState

    /**
     * MainActivity と同型の最小 NavHost（tabs 起点・detail/reading が上積み）。tabs 内は実物の TabPagerHost。
     * reading ルートの中身は読書画面の戻り配線ミラー: 内部スタック（[ReadingBackStack]）を持ち、
     * システム Back（BackHandler）と左上 ←（"UP" ボタン）が**同一の performBack** を叩く。
     */
    private fun buildNav(initialTabPage: Int) {
        composeTestRule.setContent {
            navController = rememberNavController()
            pager = rememberPagerState(initialPage = initialTabPage, pageCount = { 3 })
            NavHost(navController = navController, startDestination = TAB_HOST_ROUTE) {
                composable(TAB_HOST_ROUTE) {
                    TabPagerHost(
                        pagerState = pager,
                        pages = listOf({ Text("SHELF") }, { Text("DISC") }, { Text("SET") }),
                    )
                }
                composable("discovery/detail/{ncode}") { Text("DETAIL") }
                composable("reading/{bookId}/{startFile}") { entry ->
                    val startFile = entry.arguments?.getString("startFile") ?: ReadingBackStack.INDEX
                    // 本番は rememberSaveable（listSaver で経路を永続化）だが、その保存契約は
                    // ReadingBackStackTest が固定済み＝ここは遷移の着地だけを見るため素の remember で足りる。
                    var backStack by remember { mutableStateOf(ReadingBackStack.initial(startFile)) }
                    val performBack: () -> Unit = {
                        val popped = backStack.back()
                        if (popped != null) backStack = popped else upFromReading(navController, pager)
                    }
                    BackHandler(enabled = true, onBack = performBack)
                    Column {
                        // 現在地を可視化＝「読書ルートに留まったまま目次へ戻った」を判別する材料（契約⑤）。
                        Text("READING:${backStack.current}")
                        Text(text = "UP", modifier = Modifier.clickable(onClick = performBack))
                        // 目次から章を開く（onSelectChapterFromToc 相当）＝内部スタックを2段にする手段。
                        Text(
                            text = "OPEN",
                            modifier = Modifier.clickable { backStack = backStack.openChapter("c5.html") },
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** 既存契約（①②③）用: 本棚から目次で入場した状態まで進める。 */
    private fun setUpNav(initialTabPage: Int) {
        buildNav(initialTabPage)
        composeTestRule.runOnIdle { navController.navigate("reading/b1/index.html") }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? =
        composeTestRule.runOnIdle { navController.currentBackStackEntry?.destination?.route }

    /** システム Back（横スワイプ／端末ボタン）。読書ルートの BackHandler が受ける。 */
    private fun pressBack() {
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** 左上 ← ボタン。Back と同じ performBack を叩く配線（ミラー）。 */
    private fun clickUp() {
        composeTestRule.onNodeWithText("UP").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun escapeFromReading_popsToTabHost_onBookshelfPage() {
        // ① 通常入場（本棚ページから読書へ）: 脱出でタブ層へ戻り本棚ページのまま。
        setUpNav(initialTabPage = 0)
        assertEquals("reading/{bookId}/{startFile}", currentRoute())
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.BOOKSHELF) }
        composeTestRule.waitForIdle()
        assertEquals("脱出は有効な pop としてタブ層へ戻ること", TAB_HOST_ROUTE, currentRoute())
        assertEquals("着地は本棚ページ", 0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun escapeFromReading_snapsPagerToBookshelf_evenFromOtherTabPage() {
        // ② deep link 入場相当: Pager が設定タブに居ても契約は「目次→本棚」＝pop＋スナップで本棚着地。
        setUpNav(initialTabPage = 2)
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.BOOKSHELF) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("他タブに居ても本棚ページへスナップ", 0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun popToMissingRoute_isSilentlyIgnored_2026_07_25_bugMechanism() {
        // ③ 真因の機序を固定: スタックに無いルートへの pop は例外を出さず false で無視され、
        //    ユーザーは現在画面（目次）に幽閉される。だから pop 先は TAB_HOST_ROUTE 定数共有が必須。
        setUpNav(initialTabPage = 0)
        val popped = composeTestRule.runOnIdle { navController.popBackStack("bookshelf", false) }
        composeTestRule.waitForIdle()
        assertFalse("スタックに無いルートへの pop は黙って無視される（バグの機序）", popped)
        assertEquals("現在地が動かない＝幽閉の再現", "reading/{bookId}/{startFile}", currentRoute())
        // 対して正しい脱出は同じ状態から必ず成功する（①との対比で機序を1テスト内でも可視化）。
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.BOOKSHELF) }
        composeTestRule.waitForIdle()
        assertTrue("正規の脱出後はタブ層", currentRoute() == TAB_HOST_ROUTE)
    }

    // ────── ④ 入場元2種 × 操作2種の4通り（脱出先は upFromReading・戻り規則は階層 up）──────

    /** 本棚から続きを直行で開く（BookshelfScreen.onOpenBook 相当＝タブ層の上に reading を1枚）。 */
    private fun enterReadingFromBookshelf() {
        composeTestRule.runOnIdle { navController.navigate("reading/b1/c5.html") }
        composeTestRule.waitForIdle()
    }

    /** 作品詳細の「アプリで読む」から開く（onOpenImportedBook 相当＝detail の上に reading を1枚）。 */
    private fun enterReadingFromDetail() {
        composeTestRule.runOnIdle { navController.navigate("discovery/detail/n1234ab") }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { navController.navigate("reading/b1/c5.html") }
        composeTestRule.waitForIdle()
    }

    @Test
    fun back_fromBookshelfEntry_landsOnBookshelfTab() {
        // (1/4) 本棚直行入場 × システム Back → 「章→目次→本棚」の2段（階層 up）。
        buildNav(initialTabPage = 0)
        enterReadingFromBookshelf()
        pressBack() // 1回目＝章の一つ上＝目次（読書ルートに留まる）
        assertEquals("1回目は読書ルートを出ない", "reading/{bookId}/{startFile}", currentRoute())
        composeTestRule.onNodeWithText("READING:index.html").assertIsDisplayed()
        pressBack() // 2回目＝目次の一つ上＝読書フローの外＝入場元（本棚）
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("着地は本棚ページ", 0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun up_fromBookshelfEntry_landsOnBookshelfTab() {
        // (2/4) 本棚直行入場 × 左上 ← → Back と同じ2段・同じ本棚。操作で行き先が割れないことの半分。
        buildNav(initialTabPage = 0)
        enterReadingFromBookshelf()
        clickUp()
        assertEquals("← も1回目は目次まで（Back と同じ段数）", "reading/{bookId}/{startFile}", currentRoute())
        composeTestRule.onNodeWithText("READING:index.html").assertIsDisplayed()
        clickUp()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun back_fromDetailEntry_landsOnDetail_notBookshelf() {
        // (3/4) 作品詳細入場 × システム Back → 「章→目次→その詳細」。
        // 2026-09-04 まで終端は popToTab 固定で、ここが本棚に化けていた（＝ADR 0046 が挙げた実在のバグ）。
        // 直したのは終端の行き先（upFromReading）だけで、戻り規則は階層 up のまま＝2段は変わらない。
        buildNav(initialTabPage = 0)
        enterReadingFromDetail()
        pressBack()
        assertEquals("1回目は読書ルートを出ない", "reading/{bookId}/{startFile}", currentRoute())
        pressBack()
        assertEquals("詳細から入場したら詳細へ帰る", "discovery/detail/{ncode}", currentRoute())
    }

    @Test
    fun up_fromDetailEntry_landsOnDetail_notBookshelf() {
        // (4/4) 作品詳細入場 × 左上 ← → Back と同じ段数・同じ詳細。
        buildNav(initialTabPage = 0)
        enterReadingFromDetail()
        clickUp()
        assertEquals("1回目は読書ルートを出ない", "reading/{bookId}/{startFile}", currentRoute())
        clickUp()
        assertEquals("詳細から入場したら詳細へ帰る", "discovery/detail/{ncode}", currentRoute())
    }

    @Test
    fun back_fromTocEntry_staysInReadingUntilInnerStackIsEmpty() {
        // ⑤ 内部スタックが残る間は読書ルートを出ない: 目次入場→章を開くと1回目は目次へ上がるだけで、
        // 2回目に初めて終端（入場元＝本棚）へ抜ける。終端判定（upFromReading）が毎回走ってしまう
        // ＝「章の Back で本ごと出てしまう」退行の防波堤。← 側も同じ2段を辿る（階層 up・ADR 0047）。
        buildNav(initialTabPage = 0)
        composeTestRule.runOnIdle { navController.navigate("reading/b1/index.html") }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("OPEN").performClick() // 目次→章（[index, c5]）
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("READING:c5.html").assertIsDisplayed()

        pressBack() // 1回目＝章の一つ上＝目次（読書ルートに留まる）
        assertEquals("読書ルートを出ていないこと", "reading/{bookId}/{startFile}", currentRoute())
        composeTestRule.onNodeWithText("READING:index.html").assertIsDisplayed()

        clickUp() // 2回目は ← で＝目次の一つ上＝終端（本棚）。操作を混ぜても同じ列を辿ることの確認を兼ねる。
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
    }
}
