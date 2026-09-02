// 教示「はじめに」のカード列（正本モック docs/design-candidates/tutorial-onboarding-K.html §3/§4/§8 の翻訳）。
// 意匠の自己判断はしない＝文言・順序・ボタン語・点の打ちかたはすべて正本の写経で、ここでは決めない。
package com.novelreader.ui.intro

import androidx.compose.runtime.Immutable

/**
 * カードの「出す単位」。前版の「章」（実感が要るかでの仕分け）から意味が変わり、
 * いまは〈どの画面に着いたときに出すか〉を表す（正本 §3）。
 */
internal enum class IntroGroup {
    /** 組A「このアプリのこと」＝初回・本棚が空のとき（1・2 枚目）。読んだ経験を要さない事実だけ。 */
    ABOUT,

    /** 組B「読みかた」＝本文を初めて開いたとき（3・4 枚目）。実感と説明が同時に届く。 */
    READING,

    /** 組C「さがしかた」＝検索画面を初めて開いたとき（5 枚目）。 */
    SEARCH,
}

/** カード内の項目立て（組B・組C）。dt＝割り当ての名前・dd＝その中身。 */
@Immutable
internal data class IntroItem(val label: String, val text: String)

/**
 * カードが載せる二者択一（いまは組A の 2 枚目＝本文の向きだけ）。正本 §4 の「選択肢」行に対応する。
 *
 * **ここに現在値も onSelect も持たせない**——[IntroDeck.cards] は `object` 直下の不変リストで、
 * アプリ通算 1 個しか存在しない。可変の状態を混ぜた瞬間に「列＝文言の正本」ではなくなり、
 * 端末の設定値がカード定義に貼り付く（テストも並び順に依存して壊れる）。
 * 現在値と確定は [IntroController] が持ち、描画のときだけ突き合わせる。
 */
@Immutable
internal data class IntroChoice(
    /** 値が false のときのラベル。ここでは横書き＝[com.novelreader.PrefKeys.READING_VERTICAL] の既定側。 */
    val labelForFalse: String,
    /** 値が true のときのラベル。ここでは縦書き。 */
    val labelForTrue: String,
)

/**
 * カード1枚。**列は1本・描画コンポーネントも1つ**という正本 §8 の要を型でも守るため、
 * 「どの回で出すか」を持たせず〈属する組〉だけを持たせる（回の切りかたは [IntroFlow] が決める）。
 *
 * [text] 中の `**…**` は強調＝正本の `<b>`／`<em>` に対応する。段落中の強調は墨（onSurface）、
 * 項目中の強調は藍（primary）で、色の出し分けは描画側が文脈で決める（正本 CSS の `.ocard p b` と
 * `.ocard dd em` がまさにそう分かれている）。
 */
@Immutable
internal data class IntroCard(
    val group: IntroGroup,
    val figure: IntroFigure,
    val title: String,
    /** 本文段落（組A）。 */
    val paragraphs: List<String> = emptyList(),
    /** 項目立て（組B・組C）。1 カード 3〜4 項目までに抑える＝正本 §4 の上限。 */
    val items: List<IntroItem> = emptyList(),
    /**
     * 2 択チップの行（正本 §4 の「選択肢」）。null＝そのカードは何も選ばせない。
     * 現在値は持たない（[IntroChoice] の KDoc 参照）。
     */
    val choice: IntroChoice? = null,
    /** `.later`＝再訪導線を伝える小さい行（組の最後のカードにだけ置く＝正本 §5）。 */
    val footnote: String? = null,
    /** まだ先があるときの主ボタン語。 */
    val advanceLabel: String = "つぎへ",
    /** その回の終端になったときの主ボタン語（＝閉じる）。 */
    val terminalLabel: String = "とじる",
)

/**
 * 順序つきの不変リスト 1 本（6 要素）。**2 つ目の列を作らないこと**——入口が増えても増やすのは
 * 呼び出し側だけ、というのが正本 §3/§8 の設計の芯で、ここを分けた瞬間に文言が二重管理になる。
 */
internal object IntroDeck {

