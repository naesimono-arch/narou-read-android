package com.novelreader.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile の生成シナリオ（起動経路のみ）。
 *
 * ⚠️ 対象パッケージを定数で持たない理由: baselineprofile プラグインは :app へ
 * nonMinifiedRelease variant を自前で生成する。その applicationId は buildType 構成次第で
 * 素の "com.novelreader" にも接尾辞付きにもなり得る（既存 benchmark buildType は ".benchmark" を付ける）。
 * ここを固定値で書くと**プラグインが計測させたいアプリと別のアプリを静かに計測して**、
 * それらしいプロファイルが出てしまう＝赤にならない失敗になる。
 * プラグインは producer の instrumentation runner 引数 targetAppId に対象を注入するので、それを唯一の出所にする。
 * 引数が無いときは黙って既定値へ落とさず例外で落とす（別アプリ計測より「生成失敗」の方が発見できるため）。
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = resolveTargetAppId(),
        // 起動経路のみ。読書ルートのスクロールまで含めるには蔵書シードが要る（下の TODO）。
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // TODO(次便): 読書ルートを含める。:macrobenchmark の LibrarySeeding.clearAndSeedLibrary で
        // 計測用の1冊を投入 → 本棚スクロール → 章を開く → 章内スクロール → 章送り、まで伸ばす。
        // シード実装は :macrobenchmark 側にしか無く、そのまま呼べない（モジュール分離の代償）。
    }

    /**
     * 計測対象の applicationId を instrumentation 引数から取り出す。
     *
     * ⚠️ 自分自身を指していないかを必ず検査する。ここを素通しすると macrobenchmark が
     * 「対象アプリ」として**テストプロセス自身を force-stop** し、instrumentation が
     * 実行 0.7 秒で落ちる＝Gradle 側には Java 例外も出ずに
     * "Process crashed." としか出ない（原因がまったく読めない失敗になる。2026-09-02 に実際に踏んだ）。
     * 値を添えて明示的に落とせば、同じ配線ミスが次は一読で分かる。
     */
    private fun resolveTargetAppId(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val selfPackage = instrumentation.context.packageName
        val targetAppId = InstrumentationRegistry.getArguments().getString(TARGET_APP_ID_ARG)
            ?: error(
                "$TARGET_APP_ID_ARG が instrumentation 引数に無い＝baselineprofile プラグイン経由で起動されていない",
            )
        check(targetAppId != selfPackage) {
            "$TARGET_APP_ID_ARG が計測用テストAPK自身($selfPackage)を指している＝配線ミス。" +
                "計測対象アプリの applicationId でなければならない" +
                "（targetContext=${instrumentation.targetContext.packageName}）"
        }
        return targetAppId
    }

    private companion object {
        // baselineprofile Gradle プラグインが producer の instrumentation 実行時に渡す引数名（固定）
        const val TARGET_APP_ID_ARG = "targetAppId"
    }
}
