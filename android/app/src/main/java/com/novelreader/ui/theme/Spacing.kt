package com.novelreader.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 余白の離散スケール（値の正本＝ADR 0014 §C・2026-07-13 拡張7段裁定）。
 *
 * スケール＝{4, 8, 12, 16, 24, 32, 40}。任意 dp の直書きは禁止（丸め＝最近傍・
 * 等距離は大きい側）。12/32 は悉皆調査（.claude/plans/F-spacing-audit-raw-2026-07-12.json）
 * で 8↔16・24↔40 の谷に実データが密集していたための限定追加＝裁定の Why は ADR 0014 参照。
 *
 * 命名が数値なのは意図的: 7段を T シャツ命名（XS〜XXL）にすると段の追加・改訂で
 * 序数がずれ意味ドリフトを生む。数値名なら正本モックの px と 1:1 で翻訳でき、
 * tools/check_design_tokens.py の機械検査（トークン参照 lint）も最短になる。
 */
object Spacing {
    val S4 = 4.dp // 最小アキ（チップ内・バッジ・微ギャップ）
    val S8 = 8.dp // 近接グループ内の標準ギャップ
    val S12 = 12.dp // カード/シート内側の標準パディング（旧 10/11/12/13 帯の受け皿）
    val S16 = 16.dp // ブロック間・画面横マージンの標準
    val S24 = 24.dp // セクション間・グリッドギャップ（呼吸の単位）
    val S32 = 32.dp // 大セクション区切り・シート下余白（旧 28/30/34 帯の受け皿）
    val S40 = 40.dp // 画面リズムの最大単位（空状態・大見出し回り）
}

/**
 * 構造インセット（base scale 外＝ADR 0014 §C の除外軸）。
 *
 * ここに載るのは「他要素の寸法・回避距離から決まる」値で、余白のリズムではない。
 * だから離散スケールへ丸めず、意味ごとの命名トークンで顕在化する（値の由来は各コメント）。
 */
object Insets {
    /** 本棚グリッド下端: FAB と最終行の重なり回避ぶん。 */
    val ScrollBottomForFab = 96.dp

    /** 読書: クローム復帰ヒントを下端バーの上へ逃がすクリアランス（FAB 回避と同値だが別意味）。 */
    val ChromeHintBottom = 96.dp

    /** 続きカード: 本文ブロック下の大余白（次章導線との分離）。 */
    val ContinuationBodyBottom = 60.dp

    /** 読書本文の上クリアランス: statusBars インセットへの加算ぶん（WindowInsets 加算軸）。 */
    val ReadingBodyTopExtra = 64.dp

    /** 読書本文の下クリアランス: navigationBars インセットへの加算ぶん（WindowInsets 加算軸）。 */
    val ReadingBodyBottomExtra = 80.dp

    /** M星図: 星空リスト末尾／スナックバーが下辺の地平（発見導線＋迎える・モック .horizon 112px 相当）と
     *  重ならないための回避距離。地平コンポーネントの実高から決まる構造値＝リズムの余白ではない。 */
    val SkyHorizonClearance = 120.dp
}
