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
    // 使用側の直書きは 2026-07-27 の純構造リファクタで全数この定数参照へ張替済み。
    // 値の正本はこちら＝新規の読み書きも必ずこの定数を参照すること。

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

    /** 本文欠落の一括検出バナー（案C）を提示済みの欠落 bookId 集合（StringSet）。
     *  同一集合では再表示しない＝「新規に検出した際に一度だけ表示」の指紋（domain/ReimportPlan.kt）。 */
    const val REIMPORT_SWEEP_SEEN_IDS = "reimport_sweep_seen_ids"

    /** 蔵書PDFの保管フォルダとして選ばれた SAF ツリー URI（String・案X＝domain/PdfFolderScan.kt）。
     *  値の意味: ACTION_OPEN_DOCUMENT_TREE の結果 URI 文字列。ここに値がある＝「一度場所を教わった」で、
     *  以後の本文欠落は folder picker を出さずに走査できる（案X の要）。
     *  ⚠ 値は端末固有の content:// 文字列で、他端末へバックアップ復元しても意味を持たない。読み出し側
     *  （BookshelfViewModel）は persistedUriPermissions に生きた権限がある場合だけ「記憶済み」と扱う
     *  ＝権限を失った URI を覚えているふりをしない（走査が SecurityException で全滅するのを防ぐ）。
     *  ⚠ キー文字列は不変（冒頭の【重要】参照）。 */
    const val PDF_LIBRARY_TREE_URI = "pdf_library_tree_uri"

    /** P装い: ヒンジ開度のデテント段（Int 0..2）。 */
    const val P_HINGE_DETENT = "p_hinge_detent"

    // ── app_prefs: 診断（com.novelreader.diagnostics）──
    // 異常終了の推定に使う3点。値はいずれも「前回プロセスの状態」で、次の起動で読んで判定する。

    /** 前面セッションが開いているか（Boolean）。開いたまま次の起動が来たら異常終了と推定する。 */
    const val DIAG_SESSION_OPEN = "diag_session_open"

    /** 前面での最終確認時刻（Long・epochMillis）。異常終了イベントの発生時刻として使う。 */
    const val DIAG_LAST_SEEN_AT = "diag_last_seen_at"

    /** 前面での最終表示画面（String）。異常終了イベントの発生画面として使う。 */
    const val DIAG_LAST_SCREEN = "diag_last_screen"

    // ── narou_search_history: なろう検索履歴（DataStore・DataStoreSearchHistoryStore）──

    /** ピン留め検索語（String・改行区切り）。 */
    const val SEARCH_HISTORY_PINNED = "pinned"

    /** 最近の検索語（String・改行区切り）。 */
    const val SEARCH_HISTORY_RECENT = "recent"
}
