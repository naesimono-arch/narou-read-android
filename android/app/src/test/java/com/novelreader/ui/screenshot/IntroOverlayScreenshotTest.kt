package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.intro.IntroDeck
import com.novelreader.ui.intro.IntroFigure
import com.novelreader.ui.intro.IntroFlow
import com.novelreader.ui.intro.IntroGroup
import com.novelreader.ui.intro.IntroOverlayContent
import com.novelreader.ui.intro.IntroSecondary
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 教示「はじめに」カード列（[IntroOverlayContent]）のスクリーンショット回帰。
 *
 * ## なぜ張るか
 * このカード列は **新規ユーザーがアプリで最初に見る面**なのに golden を1枚も持っていなかった。
 * 既存の [com.novelreader.ui.intro.IntroOverlayContentTest] は semantics（文言・ボタンの有無・押下）
 * だけを見るため、次の3つを原理的に検出できない:
 *
 *  1. **線画（[com.novelreader.ui.intro.IntroFigureArt]）が絵として壊れること**。7 枚それぞれに
 *     正本モックの `<svg>` path を座標で写経した Canvas 直描きが付いているが、`clearAndSetSemantics`
 *     で TalkBack から隠してあるので **semantics には1ノードも出ない**＝絵以外に検査手段が無い。
 *     座標を1つ書き損じても、いまは全テストが緑のまま通る。
 *  2. テーマ退行（スクリム α .74 の重なり・素地/墨/藍/罫の対比）。カードは背景の上に重なる面なので、
 *     テーマごとに「カードが背景から浮いて見えるか」が変わる。
 *  3. fontScale 2.0 での版面（図版 112dp ＋ 本文 ＋ 項目 ＋ 点 ＋ ボタンが 1 枚に積まれる構造）。
 *
 * ## 撮る状態（なぜ「1 枚 = 1 case」で列の全数か）
 * 状態を代表 1〜2 枚に絞らないのは、**カードごとに固有の線画を持つ**ため（上記 1）＝間引いた枚数は
 * そのぶん無検査で残る。7 枚は同時に、版面を決める 5 軸を過不足なく張る:
 *
 * | case | 本文の形 | 2 択チップ | 小さい行 | 副ボタン | 点 |
 * |---|---|---|---|---|---|
 * | `about_intro` | 段落2 | 無 | 無 | ［あとで］ | 3 個・1 個目 |
 * | `about_orientation` | 段落1 | **有**（右が on） | 有（罫） | ［← もどる］ | 3 個・2 個目 |
 * | `about_ways`（**代表**） | 段落2 | 無 | 有（罫） | ［← もどる］ | 3 個・3 個目 |
 * | `reading_tap` | 項目2 | 無 | 無 | ［あとで］ | 2 個・1 個目 |
 * | `reading_vertical` | 項目3 | 無 | 無 | ［← もどる］ | 2 個・2 個目 |
 * | `search_single` | 項目3 | 無 | 有（罫） | **無し**（主ボタン単独・右寄せ） | **無し** |
 * | `import_single` | 項目3 | 無 | 有（罫） | **無し**（同上） | **無し** |
 *
 * `import_single` は版面の軸としては `search_single` と同型（項目3・小さい行・副ボタン無し・点無し）だが、
 * **線画が違う**＝上記 1 の理由でここだけは間引けない（絵は semantics に 1 ノードも出ない）。
 *
 * `about_orientation` を間引けないのは、**チップの選択/非選択という 1 軸がこの 1 枚にしか出ない**ため
 * ——藍の実塗り（on）と枠線だけ（off）の対比が壊れても、semantics は「選択済み」を返し続ける。
 *
 * 代表を `about_ways` にしたのは、**このカード列が使う色トークンを1枚で全部露出する唯一の状態**だから:
 * スクリム・素地・墨（題と段落中の強調）・infoText（本文と項目名と副ボタン）・outlineVariant（再訪導線の罫）・
 * outline α（消し点）・primary（点 on・主ボタン・線画の主線）・onPrimary（ボタン文字）。
 * ここを 3テーマ×2スケール全数で撮れば、他 case はライトの 1.0/2.0 だけで足りる
 * （既存の張り方＝BookshelfD/K・ProcessingBannerK と同じ「代表 case だけ全数」の流儀）。
 *
 * **通し（walkthrough=true）は撮らない**: 版面の構造（点・副ボタン・本文）は自動割り込みと同一で、
 * 変わるのは主ボタンの語だけ（終端でなくなるので「つぎへ」になる）。語は
 * [com.novelreader.ui.intro.IntroOverlayContentTest] が固定しており、絵を6枚増やして買えるものが無い。
 *
 * ## 撮り方
 * - **[captureDialogSkinned] は使わない**。教示カードは `Dialog` の別窓ではなく MainActivity と同じ窓の
 *   `Box` に重なる（IntroOverlay.kt「NavHost には足さない＝呼び出し元の上へ重ねる」）ので、
 *   [captureSkinned] の `LocalDensity` 上書きがそのまま効く（別窓なら効かない＝あちらの KDoc 参照）。
 * - スキンは K 固定（ADR 0027 の出荷面）。カードは `LocalShelfColors.infoText` を読むので、
 *   撮る対象と同じ入口（[Skin.MEIKAI_K] の [com.novelreader.ui.theme.NovelReaderTheme]）で包む。
 * - 背景は**実画面ではなく平坦な素地**。正本モックは組ごとに違う背景（空の本棚／本文／検索）を描くが、
 *   実画面を敷くには VM・DB・実 API を立てる必要があり、守りたいカードの版面と無関係な配線でテストが折れる
 *   （本棚ダイアログ群を「ホスト画面ごと」でなく単体で撮っているのと同じ裁定＝ScreenshotTestSupport.kt）。
 *   平坦にすると差分がカード側の変化だけに帰属する利点もある。
 * - fontScale 2.0 では中身が縦スクロール器（IntroOverlay.kt）に収まりきらず、撮れるのは
 *   **スクロール前の 1 画面目**になる。この絵は「開いた瞬間に何が見えるか」を守るもので、
 *   ［とじる］へ到達できるかは絵に痕跡が残らない＝そちらは IntroOverlayContentTest の
 *   `performScrollTo` が別軸で見る（役割を混ぜない）。
 *
 * ゲート非同乗（`testDebugUnitTest` では `captureRoboImage` が no-op）の理由は ScreenshotTestSupport.kt。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class IntroOverlayScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val flow = IntroOverlayGoldenCases.byId(caseId).flow
        composeTestRule.captureSkinned(
            skin = Skin.MEIKAI_K,
            theme = theme,
            fontScale = fontScale,
            // ⚠️ 接頭辞は**この場に文字列リテラルで**置く（定数や名前付き引数にしない）。
            // GoldenCoverageTest.capturedPrefixes() は `goldenName("…"` の形を正規表現で読み取って
            // 「撮っているはずの束」を数えるので、定数に逃がすと束ごと走査から外れ、記録した PNG が
            // まるごと孤児として赤くなる（黙って外れはしないが、往復が1回増える）。
            fileName = goldenName("IntroOverlayK", caseId, theme, fontScale),
        ) {
            // カードは自分の素地しか持たない＝背後に敷く面が無いとスクリムの α が合成先を失う。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                IntroOverlayContent(
                    flow = flow,
                    onNext = {},
                    onBack = {},
                    onDismiss = {},
                    // 正本 §3 の 2/3 は**縦書きが on** の姿。golden もその 1 状態だけを撮る
                    // （off 側は同じ部品の色違いで、増やしても線画も版面も新しく張らない）。
                    orientationVertical = true,
                    onOrientationChange = {},
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            IntroOverlayGoldenCases.ALL.forEach { case ->
                // 代表だけ 3テーマ全数、他はライトのみ（色トークンは代表 case が張る＝既存の流儀）。
                val themes = if (case.fullMatrix) ScreenshotConfig.THEMES else listOf(ReadingTheme.LIGHT)
                themes.forEach { t ->
                    ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(case.caseId, t, s)) }
                }
            }
        }
    }
}

