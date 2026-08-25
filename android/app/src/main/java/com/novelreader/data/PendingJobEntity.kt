package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 変換待ち・変換中ジョブの永続レコード（処理キューのディスク上の写し）。
 *
 * なぜ永続化するか: OEM kill（ColorOS は特に積極的）・OOM・FGS の onTimeout でプロセスごと
 * 落ちるとメモリ上のキュー（PdfProcessingService.uriQueue）は消え、ユーザーには何も通知されない。
 * enqueue 時に記帳し、変換の成否確定時に削除することで、「残っている行 = 未完了ジョブ」として
 * 次回アプリ起動時に検出→通知＋再開できる（NovelReaderApplication.runStartupRecoveryOnce）。
 *
 * uri を主キーにする: 同一 PDF の重複 enqueue は再開観点では1回で足りるため REPLACE で畳む。
 */
@Entity(tableName = "pending_jobs")
data class PendingJobEntity(
    /** PDF の content:// URI 文字列（再開時に Uri.parse で復元する） */
    @PrimaryKey val uri: String,
    /** 再開不能時の通知に使う表示名（ファイル名ベース。空なら「不明」扱い） */
    val displayName: String = "",
    /** enqueue 時刻（epoch millis）。再開時にこの昇順で再投入し元のキュー順を保つ */
    val enqueuedAt: Long = 0,
    /**
     * 「起動時リカバリがこのジョブを再開した回数」。**再起動ループの唯一の止め金**。
     *
     * なぜ回数を DB に持つ必要があるか（真因）: 取込が失敗しても catch できたときは失敗経路が
     * pending_jobs 行を消すのでループにはならない。ループになるのは **catch が走らないとき**
     * ——OOM や OEM kill でプロセスごと落ちる場合——で、このとき行は「未完了」のまま残り、次回起動の
     * リカバリが同じ PDF を再投入し、同じ地点でまた死ぬ。メモリ上のカウンタはプロセスと一緒に消えるので、
     * 「プロセスが死んでも残る場所」＝この行自体に数えるしかない。
     *
     * リカバリは**再投入する前に**加算する（加算してから死ぬ＝次回は必ず 1 多い値を見る）。
     * [com.novelreader.StartupRecovery.MAX_RESUME_ATTEMPTS] 回に達したジョブは再開せず、
     * 行を消してユーザーに見えるエラーへ畳む＝無限ループが構造的に成立しなくなる。
     */
    val attempts: Int = 0,
)
