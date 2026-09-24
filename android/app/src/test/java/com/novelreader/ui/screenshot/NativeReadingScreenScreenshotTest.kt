package com.novelreader.ui.screenshot

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.ui.ChapterNav
import com.novelreader.ui.ChapterScreenContent
import com.novelreader.ui.ContinuationCta
import com.novelreader.ui.NcodeLink
import com.novelreader.ui.ReadingChrome
import com.novelreader.ui.ReadingTypography
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.rememberReadingColors
import com.novelreader.viewmodel.NcodeSearchUiState
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 読書ルートとクローム（[ChapterScreenContent]＝上下バー・「最上部へ」ピル・没入の取っ手・復帰ヒント）の
 * スクリーンショット回帰。**監査 G-8 の撮影条件追加・優先度1位**（読書ルートが golden 0枚だった穴）。
 *
 * ## なぜ golden でなければ守れないか
 * 既存の読書層テストは [com.novelreader.ui.NativeReadingScreenA11yTest]（customActions と実ボタンの同一性）・
 * [com.novelreader.ui.NativeReadingScreenTopPillTest]（ピルの出現条件と寸法）・
 * [com.novelreader.ui.ReadingBarAlphaTest]（alpha 合成の純関数）と、いずれも **semantics か算術**しか見ない。
 * だがこの面の意匠は、その2つに1ビットも現れない層に載っている:
 *
 *  1. **上下バーの面 α.92 越しに本文が透ける**こと（2026-09-04 裁定 D/K・2026-09-07 裁定 J）。
 *     [com.novelreader.ui.readingChromeBarSurface] は `drawBehind` の2段塗り＝描画結果であり、
 *     α を 1f へ戻しても semantics は 1 ノードも変わらない。しかも **正本モックには写らない**——
 *     `skins/reading-J.html` の4パネルは全て「バーの下に本文が無い」構図（章頭 64dp クリアランス／没入／
 *     章末の空き）で、透けが1枚も出ない（2026-09-07 のコミット why が明記）。**絵以外に検査手段が無い**。
 *  2. **「最上部へ」ピルの器**（α.78 の面＋1px ヘアライン枠＋外側だけの落ち影）。影を面の下へ敷く素朴な
 *     実装へ書き換えると器の内側が濁るが、[com.novelreader.ui.ReadingChromePillSurfaceTest] が見るのは
 *     切り出した描画関数**単体**で、実画面の中でピルが本文の上にどう載るかまでは見ていない。
 *  3. **没入の取っ手**（藍 α.30 の 46×3dp 帯）。`clearAndSetSemantics {}` で TalkBack から隠してあり、
 *     濃さは `graphicsLayer` 内の deferred read＝**semantics にも算術にも出ない**。
 *  4. **復帰ヒントの色**（2026-09-07 に textSecondary → infoText へ移した AA 是正）。文言は既存テストが
 *     固定しているが、色は絵にしか出ない。
 *
 * ## 撮る状態（4 case × 2 スキン）
 *
 * | case | 没入 | 位置 | 何をこの1枚だけが張るか |
 * |---|---|---|---|
 * | `head` | off | 章頭 | 上バーの章題（明朝・省略）／章見出し／64dp クリアランス＝**バーの下に本文が無い**基準面 |
 * | `mid_pill`（**代表**） | off | 章途中 | **バーの面越しに本文が透ける**／「最上部へ」ピル（語つき初回）／下端4ボタン |
 * | `immersive` | **on** | 章途中 | バー全退避＝本文が全画面／下端の取っ手（藍 α.30） |
 * | `immersive_hint` | **on** | 章途中 | 復帰ヒント「画面をタップでメニュー」＝infoText のピル |
 *
 * 代表を `mid_pill` にしたのは、**この面の色トークンを1枚で最も多く露出する**ため（バー地 navBackground/
 * topBarBackground・題字 topBarTitle・アイコン topBarIcon・強調 accent・無効 placeholder・本文 text・
 * ピルの枠 divider）。既存の張り方（BookshelfD/K・IntroOverlayK）と同じ「代表 case だけ 3テーマ×2スケール
 * 全数、追加 case はライトの 1.0/2.0」の流儀に従う。ただし **`immersive` も全数**にしてあるのは、監査 G-8 の
 * 起票が「3テーマ×2スケール×**没入2値**」と条件を名指ししているため＝没入は追加状態ではなく軸そのもの。
 *
 * ## なぜ K と J の2スキンか
 * 出荷素地は K（[Skin.MEIKAI_K]・トークンは D へ全委譲）なので K が主。J（[Skin.PORTAL_J]）を足すのは
 * **2026-09-07 に J のバーも α.92 へ移した裁定を、他のどのテストも守っていない**から——変更点は
 * `SkinJ.readingBarSurfaceAlpha` の1行で、戻しても全ゲートが緑のまま通る。J は ADR 0027 で出荷スコープ外
 * だが、[TocPortalJ] を「同型の欠陥を見るためだけ」に撮っている前例と同じ理由で例外を置く。
 * J は代表 case（`mid_pill`）だけを 3テーマ全数で撮る＝裁定の根拠が「ダークも同値クラス（--bar #101913 と
 * --bg #0F1712）で3テーマとも同じ向きに効く」だったので、**3テーマ揃っていないと根拠を張れない**。
 *
 * ## 撮り方（[captureSkinned] を使わない理由）
 * この面は「どの state で描くか」が絵の全てで、state は `setContent` の**後**でしか作れない
 * （[TopAppBarState.heightOffsetLimit] はバーが自分の実高を測ってから入る値＝合成前には 0f）。
 * 共有ヘルパは `setContent` → 即 capture の2手なので、あいだに state の突き当てを挟むこの形を直に書く。
 * 突き当て方は [com.novelreader.ui.NativeReadingScreenTopPillTest] と同一（同じ画面の semantics 側の兄弟）。
 *
 * ⚠️ Robolectric では systemBars inset が 0 になる＝[com.novelreader.ui.chromeBarBands] のシステム帯が
 * 高さ 0 へ丸まり、バーは**全面が「面」**として α で塗られる。実機では帯（ステータス/ナビ）だけ不透明で
 * 残るので、golden とは1点だけ姿が違う。透過そのものを見る目的には影響しない（むしろ面の α が全高に
 * 出るので変化が読みやすい）が、**「帯が不透明であること」はこの golden では守れない**——そちらは
 * [com.novelreader.ui.ReadingChromeBarSurfaceTest] 相当の純関数側（chromeBarBands）が見る軸。
 *
 * ⚠️ 縦書き（[ReadingTypography.verticalMode]）は撮らない。クローム側の差は「章題を出さない」と
 * 「下端4ボタンの鏡像配置」の2つで、どちらも semantics に出る＝
 * [com.novelreader.ui.ReadingBottomBarMirrorTest] が既に張っている。本文側の縦組は
 * `VerticalChapterContent` / `VerticalParagraph` の束が別軸で持つ。
 *
 * ゲート非同乗（`testDebugUnitTest` では `captureRoboImage` が no-op）の理由は ScreenshotTestSupport.kt。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NativeReadingScreenScreenshotTest(
    private val caseId: String,
    private val skin: Skin,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val case = ReadingRouteGoldenCases.byId(caseId, skin)
        // heightOffsetLimit は 0f で始める＝バーが自分の実高を測って負値を入れるのを待つ（下の突き当て）。
        val topAppBarState = TopAppBarState(0f, 0f, 0f)
        val lazyListState = LazyListState(firstVisibleItemIndex = case.firstVisibleItemIndex)

        composeTestRule.setContent {
            val base = LocalDensity.current
            // フォントスケールだけ差し替える（density は端末値を維持）＝captureSkinned と同方式。
            // 読書ルートは Dialog の別窓ではなくホスト窓へ直に載るので LocalDensity 上書きがそのまま効く。
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                NovelReaderTheme(skin = skin, theme = theme) {
                    ReadingRouteFixture(
                        theme = theme,
                        lazyListState = lazyListState,
                        topAppBarState = topAppBarState,
                        showChromeHint = case.showChromeHint,
                        topPillLabelShown = case.topPillLabelShown,
                    )
                }
            }
        }

        // 没入 on/off の作り分け。実装は真偽値を持たず `collapsedFraction`（= heightOffset / limit）だけを
        // 見る（本文タップのトグルが実オフセットを動かす唯一の駆動元）ので、ここでも state を突き当てる。
        // limit がまだ 0f なのは合成前に測れなかった場合＝保険で -100f を入れる（TopPillTest と同一）。
        composeTestRule.runOnIdle {
            if (topAppBarState.heightOffsetLimit >= 0f) topAppBarState.heightOffsetLimit = -100f
            topAppBarState.heightOffset = if (case.immersive) topAppBarState.heightOffsetLimit else 0f
        }
        // ピルは AnimatedVisibility のフェード入場を挟む＝突き当ての直後はまだ半透明。出きるまで待つ。
        composeTestRule.waitForIdle()

        // ⚠️ 接頭辞は**この場に文字列リテラルで**置く（定数や名前付き引数にしない）。
        // GoldenCoverageTest.capturedPrefixes() は `goldenName("…"` の形を正規表現で読み取って
        // 「撮っているはずの束」を数えるので、定数に逃がすと束ごと走査から外れ、記録した PNG が
        // まるごと孤児として赤くなる。2スキンぶんの分岐を if で書き下すのはそのため。
        val fileName = if (skin == Skin.PORTAL_J) {
            goldenName("ReadingScreenJ", caseId, theme, fontScale)
        } else {
            goldenName("ReadingScreenK", caseId, theme, fontScale)
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName",
        )
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_{1}_{2}_scale{3}")
        fun data(): List<Array<Any>> = buildList {
            ReadingRouteGoldenCases.ALL.forEach { case ->
                // 代表だけ 3テーマ全数、他はライトのみ（既存の流儀＝GoldenCoverageTest の KDoc）。
                val themes = if (case.fullMatrix) ScreenshotConfig.THEMES else listOf(ReadingTheme.LIGHT)
                themes.forEach { t ->
                    ScreenshotConfig.FONT_SCALES.forEach { s ->
                        add(arrayOf<Any>(case.caseId, case.skin, t, s))
                    }
                }
            }
        }
    }
}