/**
 * 撮る状態の表。**列（[IntroDeck]）から導けるものは導く**——`index` を数値で直書きすると、
 * カードの並べ替えで「撮っているつもりの状態」が静かに別のカードへずれる。
 */
internal data class IntroGoldenCase(
    val caseId: String,
    val flow: IntroFlow,
    /** 3テーマ×2スケールを全数で撮る代表か（false はライトの 1.0/2.0 だけ）。 */
    val fullMatrix: Boolean = false,
)

internal object IntroOverlayGoldenCases {

    val ALL: List<IntroGoldenCase> = listOf(
        IntroGoldenCase("about_intro", IntroFlow(IntroGroup.ABOUT, walkthrough = false)),
        IntroGoldenCase(
            caseId = "about_orientation",
            flow = IntroFlow(
                IntroGroup.ABOUT,
                walkthrough = false,
                // 数値で書かず**列から引く**＝並べ替えても「撮っているつもりの状態」がずれない
                // （このファイルの冒頭で `index` の直書きを禁じているのと同じ理由）。
                index = IntroDeck.cards.indexOfFirst { it.choice != null },
            ),
        ),
        IntroGoldenCase(
            caseId = "about_ways",
            flow = IntroFlow(
                IntroGroup.ABOUT,
                walkthrough = false,
                index = IntroDeck.lastIndexOf(IntroGroup.ABOUT),
            ),
            fullMatrix = true,
        ),
        IntroGoldenCase("reading_tap", IntroFlow(IntroGroup.READING, walkthrough = false)),
        IntroGoldenCase(
            caseId = "reading_vertical",
            flow = IntroFlow(
                IntroGroup.READING,
                walkthrough = false,
                index = IntroDeck.lastIndexOf(IntroGroup.READING),
            ),
        ),
        IntroGoldenCase("search_single", IntroFlow(IntroGroup.SEARCH, walkthrough = false)),
        IntroGoldenCase("import_single", IntroFlow(IntroGroup.IMPORT, walkthrough = false)),
    )

