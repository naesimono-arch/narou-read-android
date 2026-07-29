package com.novelreader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 章スクロール位置の保存経路のうち、**章破棄フラッシュ**（2026-07-29 追加）を固定する。
 *
 * 固定したい穴: 継続保存は `debounce(400)` で間引いており、その collector は章サブコンポジションと同時に
 * キャンセルされる。よってスクロール停止から 400ms 未満で章を送る／目次へ上がると、その章の最終位置が
 * どこにも書かれず、戻ったとき古い位置（多くは入場直後に保存された章先頭）へ着地していた。
 *
 * 判定は「破棄をまたいで保存回数が増えたか」で行う＝debounce が発火済みかどうかに依存しない
 *（テストの時計制御に頼らず、破棄フラッシュの寄与だけを取り出すための書き方）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterScrollPersistenceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val saves = mutableListOf<Pair<Int, Int>>()

    /** 章の途中（段落42・端数7px）を読んでいる状態。LazyColumn を組まなくても位置は保持される。 */
    private fun mount(referenceMode: Boolean): () -> Unit {
        val state = LazyListState(firstVisibleItemIndex = 42, firstVisibleItemScrollOffset = 7)
        var mounted by mutableStateOf(true)
        composeTestRule.setContent {
            if (mounted) {
                ChapterScrollPersistence(
                    lazyListState = state,
                    currentFile = "chap_3.html",
                    referenceMode = referenceMode,
                    onSaveScroll = { index, offset -> saves += index to offset },
                )
            }
        }
        composeTestRule.waitForIdle()
        return { composeTestRule.runOnIdle { mounted = false } }
    }

    @Test
    fun `章の破棄時に最終位置がフラッシュされる`() {
        val unmount = mount(referenceMode = false)
        val beforeUnmount = composeTestRule.runOnIdle { saves.size }

        unmount()

        composeTestRule.runOnIdle {
            // 破棄が1件を追加している＝debounce の発火有無に関わらず破棄フラッシュが効いている。
            assertTrue("章破棄で位置が保存されていない（debounce 待ちの最終位置が失われる）", saves.size > beforeUnmount)
            assertEquals(42 to 7, saves.last())
        }
    }

    @Test
    fun `参照ジャンプ中は章が破棄されても書かない（続き先端の DB 値を守る）`() {
        val unmount = mount(referenceMode = true)
        unmount()
        composeTestRule.runOnIdle { assertTrue(saves.isEmpty()) }
    }
}