/**
 * 読書ルートの描画層を、route（[com.novelreader.ui.ChapterScreen]）が渡すのと同じ形で組む。
 *
 * なぜ route ごとでなく描画層 Content か: route は VM・SharedPreferences・narouRepository・非同期パースを
 * 抱える＝撮りたい版面と無関係な配線でテストが折れる。stateless な Content を撮るのは
 * さがす配下4束（DiscoverySearchScreen ほか）で確立した流儀そのまま。
 *
 * ⚠️ [rememberReadingColors] で引くのが load-bearing。`ReadingTheme.colors` は **D 固定**のアクセサで、
 * J で撮っても D の配色が出る（絵は「それらしく」見えるので気づけない）。実画面と同じ経路で引く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingRouteFixture(
    theme: ReadingTheme,
    lazyListState: LazyListState,
    topAppBarState: TopAppBarState,
    showChromeHint: Boolean,
    topPillLabelShown: Boolean,
) {
    val colors = rememberReadingColors(theme)
    // 束は全フィールド必須（既定値なし＝ReadingFace.kt 冒頭の「配線忘れをコンパイルエラーへ」）。
    ChapterScreenContent(
        parseResult = ParseResult.Success(FixtureChapter),
        colors = colors,
        typography = ReadingTypography(
            // 出荷既定（ReadingScreen の app_prefs 既定）と同じ値＝「初期設定のまま読んでいる人の面」を撮る。
            fontSize = 18,
            onFontSizeChange = {},
            onFontSizePersist = {},
            lineHeightEm = 2.5f,
            onLineHeightChange = {},
            onLineHeightPersist = {},
            bodyMarginDp = 20,
            onBodyMarginChange = {},
            onBodyMarginPersist = {},
            verticalMode = false,
            onVerticalModeChange = {},
        ),
        theme = ThemeControl(
            appTheme = theme,
            onThemeChange = {},
            followingSystem = false,
            onFollowSystem = {},
        ),
        chrome = ReadingChrome(
            lazyListState = lazyListState,
            topAppBarState = topAppBarState,
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
            // true＝初期退避の実測待ちを抜けた状態。false だとバーが alpha=0 で消え、撮る対象が消滅する。
            barsVisualReady = true,
            showChromeHint = showChromeHint,
            topPillLabelShown = topPillLabelShown,
            onTopPillLabelShown = {},
        ),
        nav = ChapterNav(
            prevFile = "c0002.html",
            nextFile = "c0004.html",
            // true＝前後章ボタンが活性（disabled の淡色は撮らない＝下端バーは「読める状態」を代表させる）。
            navEnabled = true,
            isLastChapter = false,
            // 目次ロード済みの姿。M/P のみが読む値だが、null で渡すと J の章末印まで縮退形へ変わるので実値。
            chapterNumber = 3,
            totalChapters = 42,
            onNavigateTo = {},
            onBack = {},
            onNavigateToBookshelf = {},
        ),
        ncodeLink = NcodeLink(
            bookTitle = "灯台守の休暇",
            ncode = null,
            ncodeSearchState = NcodeSearchUiState.Loading,
            onSearchNcode = {},
            onRetryNcodeSearch = {},
            onLinkNcode = {},
        ),
        // null＝継続カードを出さない（最終章でないので実画面でも出ない）。
        continuationCta = ContinuationCta(
            continuationInfo = null,
            onReadContinuation = {},
            onOpenWorkPage = {},
        ),
        prevPeek = null,
        nextPeek = null,
        showReturnChip = false,
        onReturnToContinuation = {},
        onRetryParse = {},
    )
}

/** 撮る状態の表。 */
private data class ReadingRouteGoldenCase(
    val caseId: String,
    val skin: Skin,
    /** 没入（バー全退避）か。false＝クローム表示。 */
    val immersive: Boolean,
    /**
     * 先頭可視アイテムの index。
     * ⚠️ **0 と「章の3割以上」は別の絵になる**——0 では contentPadding.top（ステータス inset＋64dp）が効いて
     * バーの下に本文が来ず、透過が1画素も写らない。ピルの出現条件（index×10 ≥ 全×3）とも共通の軸。
     */
    val firstVisibleItemIndex: Int,
    val showChromeHint: Boolean = false,
    /** true＝ピルはアイコンのみ。false＝通算初回の語つき（器が 32→36dp へ伸びる姿）。 */
    val topPillLabelShown: Boolean = true,
    /** 3テーマ×2スケールを全数で撮る代表か（false はライトの 1.0/2.0 だけ）。 */
    val fullMatrix: Boolean = false,
)

