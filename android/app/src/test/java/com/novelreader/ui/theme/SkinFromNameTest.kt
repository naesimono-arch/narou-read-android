package com.novelreader.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * スキン永続化文字列（SharedPreferences "app_skin"）からの復元 [skinFromName] の防御契約。
 *
 * なぜテストするか: 保存値は enum の生 String＝将来スキンを改名・削除した端末では不正値が残る。
 * そのとき起動クラッシュではなく既定 D へ静かに戻ることがこの関数の存在理由（"reading_theme" と同じ防御）。
 */
class SkinFromNameTest {

    @Test
    fun `キー不在(null)は既定の和モダンDへ`() {
        assertEquals(Skin.WAMODERN_D, skinFromName(null))
    }

    @Test
    fun `保存済みの各スキン名は正しく復元される`() {
        // 全 enum 値を機械的に往復（スキン追加時にこのテストが自動で新値も覆う）
        Skin.entries.forEach { skin ->
            assertEquals(skin, skinFromName(skin.name))
        }
    }

    @Test
    fun `不正値(改名・削除済みスキン)はクラッシュせず既定Dへ`() {
        assertEquals(Skin.WAMODERN_D, skinFromName("REMOVED_SKIN_X"))
        assertEquals(Skin.WAMODERN_D, skinFromName(""))
    }
}
