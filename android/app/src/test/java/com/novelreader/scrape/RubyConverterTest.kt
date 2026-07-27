package com.novelreader.scrape

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * convertRuby（`<ruby>` → 中間ルビ記法 `|base《reading》`）の出力契約テスト。
 * KakuyomuAdapter / GenericSiteAdapter が共有する唯一の実装のため、ここで契約を直接固定する
 * （アダプタ経由の統合テストは KakuyomuAdapterUnitTest 側にあり、本テストは変換関数単体の境界を担う）。
 */
class RubyConverterTest {

    /** 合成 HTML から最初の `<ruby>` 要素を取り出すヘルパー。 */
    private fun ruby(html: String) = Jsoup.parse(html).selectFirst("ruby")!!

    @Test
    fun standardRuby_convertsToIntermediateNotation() {
        // 代表形: rt 直置き（カクヨム等のレンダ形）。ASCII パイプ＋二重山括弧が下流 applyRuby の必須入力。
        assertEquals("|漢字《かんじ》", convertRuby(ruby("<ruby>漢字<rt>かんじ</rt></ruby>")))
    }

    @Test
    fun rbWrappedBase_sameResultAsBareBase() {
        // `<rb>` 包みでも省略でも base の拾い方が変わらない（契約＝KDoc 明記）。
        assertEquals("|星《ほし》", convertRuby(ruby("<ruby><rb>星</rb><rt>ほし</rt></ruby>")))
    }

    @Test
    fun rpParentheses_discarded() {
        // rp（読み仮名の括弧）は捨てる契約。非対応ブラウザ向け括弧が本文へ漏れないこと。
        assertEquals(
            "|漢字《かんじ》",
            convertRuby(ruby("<ruby>漢字<rp>（</rp><rt>かんじ</rt><rp>）</rp></ruby>"))
        )
    }

    @Test
    fun missingReading_fallsBackToVisibleText() {
        // rt 無し＝reading 空 → 記法化せず可視テキストへフォールバック（壊れたマークアップで本文を失わない）。
        assertEquals("漢字", convertRuby(ruby("<ruby>漢字</ruby>")))
    }

    @Test
    fun missingBase_fallsBackToVisibleText() {
        // base 空（rt のみ）でも同様にフォールバック。ruby.text() は rt の中身を含むため読みが残る。
        assertEquals("かんじ", convertRuby(ruby("<ruby><rt>かんじ</rt></ruby>")))
    }

    @Test
    fun uppercaseTags_handledViaJsoupLowercasing() {
        // jsoup がタグ名を小文字化するため大文字 `<RUBY>` も同一経路で変換される（契約＝KDoc 明記）。
        assertEquals("|漢字《かんじ》", convertRuby(ruby("<RUBY>漢字<RT>かんじ</RT></RUBY>")))
    }

    @Test
    fun multipleRtSegments_flattenedWithSpaceSeparator() {
        // 特殊形: グループルビ（rt 複数）。現行実装は select("rt").text() で全 rt を空白結合し、
        // base も連結されるため「1つのルビ」に平坦化される。理想形の主張ではなく**現行挙動の固定**
        // （変更時はこのテストを意図的に更新して差分を可視化する）。
        assertEquals(
            "|大空《おお ぞら》",
            convertRuby(ruby("<ruby>大<rt>おお</rt>空<rt>ぞら</rt></ruby>"))
        )
    }

    @Test
    fun surroundingWhitespace_trimmedFromBaseAndReading()  {
        // base/reading とも trim される（マークアップ整形由来の空白が記法へ混入しない）。
        assertEquals("|漢字《かんじ》", convertRuby(ruby("<ruby> 漢字 <rt> かんじ </rt></ruby>")))
    }
}
