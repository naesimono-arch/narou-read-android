package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.model.TocEntry
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンM「星図」の目次ルーター（ADR 0022 §1）＋ TocSkyM の描画分岐・結線テスト。
 *
 * 固定するもの（C2 仕様書 §5）:
 *  1) M 装着で星図目次（本棚と同期する話数 sync）が出る＝D 構造でない（D 目次は sync を持たない）
 *  2) D 装着では従来の D 描画のまま（ルーターが D 経路を横取りしない）＝sync 不在・章題は D で表示
 *  3) 現在章の検出（強調表示・canvas 点火の源）が sync の話数に現れる
 *  4) 章タップで fileName 付き onSelectChapter が呼ばれる
 *  5) 空状態で「章が見つかりません」を出す
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、ルーター分岐だけを検証しテーマ SideEffect を
 * 切り離すため（BookshelfSkyMTest と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TocSkyMTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val entries = listOf(
        TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
        TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
        TocEntry(title = "第三章 決戦", fileName = "chap_3.html"),
    )

    private fun setToc(
        skin: Skin,
        state: TocState,
        currentChapterFile: String? = null,
        onSelectChapter: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin) {
                MaterialTheme {
                    NativeTableOfContentsScreen(
                        tocState = state,
                        colors = ReadingTheme.LIGHT.colors,
                        currentChapterFile = currentChapterFile,
                        onSelectChapter = onSelectChapter,
                        onNavigateToBookshelf = {},
                        onRetry = {},
                    )
                }
            }
        }
    }

    @Test
    fun `M装着では星図目次の話数ラベルが出る＝D構造でない`() {
        // 現在章を第1話にずらすのは、現在地チップも 2026-08-07 の短縮で同じ「第N話」を描くようになり、
        // 現在章に重ねると行ラベルを onNodeWithText で名指しできなくなるため（識別したいのは行の .ep）。
        setToc(Skin.SEIZU_M, TocState.Content(entries), currentChapterFile = "chap_1.html")
        // K形伝播後の星図の識別点＝各行の話数ラベル .ep「第N話」（D 目次は話数を章題へインラインし独立ラベルを持たない）。
        composeTestRule.onNodeWithText("第2話").assertIsDisplayed()
        composeTestRule.onNodeWithText("第一章 出会い").assertIsDisplayed()
    }

    @Test
    fun `D装着では話数ラベルが出ず従来のD描画のまま`() {
        // 現在章を第1話にずらす理由は上のテストと同じ（D の現在地チップも「第N話」を描くため、
        // 現在章に重ねると「行ラベルが無い」ことの断言がチップに引っかかって成り立たない）。
        setToc(Skin.WAMODERN_D, TocState.Content(entries), currentChapterFile = "chap_1.html")
        composeTestRule.onNodeWithText("第2話").assertDoesNotExist()
        composeTestRule.onNodeWithText("第二章 旅立ち").assertIsDisplayed()
    }

    @Test
    fun `現在章の検出が現在地チップに現れる（強調とcanvas点火の源）`() {
        setToc(Skin.SEIZU_M, TocState.Content(entries), currentChapterFile = "chap_3.html")
        // 現在地チップが現在章（chap_3＝第3話）を指す。見える文字は 2026-08-07 の裁定で「第3話」だけに
        // 短縮され、行の話数ラベルと同字面になったため、チップは読み上げ名（削った前置きを残した側）で名指しする。
        composeTestRule.onNodeWithContentDescription("いま読んでいる: 第3話").assertIsDisplayed()
    }

    @Test
    fun `章タップでファイル名付きonSelectChapterが呼ばれる`() {
        var selected: String? = null
        setToc(
            Skin.SEIZU_M, TocState.Content(entries),
            currentChapterFile = "chap_1.html",
            onSelectChapter = { selected = it },
        )
        composeTestRule.onNodeWithText("第二章 旅立ち").performClick()
        assertEquals("chap_2.html", selected)
    }

    @Test
    fun `空状態では章が見つかりませんを表示する`() {
        setToc(Skin.SEIZU_M, TocState.Empty)
        composeTestRule.onNodeWithText("章が見つかりません").assertIsDisplayed()
    }
}
