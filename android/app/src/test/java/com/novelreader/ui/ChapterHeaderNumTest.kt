package com.novelreader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.model.ChapterContent as ChapterContentModel
import com.novelreader.model.TextSegment
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.tokens
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 章見出しの話数ラベル（2026-08-06 裁定①〔出す〕・②③は推奨適用）の適用規則を横書き D と縦書きで固定する。
 *
 * 固定する規則（正本モック reading-D / reading-vertical-scroll-D の .chap-h＝.num+.t の2要素見出し）:
 * (a) 原文接頭辞のある章は接頭辞を `.num` へ分離し、題（.t）は本体だけ（話数二重の解消）。
 * (b) 接頭辞の無い章だけ index（目次順）から「第 N 話」（漢数字＝モック表記）を補完する。
 * (c) 題が漢数字ラベル（第一話…）で始まる章には補完しない（補完すると二重が再発する）。
 * (d) 4桁話数（実蔵書最大860話・なろう長編は4桁が普通）でもラベル書式が崩れない。
 * (e) 横書きと縦書きで同じ規則（向きで同じ本の見出しが変わらない）。
 * M/P/J 章扉の同規則は ReadingChromeM 系・ReadingCartridgePTest・ReadingPortalJTest 側で固定
 * （M はスキン分岐ごと本テストで代表1件を張る）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ChapterHeaderNumTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private fun content(title: String) = ChapterContentModel(
        title = title,
        segments = persistentListOf<TextSegment>(TextSegment.Plain("本文です。")),
    )

    private fun setHorizontal(skin: Skin, title: String, chapterNumber: Int?) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                ChapterContent(
                    content = content(title),
                    colors = colors,
                    fontSize = 18,
                    lineHeightEm = 2.5f,
                    bodyMarginDp = 20,
                    chapterNumber = chapterNumber,
                )
            }
        }
    }

    private fun setVertical(title: String, chapterNumber: Int?) {
        composeTestRule.setContent {
            VerticalChapterContent(
                content = content(title),
                colors = colors,
                fontSize = 17,
                lineHeightEm = 2.4f,
                bodyMarginDp = 20,
                chapterNumber = chapterNumber,
            )
        }
    }

    // ---- 横書き D（共通 ChapterHeader＝K/C も同経路） ----

    @Test
    fun `D 原文接頭辞のある章は接頭辞を話数側に・題は本体だけ出す`() {
        setHorizontal(Skin.WAMODERN_D, "０１．婚約の継続をされたいのですか？", chapterNumber = 1)
        composeTestRule.onNodeWithText("０１").assertIsDisplayed()
        composeTestRule.onNodeWithText("婚約の継続をされたいのですか？").assertIsDisplayed()
        // 接頭辞込みの原文題と index 補完の両方が消えている＝1系統になった証拠。
        composeTestRule.onNodeWithText("０１．婚約の継続をされたいのですか？").assertDoesNotExist()
        composeTestRule.onNodeWithText("第 一 話").assertDoesNotExist()
    }

    @Test
    fun `D 接頭辞の無い章はindexから漢数字で補完する（モック表記）`() {
        setHorizontal(Skin.WAMODERN_D, "雨上がりの城門にて", chapterNumber = 127)
        composeTestRule.onNodeWithText("第 百二十七 話").assertIsDisplayed()
        composeTestRule.onNodeWithText("雨上がりの城門にて").assertIsDisplayed()
    }

    @Test
    fun `D 接頭辞もindexも無い章は題のみ（ラベル行を出さない）`() {
        setHorizontal(Skin.WAMODERN_D, "雨上がりの城門にて", chapterNumber = null)
        composeTestRule.onNodeWithText("雨上がりの城門にて").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("第", substring = true).assertCountEquals(0)
    }

    @Test
    fun `D 漢数字ラベルで始まる題には補完しない（話数二重の回避）`() {
        setHorizontal(Skin.WAMODERN_D, "第百二十七話　城門", chapterNumber = 127)
        composeTestRule.onNodeWithText("第百二十七話　城門").assertIsDisplayed()
        composeTestRule.onNodeWithText("第 百二十七 話").assertDoesNotExist()
    }

    @Test
    fun `D 4桁話数でもラベル書式が崩れない`() {
        setHorizontal(Skin.WAMODERN_D, "雨上がりの城門にて", chapterNumber = 1024)
        composeTestRule.onNodeWithText("第 千二十四 話").assertIsDisplayed()
    }

    // ---- 横書き M（章扉分岐の代表1件＝同じ規則が M 経路にも配線されている証拠） ----

    @Test
    fun `M 原文接頭辞のある章は接頭辞を話数側に・題は本体だけ出す`() {
        setHorizontal(Skin.SEIZU_M, "０１．婚約の継続をされたいのですか？", chapterNumber = 1)
        composeTestRule.onNodeWithText("０１").assertIsDisplayed()
        composeTestRule.onNodeWithText("婚約の継続をされたいのですか？").assertIsDisplayed()
        composeTestRule.onNodeWithText("０１．婚約の継続をされたいのですか？").assertDoesNotExist()
    }

    // ---- 縦書き（横書きと同じ規則＝向きで見出しが変わらない） ----
    // 縦書きのラベルは1マス1Text（縦積み）のため文字列一致で拾えない。見出し全体を1つの heading ノードに
    // 束ねた contentDescription（ラベル→題の読み順）で規則の適用を検証する。

    @Test
    fun `縦書き 接頭辞の無い章はindexから漢数字で補完する`() {
        setVertical("雨上がりの城門にて", chapterNumber = 127)
        composeTestRule
            .onNode(hasContentDescription("第 百二十七 話　雨上がりの城門にて"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `縦書き 原文接頭辞のある章は接頭辞を話数側に・題は本体だけ読む`() {
        setVertical("０１．婚約の継続をされたいのですか？", chapterNumber = 1)
        composeTestRule
            .onNode(hasContentDescription("０１　婚約の継続をされたいのですか？"), useUnmergedTree = true)
            .assertExists()
        composeTestRule
            .onAllNodes(hasContentDescription("０１．", substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `縦書き 接頭辞もindexも無い章は題のみを読む`() {
        setVertical("雨上がりの城門にて", chapterNumber = null)
        composeTestRule
            .onNode(hasContentDescription("雨上がりの城門にて"), useUnmergedTree = true)
            .assertExists()
    }
}
