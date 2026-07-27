package com.novelreader.repository

import android.content.Context
import android.util.Log
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
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
    private val registry: SiteAdapterRegistry,
) {

    /** Web小説を取り込む（汎用Web小説DL基盤・P3）。詳細契約は [BookRepository.addWebBook] を参照。 */
    suspend fun addWebBook(
        inputUrl: String,
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
            bookDao.findBySourceUrl(workUrl)?.let { return@runCatching AddBookResult.Duplicate(it) }

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
            val bookId = UUID.randomUUID().toString().take(8)
            val outputDir = BookEntity.resolveHtmlDir(context.filesDir, bookId)
            try {
                val finalChapters = ChapterProcessor.processForewordAfterword(rawChapters)
                HtmlExporter.exportToPwa(finalChapters, bookId, toc.meta.title, outputDir)

                // ⑥ Room 登録。栞の個体差は PDF 経路と同じ抽選ロジックを再利用（取込時に真の乱数で1回だけ固定）。
                // author が null（サイトに著者名が無い）のときは BookEntity.author の既定値流儀に合わせて空文字にする。
                val shiori = drawPersistedShiori(Random.Default)
                val book = BookEntity(
                    id = bookId,
                    title = toc.meta.title,
                    htmlDirPath = outputDir.absolutePath,
                    author = toc.meta.author ?: "",
                    addedAt = System.currentTimeMillis(),
                    // ncode は Web 取込では持たない（なろう継続読書の紐付けは別経路で、ここでは付けない）。
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
