package com.novelreader.ui.skins.k

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 横向き構造（ADR 0034 ＝ **T1 横一列化 × NavigationRail R1 × FAB は Rail 上端**）の Compose 翻訳を、
 * 実寸で固定する。
 *
 * ## このテストが押さえる3点
 * 1. **翻訳前の真値**（Rail 未結線＝従来の縦積み）が [FIXED_TOP_BEFORE] / [VIEWPORT_BEFORE] であること。
 *    ここを一緒に測るのは、後述の「改善したことになっている」事故を潰すため——T1 後の値だけを焼くと、
 *    比較対象が**人の記憶**になり、前提が腐っても赤くならない。両方を1つのテストが持てば差分が自明になる。
 * 2. **T1 後の固定トップ**が横1行へ畳まれ、実効ビューポートがカード実高（横向き5列で ~188dp）を超えること。
 * 3. **超過の吸い先が E（期間タブの既存 `horizontalScroll`）**であること＝fontScale 1.0/1.15 では
 *    期間タブ6本が全部見え、検索プレースホルダは省略されない（＝情報が減る壊れ方をしていない）。
 *
 * ## ⚠️ [GraphicsMode] NATIVE 必須
 * 測るものが「題字 titleLarge の行高」「プレースホルダ16文字の実幅」＝**実フォントのメトリクス**だから。
 * 既定の LEGACY は文字幅を字数で代用するので、同じコードで 105.5dp（LEGACY）／120.5dp（NATIVE）と
 * 15dp も食い違い、しかも**偽の値がモック導出値 108dp と符合して「実装は前提どおり」と読めてしまった**
 *（機序＝`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 * 付け忘れを実行時に捕まえる裏取りが [assertNativeGraphics]。
 *
 * ## 写せないもの
 * Robolectric の window insets は全て 0（`@Config(qualifiers)` にインセット修飾子が無い）。
 * ステータスバー／ジェスチャーバーはこの実測に含まれない＝実効ビューポートは実機より 1 段広い。
 * 推測値を流し込むと「推測を回帰の基準線へ焼き込む」ことになるので既存流儀どおり含めない
 *（[BookshelfKLandscapeScreenshotTest] と同じ限界）。横向きの最終判断は実機に残る。
 */
@RunWith(RobolectricTestRunner::class)
// 横向き（w>h と land を両方明示する理由は BookshelfKLandscapeScreenshotTest と同じ）。
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KLandscapeRailT1Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rankingContent = DiscoveryUiState.Content(
        allcount = 3,
        novels = (1..3).map { workSummary(title = "作品$it", ncode = "N%04dAA".format(it)) },
    )

    // ── 1. 翻訳前の真値（Rail 未結線＝従来の縦積みがそのまま出ること）────────────────────────

    @Test
    fun `Rail未結線の横向きは従来のまま＝固定トップ 120_5dp・実効ビューポート 174_5dp`() {
        assertNativeGraphics()
        // LocalKTabSelect を provide しない＝結線が来ていない状態。ここで従来の姿が出ることが、
        // 「取り込んだ瞬間に横向きが壊れる」ことが無いという保証そのもの。
        setDiscovery(fontScale = 1.0f, railWired = false)

        val (fixedTop, viewport) = measureDiscoveryFixedTop()
        println("[横向き実測 翻訳前] fixedTop=$fixedTop viewport=$viewport（Rail 未結線・fontScale=1.0）")
        assertEquals("翻訳前の固定トップが動いた（この値は ADR 0034 注記の真値）", FIXED_TOP_BEFORE, fixedTop)
        assertEquals("翻訳前の実効ビューポートが動いた", VIEWPORT_BEFORE, viewport)
        // 帯（KBottomNav）が出ている＝Rail 化していないこと自体の裏取り。
        composeTestRule.onNodeWithText("設定").assertIsDisplayed()
    }

    // ── 2. T1 後の固定トップ ────────────────────────────────────────────────

    @Test
    fun `T1で固定トップが横1行へ畳まれる（fontScale 1_0）`() {
        assertNativeGraphics()
        setDiscovery(fontScale = 1.0f, railWired = true)

        val (fixedTop, viewport) = measureDiscoveryFixedTop()
        println("[横向き実測 T1] fixedTop=$fixedTop viewport=$viewport（fontScale=1.0）")
        assertEquals("T1 の固定トップ（検索欄 52dp ＋ 上 S4）が動いた", FIXED_TOP_T1, fixedTop)
        assertEquals("T1 の実効ビューポートが動いた", VIEWPORT_T1, viewport)
        // 稼いだぶん。ここが 0 に近づいたら T1 が効いていない＝畳めていない。
        assertTrue(
            "T1 が固定トップを畳めていない: before=$FIXED_TOP_BEFORE after=$fixedTop",
            fixedTop <= FIXED_TOP_BEFORE - 40.dp,
        )
    }

    @Test
    fun `T1は fontScale 1_15 でも横1行に収まる`() {
        assertNativeGraphics()
        setDiscovery(fontScale = 1.15f, railWired = true)

        val (fixedTop, viewport) = measureDiscoveryFixedTop()
        println("[横向き実測 T1] fixedTop=$fixedTop viewport=$viewport（fontScale=1.15）")
        // 1.15 でも 1.0 と同じ 1 行（検索欄は固定高 52dp・題字は Rail へ抜けている）＝縦は太らない。
        assertEquals("fontScale 1.15 で固定トップが太った", FIXED_TOP_T1, fixedTop)
        assertT1RowFitsHorizontally(fontScale = 1.15f)
    }

    // ── 3. 超過の吸い先＝E（期間タブの既存 horizontalScroll）──────────────────────────

    @Test
    fun `T1の横1行は fontScale 1_0 で期間タブ6本まで収まる（超過はEが吸う設計）`() {
        assertNativeGraphics()
        setDiscovery(fontScale = 1.0f, railWired = true)
        assertT1RowFitsHorizontally(fontScale = 1.0f)
    }

    /**
     * 横1行が**溢れていない**ことの検査。
     *
     * 見るのは2点だけ:
     *  ・末尾の期間タブ「新着」の右端が画面内に居る（`horizontalScroll` は溢れても
     *    `getUnclippedBoundsInRoot` には画面外の座標が出るので、これが溢れの検出になる）
     *  ・検索プレースホルダが省略されていない（＝検索欄が要求幅を貰えている）
     *
     * 溢れたときに**どちらが壊れるか**が設計の核心なので、両方を同時に見る。E に吸わせる形（検索欄＝
     * 中身なり／期間タブ＝残り幅）なら、溢れは常に期間タブの横スクロールとして現れ、文言は削れない。
     */
    private fun assertT1RowFitsHorizontally(fontScale: Float) {
        val rootRight = composeTestRule.onRoot().getUnclippedBoundsInRoot().right
        val lastTab = composeTestRule.onNode(
            hasText(NarouOrder.NEW.uiLabel) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val searchField = composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).getUnclippedBoundsInRoot()
        val fieldWidth = searchField.right - searchField.left
        // 行が使える幅＝画面幅 − Rail 80dp − 左右マージン S24×2。検索欄は非 weight＝Row はまずこの幅を上限に
        // measure するので、**要求幅がこの上限に達したときだけ**クランプされてプレースホルダが省略される。
        // ⇒ `fieldWidth < rowAvailable` が「省略されていない」の必要十分条件（省略の有無を直接見るより頑健）。
        val rowAvailable = rootRight - KNavigationRailWidth - ROW_SIDE_MARGIN * 2
        println(
            "[T1横1行 収まり] fontScale=$fontScale 検索欄=${searchField.left}..${searchField.right}(幅 $fieldWidth) " +
                "末尾タブ右端=${lastTab.right} 行の使える幅=$rowAvailable 画面右端=$rootRight",
        )
        assertTrue(
            "期間タブが横1行から溢れた（E が吸っている＝収まっていない）: 新着右端=${lastTab.right}",
            lastTab.right <= rootRight - ROW_SIDE_MARGIN,
        )
        assertTrue(
            "検索欄が行いっぱいに引き伸ばされた＝プレースホルダが省略される側に倒れている: 幅=$fieldWidth 上限=$rowAvailable",
            fieldWidth < rowAvailable,
        )
    }

    // ── 本棚側（題字と冊数の Rail 移設・FAB の Rail 上端・下端 96dp の撤去）─────────────────

    @Test
    fun `本棚の横向きは題字と冊数が Rail へ移り FAB が Rail 上端の円形になる`() {
        assertNativeGraphics()
        setKGridView(true)
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            WithRail(railWired = true) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        BookshelfK(
                            data = KShelfFixtures.mixedData(),
                            chrome = KShelfFixtures.chrome(KShelfFixtures.mixedStatusCounts),
                            actions = KShelfFixtures.actions,
                            selection = KShelfFixtures.selection,
                            webActions = KShelfFixtures.webActions,
                            snackbarHostState = remember { SnackbarHostState() },
                        )
                    }
                    KBottomNav(current = KTab.BOOKSHELF, onSelect = {})
                }
            }
        }

        val rootTop = composeTestRule.onRoot().getUnclippedBoundsInRoot().top
        // 「本棚」は Rail ヘッダとタブラベルの2箇所に出る（ADR 0034 が引き受けた二重化）ので、
        // 冊数「4冊」＝Rail ヘッダにしか無い文字で移設先を特定する。
        // 冊数は「N冊」＝この画面で「冊」を含む唯一の文字（N は蔵書＋Web の合算なので数を書かない）。
        val railMeta = composeTestRule.onNodeWithText("冊", substring = true).getUnclippedBoundsInRoot()
        assertTrue(
            "冊数が Rail（左端 ${KNavigationRailWidth}）の外に居る＝移設できていない: left=${railMeta.left}",
            railMeta.right <= KNavigationRailWidth,
        )

        // グリッド上端＝固定トップの実高。T1 では〈状態チップ行＋表示切替〉の1行だけが残る。
        val grid = composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).getUnclippedBoundsInRoot()
        val fixedTop = grid.top - rootTop
        println("[横向き実測 T1 本棚] fixedTop=$fixedTop viewport=${grid.bottom - grid.top}")
        assertEquals("本棚 T1 の固定トップ（チップ行と表示切替の高い方）が動いた", FIXED_TOP_T1_SHELF, fixedTop)

        // FAB は Rail 上端の円形（52dp）＝本文の右下ではない。読み上げ名は拡張FABと同一に保つ。
        val fab = composeTestRule.onNodeWithContentDescription("PDFを追加").getUnclippedBoundsInRoot()
        assertTrue(
            "FAB が Rail の外に居る（右下据え置きのまま）: left=${fab.left}",
            fab.right <= KNavigationRailWidth,
        )
        assertEquals("Rail FAB がモック .rfab の 52dp でない", 52.dp, fab.right - fab.left)
    }

    // ── 設定タブを含めた3面のナビ形の一致（横向きの「跳ね」の消滅）──────────────────

    /**
     * 横向きでは**3タブとも帯（[KBottomNav]）が出ない**＝タブを移っても本文の高さが変わらないこと。
     *
     * なぜこれを固定するか（真因）: 帯の出し分けを「Rail を立てる面だけ隠す」形にしていた当初案では、
     * さがす⇄設定 のスワイプ中点で `currentPage` が切り替わった瞬間に帯が出入りし、
     * `Column{ Pager(weight(1f)); KBottomNav }` の本文高が 64dp ぶん跳ねる（Pager の measure ごと動く）。
     * ＝**意匠の適用範囲（T1 は本棚とさがすだけ）と、ナビをどの軸へ置くかという構造は別物**で、
     * 後者は3面で揃っていなければならない。ここが崩れると跳ねが復活する。
     *
     * 3面を1回のコンポーズで測るために横に並べる（`setContent` は1テスト1回しか呼べない）。
     * 各枠は縦フル＝帯が出れば**その枠の本文高だけが縮む**ので、3値の一致がそのまま「跳ねない」の証明になる。
     */
    @Test
    fun `横向きは3タブとも帯が出ない＝タブ間で本文の高さが跳ねない`() {
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            WithRail(railWired = true) {
                Row(Modifier.fillMaxSize()) {
                    KTab.entries.forEach { tab ->
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1f).fillMaxWidth().testTag(pageTag(tab)))
                            KBottomNav(current = tab, onSelect = {})
                        }
                    }
                }
            }
        }

        val heights = KTab.entries.associateWith {
            composeTestRule.onNodeWithTag(pageTag(it)).getUnclippedBoundsInRoot().let { b -> b.bottom - b.top }
        }
        val screenHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().let { it.bottom - it.top }
        println("[横向き 帯の出し分け] $heights（画面高 $screenHeight）")
        heights.forEach { (tab, h) ->
            assertEquals("$tab の横向きで帯が出ている＝タブ間で本文高が跳ねる", screenHeight, h)
        }
    }

    /**
     * 設定タブの横向きが Rail を立てつつ、**開発節（栞先端の観察器）を壊していない**こと。
     * Rail 化は左端 80dp を本文から削るので、本文側の行が消える／押せなくなる形で壊れうる。
     */
    @Test
    fun `設定の横向きは Rail が立ち 開発節が残って押せる`() {
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            WithRail(railWired = true) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        SettingsScreenK(
                            appTheme = ReadingTheme.LIGHT,
                            onThemeChange = {},
                            followingSystem = false,
                            onFollowSystem = {},
                            currentSkin = Skin.MEIKAI_K,
                            onOpenWardrobe = {},
                            // 「データ」節の〈診断の記録〉行の飛び先。この観点では叩かないので no-op。
                            onOpenDiagnosticsExport = {},
                            skinSwitchingEnabled = true,
                            // 開発節の露出を明示的に開ける（既定 false＝節ごと消える側は ADR 0027 の別テストが持つ）。
                            shioriHighLoadRowVisible = true,
                            shioriHighLoadK = false,
                            onShioriHighLoadChange = {},
                            shioriDebugTipIndex = null,
                            onShioriDebugTipChange = {},
                        )
                    }
                    KBottomNav(current = KTab.SETTINGS, onSelect = {})
                }
            }
        }

        // Rail が立っている＝「本棚」タブラベルが左端 80dp の内側に居る（設定面の本文にこの語は無い）。
        val shelfTab = composeTestRule.onNodeWithText("本棚").getUnclippedBoundsInRoot()
        assertTrue("設定の横向きで Rail が立っていない: 本棚ラベル right=${shelfTab.right}", shelfTab.right <= KNavigationRailWidth)

        // 開発節が残り、観察器の行が本文（Rail の右）に居て押せる。
        // 横向きは画面高 360dp＝設定は縦スクロールの面なので、開発節は初期状態では折り返しの下に居る
        //（存在するが displayed ではない）。送ってから見るのが正で、ここを assertIsDisplayed だけで
        // 済ませると「節が消えた」と「まだ画面外」を区別できない。
        composeTestRule.onNodeWithText("開発").performScrollTo().assertIsDisplayed()
        val observeRow = composeTestRule.onNodeWithText("栞先端を固定（観察）")
            .performScrollTo().getUnclippedBoundsInRoot()
        assertTrue("開発節の観察行が Rail の裏へ回り込んだ: left=${observeRow.left}", observeRow.left >= KNavigationRailWidth)
        composeTestRule.onNodeWithText("＋1").assertHasClickAction()
        composeTestRule.onNodeWithText("解除").assertHasClickAction()
    }

    // ── 補助 ────────────────────────────────────────────────────────────────

    private fun setDiscovery(fontScale: Float, railWired: Boolean) {
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale) { _ ->
            WithRail(railWired) {
                // MainActivity と同じ入れ子（本文が weight(1f)・その下に恒常ナビ）で組む＝実効ビューポートを
                // 「ナビが縦を削った後」の値にするため。Rail 化が効いていれば KBottomNav 自身が何も描かない。
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        DiscoveryHomeK(
                            order = NarouOrder.WEEKLY,
                            state = rankingContent,
                            onBack = {},
                            onOpenDetail = {},
                            onOpenGenre = {},
                            onPickBiggenre = { _, _ -> },
                            onOpenSearch = {},
                            onPickMood = {},
                            onSelectOrder = {},
                            onRefresh = {},
                            // 気分の組を固定＝端末日付で高さが揺れないことを保証する（既定値は日付導出）。
                            initialMoodPattern = MoodPattern.CLASSIC,
                        )
                    }
                    KBottomNav(current = KTab.DISCOVER, onSelect = {})
                }
            }
        }
    }

    /** Rail 化の起動条件（上位シェルの結線）を、テストからだけ差し込む。 */
    @Composable
    private fun WithRail(railWired: Boolean, content: @Composable () -> Unit) {
        if (railWired) {
            CompositionLocalProvider(LocalKTabSelect provides { _: KTab -> }) { content() }
        } else {
            content()
        }
    }

    /** 固定トップ＝ルート上端から縦リスト上端まで／実効ビューポート＝そのリストの実高。 */
    private fun measureDiscoveryFixedTop(): Pair<Dp, Dp> {
        val rootTop = composeTestRule.onRoot().getUnclippedBoundsInRoot().top
        val list = composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).getUnclippedBoundsInRoot()
        return (list.top - rootTop) to (list.bottom - list.top)
    }

    /**
     * 実行時に計量系そのものを試して NATIVE を裏取りする（[DiscoveryHomeKLandscapeT1RowTest] と同法）。
     * アノテーションは付け忘れても赤くならないので、寸法を焼くテストは自分で確かめる。
     */
    private fun assertNativeGraphics() {
        val paint = android.graphics.Paint().apply { textSize = 100f }
        val narrow = paint.measureText("iiii")
        val wide = paint.measureText("WWWW")
        val fullWidth = paint.measureText("日")
        assertTrue(
            "GraphicsMode が NATIVE でない＝焼こうとしている寸法は偽装値: iiii=$narrow WWWW=$wide 日=$fullWidth",
            narrow != wide && fullWidth > 50f,
        )
    }

    private companion object {
        const val SEARCH_PLACEHOLDER = "作品名・作者名・キーワードで探す"

        fun pageTag(tab: KTab) = "page_${tab.name}"

        /** 実装 `DiscoveryListHorizontalMargin`（private のため同値を再掲）＝T1 行の左右マージン。 */
        val ROW_SIDE_MARGIN = 24.dp

        /**
         * 翻訳前の真値（ADR 0034 注記・2026-08-21 に NATIVE で測り直した値）。
         * 旧 105.5dp は LEGACY の代用計量による偽装値で、モック導出 108dp と符合して事故のもとになった。
         */
        val FIXED_TOP_BEFORE = 120.5.dp
        val VIEWPORT_BEFORE = 174.5.dp

        /** T1 後の実測（初回実行の println を焼いた値）。 */
        val FIXED_TOP_T1 = 56.dp
        val VIEWPORT_T1 = 304.dp

        /**
         * 本棚 T1 の固定トップ。**2026-08-26 に 56dp から 48dp へ**（値だけ見ても分からないので内訳を残す）。
         *
         * 内訳の変化＝チップ行が正本どおりに縮み、**支配要因が〈状態チップ行〉から〈表示切替〉へ移った**:
         * - 旧 56dp ＝ チップ行（チップ 44dp ＋ 下 [Spacing.S12]）。チップ 44 の内訳は
         *   行箱 28dp ＋ 上下 [Spacing.S8]。**この 28dp が事故**で、`KStatusChip` の `Text` が
         *   `fontSize` だけ 11.5sp へ落として `lineHeight` を既定 `Typography.bodyLarge`（28.sp）から
         *   継承したまま残していたぶん（正本 .chip の総高 34px に対し実測 44.19dp ＝+30%）。
         * - 新 48dp ＝ **表示切替（`KViewToggleButton` の M3 `IconButton`）の 48dp**。
         *   チップ行は 34.4 ＋ 12 ＝ **46.4dp** まで縮み（正本 `bookshelf-K-landscape.html` の
         *   `.chips` を headless Chrome で実測 **46.00px**＝一致）、`max(46.4, 48)` で切替側が勝つ。
         *
         * ⚠️ 48dp は**当たり判定の下限であって意匠ではない**（正本 `.view` の絵は 38px）。
         * つまりこの定数はもう「チップ行の高さ」ではないので、**チップの寸法を測る用途に使い回さないこと**。
         * 縦向き（trailing なし＝チップ行だけ）は 46.4dp で、こことは別の数になる。
         */
        val FIXED_TOP_T1_SHELF = 48.dp
    }
}
