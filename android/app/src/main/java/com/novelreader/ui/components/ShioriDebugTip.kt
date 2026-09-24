package com.novelreader.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.novelreader.BuildConfig

// ============================================================
// ShioriDebugTip — 栞の先端意匠（tip）を1種に固定して観察する debug 限定スイッチ（2026-08-25）。
//
// なぜ要るか: 高負荷アニメ（tip 0〜8 の専用振り付け）の最終審級は実機の質感（ADR 0005 §B）なのに、
// tip は書影ごと（title/永続抽選）に決まる＝狙った tip を実機に出すには蔵書の並びを操作で戻し続けるしかなく、
// 2026-08-21 の実機ツアーで「目視不能」と判明した。tip を固定できれば棚の全冊が同じ先端になり、
// 0→8 を順に送って裁定できる（tip 9〜173 の絵柄検分にも同じ手が効くので全 tip を指定できる形にした）。
//
// なぜ「生成側の override」＝ここに process-global の状態を置くか: tip は
// 本棚グリッド(BookshelfK)→カード→[ShioriCover] と**引数で**流れており、途中の画面を書き換えずに差し込むには
// 生成の最終地点（[shioriParams]）で上書きするしかない。観察器のために本棚・カードの引数列を増やすと、
// release にも残る恒久 API になってしまう（＝影響面はここに閉じる方が小さい）。
//
// なぜ debug 限定か: これは意匠の観察器であって製品機能ではない。全冊の先端が同じ絵になる状態は
// 「同じ本＝同じ絵」という栞書影の生命線（ShioriGenerator 冒頭）をわざと壊すので、出荷ビルドには
// UI も挙動も一切出さない。[BuildConfig.DEBUG] は定数畳み込みされるため release では分岐ごと落ち、
// snapshot への購読すら発生しない（星図M・栞アニメの既存トグルと同じ露出規約）。
// ============================================================

/** 栞先端 tip の固定状態（プロセス内単一情報源）。永続化との橋渡しは MainActivity が持つ。 */
internal object ShioriDebugTip {
    /** 固定中の tip（null＝固定しない）。snapshot state なので、変えると描画中の栞が再コンポーズされる。 */
    private val state: MutableState<Int?> = mutableStateOf(null)

    /**
     * 現在の固定 tip。release では state を読まずに常に null を返す（＝観察器が挙動に出ない二重ガードの外側。
     * 読まないので snapshot 購読も張られず、既存の描画経路は1命令も変わらない）。
     */
    val fixedIndex: Int?
        get() = if (BuildConfig.DEBUG) state.value else null

    /** 固定 tip を設定する（null・範囲外＝解除）。release では no-op＝値を持てないので UI が無くても安全。 */
    fun set(index: Int?) {
        if (!BuildConfig.DEBUG) return
        state.value = sanitizeShioriDebugTip(index)
    }
}

/** prefs 保存値・UI 入力値の正規化（[0,[SHIORI_TIP_COUNT]) 以外は null＝固定しない）。 */
internal fun sanitizeShioriDebugTip(index: Int?): Int? = index?.takeIf { it in 0 until SHIORI_TIP_COUNT }

/**
 * 「debug ビルドか」を外から与えて固定値を解決する純関数（prefs 復元の入口）。
 *
 * なぜ [BuildConfig] を直読みせず引数で受けるか: JVM 単体テストは debug の BuildConfig しか見ないため、
 * release 側（常に null＝観察器が出荷ビルドへ漏れない）を引数でしか固定できない
 * （SettingsScreenK の露出可否引数と同流儀＝ADR 0027 決定4）。
 */
internal fun shioriDebugTipFor(isDebug: Boolean, storedIndex: Int?): Int? =
    if (isDebug) sanitizeShioriDebugTip(storedIndex) else null

/**
 * 固定 tip の送り（debug UI のステッパー用・純関数）。
 * 「解除→tip 0→tip 1→…→tip 173→解除」の巡回として扱う＝解除も1つの位置なので、
 * ±1/±10 のどちら向きに送っても必ず解除へ戻れる（行き止まりを作らない）。
 */
internal fun shioriDebugTipStep(current: Int?, delta: Int): Int? {
    val slots = SHIORI_TIP_COUNT + 1 // 位置0＝解除・位置1..N＝tip 0..N-1
    val cur = if (current == null) 0 else sanitizeShioriDebugTip(current)?.plus(1) ?: 0
    val next = ((cur + delta) % slots + slots) % slots // Kotlin の % は負を返すので二段で非負化
    return if (next == 0) null else next - 1
}
