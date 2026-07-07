package com.novelreader.narou

import com.novelreader.narou.model.NarouCuratedKeywords
import org.junit.Assert.assertTrue
import org.junit.Test

class NarouCuratedKeywordsTest {

    @Test
    fun testKeywordsValidity() {
        val allCategories = NarouCuratedKeywords.basicCategories + NarouCuratedKeywords.genreCategories

        for (category in allCategories) {
            val title = category.title
            val words = category.words

            // 1. 空文字がないことを検証
            for (word in words) {
                assertTrue("カテゴリ「$title」に空文字が含まれています", word.isNotEmpty())
            }

            // 2. カテゴリ内の重複語がないことを検証
            val uniqueWords = words.toSet()
            assertTrue(
                "カテゴリ「$title」に重複したキーワードが含まれています: " +
                        words.groupBy { it }.filter { it.value.size > 1 }.keys,
                words.size == uniqueWords.size
            )
        }
    }
}
