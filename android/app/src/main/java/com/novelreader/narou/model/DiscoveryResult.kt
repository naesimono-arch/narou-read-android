package com.novelreader.narou.model

/**
 * ディスカバリ取得のドメイン結果。
 *
 * API の生レスポンスは「先頭要素=allcount / 以降=作品」という配列だが、UI/ViewModel が扱いやすいよう
 * Repository 層で **allcount と作品本体を分離**した形にして返す（[NovelApiRepository] が `list.drop(1)` で本体化）。
 *
 * @param allcount 条件に一致する全作品数（ページング総数表示等に使う。先頭要素の allcount）。
 * @param novels   実際に返ってきた作品（先頭の allcount 専用要素を除いたもの）。
 */
data class DiscoveryResult(
    val allcount: Int,
    val novels: List<NarouNovel>,
)
