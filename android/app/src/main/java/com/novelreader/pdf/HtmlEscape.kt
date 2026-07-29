package com.novelreader.pdf

/**
 * HTML エスケープ（パッケージ共有のトップレベルヘルパー。移植元 html.escape(s, quote=True)）。
 *
 * `"` と `'` も実体参照にする（属性値へ本文由来の文字列が入っても壊れないようにするため）。
 * ChapterProcessor（本文）と HtmlExporter（作品名・章タイトル）の双方が同一実装を使う。
 * **なぜ 1 箇所へ集約するか**: 複製すると片方だけ修正して escape 挙動が drift し、
 * `golden_html/` とのバイト等価（HtmlExporterGoldenTest）が静かに崩れる事故を招くため。
 *
 * 置換順は & を最優先（後続の `&lt;` 等が生む & を二重エスケープしないため）＝順序は入替え不可。
 */
internal fun htmlEscape(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
