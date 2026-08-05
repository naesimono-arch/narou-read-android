package com.novelreader.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ShioriCover の高負荷アニメ経路（2026-08-06 裁定）の描画レベル検証。
 *
 * 固定するもの:
 *  1) トグル OFF（既定）＝時間を進めても1pxも変わらない（既存 golden 非影響の実描画根拠）
 *  2) トグル ON でも静止区間は OFF と1pxも変わらない（モックの「静止時の意匠不変」規則の実描画照合）
 *  3) 振り付けの動作区間（tip0 の一閃 69%）では絵が変わる（アニメが実際に合成へ入っている陽性確認）
 *  4) tip 1〜8 も ON で描画が落ちない（9振り付けの描画スモーク）
 *
 * 時計は withInfiniteAnimationFrameMillis 経由＝mainClock（autoAdvance=false）で任意送りできる。
 * カード位相（title 決定論）は shioriHighLoadPhaseSec を同じ式で引いて目標フレーム時刻を逆算する。
 *
 * 撮像はリポジトリ既定の Roborazzi（captureRoboImage→PNG→decode 比較）を使う。
 * なぜ captureToImage でないか（2026-08-06 ゲート FAIL の真因）: captureToImage は前段の forceRedraw が
 * 「次の描画フレーム」を waitUntil で待つが、paused mainClock では仮想時計が進まず新フレームが来ない＝
 * 2000ms timeout で必ず落ちる（WindowCapture.android.kt:178→124）。Roborazzi の compose 撮像は
 * fetchSemanticsNode＋waitForIdle のみで View 階層を直接 draw する（1.30.1 の bytecode で forceRedraw／
 * waitUntil 不在を確認）＝フレーム待ちを踏まないため paused clock と両立する（golden 100枚と同一の描画経路）。
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ShioriCoverHighLoadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // 撮像 PNG の置き場＝JUnit 管理の一時領域（テスト後に自動削除＝golden ディレクトリを汚さない）。
    @get:Rule
    val tmpDir = TemporaryFolder()

    /**
     * 現在フレームを PNG へ書き出して読み戻す。taskType=Record を明示する理由: Roborazzi は gradle の
     * record/verify プロパティ未指定（＝素の testDebugUnitTest）では captureRoboImage が no-op になる標準挙動
     * （ScreenshotTestSupport の注記どおり）で、本テストは「その場で撮って比較」が本体のため無条件に
     * 撮像させる必要がある。golden 運用（記録/検証の二段）とは独立＝比較は本テスト内で完結し、
     * 出力は一時領域のみ＝src/test/screenshots の golden には一切触れない。
     */
    private fun capture(name: String): Bitmap {
        // newFile でなく未作成パスを渡す＝Record の「新規書き出し」以外のファイル意味論を踏まない。
        val file = File(tmpDir.root, "$name.png")
        composeTestRule.onRoot().captureRoboImage(file, RoborazziOptions(taskType = RoborazziTaskType.Record))
        return BitmapFactory.decodeFile(file.absolutePath)
            ?: error("撮像 PNG の読み戻しに失敗: $file")
    }

    @Test
    fun `トグルOFFは時間を進めても完全静止（1pxも変わらない）`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                Box(Modifier.size(150.dp, 225.dp)) {
                    ShioriCover(
                        title = TITLE,
                        modifier = Modifier.size(150.dp, 225.dp),
                        persistedTipIndex = 0,
                        persistedLenFrac = 0.46f,
                        highLoadAnim = false,
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(16)
        val before = capture("off_t0")
        // tip0 の一閃（69%×7.4s≒5.1s）を跨ぐだけ進める＝OFF で動く実装ならここで必ず差が出る。
        composeTestRule.mainClock.advanceTimeBy(5_200)
        val after = capture("off_t5200")
        assertTrue("トグル OFF で描画が変化した（完全静止の破れ）", before.sameAs(after))
    }

    @Test
    fun `トグルONの静止区間はOFFと同一で・一閃の区間では変化する`() {
        val phase = shioriHighLoadPhaseSec(TITLE)
        val period = 7.4f // tip0 魚尾
        var anim by mutableStateOf(false)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                Box(Modifier.size(150.dp, 225.dp)) {
                    ShioriCover(
                        title = TITLE,
                        modifier = Modifier.size(150.dp, 225.dp),
                        persistedTipIndex = 0,
                        persistedLenFrac = 0.46f,
                        highLoadAnim = anim,
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(16)
        val staticOff = capture("on_static_off")

        composeTestRule.runOnUiThread { anim = true }
        // waitForIdle が必須（2026-08-06 再ゲート FAIL の真因）: snapshot の apply 通知は
        // GlobalSnapshotManager→AndroidUiDispatcher＝Robolectric main looper 便で流れるが、
        // mainClock.advanceTimeBy は kotlinx テストスケジューラしか進めず looper を汲まない。
        // 汲まないままだとトグルの invalidation が次の capture 内 waitForIdle まで届かず、
        // 時計コルーチンの 0 点（start 正規化）が丸ごと1捕獲ぶん遅れて、snap 狙いの 69% が
        // 実際には rest 窓内（〜47%）に着地して「変化なし」で落ちる。ここで looper を汲んで
        // invalidation を Recomposer へ確実に届けてから、フレームで再コンポーズ＋時計起動をさせる。
        composeTestRule.waitForIdle()
        // 再コンポーズ＋時計コルーチンの初回フレーム（＝クロック 0 への正規化）に2フレーム与える。
        composeTestRule.mainClock.advanceTimeBy(32)
        var elapsedMs = 32L

        // 静止区間の中央（30%）へ。tip0 の全トラック（rot≤66%・flex≤64%・G≤66%・bf0≤66%）が rest の点。
        val restTarget = ((0.30f * period - phase).mod(period) * 1000f).toLong()
        composeTestRule.mainClock.advanceTimeBy(cyclesAhead(restTarget, elapsedMs, period) - elapsedMs)
        elapsedMs = cyclesAhead(restTarget, elapsedMs, period)
        val restOn = capture("on_rest")
        // モックの規則「静止時の意匠を壊さない」の実描画照合＝rest 値はすべて恒等変形なので OFF と一致する。
        assertTrue("ON の静止区間が OFF（静的意匠）と一致しない", staticOff.sameAs(restOn))

        // 一閃の頂点（69%＝t0-L rotate 15deg）へ。ここで差が出なければアニメは合成に入っていない。
        val snapTarget = ((0.69f * period - phase).mod(period) * 1000f).toLong()
        composeTestRule.mainClock.advanceTimeBy(cyclesAhead(snapTarget, elapsedMs, period) - elapsedMs)
        val snapOn = capture("on_snap")
        assertFalse("ON の一閃区間で描画が変化しない（アニメ未合成）", snapOn.sameAs(restOn))
    }

    @Test
    fun `tip1〜8もONで描画スモーク（全振り付けの描画関数が実行できる）`() {
        var tip by mutableStateOf(1)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                Box(Modifier.size(150.dp, 225.dp)) {
                    ShioriCover(
                        title = TITLE,
                        modifier = Modifier.size(150.dp, 225.dp),
                        persistedTipIndex = tip,
                        persistedLenFrac = 0.46f,
                        highLoadAnim = true,
                    )
                }
            }
        }
        for (i in 1..8) {
            composeTestRule.runOnUiThread { tip = i }
            // apply 通知は looper 便＝先に汲まないと tip 切替が1周遅れで描かれ、スモークが
            // 「前の tip をもう一度描いただけ」になる（ON/OFF テストと同じ真因の別症状）。
            composeTestRule.waitForIdle()
            // 位相を散らしながら数フレーム描く（描画関数のクラッシュ・変形合成の破綻をあぶり出すスモーク）。
            composeTestRule.mainClock.advanceTimeBy(700)
            capture("smoke_tip$i")
        }
    }

    private companion object {
        const val TITLE = "高負荷モード検分用の栞"

        /** 目標周期内オフセット target(ms) を、既経過 elapsed(ms) より先の同位相時刻へ持ち上げる。 */
        fun cyclesAhead(targetMs: Long, elapsedMs: Long, periodSec: Float): Long {
            val periodMs = (periodSec * 1000f).toLong()
            var t = targetMs
            while (t <= elapsedMs + 48) t += periodMs // +48ms＝フレーム丸め（16ms 粒度）の安全余白
            return t
        }
    }
}
