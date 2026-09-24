package com.novelreader.ui

/**
 * 読書フローの Back スタック（純データ構造・Compose 非依存＝JVM 単体テストで不変条件を固定できる）。
 *
 * 画面はファイル名で表す: [INDEX]（"index.html"）＝目次／それ以外＝章。
 * [screens] は入場元（本棚／作品詳細）から読書画面へ「実際に辿った経路」の写しで、末尾が現在地。
 * この経路は前進操作（[openChapter]/[openToc]/[sibling]/[returnTo]）が既出画面への巻き戻し・退避元探索に使う。
 *
 * 【Back の定義＝「必ず一つ上の階層へ」】（2026-07-19 ユーザー裁定・07-29 に全アプリへ拡大＝ADR 0026）:
 * [back] は現在地が章なら目次へ上がり、現在地が目次なら null（＝読書フローの終端。呼び出し側が
 * 入場元へ戻す）。システム Back と左上 ← ボタン（章・目次とも）は**この1関数だけ**を叩く＝同じ画面で
 * 操作によって行き先が割れない。各入場形の帰結:
 *   ・直行入場 [章]（続きから・通知）… 章 → 目次 → 読書フロー脱出（2段。1発では出ない）
 *   ・目次経由 [目次,章] … 章 → 目次 → 脱出（同じ形に合流する）
 *   ・直行入場から目次を開いた [章,目次] … 目次 → 脱出（章へは降りない＝上がる方向しか無い）
 *
 * 【2026-09-07 に「前画面へ1段」へ倒したのを同日撤回した経緯】（ADR 0047・4度目の反転を防ぐ記録）:
 * ADR 0046 が [back] を「末尾1枚 pop」＝履歴逆走へ再定義したが、これは 07/19（handover に
 * **「再検討禁止」と明記**）と 07/29（ADR 0026「アプリ全体で ← も Back も階層 up」）という2つの
 * ユーザー裁定を Claude の自己裁定で覆したもので、実機目視の関門も通していなかった。
 * ⚠️ **0046 が挙げた動機自体は実在するバグ**（作品詳細の「アプリで読む」から入場しても終端が
 * popToTab(BOOKSHELF) 固定で本棚へ落ちる）。ただしそれは**終端の脱出先を入場元依存にする**だけで
 * 階層モデルのまま解ける（[MainActivity.upFromReading]）＝失効していたのは「読書の親＝本棚」という
 * **親の固定**であって、階層モデル自体ではなかった。モデルごと倒したのが 0046 の誤りである。
 * ∴ 次に規則を動かす提案は〈嗜好〉でも〈手段の一般性〉でもなく、**この階層 up が表現できない
 * 具体的な破綻**を示すこと。示せないなら 07/19・07/29 の裁定が有効である。
 * 07/19 がもう一つ求めた「横移動（話送り・覗き）を Back で逆走させない」は下の前進2規則が担う。
 *
 * 【なぜ「訪れた画面を無条件に積む」旧 navHistory を採らないか】（前進の深さを縛る規則・2026-07-12 バグ再発防止）:
 * 旧実装は前進・後退を問わず訪れたファイルを全て push したため、目次⇄章を覗くたびに段が増えた（重大 UX 問題）。
 * 本構造は前進を次の2規則で縛り、経路が無制限に伸びるのを封じる:
 *   1. 既出画面への移動は「その画面まで巻き戻す」＝重複を積まない（Jetpack の popUpTo(inclusive=false) 相当）。
 *      目次ボタンで既存の目次へ戻る／「続きに戻る」で退避元章へ復帰、が全てここに集約される。
 *   2. 章⇄章の話送り・続き復帰は「置き換え」（横移動）＝深さを増やさない。
 * この2規則により、覗き（目次→章→目次→別章…）を何度繰り返してもスタック深さは増えない（＝不変条件②）。
 */
data class ReadingBackStack(val screens: List<String>) {

    init {
        // 現在地（末尾）が常に要るため空スタックは不正。initial→各操作は決してこれを破らない設計。
        require(screens.isNotEmpty()) { "読書スタックは空にできない（少なくとも入場画面を1枚持つ）" }
    }

    /** 現在表示中の画面（末尾＝スタックトップ）。 */
    val current: String get() = screens.last()

