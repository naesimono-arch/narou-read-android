package com.novelreader.ui.skins.k

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
// onNode は SemanticsNodeInteractionsProvider のメソッド＝top-level import 不可（rule 経由で呼ぶ）
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.novelreader.backup.BackupOptIn
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 設定「データ」節の引き継ぎ2行（正本モック `skins/candidates/settings-backup-row-candidates.html` 案C-2）。
 *
 * ## 何を守るテストか
 * agent 本体（restricted mode）は JVM で再現できないので、ここで固定するのは**利用者から見える側**:
 *  1. 行が release でも出る（この2行は `BuildConfig.DEBUG` ゲートの外＝節の他の行と露出条件が違う）。
 *  2. **既定が OFF**——裁定の中身そのもの。UI 側が既定値を独自に持ってしまうと、
 *     [BackupOptIn.DEFAULT_ENABLED] を変えても画面だけ古い既定のままになる。
 *  3. トグルの操作が **agent が読むのと同じ置き場**（app_prefs）へ着く。ここが切れると
 *     「UI では ON なのに一生バックアップされない」という無音の不一致になる。
 *  4. 説明行が説明ダイアログへ着く（叩けない山括弧＝案B から引き継いだ「事故の前に伝える」導線）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKBackupRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    private companion object {
        const val TOGGLE_LABEL = "読書記録の引き継ぎ"
        const val INFO_LABEL = "引き継がれるもの"
    }

    @Before
    fun clear() {
        BackupOptIn.prefs(appContext).edit().clear().commit()
    }

    private fun setSettings() {
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    onOpenDiagnosticsExport = {},
                    skinSwitchingEnabled = true,
                )
            }
        }
    }

    /** モックの merge 済み行を祖先に持つ toggleable＝節内の他の Switch（通知）と取り違えない指定。 */
    private fun toggle() = composeTestRule
        .onNode(isToggleable() and hasAnyAncestor(hasText(TOGGLE_LABEL, substring = true)))

    @Test
    fun `2行とも「データ」節に出る（debug ゲートの外）`() {
        setSettings()
        // assertIsDisplayed でなく assertExists: 設定リストは長く 640dp ビューポートの外に置かれうる。
        composeTestRule.onNodeWithText("データ").assertExists()
        composeTestRule.onNodeWithText(TOGGLE_LABEL).assertExists()
        composeTestRule.onNodeWithText(INFO_LABEL).assertExists()
    }

    @Test
    fun `既定は OFF（キーが無い端末＝初回起動と同じ状態）`() {
        setSettings()
        toggle().performScrollTo().assertIsOff()
    }

    @Test
    fun `保存済みの ON が初期状態に反映される`() {
        BackupOptIn.setEnabled(appContext, true)
        setSettings()
        toggle().performScrollTo().assertIsOn()
    }

    @Test
    fun `トグルの操作が agent の読む置き場へ着く`() {
        setSettings()
        toggle().performScrollTo().performClick()
        assertTrue(
            "UI が ON になっても app_prefs へ落ちていなければ、agent は永遠に OFF と判断する",
            BackupOptIn.isEnabled(appContext),
        )
        toggle().performClick()
        assertFalse(BackupOptIn.isEnabled(appContext))
    }

    @Test
    fun `説明行から引き継ぎの説明ダイアログが開く`() {
        setSettings()
        composeTestRule.onNodeWithText(INFO_LABEL).performScrollTo().performClick()
        composeTestRule.onNodeWithText("端末を替えたときの引き継ぎ").assertExists()
        // 「本文は引き継がれません」を事故の前に伝えるのがこのダイアログの存在理由
        //（設計ドラフト §3.3 結論②＝機種変更で PDF 本の本文は戻らない）。文言が痩せたら落とす。
        composeTestRule.onNodeWithText("引き継がれないもの").assertExists()
    }
}
