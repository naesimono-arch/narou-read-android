package com.novelreader.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.discovery.NovelDetailContent
import com.novelreader.ui.skins.k.captureRoot
import com.novelreader.ui.skins.k.setSkinKContent
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.NovelDetailUiState
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 作品詳細（nav ルート `discovery/detail/{ncode}`＝[com.novelreader.ui.discovery.NovelDetailScreen]）の
 * スクリーンショット回帰。**監査 2026-08-06 G-8 で 0枚だったさがす配下の面**を埋める束（優先度2位）。
 *
 * ### なぜ K 素地で包むか（captureThemed でなく setSkinKContent）
 * この画面はスキン分岐を持たない共有実装だが、出荷時に載る素地は既定スキン＝[com.novelreader.ui.theme.Skin.MEIKAI_K]
 * （ADR 0027 の初回公開スコープ）。[captureThemed] は `NovelReaderTheme` の既定 skin（WAMODERN_D）で包むため、
 * 画面が読む LocalShelfColors（末尾メタの infoText）が実際の出荷素地と別の束になる。現時点で K は D へ全委譲＝
 * 色値は一致するが、K がパレットを分けた瞬間に golden が静かに実画面から乖離する（K support の KDoc と同じ判断）。
 *
 * ### なぜ VM でなく [NovelDetailContent] を撮るか
 * ルート層は `viewModel.load(ncode)` で実 API を叩き、`fetchedAtMillis` に**実時刻**が入る＝絵が撮るたび変わる。
 * 描画層は state と callback だけの葉なので、状態を固定して撮れる（既存の分割方針そのままの利用＝
 * [ReadingSettingsSheetScreenshotTest] がシート中身を直接組むのと同型）。
 *
 * ### 決定性のための TimeZone 固定
 * 末尾の「HH:mm 時点の情報」は `SimpleDateFormat` が **JVM 既定 TimeZone** を読む。golden は WSL(Linux) 記録を
 * 正とするが、CI コンテナが UTC・開発機が JST という差だけで絵が変わり、誰も触っていないのに赤くなる。
 * `fetchedAtMillis` の固定だけでは足りないため、TimeZone も Asia/Tokyo へ固定して比較対象から外す。
 *
 * ### 撮る case と選定理由
 *  - `content`（3テーマ×2スケール全数）＝上端。書影ヒーロー＋作者/ジャンル行＋ステータス2×2表＋固定バー。
 *    fontScale 2.0 で最初に壊れるのはここで、**長作者名（15字）×最長ジャンルラベル「ヒューマンドラマ」**の
 *    組が作者行の `weight(1f, fill=false)` 防御を試し、**"約41時間（123.5万字）"** が `IntrinsicSize.Min` で
 *    組んだ2×2表の列幅主張を試す。短い既定値（題名 "t"・作者 "w"）で撮ると 1.0 と 2.0 の差がほぼ出ず、
 *    走査 `tools/check_golden_*.py`（1.0 と 2.0 の対で判定）が原理的に何も拾えない。
 *  - `story`（ライトのみ×2スケール）＝あらすじ〜キーワードまで送った版。上端 golden の画角外にある
 *    約210字の本文ブロックと 24個のタグ FlowRow がここにしか写らない。既存流儀どおり追加状態はライトのみ
 *    （色トークンは `content` 側が張る）で、狙いは折り返し段数の退行だけに絞る。
 *
 * 撮っていないもの（既知の穴）: Loading / NotFound / Error の3状態、取込済み（`isImported=true`）の
 * 固定バー分岐、本棚トグルの on 状態。いずれも版面が小さく、拡大破綻の主戦場ではないため後回し。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NovelDetailScreenScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun pinTimeZone() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    }

    @After
    fun restoreTimeZone() {
        // 既定 TimeZone は JVM 全体の状態＝戻さないと同一 JVM で後続するテストへ漏れる。
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun capture() {
        composeTestRule.setSkinKContent(theme, fontScale) { _ ->
            NovelDetailContent(
                ncode = Ncode(NCODE),
                uiState = NovelDetailUiState.Content(
                    novel = DiscoveryScreenshotFixtures.detail(),
                    // 固定 epoch ＋ TimeZone 固定で末尾メタを「20:00 時点の情報」に一意化する。
                    fetchedAtMillis = FETCHED_AT_MILLIS,
                ),
                onSearchKeywords = {},
                onImportPdf = {},
                onUp = {},
                onRetry = {},
                onReadOnNarou = {},
            )
        }

        if (caseId == CASE_STORY) {
            // 最後のタグまで送る＝あらすじブロックとタグ FlowRow の全段が画角に入ることの担保。
            // 「キーワード」見出しへ送るだけだと見出しが下端に来てチップが1つも写らない
            //（送り先を末尾要素にするのは DiscoveryHomeKScreenshotTest の ranking case と同じ考え方）。
            composeTestRule.onNodeWithText(LAST_KEYWORD).performScrollTo()
            composeTestRule.waitForIdle()
            // スクロールが空振りすると golden が「上端と同じ絵」に化け、以後この case は
            // どんな退行も検出しなくなる（＝撮っているのに守らない golden）。
            composeTestRule.onNodeWithText(LAST_KEYWORD).assertIsDisplayed()
        }

        composeTestRule.captureRoot(goldenName("NovelDetailScreen", caseId, theme, fontScale))
    }

    companion object {
        private const val CASE_CONTENT = "content"
        private const val CASE_STORY = "story"
        /** 書影の地色は bookId のハッシュ由来（[com.novelreader.ui.components.BookCover]）＝ncode 固定で決定的。 */
        private const val NCODE = "N9876AB"

        private const val FETCHED_AT_MILLIS = 1_767_265_200_000L // 2026-01-01 20:00 JST

        /** [DiscoveryScreenshotFixtures.MANY_KEYWORDS] の最終トークン。 */
        private const val LAST_KEYWORD = "完結済み"

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_CONTENT, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_STORY, ReadingTheme.LIGHT, s))
            }
        }
    }
}
