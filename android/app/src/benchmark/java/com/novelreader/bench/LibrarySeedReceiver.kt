package com.novelreader.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.novelreader.PrefKeys
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.concurrent.thread

/**
 * Macrobenchmark 用の「10倍蔵書シーダー」。本棚スクロール jank 計測のために、決定論的なフェイク蔵書を
 * 計測対象アプリ（benchmark ビルド＝com.novelreader.benchmark）の実 DB へ投入する BroadcastReceiver。
 *
 * chapterCount 拡張（次フェーズ「長時間章送り jank ベンチ」用）:
 * フェイク蔵書は htmlDirPath の実体（章HTML）を持たない＝全冊0話のため章送り計測には使えない。
 * chapterCount>=1 のとき、最新の1冊（bench_seed_{count-1}＝addedAt 最大＝本棚 addedAt 降順の先頭）だけに
 * 実 HTML 章（決定論生成）を書き込み、題を固定文字列にし、progress を chap_1 先頭へ毎回リセットする。
 * こうして「実際に章を送れる1冊」を再現性ある状態で用意する（詳細は onReceive／writeChapterBook）。
 *
 * なぜ benchmark ソースセット限定か（出荷物に含まれない根拠）:
 * このファイルは src/benchmark 配下にのみ存在するため、debug/release ビルドには一切コンパイルされない。
 * つまりユーザーへ配布される APK にこの Receiver は存在せず、フェイク蔵書投入の攻撃面を出荷物に残さない。
 *
 * なぜ exported=true でも安全か（Manifest overlay 側にも明記）:
 * benchmark ビルドは applicationIdSuffix ".benchmark" で実蔵書(com.novelreader)と別パッケージに隔離され、
 * かつ debug/release には Receiver 自体が存在しない。計測専用ビルドの中だけで完結する。
 */
class LibrarySeedReceiver : BroadcastReceiver() {

