package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * 引用符は縦書き用（〝〞〟）も欧文（“”‘’）も位置替え。
     *
     * なぜ同じ扱いか: UAX#50 の Vertical_Orientation が 7 字とも **Tr**（＝縦字形への変形が主段・
     * 変形できない書体では回転が fallback 段）で**同値**のため。欧文側は 2026-09-03 の規範照合まで
     * 表に無く UPRIGHT 既定＝Tr のどちらの段でもない第3の状態だった（実データ 7,319 件）。
     * 族を割ると「なぜこの字だけ違うのか」を説明できないので揃える。
     */
    @Test
    fun `引用符は縦書き用も欧文も位置替え`() {
        assertClass(CharClass.PUNCT_REPOSITION, "〝〞〟")
        assertClass(CharClass.PUNCT_REPOSITION, "“”‘’")
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

    /**
     * 2026-09-07 裁定の固定 (a): 欧文引用符 “”‘’ は **PUNCT_REPOSITION のまま・自前回転へ倒さない**。
     *
     * UAX#50 は 4 字とも Tr（変形が主段・回転が fallback 段）で、PGEM10 では主段が出ず正立に見える。
     * それでも回転へ倒さない理由は [CharClassifier] の PUNCT_REPOSITION_CHARS の KDoc に書いた 3 点
     * （①自前回転はセル中心 pivot で、Tr の主段「回転＋隅への位置替え」の代用にならない
     *   ②焼き込むと vert が効く書体で二重変換になる ③実機の版面で破綻していない）。
     * 実データ 7,319 件の見えが変わる変更なので、無言で覆せないようにここで名指しで留める。
     */
    @Test
    fun `欧文引用符は位置替えのまま自前回転へ倒さない（2026-09-07 裁定）`() {
        for (ch in "“”‘’") {
            assertEquals("'$ch' は位置替えのまま", CharClass.PUNCT_REPOSITION, CharClassifier.classify(ch.toString()))
            assertFalse(
                "'$ch' を MANUAL_ROTATE_REQUIRED へ入れてはいけない（Tr の主段は回転ではない）",
                ch.toString() in VertFeatureCoverage.MANUAL_ROTATE_REQUIRED,
            )
        }
    }

    /**
     * 2026-09-07 裁定の固定 (b): ￣ゝヽヾ は **CharClass としては 4 字とも正立のまま**。
     *
     * ￣ は vert の縦字形だけを使う（次のテストで固定）が、向きは正立＝
     * CharClass を PUNCT_REPOSITION へ移すのは誤り（advance と禁則の意味が変わる）。
     * ゝヽヾ は UAX#50 が U（Upright）と宣言＝規範どおり正立。
     */
    @Test
    fun `全角マクロンと繰返し記号は正立のまま`() {
        assertClass(CharClass.UPRIGHT, "￣ゝヽヾ")
    }

    /**
     * 2026-09-07 裁定の固定 (b): 正立のまま vert を適用するのは ￣U+FFE3 **だけ**。
     *
     * ￣＝UAX#50 Tr かつ PGEM10 実測で主段が実在（横棒 64×4 → 右端の縦棒 3×66）＝適用する。
     * ゝヽヾ＝UAX#50 U かつ実測の bounds 差は最大 3px（ヒンティング差＝縦字形ではない）＝適用しない。
     * `changed=true` だけを根拠に足すと ゝヽヾ が混ざるので、この集合は名指しで固定する。
     */
    @Test
    fun `正立のまま vert 縦字形を使う字は ￣ だけ`() {
        assertEquals(setOf("￣"), VertFeatureCoverage.VERT_FORM_REQUIRED_ON_UPRIGHT)
        assertTrue("￣ は vert 適用", VertFeatureCoverage.usesVertFormWhileUpright("￣"))
        for (ch in "ゝヽヾ亜あア？０") {
            assertFalse("'$ch' に vert を適用してはいけない", VertFeatureCoverage.usesVertFormWhileUpright(ch.toString()))
        }
        assertFalse("空文字は適用しない", VertFeatureCoverage.usesVertFormWhileUpright(""))
    }

    @Test
    fun `vert非対応の自前回転必須リストを固定`() {
        // P0-1 実測（…‥；−）＋ 2026-07-17 v2 追加実測（―=U+2015 が vert 無効。—‐– は未計測の同系＝防御的に自前回転）。
        // －(U+FF0D) は 2026-09-03 追加（ADR 0041 決定1）。それまで抽出が FF0D→2212 へ正規化していたため
        // この字は実データに現れず、リストにも入っていなかった。写像の撤去で**実際に出てくる字**になった
        // （実測 N6169DZ 本文 1,057 件）ので、同形の 2212 と同じく自前回転側へ入れる。
        assertEquals(
            setOf("…", "‥", "；", "−", "－", "―", "—", "‐", "–"),
            VertFeatureCoverage.MANUAL_ROTATE_REQUIRED,
        )
    }
}
