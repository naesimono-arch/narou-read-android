package com.novelreader.scrape

import org.jsoup.nodes.Element

/**
 * `<ruby>` 要素 → 中間ルビ記法 `|base《reading》`（ASCII パイプ）への変換。KakuyomuAdapter と
 * GenericSiteAdapter が共有する唯一の実装（両者にコピペで存在していたものを1本化）。
 *
 * なぜ共有ヘルパへ切り出すか: ルビ変換は「RP（読み仮名の括弧）を捨てる・`<rb>` 包み/省略の双方を base として拾う・
 * jsoup がタグ名を小文字化するため大文字 `<RUBY>` も同じ経路で処理できる」という**出力契約**そのもので、
 * サイトが違っても不変。二重実装は契約のドリフト源になるため1箇所に集約する（Kakuyomu の既存出力は不変）。
 *
 * 契約: `|base《reading》` の ASCII パイプは下流 `applyRuby`（読書画面のルビ描画）の必須入力形式。
 * base か reading のどちらかが空なら記法化せず可視テキスト（`ruby.text()`）へフォールバックする。
 */
internal fun convertRuby(ruby: Element): String {
    val reading = ruby.select("rt").text().trim()
    // rt（読み）と rp（読みを囲む括弧）を除いた残り＝base。`<rb>` 包みでも省略でも同じ結果になる。
    val base = ruby.clone().apply { select("rt, rp").remove() }.text().trim()
    return if (base.isNotEmpty() && reading.isNotEmpty()) "|$base《$reading》" else ruby.text()
}
