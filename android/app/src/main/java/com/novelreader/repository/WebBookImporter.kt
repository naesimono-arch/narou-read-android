package com.novelreader.repository

import android.content.Context
import android.util.Log
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressDao
import com.novelreader.pdf.ChapterProcessor
import com.novelreader.pdf.HtmlExporter
import com.novelreader.pdf.RawChapter
import com.novelreader.scrape.ScrapeIntegrity
import com.novelreader.scrape.SiteAdapterRegistry
import com.novelreader.repository.BookRepository.AddBookResult
// 栞の個体差抽選（純ロジック・Compose 非依存）。ShioriCover.kt（Compose 依存）は import しない
// ＝先端総数/レンジの正本は ShioriGenerator.kt 側にあり、repository はそこだけを参照する。
import com.novelreader.ui.components.drawPersistedShiori
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.util.UUID

// 分割後も従来どおりのタグで出す（ログ検索の継続性＝挙動不変のため各協力クラスで同一タグを使う）。
private const val TAG = "BookRepository"

/**
 * 責務: Web小説の取込（URL解決→目次/本文取得→破損監視→HTML生成→Room 登録。汎用Web小説DL基盤・P3）。
 *
 * [DefaultBookRepository] の責務分割（2026-07-27 構造リファクタ）で委譲抽出した協力クラス。
 * registry（サイト解決の差替継ぎ目）を注入で受ける理由は DefaultBookRepository のコンストラクタ引数コメント参照。
 */
