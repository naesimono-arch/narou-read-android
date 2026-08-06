package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.ProcessingBanner
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.ProcessingSource
import com.novelreader.viewmodel.ProcessingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 処理中バナー（[ProcessingBanner]）のスクリーンショット回帰。
 *
 * ## なぜ張るか（2026-08-07 の文言棚卸し）
 * 本棚の最上段に常駐するこの器は golden を**1枚も持っていなかった**のに、中身は
 * 「1行固定の題名（`maxLines=1` + Ellipsis）」「2行までの phase 行」「件数バッジ」「停止ボタン」が
 * **1つの Row を奪い合う**構造で、fontScale 2.0 では題名がほぼ消え、phase の末尾（ページ数）が
 * 落ちる方向へ壊れる。phase の 2行許容は「読み込み中のページ数が読めない」という実使用の不満に
 * 応えた仕様（ProcessingBanner.kt のコメント）なので、拡大時にページ数が切れることは**仕様の失効**
 * そのものであり、絵で固定していないと誰も気づかない。
 *
 * ## 撮る case
 *  - `pdf`（**代表**・3テーマ×2スケール全数）: 供給元 PDF＝4段ステッパー・進捗バー・「ステップ n/4」まで
 *    出る最も詰まった状態。題名は実在しうる長さ、phase はページ数付き、件数バッジ（2/5件）と停止ボタンも出す。
 *  - `web`（ライト×2スケール）: 供給元 WEB＝ステッパー3要素を出さない分岐（2026-07-29 裁定②の是正結果）。
 *    「PDF 専用の器を Web にも描いていた」退行が戻ったらこの絵が変わる。
 *  - `stopping`（ライト×2スケール）: 停止操作後＝主見出しが「停止しています…」へ替わり停止ボタンが消える分岐。
 * 追加 case をライトのみにするのは既存の張り方（BookshelfD/K）と同じ理由＝色トークンは代表 case が張る。
 *
 * スキンは K 固定（ADR 0027 で初回公開スコープは明快K 単独＝出荷する唯一の面。バナーの色は
 * `colorScheme.primaryContainer` 系＝スキンが供給するため、撮る対象と同じ入口で包む）。
 * ゲート非同乗（testDebugUnitTest では captureRoboImage が no-op）の理由は ScreenshotTestSupport.kt を参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ProcessingBannerScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val state = when (caseId) {
            CASE_WEB -> WEB_STATE
            CASE_STOPPING -> PDF_STATE.copy(isStopping = true)
            else -> PDF_STATE
        }
        composeTestRule.captureSkinned(
            skin = Skin.MEIKAI_K,
            theme = theme,
            fontScale = fontScale,
            fileName = goldenName("ProcessingBannerK", caseId, theme, fontScale),
        ) {
            // バナーは自前の面色（primaryContainer）だけを持ち素地を持たない＝棚の背景を敷いて対比ごと撮る。
            // 幅は本番と同じく親が決める（ProcessingBanner の root は幅を固定しない契約）。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ProcessingBanner(
                    processingState = state,
                    onStop = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    companion object {
        private const val CASE_PDF = "pdf"
        private const val CASE_WEB = "web"
        private const val CASE_STOPPING = "stopping"

        /**
         * PDF 変換中の worst case。題名は 1行に収まらない長さ（省略記号の位置が退行の目印）、
         * phase は生成元 PdfBookExtractor が実際に出す「…45%（3/12ページ）」形＝末尾のページ数が
         * 切られていないかを見る。queueTotal>1 で件数バッジも器の取り合いに参加させる。
         */
        private val PDF_STATE = ProcessingState(
            isProcessing = true,
            stepIndex = 1,
            stepTotal = 4,
            stepLocalPercent = 0.45f,
            phase = "本文を読み込んでいます… 45%（3/12ページ）",
            title = "転生したら剣でした 〜Sランク冒険者と魔剣の物語〜",
            queueCurrent = 2,
            queueTotal = 5,
            source = ProcessingSource.PDF,
        )

        /** Web 取込中。章単位取得なのでステップ概念を持たない（phase の章進行だけが進む）。 */
        private val WEB_STATE = ProcessingState(
            isProcessing = true,
            phase = "章 37/214 取得中",
            title = "無職転生 〜異世界行ったら本気だす〜",
            source = ProcessingSource.WEB,
        )

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_PDF, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_WEB, ReadingTheme.LIGHT, s))
                add(arrayOf<Any>(CASE_STOPPING, ReadingTheme.LIGHT, s))
            }
        }
    }
}
