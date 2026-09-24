package com.novelreader.backup

import android.content.Context
import android.content.SharedPreferences
import com.novelreader.PrefKeys

/**
 * 「読書記録の引き継ぎ」（Android の Auto Backup）のオプトイン値。読み書きの唯一の窓口。
 *
 * ## なぜ独立した object にするか
 * 値を読む側が2つあり、**片方が極端に不自由な文脈で走る**ため。
 *  1. 設定画面のトグル（通常のアプリプロセス）
 *  2. [NovelReaderBackupAgent]＝バックアップ中の **restricted mode**（`Application` サブクラス非生成・
 *     ContentProvider 未初期化・Activity 非起動）
 * 2 の制約が 1 の実装選択を縛る（DataStore も Room も使えない＝素の SharedPreferences 一択）ので、
 * 「どちらから読んでも同じ1つの規則」をここに閉じ込め、呼ぶ側にキー名も既定値も持たせない。
 *
 * ## 既定は OFF（[DEFAULT_ENABLED]）
 * 2026-09-03 の人間裁定。設計ドラフト `.claude/plans/auto-backup-design-2026-08-26.md` §6 案C の
 * 設計者所見は「既定 ON（既存利用者の挙動を変えない）」だったが、裁定で覆っている。
 * ⚠️ **含意**: この変更が乗ったビルドへ更新した既存利用者は、明示的に ON にしない限り
 * バックアップが止まる（ADR 0015 が Critical と呼んだ「機種変更で棚・読書位置・栞を全損」の状態へ
 * 既定で戻る）。設定行の文言でそれを伝えるのが UI 側の責務。
 */
object BackupOptIn {

    /**
     * キー不在時の値＝**OFF**（上記の裁定）。
     * 定数として公開するのは、テストと UI が「既定は false」を各自の直書きで持たないため
     * （直書きが散ると、既定を変える裁定が来たときに片方だけ取り残される）。
     */
    const val DEFAULT_ENABLED = false

    /**
     * オプトイン値を読む。**restricted mode から呼ばれる経路がここを通る**ので、
     * `Context.getSharedPreferences` 以外の API を足さないこと。
     *
     * ⚠️ 例外を握り潰す `try/catch` を置いていないのは意図的。ここで投げうるのは実質
     * `ClassCastException`（同じキーに Boolean 以外が入っている場合）だけで、それは本 object を
     * 通す限り成立しない。かつ**握り潰しても結果は変わらない**——読めなければ agent は例外で終わり
     * ＝そのバックアップは行われず、false を返した場合と同じ「運ばない」に着地する。
     * 起きない事態に防御を書くと、本当に壊れたときの徴（例外）まで消える。
     */
    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PrefKeys.BACKUP_OPT_IN, DEFAULT_ENABLED)

    /** [isEnabled] の Context 版（設定画面・[NovelReaderBackupAgent] の双方が使う）。 */
    fun isEnabled(context: Context): Boolean = isEnabled(prefs(context))

    /**
     * オプトイン値を書く。
     *
     * ⚠️ `commit()` ではなく `apply()`（非同期）で足りる: 次にこの値が読まれるのは
     * OS がバックアップを起こす時＝プロセスも別で、書き込みから十分に時間が空く。
     * 同一プロセス内の読みは apply() 直後でもメモリ上のキャッシュから正しい値が返る。
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PrefKeys.BACKUP_OPT_IN, enabled).apply()
    }

    /** アプリ設定の単一置き場（[PrefKeys.FILE_APP_PREFS]）。 */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
}
