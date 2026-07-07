package com.novelreader.ui.discovery

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryQueryLabelsTest {

    @Test
    fun `rangeText - 以上・以下・範囲・単一値・不正値を正しく整形すること`() {
        assertEquals("30分〜", rangeText("30-", "分"))
        assertEquals("〜30分", rangeText("-30", "分"))
        assertEquals("30〜100分", rangeText("30-100", "分"))
        assertEquals("30分", rangeText("30", "分"))
        assertNull(rangeText(null, "分"))
        assertNull(rangeText("", "分"))
        assertNull(rangeText("abc-", "分"))
    }

    @Test
    fun `charCountText - 万の倍数は万表記、それ以外は桁区切りになること`() {
        assertEquals("10万", charCountText(100000))
        assertEquals("1万", charCountText(10000))
        assertEquals("8,500", charCountText(8500))
        assertEquals("15,000", charCountText(15000)) // 1.5万は万の倍数でないので桁区切り
    }

    @Test
    fun `conditionChipLabels - デフォルトクエリは並び順チップのみになること`() {
        val labels = conditionChipLabels(DiscoveryQuery())
        assertEquals(listOf("週間順"), labels)
    }

    @Test
    fun `conditionChipLabels - 気分プリセット相当（短編×読了30分以内）のチップが人の言葉になること`() {
        val labels = conditionChipLabels(
            DiscoveryQuery(type = NarouNovelType.SHORT, time = "-30")
        )
        assertEquals(listOf("短編", "読了〜30分", "週間順"), labels)
    }

    @Test
    fun `conditionChipLabels - 複合条件（転生転移・残酷除外・期間・文字数・挿絵あり）が全て出ること`() {
        val labels = conditionChipLabels(
            DiscoveryQuery(
                order = NarouOrder.TOTAL,
                tensei = true,
                tenni = true,
                excludeZankoku = true,
                lastup = NarouLastup.SEVENDAY,
                length = "100000-",
                sasie = "1-",
            )
        )
        assertEquals(
            listOf("転生・転移", "残酷描写を除く", "7日以内に更新", "10万字〜", "挿絵あり", "累計順"),
            labels
        )
    }

    @Test
    fun `conditionChipLabels - word検索では選択した範囲が1チップに束ねられ、除外語も出ること`() {
        val labels = conditionChipLabels(
            DiscoveryQuery(
                word = "スローライフ",
                notWord = "残酷",
                inTitle = true,
                inKeyword = true,
            )
        )
        assertEquals(listOf("タイトル・キーワード", "除外: 残酷", "週間順"), labels)
    }

    @Test
    fun `conditionChipLabels - ジャンル指定はラベル化されること`() {
        val labels = conditionChipLabels(
            DiscoveryQuery(genres = setOf(201), biggenres = setOf(1))
        )
        assertEquals(listOf("恋愛", "ハイファンタジー", "週間順"), labels)
    }
}
