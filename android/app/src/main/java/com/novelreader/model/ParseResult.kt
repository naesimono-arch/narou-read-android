package com.novelreader.model

/** 章HTMLパースの結果を表すシールドクラス */
sealed class ParseResult {
    data object Loading : ParseResult()
    data class Success(val content: ChapterContent) : ParseResult()
    data class Error(val message: String, val fileName: String) : ParseResult()
}
