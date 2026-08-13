package com.novelreader.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import org.robolectric.RuntimeEnvironment

/**
 * Roborazzi スクリーンショットテストの共有基盤（ADR 0009 増補1）。
 *
 * なぜスクリーンショットテストを足すか: 「ライトとセピアが同色」級のテーマ退行・トークン変更の
 * 意図せぬ波及・フォントスケール拡大時のレイアウト破綻を機械検知するため（ADR 0014 §A の
 * 「④コードは③モックとの乖離をスクリーンショットで検出する」層）。ピクセル単位の意匠美の
 * 判定は対象外（そこは ADR 0005 §B の実機フィードバック後詰め層のまま）。
 *
 * なぜ既定ゲート（testDebugUnitTest）に golden 比較を載せないか: Roborazzi はシステムプロパティ
 * （roborazzi.test.record/verify）未指定時は captureRoboImage が no-op になる標準挙動のため、素の
 * testDebugUnitTest では比較が走らず既存ゲートの速度・安定性を保てる。記録は recordRoborazziDebug、
 * 検証は verifyRoborazziDebug の明示実行運用とする（ADR 0009 増補1・ゲート方針）。
 *
 * golden の置き場は src/test/screenshots/（build/ 配下でなく git 追跡する参照画像）。
 * JVM のフォントレンダリングは環境依存のため golden は WSL(Linux) 記録を正とする（ADR 0009 増補1）。
 */
internal object ScreenshotConfig {
    /** golden PNG の格納先。Robolectric 単体テストの作業ディレクトリ（app/）からの相対。 */
    const val SCREENSHOT_DIR = "src/test/screenshots"

    /** テーマ × フォントスケールのマトリクス。テーマ退行検知のため 3 テーマ全てを回す。 */
    val THEMES = listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK)

    /** 1.0=既定、2.0=拡大時のレイアウト破綻検知。 */
    val FONT_SCALES = listOf(1.0f, 2.0f)

    /** ParameterizedRobolectricTestRunner 用: 全テーマ × 全スケールの直積。 */
    fun matrix(): List<Array<Any>> =
        THEMES.flatMap { theme ->
            FONT_SCALES.map { scale -> arrayOf<Any>(theme, scale) }
        }

    fun themeLabel(theme: ReadingTheme): String = theme.name.lowercase()

    /** ファイル名の scale 部（"1.0" / "2.0"）。 */
    fun scaleLabel(scale: Float): String = scale.toString()
}

/**
 * golden ファイル名の共通部（`<画面>_<状態>_<テーマ>_<スケール>.png`）。既存 golden の命名規約に揃える。
 * スキン非依存の純粋な整形＝スキン別の support ではなくここに置く（K/D 双方の状態別 golden が使う）。
 */
internal fun goldenName(screen: String, caseId: String, theme: ReadingTheme, fontScale: Float): String =
    "${screen}_${caseId}_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"

/**
 * 指定テーマ・フォントスケールで content を描画し、golden PNG を記録/検証する。
 *
 * - NovelReaderTheme(theme) で包む＝Material colorScheme・ShelfColors をテーマ追従させる。
 * - 読書系 Composable は colors: ReadingColors も要るため theme.colors を content へ渡す。
 * - フォントスケールは LocalDensity を上書きして与える（density は端末値を維持し fontScale だけ変える）。
 *   なぜ @Config(qualifiers) でなく LocalDensity 上書きか: リソース修飾子には fontScale の直接指定が
 *   無く、Compose の sp 換算に確実・局所的に効くこの方式が安定するため。
 */
internal fun ComposeContentTestRule.captureThemed(
    theme: ReadingTheme,
    fontScale: Float,
    fileName: String,
    content: @Composable (ReadingColors) -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            NovelReaderTheme(theme = theme) {
                content(theme.colors)
            }
        }
    }
    onRoot().captureRoboImage(
        filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName",
    )
}

