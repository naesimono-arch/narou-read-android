package com.novelreader.ui.skins.k

import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定「きせかえ」行の副文が、**値（括弧書き）の途中では折り返さない**ことの回帰。
 *
 * 何を守るか（2026-07-30 実機観察）: 実機 360dp では副文は必ず2行になる。既定の貪欲改行は値の途中で
 * 割り（`）` を行頭に置かない禁則が働くので「ン）」が道連れで落ち、閉じ括弧だけこぼれたように見える）。
 * WORD JOINER(U+2060) を値の各文字間へ挟むことで割れ目を「（」の直前＝文と値の境目へ固定する。
 *
 * 見た目そのものは golden（SettingsScreenKScreenshotTest）が持つ。ここが縛るのは**構造的な不変条件**
 * ＝「値は一塊・文側は自由に折り返せる・可視文字列は一字も変わらない」の3点で、これは画素比較より
 * 早く・原因を名指しで壊れる。
 */
class WardrobeRowDescriptionTest {

    /** 行分割禁止（WORD JOINER）。字幅を持たず画面には現れない。 */
    private val wj = '\u2060'

    /** 副文のうち値の部分（「（」以降）。文と値の境目を境界文字そのもので切り出す。 */
    private fun valuePartOf(description: String) = description.substring(description.indexOf('（'))

    private fun visible(description: String) = description.filter { it != wj }

    @Test
    fun `見える文字列はモックの副文と一字も違わない`() {
        // 不可視の WJ を除いた見え方が正本（settings-D.html .rd）と同一＝組版の手当てが文言を変えていない。
        val skin = Skin.WAMODERN_D
        assertEquals(
            "本棚や画面の装いを変える（現在: ${skin.displayName}）",
            visible(wardrobeRowDescription(skin.displayName)),
        )
    }

    @Test
    fun `括弧の値は一塊＝内部に行分割の余地が無い`() {
        val value = valuePartOf(wardrobeRowDescription(Skin.WAMODERN_D.displayName))
        val chars = visible(value)
        assertTrue("値が空＝副文の組み立てが壊れている", chars.length >= 2)
        // 隣り合う2文字の間に必ず WJ が入る＝どの位置でも分割できない（＝「和モダ／ンD」が起きない）。
        for (i in 0 until chars.length - 1) {
            val joined = "${chars[i]}$wj${chars[i + 1]}"
            assertTrue("値の中に分割可能な位置が残っている: ${chars[i]}${chars[i + 1]}", value.contains(joined))
        }
    }

    @Test
    fun `文の側は分割可能なまま＝2行目へ落ちるのは値ごと`() {
        // 文側にも WJ を入れると1行に押し込もうとして溢れる。割れ目は「（」の直前に1か所だけ要る。
        val description = wardrobeRowDescription(Skin.WAMODERN_D.displayName)
        assertFalse("文側にも分割禁止が漏れている", description.substringBefore('（').contains(wj))
    }

    @Test
    fun `全スキンの装い名で成立する（名前の長さに依存しない）`() {
        Skin.entries.forEach { skin ->
            assertEquals(
                "本棚や画面の装いを変える（現在: ${skin.displayName}）",
                visible(wardrobeRowDescription(skin.displayName)),
            )
        }
    }
}
