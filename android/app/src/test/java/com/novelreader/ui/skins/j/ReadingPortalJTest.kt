package com.novelreader.ui.skins.j

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンJ（ポータル）の読書クローム部品のテキスト写経を回帰で守る（ADR 0009・reading-J.html 正本）。
 * 章扉の漢数字話数「第 百二十七 話」と章題、章末印「— 第N話 了 —」の書式が崩れないことに集中する
 * （ambient/glyph/敷居の Canvas 意匠と J2 敷居光の出没は目視・実機層。ここは表示ロジックの退行に絞る）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingPortalJTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.DARK.colors

    @Test
    fun `章扉は漢数字話数「第 百二十七 話」と章題を出す`() {
        composeTestRule.setContent {
            ChapterHeaderJ(
                title = "雨上がりの城門にて",
                chapterNumber = 127,
                colors = colors,
                readingTheme = ReadingTheme.DARK,
                fontSize = 18,
                bodyMarginDp = 26,
                bodyMaxWidth = 600.dp,
            )
        }
        composeTestRule.onNodeWithText("第 百二十七 話").assertIsDisplayed()
        composeTestRule.onNodeWithText("雨上がりの城門にて").assertIsDisplayed()
    }

    @Test
    fun `章番号が不明なら話数行は出さず章題のみ`() {
        composeTestRule.setContent {
            ChapterHeaderJ(
                title = "無名章",
                chapterNumber = null,
                colors = colors,
                readingTheme = ReadingTheme.DARK,
                fontSize = 18,
                bodyMarginDp = 26,
                bodyMaxWidth = 600.dp,
            )
        }
        composeTestRule.onNodeWithText("無名章").assertIsDisplayed()
    }

    @Test
    fun `章末印は「— 第N話 了 —」を漢数字で出す（遊び心J2の相方）`() {
        composeTestRule.setContent {
            ChapterEndMarkJ(chapterNumber = 127, colors = colors)
        }
        composeTestRule.onNodeWithText("— 第百二十七話 了 —").assertIsDisplayed()
    }

    @Test
    fun `章番号が不明な章末印は「— 了 —」に縮退する（情報を偽らない）`() {
        composeTestRule.setContent {
            ChapterEndMarkJ(chapterNumber = null, colors = colors)
        }
        composeTestRule.onNodeWithText("— 了 —").assertIsDisplayed()
    }
}