internal class WebBookImporter(
    private val context: Context,
    private val bookDao: BookDao,
    // 上書き取込後の読書位置 clamp（話数が減ったとき最終章へ丸める）に使う。読み書きとも progress 行のみ。
    private val progressDao: ProgressDao,
    private val registry: SiteAdapterRegistry,
) {

    /** Web小説を取り込む（汎用Web小説DL基盤・P3）。詳細契約は [BookRepository.addWebBook] を参照。 */
    suspend fun addWebBook(
        inputUrl: String,
        overwrite: Boolean,
        onProgress: ((Int, String) -> Unit)?,
    ): Result<AddBookResult> = withContext(Dispatchers.IO) {
        // なぜ pending_jobs（永続キュー）を使わないか（P3 裁定）: PDF 取込は SAF 権限の再取得や OEM kill 後の
        // 自動再開が要るためキューへ記帳するが、Web 取込の失敗は即時 Result.failure で返し、リトライはユーザー
        // 操作（再度 URL を投入）に委ねる。ネットワークの一過性失敗を勝手に自動再開すると、相手サイトへ意図
        // せぬ再アクセスを繰り返し行儀を損なう（低頻度アクセスの原則）ため、再試行は明示操作に限定する。
        runCatching {
            // ① URL 解決（規約ゲート）。Supported 以外は失敗で返す。UI は呼び出し前に registry を直接引いて
            // Blocked/Unsupported を出し分ける前提（公式サイト導線への誘導）ゆえ、repository は失敗で足りる。
            val supported = when (val r = registry.resolve(inputUrl)) {
                is SiteAdapterRegistry.Resolution.Supported -> r
                is SiteAdapterRegistry.Resolution.Blocked ->
                    throw IllegalArgumentException("自前DL不可のサイト（公式サイトで読む対象）: ${r.hostLabel}")
                SiteAdapterRegistry.Resolution.Unsupported ->
                    throw IllegalArgumentException("未対応のサイトURL（アダプタ未整備）: $inputUrl")
            }
            val adapter = supported.adapter
            val workUrl = supported.workUrl // アダプタが正規化した作品トップ URL（BookEntity.sourceUrl に入る）

            // ② sourceUrl 重複ガード（重い取得の前に弾く＝PDF の hash 遮断と同じ「重い処理の前に弾く」位置）。
            // 同一作品 URL の蔵書が既にあれば目次・全章の取得を一切走らせず Duplicate を返す。
            // 復元モード（本文欠落→再取込・2026-07-29 案B/C）: 既存行があっても本文実体が欠落していれば
            // Duplicate で止めず、既存行（id・進捗・栞・addedAt）を保持したまま再取得して本文だけ作り直す
            // ＝重複行を作らない（分岐④「Webから再取得」の実体）。
            // 上書きモード（2026-08-05 仕様＝重複拒否の撤廃）: ユーザーが確認ダイアログで「上書き」を
            // 選んだ再投入（overwrite=true）は、本文実体が生きていても同じ復元経路へ流す＝既存行を保持した
            // まま再取得して本文を差し替える。連載の新着話はこの再取得に含まれる（U1「続き取得」の実体）。
            val existingWeb = bookDao.findBySourceUrl(workUrl)
            val restoreTarget = existingWeb?.takeIf { overwrite || !it.hasContent(context.filesDir) }
            if (existingWeb != null && restoreTarget == null) {
                return@runCatching AddBookResult.Duplicate(existingWeb)
            }

            // ③ 目次取得 → 各章本文取得。Crawl-delay は ScrapeHttpClient が内蔵するため、ここで追加の sleep はしない。
            val toc = adapter.fetchToc(workUrl)
            val total = toc.chapters.size
            val rawChapters = toc.chapters.mapIndexed { index, ref ->
                val i = index + 1
                onProgress?.invoke(i, "章 $i/$total 取得中")
                adapter.fetchChapter(ref)
            }

            // ③' 破損監視（層1・handover P4）: 取得直後に構造妥当性を検査する。空 TOC・空本文・異常に短い本文は
            // ScrapeStructureException（ScrapeException 派生）で弾き、ViewModel が「公式サイトで読む」逃げ道へ落とす。
            // 重い検査ロジックは scrape/ 共通層（ScrapeIntegrity）に置き、ここは呼ぶだけ＝例外型での分岐は下流に委ねる
            // （下の fold は CancellationException 以外を Result.failure(e) へ載せる＝構造疑いの型が呼び出し側まで届く）。
            ScrapeIntegrity.verify(toc, rawChapters)

            // ④ Web 本文連結ハッシュ（決定論・重複判定と回帰テストの固定点）。HTML 変換前の生本文で計算する
            // （中間ルビ記法のまま＝定義 webContentSha256 を参照。抽出後の HTML に依存させない）。
            val contentSha256 = webContentSha256(rawChapters)

            // ⑤ PDF 蔵書と同契約の HTML を生成する（前後書き整形 → exportToPwa）。outputDir は既存 bookId
            // ディレクトリ規約（filesDir/novels/<bookId>）と同一＝掃除・復元経路をそのまま共有する。
            // 復元時は既存行の id ディレクトリへ直接生成する（id 不変＝進捗・栞の紐付けを保つ）。
            // torn 残骸（index 無しで chap だけ残る等）が新しい一式へ混入しないよう先に消す。
            val bookId = restoreTarget?.id ?: UUID.randomUUID().toString().take(8)
            val outputDir = BookEntity.resolveHtmlDir(context.filesDir, bookId)
            if (restoreTarget != null) outputDir.deleteRecursively()
            try {
                val finalChapters = ChapterProcessor.processForewordAfterword(rawChapters)
                HtmlExporter.exportToPwa(finalChapters, toc.meta.title, outputDir)

                if (restoreTarget != null) {
                    // ⑥' 復元の確定: 既存行を部分 UPDATE（updateRestoredContent）＝id・進捗・栞・addedAt・
                    // sourceUrl/sourceSite 不変。contentSha256 は再取得後の最新本文で更新する（連載の追補が
                    // あれば指紋も変わるのが正）。sourceUri は Web 本では常に NULL＝既存値をそのまま渡す。
                    // 確定の2書き込み（行 UPDATE→進捗 clamp）は NonCancellable で最後まで通す:
                    // この途中でキャンセルされると下の catch(Throwable) が生成済み本文を消し、
                    // 「行だけ更新済み・本文なし」の torn 状態になる（PDF 経路の確定が NonCancellable で
                    // 保護されているのと同じ理由。生成完了後の確定は数msの DB 書きのみ＝中断を許す価値がない）。
                    withContext(kotlinx.coroutines.NonCancellable) {
                        bookDao.updateRestoredContent(
                            restoreTarget.id, outputDir.absolutePath, contentSha256, restoreTarget.sourceUri,
                        )
                        // 読書位置の clamp（上書き/復元共通）: 新しい一式の章数より先の章を指す progress は
                        // 開けない章＝壊れた再開点になる（作者が話を削除した・古い版へ戻った等）。最終章の
                        // 先頭へ丸め、実際に存在する位置から再開できるようにする。読了印（reachedEnd）と
                        // lastReadAt は実績なので触らない（clampReadingPosition の KDoc 参照）。
                        clampReadingPosition(restoreTarget.id, finalChapters.size)
                    }
                    return@runCatching AddBookResult.Added(
                        restoreTarget.copy(htmlDirPath = outputDir.absolutePath, contentSha256 = contentSha256),
                        restored = true,
                    )
                }

                // ⑥ Room 登録。栞の個体差は PDF 経路と同じ抽選ロジックを再利用（取込時に真の乱数で1回だけ固定）。
                // author が null（サイトに著者名が無い）のときは BookEntity.author の既定値流儀に合わせて空文字にする。
                val shiori = drawPersistedShiori(Random.Default)
                val book = BookEntity(
                    id = bookId,
                    title = toc.meta.title,
                    htmlDirPath = outputDir.absolutePath,
                    author = toc.meta.author ?: "",
                    addedAt = System.currentTimeMillis(),
                    // ncode は Web 取込では持たない（なろう継続読書の紐付けは別経路＝NcodeLinkSheet の
                    // 人間確定のみ。題名突合の自動推定で埋めない理由＝BookEntity.ncode の原則コメント）。
                    // 「本棚に置く」済み同一作品との二重カードは、表示層の自然昇格が題名＋作者一致でも
                    // 引っ込めて解消する（ShelfItems.isPromotedWeb・2026-07-29 発見バグの対処）＝
                    // データへ推定を書かずに棚の重複だけを消す分担。
                    ncode = null,
                    contentSha256 = contentSha256,
                    shioriTipIndex = shiori.tipIndex,
                    shioriLenFrac = shiori.lenFrac,
                    // sourceUri（content:// ＝PDF削除機能の列）は Web 経路では触らない。sourceUrl/sourceSite に出所を記録。
                    sourceUri = null,
                    sourceUrl = workUrl,
                    sourceSite = adapter.siteKey,
                )
                bookDao.insertBook(book)
                AddBookResult.Added(book)
            } catch (e: Throwable) {
                // HTML 生成後〜登録前に失敗したら書きかけ出力を消す（本棚に出ない孤立ディレクトリを残さない＝
                // addBook の抽出失敗クリーンアップと同思想）。真因は握り潰さず rethrow して下の fold で失敗に落とす。
                outputDir.deleteRecursively()
                throw e
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                // キャンセルは素通し（runCatching が CancellationException も捕捉するため rethrow）。
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "addWebBook 失敗", e)
                // Web 取込の失敗はキュー再開を持たないため、そのまま失敗として返す（リトライはユーザー操作）。
                Result.failure(e)
            },
        )
    }

    // 進捗 clamp の実体は top-level の [clampReadingPosition]（PDF 上書き経路＝PdfBookImporter と共有）。
    private suspend fun clampReadingPosition(bookId: String, newChapterCount: Int) =
        clampReadingPosition(progressDao, bookId, newChapterCount)
}

