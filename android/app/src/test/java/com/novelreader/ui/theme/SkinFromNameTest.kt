package com.novelreader.ui.theme

import com.novelreader.BuildConfig
import com.novelreader.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スキン永続化文字列（SharedPreferences "app_skin"）からの復元 [skinFromName] の防御契約と、
 * 公開スコープ機能ゲート（ADR 0027 適用点3）のクランプ。
 *
 * なぜテストするか:
 *  ①保存値は enum の生 String＝将来スキンを改名・削除した端末では不正値が残る。そのとき起動クラッシュでは
 *    なく既定の明快K へ静かに戻ることがこの関数の存在理由（"reading_theme" と同じ防御）。
 *  ②公開ビルドでは**正当な保存値も**明快K へ寄せる必要がある。入口（きせかえ行・装いの間ルート）を塞いでも、
 *    すでに D/M を選んである端末や Auto Backup 復元はここを通って素通りするため（ADR 0027「なぜ入口を消す
 *    だけでは足りないか」）。フラグは引数で受ける＝JVM テストが見られない release 側の経路をここで固定する。
 */
class SkinFromNameTest {

    @Test
    fun `キー不在(null)は既定の明快Kへ`() {
        // 2026-07-23 デフォルトを和モダンD→明快Kへ変更（plan default-ui-clarity-K）
        assertEquals(Skin.MEIKAI_K, skinFromName(null))
    }

    @Test
    fun `保存済みの各スキン名は正しく復元される`() {
        // 全 enum 値を機械的に往復（スキン追加時にこのテストが自動で新値も覆う）
        Skin.entries.forEach { skin ->
            assertEquals(skin, skinFromName(skin.name))
        }
    }

    @Test
    fun `不正値(改名・削除済みスキン)はクラッシュせず既定Kへ`() {
        assertEquals(Skin.MEIKAI_K, skinFromName("REMOVED_SKIN_X"))
        assertEquals(Skin.MEIKAI_K, skinFromName(""))
    }

    @Test
    fun `ゲートon(開発ビルド)＝保存値をそのまま復元する`() {
        // 引数を明示＝ビルド variant に依存せず on 側の契約を固定する（上の既定引数版と同じ結果になるべき）。
        Skin.entries.forEach { skin ->
            assertEquals(skin, skinFromName(skin.name, skinSwitchingEnabled = true))
        }
        assertEquals(Skin.MEIKAI_K, skinFromName(null, skinSwitchingEnabled = true))
    }

    @Test
    fun `ゲートoff(公開ビルド)＝保存値がどれでも明快Kへクランプする`() {
        // ここが ADR 0027 の穴塞ぎの本体。D/M を選んである検証機・Auto Backup 復元の両方がこの経路を通る。
        Skin.entries.forEach { skin ->
            assertEquals(
                "保存値 ${skin.name} が公開ビルドで素通りしている",
                Skin.MEIKAI_K,
                skinFromName(skin.name, skinSwitchingEnabled = false),
            )
        }
        assertEquals(Skin.MEIKAI_K, skinFromName(null, skinSwitchingEnabled = false))
        assertEquals(Skin.MEIKAI_K, skinFromName("REMOVED_SKIN_X", skinSwitchingEnabled = false))
    }

    @Test
    fun `本番配線＝既定引数はビルド時定数を読む（debug は on）`() {
        // 引数省略時に何も読んでいない（常に true 固定など）事故を防ぐ。
        // 限界の明示: JVM テストが見られるのは debug の BuildConfig だけで、**release=false は本テストでは
        // 保証できない**（build.gradle の buildTypes が正本・実測は generateReleaseBuildConfig の出力）。
        assertTrue("debug でスキン軸が塞がると開発中に装いを試せない", BuildConfig.SKIN_SWITCHING_ENABLED)
        assertEquals(BuildConfig.SKIN_SWITCHING_ENABLED, Features.skinSwitchingEnabled)
        assertEquals(skinFromName(Skin.SEIZU_M.name, Features.skinSwitchingEnabled), skinFromName(Skin.SEIZU_M.name))
    }
}
