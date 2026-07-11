package com.novelreader.model

/**
 * 蔵書を一意に指す書籍ID（`BookEntity.id`＝UUID 先頭8桁）のドメイン型。
 *
 * なぜ value class（生 String でなく）か（系統4 Ncode 型付けの続き）:
 *  進捗保存 API は `saveScrollPosition(bookId, filename, …)` のように bookId と章ファイル名という
 *  「同じ String 型が隣り合う」シグネチャを持ち、生 String 同士では引数の取り違えにコンパイラが
 *  無防備だった。bookId を [BookId]・ファイル名を [ChapterFilename] と別の value class にすることで
 *  取り違えを型で弾ける（[Ncode][com.novelreader.narou.model.Ncode] と同じ動機）。実行時は String に
 *  unbox されるため割り当てコストは無く、Compose からは下地 String が Stable なため Stable 扱いで受け取れる。
 *
 * なぜ正規化・検証を持たせないか（挙動不変の優先）: 既存コードは bookId を UUID 由来の不変トークンとして
 *  そのまま比較・キー化しており、正規化の余地は無い。value class は init を持てず（本体は単一プロパティ）、
 *  余計な検証を足すと既存の等価比較・保存が変わりうるため「値をそのまま包む」だけに留める。
 *  ＝永続化境界（Room `BookEntity.id`/`ProgressEntity.bookId` の DAO 呼び出し）は String のまま、
 *  ドメイン/VM/UI に入る/出る所でのみ [BookId] へ包む/ほどく（`.value` で unwrap）。
 */
@JvmInline
value class BookId(val value: String)

/**
 * 章HTMLのファイル名（例: "chap_1.html" / 目次は "index.html"）のドメイン型。
 *
 * なぜ value class か: [BookId] と対で導入する。進捗保存 API で bookId と隣り合う String を別型にして
 *  引数取り違えを型で防ぐのが目的（詳細は [BookId] の KDoc）。型付けの範囲は「bookId と隣接して
 *  取り違えうる」進捗保存シグネチャ（saveProgress/saveScrollPosition）の filename 引数に限定する。
 *  ナビゲーション履歴・目次エントリ・パス解決など画面内部のファイル名運搬は従来どおり String のまま
 *  （そこまで型付けを広げると TocEntry 等のモデルや nav ルート文字列へ連鎖するため線を引く）。
 *
 * なぜ正規化・検証を持たせないか: 既存コードはファイル名を「index.html か否か」の等価判定や
 *  `File(htmlDirPath, name)` の結合にそのまま使う。value class に検証を足すと既存判定が変わりうるため
 *  素通し（[Ncode][com.novelreader.narou.model.Ncode]・[BookId] と同方針）。境界では `.value` で unwrap する。
 */
@JvmInline
value class ChapterFilename(val value: String)
