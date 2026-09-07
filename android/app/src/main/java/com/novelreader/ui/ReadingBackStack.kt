package com.novelreader.ui

/**
 * 読書フローの Back スタック（純データ構造・Compose 非依存＝JVM 単体テストで不変条件を固定できる）。
 *
 * 画面はファイル名で表す: [INDEX]（"index.html"）＝目次／それ以外＝章。
 * [screens] は入場元（本棚／作品詳細）から読書画面へ「実際に辿った経路」の写しで、末尾が現在地。
 * この経路は前進操作（[openChapter]/[openToc]/[sibling]/[returnTo]）が既出画面への巻き戻し・退避元探索に使う。
 *
 * 【Back の定義＝「前画面へ1段戻る」】（2026-09-07 裁定・不変条件①）:
 * [back] は末尾を1枚 pop し、下段が無ければ null（＝読書フローの終端。呼び出し側が入場元へ戻す）。
 * システム Back と左上 ← ボタン（章・目次とも）は**この1関数だけ**を叩く＝同じ画面で操作によって
 * 行き先が割れない。各入場形の帰結:
 *   ・直行入場 [章]（続きから・通知）… Back/← とも1発で読書フローを出る（目次へは下端「目次」ボタン）
 *   ・目次経由 [目次,章] … 章 → 目次 → 読書フロー脱出
 *   ・直行入場から目次を開いた [章,目次] … 目次 → 章（前画面。本棚へは落ちない）
 *
 * 【なぜ 2026-07-19「必ず一つ上の階層へ」を覆したか】（これで3度目の反転＝理由を残す）:
 * 07/19 が階層 up を採った目的は〈Back と ← の行き先が割れる〉ことの解消で、その一致自体は
 * 「両者が同じ関数を叩く」ことで保たれる＝階層 up は目的でなく手段の一つでしかなかった。
 * 一方その手段は「読書画面の親＝本棚」を固定前提に置いており、2026-09-04 に作品詳細の
 * 「アプリで読む」が2つ目の入場元になった時点で前提が壊れた（詳細から入場しても本棚へ落ちる）。
 * 前画面遷移は親を固定しないので入場元へ正しく帰れ、発見サブツリーの up（1 pop・ADR 0026）とも
 * 同一モデルになる＝アプリ内に2つのナビゲーションモデルが同居する状態も併せて解消する。
 * 07/19 がもう一つ求めた「横移動（話送り・覗き）を Back で逆走させない」は [back] の定義ではなく
 * 下の前進2規則が担っているため、この改訂でも失われない（話送りを何話続けても下段は増えない）。
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
     * 章から目次を開く（下端「目次」ボタン＝2026-09-07 以降このボタンが目次への唯一の導線）。
     * 既存の目次があればそこへ巻き戻し、本文直行で目次が無ければ積む。
     * "index.html" を横移動で扱うと直行本文の段を失うため必ず drill 扱いにする。
     * ⚠️ 章の ← はここを叩かない（← は [back]＝前画面へ。旧「← ＝目次を開く」は 07/19 契約の遺物）。
     */
    fun openToc(): ReadingBackStack = navigate(INDEX, lateral = false)

    /**
     * 前後章の話送り（横移動＝置き換えで深さ不変＝不変条件②: 何話読み進めても段が増えないため、
     * [back] が末尾を1枚 pop しても「読んできた章列を1話ずつ逆走する」ことにはならない）。
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
     * システム Back と左上 ← ボタンの唯一の実装＝「前画面へ1段戻る」（2026-09-07 裁定）:
     *   ・下段が在る … 末尾を1枚捨てて前画面を現在地にする（[screens] は必ず1枚縮む＝空は生まない）
     *   ・下段が無い（入場画面1枚のみ） … null＝読書フローの終端。呼び出し側が入場元（本棚／作品詳細）へ戻す
     * 画面側で「章なら目次・目次なら脱出」と分岐させないのが要: 分岐を持たせると 07/15→07/19 の
     * ように Back と ← の実装が別々に育ち、同じ画面で操作によって行き先が割れる（3度の反転の真因）。
     */
    fun back(): ReadingBackStack? =
        if (screens.size == 1) null else ReadingBackStack(screens.dropLast(1))

    companion object {
        /** 目次を表すファイル名（章ファイルと区別する唯一のセンチネル）。 */
        const val INDEX: String = "index.html"

        /**
         * 入場スタック＝辿った経路の起点1枚。startFile が章なら [本文直行]＝Back/← は1発で読書フローを出る、
         * "index.html" なら [目次]＝そこから開いた章が push されて Back/← は 章→目次→脱出 の2段になる。
         * （startFile は getLastRead()＝続きが在れば章・無ければ "index.html"＝MainActivity/BookshelfScreen）。
         * ⚠️ 脱出先（本棚 or 作品詳細）はこの構造の関心事ではない＝入場元は NavController 側が知る
         * （MainActivity.upFromReading）。ここに入場元を持ち込むと同じ状態が2箇所に住む。
         */
        fun initial(startFile: String): ReadingBackStack = ReadingBackStack(listOf(startFile))
    }
}
