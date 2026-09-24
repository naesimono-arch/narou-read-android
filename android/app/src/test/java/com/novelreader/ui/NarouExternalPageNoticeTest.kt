package com.novelreader.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.novelreader.PrefKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * なろう外部ページの一度きり注意喚起（ADR 0042 決定1＝案A）の意味論を固定する。
 *
 * ## 固定したい穴
 * 1. **抑止フラグが効かない**（＝毎回出る／逆に一度も出ない）。永続フラグ絡みの退行は例外もログも出さず、
 *    「なんとなく出ない」形でしか現れないので、テストが唯一の番人になる。
 * 2. **チェックしていないのに焼かれる**。読み落とした人に二度と届かなくなる方向の退行で、
 *    症状が「出ない」だけなので実機検分でも気づけない。
 * 3. **文言が ADR の禁止則へ戻る**。とくに「ふりがなは付かず」は**事実誤認**（なろうのページには作者の
 *    ルビがそのまま出る＝効かないのはアプリ側の機能）で、ADR 0042 がその是正のために立った当のもの。
 *    語を戻す変更はコンパイルも既存テストも通ってしまうため、ここで字面を張る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NarouExternalPageNoticeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val prefs: SharedPreferences by lazy {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
    }

    @Before
    fun clearPrefs() {
        // Robolectric のアプリ状態はテスト間で共有されうる。抑止フラグは「一度立てば以後ずっと」の
        // 性質なので、前のテストの残留が次のテストを偽GREEN にする（出ないのが正しいのか残留なのか
        // 区別できなくなる）。毎回まっさらから始める。
        prefs.edit().clear().commit()
    }

    /**
     * 入場のたびに [NarouExternalPageNoticeHost] を作り直す器。
     * `key` を変えると内部の rememberSaveable が別スロットになる＝「WebView 画面へ入り直した」に相当する。
     */
    private fun setContentWithReentry(): () -> Unit {
        var round by mutableIntStateOf(0)
        composeTestRule.setContent {
            MaterialTheme {
                key(round) { NarouExternalPageNoticeHost(prefs) }
            }
        }
        // 再入場はコンポジションの外（テストスレッド）から起こすので runOnIdle 経由で入れる
        // ＝再コンポーズが済んでから assert に進む。
        return { composeTestRule.runOnIdle { round += 1 } }
    }

    // ── 文言（ADR 0042 決定2 の3要素と禁止則）─────────────────────────────

    @Test
    fun `本文は主語をアプリ側へ寄せる（「ふりがなは付かず」は事実誤認なので戻さない）`() {
        val body = NarouExternalPageNotice.BODY
        assertFalse(
            "なろうのページには作者のルビがそのまま出る＝「ふりがなは付かず」は嘘",
            body.contains("ふりがなは付か"),
        )
        assertTrue("効かないのはアプリ側のふりがな機能、と読める必要がある", body.contains("ふりがな機能"))
        assertTrue("アプリの操作・表示設定も効かないことを言う", body.contains("アプリの操作・表示設定"))
    }

    @Test
    fun `なろうの表示のまま出ることを言い、かつ「広告」は名指ししない`() {
        val body = NarouExternalPageNotice.BODY
        assertTrue("無加工で出すことを言う", body.contains("そのまま表示"))
        assertFalse(
            "名指しすると〈場所の案内〉が〈警告〉へ変質する（ADR 0042 決定2）",
            body.contains("広告"),
        )
    }

    @Test
    fun `見出しは境界を言う（移動すると書かない＝アプリ内 WebView のままなので嘘になる）`() {
        val title = NarouExternalPageNotice.TITLE
        assertTrue("相手の場所の名を名乗る", title.contains("小説家になろう"))
        assertTrue("境界を言う語は「ここからは」", title.startsWith("ここからは"))
        assertFalse("移動しない", (title + NarouExternalPageNotice.BODY).contains("移動"))
    }

    // ── 抑止フラグ ─────────────────────────────────────────────────

    @Test
    fun `未抑止なら注意喚起が出る`() {
        setContentWithReentry()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.BODY).assertIsDisplayed()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.SUPPRESS_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.CONFIRM_LABEL).assertIsDisplayed()
    }

    @Test
    fun `チェックして閉じると抑止フラグが立ち、2回目以降は出ない`() {
        val reenter = setContentWithReentry()

        composeTestRule.onNodeWithText(NarouExternalPageNotice.SUPPRESS_LABEL).performClick()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.CONFIRM_LABEL).performClick()

        assertTrue(
            "「次回から表示しない」を選んで閉じたら永続する",
            prefs.getBoolean(PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED, false),
        )
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertDoesNotExist()

        reenter()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertDoesNotExist()
    }

    @Test
    fun `チェックせずに閉じた回は焼かない（次の入場でもう一度届く）`() {
        val reenter = setContentWithReentry()

        composeTestRule.onNodeWithText(NarouExternalPageNotice.CONFIRM_LABEL).performClick()

        assertFalse(
            "読み落とした人へもう一度届く余地を残す",
            prefs.getBoolean(PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED, false),
        )
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertDoesNotExist()

        reenter()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertIsDisplayed()
    }

    @Test
    fun `抑止済みの端末では入場しても最初から出ない`() {
        prefs.edit().putBoolean(PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED, true).commit()
        setContentWithReentry()
        composeTestRule.onNodeWithText(NarouExternalPageNotice.TITLE).assertDoesNotExist()
    }

    @Test
    fun `判定と永続化は単一結節点にある（画面を経由せずに固定する）`() {
        assertTrue("既定は出す", NarouExternalPageNotice.shouldShow(prefs))
        NarouExternalPageNotice.suppress(prefs)
        assertFalse("抑止後は出さない", NarouExternalPageNotice.shouldShow(prefs))
    }
}
