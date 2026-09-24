package com.novelreader.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.novelreader.PrefKeys
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Auto Backup 復元による偽の ABNORMAL_EXIT を落とす仕掛け（[InstallSentinel]）の振る舞い。
 *
 * ## なぜ純関数テスト（[DiagnosticsTest]）だけでは足りないか
 * `shouldReportAbnormalExit` の真理値表を固定しても、**徴をいつ立てるか・残骸をいつ畳むか**は
 * [SessionWatch.onProcessStart] の手続き側にある。とくに「復元直後に残骸を畳み忘れると、
 * 偽陽性が消えるのではなく**次の起動へ1回ずれる**」という失敗は真理値表からは見えない。
 * そこで本テストは実物（実ファイルの徴・実 prefs・実 [DiagnosticsStore]）を通して起動を並べ、
 * **記録が出る／出ない**を件数で見る。
 *
 * ## 対で縛っていること（片方だけでは意味が無い）
 *  ・復元直後（このインストールでの起動歴が無い）は記録**しない**。
 *  ・それ以外の起動では本物の異常終了を**これまでどおり記録する**——復元後の2回目以降も含む。
 *    ここが緑でなければ、この修正は「偽陽性を消すために診断ごと殺した」ことになる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionWatchRestoreGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var prefs: SharedPreferences
    private lateinit var store: DiagnosticsStore
    private lateinit var recorder: DiagnosticsRecorder

    /** 復元では決して現れない場所（本番は `Context.getNoBackupFilesDir()`）の代役。 */
    private lateinit var noBackupDir: File

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // SessionWatch は prefs を注入で受けるので、app_prefs 本体を汚さない専用ファイルで回す。
        prefs = context.getSharedPreferences("session_watch_restore_guard_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = DiagnosticsStore(tmp.newFolder("diagnostics"))
        recorder = DiagnosticsRecorder(context, store)
        noBackupDir = tmp.newFolder("no_backup")
    }

    /** 1回のプロセス起動を模す（毎回 new する＝本番もプロセスごとに作り直されるため）。 */
    private fun launchApp() =
        SessionWatch(prefs, recorder, NoBackupFileInstallSentinel(noBackupDir)).onProcessStart()

    /** 「前面で使っている最中に消えた」痕跡を prefs に置く（旧端末が書いた状態＝復元されてくる形）。 */
    private fun leaveOpenSession(lastSeenAt: Long = OLD_DEVICE_MILLIS) {
        prefs.edit()
            .putBoolean(PrefKeys.DIAG_SESSION_OPEN, true)
            .putLong(PrefKeys.DIAG_LAST_SEEN_AT, lastSeenAt)
            .putString(PrefKeys.DIAG_LAST_SCREEN, "reading/oldbook")
            .commit()
    }

    /** このインストールで既に起動歴がある状態にする（＝徴を立てる）。 */
    private fun markInstallAsKnown() = NoBackupFileInstallSentinel(noBackupDir).create()

    @Test
    fun `本物の異常終了は記録される（徴が立っている通常の起動）`() {
        markInstallAsKnown()
        leaveOpenSession()

        launchApp()

        assertEquals("本物の異常終了が記録されていない＝診断そのものを殺している", 1, store.count())
        assertTrue(store.dumpAll().contains("ABNORMAL_EXIT"))
    }

    @Test
    fun `復元直後の初回起動では記録しない（旧端末の残骸を自分の異常終了と読まない）`() {
        // 復元で現れるのは prefs だけ。no_backup/ の徴は復元対象外なので立っていない。
        leaveOpenSession()

        launchApp()

        assertEquals("復元由来の残骸を偽の異常終了として記録した", 0, store.count())
    }

    @Test
    fun `復元直後に畳むので偽陽性が次の起動へずれない`() {
        // これが「畳み忘れ」の検知器。畳まないと2回目は徴が在る＝本物として通ってしまう。
        leaveOpenSession()

        launchApp() // 復元直後（抑止される）
        launchApp() // その次の起動（徴は既に在る）

        assertEquals("偽陽性が消えたのではなく1回ずれていた", 0, store.count())
        assertFalse(
            "残骸の DIAG_SESSION_OPEN が畳まれていない",
            prefs.getBoolean(PrefKeys.DIAG_SESSION_OPEN, false),
        )
    }

    @Test
    fun `復元した端末でも、その後に起きた本物の異常終了は記録される`() {
        // 抑止は初回の1回きりであって、この端末の診断を恒久的に殺すものではない。
        leaveOpenSession()
        launchApp() // 復元直後＝抑止
        assertEquals(0, store.count())

        // ここから先はこの端末の実使用。前面に出て、そのまま消えた（背面へ回っていない）。
        SessionWatch(prefs, recorder, NoBackupFileInstallSentinel(noBackupDir))
            .onForeground(nowMillis = THIS_DEVICE_MILLIS)

        launchApp()

        assertEquals("復元を経た端末で本物の異常終了が拾えていない", 1, store.count())
        assertTrue(store.dumpAll().contains("ABNORMAL_EXIT"))
    }

    @Test
    fun `徴はバックアップされない場所のファイルとして立つ`() {
        // 徴が prefs 側に混ざっていたら、それ自体が復元されて仕掛けが無効になる（無音の退行）。
        // 「立つ場所」を実測で固定し、prefs へ移す改修をここで止める。
        assertFalse(NoBackupFileInstallSentinel(noBackupDir).isPresent())

        launchApp()

        assertTrue(
            "起動しても徴が立たない＝毎回『初回』に見え、異常終了が永久に記録されなくなる",
            NoBackupFileInstallSentinel(noBackupDir).isPresent(),
        )
        assertTrue(noBackupDir.listFiles().orEmpty().any { it.isFile })
    }

    private companion object {
        /** 旧端末が最後に生存確認を書いた時刻（この時刻で偽の記録が作られるのが元の欠陥）。 */
        const val OLD_DEVICE_MILLIS = 1_700_000_000_000L

        /** 復元後、この端末で実際に前面へ出た時刻。 */
        const val THIS_DEVICE_MILLIS = 1_800_000_000_000L
    }
}
