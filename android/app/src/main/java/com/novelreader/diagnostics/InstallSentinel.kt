package com.novelreader.diagnostics

import java.io.File
import java.io.IOException

/**
 * 「いま動いているこのデータ領域で、一度でもアプリが起動したか」の徴。
 *
 * ## なぜこんなものが要るのか（真因）
 * 異常終了の推定（[SessionWatch]）は `app_prefs` の `DIAG_*` を「**前回の自分**が書いた記録」として読む。
 * ところが `app_prefs.xml` は Auto Backup の対象なので、機種変更や再インストールの復元で
 * **他の端末・他のインストールが書いた `DIAG_*` が、そのままこの端末の prefs として現れる**。
 * 旧端末が前面のまま（＝`DIAG_SESSION_OPEN=true` のまま）バックアップされていれば、新端末の初回起動は
 * それを「開いたまま次の起動が来た」と読み、**旧端末の時刻で偽の ABNORMAL_EXIT を1件記録する**。
 *
 * 真因は `shouldReportAbnormalExit` の条件が緩いことではなく、**復元されたセッション状態を
 * 同じインストールの前回起動と区別する手段が prefs の中に1つも無いこと**。prefs はまるごと復元されるので、
 * prefs の中へどんな印を足しても一緒に復元されてしまい、区別の役に立たない。
 *
 * ## だから徴は prefs の外＝`no_backup/` に置く
 * `Context.getNoBackupFilesDir()` は**公式ガイドが「バックアップ対象から常に除外される」と保証している**
 * 数少ない場所（`getCacheDir` / `getCodeCacheDir` と同じ扱い）で、XML の宣言に依存しない。
 * ＝ここに置いた徴は復元では**決して現れない**ので、「徴が無い＝この prefs はこのインストールが書いたものではない」
 * が成り立つ。
 *
 * ## 採らなかった方式と、その理由
 *  - **`shouldReportAbnormalExit` の条件を緩める／`DIAG_*` をバックアップ対象から外す**:
 *    どちらも症状を隠すだけ。前者は本物の異常終了まで数えなくなり、後者は復元先で診断の連続性を失う。
 *    そもそも `DIAG_*` はキー単位で除外できない（Auto Backup の最小粒度はファイル＝`app_prefs.xml` 丸ごと）。
 *  - **`BackupAgent.onRestoreFinished()` で畳む**: 一見いちばん素直だが、**復元経路でしか呼ばれない**うえ
 *    restricted mode で走るため JVM でも androidTest でも検証できず、静かに失敗しても誰も気づけない
 *    （[com.novelreader.backup.NovelReaderBackupAgent] の KDoc の代償がそのまま乗る）。
 *    こちらは「使う瞬間に徴を確かめる」形なので、フックが呼ばれたかどうかに依存しない。
 *  - **`Build.FINGERPRINT` / `ANDROID_ID` を prefs に控えて突き合わせる**: 端末が変わった復元は捕まえられるが、
 *    **同じ端末での再インストール＋復元**（値が一致する）を取りこぼす。そちらも prefs は他インストール由来で
 *    同じ偽陽性が出るので、端末の同一性は判定軸として間違っている。加えて端末識別子の保持は
 *    「収集なし」で通しているデータセーフティ申告と相性が悪い。
 *  - **`PackageManager` の `firstInstallTime` を控えて突き合わせる**: 上の取りこぼしは無いが、
 *    プラットフォームの保証ではなく**タイムスタンプ一致に賭けた推定**でしかない。`no_backup/` は
 *    「復元されない」ことが仕様として保証された唯一の道具なので、推定を挟む理由が無い。
 */
interface InstallSentinel {

    /** 徴が既に在るか（＝このインストールで少なくとも一度は起動済みか）。 */
    fun isPresent(): Boolean

    /** 徴を立てる。以後の起動では [isPresent] が true を返す。 */
    fun create()
}

/**
 * `no_backup/` 配下の 0 バイトファイルで [InstallSentinel] を実現する本番実装。
 *
 * @param noBackupDir `Context.getNoBackupFilesDir()`。**ここ以外を渡してはいけない**——
 *   バックアップ対象の場所へ置くと徴ごと復元され、この仕掛けは黙って無効になる（テストは緑のまま）。
 */
class NoBackupFileInstallSentinel(private val noBackupDir: File) : InstallSentinel {

    private val marker: File get() = File(noBackupDir, FILE_NAME)

    override fun isPresent(): Boolean = marker.isFile

    /**
     * ⚠️ [IOException] だけを捕まえるのは「握り潰し」ではなく**倒れる向きの選択**。
     * 徴の生成は診断という副次機能の脇道なので、ここで例外を投げると**起動そのものを落とす**。
     * かといって失敗を無視して「在ることにする」と、次回以降ずっと復元由来の残骸を本物として数えてしまう。
     * 捕まえて何もしなければ徴は**立たないまま**＝次の起動でも [isPresent] が false ＝
     * 異常終了の推定が抑止されたままになる。つまり失敗は必ず「記録が減る側」に倒れ、
     * 「偽の記録が増える側」には決して倒れない。実際には app 専用ディレクトリへの 0 バイト作成なので、
     * ここが失敗する状況ではアプリ全体が既に立ち行かない。
     */
    override fun create() {
        try {
            noBackupDir.mkdirs()
            marker.createNewFile()
        } catch (e: IOException) {
            // 上記のとおり意図的に無処理。徴が立たない＝抑止が続く（安全側）。
        }
    }

    private companion object {
        /** 名前を変えると全利用者で徴が消え、更新直後の1回だけ異常終了が記録されなくなる＝変えない。 */
        const val FILE_NAME = "diag_install_sentinel"
    }
}
