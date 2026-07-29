package com.novelreader.ui

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.discovery.KotlinSourceScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 読書ウィンドウ契約（09-D バー契約 / 09-F 消灯抑止）の所有権を固定する。
 *
 * 固定したい真因（2026-07-29 実機確認）: この契約を章スコープ（ChapterScreen）が DisposableEffect で
 * 所有していたため、章送りでは〈新章の入場エフェクト → 旧章の onDispose〉の順に走り、後から走る旧章の
 * 後始末（keepScreenOn=false／systemBars を show）が勝って没入と消灯抑止が章のたびに壊れていた。
 * 直し方は所有権の移動（画面スコープ＝ReadingScreen）なので、テストも「どこが持っているか」を固定する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingWindowContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var view: View? = null

    @Test
    fun `章表示中は消灯抑止が立ち目次では下りる`() {
        var showingChapter by mutableStateOf(true)
        composeTestRule.setContent {
            view = LocalView.current
            ReadingWindowContract(showingChapter = showingChapter)
        }

        composeTestRule.runOnIdle { assertTrue(view!!.keepScreenOn) }
        composeTestRule.runOnIdle { showingChapter = false }
        // 目次は没入対象外＝抑止を外す（従来 ChapterScreen の onDispose が担っていた復帰の移設先）。
        composeTestRule.runOnIdle { assertFalse(view!!.keepScreenOn) }
    }

    @Test
    fun `読書画面を離れたら消灯抑止を必ず戻す`() {
        var mounted by mutableStateOf(true)
        composeTestRule.setContent {
            view = LocalView.current
            if (mounted) ReadingWindowContract(showingChapter = true)
        }

        composeTestRule.runOnIdle { assertTrue(view!!.keepScreenOn) }
        composeTestRule.runOnIdle { mounted = false }
        composeTestRule.runOnIdle { assertFalse(view!!.keepScreenOn) }
    }

    @Test
    fun `章サブコンポジションを入れ替えても消灯抑止が外れない`() {
        var file by mutableStateOf("chap_1.html")
        composeTestRule.setContent {
            view = LocalView.current
            // ReadingScreen と同じ配置＝契約は章の AnimatedContent の「外」に置く。
            // 章スコープに置くと入れ替えのたびに破棄→再生成が起き、退場側の後始末が入場側に勝つ。
            ReadingWindowContract(showingChapter = true)
            AnimatedContent(
                targetState = file,
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                label = "chapter",
            ) { f -> Text(f) }
        }

        composeTestRule.runOnIdle { assertTrue(view!!.keepScreenOn) }
        composeTestRule.runOnIdle { file = "chap_2.html" }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { assertTrue(view!!.keepScreenOn) }
    }

    /**
     * 所有権そのものの退行検知（上の振る舞いテストだけでは「章スコープが再び所有し直す」退行を捕まえられない
     * ため、ソースの形で固定する）。走査根が解けないときは黙って PASS させず必ず fail させる。
     */
    @Test
    fun `ウィンドウ資源の所有者は画面スコープのままである`() {
        val root = KotlinSourceScanner.findModuleSourceRoot()
            ?: throw AssertionError("走査根（src/main/java/com/novelreader）が解けない＝検知器が死んでいる")

        // なぜ stripComments を通すか: 検査したいのは「実コードが所有していないこと」であって、
        // why を説明するコメント中の言及は退行ではない（むしろ残すべき記述）。生テキストへの contains は
        // ChapterScreen 冒頭の所有権コメントを拾って偽陽性になる。位置比較も同じ基準に揃える（一様除去のため順序は不変）。
        val chapterScreen = KotlinSourceScanner.stripComments(File(root, "ui/ChapterScreen.kt").readText())
        assertFalse(
            "ChapterScreen（章スコープ）が keepScreenOn を持つと章送りのたびに破棄→再生成され、" +
                "退場側の後始末が入場側に勝つ（2026-07-29 の真因）。所有者は ReadingWindowContract。",
            chapterScreen.contains("keepScreenOn"),
        )
        assertFalse(
            "ChapterScreen の onDispose でシステムバーを戻すと同じ順序競合が再発する。",
            Regex("""onDispose\s*\{[^}]*systemBars""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(chapterScreen),
        )

        val readingScreen = KotlinSourceScanner.stripComments(File(root, "ui/NativeReadingScreen.kt").readText())
        val contractCall = readingScreen.indexOf("ReadingWindowContract(showingChapter")
        val chapterSwap = readingScreen.indexOf("tocBodyTransition.AnimatedContent(")
        assertTrue("ReadingScreen が ReadingWindowContract を呼んでいない", contractCall >= 0)
        assertTrue("章の AnimatedContent が見つからない＝検知器の前提が壊れている", chapterSwap >= 0)
        assertTrue(
            "ReadingWindowContract は章の AnimatedContent より外側（＝手前）で呼ぶこと",
            contractCall < chapterSwap,
        )
    }
}
