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
 * スキンJ「ポータル」の目次ルーター（ADR 0022 §1）＋ TocPortalJ の描画分岐テスト（TocSkyMTest/TocCartridgePTest と同型）。
 *
 * 固定するもの:
 *  1) J 装着で「廊下を進む道程」目次が出る＝D 構造でない。J は数値カウンタを持たない（cap「光量で進行を語る」）ため、
 *     M の話数 sync・P の HUD STAGE に相当する D-vs-J の識別点は現在章の a11y 焦点「現在の章」に置く
 *     （視覚は金面/灯りで示すが数値は足さない＝発明回避。「現在の章」は D 目次に無い＝画面丸ごと分岐の証拠）。
 *  2) D 装着では従来の D 描画のまま（ルーターが D 経路を横取りしない）＝「現在の章」不在・章題は D で表示
 *  3) 現在章の検出（強調・道点火の源）が「現在の章」焦点として現れる
 *  4) 章タップで fileName 付き onSelectChapter が呼ばれる
 *  5) 空状態で「章が見つかりません」を出す
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、ルーター分岐だけを検証しテーマ SideEffect を
 * 切り離すため（TocSkyMTest/TocCartridgePTest と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TocPortalJTest {

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
    fun `J装着では廊下の道程目次が出る＝D構造でない`() {
        setToc(Skin.PORTAL_J, TocState.Content(entries), currentChapterFile = "chap_2.html")
        // J ポータルの識別点＝現在章「いま立つ扉」の a11y 焦点（D 目次には無い＝画面丸ごと分岐している）。
        composeTestRule.onNodeWithContentDescription("現在の章").assertIsDisplayed()
        composeTestRule.onNodeWithText("第一章 出会い").assertIsDisplayed()
    }

    @Test
    fun `D装着では現在の章焦点が出ず従来のD描画のまま`() {
        setToc(Skin.WAMODERN_D, TocState.Content(entries), currentChapterFile = "chap_2.html")
        composeTestRule.onNodeWithContentDescription("現在の章").assertDoesNotExist()
        composeTestRule.onNodeWithText("第二章 旅立ち").assertIsDisplayed()
    }

    @Test
    fun `現在章の検出が現在の章焦点に現れる（強調と道点火の源）`() {
        setToc(Skin.PORTAL_J, TocState.Content(entries), currentChapterFile = "chap_3.html")
        // 現在章＝chap_3。強調（金面/灯る節）の源＝現在章焦点が1点だけ現れる。
        composeTestRule.onNodeWithContentDescription("現在の章").assertIsDisplayed()
        composeTestRule.onNodeWithText("第三章 決戦").assertIsDisplayed()
    }

    @Test
    fun `章タップでファイル名付きonSelectChapterが呼ばれる`() {
        var selected: String? = null
        setToc(
            Skin.PORTAL_J, TocState.Content(entries),
            currentChapterFile = "chap_1.html",
            onSelectChapter = { selected = it },
        )
        composeTestRule.onNodeWithText("第二章 旅立ち").performClick()
        assertEquals("chap_2.html", selected)
    }

    @Test
    fun `空状態では章が見つかりませんを表示する`() {
        setToc(Skin.PORTAL_J, TocState.Empty)
        composeTestRule.onNodeWithText("章が見つかりません").assertIsDisplayed()
    }
}
