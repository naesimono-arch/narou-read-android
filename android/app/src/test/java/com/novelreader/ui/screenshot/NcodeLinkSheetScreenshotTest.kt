package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.ui.NcodeLinkSheetContent
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.NcodeSearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * なろう紐付けシート（[com.novelreader.ui.NcodeLinkSheet]）の中身のスクリーンショット回帰。
 * **監査 2026-08-06 G-8 で 0枚だった面**の残り（読書ルート配下の被せもの）。
 *
 * `ModalBottomSheet` の枠を剥がした [NcodeLinkSheetContent] を撮る理由は本番側 KDoc に書いた
 * （Robolectric では枠が別ウィンドウ＋部分展開になり下端が絵に乗らない）。既存の
 * [ReadingSettingsSheetScreenshotTest] と同じ流儀・同じ [captureThemed]（スキン既定＝D の素地）で包む
 * ——このシートは読書ルート（NativeReadingScreen）から出る面で、K のさがす系ではないため。
 *
 * ### 撮る case と選定理由
 *  - `candidates`（3テーマ×2スケール全数）＝候補リストが出た状態。**このシートで最も器が詰まる状態**で、
 *    検索欄・候補行（題名1行 ellipsis ＋ 作者・状態の情報行）・手動 N コード欄・紐付けボタンが
 *    同じ Column を奪い合う。題名は書籍化前の長題（37字＝[DiscoveryScreenshotFixtures.LONG_TITLE]）を
 *    含めて `maxLines=1` の省略が効いていることごと固定する。手動 N コード欄は妥当値を入れて
 *    「紐付け」ボタンの**有効色**側を撮る（無効色は下の `error` case が撮る）。
 *  - `error`（ライトのみ×2スケール）＝通信失敗の再試行面。監査 2026-08-06 根因④で
 *    「固定 height 120dp だと 2.0 で再試行ボタンのタップ標的ごと欠ける」を `heightIn(min=120.dp)` へ
 *    直した箇所そのもの＝**是正が退行で巻き戻ったら絵で分かる**ようにする。テーマ全数を撮らないのは
 *    色トークンが candidates 側と同一で、テーマ退行はそちらの束が張るため。
 *
 * 撮っていないもの（既知の穴）: Loading（"検索中..." の1行のみ＝器の破綻が起きる構造を持たない）、
 * 候補ゼロの Success（同じく1行）。
 *
 * ### 器が溢れたときの見え方（2026-08-17 に是正済み）
 * 初回記録時の 2.0 は「壊れた絵」だった＝中身の Column に `verticalScroll` が無く、長書名で補足文が
 * 膨らむと手動 N コード欄・「紐付け」・「再試行」が画面外へ押し出されて到達不能だった。器
 * （[com.novelreader.ui.NcodeLinkSheetContent] のスクロール）を足して再記録済み。以後この束は
 * **「溢れてもスクロール可能域の先頭が正しく描かれる」**ことを張る＝器が外れて内容が押し出される
 * 退行はここで赤くなる（絵が変わるのが期待どおりな場面の判別は
 * `docs/knowledge/golden-record-bakes-in-regressions.md`）。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NcodeLinkSheetScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val state = if (caseId == CASE_ERROR) ERROR_STATE else CANDIDATES_STATE
        // 手動入力欄は case で撮り分ける（妥当な N コード＝ボタン有効色／空＝プレースホルダと無効色）。
        val manual = if (caseId == CASE_ERROR) "" else "N9876AB"
        composeTestRule.captureThemed(
            theme,
            fontScale,
            goldenName("NcodeLinkSheet", caseId, theme, fontScale),
        ) { colors ->
            // シートの中身は自前で背景を持たない（本番では ModalBottomSheet の containerColor が敷く）ため、
            // テーマ素地を敷いて版面として捉える（ReadingSettingsSheetScreenshotTest と同じ扱い）。
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                NcodeLinkSheetContent(
                    bookTitle = BOOK_TITLE,
                    searchState = state,
                    colors = colors,
                    // 検索欄の初期値＝本番と同じく書名（長題がそのまま入る＝singleLine の詰まりを撮る）。
                    inputText = BOOK_TITLE,
                    onInputTextChange = {},
                    manualNcode = manual,
                    onManualNcodeChange = {},
                    onSearch = {},
                    onRetry = {},
                    onConfirm = {},
                )
            }
        }
    }

    companion object {
        private const val CASE_CANDIDATES = "candidates"
        private const val CASE_ERROR = "error"

        /** 補足文「「〜」の続きを…」に長題が入る＝2.0 で何行に膨らむかを見る。 */
        private const val BOOK_TITLE = DiscoveryScreenshotFixtures.LONG_TITLE

        private val CANDIDATES_STATE = NcodeSearchUiState.Success(
            // 長題・完結済・短編・話数欠損なしの混在＝行高が揃わない方向の退行も1枚で見える。
            DiscoveryResult(allcount = 4, novels = DiscoveryScreenshotFixtures.resultRows()),
        )

        /** 本番の文言に合わせた通信失敗文（2.0 で折り返して再試行ボタンを押し下げる長さ）。 */
        private val ERROR_STATE = NcodeSearchUiState.Error("通信に失敗しました。電波の良い場所で再試行してください。")

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_CANDIDATES, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_ERROR, ReadingTheme.LIGHT, s))
            }
        }
    }
}