    companion object {
        /** シード実行の action。ベンチ側は明示コンポーネント＋この action で ordered broadcast する。 */
        const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"

        /** bench_seed 行の id 接頭辞（投入・削除・件数集計で共有する単一規約）。 */
        private const val ID_PREFIX = "bench_seed_"

        // 章送り計測の対象本の固定題。ベンチ側はこの文字列を By.text で探して書影を特定する契約
        // （他99冊は TITLE_POOL 巡回で長さがばらつくが、この本だけは一意に発見できる必要がある）。
        private const val CHAPTER_BOOK_TITLE = "章送り計測の書"

        // progress.lastReadAt は 0（未接触）に固定する（System.currentTimeMillis 禁止・決定論）。
        // なぜ 0 か（>0 だと本棚の底へ沈み By.text で見つからない＝実機 FAIL の真因）:
        // 本棚の二層ソート（ShelfItems.recencyKeyOf・ADR 0016 層反転）は lastReadAt>0 の本を
        // 下層 tier0 に置く＝未読99冊の下へ沈み、LazyColumn 仮想化で semantics 不在になり
        // ベンチの By.text("章送り計測の書") が発見できない（2026-07-17 実機で実証）。
        // lastReadAt=0 は ProgressEntity 既定値と同じ「位置だけ在って未接触」の状態で、
        // 上層 tier1×addedAt 最大＝本棚先頭に決定論で出る。getLastRead（chap_1 直着地）は
        // lastReadAt に依存せず、relativeReadLabel も lastReadAt<=0 は null 表示のため無害。
        private const val UNTOUCHED_LAST_READ_AT = 0L

        // 書影は shiori*/画像を持たない本では title 由来の純 Canvas フォールバック描画になる。
        // その描画に「題字の長さの多様性」を与えるため、長い転生系題名から2文字の短題までを散らしたプール。
        // なぜ決定論（固定リスト巡回）か: 実行毎に同じ蔵書＝同じ描画になり、jank 計測の再現性を保つため。
        private val TITLE_POOL = listOf(
            "転生したら最強スライムだった件について語ろう",
            "追放されたけれど辺境でのんびり領地経営します",
            "勇者パーティを追放された俺、実は最強でした",
            "悪役令嬢ですが破滅エンドを全力で回避します",
            "異世界に召喚された高校生の長すぎる冒険譚",
            "辺境の錬金術師",
            "竜と少女の物語",
            "星降る夜に",
            "月光",
            "灯火",
        )

        // 章本文を決定論生成するための日本語文プール（各 約30字）。章・段落・文インデックスの算術だけで
        // 選び、乱数（java.util.Random）は一切使わない＝毎回同じ本文になり計測の再現性を保つ。
        private val SENTENCE_POOL = listOf(
            "朝靄の中を歩きながら、彼は昨日の出来事を静かに思い返していた。",
            "遠くの山並みが淡い光に染まり、風はまだ冷たさを残していた。",
            "少女は古い書物を閉じ、窓の外へ視線を投げかけたまま黙り込んだ。",
            "石畳の路地を抜けると、市場のざわめきが一気に耳へ押し寄せてきた。",
            "彼女の言葉には、長い旅路で培われた確かな覚悟が滲んでいた。",
            "灯りの消えた広間で、二人は互いの決意を静かに確かめ合った。",
            "剣を鞘に納めた瞬間、張り詰めていた空気がふっと緩んでいった。",
            "夜明け前の森は、まだ眠る獣の息づかいだけが微かに響いていた。",
            "老人は炉の火を見つめ、遠い昔の約束をぽつりと語り始めた。",
            "雨上がりの街路には、濡れた石の匂いと新しい朝の気配があった。",
            "彼は地図を広げ、次に向かうべき土地の名を指先でなぞった。",
            "風車の影が長く伸びる丘で、少年は帰らぬ者たちを思っていた。",
        )

        // ルビ（漢字→よみ）の決定論プール。段落インデックスの算術で選び、ルビ描画コストを計測に含める。
        private val RUBY_POOL = listOf(
            "境界" to "きょうかい",
            "残響" to "ざんきょう",
            "黄昏" to "たそがれ",
            "刻印" to "こくいん",
            "旅路" to "たびじ",
            "覚悟" to "かくご",
            "静寂" to "せいじゃく",
            "運命" to "うんめい",
            "灯火" to "ともしび",
            "約束" to "やくそく",
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val count = intent.getIntExtra("count", 100)
        val clear = intent.getBooleanExtra("clear", false)
        // chapterCount=0 なら従来挙動と完全に同一（後方互換）。1以上で最新1冊に実 HTML 章を書き込む。
        val chapterCount = intent.getIntExtra("chapterCount", 0)
        // Receiver のライフサイクルより DB 操作が長生きするため applicationContext を掴む。
        val appContext = context.applicationContext

        // gridMode は「extra に含まれている時のみ」prefs を書く（未指定なら現状のモードを尊重）。
        // 本棚は app_prefs のビュー切替キーを composition で読む（ui/skins/ShelfViewToggle）ので、
        // アプリの cold start 前にこの値を確定させておけば list/grid が意図どおり描画される。
        //
        // なぜ K の k_grid_view も書くか（2026-08-05 是正・旧実装は is_grid_view だけを書いていた）:
        // グリッド⇄リストの表示状態はスキンごとに別キー（D=is_grid_view / K=k_grid_view）で、
        // benchmark ビルドは ADR 0027 の機能ゲート（initWith release ＝SKIN_SWITCHING_ENABLED=false）で
        // スキンが明快K へクランプされる＝実際に読まれるのは k_grid_view だけだった。つまり旧実装は
        // 「面を指定したつもりで1つも指定できておらず」、BookshelfScrollBenchmark の scrollList /
        // scrollGrid が両方とも K の既定（グリッド）を測っていた＝リスト面の回帰が無防備だった。
        // 両方書くのは、シーダーに「今どのスキンへクランプされるか」を知らせないため——ゲートを反転
        // （課金解禁）して D で走らせる日が来ても、この Receiver は無改修で正しい面を作れる。
        // M/P/J の m_sky_view 等は星図⇄一覧など**別の軸**なので gridMode の対象外（ここでは触らない）。
        if (intent.hasExtra("gridMode")) {
            val grid = intent.getBooleanExtra("gridMode", false)
            appContext.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PrefKeys.IS_GRID_VIEW, grid)
                .putBoolean(PrefKeys.K_GRID_VIEW, grid)
                .apply()
        }

        // goAsync で処理完了まで Receiver を生かし、DB I/O は別スレッドで回す（メインを塞がない）。
        // 100件のトランザクションは Receiver の ANR 上限（~10s）内に十分収まる。
        // 章HTML書き込みも同上限内: chapterCount は最大でも数十章、1章＝約30〜40段落×60〜120字の
        // 数KB テキストで、数十ファイルの writeText は合計でも 1 秒未満（ディスク I/O のみ・変換なし）。
        val pending = goAsync()
        thread {
            var benchSeedTotal = 0
            var summary: String
            try {
                val db = AppDatabase.getDatabase(appContext)
                val dao = db.bookDao()
                val progressDao = db.progressDao()
                runBlocking(Dispatchers.IO) {
                    db.withTransaction {
                        if (clear) {
                            // id が決定論なので count 範囲の deleteById ループで確実に消せる。
                            // progress 行も同じ範囲で消す（章送り本のリセット痕跡を残さない）。
                            for (i in 0 until count) {
                                dao.deleteById(idOf(i))
                                progressDao.deleteByBookId(idOf(i))
                            }
                        } else {
                            // chapterCount>=1 のときだけ最新1冊（i=count-1）を固定題の章送り本にする。
                            val chapterBookIndex = if (chapterCount >= 1) count - 1 else -1
                            for (i in 0 until count) {
                                dao.insertBook(makeBook(appContext, i, i == chapterBookIndex))
                            }
                        }
                    }

                    // ファイル I/O は DB トランザクション外で行う（書き込み中に DB の write ロックを
                    // 長く保持しないため）。失敗（IOException 等）は握り潰さず外側の catch へ伝播させる。
                    if (clear) {
                        // 各 bench_seed の HTML 実体ディレクトリを再帰削除（存在しなければ no-op で false 返却）。
                        for (i in 0 until count) {
                            File(appContext.filesDir, "${BookEntity.NOVELS_SUBDIR}/${idOf(i)}")
                                .deleteRecursively()
                        }
                    } else if (chapterCount >= 1) {
                        val bookId = idOf(count - 1)
                        writeChapterBook(appContext, bookId, chapterCount)
                        // progress を chap_1.html・先頭位置へ強制リセット（毎シード必ず chap_1 に戻す＝
                        // ベンチ反復の決定論）。insertIfAbsent だけでは2回目以降リセットされないため、
                        // 行が無ければ作成（insertIfAbsent）→ 位置列を必ず上書き（updatePosition）の2手で戻す。
                        // reachedEnd は updatePosition が意図的に触らない（sticky）が、章送りは chap_1 の
                        // 先頭位置から始めるため読書開始位置の決定論には影響しない。
                        progressDao.insertIfAbsent(
                            ProgressEntity(bookId = bookId, lastReadFilename = "chap_1.html"),
                        )
                        progressDao.updatePosition(
                            bookId = bookId,
                            filename = "chap_1.html",
                            scrollIndex = 0,
                            scrollOffset = 0,
                            lastReadAt = UNTOUCHED_LAST_READ_AT,
                        )
                    }

                    // 投入（または削除）後の bench_seed 件数を数え、resultCode の値源にする。
                    benchSeedTotal = dao.getAllBookIds().count { it.startsWith(ID_PREFIX) }
                }
                summary = if (clear) {
                    "cleared bench_seed rows (requested=$count, remaining=$benchSeedTotal)"
                } else {
                    "seeded count=$count, bench_seed total=$benchSeedTotal, chapterCount=$chapterCount"
                }
            } catch (t: Throwable) {
                // 失敗を黙って PASS に化けさせない: resultCode は 0 のまま、要約に理由を残す
                // （HTML 書き込み失敗もここに落ち、benchSeedTotal=0＝期待件数に届かず fail に倒れる）。
                summary = "seed failed: ${t.message}"
            }
            pending.setResultCode(benchSeedTotal)
            pending.setResultData(summary)
            pending.finish()
        }
    }

