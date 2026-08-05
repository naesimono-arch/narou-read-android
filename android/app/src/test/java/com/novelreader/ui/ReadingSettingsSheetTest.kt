package com.novelreader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.CompositionLocalProvider
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ReadingSettingsSheetContent（表示設定シートの内容）の表示＋テーマ選択コールバックのテスト（ADR 0009）。
 * なぜシート枠（ModalBottomSheet）でなく Content を対象にするか: 枠は別ウィンドウ描画＋部分展開で
 * 下部が画面外に出るため、Robolectric では可視判定・クリック注入が不安定（実測で assertIsDisplayed／
 * performClick 経由の結線検証が落ちた）。テストの狙いは state+callback の葉である内容の検証なので、
 * 枠を剥がした Content を直接組む。値ラベルの表示（fontSize/lineHeight/margin の写経が崩れて
 * いないこと）とチップ結線という退行が痛い箇所に集中する（過剰網羅は避ける）。
 */
@RunWith(RobolectricTestRunner::class)
// 実端末相当の画面（スクショテストと同一 w360dp-h640dp）で組む。素の既定画面は縦が短く、縦書きトグル
// 追加後はシート内容（非スクロールの Column）が画面外へあふれ末尾スライダー値の可視判定が落ちるため、
// スクショ回帰と同じ現実的な縦寸で全設定行が収まる前提を揃える。
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ReadingSettingsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private fun setSheet(
        readingTheme: ReadingTheme = ReadingTheme.LIGHT,
        onThemeChange: (ReadingTheme) -> Unit = {},
        verticalMode: Boolean = false,
        onVerticalModeChange: (Boolean) -> Unit = {},
        // スキンM の意匠分岐（テーマ固定表示・星のつまみ）検証用。既定 D＝既存テストは完全不変。
        skin: Skin = Skin.WAMODERN_D,
        // J の「システムに従う」（扉プレビュー下の追従入口）結線検証用。既定 false/空＝既存テストは不変。
        followingSystem: Boolean = false,
        onFollowSystem: () -> Unit = {},
        // 案3ライブプレビューの押下行通知（押下=該当行・解放=null）の契約検証用。既定 no-op＝既存テストは不変。
        onAdjustingRowChange: (ReadingSettingsAdjustingRow?) -> Unit = {},
    ) {
        composeTestRule.setContent {
            // LocalSkin（意匠分岐）と LocalSkinTokens（テーマ節の畳み判定）は本番では対で供給される。
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                ReadingSettingsSheetContent(
                    colors = colors,
                    readingTheme = readingTheme,
                    onThemeChange = onThemeChange,
                    followingSystem = followingSystem,
                    onFollowSystem = onFollowSystem,
                    fontSize = 18,
                    onFontSizeChange = {},
                    onFontSizePersist = {},
                    lineHeightEm = 2.5f,
                    onLineHeightChange = {},
                    onLineHeightPersist = {},
                    bodyMarginDp = 20,
                    onBodyMarginChange = {},
                    onBodyMarginPersist = {},
                    verticalMode = verticalMode,
                    onVerticalModeChange = onVerticalModeChange,
                    onAdjustingRowChange = onAdjustingRowChange,
                )
            }
        }
    }

    /** スライダーは文字ラベルを持たないため ProgressBarRangeInfo（現在値・レンジ・steps）で一意特定する。 */
    private fun sliderNode(current: Float, range: ClosedFloatingPointRange<Float>, steps: Int) =
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(current, range, steps),
            ),
        )

    @Test
    fun `M装着ではテーマ3択の代わりに固定表示行を出しスライダー値は不変`() {
        setSheet(skin = Skin.SEIZU_M)
        // 固定表示（settings-M .theme-fixed）＝何が装着されているか＋変種切替の所在。
        composeTestRule.onNodeWithText("星図 ・ 夜の相").assertIsDisplayed()
        // 入口移管（2026-07-29 本棚→設定タブ「きせかえ」）後の実導線を指す文言であること。
        composeTestRule.onNodeWithText("ほかの装いは設定の「きせかえ」から").assertIsDisplayed()
        // 3択チップは出ない（1変種＝押しても変わらないチップを出さない）。
        composeTestRule.onNodeWithText("ライト").assertDoesNotExist()
        // ロジック共有の証左＝スライダー現在値は D と同一書式のまま。
        composeTestRule.onNodeWithText("18sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.5").assertIsDisplayed()
    }

    @Test
    fun `P装着では標準テーマ3択を出す（M固定表示行と違い壊さない）`() {
        // P は supportedThemes=3（ADR 0022 §2 追記）＝標準3択が出る。M の固定表示行分岐に流れないこと。
        setSheet(skin = Skin.CARTRIDGE_P)
        composeTestRule.onNodeWithText("表示設定").assertIsDisplayed()
        composeTestRule.onNodeWithText("ライト").assertIsDisplayed()
        composeTestRule.onNodeWithText("セピア").assertIsDisplayed()
        composeTestRule.onNodeWithText("ダーク").assertIsDisplayed()
        // P のシステムメニューヘッダ（settings-P .sysbar）が出る。
        composeTestRule.onNodeWithText("POCKET NOVEL").assertIsDisplayed()
        // ロジック共有の証左＝スライダー現在値は D と同一書式のまま。
        //（P はシステムメニュー面がヘッダぶん高く、本文余白スライダー "20dp" は Robolectric の 470px 窓の
        //   外に出るため assertExists で存在のみ確認する＝視認は 18sp/2.5 で担保。実機のシートはスクロールする）。
        composeTestRule.onNodeWithText("18sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("20dp").assertExists()
    }

    @Test
    fun `見出しとテーマ3択と各スライダーの現在値を表示する`() {
        setSheet()
        composeTestRule.onNodeWithText("表示設定").assertIsDisplayed()
        composeTestRule.onNodeWithText("ライト").assertIsDisplayed()
        composeTestRule.onNodeWithText("セピア").assertIsDisplayed()
        composeTestRule.onNodeWithText("ダーク").assertIsDisplayed()
        // 現在値ラベル（写経した書式のまま出ること）
        composeTestRule.onNodeWithText("18sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("20dp").assertIsDisplayed()
    }

    @Test
    fun `テーマチップのタップでonThemeChangeが該当テーマで呼ばれる`() {
        var picked: ReadingTheme? = null
        setSheet(onThemeChange = { picked = it })
        composeTestRule.onNodeWithText("セピア").performClick()
        assertEquals(ReadingTheme.SEPIA, picked)
    }

    @Test
    fun `縦書きトグルの見出しとチップと現在値を表示する`() {
        setSheet()
        composeTestRule.onNodeWithText("本文の向き").assertIsDisplayed()
        composeTestRule.onNodeWithText("縦書き").assertIsDisplayed()
        // trailing の現在値（モック settings-D 案C・2026-08-06 裁定）: 横書き中は節ラベル右端に「横書き」。
        composeTestRule.onNodeWithText("横書き").assertIsDisplayed()
    }

    @Test
    fun `縦書きON時はtrailing現在値が縦書きへ変わる`() {
        // ON では現在値も「縦書き」になりチップ label と文言が重複するため、現在値側の検証は
        // 「横書きの不在」＋「縦書き2ノード（チップ＋現在値）」で行う（onNodeWithText の一意性を保つ）。
        setSheet(verticalMode = true)
        composeTestRule.onNodeWithText("横書き").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("縦書き").assertCountEquals(2)
    }

    @Test
    fun `縦書きチップのタップでonVerticalModeChangeが反転値で呼ばれる`() {
        var toggled: Boolean? = null
        // 現在 OFF（横書き）→ タップで ON（縦書き）を要求する反転値が飛ぶ。
        setSheet(verticalMode = false, onVerticalModeChange = { toggled = it })
        composeTestRule.onNodeWithText("縦書き").performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun `スライダー押下中は該当行を通知し解放でnullへ戻る（案3ライブプレビュー契約）`() {
        val events = mutableListOf<ReadingSettingsAdjustingRow?>()
        setSheet(onAdjustingRowChange = { events += it })
        val fontSlider = sliderNode(current = 18f, range = 14f..24f, steps = 9)
        // M3 Slider の押下検出は DragInteraction 経由＝touch slop を超える移動でドラッグ開始させる
        // （down だけでは PressInteraction が emit されない現行実装のため）。
        fontSlider.performTouchInput {
            down(center)
            moveBy(Offset(viewConfiguration.touchSlop + 24f, 0f))
        }
        composeTestRule.runOnIdle {
            assertEquals(ReadingSettingsAdjustingRow.FONT_SIZE, events.last())
        }
        // 押下中もレイアウトは不変（退避は graphicsLayer alpha のみ）＝他節のノードは存在し続ける。
        composeTestRule.onNodeWithText("テーマ").assertExists()
        composeTestRule.onNodeWithText("行間").assertExists()
        // 解放＝復帰。通知は null へ戻る。
        fontSlider.performTouchInput { up() }
        composeTestRule.runOnIdle { assertNull(events.last()) }
    }

    @Test
    fun `行間スライダー押下はLINE_HEIGHT行として通知される（行の対応関係）`() {
        val events = mutableListOf<ReadingSettingsAdjustingRow?>()
        setSheet(onAdjustingRowChange = { events += it })
        sliderNode(current = 2.5f, range = 2.3f..2.8f, steps = 4).performTouchInput {
            down(center)
            moveBy(Offset(viewConfiguration.touchSlop + 24f, 0f))
        }
        composeTestRule.runOnIdle {
            assertEquals(ReadingSettingsAdjustingRow.LINE_HEIGHT, events.last())
        }
    }

    @Test
    fun `J装着では扉プレビュー3択＋システムに従うを出す（settings-J）`() {
        // J は supportedThemes=3（ADR 0022 §2）＝扉プレビューの3択が出る。M の固定表示行分岐に流れないこと。
        setSheet(skin = Skin.PORTAL_J)
        composeTestRule.onNodeWithText("表示設定").assertIsDisplayed()
        composeTestRule.onNodeWithText("ライト").assertIsDisplayed()
        composeTestRule.onNodeWithText("セピア").assertIsDisplayed()
        composeTestRule.onNodeWithText("ダーク").assertIsDisplayed()
        // D 機能の J 意匠移植＝OS 明暗への自動追従へ戻す入口。
        composeTestRule.onNodeWithText("システムに従う").assertIsDisplayed()
        // ロジック共有の証左＝スライダー現在値は D と同一書式のまま。
        //（J の扉プレビュー3択＋追従入口は縦に高く、スライダー値は Robolectric の 470px 窓の外に出るため
        //   assertExists で存在のみ確認する＝P 装着テストの "20dp" と同じ扱い。実機のシートはスクロールする）。
        composeTestRule.onNodeWithText("18sp").assertExists()
        composeTestRule.onNodeWithText("2.5").assertExists()
    }

    @Test
    fun `J装着の扉タップでonThemeChangeが該当テーマで呼ばれる`() {
        var picked: ReadingTheme? = null
        setSheet(skin = Skin.PORTAL_J, onThemeChange = { picked = it })
        composeTestRule.onNodeWithText("セピア").performClick()
        assertEquals(ReadingTheme.SEPIA, picked)
    }

    @Test
    fun `J装着のシステムに従うタップでonFollowSystemが呼ばれる`() {
        var followed = false
        setSheet(skin = Skin.PORTAL_J, onFollowSystem = { followed = true })
        composeTestRule.onNodeWithText("システムに従う").performClick()
        assertEquals(true, followed)
    }
}
