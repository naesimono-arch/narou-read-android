package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * 目次画面の ← の**読み上げ語**を全スキンで固定する契約テスト（2026-09-07 ユーザー裁定）。
 *
 * 何を守るか＝「目次の ← は行き先を名乗らず操作名『戻る』と読み上げる」。
 * なぜ: 目次の ← の実体は読書フローの脱出（performBack →[ReadingBackStack.back] が null →
 * [com.novelreader.upFromReading]）で、**行き先が入場元で変わる**——本棚から入れば本棚、作品詳細の
 * 「アプリで読む」から入ればその詳細へ帰る。旧語 "本棚に戻る" は後者で**嘘の読み上げ**になっていた
 * （0046 以前からの既存欠陥で、2026-09-04 に作品詳細という2つ目の入場元ができた時点から嘘だった）。
 *
 * ⚠️ 章の ←（"目次に戻る"）と非対称なのは正しい: 章の ← の一つ上は必ず目次＝行き先が一意なので名乗れる。
 * 名乗れるものは名乗り、変わるものは名乗らない、という同一の規則から出た別の答え（章側は
 * [NativeReadingScreenA11yTest] が固定する）。
 *
 * なぜ1本で全スキンを回すか: 目次は [NativeTableOfContentsScreen] が [LocalSkin] で6スキンへ振り分け、
 * ← の実体は D/C 共通実装＋K/M/J/P の4スキン実装の**計5箇所**に分かれて住む。1箇所直して他が
 * 取り残される形の退行（今回まさにそれが5箇所すべてに潜在していた）は、スキンを跨いで初めて見える。
 * [Skin.entries] を回すので、**新しい装いを足せば自動でこの契約の対象になる**（登録簿を持たなくてよいのは
 * ルーターの when が Skin を網羅するため）。
 *
 * テスト1本につき `setContent` は1回しか呼べないため、装いは引数でなく状態として持ち替える
 * （[SettingsScreenKSkinGateTest] と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TocBackButtonLabelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 装着中の装い。値を変えると目次ルーターが組み直され、そのスキンの ← が描かれる。 */
    private val currentSkin = mutableStateOf(Skin.MEIKAI_K)

    private var backPressed = 0

    private val entries = listOf(
        TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
        TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
    )

    private fun setToc() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides currentSkin.value) {
                MaterialTheme {
                    NativeTableOfContentsScreen(
                        tocState = TocState.Content(entries),
                        colors = ReadingTheme.LIGHT.colors,
                        // 未読にして現在地チップを出さない＝見たいのは ← の1ノードだけなので、
                        // 読み上げを持つ他の部品を増やさない（チップは別テストの管轄）。
                        currentChapterFile = null,
                        onSelectChapter = {},
                        onNavigateToBookshelf = { backPressed++ },
                        onRetry = {},
                    )
                }
            }
        }
    }

    @Test
    fun `全スキンの目次←は行き先でなく操作名「戻る」を読み上げる`() {
        setToc()
        Skin.entries.forEach { skin ->
            currentSkin.value = skin
            composeTestRule.waitForIdle()
            // onNodeWithContentDescription は完全一致かつ「ちょうど1件」を要求する＝
            // 語が違えば見つからず、二重に描かれても落ちる（どちらの壊れ方も取り逃さない）。
            composeTestRule.onNodeWithContentDescription("戻る")
                .assertIsDisplayed()
            // 旧語が残っていないことを名指しで確認する。上の断言だけだと、← が2つ描かれて
            // 片方が旧語のまま、という中途半端な退行を「1件見つかった」で見逃しうる。
            composeTestRule.onNodeWithContentDescription("本棚に戻る")
                .assertDoesNotExist()
        }
    }

    @Test
    fun `全スキンで「戻る」ノードが脱出コールバックへ結線されている`() {
        // 語だけを見ると「たまたま同じ読み上げを持つ別ノード」を掴んでも緑になる。
        // 押して脱出コールバックが1回だけ増えることまで見て、掴んだのが本物の ← だと確定させる。
        setToc()
        Skin.entries.forEachIndexed { i, skin ->
            currentSkin.value = skin
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription("戻る").performClick()
            composeTestRule.waitForIdle()
            assertEquals("${skin.name} の ← が onNavigateToBookshelf へ結線されていない", i + 1, backPressed)
        }
    }
}
