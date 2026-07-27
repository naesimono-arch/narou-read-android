package com.novelreader

/**
 * 設定キーの正本（SharedPreferences / Preferences DataStore の全キーを一元定義）。
 *
 * なぜ集約するか: キー文字列の直書きが各ファイルに散在すると、タイポで「書くのは新キー・
 * 読むのは旧キー」になってもコンパイルは通り、設定が黙って初期値へ戻る欠陥クラスが成立する
 * （実行時エラーすら出ない＝発見が最も遅いバグ）。定数参照ならタイポは即コンパイルエラー。
 *
 * 【重要】キーの文字列値は1文字も変えないこと。値は既存ユーザーの端末に永続済みで、
 * 変えた瞬間に旧キーの保存値へ到達できなくなり、その設定は全ユーザーで黙って初期値へ飛ぶ。
 * どうしても改名したいときは「旧キー読出→新キー書込→旧キー削除」の移行コードとセットでのみ
 * 行う（[SETTINGS_SCHEMA_VERSION] はその移行判別のための版番号キー）。
 */
object PrefKeys {
    // ── 置き場（ファイル/ストア名）。キー同様、タイポで「別ストアが静かに生える」ため一元化する ──

    /** アプリ全設定の SharedPreferences ファイル名（テーマ・読書設定・各種フラグの単一置き場）。 */
    const val FILE_APP_PREFS = "app_prefs"

    /** なろう検索履歴の Preferences DataStore 名（[com.novelreader.narou.DataStoreSearchHistoryStore]）。 */
    const val FILE_NAROU_SEARCH_HISTORY = "narou_search_history"

    // ── app_prefs: アプリ全体（MainActivity）──

    /** 設定スキーマ版（Int）。enum 生文字列保存の改名耐性のための版番号（MainActivity.SETTINGS_SCHEMA_VERSION が現行値）。 */
    const val SETTINGS_SCHEMA_VERSION = "settings_schema_version"

    /** 読書テーマ（String=ReadingTheme 名）。キー不在＝システムのライト/ダークへ追従。 */
    const val READING_THEME = "reading_theme"

    /** UIスキン（String=Skin 名）。キー不在＝D（既定装い）。 */
    const val APP_SKIN = "app_skin"

    /** 高負荷スカイ試作トグル（Boolean・debug 限定＝ADR 0023）。 */
    const val SKY_HIGH_LOAD_M = "sky_high_load_m"

    /** 新着話通知のオプトイン（Boolean・既定 false＝公理13）。 */
    const val NEW_EPISODE_NOTIFY_ENABLED = "new_episode_notify_enabled"

    // ── app_prefs: 読書画面（NativeReadingScreen）──

    /** 本文フォントサイズ sp（Int）。 */
    const val READING_FONT_SIZE = "reading_font_size"

    /** 本文行送り em（Float）。 */
    const val READING_LINE_HEIGHT = "reading_line_height"

    /** 本文左右マージン dp（Int）。 */
    const val READING_BODY_MARGIN = "reading_body_margin"

    /** 縦書きモード（Boolean）。 */
    const val READING_VERTICAL = "reading_vertical"

    /** 没入モードのヒント表示済みフラグ（Boolean・初回のみ表示）。 */
    const val IMMERSIVE_HINT_SHOWN = "immersive_hint_shown"

    // ── app_prefs: 本棚（BookshelfScreen / skins 配下）──
    // 使用側は現時点では文字列直書きのまま（当該ファイルは並行改修中のため張り替えは追って実施）。
    // 値の正本はこちら＝新規の読み書きは必ずこの定数を参照すること。

    /** 電池最適化の案内ダイアログを閉じた（Boolean）。 */
    const val BATTERY_DIALOG_DISMISSED = "battery_dialog_dismissed"

    /** D装い: グリッド表示か（Boolean）。 */
    const val IS_GRID_VIEW = "is_grid_view"

    /** K装い: グリッド表示か（Boolean）。 */
    const val K_GRID_VIEW = "k_grid_view"

    /** M装い: スカイ表示か（Boolean）。 */
    const val M_SKY_VIEW = "m_sky_view"

    /** P装い: ラック表示か（Boolean）。 */
    const val P_RACK_VIEW = "p_rack_view"

    /** J装い: デッキ表示か（Boolean）。 */
    const val J_DECK_VIEW = "j_deck_view"

    /** 通知許可プライミングの表示済みフラグ（Boolean）。 */
    const val NOTIF_PRIMING_SHOWN = "notif_priming_shown"

    /** P装い: ヒンジ開度のデテント段（Int 0..2）。 */
    const val P_HINGE_DETENT = "p_hinge_detent"

    // ── narou_search_history: なろう検索履歴（DataStore・DataStoreSearchHistoryStore）──

    /** ピン留め検索語（String・改行区切り）。 */
    const val SEARCH_HISTORY_PINNED = "pinned"

    /** 最近の検索語（String・改行区切り）。 */
    const val SEARCH_HISTORY_RECENT = "recent"
}