    /**
     * 既出なら「その画面まで巻き戻す」・横移動なら「置き換え」・それ以外は「積む」の統一規則。
     * 既出判定を最優先にするのが不変条件②（覗きで段を増やさない・重複を積まない）の要。
     */
    private fun navigate(target: String, lateral: Boolean): ReadingBackStack {
        val existing = screens.indexOf(target)
        return when {
            // 規則1: 既出画面への移動＝popUpTo(inclusive=false) 相当。旧 navHistory の重複 push を封じる中核。
            // toList() で view でなく独立コピーにする（subList の view を保存/直列化に渡す事故を避ける）。
            existing >= 0 -> ReadingBackStack(screens.subList(0, existing + 1).toList())
            // 規則2: 横移動（話送り・続き復帰）は現在段を置き換え、深さを増やさない。
            lateral -> ReadingBackStack(screens.dropLast(1) + target)
            // それ以外は下層へ潜る新しい段（本棚/章から目次を開く・目次から章へ drill）。
            else -> ReadingBackStack(screens + target)
        }
    }

    /**
     * 目次から章を開く（drill down／覗き含む）。既出なら巻き戻し、無ければ積む。
     * 覗き（続き位置と別章）でも push→Back の pop で相殺されるため、反復しても深さは増えない（不変条件②）。
     */
    fun openChapter(file: String): ReadingBackStack = navigate(file, lateral = false)

    /**
     * 章から目次を開く（下端「目次」ボタン・章の Up ←・システム Back の3つが行き着く先）。
     * 既存の目次があればそこへ巻き戻し、本文直行で目次が無ければ積む。
     * "index.html" を横移動で扱うと直行本文の段を失うため必ず drill 扱いにする。
     * 章の ← とシステム Back は [back] 経由でここへ合流する（階層 up＝章の親は目次）＝
     * ⚠️ 目次への導線は下端ボタン単独ではない（ADR 0046 が書いた「このボタンが目次への唯一の導線」は、
     * 章の ← が目次を開かない前提＝履歴逆走でのみ真で、階層 up へ戻した今は偽）。
     */
    fun openToc(): ReadingBackStack = navigate(INDEX, lateral = false)

    /**
     * 前後章の話送り（横移動＝置き換えで深さ不変＝不変条件②: 何話読み進めても段が増えないため、
     * [back]（＝一階層 up）は「読んできた章列を1話ずつ逆走する」ことにならず常に目次へまっすぐ上がる。
     * why: 逆走しない根拠を back の定義だけに置かない——置き換えが章列を経路へ積まないことが要で、
     * これが崩れると back の定義を変えた瞬間に逆走が復活する）。
     * 端章の prev/next は目次（[INDEX]）へ抜けるため、その場合は [openToc] へ委譲する
     * （目次を横移動で置き換えると直行本文の下段を失うため）。
     */
    fun sibling(file: String): ReadingBackStack =
        if (file == INDEX) openToc() else navigate(file, lateral = true)

    /**
     * 「続きに戻る」＝参照ジャンプの退避元章へ復帰（横移動）。退避元が下段に在れば巻き戻し、
     * 無ければ現在の覗き章を置き換える（いずれも段を増やさない）。参照モード自体の解除は呼び出し側が担う。
     */
    fun returnTo(file: String): ReadingBackStack = navigate(file, lateral = true)

    /**
     * システム Back と左上 ← ボタンの唯一の実装＝「必ず一つ上の階層へ」（2026-07-19 裁定・ADR 0047 で復帰）:
     *   ・現在地が章 … 目次へ上がる（[openToc]＝既出の目次まで巻き戻し、直行入場で目次が無ければ積む）
     *   ・現在地が目次 … null＝読書フローの終端。呼び出し側が入場元（本棚／作品詳細）へ戻す
     * ⚠️ 末尾1枚 pop（履歴逆走）にしないこと: [章,目次] で目次から章へ**降りて**しまい、
     * 「戻るのに階層が下がる」が起きる（ADR 0046 の実害・実機で最初に指摘された症状）。
     * 画面側で分岐させないのが要: 分岐を持たせると 07/15→07/19 のように Back と ← の実装が
     * 別々に育ち、同じ画面で操作によって行き先が割れる（3度の反転の真因）。
     */
    fun back(): ReadingBackStack? = if (current == INDEX) null else openToc()

    companion object {
        /** 目次を表すファイル名（章ファイルと区別する唯一のセンチネル）。 */
        const val INDEX: String = "index.html"

        /**
         * 入場スタック＝辿った経路の起点1枚。startFile が章なら [本文直行]、"index.html" なら [目次]。
         * どちらも Back/← は 章→目次→脱出 の階層 up に合流する（直行入場は目次が積まれてから脱出）。
         * （startFile は getLastRead()＝続きが在れば章・無ければ "index.html"＝MainActivity/BookshelfScreen）。
         * ⚠️ 脱出先（本棚 or 作品詳細）はこの構造の関心事ではない＝入場元は NavController 側が知る
         * （MainActivity.upFromReading）。ここに入場元を持ち込むと同じ状態が2箇所に住む。
         */
        fun initial(startFile: String): ReadingBackStack = ReadingBackStack(listOf(startFile))
    }
}
