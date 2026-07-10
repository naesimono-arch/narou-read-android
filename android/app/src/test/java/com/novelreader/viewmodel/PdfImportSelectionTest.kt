package com.novelreader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImportSelectionTest {

    // --- なろう形式ファイル名の判定 ---

    @Test
    fun `Nコード名のPDFはなろう形式と判定する`() {
        assertTrue(isNarouPdfFileName("N2959KI.pdf"))
        assertTrue(isNarouPdfFileName("N6169DZ.pdf"))
        assertTrue(isNarouPdfFileName("N1453LW.pdf"))
        // 大文字小文字・前後空白は無視する
        assertTrue(isNarouPdfFileName("n2959ki.pdf"))
        assertTrue(isNarouPdfFileName("N2959KI.PDF"))
        assertTrue(isNarouPdfFileName("  N2959KI.pdf  "))
    }

    @Test
    fun `Nコード名でないPDFはなろう形式でないと判定する`() {
        assertFalse(isNarouPdfFileName("シャングリラ・フロンティア.pdf"))
        assertFalse(isNarouPdfFileName("report.pdf"))
        assertFalse(isNarouPdfFileName("第1巻.pdf"))
        // 拡張子違い・ブラウザの重複サフィックスは弾く（改名扱い＝UIの「すべて取り込む」で救済）
        assertFalse(isNarouPdfFileName("N2959KI.txt"))
        assertFalse(isNarouPdfFileName("N2959KI(1).pdf"))
        // 桁不足（数字4桁未満）は弾く
        assertFalse(isNarouPdfFileName("N12A.pdf"))
    }

    // --- 自然順比較（数字を数値として比較） ---

    @Test
    fun `自然順比較は数字列を数値として比較する`() {
        assertTrue(naturalFileNameComparator.compare("第2話.pdf", "第10話.pdf") < 0)
        assertTrue(naturalFileNameComparator.compare("vol2.pdf", "vol10.pdf") < 0)
        // 先頭ゼロは無視（"08" < "10"）
        assertTrue(naturalFileNameComparator.compare("08.pdf", "10.pdf") < 0)
        // 数字以外はコードポイント順
        assertTrue(naturalFileNameComparator.compare("a.pdf", "b.pdf") < 0)
    }

    @Test
    fun `辞書順ソートで乱れる巻数を自然順ソートは正しく並べる`() {
        val names = listOf("第10話.pdf", "第2話.pdf", "第1話.pdf")
        val sorted = names.sortedWith(naturalFileNameComparator)
        assertEquals(listOf("第1話.pdf", "第2話.pdf", "第10話.pdf"), sorted)
    }

    // --- 取込計画（仕分け＋投入順） ---

    @Test
    fun `なろう形式と非なろう形式を仕分ける`() {
        val names = listOf("N0002AA.pdf", "memo.pdf", "N0001AA.pdf")
        val plan = planNarouPdfImport(names)
        // なろう形式のインデックスは 0,2、非なろうは 1
        assertEquals(setOf(0, 2), plan.narouOrder.toSet())
        assertEquals(listOf(1), plan.nonNarouOrder)
    }

    @Test
    fun `投入順は自然昇順の逆順（本棚がaddedAt降順なので巻1を最後に投入して先頭に見せる）`() {
        // 分割PDFを想定（すべて非なろう形式の連番）。自然昇順 vol1,vol2,vol3 の逆順で投入すれば、
        // 最後に投入した vol1 が最新 addedAt を得て降順本棚の先頭に来る＝上から vol1,vol2,vol3。
        val names = listOf("vol1.pdf", "vol2.pdf", "vol3.pdf")
        val plan = planNarouPdfImport(names)
        assertTrue(plan.narouOrder.isEmpty())
        // インデックス [2(vol3), 1(vol2), 0(vol1)] の順に投入 → vol1 が最後＝本棚先頭
        assertEquals(listOf(2, 1, 0), plan.nonNarouOrder)
    }

    @Test
    fun `なろう形式のみのときは非なろうが空になる`() {
        val names = listOf("N6169DZ.pdf", "N2959KI.pdf")
        val plan = planNarouPdfImport(names)
        assertTrue(plan.nonNarouOrder.isEmpty())
        assertEquals(2, plan.narouOrder.size)
        assertEquals(setOf(0, 1), plan.narouOrder.toSet())
    }

    @Test
    fun `空リストは空の計画を返す`() {
        val plan = planNarouPdfImport(emptyList())
        assertTrue(plan.narouOrder.isEmpty())
        assertTrue(plan.nonNarouOrder.isEmpty())
    }
}