    /** bench_seed の決定論 id（0埋め4桁）。投入・削除・集計で同じ規約を通す。 */
    private fun idOf(i: Int): String = ID_PREFIX + "%04d".format(i)

    private fun makeBook(context: Context, i: Int, isChapterBook: Boolean): BookEntity {
        // 章送りベンチの対象本だけは固定題（ベンチが By.text で書影を特定する契約）。
        // 他は従来どおり題名プールを巡回し「　其の${i+1}」で一意化（描画に長さのばらつき＋id と一意対応）。
        val title =
            if (isChapterBook) CHAPTER_BOOK_TITLE
            else TITLE_POOL[i % TITLE_POOL.size] + "　其の${i + 1}"
        // htmlDirPath は filesDir/novels/bench_seed_XXXX を指す。フェイク99冊は実体を作らない
        // （章数えは File.listFiles の null 安全で 0話＝未読カードになり本棚は正常に描ける）。
        // 章送り対象本のみ、この同じパスへ後段の writeChapterBook が実 HTML を書き込む。
        val dir = File(context.filesDir, "${BookEntity.NOVELS_SUBDIR}/${idOf(i)}")
        return BookEntity(
            id = idOf(i),
            title = title,
            htmlDirPath = dir.absolutePath,
            // 決定論の追加日時（System.currentTimeMillis 禁止）: 並びが addedAt 降順で安定し、
            // 計測の反復間・実行間で本棚の見た目が一定になる（再現性のため）。
            // i=count-1 が最大＝addedAt 降順の先頭になり、章送り対象本が本棚の一番手前に来る。
            addedAt = 1_700_000_000_000L + i * 60_000L,
            // progress 行は（章送り本を除き）作らないので UNREAD。shiori*/ncode/contentSha256 は null。
        )
    }

