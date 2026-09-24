package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.screenshot.BookshelfDialogFixtures as Fx
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 本棚の長文 Snackbar のスクリーンショット回帰。
 *
 * ## なぜ張るか（2026-08-07 の文言棚卸し）
 * 取込元PDF削除の失敗通知（[com.novelreader.viewmodel.BookshelfViewModel.deleteBooks] →
 * `emitError`）は **63字**あり、しかも `transient=false` の既定経路なので「閉じる」アクション付きで
 * 出る＝1行の器に長文とボタンが同居する。ダイアログ群と同様 golden は0枚だった。
 * fontScale 2.0 では M3 の Snackbar が本文とアクションの取り合いで折り返す／切れる方向に壊れ得るのに、
 * その条件は一度も観測されていなかった。
 *
 * ## 撮り方（`SnackbarHost` でなく [Snackbar] を直接組む）
 * `SnackbarHost` は表示を `FadeInFadeOutWithScale` のアニメーションで包むため、Robolectric では
 * 撮影時点の進行度が確定せず絵が不安定になる（同じ理由でシートも枠でなく中身を撮っている＝
 * [ReadingSettingsSheetScreenshotTest]）。守りたいのは**器の版面**（長文とアクションの折り合い）なので、
 * `SnackbarHost` の既定 content がそのまま呼ぶ [Snackbar]`(snackbarData)` を同じ引数で組む。
 * K の `SnackbarHost` は content を差し替えていない（BookshelfK.kt）ので、これは本番と同一の描画経路。
 * 配置（BottomCenter・下 16dp）も K の `SnackbarHost` の modifier に合わせる。
 *
 * 代表 case は1つだけなので 3テーマ×2スケール全数で撮る（`GoldenCoverageTest` のマトリクス検査の要件でもある）。
 * スキンは K 固定（ADR 0027 の出荷面）。ゲート非同乗の理由は ScreenshotTestSupport.kt を参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ShelfSnackbarScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureSkinned(
            skin = Skin.MEIKAI_K,
            theme = theme,
            fontScale = fontScale,
            fileName = goldenName("ShelfSnackbarK", CASE_DELETE_FAIL, theme, fontScale),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Snackbar(
                    snackbarData = FixedSnackbarData(
                        FixedSnackbarVisuals(Fx.SNACKBAR_DELETE_FAIL, Fx.SNACKBAR_ACTION),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.S16),
                )
            }
        }
    }

    /** `SnackbarHostState.showSnackbar` が組み立てるのと同じ内容を固定値で与える写し。 */
    private class FixedSnackbarVisuals(
        override val message: String,
        override val actionLabel: String?,
    ) : SnackbarVisuals {
        // 本番の errorEvents 収集は withDismissAction を使わず actionLabel だけを付ける（BookshelfScreen.kt）。
        override val withDismissAction: Boolean = false
        // actionLabel 付きは Material3 の既定で Indefinite（案d の残留バグの根拠と同じ）。版面には影響しない。
        override val duration: SnackbarDuration = SnackbarDuration.Indefinite
    }

    private class FixedSnackbarData(override val visuals: SnackbarVisuals) : SnackbarData {
        override fun performAction() = Unit
        override fun dismiss() = Unit
    }

    companion object {
        private const val CASE_DELETE_FAIL = "delete_source_failed"

        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
