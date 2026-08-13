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
 * スキンP「カートリッジ」の目次ルーター（ADR 0022 §1）＋ TocCartridgeP の描画分岐テスト（TocSkyMTest と同型）。
 *
 * 固定するもの:
 *  1) P 装着で緑LCD HUD が出る＝D 構造でない（D 目次は「STAGE SELECT」の機体ラベルを持たない）
 *  2) D 装着では従来の D 描画のまま（ルーターが D 経路を横取りしない）＝HUD 不在・章題は D で表示
 *  3) 現在章の検出（強調表示・道点火の源）が HUD の現在地チップに現れる
 *  4) 現在章に唯一の実アクション「ここから再開」が出る（一画面一強調）
 *  5) 章タップで fileName 付き onSelectChapter が呼ばれる
 *  6) 空状態で「章が見つかりません」を出す
 *
 * ⚠️ 2026-08-14（K形2世代の追従）以降、P の識別点に**現在地チップの見える文字は使えない**:
 * チップは他4スキンと同じ「第N話」へ短縮され、D の目次も同じ字面を描くようになったため。
 * 機体固有の「STAGE SELECT」で P を名指し、チップは読み上げ名（削った前置きを残した側）で名指す。
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、ルーター分岐だけを検証しテーマ SideEffect を
 * 切り離すため（TocSkyMTest と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TocCartridgePTest {

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
    fun `P装着では緑LCD HUDが出る＝D構造でない`() {
        setToc(Skin.CARTRIDGE_P, TocState.Content(entries), currentChapterFile = "chap_2.html")
        // K形伝播後のカートリッジの署名＝HUD の機体ラベル「STAGE SELECT」（D 目次は現在地バーだけで機体クロームを持たない）。
        composeTestRule.onNodeWithText("STAGE SELECT").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("いま読んでいる: 第2話").assertIsDisplayed()
        composeTestRule.onNodeWithText("第一章 出会い").assertIsDisplayed()
    }

    @Test
    fun `D装着ではHUDが出ず従来のD描画のまま`() {
        setToc(Skin.WAMODERN_D, TocState.Content(entries), currentChapterFile = "chap_2.html")
        composeTestRule.onNodeWithText("STAGE SELECT").assertDoesNotExist()
        composeTestRule.onNodeWithText("第二章 旅立ち").assertIsDisplayed()
    }

    @Test
    fun `現在章の検出がHUDの現在地チップに現れる（強調と道点火の源）`() {
        setToc(Skin.CARTRIDGE_P, TocState.Content(entries), currentChapterFile = "chap_3.html")
        composeTestRule.onNodeWithContentDescription("いま読んでいる: 第3話").assertIsDisplayed()
    }

    @Test
    fun `現在章に唯一の実アクションここから再開が出る`() {
        setToc(Skin.CARTRIDGE_P, TocState.Content(entries), currentChapterFile = "chap_2.html")
        // 2026-08-07 裁定の追従（2026-08-14）でチップは文字を持たないアイコンのみ＝名指しは読み上げ名。
        composeTestRule.onNodeWithContentDescription("ここから再開").assertIsDisplayed()
    }

    @Test
    fun `章タップでファイル名付きonSelectChapterが呼ばれる`() {
        var selected: String? = null
        setToc(
            Skin.CARTRIDGE_P, TocState.Content(entries),
            currentChapterFile = "chap_1.html",
            onSelectChapter = { selected = it },
        )
        composeTestRule.onNodeWithText("第二章 旅立ち").performClick()
        assertEquals("chap_2.html", selected)
    }

    @Test
    fun `空状態では章が見つかりませんを表示する`() {
        setToc(Skin.CARTRIDGE_P, TocState.Empty)
        composeTestRule.onNodeWithText("章が見つかりません").assertIsDisplayed()
    }
}
