package com.novelreader.repository

import com.novelreader.narou.model.Ncode
import java.io.File

/**
 * 責務: なろう縦書きPDF取込のDL一時領域（cacheDir/pdf_import/）の所在解決と、蔵書（ncode）との名前照合。
 *
 * この領域は PdfImportViewModel（DL 保存）だけが書き込む単一書き手のアプリ専用領域で、
 * SAF ピッカーからユーザーが辿ることはできない。なろう取込の本は sourceUri/sourceUrl とも NULL
 * （FileProvider の content:// は永続権限を取れず記録されない＝PdfBookImporter ⑤のコメント）のため、
 * 本文欠落時にここへ残る DL 実体（<ncode>.pdf）が「取込元へ到達できる唯一の記録」になる
 * （ReimportPlan.AutoCachePdf の復旧資源）。所在の規約をこの1点に集約し、
 * 保存側・照合側・掃除側でディレクトリ名やファイル名規約が食い違わないようにする。
 *
 * java.io.File だけに依存する（ContentResolver 非依存）＝一時ディレクトリで JVM 単体テストできる。
 */
internal object NarouPdfCache {

    /** DL 一時領域のサブディレクトリ名。file_paths.xml の cache-path `pdf_import/`（FileProvider の
     *  公開範囲）と一致させること＝ここを変えるときは file_paths.xml も同時に変える。 */
    const val SUBDIR = "pdf_import"

    /** DL 一時領域の実ディレクトリ（cacheDir/pdf_import/）。 */
    fun dir(cacheDir: File): File = File(cacheDir, SUBDIR)

    /**
     * ncode に対応する取込時 PDF（<ncode>.pdf）が現存すればそれを返す（無ければ null）。
     *
     * なぜ File(dir, "$ncode.pdf") の存在チェックでなく列挙＋正規化比較か:
     * 保存名は DL 時の URL / Content-Disposition 由来（PdfImportViewModel.deriveFilename）で、
     * DB の books.ncode とは大文字小文字が一致する保証が無い（ncode の同一性判定の正本は
     * Ncode.storageKey＝trim＋大文字）。cache 内は数ファイル規模なので列挙コストは無視できる。
     * OS が逼迫時に cache を消していれば null＝呼び出し側は正直に SAF ピッカー分岐へ落ちる。
     */
    fun findFor(cacheDir: File, ncode: String): File? {
        val key = Ncode(ncode).storageKey
        return dir(cacheDir).listFiles()?.firstOrNull { f ->
            f.isFile &&
                f.extension.equals("pdf", ignoreCase = true) &&
                Ncode(f.nameWithoutExtension).storageKey == key
        }
    }

    /** ncode に対応する取込時 PDF を削除する（不在なら no-op）。呼び出し条件（いつ消してよいか）の
     *  設計は LibraryDeleter 側のコメントが正本＝ここは名前照合と削除の機械部分だけを持つ。 */
    fun deleteFor(cacheDir: File, ncode: String) {
        findFor(cacheDir, ncode)?.delete()
    }

    /**
     * 起動時の孤児掃除: どの蔵書（keepNcodes）にも対応せず、再開待ちの取込（keepFileNames）からも
     * 参照されない cache 内ファイルを削除する。
     *
     * PDF 以外・ncode 名でないファイルも掃除対象に含める理由: この領域の書き手は DL 保存
     * （PdfImportViewModel）だけで、正当に残ってよいのは〈蔵書の復旧資源（ncode 対応の PDF）〉と
     * 〈pending_jobs が再開に使う DL 実体〉の2種のみ。それ以外はプロセス kill で残った部分DL等の
     * 残骸で、名前を問わず回収してよい（cleanOrphanHtmlDirs の「DB に無い＝書きかけ」と同じ判定思想）。
     *
     * @param keepNcodes 現存する蔵書の ncode（Ncode.storageKey 形）。対応する PDF は②AutoCachePdf の復旧資源＝残す。
     * @param keepFileNames pending_jobs が参照する cache 内ファイル名（再開予定の DL 実体＝消すと再開が壊れる）。
     * @return 削除したファイル数（呼び出し側のログ用）。
     */
    fun sweepOrphans(cacheDir: File, keepNcodes: Set<String>, keepFileNames: Set<String>): Int {
        val files = dir(cacheDir).listFiles() ?: return 0
        return files.count { f ->
            f.isFile &&
                f.name !in keepFileNames &&
                Ncode(f.nameWithoutExtension).storageKey !in keepNcodes &&
                f.delete()
        }
    }
}
