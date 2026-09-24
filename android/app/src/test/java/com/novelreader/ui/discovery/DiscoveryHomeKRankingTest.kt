package com.novelreader.ui.discovery

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.skins.k.RankingAnchorTestTag
import com.novelreader.ui.skins.k.rankingPageLayerTestTag
import com.novelreader.ui.skins.k.rankingPageTestTag
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * スキンK「さがす」ランキングの期間スワイプ（2026-07-29）の回帰テスト。
 *
 * 固定する契約:
 *  1) ランキング行の上での横スワイプで期間が進み（onSelectOrder 発火）、期間タブの選択表示が追従する
 *  2) 期間タブのタップでページが送られ、選択表示（＝ページャ現在地由来）が追従する＝ページが実際に動いた証明
 *  3) 端ページ（日間/新着）での余りスワイプは外側タブ Pager へ伝播しない（rankingEdgeSeal の封止・機構レベル）
 *  4) 送り確定後、order（VM）が追いつくまでの間も据わり位置に旧期間の行を描かない（2026-08-07 の退行）
 *  5) 期間タブ行は縦スクロールしても上端に貼り付いたまま残る（2026-08-07 裁定の候補A＝sticky 化）
 *
 * 外側タブ Pager は TabPagerHost と同型の最小ハーネス〈HorizontalPager の中央ページに発見ホーム〉で再現する
 * （実 TabPagerHost は MainActivity 配線・deferNeighborPages 等の無関係な足場を要求するため。封止の機構は
 *  「入れ子スクロールの余りが親 Pager の scrollable に届くか」だけで決まり、この同型で等価に検証できる）。
 * order は VM（homeOrder）の代役として test 側の MutableState で持ち、onSelectOrder で書き戻す。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeKRankingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val orderState = mutableStateOf(NarouOrder.WEEKLY)
    private var outerPager: PagerState? = null

    /** ランキング1行だけの Content（行ノード＝スワイプの起点。1件なら期間ページが縦に短くスクロール制御が楽）。 */
    private val contentState = DiscoveryUiState.Content(
        allcount = 1,
        novels = listOf(workSummary(title = "作品W", ncode = "N1")),
    )

    /** 月間期間ぶんの Content（期間ごとに別内容＝どの期間の行が描かれているかを文字列で見分ける）。 */
    private val monthlyContentState = DiscoveryUiState.Content(
        allcount = 1,
        novels = listOf(workSummary(title = "作品M", ncode = "N2")),
    )

    /**
     * 30件の Content（sticky 検証専用）。件数の根拠: 期間タブ行の**自然位置**を確実に画面外へ追い出せること
     * ＝「たまたま画面内に居るだけ」と「貼り付いている」を取り違えないため。
     */
    private val longContentState = DiscoveryUiState.Content(
        allcount = 30,
        novels = (1..30).map { workSummary(title = "作品$it", ncode = "N%03d".format(it)) },
    )

    /** VM(homeState) の代役。既定は週間の1件（既存3テストの前提を変えない）。 */
    private val uiState = mutableStateOf<DiscoveryUiState>(contentState)

    /**
     * onSelectOrder を受けて order を即時に書き戻すか。false＝**VM 往復の遅れ**の再現。
     * 実機の order は StateFlow(homeOrder) をまたいで戻ってくるため、settle した瞬間のフレームでは
     * まだ旧期間のままになる（この間に何が描かれるかが本テストの対象）。
     */
    private var writeBackOrder = true

    private fun setHost(
        initialOrder: NarouOrder = NarouOrder.WEEKLY,
        recordedOrders: MutableList<NarouOrder>? = null,
        // 期間タブ行の溢れ（選択タブ追従の検証）を作るためだけの拡大率。density は端末値のまま fontScale
        // だけ動かす＝ScreenshotTestSupport.captureThemed と同じ張り方（リソース修飾子に fontScale は無い）。
        fontScale: Float = 1.0f,
    ) {
        orderState.value = initialOrder
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSkin provides Skin.MEIKAI_K,
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                MaterialTheme {
                    // 外側タブ Pager の代役: 中央（page 1）が発見ホーム・両隣はダミー。封止が破れると
                    // 端ページでの余りスワイプが outer を動かし settledPage が 1 から外れる。
                    val outer = rememberPagerState(initialPage = 1, pageCount = { 3 })
                    outerPager = outer
                    HorizontalPager(state = outer) { page ->
                        when (page) {
                            1 -> DiscoveryHomeContent(
                                order = orderState.value,
                                state = uiState.value,
                                onBack = {},
                                onOpenDetail = {},
                                onOpenGenre = {},
                                onPickBiggenre = { _, _ -> },
                                onOpenSearch = {},
                                onPickMood = {},
                                // VM 代役: setHomeOrder と同じく order を書き戻す（同値は VM 側で no-op だが
                                // 発火回数の検証のため記録は全数残す）。
                                onSelectOrder = {
                                    recordedOrders?.add(it)
                                    if (writeBackOrder) orderState.value = it
                                },
                                onRefresh = {},
                            )
                            else -> Text(if (page == 0) "外側ダミー左" else "外側ダミー右")
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * 画面本体の縦 LazyColumn を特定して合成域を送る。hasScrollToNodeAction だけだと外側 Pager・
     * 気分/期間の横ページャも一致するため、縦スクロール軸を持つ唯一のノードで絞る（誤って外側 Pager を
     * スクロールさせると検証対象のタブ位置ごと壊れる）。
     */
    private fun scrollListTo(text: String) {
        composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).performScrollToNode(hasText(text))
    }

    /** ランキング行上のスワイプ。durationMillis=50 の理由は DiscoveryHomeKMoodTest と同じ（確実にフリング閾値超え）。 */
    private fun swipeOnRankingRow(toLeft: Boolean) {
        composeTestRule.onNodeWithText("作品W").performTouchInput {
            if (toLeft) swipeLeft(durationMillis = 50) else swipeRight(durationMillis = 50)
        }
        composeTestRule.waitForIdle()
    }

    /**
     * [order] の「面」（その期間のページ1枚＝可視行ぶん）の節点。0件＝その期間は今どこにも合成されていない。
     * 面は行スロットごとに1つ置かれるので、可視行が複数あれば複数返る。
     */
    private fun pageLayers(order: NarouOrder) =
        composeTestRule.onAllNodes(hasTestTag(rankingPageLayerTestTag(order)))

    /**
     * **表示されていない**ことの観測（2026-09-02・覗きの常駐化に伴う観測点の強化）。
     *
     * 従来は「そのノードが合成されていない（＝ツリーに居ない）」で見ていたが、これは
     * 〈画面に出ていない面は合成もされていない〉という**合成戦略への依存**であり、覗きを常駐させた時点で
     * 前提が崩れる。さらに悪いことに、常駐する面は `clearAndSetSemantics` で子孫の意味を落とすので、
     * 文字で数える検査は**面が誤って据わり位置へ出ても 0 件のまま緑**になる＝退行を素通しする。
     * ⇒ 節点が在るかではなく、在る節点が画面に出ているかで見る。
     *
     * 0 件のときに素通しするのは意図どおり（＝自明に表示されていない）。「出ているべきもの」の側は
     * 各テストが [assertIsDisplayed] で別途名指しするので、この非対称は空振りを生まない。
     */
    private fun assertNotDisplayed(matcher: SemanticsMatcher, why: String) {
        val nodes = composeTestRule.onAllNodes(matcher)
        repeat(nodes.fetchSemanticsNodes().size) { i ->
            try {
                nodes[i].assertIsNotDisplayed()
            } catch (e: AssertionError) {
                // 握り潰しではなく文脈の付与（どの不変条件が破れたかを添えて投げ直す）。
                throw AssertionError("$why（${i + 1}件目が画面に出ている）", e)
            }
        }
    }

    /**
     * A（覗き行の新規合成）の真因対処の回帰テスト（2026-09-02）。
     *
     * 固定する契約: **隣の期間の面は、指が動き出す前から合成されている**。
     *
     * なぜ「合成済みであること」を契約にするか: 旧実装は覗きの面をドラッグ中だけ合成していた
     *（`isScrollInProgress` が false の間は隣期間 = null）。すると指が動き出した最初のフレームで
     * null→非null に変わり、**可視行ぶんの覗き行が丸ごと新規合成**される——実測（エミュ・可視6行）で
     * StaticLayout 42個・measure 25〜40ms の重いフレームが1フリックに必ず1枚出ていた
     *（`docs/knowledge/ranking-pager-jank-slow-ui-thread.md` の A）。合成の総量ではなく
     * **合成がドラッグの外に居ること**が対処の本体なので、契約もそこに置く。
     *
     * ⚠️ この観測点は時間（ms）を測らない。エミュ/CI の絶対値は走ごとに揺れて成果の根拠にならないため、
     * 「重いフレームが減った」ではなく「重い仕事が指の動き出しに紐付いていない」を構造で押さえる。
     */
    @Test
    fun `隣の期間の面は指が動き出す前から常駐している`() {
        setHost(NarouOrder.WEEKLY)
        scrollListTo("作品W")

        listOf(NarouOrder.DAILY, NarouOrder.MONTHLY).forEach { neighbor ->
            assertTrue(
                "据わっている間に隣期間（${neighbor.name}）の面が合成されていない" +
                    "＝指が動き出したフレームで覗きが新規合成される（ジャンク要因 A）",
                pageLayers(neighbor).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    /**
     * 上の常駐化が**見た目を1px も変えていない**ことの対の契約（片方だけ強めると必ずもう片方が壊れる関係）。
     *
     * 固定する契約: **据わっていない期間の面は、合成されていても画面には出ない**。
     * 覗きの面は自分のページ番号の座席（`translationX`）へ置かれ、行スロットの `clipToBounds` で切られる
     * ＝静止時は溝ごと枠の外に居る。ここが崩れると「隣の期間が据わり位置に重なって見える」退行になる。
     */
    @Test
    fun `常駐している隣の期間の面は据わっている間は表示されない`() {
        setHost(NarouOrder.WEEKLY)
        scrollListTo("作品W")

        // 前提: 据わりの面は出ている（出ていない状態で下の検査が通っても意味が無い）。
        pageLayers(NarouOrder.WEEKLY)[0].assertIsDisplayed()
        listOf(NarouOrder.DAILY, NarouOrder.MONTHLY).forEach { neighbor ->
            assertNotDisplayed(
                hasTestTag(rankingPageLayerTestTag(neighbor)),
                "据わっていない期間（${neighbor.name}）の面が画面に出ている",
            )
        }
    }

    @Test
    fun `ランキング行の横スワイプで次の期間へ進みタブ選択が追従する`() {
        val recorded = mutableListOf<NarouOrder>()
        setHost(NarouOrder.WEEKLY, recorded)
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = true)
        // settledPage 経由でちょうど1回だけ発火（初回発行 drop や order 書き戻しの往復で二重発火しない）。
        assertEquals(listOf(NarouOrder.MONTHLY), recorded)
        scrollListTo("月間")
        composeTestRule.onNodeWithText("月間").assertIsSelected()
        composeTestRule.onNodeWithText("週間").assertIsNotSelected()
    }

    @Test
    fun `期間タブのタップでページが送られ選択が追従する`() {
        val recorded = mutableListOf<NarouOrder>()
        setHost(NarouOrder.WEEKLY, recorded)
        scrollListTo("累計")
        composeTestRule.onNodeWithText("累計").performClick()
        composeTestRule.waitForIdle()
        // タップ1回ぶんだけ発火（ページ追従アニメの settle が同値でもう一度 VM を叩かない）。
        assertEquals(listOf(NarouOrder.TOTAL), recorded)
        // 選択表示はページャ現在地由来＝これが選択済みになる＝ページが実際に「累計」まで動いた証明。
        composeTestRule.onNodeWithText("累計").assertIsSelected()
        composeTestRule.onNodeWithText("週間").assertIsNotSelected()
    }

    @Test
    fun `端ページでの余りスワイプは外側タブPagerへ伝播しない`() {
        setHost(NarouOrder.NEW) // 右端ページ（新着）から開始
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = true) // 右端でさらに左スワイプ＝余りが全量発生
        assertEquals("右端での左スワイプが外側タブを動かした", 1, outerPager!!.settledPage)

        // 左端（日間）へ移して逆向きも封止されることを確認（order 書換→LaunchedEffect がページを追従）。
        orderState.value = NarouOrder.DAILY
        composeTestRule.waitForIdle()
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = false) // 左端でさらに右スワイプ
        assertEquals("左端での右スワイプが外側タブを動かした", 1, outerPager!!.settledPage)
        // ダミーページが合成されていない＝外側 Pager が微動もしていない傍証。
        composeTestRule.onNodeWithText("外側ダミー左").assertDoesNotExist()
        composeTestRule.onNodeWithText("外側ダミー右").assertDoesNotExist()
    }

    /**
     * 2026-08-07 ユーザー報告「ランキングのスワイプの後、一瞬スワイプ前の文字列が出てくる」の回帰テスト。
     *
     * 固定する契約: **据わる位置に描かれる行の期間は、VM の order でなくページャが指しているページで決まる**。
     * スワイプ確定から order が戻ってくるまでには VM 往復（settledPage→onSelectOrder→homeOrder StateFlow）の
     * 遅れが必ずあり、その間ページャは既に次期間を指している。ここで旧期間の行を据わり位置に描くと、
     * 追従した瞬間に文字だけが入れ替わる＝報告どおりの一瞬のちらつきになる。
     *
     * 遅れは [writeBackOrder]=false で再現する（実機では非同期な往復＝テストからは「まだ返ってきていない」
     * 状態そのもの）。期間ごとに別内容を控えさせてあるので、描かれている行の期間は文字列で見分けられる。
     */
    @Test
    fun `スワイプ確定後にorderが追従するまでの間も旧期間の行を据わり位置に描かない`() {
        // 月間を一度表示して控え（期間別 stale-while-revalidate）に「作品M」を積む。
        uiState.value = monthlyContentState
        setHost(NarouOrder.MONTHLY)
        // 週間へ戻す（order と state は同じ snapshot で入れ替わる＝実 VM の setHomeOrder→loadHome と同型）。
        composeTestRule.runOnIdle {
            orderState.value = NarouOrder.WEEKLY
            uiState.value = contentState
        }
        composeTestRule.waitForIdle()

        // ここから order は戻ってこない＝スワイプ確定直後のフレームに相当する状態で止める。
        writeBackOrder = false
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = true)

        // 観測点は〈合成されていない〉でなく〈**表示されていない**〉（2026-09-02・[assertNotDisplayed] の KDoc）。
        // 覗きの常駐後、旧期間（週間）の面は控えの行を持ったままツリーに居るのが正常。しかもその面は
        // 子孫の意味を落としているので、文字の有無で見ると何が壊れても緑になる＝面の位置で名指しする。
        assertNotDisplayed(hasText("作品W"), "旧期間（週間）の行が据わり位置に見えている＝報告された症状")
        assertNotDisplayed(hasTestTag(rankingPageLayerTestTag(NarouOrder.WEEKLY)), "旧期間（週間）の面が画面に出ている")
        composeTestRule.onNodeWithText("作品M").assertIsDisplayed()

        // order が追従しても内容は動かない（＝上で描いていたものが正であり、差し替えの瞬間が無い）。
        composeTestRule.runOnIdle {
            orderState.value = NarouOrder.MONTHLY
            uiState.value = monthlyContentState
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("作品M").assertIsDisplayed()
        assertNotDisplayed(hasText("作品W"), "order 追従後に旧期間（週間）の行が見えている")
        assertNotDisplayed(hasTestTag(rankingPageLayerTestTag(NarouOrder.WEEKLY)), "order 追従後に旧期間の面が画面に出ている")
    }

    /** ランキング行の左端 x（据わり位置の観測点）。行スロットの `graphicsLayer{translationX}` を含んだ実位置。 */
    private fun rankingRowLeft(text: String): Double =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left.toDouble()

    /**
     * 2026-08-14 ユーザー報告「ランキングを横スワイプすると半分次のランキングが表示されて、
     * もう一度スワイプしないと半分が埋まらない」の回帰テスト。
     *
     * 固定する契約: **横スワイプが静止したら、据わった期間の行は据わり位置（x＝送る前と同じ）に居る**。
     * 送りが半ページを越えた瞬間に `PagerState.currentPage` が次ページへ切り替わる（＝画面の [pagerOrder] が
     * 変わる）ので、行スロットの identity が期間に依存していると**指を受けているスロットごと破棄され**、
     * `scrollable` が抱えていたドラッグ／スナップの coroutine が道連れに消える。ページャは
     * `currentPageOffsetFraction` が非0のまま取り残され、誰も据え直さない（アンカーは
     * `userScrollEnabled=false`・`LaunchedEffect(order)` も currentPage 一致で何もしない）＝半ページずれて静止する。
     *
     * 観測点を x にするのは、**タブ・onSelectOrder・settledPage はどれも正しい値になってしまう**から
     *（scroll session が死ぬと isScrollInProgress は false＝settledPage は次ページを名乗る）。
     * 症状は位置にしか出ないので、位置で見る。
     */
    @Test
    fun `横スワイプで期間を送った後に行が据わり位置へ収まる`() {
        setHost(NarouOrder.WEEKLY)
        scrollListTo("作品W")
        val seatLeft = rankingRowLeft("作品W")

        swipeOnRankingRow(toLeft = true)

        // 送り先（月間）に控えは無いが、order 書き戻しで state(Content) が当たり同じ行が据わる。
        scrollListTo("作品W")
        assertEquals(
            "期間送りの後もランキング行が据わり位置へ戻っていない（＝ページャが半ページずれたまま静止）",
            seatLeft,
            rankingRowLeft("作品W"),
            0.5,
        )
    }

    /**
     * 同上の契約を**一覧の途中の行**で固定する。1行目だけで見ていると「たまたま先頭スロットが生き残った」
     * ケースを通してしまうため、30行ぶんの item がある中で下の方の行から送っても据わることまで見る
     *（実機は常に30行＝こちらが実運用の形）。送り先の期間は控えが無いので骨30行＝スロット数は同数で、
     * 中身の種類（Rows→Skeleton）だけが入れ替わる経路も同時に通る。
     */
    @Test
    fun `一覧途中の行から横スワイプしても据わり位置へ収まる`() {
        uiState.value = longContentState
        setHost(NarouOrder.WEEKLY)
        scrollListTo("作品15")
        val seatLeft = rankingRowLeft("作品15")

        composeTestRule.onNodeWithText("作品15").performTouchInput { swipeLeft(durationMillis = 50) }
        composeTestRule.waitForIdle()

        scrollListTo("作品15")
        assertEquals(
            "一覧途中の行から送ると据わり位置へ戻らない",
            seatLeft,
            rankingRowLeft("作品15"),
            0.5,
        )
    }

    /** 期間タブ行の上端 y（貼り付き検証の観測点）。行を代表させるのは選択タブの文字ノード。 */
    private fun tabRowTop(): Double =
        composeTestRule.onNodeWithText("週間").fetchSemanticsNode().boundsInRoot.top.toDouble()

    /**
     * 2026-08-07 ユーザー裁定（候補A＝モック discovery-K-period-sticky-A.html）の回帰テスト。
     *
     * 固定する契約: **期間タブ行はランキングを縦に読み進めても上端に貼り付いたまま残る**。
     * 裁定の狙いは「スクロール中も現在地（どの期間か）と隣の選択肢が見え、直接タップで切り替えられる」ことなので、
     * 「見えている」だけでなく「動かない（＝貼り付いている）」まで見る。
     *
     * 3点で名指しする:
     *  ・スクロール量の違う2地点でタブ行の上端 y が一致する＝流れずに貼り付いている
     *  ・その状態で可視である（sticky でなければ自然位置はとうに画角外）
     *  ・直上の見出し「ランキング」は流れ去っている＝タブ行の自然位置を確かに追い越した後の観測である
     *   （これが無いと「まだタブまで到達していないだけ」でも通ってしまう）
     */
    @Test
    fun `期間タブ行は縦スクロールしても上端に貼り付いたまま残る`() {
        uiState.value = longContentState
        setHost(NarouOrder.WEEKLY)

        scrollListTo("作品15")
        val topAtMiddle = tabRowTop()
        scrollListTo("作品30")
        val topAtBottom = tabRowTop()

        assertEquals("スクロールで期間タブ行の上端が動いた＝貼り付いていない", topAtMiddle, topAtBottom, 0.5)
        composeTestRule.onNodeWithText("週間").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("ランキング").assertCountEquals(0)
    }

    /**
     * 2026-08-14 ユーザー裁定「A 案（期間タブ行ごと pinned）のまま穴を塞ぐ」＝ADR 0033 の回帰テスト。
     *
     * 固定する契約: **タブ行が画面幅に収まらないときでも、選択中の期間タブは可視域に入る**。
     * 貼り付いていても行内で画面外へ出てしまえば「現在地が常に見える」という A 案の約束は破れる。
     * しかも期間は行の横スワイプでも変わる＝ユーザーはタブに触れずに現在地を動かすので、見えなくなったこと
     * に気付く手掛かりが無い（＝塞ぐべき穴）。
     *
     * 溢れは 幅 360dp（実機の溢れ条件と同じ）×fontScale 2.0（golden で使っている最大フォント＝新しい条件を
     * 作らない）で起こす。
     *
     * ⚠️ **[GraphicsMode] NATIVE が必須**（2026-08-14・これを欠いて最初の実装が赤になった）。既定の LEGACY は
     * 文字の実測をせず、Compose の計測経路 `ShadowPaint.nGetRunAdvance` が**文字数をそのまま px として返す**
     *（TextLayoutMode=REALISTIC 時は `end - start`・それ以外は 0）。つまりタブ6本の文字は全部で 13px にしかならず、
     * fontScale をいくら上げても幅は1px も動かない＝**溢れが原理的に起こらない**。NATIVE では
     * nativeruntime 同梱の実フォント（CJK は DroidSansFallback）で測るので、13文字×13sp×2.0＝約 338dp、
     * 溝 5×16dp＝80dp、合計 約 418dp が可視域 312dp（360dp −左右 S24）に対して溢れる。
     * 溢れていること自体は冒頭の前提アサートで名指しする（溢れなければ空振りではなく赤にする）。
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun `溢れる幅でも選択中の期間タブは可視域へ追従する`() {
        setHost(NarouOrder.DAILY, fontScale = 2.0f)
        scrollListTo("作品W") // 期間タブ行を上端へ貼り付かせてから見る（自然位置のままだと画角外）
        composeTestRule.onNodeWithText("新着")
            .assertIsNotDisplayed() // 前提: 右端の「新着」は日間を選んでいる間タブ行の可視域の外に居る

        composeTestRule.runOnIdle { orderState.value = NarouOrder.NEW }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("新着").assertIsSelected()
        composeTestRule.onNodeWithText("新着").assertIsDisplayed()
    }

    /**
     * 2026-08-19 の修正（アンカーが行の左右余白を受けず送り量と覗きがずれる）の回帰テスト。
     *
     * 固定する契約: **状態供給アンカーが measure される幅＝行の実幅**。
     * `PagerState` の1ページぶんの送り量は `layoutInfo.pageSize`（＝アンカーが measure された幅）から
     * 決まるので、アンカーだけが左右マージンを受けずに画面幅で measure されると、〈1ページ＝画面幅〉と
     * 〈行の実幅＝画面幅−2×S24〉が食い違い、1ページ送るのに指を行幅より広く動かす羽目になる
     * ＝覗きの溝も設計値からずれる。
     *
     * 覗きの溝を測るのでなく**幅そのもの**を突き合わせるのは、溝のずれが原因（ページ幅）の二次症状に
     * すぎないため。ここが一致していれば送り量と覗きは定義から従う。
     *
     * ⚠️ [GraphicsMode] NATIVE を明示する。この試験は寸法を主張するので、既定の LEGACY
     *（文字幅＝文字数の代用計量）で通っても検出力の保証にならない
     *（`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
     * 幅 360dp を固定するのは、余白（2×24dp）が幅全体に対して十分大きく、取り違えが起きない画角を選ぶため。
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun `アンカーのページ幅はランキング行の実幅と一致する`() {
        setHost(NarouOrder.WEEKLY)
        scrollListTo("作品W")

        val anchorWidth = composeTestRule.onAllNodesWithTag(RankingAnchorTestTag)[0]
            .getUnclippedBoundsInRoot().width
        val rowWidth = composeTestRule.onAllNodesWithTag(rankingPageTestTag(NarouOrder.WEEKLY))[0]
            .getUnclippedBoundsInRoot().width

        // 前提: 行は画面いっぱいではない（左右マージンを受けている）。これが崩れたら比較自体が無意味なので
        // 空振りでなく赤で気付く。
        assertEquals("行が画面幅そのままになっている＝縦リストの左右マージンが消えた", 312f, rowWidth.value, 0.5f)
        assertEquals(
            "アンカーの measure 幅が行の実幅と違う＝ページ送り量と覗きの溝がその差ぶんずれる",
            rowWidth.value,
            anchorWidth.value,
            0.5f,
        )
    }
}
