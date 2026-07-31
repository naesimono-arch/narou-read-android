package com.novelreader.ui.skins.k

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.screenshot.ScreenshotConfig
import com.novelreader.ui.screenshot.goldenName
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「設定」（[SettingsScreenK]）のスクリーンショット回帰（ADR 0009 増補1）。
 *
 * K で新設され恒常ナビの3目的地の一つに昇格した画面＝出荷時に必ず開かれる面（ADR 0027 の単独公開
 * スコープ）だが golden が無かった。意匠は MaterialTheme の colorScheme/typography 追従＝
 * 「ライトとセピアが同色」級のテーマ退行がそのまま出る面でもある。
 *
 * 撮る状態と選定理由: 3変種スキン（K）・システム追従オフ・通知トグル OFF（既定＝オプトイン）。
 *  ・3変種スキンを選ぶ理由: テーマ行が「畳んだ固定表示」でなく〈現在値＋右矢印〉の可変行として出る側＝
 *    K 自身の実状態。単一変種（M/C）の畳み表示は K の出荷面には現れない。
 *  ・通知 OFF を選ぶ理由: 既定値であり、prefs 未設定の実機初回起動と一致する（ON の絵は別の状態＝
 *    Switch の塗りだけの差分のため代表からは外す）。
 *
 * 既知の再記録トリガ（偽陽性ではなく「意図した変更」として扱うもの）:
 *  ・versionName の改訂: 「バージョン」行は BuildConfig.VERSION_NAME をそのまま描くため、採番
 *    （ADR 0025）で値が動くと golden も動く。テスト側からは差し替えられない（本番コードが直接読む）。
 *  ・BuildConfig.DEBUG: debug ユニットテストでは true 固定のため「データ（取り込み状態の診断）」節が
 *    golden に含まれる。release では消える節＝この golden は debug 版の面を固定している。
 *
 * このテストが赤くなる条件:
 *  ・見出し「設定」の字面/余白、グループ見出し（表示/通知/データ/このアプリ）の語彙と間隔
 *  ・カード面（surface＋outlineVariant 1dp 枠・影0）とその角丸
 *  ・行の構造（アイコン24dp・アイコン無し行のテキスト開始位置 S40 揃え・説明文の有無・trailing）
 *  ・テーマ行の現在値表記（ライト/セピア/ダーク・システム追従時の文言）
 *  ・きせかえ行の説明文に埋まる現在スキン名（"明快"）
 *  ・通知行のトグル位置と説明文
 *  ・colorScheme（surface/onSurfaceVariant/outlineVariant）・typography の値変更
 *  ・fontScale 2.0 で行が2行化し版面が伸びる/切り詰まる変化
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureSkinK(theme, fontScale, goldenName("SettingsScreenK", "default", theme, fontScale)) { _ ->
            // SettingsScreenK 自身は背景を持たない（実アプリでは NavHost 側の面に載る）ため、
            // テーマ素地を敷いて版面として捉える（ReadingSettingsSheetScreenshotTest と同じ扱い）。
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SettingsScreenK(
                    // 現在テーマ＝描画テーマと一致させる（設定画面が自分の状態を正しく映していることごと固定）。
                    appTheme = theme,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    // 公開スコープ機能ゲート（ADR 0027）は on 側で撮る＝この golden は debug 版の面を固定する
                    // （off 側＝きせかえ行が消えた面は画素でなく構造で縛る＝SettingsScreenKSkinGateTest）。
                    skinSwitchingEnabled = true,
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
