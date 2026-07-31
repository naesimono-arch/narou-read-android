package com.novelreader.ui.skins.k

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.novelreader.PrefKeys
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.rememberReadingColors
import org.robolectric.RuntimeEnvironment

/**
 * 明快K（[Skin.MEIKAI_K]＝既定スキン・ADR 0027 で初回公開の単独スコープ）画面群のスクリーンショット
 * 回帰の共有基盤。土台（golden の置き場・テーマ×スケール行列・no-op 挙動・記録運用）は
 * [ScreenshotConfig] / ui/screenshot/ScreenshotTestSupport.kt が正本＝ここでは K 固有の差分だけを足す。
 *
 * K 固有の差分は2点だけ:
 *  1) [NovelReaderTheme] へ skin=[Skin.MEIKAI_K] を渡す。既存 captureThemed は skin 既定（D）で包むため、
 *     K 画面が読む [com.novelreader.ui.theme.LocalSkin] / LocalSkinTokens / LocalShelfColors /
 *     LocalShioriColors が D のまま供給される＝実画面と違う束で撮ってしまう。
 *     （現時点で SkinK は SkinD へ全委譲のため色値は一致するが、K がパレットを分けた瞬間に
 *       golden が実画面から静かに乖離する。撮る対象と同じ入口で包むのが正しい。）
 *  2) content へ渡す [ReadingColors] を D 固定アクセサ（ReadingTheme.colors）でなく
 *     [rememberReadingColors]（LocalSkinTokens 経由＝実画面と同じ引き方）で解決する。同上の理由。
 *
 * 撮影前に操作（スクロール等）が要る画面のために [setSkinKContent] と [captureRoot] を分けて公開し、
 * 一発で足りる画面は既存流儀どおり [captureSkinK] 1本で書ける形を保つ。
 */
internal fun ComposeContentTestRule.setSkinKContent(
    theme: ReadingTheme,
    fontScale: Float,
    content: @Composable (ReadingColors) -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        // フォントスケールだけ差し替える（density は端末値を維持）＝ScreenshotTestSupport と同方式。
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = theme) {
                content(rememberReadingColors(theme))
            }
        }
    }
}

/** 現在のルート描画を golden として記録/検証する（比較の有無は Roborazzi のシステムプロパティ次第＝既存流儀）。 */
internal fun ComposeContentTestRule.captureRoot(fileName: String) {
    onRoot().captureRoboImage(filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName")
}

/** [setSkinKContent] → [captureRoot] の定型（撮影前の操作が要らない画面用）。 */
internal fun ComposeContentTestRule.captureSkinK(
    theme: ReadingTheme,
    fontScale: Float,
    fileName: String,
    content: @Composable (ReadingColors) -> Unit,
) {
    setSkinKContent(theme, fontScale, content)
    captureRoot(fileName)
}

/**
 * K 本棚のグリッド⇄リスト表示状態を先置きする。
 *
 * なぜ prefs 直書きか: この状態は K 自身が [PrefKeys.K_GRID_VIEW] で所有し（skins/ShelfViewToggle・
 * 2026-07-27 移設）、引数では渡せないため。既存の BookshelfLogMTest（M_SKY_VIEW）と同じ先置き流儀。
 * apply でなく commit＝合成前に確実に効かせる。
 */
internal fun setKGridView(isGrid: Boolean) {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PrefKeys.K_GRID_VIEW, isGrid)
        .commit()
}
