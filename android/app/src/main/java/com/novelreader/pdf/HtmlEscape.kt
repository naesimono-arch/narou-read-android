package com.novelreader.pdf

/**
 * Python html.escape(s, quote=True) の忠実移植（パッケージ共有のトップレベルヘルパー）。
 *
 * quote=True が Python の既定＝ " と ' もエスケープする。ChapterProcessor（本文）と
 * HtmlExporter（作品名・章タイトル）の双方が Python 出力とバイト等価であるために、
 * 同一実装を 1 箇所へ集約する。**なぜ集約するか**: 複製すると片方だけ修正して
 * escape 挙動が drift し、バイト等価ゴールデンが静かに崩れる事故を招くため。
 *
 * 置換順は & を最優先（後続の &lt; 等の & を二重エスケープしないため）＝Python 実装と同一。
 */
internal fun htmlEscape(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
