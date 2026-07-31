package com.novelreader.ui.skins.k

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.domain.ReadingStatus
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.screenshot.goldenName
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「本棚」（[BookshelfK]）のスクリーンショット回帰（ADR 0009 増補1）。
 *
 * なぜ K の本棚に golden が要るか: K は既定スキンで、ADR 0027 により初回公開は K 単独＝**出荷される
 * 画面**。にもかかわらず既存 golden は D/共通部品（ContinuationCard・ReadingSettingsSheet 等）に
 * 集中しており、実際にユーザーが最初に見る面は無検査だった。
 *
 * 撮る状態と選定理由:
 *  - grid_mixed（既定のグリッド・蔵書3冊＋Web未取込1件）＝K の顔。読了/未読/よみかけ/Web未取込の
 *    4種の描き分けが1枚に同居し、2列固定・書影 3:4・キャプション1行 clamp・可視⋮・状態行・
 *    フィルタチップの活性/不活性・拡張FABまで同時に固定できる（状態1つでは大半が無検査に残る）。
 *  - list_mixed（リスト＝案A 題字1行）＝グリッドとはレイアウトが別物（色帯・行高≈71dp・下ヘアライン・
 *    Web行の破線フレーム・続きバッジ）。グリッド golden では一切カバーされない第2の版面。
 *  - empty（空棚）＝初回起動で最初に見える面。CTA2つの並びは拡大時に折り返す危険がある。
 *  - grid_missing（欠落カード）＝状態行が K で最長になる唯一の状態「本文なし・タップで再取込」。
 *    2026-07-30 の実機でここだけが2列グリッドで折り返しており、既存3状態はどれもこの文言を持たない
 *    ＝**状態行がカード全幅を得ているか**を検査できる唯一の絵（可視⋮の 32dp を奪われると赤くなる）。
 *
 * テーマ×スケールの張り方（枚数を無闇に増やさないための方針）:
 *  代表状態 grid_mixed は既存流儀どおり 3テーマ × fontScale{1.0,2.0} の全数。
 *  追加状態（list_mixed・empty）はライトのみ × 2スケール＝**レイアウト骨格の退行検知に絞る**
 *  （色トークンは全カード共通で grid_mixed が張っており、同じ束をテーマ3種で撮り直しても
 *   新しい退行は捉えられないため）。
 *
 * このテストが赤くなる条件（＝常に緑にならないことの担保）:
 *  ・グリッドの列数（縦2列）・書影のアスペクト比/角丸/影・キャプションの行数や字面（明朝1行 ellipsis）
 *  ・状態行の語彙と徴（「読了」／藍ドット＋「未読」／「第N/M話」／欠落「本文なし・タップで再取込」）
 *  ・状態行がキャプション行の**外**（カード全幅）に置かれていること・可視⋮の位置と大きさ
 *  ・キャプション行の最小高（⋮のタップ面と同値＝選択モードで⋮が消えてもカード高が変わらない）
 *  ・フィルタチップの形（丸ピル）・選択塗り・0件時の淡色不活性
 *  ・Web未取込の署名（青磁1.5dp破線・紙地5%沈め・「なろう・未取込」の色と太さ）
 *  ・リスト行の高さ・左端4dp色帯・メタ行の連結（著者・状態・続きN話バッジ）・下ヘアライン
 *  ・空状態の見出し/説明文/CTA2つ（藍実塗り＋輪郭）の構図
 *  ・ヘッダ（「本棚」＋冊数のベースライン揃え＋表示切替アイコンの図柄）
 *  ・上記に効くトークン（colorScheme・ShelfColors.infoText・ShioriColors・Spacing・Font*）の値変更
 *  ・fontScale 2.0 でのはみ出し/切り詰め挙動の変化
 * 逆に、これらを一切変えないリファクタでは緑のまま通る。
 *
 * ゲート非同乗（testDebugUnitTest では captureRoboImage が no-op）の理由は
 * ui/screenshot/ScreenshotTestSupport.kt を参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class BookshelfKScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        // グリッド⇄リストは K 自身が prefs で所有する＝引数でなく先置きで面を選ぶ。
        setKGridView(caseId != CASE_LIST)
        val data = when (caseId) {
            CASE_EMPTY -> KShelfFixtures.emptyData()
            CASE_MISSING -> KShelfFixtures.missingContentData()
            else -> KShelfFixtures.mixedData()
        }
        // 型を明示する（emptyMap() 側の型引数を if 式の推論任せにしない）。
        val counts: Map<ReadingStatus, Int> =
            if (caseId == CASE_EMPTY) emptyMap() else KShelfFixtures.mixedStatusCounts

        composeTestRule.captureSkinK(theme, fontScale, goldenName("BookshelfK", caseId, theme, fontScale)) { _ ->
            // K は自前で背景（colorScheme.background）と statusBarsPadding を持つ＝素地を敷き足さない。
            // 恒常ボトムナビは NavHost の外（MainActivity）が描くため本画面には含まれない＝実画面と同じ。
            BookshelfK(
                data = data,
                chrome = KShelfFixtures.chrome(counts),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    companion object {
        private const val CASE_GRID = "grid_mixed"
        private const val CASE_LIST = "list_mixed"
        private const val CASE_EMPTY = "empty"

        /** 欠落カード（案B）。状態行が K で最長になる唯一の状態＝折り返しの検査点（KShelfFixtures 参照）。 */
        private const val CASE_MISSING = "grid_missing"

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            // 代表状態＝全テーマ×全スケール（テーマ退行と拡大破綻の両軸）。
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_GRID, t, s)) }
            }
            // 追加状態＝ライトのみ×2スケール（骨格の退行検知に限定・上のクラスコメント参照）。
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_LIST, ReadingTheme.LIGHT, s))
                add(arrayOf<Any>(CASE_EMPTY, ReadingTheme.LIGHT, s))
            }
            // 欠落カードは等倍のみ＝実機で折り返した条件（360dp・fontScale 1.0）そのものを固定する。
            // 2.0 では K の状態行はどの文言でも折り返す（幅の問題ではない）ため、ここでは撮らない。
            add(arrayOf<Any>(CASE_MISSING, ReadingTheme.LIGHT, 1.0f))
        }
    }
}
