package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CharClassifier の分類表を P0-1 実測記録に基づき全数固定化する。
 * 正本: .claude/plans/vertical-mode-p0-measurements-2026-07-17.md（P0-1 の vert 実効/正立/回転リスト）。
 */
class CharClassifierTest {

    private fun assertClass(expected: CharClass, chars: String) {
        for (ch in chars) {
            assertEquals("'$ch' の分類", expected, CharClassifier.classify(ch.toString()))
        }
    }

    // --- PUNCT_REPOSITION（右上寄せ系の位置替え）: 句読点＋小書き仮名 ---

    @Test
    fun `句読点は位置替え`() {
        assertClass(CharClass.PUNCT_REPOSITION, "、。，．")
    }

    @Test
    fun `小書き仮名は位置替え`() {
        assertClass(CharClass.PUNCT_REPOSITION, "ぁぃぅぇぉっゃゅょゎゕゖ")
        assertClass(CharClass.PUNCT_REPOSITION, "ァィゥェォッャュョヮヵヶ")
    }

    // --- ROTATE（90度回転）: 括弧・長音・ダッシュ類・半角英数字 ---

    @Test
    fun `括弧類は回転`() {
        assertClass(CharClass.ROTATE, "「」『』（）〔〕［］｛｝〈〉《》【】")
    }

    @Test
    fun `長音波ダッシュ約物一部は回転`() {
        assertClass(CharClass.ROTATE, "ー～〜…‥—―‐–＝：；−｜")
    }

    @Test
    fun `半角英数字は回転（文脈非依存の欧文横倒し既定）`() {
        assertClass(CharClass.ROTATE, "0123456789")
        assertClass(CharClass.ROTATE, "ABCdefXYZ")
    }

    // --- UPRIGHT（正立）: 漢字・仮名・全角英数字・約物「？！・」・その他 ---

    @Test
    fun `漢字仮名カナは正立`() {
        assertClass(CharClass.UPRIGHT, "亜雨あいアイ")
        assertEquals(CharClass.UPRIGHT, CharClassifier.classify("ｱ")) // 半角カナ
    }

    @Test
    fun `全角英数字は正立`() {
        assertClass(CharClass.UPRIGHT, "ＡＢＣ")
        assertClass(CharClass.UPRIGHT, "０１２")
    }

    @Test
    fun `約物クエスチョン感嘆中黒は正立（P0実測）`() {
        // P0-1: 縦書き約物は正立が正解（vert が効かないのが正しい）。
        assertClass(CharClass.UPRIGHT, "？！・")
    }

    // --- 未知文字は UPRIGHT に倒す（防御的既定） ---

    @Test
    fun `未知文字は正立に倒す`() {
        assertEquals(CharClass.UPRIGHT, CharClassifier.classify("🎉")) // サロゲートペア
        assertEquals(CharClass.UPRIGHT, CharClassifier.classify("한")) // ハングル
        assertEquals(CharClass.UPRIGHT, CharClassifier.classify("")) // 空文字も安全に正立
    }

    // --- vert フォールバック必須リスト（P0-1 実測） ---

    @Test
    fun `vert非対応の自前回転必須リストを固定`() {
        // P0-1 実測（…‥；−）＋ 2026-07-17 v2 追加実測（―=U+2015 が vert 無効。—‐– は未計測の同系＝防御的に自前回転）。
        assertEquals(
            setOf("…", "‥", "；", "−", "―", "—", "‐", "–"),
            VertFeatureCoverage.MANUAL_ROTATE_REQUIRED,
        )
    }
}
