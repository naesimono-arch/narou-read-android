package com.novelreader.ui

import com.novelreader.model.TocEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 目次を開いた瞬間に現在章付近を初期表示するための先頭可視 index 導出（tocInitialFirstVisibleIndex）の
 * 単体テスト。実機フィードバック「既に読み終えた話は一覧を開いた際に現在地付近から表示したい」の
 * 導出規則（現在章の1つ手前・未読は先頭・終端 clamp）を UI から切り離して固定する。
 */
class TocInitialScrollIndexTest {

    private fun entries(vararg files: String): List<TocEntry> =
        files.map { TocEntry(title = it, fileName = it) }

    @Test
    fun `既読は現在章の1つ手前を初期先頭にする（前後文脈を残す）`() {
        val list = entries("c1.html", "c2.html", "c3.html", "c4.html")
        assertEquals(2, tocInitialFirstVisibleIndex(list, "c4.html"))
    }

    @Test
    fun `現在章が先頭でも負にならず0へclampする`() {
        val list = entries("c1.html", "c2.html", "c3.html")
        assertEquals(0, tocInitialFirstVisibleIndex(list, "c1.html"))
    }

    @Test
    fun `未読（currentChapterFileがnull）は先頭から`() {
        val list = entries("c1.html", "c2.html", "c3.html")
        assertEquals(0, tocInitialFirstVisibleIndex(list, null))
    }

    @Test
    fun `現在章がリストに無い（未一致）ときは先頭から`() {
        val list = entries("c1.html", "c2.html", "c3.html")
        assertEquals(0, tocInitialFirstVisibleIndex(list, "missing.html"))
    }

    @Test
    fun `終端章でも1つ手前を返す（終端の空白抑制はLazyList任せ）`() {
        // 終端章では index-1 を返すだけ。実際の空白抑制（それ以上スクロールしない clamp）は
        // LazyList の標準挙動に委ねる設計なので、この純関数は素の index-1 を返せばよい。
        val list = entries("c1.html", "c2.html", "c3.html", "c4.html", "c5.html")
        assertEquals(3, tocInitialFirstVisibleIndex(list, "c5.html"))
    }

    @Test
    fun `空リストは先頭0`() {
        assertEquals(0, tocInitialFirstVisibleIndex(emptyList(), "c1.html"))
    }
}
