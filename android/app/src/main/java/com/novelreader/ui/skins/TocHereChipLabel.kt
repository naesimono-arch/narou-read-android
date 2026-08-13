package com.novelreader.ui.skins

/**
 * 目次の現在地バーにある現在話チップの**読み上げ名**。K/D/M/J が共有する（スキンごとに書かない）。
 *
 * なぜ見た目と読み上げを分けるか（裁定 2026-08-07）:
 * 見た目のチップは「第N話」だけに短縮した。このバー自体が現在地バーなので「いま読んでいる」は
 * 画面の文脈で既に伝わる一方、チップは Row の**非加重子**として実寸を先取りし、
 * fontScale 2.0・4桁話数（第1024話／全1240話）ではチップ単独でバー全幅 656px を超え
 *（K 641px・D 627px・J 631px・M 563px＋星と間隔で 655px）、同じ行の進捗テキストへ残るのは
 * 15px / 29px / 25px / 1px＝**可視0文字**だった。前置き「いま読んでいる: 」の分が進捗側へ戻る。
 *
 * 読み上げには画面の文脈が無い（バーの役割も配置も音にはならない）ため、視覚から削った語は
 * contentDescription で残す＝**短縮するのは見た目だけで、情報は落とさない**。
 *
 * @param currentIndex 現在章の 0 起点 index（呼び出し側で 0 以上を保証済み。表示は +1 の話数）
 */
internal fun tocHereChipContentDescription(currentIndex: Int): String =
    "いま読んでいる: 第${currentIndex + 1}話"

/** 現在話チップの**見える文字**（読み上げ名は [tocHereChipContentDescription]）。 */
internal fun tocHereChipLabel(currentIndex: Int): String = "第${currentIndex + 1}話"
