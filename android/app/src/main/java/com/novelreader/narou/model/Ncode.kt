package com.novelreader.narou.model

/**
 * なろう作品を一意に指す Nコード（例: "N1234AB"）のドメイン型。
 *
 * なぜ value class（生 String でなく）か:
 *  ドメイン/VM/UI 層では ncode と bookId・query 等の別の String が隣り合って受け渡される
 *  （例: `linkNcode(bookId, ncode)`）。生 String 同士は引数取り違えにコンパイラが無防備だが、
 *  @JvmInline value class にすると型で取り違えを弾ける。実行時は String に unbox されるため
 *  割り当てコストは無く、Compose からは Stable 扱い（下地の String が Stable なため）で受け取れる。
 *
 * なぜ正規化を集約せず「素通し」か（挙動不変の優先）:
 *  なろう ncode には大文字/小文字の表記ゆれがあり、既存コードは正規化を「用途ごとに別々」に施している
 *  ―― URL 生成は `.trim().lowercase()`（[com.novelreader.narou.narouWorkUrl] 等）、
 *  紐付け保存は `.trim().uppercase()`（NcodeLinkSheet）、詳細取得は `.trim()` のみ、と一貫していない。
 *  value class は init ブロックを持てず（本体は単一プロパティのみ）、かつ equals は下地 String に委譲する
 *  ため、生成経路で一様な正規化を強制すると URL 入力や保存値・一致判定のいずれかで挙動が変わる。
 *  そこで本型は「値をそのまま包む」だけに留め、正規化は各既存サイトで `.value` に対して従来どおり施す。
 *  ＝境界（Room `BookEntity.ncode` / Moshi `NarouNovel.ncode` / Retrofit `NarouApiService`）は String のまま、
 *  ドメイン/VM/UI に入る/出る所でのみ [Ncode] へ包む/ほどく。
 */
@JvmInline
value class Ncode(val value: String)