private object ReadingRouteGoldenCases {

    /**
     * 章途中の着地 index。全 [FIXTURE_PARAGRAPHS] 段落＋章見出し1に対して 3割の閾値を余裕で超え、かつ
     * 下に十分な段落が残る位置（＝画面が本文で埋まる）。
     */
    private const val MID_INDEX = 80

    val ALL: List<ReadingRouteGoldenCase> = listOf(
        ReadingRouteGoldenCase(
            caseId = "head",
            skin = Skin.MEIKAI_K,
            immersive = false,
            firstVisibleItemIndex = 0,
        ),
        ReadingRouteGoldenCase(
            caseId = "mid_pill",
            skin = Skin.MEIKAI_K,
            immersive = false,
            firstVisibleItemIndex = MID_INDEX,
            // 語つき＝器がいちばん大きい姿。fontScale 2.0 で語が欠けないかもここで見る。
            topPillLabelShown = false,
            fullMatrix = true,
        ),
        ReadingRouteGoldenCase(
            caseId = "immersive",
            skin = Skin.MEIKAI_K,
            immersive = true,
            firstVisibleItemIndex = MID_INDEX,
            fullMatrix = true,
        ),
        ReadingRouteGoldenCase(
            caseId = "immersive_hint",
            skin = Skin.MEIKAI_K,
            immersive = true,
            firstVisibleItemIndex = MID_INDEX,
            // ヒントと取っ手は排他（immersiveHandleVisible がピルの実寿命を見る）＝この case は取っ手を写さない。
            showChromeHint = true,
        ),
        ReadingRouteGoldenCase(
            caseId = "mid_pill",
            skin = Skin.PORTAL_J,
            immersive = false,
            firstVisibleItemIndex = MID_INDEX,
            topPillLabelShown = false,
            fullMatrix = true,
        ),
    )

