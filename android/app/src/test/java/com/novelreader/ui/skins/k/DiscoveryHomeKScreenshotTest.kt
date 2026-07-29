package com.novelreader.ui.skins.k

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.workPoints
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「さがす」（[DiscoveryHomeK]）のスクリーンショット回帰（ADR 0009 増補1）。
 *
 * 日付依存について（2026-07-30 に本番側で解消済み）: きょうの気分の初期組はかつて MoodSectionK の内部で
 * `LocalDate.now()` から導出され、テストから固定できなかった（`LocalDate.now()` は JDK クラス＝
 * Robolectric の shadow 対象外＝差し替わるのは instrumented クラス内の System.currentTimeMillis だけ）。
 * この golden は当初その回避のため画面末尾まで送って気分ブロックを画角外へ逃がし、逃がし損ねを機械
 * アサートで見張っていたが、[DiscoveryHomeK] の `initialMoodPattern` への state hoisting によって
 * **回避策ごと不要になったため撤去した**（役目を終えた足場は残さない）。いまは組を固定して上端から素直に撮る。
 *
 * 撮る状態と選定理由:
 *  - home（上端・組=CLASSIC 固定）＝K の顔。画面タイトル「さがす」＋第一強調の実検索フィールド、
 *    きょうの気分カード（藍ルール＋明朝見出し）、ドットインジケータ、日替わり注記、ジャンルチップ列と、
 *    K が「さがす」で主張している構造がすべてこの1枚に入る。組を CLASSIC に固定するのは、3組のうち
 *    どれを撮っても検出力は同じで、enum 先頭＝レビュー時に「どの組か」を迷わない基準点になるため。
 *  - ranking（末尾へ送った版・ライトのみ）＝ランキング行（NovelListRow）と公式リンク行。上端 golden では
 *    画角外に出る領域で、順位数字の上位3位色分け・メタ3点・期間タブの選択下線がここにしか写らない。
 *    スクロールは日付回避の名残ではなく「1画面に収まらない画面の下半分を撮る」ための状態選択として残す。
 *
 * このテストが赤くなる条件:
 *  ・固定トップ（h1「さがす」・検索フィールドの高さ52dp/角丸12dp/surfaceVariant 地/プレースホルダ文言）
 *  ・気分カードの版面（枠1dp・左3dp 藍ルール・明朝見出し・説明文の色）と2列×2行の格子、ドットの
 *    形（現在=16x6 ピル／他=6dp 円）、日替わり注記の文言
 *  ・気分ブロックの高さ安定枠（全3組を重ねて最大高を予約する仕組み）が壊れて枠高が変わった場合
 *  ・ジャンルチップの形（丸枠）と「すべて→」の藍枠藍字
 *  ・期間タブの語彙・選択表示（藍 bold＋下線2dp）・下罫
 *  ・ランキング行の構造（順位数字34dp幅・明朝・上位3位のみ primary／題名2行 clamp／著者＋ジャンルタグ／
 *    連載状態・読了目安・ポイントのメタ3点）と行間の区切り線、公式リンク行（区切り線・文言・↗）
 *  ・colorScheme/ShelfColors.infoText/Spacing/Font* の値変更、fontScale 2.0 での折返し変化
 *
 * 撮っていないもの（既知の穴）: 気分の別2組（CLASSIC 以外）・ランキングのスケルトン/空/失敗。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DiscoveryHomeKScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rankingContent = DiscoveryUiState.Content(
        allcount = RANKING_TITLES.size,
        novels = RANKING_TITLES.mapIndexed { index, title ->
            workSummary(
                title = title,
                author = "作者${index + 1}",
                ncode = "N%04dAA".format(index + 1),
                // メタ3点（連載状態・読了目安・ポイント）が全部埋まる行にする＝行の版面を最大限固定する。
                chapterCount = 40 + index,
                serialState = if (index % 3 == 0) SerialState.COMPLETED else SerialState.ONGOING,
                readMinutes = 120 + index * 10,
                genreCode = 201,
                // order=週間ゆえ週間ポイントが行末に出る（値は固定＝日付や実データに依らない）。
                points = workPoints(weekly = 90_000 - index * 1_000),
            )
        },
    )

    @Test
    fun capture() {
        composeTestRule.setSkinKContent(theme, fontScale) { _ ->
            DiscoveryHomeK(
                // 週間を選ぶ理由: 期間タブの選択表示（藍 bold＋2dp 下線）が先頭以外に付く＝タブの
                // 選択位置ごと golden に載る（日間だと「先頭が選択」と区別が付かない）。
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
                // 日替わりの組を固定＝golden を端末日付から切り離す（本番の既定値は日付導出のまま）。
                initialMoodPattern = MoodPattern.CLASSIC,
            )
        }

        if (caseId == CASE_RANKING) {
            // 画面本体の縦 LazyColumn を特定して末尾（公式リンク）まで送る。hasScrollToNodeAction だけでは
            // 気分/期間の横ページャも一致するため、縦スクロール軸を持つ唯一のノードで絞る
            //（DiscoveryHomeKRankingTest と同じ絞り方＝横ページャを誤って動かすと画角ごと変わる）。
            composeTestRule.onNode(
                hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            ).performScrollToNode(hasText(OFFICIAL_LINK))
            composeTestRule.waitForIdle()
            // 末尾まで届いている＝空白を撮っていないことの担保。スクロールが空振りすると golden が
            // 「上端と同じ絵」に化け、以後この case はどんな退行も検出しなくなる。
            composeTestRule.onNodeWithText(OFFICIAL_LINK).assertIsDisplayed()
        }

        composeTestRule.captureRoot(goldenName("DiscoveryHomeK", caseId, theme, fontScale))
    }

    companion object {
        private const val CASE_HOME = "home"
        private const val CASE_RANKING = "ranking"
        private const val OFFICIAL_LINK = "小説家になろう公式サイトで探す"

        /** 10件＝末尾送りの ranking ケースで行が画面を満たす件数（少ないと下半分が余白ばかりになる）。 */
        private val RANKING_TITLES = listOf(
            "銀の匙と月の砂",
            "辺境ギルドの薬草係",
            "冬告げの塔",
            "海鳴りのラプソディ",
            "追放されたので図書館を建てる",
            "薄明の遊撃隊",
            "石畳の街と時計職人",
            "神様の落とし物",
            "雨天決行の冒険者たち",
            "最果ての灯台",
        )

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            // 代表状態（上端）＝全テーマ×全スケール。
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_HOME, t, s)) }
            }
            // 追加状態（末尾）＝ライトのみ×2スケール。色トークンは上端 golden が張るため、ここは
            // ランキング行の骨格（順位色・メタ3点・折返し）の退行検知に絞る。
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_RANKING, ReadingTheme.LIGHT, s))
            }
        }
    }
}
