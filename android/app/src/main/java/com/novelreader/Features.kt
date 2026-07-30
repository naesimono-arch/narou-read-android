package com.novelreader

/**
 * 公開スコープの機能ゲート（ADR 0027）。**中身だけが差し替わる一枚板**として置く。
 *
 * 何を解くか: 「初回公開は明快K 単独＝D/C/M/P/J と装いの間ごと隠す」という方針が、コード側に
 * 実現手段を1つも持っていなかった（release でも装いの間から6種すべて選べた）。
 *
 * なぜ適用点で `BuildConfig` を直に読まないか（ADR 0027 決定4・5）:
 *  1. JVM 単体テストは debug の BuildConfig しか見ない＝off 側の経路をテストで固定できず、
 *     「テストは緑のまま公開ビルドだけ壊れる」クラスの退行を作る。適用点はフラグを**引数で**受け、
 *     本ファイルは値の出所だけを持つ（両値の検証は引数側で行う）。
 *  2. 課金実装後、この判定は「デバッグビルドか」から「購入したか」へ意味が変わる。読み口を1点に
 *     閉じておけば、そのとき差し替えるのはここだけで**適用点3つは不変**でいられる——それが本 object の目的。
 *
 * 現在の適用点は3つだけ（増やさないことが ADR 0027 の眼目）:
 *  - 設定「きせかえ」行を出さない（`ui/skins/k/SettingsScreenK.kt`）
 *  - 装いの間ルートを NavHost へ登録しない（`MainActivity.kt` の `wardrobeRoute`）
 *  - 保存値の復元を明快K へクランプする（`ui/theme/Skin.kt` の `skinFromName`）
 */
object Features {

    /**
     * スキン軸を外へ出してよいか。値の出所はビルド時定数（debug=true / release・benchmark=false）。
     *
     * `val` を getter 式にしているのは、将来ここが購入状態の読み出し（＝実行時に変わりうる値）へ
     * 差し替わるため。定数へ潰すと呼び出し側が「起動時に1度だけ確定する値」を前提に書き始める。
     */
    val skinSwitchingEnabled: Boolean
        get() = BuildConfig.SKIN_SWITCHING_ENABLED
}
