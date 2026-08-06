package com.novelreader.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * reduce-motion 判定（`theme/ReduceMotion.kt`）が**設定変更に即座に追従する**ことを固定する回帰テスト。
 *
 * 何を守るテストか（監査 2026-08-06 C2 の真因）: 以前は 8 画面がそれぞれ
 * `remember { Settings.Global.getFloat(...) == 0f }` とキー無しで読んでいた。この設定の変更は
 * 構成変更を伴わない＝Activity 再生成も再コンポーズも起きないため、値は初回コンポーズのまま凍り、
 * 「アニメーションを削除」を後から ON にしてもプロセスを殺すまで効かなかった。
 * **判定源をキー無し remember へ戻すと本テストは赤くなる**（= 凍結の再発検知器）。
 *
 * 両方向（OFF→ON と ON→OFF）を測るのは、片道だけ通る実装（初期値が偶然一致しているだけ）で
 * 緑にならないようにするため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReduceMotionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `設定のONOFFが再起動を待たずに反映される`() {
        val context: Context = RuntimeEnvironment.getApplication()
        setAnimatorDurationScale(context.contentResolver, 1f)

        composeTestRule.setContent {
            Text(if (rememberReduceMotion()) REDUCED else NORMAL)
        }
        composeTestRule.onNodeWithText(NORMAL).assertIsDisplayed()

        // OFF→ON（支援を必要とする人が実際に踏む向き）。
        setAnimatorDurationScale(context.contentResolver, 0f)
        settle()
        composeTestRule.onNodeWithText(REDUCED).assertIsDisplayed()

        // ON→OFF も届く（購読が一方向でないこと）。
        setAnimatorDurationScale(context.contentResolver, 1f)
        settle()
        composeTestRule.onNodeWithText(NORMAL).assertIsDisplayed()
    }

    @Test
    fun `上流が提供した値が下流の判定に勝つ`() {
        val context: Context = RuntimeEnvironment.getApplication()
        // 端末設定は「動かす」側に置いたまま、上流（MainActivity 相当）が true を配る。
        // 下流が自前で端末設定を読み直していたら false になり落ちる＝配布経路が単一情報源であることの固定。
        setAnimatorDurationScale(context.contentResolver, 1f)
        var provided by mutableStateOf(true)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides provided) {
                Text(if (rememberReduceMotion()) REDUCED else NORMAL)
            }
        }
        composeTestRule.onNodeWithText(REDUCED).assertIsDisplayed()

        // 上流の値の変化も下流へ流れる（root の 1 購読で全画面が切り替わること）。
        provided = false
        settle()
        composeTestRule.onNodeWithText(NORMAL).assertIsDisplayed()
    }

    @Test
    fun `判定式はアニメーターの尺0だけをreduceとみなす`() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        setAnimatorDurationScale(resolver, 0f)
        assertTrue(readReduceMotion(resolver))
        setAnimatorDurationScale(resolver, 1f)
        assertFalse(readReduceMotion(resolver))
        // 0.5 倍速（「アニメを速くする」設定）は reduce ではない＝止めない。
        setAnimatorDurationScale(resolver, 0.5f)
        assertFalse(readReduceMotion(resolver))
    }

    /**
     * 設定値を書き、変更通知を流す。
     *
     * notifyChange を明示するのは、Robolectric の `Settings.Global.putFloat` が実機の SettingsProvider と違い
     * 観測者へ通知を出すとは限らないため（テストが実装依存で緑/赤に揺れない）。通知は登録 URI 単位で届くので、
     * 購読側が別の URI を見ていれば届かない＝配線の固定にもなる。
     */
    private fun setAnimatorDurationScale(resolver: ContentResolver, scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    /** ContentObserver は main Looper の Handler 経由で届くため、compose を待つ前に Looper を空にする。 */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val REDUCED = "reduced"
        const val NORMAL = "normal"
    }
}
