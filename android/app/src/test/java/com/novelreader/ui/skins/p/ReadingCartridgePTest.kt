package com.novelreader.ui.skins.p

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
 * スキンP（カートリッジ）の読書クローム部品のテキスト写経を回帰で守る（ADR 0009・reading-P.html 正本）。
 * 章扉のピクセル話数「第N話 ／ 全M話」と HUD セーブチップ「N/全数 · %」の書式が崩れないことに集中する
 * （意匠 Canvas は目視・実機層。ここは表示ロジックの退行が痛い箇所に絞る＝過剰網羅を避ける）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingCartridgePTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    @Test
    fun `章扉はピクセル話数「第N話 ／ 全M話」と章題を出す`() {
        composeTestRule.setContent {
            ChapterHeaderP(
                title = "雨上がりの城門にて",
                chapterNumber = 127,
                totalChapters = 340,
                colors = colors,
                fontSize = 18,
                bodyMarginDp = 15,
                bodyMaxWidth = 600.dp,
            )
        }
        composeTestRule.onNodeWithText("第127話 ／ 全340話").assertIsDisplayed()
        composeTestRule.onNodeWithText("雨上がりの城門にて").assertIsDisplayed()
    }

    @Test
    fun `全章数が不明なら話数は「第N話」に縮退する（版面を壊さない）`() {
        composeTestRule.setContent {
            ChapterHeaderP(
                title = "序",
                chapterNumber = 1,
                totalChapters = null,
                colors = colors,
                fontSize = 18,
                bodyMarginDp = 15,
                bodyMaxWidth = 600.dp,
            )
        }
        composeTestRule.onNodeWithText("第1話").assertIsDisplayed()
    }

    @Test
    fun `章番号が不明なら話数行は出さず章題のみ`() {
        composeTestRule.setContent {
            ChapterHeaderP(
                title = "無名章",
                chapterNumber = null,
                totalChapters = 340,
                colors = colors,
                fontSize = 18,
                bodyMarginDp = 15,
                bodyMaxWidth = 600.dp,
            )
        }
        composeTestRule.onNodeWithText("無名章").assertIsDisplayed()
    }

    @Test
    fun `連続プレイの炎は連続読書日数「N日」を出す（遊び心P3）`() {
        // データ源は未実装のため本番HUDでは未配線・非表示だが、部品自体は日数を正直に写して描く
        //（ダミー数値の捏造はしない＝呼び出し側が実データを渡すまで骨格から呼ばれない）。
        composeTestRule.setContent { StreakFlameP(streakDays = 6) }
        composeTestRule.onNodeWithText("6日").assertIsDisplayed()
    }

    @Test
    fun `連続が途切れた種火でも日数表記は正直に出る（遊び心P3）`() {
        composeTestRule.setContent { StreakFlameP(streakDays = 1) }
        composeTestRule.onNodeWithText("1日").assertIsDisplayed()
    }

    @Test
    fun `HUDセーブチップは「N全数 進捗%」の緑LCD読み取りを出す`() {
        composeTestRule.setContent {
            // 127/340 ≒ 0.3735 → 37%（fraction から整数%へ丸める）
            SaveChipP(chapterNumber = 127, totalChapters = 340, fraction = 127f / 340f)
        }
        composeTestRule.onNodeWithText("127/340 · 37%").assertIsDisplayed()
    }
}