    /**
     * ⚠️ caseId **だけ**では引けない（`mid_pill` は K と J の2件が同名で並ぶ）。スキンまで込みで引く＝
     * caseId だけで引くと J のパラメータでも K の case が返り、**J の PNG に K の絵が焼かれる**
     * （どちらも「それらしい読書画面」なので絵を見ても気づけない）。
     */
    fun byId(caseId: String, skin: Skin): ReadingRouteGoldenCase =
        ALL.firstOrNull { it.caseId == caseId && it.skin == skin }
            ?: error("未知の case: $caseId / $skin")
}

/** 本文の段落数。3割閾値と「章途中で画面が本文で埋まる」を両立できれば足りる。 */
private const val FIXTURE_PARAGRAPHS = 120

/**
 * 撮影用の章。
 *
 * ⚠️ 各段落を字下げ「　」で始めるのが load-bearing。表示側の `splitIntoParagraphs` は「行頭が字下げ／
 * 開き括弧」を段落の切れ目に使う（ADR 0041 決定2）ので、字下げの無い行を並べると**全部1段落へ畳まれて**
 * アイテムが1個になり、この束が要求する「章の3割以上まで進んだ状態」そのものが作れない。
 *
 * 段落ごとに番号を織り込むのは、絵の差分を目で追えるようにするため（どの段落が画面のどこに来たかが読める）。
 * `object` で1度だけ組むのは、パラメータ 22 通りで同じリストを組み直さないため。
 */
private val FixtureChapter = ChapterContent(
    title = "第三話　灯台守の休暇",
    segments = buildList {
        repeat(FIXTURE_PARAGRAPHS) { i ->
            add(
                TextSegment.Plain(
                    "　${i + 1}　岬の道はまだ湿っていて、風は塩の匂いを運んでくる。" +
                        "灯台の白い壁が朝の光を返し、うねる海面の遠くで漁船が二艘、静かに位置を変えていた。",
                ),
            )
            add(TextSegment.LineBreak)
        }
    }.toImmutableList(),
)
