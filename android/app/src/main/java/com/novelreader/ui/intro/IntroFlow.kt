// 「その回」の進みかたを決める純ロジック（Compose 非依存＝JVM で直接固定できる形にする）。
// 正本 docs/design-candidates/tutorial-onboarding-K.html §3/§8 の規則をそのまま式にしたもの。
package com.novelreader.ui.intro

import androidx.compose.runtime.Immutable

/**
 * 1 回ぶんの読み進み状態。**入口が渡すのは〈開始する組〉と〈通しで読むか〉の 2 値だけ**（正本 §8）。
 *
 * @param startGroup その回がどの組から始まるか。
 * @param walkthrough 設定＞操作の説明からの「通し」か（true＝組をまたいで列の最後まで進む）。
 * @param index 列（[IntroDeck.cards]）の絶対インデックス。組をまたぐ特別扱いを持たないための単一の位置。
 */
@Immutable
internal data class IntroFlow(
    val startGroup: IntroGroup,
    val walkthrough: Boolean,
    val index: Int = IntroDeck.firstIndexOf(startGroup),
) {
    val card: IntroCard get() = IntroDeck.cards[index]

    val group: IntroGroup get() = card.group

    /**
     * 終端かどうかは〈通し ? 列の最後 : 組の最後〉の 1 行で決まる（正本 §8）。
     * ここを組ごとの分岐で書くと入口が増えるたびに条件が増える＝二重実装の入口になる。
     */
    val isTerminal: Boolean
        get() = index == (if (walkthrough) IntroDeck.cards.lastIndex else IntroDeck.lastIndexOf(group))

    /**
     * **この回で出うるカードの範囲**（先頭〜終端）。カードの寸法を揃える単位はこれ＝
     * 描画側は範囲の全カードを測って最も高い 1 枚に本文域を合わせる（2026-09-07 実機所見の是正）。
     *
     * なぜ「列 7 枚」でなく「その回」か: 組A 単独の回で組D（最も高い）に合わせると、1 枚目に画面の
     * 3 分の 1 近い空白が空き、正本の「全体量を見せないための形」を自分で壊す。
     * なぜ [group] でなく [startGroup] で始めるか: 通しでは組をまたいで進むが [back] は開始位置より
     * 前へは戻れない＝出うるのは常に「開始位置から終端まで」の連続区間。
     */
    val runIndices: IntRange
        get() = IntroDeck.firstIndexOf(startGroup)..
            (if (walkthrough) IntroDeck.cards.lastIndex else IntroDeck.lastIndexOf(startGroup))

    /** その回の先頭カードか（先頭では ［← もどる］ の代わりに ［あとで］ を出す＝正本 §8）。 */
    val isFirst: Boolean get() = index == IntroDeck.firstIndexOf(startGroup)

    /**
     * 現在地の点は「**いま居る組**の枚数」ぶんだけ打つ（列全体の 5 個は絶対に打たない＝正本 §3 の要）。
     * 1 枚しかない組（組C）では 0＝打たない——1 個だけの点は壊れて見え、通しでは点の不在が
     * そのまま「これで終わり」の合図になる。
     */
    val dotCount: Int get() = IntroDeck.countIn(group).takeIf { it > 1 } ?: 0

    /** 点の現在地（組の中で何枚目か・0 始まり）。組が変わればここも 0 に戻る＝点のリセット。 */
    val dotIndex: Int get() = index - IntroDeck.firstIndexOf(group)

    /** 主ボタンの語。まだ先があるなら advance、その回の終端なら terminal（＝閉じる）。 */
    val primaryLabel: String get() = if (isTerminal) card.terminalLabel else card.advanceLabel

    /**
     * 副ボタン。先頭では ［あとで］（閉じる）、それ以降は ［← もどる］。
     * ただし **1 枚で終わる回（組C 単独）では副ボタンを置かない**——正本の組C は
     * `.obtns.r`（主ボタンのみ右寄せ）で、［あとで］と［とじる］が同義になって並ぶのを避けている。
     */
    val secondary: IntroSecondary
        get() = when {
            !isFirst -> IntroSecondary.BACK
            isTerminal -> IntroSecondary.NONE
            else -> IntroSecondary.LATER
        }

    /** ［← もどる］＝ただのインデックス −1。組をまたぐ特別扱いは要らない（正本 §8）。 */
    fun back(): IntroFlow = if (isFirst) this else copy(index = index - 1)

    fun advanced(): IntroFlow = if (isTerminal) this else copy(index = index + 1)
}

/** 副ボタンの3態（NONE＝置かない）。 */
internal enum class IntroSecondary { NONE, LATER, BACK }
