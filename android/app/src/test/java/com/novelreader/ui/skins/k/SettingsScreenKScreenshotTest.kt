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
 * ### 追加 case `followsystem`（ライトのみ×2スケール・2026-08-17）
 * `followingSystem=true` のときテーマ行の trailing は「システムに従う」＝**この行に出る文言の最長**
 * （既定 case の「ライト」等は3字）。値が行幅を先取りして title「テーマ」を潰す破綻はこの worst case
 * でしか写らない（既定 case では原理的に再現しない）。テーマ全数を撮らないのは色トークンが `default` と
 * 共通で、テーマ退行はそちらの束が張るため（ChapterHeader・TocSkyM と同じ「拡大破綻の軸だけを張る束」）。
 *
 * この case の 2.0 は**初回記録（2026-08-17）で「テーマ」が `…` に潰れた壊れた絵を焼いていた**が、
 * 同日の裁定（値に上限を付けて行名に幅を予約する＝モック案B）で是正し再記録済み＝現在の golden は
 * 「テーマ」が読め、値が「システ…」に縮んだ絵。**画素はこの状態を退行から守るだけで「何が正しいか」は
 * 言わない**ので、行名が読めること自体は [SettingsRowWidthLayoutTest] がレイアウト値で固定している
 * （`docs/knowledge/golden-record-bakes-in-regressions.md` の型＝画素だけに頼らない）。
 *
 * 既知の再記録トリガ（偽陽性ではなく「意図した変更」として扱うもの）:
 *  ・versionName の改訂: 「バージョン」行は BuildConfig.VERSION_NAME をそのまま描くため、採番
 *    （ADR 0025）で値が動くと golden も動く。テスト側からは差し替えられない（本番コードが直接読む）。
 *  ・BuildConfig.DEBUG: debug ユニットテストでは true 固定のため「データ」節の**2行目**
 *    〈取り込み状態の診断〉が golden に含まれる。release ではこの行だけが消える。
 *    ⚠️ 2026-09-03 にゲートを節ごとから行単位へ掛け替えた（正本 settings-K.html の申し送り）＝
 *    節と1行目〈診断の記録〉は release でも出る。旧記述「節ごと消える」はこの改訂で無効。
 *
 * このテストが赤くなる条件:
 *  ・見出し「設定」の字面/余白、グループ見出し（表示/通知/データ/つかいかた/このアプリ）の語彙と間隔
 *  ・「データ」節の行構成（〈診断の記録〉＝release でも出る／〈取り込み状態の診断〉＝debug のみ）
 *  ・カード面（surface＋outlineVariant 1dp 枠・影0）とその角丸
 *  ・行の構造（アイコン24dp・アイコン無し行のテキスト開始位置 S40 揃え・説明文の有無・trailing）
 *  ・テーマ行の現在値表記（ライト/セピア/ダーク・システム追従時の文言）
 *  ・きせかえ行の右端に出る現在スキン名（"明快"）と説明文の1行化（値は副文でなく trailing＝モック `.rv`）
 *  ・通知行のトグル位置と説明文
 *  ・colorScheme（surface/onSurfaceVariant/outlineVariant）・typography の値変更
 *  ・fontScale 2.0 で行が2行化し版面が伸びる/切り詰まる変化
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureSkinK(theme, fontScale, goldenName("SettingsScreenK", caseId, theme, fontScale)) { _ ->
            // SettingsScreenK 自身は背景を持たない（実アプリでは NavHost 側の面に載る）ため、
            // テーマ素地を敷いて版面として捉える（ReadingSettingsSheetScreenshotTest と同じ扱い）。
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SettingsScreenK(
                    // 現在テーマ＝描画テーマと一致させる（設定画面が自分の状態を正しく映していることごと固定）。
                    appTheme = theme,
                    onThemeChange = {},
                    followingSystem = caseId == CASE_FOLLOW_SYSTEM,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    // 「データ」節の〈診断の記録〉行の飛び先。この観点では叩かないので no-op。
                    onOpenDiagnosticsExport = {},
                    // 公開スコープ機能ゲート（ADR 0027）は on 側で撮る＝この golden は debug 版の面を固定する
                    // （off 側＝きせかえ行が消えた面は画素でなく構造で縛る＝SettingsScreenKSkinGateTest）。
                    skinSwitchingEnabled = true,
                )
            }
        }
    }

    companion object {
        private const val CASE_DEFAULT = "default"
        private const val CASE_FOLLOW_SYSTEM = "followsystem"

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_DEFAULT, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_FOLLOW_SYSTEM, ReadingTheme.LIGHT, s))
            }
        }
    }
}
