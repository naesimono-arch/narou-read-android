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
 * 正規化の設計（2026-07-27 型化）:
 *  コンストラクタと equals は従来どおり「素通し」（値をそのまま包む・正規化しない）。
 *  value class は init 正規化を強制すると URL 入力・保存値・一致判定のいずれかで挙動が変わるため、
 *  生成経路は不変のまま、**用途別の正規化だけを本型のアクセサへ集約**する:
 *   - [storageKey] … trim+大文字。Room 保存キー・重複突合・通知 tag（NcodeLinkSheet 由来の保存正規化）
 *   - [urlSlug]    … trim+小文字。なろう URL パス生成・URL 照合（narouWorkUrl 等）
 *   - [apiParam]   … trim のみ。なろう API へのクエリ（API 側が大小を無視するため case は保持）
 *  手書きの `.trim().uppercase()` 等を各サイトに分散させない（表記ゆれバグの単一防御点）。
 *  ＝境界（Room `BookEntity.ncode` / Moshi `NarouNovel.ncode` / Retrofit `NarouApiService`）は String のまま、
 *  ドメイン/VM/UI に入る/出る所でのみ [Ncode] へ包む/ほどく。
 */
@JvmInline
value class Ncode(val value: String) {

    /** 保存・照合キー（trim＋大文字）。web_novels / web_reading_progress の Room キー、
     *  取込済み ncode の突合、新着話通知 tag の組み立てに使う。
     *  なぜ引数なし uppercase() か: Kotlin の引数なし版は不変ロケール（Locale.ROOT 相当）の
     *  Unicode 規則で変換するため、既存サイトの `uppercase()`/`uppercase(Locale.ROOT)` と 1 ビットも変わらない。 */
    val storageKey: String get() = value.trim().uppercase()

    /** URL スラッグ（trim＋小文字）。なろうの Web サーバーは URL パスの Nコードを小文字で
     *  要求するため、URL 生成（narouWorkUrl/narouEpisodeUrl）と URL 照合はこの形で行う。
     *  引数なし lowercase() は不変ロケール＝既存の `lowercase(Locale.ROOT)` と同一挙動。 */
    val urlSlug: String get() = value.trim().lowercase()

    /** なろう API へ渡すパラメータ形（trim のみ・大小は保持）。API 側が ncode の大小を
     *  無視するため case 正規化は不要＝従来の「詳細取得は trim のみ」流儀をそのまま型に移す。 */
    val apiParam: String get() = value.trim()

    /** 表記ゆれ（前後空白・大小文字）を無視した同一作品判定＝storageKey 同士の一致。
     *  型化時は既存サイト（NovelDetailViewModel.isImported）の equals(ignoreCase) を素通しで
     *  移していたが、保存・突合の正本は storageKey（trim＋大文字）であり、非 ASCII では
     *  ignoreCase 比較と uppercase 突合の結果が割れ得る（例: "ß"）＝同一作品判定だけが
     *  第4流儀として残っていた。2026-07-27 ユーザー裁定で storageKey 突合へ統一
     *  （ASCII の実在 Nコードでは従来と同値＝実挙動は不変）。 */
    fun sameWorkAs(other: Ncode): Boolean = storageKey == other.storageKey

    companion object {
        /** 生入力（検索結果の ncode・手動入力欄）から「値そのものを保存キー形に正規化した」
         *  Ncode を作る境界用ファクトリ。NcodeLinkSheet の確定のように、下流（linkNcode→Room）が
         *  値を素通しで永続化するため、包む時点で正規化を済ませる必要があるサイトで使う。 */
        fun normalizedForStorage(raw: String): Ncode = Ncode(Ncode(raw).storageKey)
    }
}
