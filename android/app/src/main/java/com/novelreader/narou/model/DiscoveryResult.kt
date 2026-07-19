package com.novelreader.narou.model

import com.novelreader.discovery.model.WorkSummary

/**
 * ディスカバリ取得のドメイン結果（Repository の公開戻り値）。
 *
 * API の生レスポンスは「先頭要素=allcount / 以降=作品」という配列だが、UI/ViewModel が扱いやすいよう
 * Repository 層で **allcount と作品本体を分離**し、さらに作品はサイト非依存の [WorkSummary] へ写像して返す
 * （なろう固有 DTO NarouNovel は narou/ の内側に閉じ込め UI へ漏らさない＝P5 発見層の脱なろう）。
 *
 * @param allcount 条件に一致する全作品数（ページング総数表示等に使う。先頭要素の allcount）。
 * @param novels   実際に返ってきた作品（要約モデル。allcount 専用センチネル・title/writer 欠落は写像時に除去済み）。
 */
data class DiscoveryResult(
    val allcount: Int,
    val novels: List<WorkSummary>,
)

/**
 * ページ単位のディスカバリ取得結果（結果一覧のフルページング＝F-J 用）。
 *
 * [DiscoveryResult] に「次ページが取得できるか」の判定材料を1つ足したもの。
 *
 * @param allcount        条件に一致する全作品数（先頭要素の allcount）。初回ページで確定し、以降のページでは
 *                        VM 側が保持する（load-more では未使用のため 0 でも害はない）。
 * @param novels          このページで返ってきた作品スライス（要約モデル。センチネル・欠落は写像時に除去済み）。
 * @param reachedApiLimit 次ページが**なろうAPIのエンベロープ**に阻まれて取得不能なとき true。
 *                        通常検索は st（表示開始位置）最大 2000、SHORT+RENSAI マージ経路は lim 最大 500 が壁
 *                        （narou_api_manual.md §3.2）。全件到達（loaded>=allcount）とは区別され、VM が
 *                        「取得上限に達しました」表示に使う。
 */
data class DiscoveryPage(
    val allcount: Int,
    val novels: List<WorkSummary>,
    val reachedApiLimit: Boolean,
)