    /**
     * 章送り計測の対象本に実 HTML（chap_N.html × chapterCount ＋ index.html）を決定論生成で書き込む。
     * 毎回上書き＝冪等（同じ bench_seed_XXXX を何度シードしても同じ内容に収束する）。
     * 生成形状は HtmlExporter の出力（h1＋div.content・ul.index-list・ruby）を模倣し、
     * ChapterHtmlParser が章・目次・ルビを解釈できる最小要件を満たす。
     */
    private fun writeChapterBook(context: Context, bookId: String, chapterCount: Int) {
        val dir = File(context.filesDir, "${BookEntity.NOVELS_SUBDIR}/$bookId")
        dir.mkdirs() // 既存でも問題なし（冪等）

        val indexItems = StringBuilder()
        for (n in 1..chapterCount) {
            // ゼロ埋めなし＝HtmlExporter の命名（chap_${i+1}.html）と同じ規約。
            File(dir, "chap_$n.html").writeText(buildChapterHtml(n), Charsets.UTF_8)
            indexItems.append("            <li><a href=\"chap_$n.html\">第${n}章</a></li>\n")
        }
        File(dir, "index.html").writeText(buildIndexHtml(indexItems.toString()), Charsets.UTF_8)
    }

    /** chap_N.html を生成する。h1＝章題・div.content＝本文（段落は \n 区切りのテキストノード）。 */
    private fun buildChapterHtml(chapter: Int): String {
        // 段落数は章ごとに 30〜40 で変化させる（算術のみ・乱数なし）。
        val paraCount = 30 + (chapter % 11)
        val body = (0 until paraCount).joinToString("\n") { buildParagraph(chapter, it) }
        return "<!DOCTYPE html>\n" +
            "<html lang=\"ja\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>第${chapter}章</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>第${chapter}章</h1>\n" +
            "        <div class=\"content\">\n" +
            body + "\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>\n"
    }

    /** index.html を生成する。ul.index-list に全章の li を並べる（ChapterHtmlParser.parseToc の要件）。 */
    private fun buildIndexHtml(listItems: String): String {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"ja\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>$CHAPTER_BOOK_TITLE - 目次</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>$CHAPTER_BOOK_TITLE</h1>\n" +
            "        <ul class=\"index-list\">\n" +
            listItems +
            "        </ul>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>\n"
    }

    /**
     * 1段落を決定論生成する（可視で約60〜120字）。章・段落インデックスの算術だけで内容を変化させる。
     * なぜ算術のみ（乱数禁止）か: 毎回同じ本文＝同じレイアウト・描画になり、章送り jank 計測の
     * 反復間・実行間の再現性を保つため（既存シーダーの決定論方針を踏襲）。
     */
    private fun buildParagraph(chapter: Int, para: Int): String {
        // 2〜3文（各 約30字）を連結＝可視で約60〜100字。ルビ段落はさらに 約15字加わり上限120字に収まる。
        val sentenceCount = 2 + ((chapter + para) % 2)
        val sb = StringBuilder()
        for (s in 0 until sentenceCount) {
            val idx = (chapter * 7 + para * 3 + s * 5) % SENTENCE_POOL.size
            sb.append(SENTENCE_POOL[idx])
        }
        // 3段落に1回ルビを混ぜる（ルビ描画コストを計測に含めるため）。語・位置は算術で決定＝乱数なし。
        if (para % 3 == 0) {
            val ruby = RUBY_POOL[(chapter * 2 + para) % RUBY_POOL.size]
            sb.append("あの${rubyTag(ruby.first, ruby.second)}の記憶が胸を過ぎった。")
        }
        return sb.toString()
    }

    /** <ruby>親<rt>ふり</rt></ruby> 形（ChapterHtmlParser のルビ要件・HtmlExporter の CSS 前提と同形）。 */
    private fun rubyTag(base: String, reading: String): String =
        "<ruby>$base<rt>$reading</rt></ruby>"
}
