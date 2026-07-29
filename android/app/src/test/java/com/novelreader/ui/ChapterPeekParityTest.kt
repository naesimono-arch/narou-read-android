package com.novelreader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.model.ChapterContent as ChapterContentModel
import com.novelreader.model.TextSegment
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.tokens
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スワイプ覗き＝遷移後表示の完全一致（[ChapterPeek] の契約）のうち、**章扉の話数行**を固定する。
 *
 * 固定したい退行（2026-07-29 修正）: [ChapterPeekPanel] が chapterNumber/totalChapters を
 * [ChapterContent] へ渡していなかったため、話数行を持つスキン（M/P/J）では覗きの章扉だけが1行低く、
 * スワイプ確定の直後に本文が1行ぶんずれていた。位置と同じく「着地と同じ材料を焼き込む」ことで一致させる。
 * 検証はテキストが決定的な P（「第N話 ／ 全M話」）で行う。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterPeekParityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val skin = Skin.CARTRIDGE_P
    private val colors = ReadingTheme.LIGHT.colors

    private val content = ChapterContentModel(
        title = "覗きの章",
        segments = listOf<TextSegment>(TextSegment.Plain("本文です。")).toImmutableList(),
    )

    private fun setPeek(chapterNumber: Int?, totalChapters: Int?) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                ChapterPeekPanel(
                    translationX = { 0f },
                    peek = ChapterPeek(
                        content = content,
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        chapterNumber = chapterNumber,
                        totalChapters = totalChapters,
                    ),
                    colors = colors,
                    fontSize = 18,
                    lineHeightEm = 2.5f,
                    bodyMarginDp = 20,
                    readingTheme = ReadingTheme.LIGHT,
                )
            }
        }
    }

    @Test
    fun `覗きの章扉にも着地と同じ話数行が出る`() {
        setPeek(chapterNumber = 3, totalChapters = 10)
        composeTestRule.onNodeWithText("第3話 ／ 全10話").assertIsDisplayed()
    }

    @Test
    fun `着地側の章扉も同じ話数行を出す（覗きと同一であることの対照）`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                ChapterContent(
                    content = content,
                    colors = colors,
                    fontSize = 18,
                    lineHeightEm = 2.5f,
                    bodyMarginDp = 20,
                    chapterNumber = 3,
                    totalChapters = 10,
                    readingTheme = ReadingTheme.LIGHT,
                )
            }
        }
        composeTestRule.onNodeWithText("第3話 ／ 全10話").assertIsDisplayed()
    }

    @Test
    fun `話数が不明なら覗きでも話数行を出さない（アサートが効いていることの陰性対照）`() {
        setPeek(chapterNumber = null, totalChapters = null)
        composeTestRule.onNodeWithText("第3話 ／ 全10話").assertDoesNotExist()
    }
}
