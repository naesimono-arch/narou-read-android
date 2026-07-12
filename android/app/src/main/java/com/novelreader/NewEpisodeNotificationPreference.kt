package com.novelreader

import android.content.Context

/**
 * 「新着話を通知する」オプトイン設定の状態層（UX監査 C3・公理13『沈黙が既定値』）。
 *
 * なぜ既定 OFF か: U1 新着チェック（NewEpisodeCheckWorker）はアプリ未起動でもユーザーを読書へ
 * 呼び戻す push で、更新提示は本棚の「続き N話」バッジ（BookCard.NewChaptersBadge・無音・in-app）が
 * 既に担う。非時間性の更新を既定で push するのは公理13違反のため、通知はユーザーが明示 ON にした
 * ときだけに限定する（機能は撤去せずオプトインへ寄せる）。
 *
 * なぜ既存の app_prefs（SharedPreferences）に相乗りか: テーマ/文字サイズ等の既存ユーザー設定が
 * すべて "app_prefs" に集約されており（MainActivity.kt:83 ほか）、設定の置き場を一本化するため。
 */
object NewEpisodeNotificationPreference {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ENABLED = "new_episode_notify_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 通知が ON か。既定は false（オプトイン）。 */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    /** ON/OFF を永続化する。実際のスケジュール切替は
     *  [NovelReaderApplication.setNewEpisodeNotificationEnabled] が担う（状態層と制御を分離）。 */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