/** 進捗行が新章数の外を指していたら最終章の先頭へ丸めて書き戻す（判定の正本は [clampedChapterFilename]）。
 *  Web/PDF 両方の上書き・復元経路が共有する。lastReadAt は既存値をそのまま渡す: updatePosition は
 *  lastReadAt も書くクエリだが、clamp は読書行為ではないため「最近読んだ順」の並びを動かしてはならない。
 *  reachedEnd は updatePosition が触らない設計（sticky・ProgressDao の why）なので追加の防御は不要。 */
internal suspend fun clampReadingPosition(progressDao: ProgressDao, bookId: String, newChapterCount: Int) {
    val progress = progressDao.getProgress(bookId) ?: return
    val clamped = clampedChapterFilename(progress.lastReadFilename, newChapterCount) ?: return
    // スクロール位置は旧章のものなので持ち越さない（章が変わる＝座標系が変わる。章先頭 0,0 が正）。
    progressDao.updatePosition(bookId, clamped, 0, 0, progress.lastReadAt)
}

// 章本文ファイル名の規約（HtmlExporter が chap_1.html..chap_N.html を生成する）に対する clamp 判定。
private val CHAPTER_FILENAME = Regex("""chap_(\d+)\.html""")

/** 出力一式に含まれる章本文ファイル（chap_N.html）の枚数。PDF 上書き経路の clamp 基準
 *  （PDF は抽出結果の章数を TOC のような一次値で持たないため、生成物から数えるのが正）。 */
