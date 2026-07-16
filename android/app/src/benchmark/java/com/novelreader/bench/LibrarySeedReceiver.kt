package com.novelreader.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.concurrent.thread

/**
 * Macrobenchmark 用の「10倍蔵書シーダー」。本棚スクロール jank 計測のために、決定論的なフェイク蔵書を
 * 計測対象アプリ（benchmark ビルド＝com.novelreader.benchmark）の実 DB へ投入する BroadcastReceiver。
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val count = intent.getIntExtra("count", 100)
        val clear = intent.getBooleanExtra("clear", false)
        // Receiver のライフサイクルより DB 操作が長生きするため applicationContext を掴む。
        val appContext = context.applicationContext

        // gridMode は「extra に含まれている時のみ」prefs を書く（未指定なら現状のモードを尊重）。
        // 本棚は app_prefs / is_grid_view を composition で読む（BookshelfScreen 参照）ので、
        // アプリの cold start 前にこの値を確定させておけば list/grid が意図どおり描画される。
        if (intent.hasExtra("gridMode")) {
            val grid = intent.getBooleanExtra("gridMode", false)
            appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("is_grid_view", grid).apply()
        }

        // goAsync で処理完了まで Receiver を生かし、DB I/O は別スレッドで回す（メインを塞がない）。
        // 100件のトランザクションは Receiver の ANR 上限（~10s）内に十分収まる。
        val pending = goAsync()
        thread {
            var benchSeedTotal = 0
            var summary: String
            try {
                val db = AppDatabase.getDatabase(appContext)
                val dao = db.bookDao()
                runBlocking(Dispatchers.IO) {
                    db.withTransaction {
                        if (clear) {
                            // id が決定論なので count 範囲の deleteById ループで確実に消せる。
                            for (i in 0 until count) {
                                dao.deleteById(idOf(i))
                            }
                        } else {
                            for (i in 0 until count) {
                                dao.insertBook(makeBook(appContext, i))
                            }
                        }
                    }
                    // 投入（または削除）後の bench_seed 件数を数え、resultCode の値源にする。
                    benchSeedTotal = dao.getAllBookIds().count { it.startsWith(ID_PREFIX) }
                }
                summary = if (clear) {
                    "cleared bench_seed rows (requested=$count, remaining=$benchSeedTotal)"
                } else {
                    "seeded count=$count, bench_seed total=$benchSeedTotal"
                }
            } catch (t: Throwable) {
                // 失敗を黙って PASS に化けさせない: resultCode は 0 のまま、要約に理由を残す。
                summary = "seed failed: ${t.message}"
            }
            pending.setResultCode(benchSeedTotal)
            pending.setResultData(summary)
            pending.finish()
        }
    }

    /** bench_seed の決定論 id（0埋め4桁）。投入・削除・集計で同じ規約を通す。 */
    private fun idOf(i: Int): String = ID_PREFIX + "%04d".format(i)

    private fun makeBook(context: Context, i: Int): BookEntity {
        // 題名は長短プールを巡回し「　其の${i+1}」で一意化（描画に長さのばらつきを与えつつ id と一意対応）。
        val title = TITLE_POOL[i % TITLE_POOL.size] + "　其の${i + 1}"
        // htmlDirPath は filesDir/novels/bench_seed_XXXX を指すが実体は作らない。
        // なぜ実在しないままで正しいか: 章数えは File.listFiles の null 安全で 0話（未読カード）になり、
        // 実 HTML なしでも本棚カードは正常に描ける（書影も title フォールバックで画像ファイル不要）。
        val dir = File(context.filesDir, "${BookEntity.NOVELS_SUBDIR}/${idOf(i)}")
        return BookEntity(
            id = idOf(i),
            title = title,
            htmlDirPath = dir.absolutePath,
            // 決定論の追加日時（System.currentTimeMillis 禁止）: 並びが addedAt 降順で安定し、
            // 計測の反復間・実行間で本棚の見た目が一定になる（再現性のため）。
            addedAt = 1_700_000_000_000L + i * 60_000L,
            // progress 行は作らないので全冊 UNREAD。shiori*/ncode/contentSha256 はデフォルト（null）のまま。
        )
    }
}
