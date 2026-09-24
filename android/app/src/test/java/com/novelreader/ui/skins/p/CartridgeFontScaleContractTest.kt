package com.novelreader.ui.skins.p

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.model.TocEntry
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * スキンP（カートリッジ）の器と刻印が **fontScale を上げても字面を壊さない**ことを固定する契約テスト。
 *
 * ## なぜ要るか（2026-09-04 実機 PGEM10・fontScale 2.0 で出た実害）
 * 2026-09-03 の lineHeight 掃討は「版面の正は dp 側が持つ」として P の器を `Modifier.height()` で
 * dp 固定した。**dp は fontScale に追従しない**ので、字面だけが sp で伸びて器から出る。
 * 一度直しても `height(22.dp)` と書けば静かに再発する種類の破綻なので、数で止める。
 *
 * 実測で確定した2系統（どちらも 360dp 幅・xhdpi・fontScale 2.0）:
 *  1) **縦＝器から切れる**（dp 固定が真因）。`.savebar` 22dp に「42%」23.5dp／`.save` 21dp に
 *     「127/340 · 42%」26.0dp／`.streak` 24dp に「12日」／目次 `.row` 66dp に章題（2行→1行へ潰れた上で切断）。
 *  2) **横＝1行の刻印が折り返す**（dp 固定とは**無関係**＝真因が別）。PixelFamily の英字は letterSpacing が
 *     広く、「CARTRIDGE LIBRARY」は 281.5dp まで広がって残り 30.5dp に押された冊数を「10」「本」へ割り、
 *     「POCKET NOVEL · COLOR」は 2行に折れて右の通気孔を押し出していた。
 *
 * 対処は器＝比例追従（[cartridgeBoxHeight]）・刻印＝dp 固定（[engravedSp]）で、線引きの根拠は
 * `CartridgePartsP.kt` の両 KDoc が持つ。本ファイルはその2つを数で守る。
 *
 * ⚠️ [GraphicsMode.Mode.NATIVE] は必須。既定の LEGACY は実フォントを使わず「文字幅＝文字数」の代用計量に
 * なるため、行数・行箱に依存する本ファイルは**全緑のまま検出力ゼロ**になる
 * （`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 *
 * 端末幅 360dp も条件の一部（刻印が折り返すかは残り幅で決まる）＝ qualifiers を外すと意味が変わる。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class CartridgeFontScaleContractTest(private val fontScale: Float) {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 器の期待高＝ fontScale 1.0 の版面 × 拡大率（縮小側へは伸ばさない＝[cartridgeBoxHeight] と同じ規則）。 */
    private fun expected(base: Dp): Dp = base * fontScale

    private fun setP(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSkin provides Skin.CARTRIDGE_P,
                LocalSkinTokens provides Skin.CARTRIDGE_P.tokens,
                LocalDensity provides Density(base.density, fontScale),
            ) {
                MaterialTheme { content() }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val sink = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(sink)
        return sink.first()
    }

    private fun heightOfTag(tag: String): Dp =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            .let { it.bottom - it.top }

    /** 字面が器から切れていないこと。[TextLayoutResult.didOverflowHeight] が破綻の直接の指標。 */
    private fun assertNotClipped(text: String) =
        assertFalse("[$text] が器から縦に切れている（fontScale=$fontScale）", layoutOf(text).didOverflowHeight)

    @Test
    fun `読書HUDの器は fontScale に比例し字面を切らない`() {
        setP {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingSaveBarP(fraction = 0.42f, modifier = Modifier.testTag(TAG_SAVEBAR))
                SaveChipP(chapterNumber = 127, totalChapters = 340, fraction = 0.42f, modifier = Modifier.testTag(TAG_SAVECHIP))
                StreakFlameP(streakDays = 12, modifier = Modifier.testTag(TAG_STREAK))
                SettingsSysBarP(modifier = Modifier.testTag(TAG_SYSBAR))
            }
        }
        // 器の高さ＝正本の px 算術（22/21/24/28）× 拡大率。1.0 では掃討が確定させた版面そのもの。
        assertEquals(expected(22.dp), heightOfTag(TAG_SAVEBAR))
        assertEquals(expected(21.dp), heightOfTag(TAG_SAVECHIP))
        assertEquals(expected(24.dp), heightOfTag(TAG_STREAK))
        assertEquals(expected(28.dp), heightOfTag(TAG_SYSBAR))
        // 実際に切れていないこと（高さの式だけでは「式は合っているのに切れている」を見逃す）。
        listOf("SAVE", "42%", "127/340 · 42%", "12日", "POCKET NOVEL").forEach(::assertNotClipped)
    }

    @Test
    fun `ラック見出しは刻印を1行に保ち冊数を割らない`() {
        setP { LibraryHeader(count = 10) }
        // 刻印を伸ばすと 281.5dp まで広がり、残り幅に押された冊数が「10」「本」へ割れた＝実機で出た実害。
        assertEquals("刻印が折り返している（fontScale=$fontScale）", 1, layoutOf("CARTRIDGE LIBRARY").lineCount)
        assertEquals("冊数が別行へ割れている（fontScale=$fontScale）", 1, layoutOf("10 本").lineCount)
    }

    @Test
    fun `一覧面の見出しも刻印を1行に保ち冊数を割らない`() {
        // 一覧面（BookshelfListCartridgeP）は同じ見出しに「一覧 · 」を前置する＝冊数側が最長になる面。
        setP { LibraryHeader(count = 4, countPrefix = "一覧 · ") }
        assertEquals("刻印が折り返している（fontScale=$fontScale）", 1, layoutOf("CARTRIDGE LIBRARY").lineCount)
        assertEquals("冊数が別行へ割れている（fontScale=$fontScale）", 1, layoutOf("一覧 · 04 本").lineCount)
    }

    @Test
    fun `機体銘板は1行に保たれる`() {
        setP { Deck() }
        assertEquals("銘板が折り返している（fontScale=$fontScale）", 1, layoutOf("POCKET NOVEL · COLOR").lineCount)
    }

    @Test
    fun `目次の章行は fontScale に比例し章題を切らない`() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                NovelReaderTheme(skin = Skin.CARTRIDGE_P, theme = ReadingTheme.LIGHT) {
                    TocCartridgeP(
                        tocState = TocState.Content(
                            (1..40).map { TocEntry(title = if (it == CURRENT) CURRENT_TITLE else "章$it の題", fileName = "chap_$it.html") }
                        ),
                        workTitle = "辺境の薬師は千日の旅路をゆく",
                        currentChapterFile = "chap_$CURRENT.html",
                        onSelectChapter = {},
                        onNavigateToBookshelf = {},
                        onRetry = {},
                    )
                }
            }
        }
        // fontScale 2.0 では現在章が初期表示に入らない（HUD の幅配分＝別途の版面裁定・2026-08-14 起票）ため送る。
        composeTestRule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(CURRENT_TITLE))
        // 章題は正本の line-clamp 2＝**2行ぶんの取り分**が器に在ることが契約。dp 固定のままだと器が
        // 追従せず fontScale 2.0 で1行に潰れて切れていた（実測 lineCount 2→1・didOverflowHeight=true）。
        // ⚠️ ここで didOverflowHeight を見ないのは、clamp による省略（＝意図した挙動）でも真になり、
        //    「器が足りない」と「2行に収めきれない長題名」を区別できないため。行数が直接の指標になる。
        assertEquals("章題が line-clamp 2 の取り分を失っている（fontScale=$fontScale）", 2, layoutOf(CURRENT_TITLE).lineCount)
    }

    companion object {
        private const val TAG_SAVEBAR = "p-savebar"
        private const val TAG_SAVECHIP = "p-savechip"
        private const val TAG_STREAK = "p-streak"
        private const val TAG_SYSBAR = "p-sysbar"
        private const val CURRENT = 20
        private const val CURRENT_TITLE = "雨上がりの城門にて、彼女は静かに剣を置いた"

        /** 1.0＝既定の版面（掃討の成果）／2.0＝端末の「最大」文字サイズ＝実害が出た条件。 */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun data(): List<Array<Any>> = listOf(arrayOf(1.0f), arrayOf(2.0f))
    }
}
