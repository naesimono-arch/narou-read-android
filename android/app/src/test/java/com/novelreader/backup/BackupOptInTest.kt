package com.novelreader.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novelreader.PrefKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「読書記録の引き継ぎ」オプトイン値（[BackupOptIn]）の契約。
 *
 * ## ここで守れること／守れないこと（誤解を残さないために明記する）
 * [NovelReaderBackupAgent] 本体は **restricted mode** で走り、JVM 単体テストでも androidTest でも
 * 再現できない（設計ドラフト `.claude/plans/auto-backup-design-2026-08-26.md` §4.3）。
 * ＝「OFF のとき本当に1バイトも運ばれないか」は実機／エミュの `bmgr` でしか確かめられない。
 * 本テストが固定するのは agent が読む**入力側**だけ——既定値・永続化・置き場所の3点で、
 * ここが崩れると agent の分岐は正しくても結果が反転する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupOptInTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        // Robolectric の prefs はテスト間で残りうる＝既定値の検査が「前のテストの書き込み」を見ないようにする。
        BackupOptIn.prefs(context).edit().clear().commit()
    }

    @Test
    fun `既定は OFF（2026-09-03 人間裁定）`() {
        assertFalse(
            "キー不在で true を返すと、明示的に同意していない利用者のデータが黙って Google へ上がる",
            BackupOptIn.isEnabled(context),
        )
        assertFalse("既定値の定数そのものも OFF でなければ UI とテストが割れる", BackupOptIn.DEFAULT_ENABLED)
    }

    @Test
    fun `ON にすると永続化され、OFF に戻せる`() {
        BackupOptIn.setEnabled(context, true)
        assertTrue(BackupOptIn.isEnabled(context))

        BackupOptIn.setEnabled(context, false)
        assertFalse(BackupOptIn.isEnabled(context))
    }

    @Test
    fun `値は app_prefs の BACKUP_OPT_IN に入る（restricted mode から読める置き場であること）`() {
        // なぜ「置き場所」を検査するか: agent は Application も ContentProvider も生きていない文脈で
        // 走るため、素の Context API で読める SharedPreferences 以外に置くと**無音で読めなくなる**
        // （バックアップが常に OFF 相当へ倒れ、テストは緑のまま）。移設をコンパイルで止められないので、
        // ファイル名とキー名の対を実測でピン留めする。
        BackupOptIn.setEnabled(context, true)
        val raw = context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
        assertTrue(
            "app_prefs に BACKUP_OPT_IN が無い＝agent が読む場所と UI が書く場所がずれている",
            raw.contains(PrefKeys.BACKUP_OPT_IN),
        )
        assertEquals(true, raw.getBoolean(PrefKeys.BACKUP_OPT_IN, false))
    }

    @Test
    fun `SharedPreferences を直接渡す版も同じ規則で読む`() {
        // agent は Context 版を使うが、規則が2本に割れていないことを固定する
        //（片方だけ既定値を変える改修が入ると、UI と agent で解釈が食い違う）。
        val prefs = BackupOptIn.prefs(context)
        assertEquals(BackupOptIn.isEnabled(context), BackupOptIn.isEnabled(prefs))
        BackupOptIn.setEnabled(context, true)
        assertEquals(BackupOptIn.isEnabled(context), BackupOptIn.isEnabled(prefs))
    }
}