internal fun chapterFileCount(htmlDir: java.io.File): Int =
    htmlDir.listFiles()?.count { CHAPTER_FILENAME.matches(it.name) } ?: 0

/**
 * 読書位置の clamp 判定（純関数・JVM テスト対象）。上書き/復元の再取得で章数が減ったとき、
 * 進捗が指す章（chap_N.html）が新しい一式に存在しなければ最終章のファイル名を返す（呼び出し側が
 * その章の先頭へ丸める）。丸め不要（範囲内・章数0・規約外のファイル名）なら null＝進捗に触らない。
 *
 * なぜ「消す」でなく「最終章へ丸める」か: 進捗行の削除は読書位置・読了印・最近読んだ順の並びを
 * まとめて失う過剰反応。存在する最も近い位置（＝最終章）へ寄せれば、ユーザーは違和感なく再開できる。
 * 規約外のファイル名（防御・通常は発生しない）は判断材料が無いので触らない方が安全側。
 */
internal fun clampedChapterFilename(lastReadFilename: String, newChapterCount: Int): String? {
    if (newChapterCount < 1) return null
    val n = CHAPTER_FILENAME.matchEntire(lastReadFilename)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return if (n > newChapterCount) "chap_$newChapterCount.html" else null
}

/**
 * Web 取込の内容指紋（決定論）。各章順に `title + "\n" + body.joinToString("\n") + "\n"` を連結した
 * UTF-8 バイト列の SHA-256 を返す（addWebBook の重複判定・回帰テストの固定点）。
 *
 * なぜ HTML 変換前の生 [RawChapter] で計算するか: exportToPwa 後の HTML はテンプレート（style/nav）を
 * 含み実装変更で揺れる。本文そのもの（中間ルビ記法のまま）を指紋にすれば、章題・本文が同一なら常に
 * 同じハッシュになり、テストが定義から独立に期待値を組める。PDF 経路の [sha256Hex]（取込元バイト列）と
 * 同じく「内容が同じなら同じ指紋」を Web 源に与えるための対の関数。
 */
internal fun webContentSha256(chapters: List<RawChapter>): String {
    val sb = StringBuilder()
    for (chap in chapters) {
        sb.append(chap.title).append("\n")
        sb.append(chap.body.joinToString("\n")).append("\n")
    }
    return sha256Hex(sb.toString().toByteArray(Charsets.UTF_8).inputStream())
}
