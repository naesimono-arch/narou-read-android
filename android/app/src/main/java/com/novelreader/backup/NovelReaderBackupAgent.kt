package com.novelreader.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Auto Backup を**アプリ内のトグルで止められる**ようにするためだけの BackupAgent
 * （設計ドラフト `.claude/plans/auto-backup-design-2026-08-26.md` §4.2 案C・2026-09-03 人間裁定）。
 *
 * ## なぜコードが要るのか（他に手段が無い）
 * バックアップの可否を実行時に切り替える API は通常アプリに開かれていない。
 *  - `android:allowBackup` / `dataExtractionRules` / `fullBackupContent` は**マニフェスト属性・リソース
 *    ＝ビルド時固定**。実行時に書き換える手段は無い。
 *  - `BackupManager.setBackupEnabled` は `@SystemApi` ＋ `@RequiresPermission(BACKUP)`＝呼べない。
 *  - `BackupManager.dataChanged()` は key-value バックアップの変更通知で、Auto Backup の可否に効かない。
 * 残る唯一の正攻法が「自前の BackupAgent を宣言し、[onFullBackup] で `super` を呼ばない」＝本クラス。
 * マニフェストの `android:fullBackupOnly="true"` は「agent は居るが方式は full-data のまま」という
 * 公式が要求する組（これが無いと key-value バックアップとして扱われ、XML ルールが効かなくなる）。
 *
 * ## この agent が走る文脈（restricted mode）の禁忌
 * バックアップ／復元中、アプリは**制限モード**で起動される:
 * `Application` のサブクラス（`NovelReaderApplication`）は**生成されず基底 `Application` が使われる**・
 * ContentProvider は初期化されない・メイン Activity も起動しない。
 * したがって本クラスから触ってよいのは**素の `Context` API だけ**。
 *  - オプトイン値は SharedPreferences（[BackupOptIn]）から読む。
 *  - Room / DataStore を開かない（DataStore は suspend ＋ シングルトン管理、Room は**まさにいま
 *    バックアップしようとしているファイル**を開くことになる）。
 *  - `NovelReaderApplication` の初期化に依存するもの（WorkManager の on-demand 初期化など）に触らない。
 *
 * ## この agent が背負う代償（承知のうえで入れている）
 * これまでバックアップ経路は「宣言 XML だけ・動くコードゼロ」＝壊れる余地が構造的に無かった。
 * agent が例外を投げれば**そのバックアップは静かに失敗する**（利用者にも開発者にも通知されない）し、
 * restricted mode は JVM 単体テストでも androidTest でも再現できない＝**既定ゲートで守れない**。
 * 検証手段は実機／エミュの `bmgr` 手順（設計ドラフト §7）だけ。
 * だからここのコードは「分岐1つ＋ super 呼び出し」より複雑にしないこと。
 */
class NovelReaderBackupAgent : BackupAgent() {

    /**
     * full-data バックアップの入口。**OFF なら `super` を呼ばない＝何も書き出さない。**
     *
     * `super.onFullBackup` が `@xml/data_extraction_rules`（API 31+）／`@xml/backup_rules`（API 26-30）を
     * 解釈して対象を集める本体なので、呼ばなければ**既存 XML ルールの適用そのものが起きない**
     * ＝ルールを二重管理せずに全体を止められる（ON のときの対象は今までと1バイトも変わらない）。
     *
     * `this`（agent 自身）を Context に使うのは、[BackupAgent] が `ContextWrapper` で、attach 時に
     * アプリのデータディレクトリを指す base context を受け取っているため＝`applicationContext` を
     * 経由しなくても正しい `shared_prefs/` を読む。
     */
    override fun onFullBackup(data: FullBackupDataOutput) {
        if (!BackupOptIn.isEnabled(this)) return
        super.onFullBackup(data)
    }

    /**
     * key-value バックアップ。本アプリは使わない（`fullBackupOnly=true`）ので**意図的に空**。
     * [BackupAgent] が abstract で要求するため実装だけ置く＝呼ばれること自体が想定外。
     */
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    /**
     * key-value 復元。上と同じ理由で空。
     *
     * ⚠️ **full-data の復元（`onRestoreFile`）は上書きしない**＝基底の実装に任せる。
     * なぜ復元側を制御しないか: 復元はインストール直後・利用者がアプリを起動する**前**に走るので、
     * そこで読めるオプトイン値は「旧端末で書いた値がバックアップに入っていた場合」だけ＝意味が入れ子になる。
     * バックアップ側だけを制御すれば足りる（OFF の人はそもそもバックアップが空＝復元されるものが無い）。
     */
    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}
