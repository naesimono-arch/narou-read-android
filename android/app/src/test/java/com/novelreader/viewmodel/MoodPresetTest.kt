package com.novelreader.viewmodel

import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodPresetTest {

    @Test
    fun `toQuery - 各プリセットがAPI仕様どおりの範囲条件へ変換されること`() {
        MoodPreset.SHORT_TRIP.toQuery().let {
            assertEquals(NarouNovelType.SHORT, it.type)
            assertEquals("-30", it.time)
        }
        MoodPreset.BINGE.toQuery().let {
            assertEquals(NarouNovelType.KANKETSU, it.type)
            assertEquals("100000-", it.length)
            assertEquals(NarouOrder.TOTAL, it.order)
        }
        MoodPreset.DIALOGUE.toQuery().let {
            assertEquals("60-", it.kaiwaritu)
        }
        MoodPreset.ILLUSTRATED.toQuery().let {
            assertEquals("1-", it.sasie)
        }
    }

    @Test
    fun `toResultContext - 見出しと説明が結果一覧の文脈へ載ること`() {
        val ctx = MoodPreset.SHORT_TRIP.toResultContext()
        assertEquals("30分の小さな旅", ctx.title)
        assertEquals("短い時間で完結する物語。読了目安30分まで・短編のみ。", ctx.subtitle)
        assertEquals(ResultSource.MOOD, ctx.source)
        assertEquals(MoodPreset.SHORT_TRIP.toQuery(), ctx.query)
    }
}
