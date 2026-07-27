package com.novelreader.repository

import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebReadingProgressDao
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 責務: 読書進捗の永続化（蔵書の章/スクロール位置・読了フラグと、Web読書位置の furthest-wins 記録）。
 *
 * [DefaultBookRepository] の責務分割（2026-07-27 構造リファクタ）で委譲抽出した協力クラス。
 */
internal class ReadingProgressStore(
    private val progressDao: ProgressDao,
    private val webReadingProgressDao: WebReadingProgressDao,
) {

    // なぜ storageKey（trim+大文字）正規化か: 記録側と本棚カード/紐付け側の ncode 表記を一致させ、
    // 「読んだのに続きから読むが出ない」空振りを防ぐ（putWebNovel/removeWebNovel と同系の保存正規化）。
    //
    // なぜ furthest-wins（無条件 upsert でなく episode>既存のときだけ更新）か（UX監査 continuity・公理14/公理6）:
    // 目次から前の話（第10話）を「確認のため新規リンクで開いて」退出すると、その小さい話数が
    // 素の last-wins 上書きで再開ポインタを後退させ、読みかけ先端（第51話）が失われる。参照ジャンプで
    // 自動保存を後退させないため、到達済み最遠話より前進したときのみ記録する（しおりは覗き見で後退しない）。
    //   task_diary #56 の reachedByBack ガードは goBack の履歴遡行だけを抑止するが、目次から前の話を
    //   「新規リンク」で開く経路は forward 履歴が切り詰められ reachedByBack=false になるため素通りする。
    //   その後退経路をここ（全記録の単一集約点）で塞ぐ＝#56 と相補的で二重防御になる。
    // race について: onEpisodeReached は onPageFinished ごとに個別 launch するため read→write が
    //   IO 上で交錯し得るが、furthest-wins は単調なので最悪でも「一時的に低い話数が残り、次の前進で
    //   訂正される」だけ（先端の恒久喪失は起きない）。厳密原子性は複雑さに見合わないため許容する。
    suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int) = withContext(Dispatchers.IO) {
        val key = ncode.storageKey
        val existing = webReadingProgressDao.get(key)
        if (existing == null || episode > existing.lastReadEpisode) {
            webReadingProgressDao.upsert(
                WebReadingProgressEntity(
                    ncode = key,
                    lastReadEpisode = episode,
                    lastReadAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity? =
        withContext(Dispatchers.IO) { webReadingProgressDao.get(ncode.storageKey) }

    // 永続化境界: DAO は String 引数のため bookId.value でほどく（戻り値の lastReadFilename は
    // ナビ経路の文字列組み立て等でそのまま使うため String のまま返す＝型付けは識別子引数に限定）。
    suspend fun getLastRead(bookId: BookId): String? =
        withContext(Dispatchers.IO) { progressDao.getLastRead(bookId.value) }

    suspend fun getProgress(bookId: BookId): ProgressEntity? =
        withContext(Dispatchers.IO) { progressDao.getProgress(bookId.value) }

    // 読了（最終章の末尾到達）の記録。reachedEnd 列だけを立て、位置には触れない（sticky）。
    suspend fun markReachedEnd(bookId: BookId) = withContext(Dispatchers.IO) {
        // 永続化境界: Room の bookId 列は String のため .value でほどく。
        progressDao.markReachedEnd(bookId.value)
    }

    // 章を切り替えたときの進捗保存。スクロール位置は 0 にリセットする
    // （別の章へ移ったので前章のスクロール位置は引き継がない）。
    // lastReadAt を書き込み時刻でスタンプし、本棚の最近読書順ソートに使う。
    // なぜ insertIfAbsent＋updatePosition の2手か: 全列 REPLACE だと保存のたびに reachedEnd が
    // 既定へ戻り『了』印が消える。位置更新は reachedEnd を touch しない updatePosition に閉じ、
    // 行が無い初回だけ insertIfAbsent で作る（ProgressDao の why 参照）。
    suspend fun saveProgress(bookId: BookId, filename: ChapterFilename) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 永続化境界: Room(ProgressEntity) は String 列のため .value でほどいて渡す。
        progressDao.insertIfAbsent(ProgressEntity(bookId.value, filename.value, lastReadAt = now))
        progressDao.updatePosition(bookId.value, filename.value, 0, 0, now)
    }

    // 章内スクロール位置の保存。lastReadFilename も一緒に書き込むことで
    // 「どの章のどの位置か」を1行で表現する。
    // lastReadAt も毎回スタンプ（単一チャネル統合の最終1書き込みに自然に乗る）。
    // saveProgress と同じ2手（reachedEnd を消さないための insertIfAbsent＋updatePosition）。
    suspend fun saveScrollPosition(
        bookId: BookId,
        filename: ChapterFilename,
        scrollIndex: Int,
        scrollOffset: Int,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 永続化境界: Room(ProgressEntity) は String 列のため .value でほどいて渡す。
        progressDao.insertIfAbsent(
            ProgressEntity(bookId.value, filename.value, scrollIndex, scrollOffset, lastReadAt = now)
        )
        progressDao.updatePosition(bookId.value, filename.value, scrollIndex, scrollOffset, now)
    }
}