    fun byId(caseId: String): IntroGoldenCase =
        ALL.firstOrNull { it.caseId == caseId } ?: error("未知の case: $caseId")
}

/**
 * 撮影表が列の実体から取り残されていないかを機械で締める。
 *
 * ## なぜ [GoldenCoverageTest] だけでは足りないか
 * あちらの双方向照合は **golden 接頭辞（＝束）の粒度**でしか働かない。`IntroOverlayK` の PNG が
 * 1枚でも在れば「撮影テストがある束」として通ってしまうので、**7 枚目のカードを列へ足しても
 * 誰も赤くならない**——新しい線画と版面が無検査のまま出荷される（撮っていない面は最初から
 * 検査対象に入らない、という GoldenCoverageTest 自身が挙げている失敗の型そのもの）。
 * カードは nav ルートを持たない被せもの＝ルート側の網羅検査も届かないので、ここで塞ぐ。
 *
 * この3本は Robolectric を要さない純 JVM の走査で、golden PNG の有無にも依存しない
 * （＝record 前でも意味のある赤/緑を返す）。
 */
class IntroOverlayGoldenCaseCoverageTest {

    @Test
    fun `列の全カードがちょうど1つの case で撮られている`() {
        assertEquals(
            "カードを増減したら撮影表（IntroOverlayGoldenCases.ALL）も足す/消すこと。" +
                "束の粒度で見る GoldenCoverageTest はこのずれを検出できない。",
            IntroDeck.cards.indices.toSet(),
            IntroOverlayGoldenCases.ALL.map { it.flow.index }.toSet(),
        )
        assertEquals(
            "case が重複している＝同じ golden ファイル名を2回撮り、後勝ちで1つが消える。",
            IntroOverlayGoldenCases.ALL.size,
            IntroOverlayGoldenCases.ALL.map { it.caseId }.toSet().size,
        )
    }

    @Test
    fun `線画の全種と副ボタンの全態が撮影表に現れる`() {
        // 線画は semantics に一切出ない＝golden 以外に検査手段が無い（本ファイル冒頭の理由1）。
        assertEquals(
            "線画を増やしたら、それを載せたカードの case も撮ること。",
            IntroFigure.entries.toSet(),
            IntroOverlayGoldenCases.ALL.map { it.flow.card.figure }.toSet(),
        )
        // 副ボタンの3態はボタン列の配置そのもの（NONE だけ主ボタン単独の右寄せ）。
        assertEquals(
            "副ボタンの態を増やしたら、その態が出る case も撮ること。",
            IntroSecondary.entries.toSet(),
            IntroOverlayGoldenCases.ALL.map { it.flow.secondary }.toSet(),
        )
    }

    @Test
    fun `テーマ×スケール全数の代表はちょうど1つ`() {
        // 0 個だと GoldenCoverageTest のマトリクス検査が赤（免除登録を強いられる）。
        // 2 個以上は色トークンを二重に張るだけで PNG が 6枚増える（既存の張り方の裁定）。
        assertEquals(1, IntroOverlayGoldenCases.ALL.count { it.fullMatrix })
    }
}
