package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val htmlDirPath: String,
    // 末尾に追加：既存の位置引数呼び出し BookEntity(id, title, htmlDirPath) が壊れないよう
    // デフォルト値 "" を設定。STEP 9 で author 取得後に渡すようになる。
    val author: String = "",
    // 本棚を「最近の活動順」に並べるための追加日時（UNIX ミリ秒）。
    // 未読の本はこの値で、既読の本は progress.lastReadAt で並ぶ（BookDao 参照）。
    // default 0 は Migration 補完値（既存行は 0＝最後尾クラスタでタイトル順）。
    val addedAt: Long = 0L,
    // PDF↔Web継続読書（目玉①）: この蔵書に対応するなろう作品の Nコード。
    // null = 未紐付け。紐付けはユーザー確定操作（候補選択 or 手動入力）でのみ行う。
    // なぜ自動判定で埋めないか: title 一致だけでは同名別作品の誤紐付けリスクがあり、
    // 誤った「続き」へ誘導すると読書体験を壊すため（候補提示→人間の確定を必須にする）。
    val ncode: String? = null,
    // 取込元 PDF バイト列の SHA-256（小文字16進）。F-G 恒久策：同一内容の PDF を
    // 別 URI（別パスから選び直し）で再取込したとき、重い変換を走らせる前に弾くための内容指紋。
    // null = 判定不能。既存行（本カラム追加=v11 より前に取り込んだ蔵書）は Migration で NULL 補完され、
    // ハッシュ照合では一致しない（＝旧取込分は変換前遮断の対象外で、従来どおり抽出後の
    // title＋author 照合に委ねる＝多層防御の縮退として許容する）。
    val contentSha256: String? = null,
    // 栞書影の「先端意匠の種類」を取込時に真の乱数で1回だけ抽選し永続化した値（0..SHIORI_TIP_COUNT-1）。
    // null = 未抽選（v19 より前に取り込んだ既存蔵書。Migration で NULL 補完）。描画側は null なら従来どおり
    // title 由来の決定論値へフォールバックする＝既存本の見た目は一切変えない。
    // なぜ永続化するか: 従来は title ハッシュで先端・棒長も決めていたが、「同じ本＝同じ絵」を保ちつつ
    // 本ごとの個体差を強めたいオーナー要望。取込時に一度だけ引いて固定すれば以後は不変になる。
    val shioriTipIndex: Int? = null,
    // 栞書影の「棒の長さ（高さ比）」を取込時に真の乱数で1回だけ抽選し永続化した値（SHIORI_LEN_FRAC_MIN..MAX）。
    // null = 未抽選＝描画側で title 由来の決定論値へフォールバック（shioriTipIndex と同方針）。
    val shioriLenFrac: Float? = null,
    // 取込元 PDF の SAF ドキュメント URI（`content://…`）。本削除時に「取込元PDF本体も削除する」を成立させるための永続化。
    // ⚠ 直下の sourceUrl と列名が酷似するが意味は別物: こちら sourceUri は端末内 PDF を指す content:// スキーム＝
    //    「削除機能」用（feat/delete-source-pdf 由来）。sourceUrl は Web 上の作品ページを指す https:// ＝「Web取込元」用。混同禁止。
    // 当ブランチでは v20 スキーマ整合（並列 feat/delete-source-pdf 先着 v20 のパス接続）のための複製定義であり、
    // この列に値を書き込むコード（addBook 側の権限保持ロジック）は追加しない＝削除機能の実装本体は feat/delete-source-pdf 側。
    // null = 取込元PDFを削除できる本ではない（既存行および当ブランチ取込分は常に NULL）。
    val sourceUri: String? = null,
    // Web 取込元の作品 URL（`https://…`＝カクヨム等の作品ページ）。汎用Web小説DL基盤で取り込んだ本の出所を記録する。
    // ⚠ 直上の sourceUri と列名が酷似するが意味は別物: こちら sourceUrl は Web 上の作品ページ（https://）＝再取得・
    //    続話チェックの起点。sourceUri は端末内 PDF（content://）＝削除機能用。列名の U-r-i / U-r-l 一文字差ゆえ取り違え注意。
    // null = PDF 由来の蔵書（Web 取込でないため出所 URL を持たない）。
    val sourceUrl: String? = null,
    // Web 取込元のサイトアダプタキー（例 "kakuyomu"）。sourceUrl の URL をどのサイト抽出器で解釈したかを保持し、
    // 再取得時に同じアダプタへ回すための識別子。null = PDF 由来（Web 取込でない）。
    val sourceSite: String? = null,
) {
    /**
     * 保存済み htmlDirPath が実在すればそれを、無ければ bookId から再導出したディレクトリを返す（復元耐性の下地）。
     *
     * なぜ（UX監査 portable・公理18 D 再結合キー）: htmlDirPath は端末絶対パスなので、メタデータをバックアップ
     * から別端末へ復元すると古い端末のパスを指し resolvedFile==null で本文が開けない。HTML 実体の置き場は
     * 「filesDir/novels/<bookId>」という決定的規約（[resolveHtmlDir]）なので、保存パスが外れても bookId から
     * 復元できる。実体そのものの復元は別レイヤ（C2 バックアップ層別・contentSha256 での再結合）だが、位置を
     * 保存パスに固定依存しない導出をここへ一元化しておくことで、実体が揃った後の graceful な位置復帰を可能にする。
     */
    fun resolvedHtmlDir(filesDir: File): File {
        val stored = File(htmlDirPath)
        return if (stored.exists()) stored else resolveHtmlDir(filesDir, id)
    }

    companion object {
        /** HTML 実体を格納する filesDir 直下のサブディレクトリ名（取込・掃除・復元で共有する単一の規約）。 */
        const val NOVELS_SUBDIR = "novels"

        /** bookId から HTML ディレクトリを再導出する（filesDir/novels/<bookId>）。取込時の書き出し先・
         *  孤立HTML掃除の走査・復元時の位置復帰が同一規約を通るための一元化点。 */
        fun resolveHtmlDir(filesDir: File, bookId: String): File = File(filesDir, "$NOVELS_SUBDIR/$bookId")
    }
}
