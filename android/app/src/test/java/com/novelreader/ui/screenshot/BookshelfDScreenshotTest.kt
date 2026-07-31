package com.novelreader.ui.screenshot

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.PrefKeys
import com.novelreader.ui.BookshelfContent
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.skins.k.KShelfFixtures
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 和モダンD／夜行C 共通描画の「本棚」（[BookshelfContent] の D/C 経路）のスクリーンショット回帰。
 *
 * なぜ張るか（2026-07-31 に判明した golden の空白）: `src/test/screenshots/` には `BookshelfK_*` しか無く、
 * **D/C の本棚は絵での回帰が一切効いていなかった**。実際この空白のせいで、K で見つけた
 * 「状態行が可視⋮の 32dp ぶん幅不足で折り返す」欠陥と**同型の欠陥が D 側に残っていた**
 * （2026-07-30 に K を是正した便では D は無検査のまま通り続け、翌日に別途発見された）。
 * `ui/GridStatusLineWrapTest.kt` が D・K 双方の状態行を `lineCount==1` で固定してはいるが、
 * それは1つの主張の点検査で、版面全体（列数・書影比・キャプション構成・チップ・空状態）の絵ではない。
 *
 * 撮る状態は [com.novelreader.ui.skins.k.BookshelfKScreenshotTest] と**同一の4状態**にする
 * （grid_mixed / list_mixed / empty / grid_missing）。データも同じ [KShelfFixtures] を使う:
 * K と D は同じ蔵書を別の版面で描く関係にあり、**入力を共有してはじめて2組の golden の差が
 * 「版面の差」だけを表す**。入力を D 用に作り直すと、K で見つけた欠陥と同型の欠陥が
 * D 側にあっても再現しない組合せになりかねない（上記の実例がまさにそれ）。
 * ⚠️ したがって [KShelfFixtures] を編集すると K と D の golden が同時に動く。
 *
 * 横向き（K の [com.novelreader.ui.skins.k.BookshelfKLandscapeScreenshotTest]）に対応する D 版は**作らない**:
 * あの golden は「縦2列／横5列」という **K 固有の裁定**（`LocalConfiguration.orientation` 分岐）を守るもので、
 * D/C にはその分岐自体が無く（`Adaptive(124dp)` の素の追従）、守るべき裁定が存在しないため。
 *
 * テーマ×スケールの張り方は K と同じ方針: 代表状態 grid_mixed だけ 3テーマ × fontScale{1.0,2.0} の全数、
 * 追加状態はライトのみ（色トークンは全カード共通で grid_mixed が張る＝同じ束を撮り直しても新しい退行は出ない）。
 *
 * このテストが赤くなる条件:
 *  ・D グリッドの列数／書影のアスペクト比・角丸・影／キャプション行の構成（著者＋可視⋮）
 *  ・状態行がキャプション行の**外**（カード全幅）に置かれていること＝欠落文言「本文なし・タップで再取込」の折り返し
 *  ・可視⋮の位置と大きさ（`CardMenuTapSize` とキャプション行の最小高が同値であること）
 *  ・進捗行・相対時刻・続きバッジ／Web未取込カードの署名／リスト（文字目録）行の骨格
 *  ・空状態（`EmptyBookshelf`）の見出し・説明・CTA の構図
 *  ・TopAppBar の題字＋冊数のベースライン揃えと表示切替アイコン（⋮ が復活したらここに出る）
 *  ・状態フィルタチップの形・選択塗り・0件時の不活性
 *  ・上記に効くトークン（colorScheme・ShelfColors・ShioriColors・Spacing・Font*）の値変更
 *  ・fontScale 2.0 でのはみ出し／切り詰め挙動の変化
 *
 * ゲート非同乗（testDebugUnitTest では captureRoboImage が no-op）の理由は ScreenshotTestSupport.kt を参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class BookshelfDScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        // グリッド⇄リストは D 描画部が prefs で所有する（引数では渡せない）＝先置きで面を選ぶ。
        setDGridView(caseId != CASE_LIST)
        val data = when (caseId) {
            CASE_EMPTY -> KShelfFixtures.emptyData()
            CASE_MISSING -> KShelfFixtures.missingContentData()
            else -> KShelfFixtures.mixedData()
        }

        composeTestRule.captureThemed(theme, fontScale, goldenName("BookshelfD", caseId, theme, fontScale)) {
            // D は Scaffold で自前の背景と TopAppBar を持つ＝素地を敷き足さない。
            // 恒常ボトムナビは NavHost の外（MainActivity）が描くため本画面には含まれない＝実画面と同じ。
            BookshelfContent(
                uiState = BookshelfUiState.Content(
                    books = data.books,
                    webNovels = data.webNovels,
                    webReadingProgress = data.webReadingProgress,
                    webLastReadAt = data.webLastReadAt,
                ),
                progressMap = data.progressMap,
                chapterCountMap = data.chapterCountMap,
                newEpisodeNovelMap = data.newEpisodeNovelMap,
                processingState = ProcessingState(),
                actions = KShelfFixtures.actions,
                webActions = KShelfFixtures.webActions,
                // テーマ4択の⋮は D から撤去済み（2026-07-24 K形伝播）だが束の契約は残る＝現在値だけ正しく渡す。
                theme = ThemeControl(
                    appTheme = theme,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                ),
                onDeleteBooks = { _, _ -> },
                snackbarHostState = remember { SnackbarHostState() },
                reimportPlans = data.reimportPlans,
            )
        }
    }

    companion object {
        private const val CASE_GRID = "grid_mixed"
        private const val CASE_LIST = "list_mixed"
        private const val CASE_EMPTY = "empty"

        /** 欠落カード。状態行が最長になる状態＝K で実害が出た折り返しの検査点（[KShelfFixtures] 参照）。 */
        private const val CASE_MISSING = "grid_missing"

        /**
         * D 本棚のグリッド⇄リスト表示状態を先置きする（K の `setKGridView` と同じ流儀・キーだけ D のもの）。
         * apply でなく commit＝合成前に確実に効かせる。
         */
        private fun setDGridView(isGrid: Boolean) {
            RuntimeEnvironment.getApplication()
                .getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PrefKeys.IS_GRID_VIEW, isGrid)
                .commit()
        }

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            // 代表状態＝全テーマ×全スケール（テーマ退行と拡大破綻の両軸）。
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_GRID, t, s)) }
            }
            // 追加状態＝ライトのみ×2スケール（骨格の退行検知に限定）。
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_LIST, ReadingTheme.LIGHT, s))
                add(arrayOf<Any>(CASE_EMPTY, ReadingTheme.LIGHT, s))
            }
            // 欠落カードは等倍のみ＝実機で折り返した条件（360dp・fontScale 1.0）そのものを固定する。
            add(arrayOf<Any>(CASE_MISSING, ReadingTheme.LIGHT, 1.0f))
        }
    }
}
