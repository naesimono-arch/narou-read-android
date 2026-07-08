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

/**
 * ページ単位のディスカバリ取得結果（結果一覧のフルページング＝F-J 用）。
 *
 * [DiscoveryResult] に「次ページが取得できるか」の判定材料を1つ足したもの。
 *
 * @param allcount        条件に一致する全作品数（先頭要素の allcount）。初回ページで確定し、以降のページでは
 *                        VM 側が保持する（load-more では未使用のため 0 でも害はない）。
 * @param novels          このページで返ってきた作品スライス（先頭の allcount 専用要素は除去済み）。
 * @param reachedApiLimit 次ページが**なろうAPIのエンベロープ**に阻まれて取得不能なとき true。
 *                        通常検索は st（表示開始位置）最大 2000、SHORT+RENSAI マージ経路は lim 最大 500 が壁
 *                        （narou_api_manual.md §3.2）。全件到達（loaded>=allcount）とは区別され、VM が
 *                        「取得上限に達しました」表示に使う。
 */
data class DiscoveryPage(
    val allcount: Int,
    val novels: List<NarouNovel>,
    val reachedApiLimit: Boolean,
)