    val cards: List<IntroCard> = listOf(
        IntroCard(
            group = IntroGroup.ABOUT,
            figure = IntroFigure.SHELF,
            title = "はじめに",
            paragraphs = listOf(
                "**PDF** と Web の連載小説（**小説家になろう**）を、ひとつの本棚で読めます。",
                "漢字の**ふりがなは自動でつきます**。",
            ),
            advanceLabel = "つづける",
        ),
        IntroCard(
            group = IntroGroup.ABOUT,
            figure = IntroFigure.ORIENTATION,
            title = "どちらで読みますか",
            paragraphs = listOf("本文を**縦書き**にも**横書き**にもできます。"),
            // 初期選択は縦書き（[IntroController.orientationVertical] の既定）＝アプリ名も短い説明も
            // 縦書きを看板にしているのに初見が横書きで開く、という反転をここで閉じるのがこのカードの用。
            choice = IntroChoice(labelForFalse = "横書き", labelForTrue = "縦書き"),
            // 「あとから変えられる」を書かないと**取り返しのつかない選択**に見える。書くと選択が
            // 軽くなる懸念はあるが、このカードは設定の代わりではなく**既定値の確認**で、
            // 触らず ［つづける］ でも初期選択が確定する形なので、逃げ道を示しても選択は空振りしない。
            footnote = "あとから ［表示設定］＞［本文の向き］ で変えられます。",
            advanceLabel = "つづける",
        ),
        IntroCard(
            group = IntroGroup.ABOUT,
            figure = IntroFigure.TWO_WAYS,
            title = "読みかたは、2 通り",
            paragraphs = listOf(
                "**アプリで読む**　ふりがな付き。文字の大きさや縦書きに変えられます。",
                // ⚠️ 「ふりがなは付かず」は**事実誤認**だったので 2026-09-03 に是正（ADR 0042）——
                // なろうのページには**作者が付けたルビがそのまま出る**。効かないのはアプリ側の機能なので
                // 主語をアプリへ寄せ、実装済みの NarouExternalPageNotice.BODY と同じ言い回しへ揃える
                // （同じ事実を2通りに言うと、どちらかが直された時にもう一方が黙って腐る）。
                "**なろうで読む**　なろうのページをそのまま表示。アプリの操作・表示設定・ふりがな機能は効きません。",
            ),
            footnote = "読みかたの説明は、読みはじめてから ［設定］＞［操作の説明］ で。",
            terminalLabel = "はじめる",
        ),
        IntroCard(
            group = IntroGroup.READING,
            figure = IntroFigure.TAP,
            title = "横書きで読む",
            items = listOf(
                IntroItem("メニューを出す", "本文のどこでもタップ。**もう一度で消えます**。"),
                IntroItem("次の話へ", "**左へスワイプ**。下のバーの ［次章］ でも。"),
            ),
        ),
        IntroCard(
            group = IntroGroup.READING,
            figure = IntroFigure.VERTICAL,
            title = "縦書きで読む",
            items = listOf(
                IntroItem("縦書きにする", "下のバーの ［表示設定］ ＞ ［本文の向き］。文字の大きさ・行間・余白も同じ場所です。"),
                IntroItem("読み進める", "**左へスワイプ**（横書きでは「次の話へ」）。"),
                IntroItem("次の話へ", "最後まで進めて、さらに左へ。［次章］ でも。"),
            ),
            terminalLabel = "とじる",
        ),
        IntroCard(
            group = IntroGroup.SEARCH,
            figure = IntroFigure.SEARCH,
            title = "さがして、本棚に入れる",
            items = listOf(
                IntroItem("検索範囲", "言葉は**選んだ範囲の中だけ**を探します。複数選べます。"),
                IntroItem("もっと絞る", "［条件を調整］ から、ジャンル・更新時期・文字数・読了時間・除外語など。"),
                IntroItem("本棚に入れる", "作品の詳細画面で ［本棚に置く］。PDF は本棚の ［PDFを追加］。"),
            ),
            footnote = "この説明は ［設定］＞［操作の説明］ にあります。",
            terminalLabel = "とじる",
        ),
    )

    fun firstIndexOf(group: IntroGroup): Int = cards.indexOfFirst { it.group == group }

    fun lastIndexOf(group: IntroGroup): Int = cards.indexOfLast { it.group == group }

    fun countIn(group: IntroGroup): Int = cards.count { it.group == group }
}