/**
 * ダイアログ（[com.novelreader.ui.theme.NovelReaderAlertDialog]＝M3 `AlertDialog`）を撮る。
 * [captureThemed] / [captureSkinned] とは**2点だけ**違い、どちらもダイアログが別ウィンドウで描かれることに起因する。
 *
 * ### 1) フォントスケールは `LocalDensity` 上書きでは効かない → Configuration へ入れる
 * `AlertDialog` の中身は `Dialog` が起こす**別ウィンドウ**（`DialogLayout`＝`AbstractComposeView`）の
 * サブコンポジションで描かれる。この View 自身が `LocalDensity` を **自分の Context の
 * `resources.displayMetrics.density` × `configuration.fontScale`** から再提供するため、
 * 呼び出し元コンポジションで積んだ `LocalDensity` は**上書きされて届かない**。
 * 実測（2026-08-07・本ヘルパ導入時の probe）: [captureThemed] と同じ `Density(fontScale=2.0f)` の張り方で
 * 電池最適化ダイアログを撮ると、1.0 と 2.0 の PNG が**バイト単位で同一**になった＝拡大破綻は原理的に写らない。
 * そこで Robolectric の Configuration そのものを動かす [RuntimeEnvironment.setFontScale] を使う
 * （実機で OS が fontScale を配る経路と同じ＝ホスト窓とダイアログ窓の双方に一様に効く）。
 * この呼び出しは `setContent` より前でなければならない（Compose が窓の Density を解決するのが合成時のため）。
 *
 * ### 2) 撮る先は `onRoot()` でなくダイアログ窓
 * `onRoot()` は「root がちょうど1つ」を要求するが、ダイアログを出した時点で root はホスト窓と
 * ダイアログ窓の2つになり得る。どちらが取れるかに依存させず [isDialog] で窓を名指しする。
 * 撮れる絵はスクリム込みの全画面＝ダイアログが画面のどこにどれだけの大きさで載るかまで含む
 * （拡大時に器から溢れたか／ボタンが押し出されたかは、この構図でこそ読める）。
 *
 * ### なぜ「ホスト画面ごと」でなく「ダイアログ単体」を撮るか
 * 本棚のダイアログ群は [com.novelreader.ui.BookshelfScreen] のルート層が `viewModel` の状態と
 * ローカル `remember` で開閉する＝ホストごと撮るには実 VM（DB・Service・権限）を立ち上げ、
 * さらに UI 操作で各ダイアログを開かせる必要があり、撮りたい版面と無関係な配線でテストが折れる。
 * 中身だけを実物の入口（`NovelReaderAlertDialog`）で組む流儀は既に
 * [ReadingSettingsSheetScreenshotTest] が `ModalBottomSheet` に対して採っている前例と同じ。
 * ⚠️ 代償として**文言はテスト側の写し**になるため、本番との一致は
 * [com.novelreader.ui.screenshot.BookshelfDialogTextFidelityTest] が機械照合する（宣言だけの約束にしない）。
 */
internal fun ComposeContentTestRule.captureDialogSkinned(
    skin: Skin,
    theme: ReadingTheme,
    fontScale: Float,
    fileName: String,
    dialog: @Composable () -> Unit,
) {
    RuntimeEnvironment.setFontScale(fontScale)
    setContent {
        NovelReaderTheme(skin = skin, theme = theme) {
            dialog()
        }
    }
    onNode(isDialog()).captureRoboImage(
        filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName",
    )
}

/**
 * 任意スキンで content を描画し、golden PNG を記録/検証する（M/J などスキン固有トークン直参照の画面用）。
 *
 * なぜ [captureThemed]／K 版 captureSkinK と別に要るか: M/J の目次は golden 0枚で、K/D と同型の
 * 現在地バー破綻（監査 2026-08-06 G-1・fontScale 2.0 で進捗文が章一覧を押し出す）が絵の回帰に一切
 * かからなかった。captureThemed はスキン既定（D）で包み、K 版は [ReadingColors] の解決まで含む K 専用形。
 * M/J 画面は colors 引数を持たない（スキン固有トークンを直参照する）ため、撮る対象と同じ入口
 * （NovelReaderTheme に当該 skin）で包むだけのこの素の形を使う。
 */
internal fun ComposeContentTestRule.captureSkinned(
    skin: Skin,
    theme: ReadingTheme,
    fontScale: Float,
    fileName: String,
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        // フォントスケールだけ差し替える（density は端末値を維持）＝captureThemed と同方式。
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            NovelReaderTheme(skin = skin, theme = theme) {
                content()
            }
        }
    }
    onRoot().captureRoboImage(
        filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName",
    )
}
