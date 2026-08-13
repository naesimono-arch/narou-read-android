package com.novelreader.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.discovery.DiscoveryResultContent
import com.novelreader.ui.skins.k.captureSkinK
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.PagingState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 結果一覧（nav ルート `discovery/result`＝[com.novelreader.ui.discovery.DiscoveryResultScreen]）の
 * スクリーンショット回帰。**監査 2026-08-06 G-8 で 0枚だったさがす配下の面**を埋める束（優先度2位）。
 *
 * ### なぜ K 素地か・なぜ Content 層を撮るか
 * 理由は [NovelDetailScreenScreenshotTest] の KDoc と同じ（出荷素地＝K／ルート層は VM 依存）。
 * 加えてこの画面は [DiscoveryResultContent] の冒頭で `LocalSkin.current` を exhaustive に分岐し、
 * M/P/J は別実装へ委譲する＝**どのスキンで包むかが撮る絵そのものを決める**。K/D/C は共通実装へ落ちるため
 * この束は K/D/C 3スキン分の版面を張る（M/P/J は ADR 0027 で出荷スコープ外＝別束を持たない）。
 *
 * ### 撮る case と選定理由
 *  - `list`（3テーマ×2スケール全数）＝検索由来の一覧。**文脈ヘッダ（長い見出し＋補足）＋条件チップ7個の
 *    FlowRow＋件数行＋一覧行**が1枚に入る、この画面で最も情報が密な状態。fontScale 2.0 で壊れる筋は
 *    (a) 条件チップの折り返し段数が増えて一覧が画面外へ押し出される (b) 一覧行の題名2行 clamp が
 *    メタ3点を押し出す の2つで、どちらも**長い作品名と多数の条件**が無いと再現しない。
 *    `PagingState.Idle` を選ぶのは「さらに読み込む」フッタごと版面に含めるため（Complete だとフッタが消える）。
 *  - `empty`（ライトのみ×2スケール）＝0件。`DiscoveryStatusBox` 単体の golden は Error 状態しか無く、
 *    結果画面固有の空状態（CTA「検索条件を変える」付き）はどの束にも写っていなかった。
 *
 * 撮っていないもの（既知の穴）: Error 状態（版面は既存 `DiscoveryStatusBox` golden が張る）・
 * 追加読込中/追加失敗のフッタ・条件チップのドロップダウン展開。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DiscoveryResultScreenScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val state = when (caseId) {
            CASE_EMPTY -> DiscoveryUiState.Empty
            else -> DiscoveryUiState.Content(
                // allcount > novels.size ＝「N 件中 上位 M 件を表示」の分岐へ入れる（全件到達だと文言が変わる）。
                allcount = 1_284,
                novels = DiscoveryScreenshotFixtures.resultRows(),
                paging = PagingState.Idle,
            )
        }
        composeTestRule.captureSkinK(theme, fontScale, goldenName("DiscoveryResultScreen", caseId, theme, fontScale)) { _ ->
            DiscoveryResultContent(
                ctx = RESULT_CONTEXT,
                state = state,
                onUp = {},
                onEditConditions = {},
                onOpenDetail = {},
                onChangeOrder = {},
                onChangeGenreFilter = { _, _ -> },
                onRefresh = {},
                onLoadMore = {},
            )
        }
    }

    companion object {
        private const val CASE_LIST = "list"
        private const val CASE_EMPTY = "empty"

        /**
         * SEARCH 由来＝「条件を変更」導線と空状態 CTA が出る唯一の source（GENRE/MOOD/KEYWORD だと
         * 戻り先に条件シートが無いため別文言に倒れる）。query は条件チップが7個並ぶよう厚めに積む
         * ＝FlowRow の折り返しが 2.0 で何段になるかを見るのがこの case の主眼。
         */
        private val RESULT_CONTEXT = ResultContext(
            title = "「廃鉱山 付与術」の検索結果",
            subtitle = "タイトル・あらすじから",
            source = ResultSource.SEARCH,
            query = DiscoveryQuery(
                order = NarouOrder.WEEKLY,
                word = "廃鉱山 付与術",
                notWord = "ハーレム",
                inTitle = true,
                inStory = true,
                genres = setOf(DiscoveryScreenshotFixtures.GENRE_CODE_LONGEST),
                lastups = setOf(NarouLastup.SEVENDAY),
                length = "100000-",
                time = "-600",
            ),
        )

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_LIST, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_EMPTY, ReadingTheme.LIGHT, s))
            }
        }
    }
}
