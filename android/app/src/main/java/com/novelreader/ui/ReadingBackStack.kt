package com.novelreader.ui

/**
 * 読書フローの Back スタック（純データ構造・Compose 非依存＝JVM 単体テストで不変条件を固定できる）。
 *
 * 画面はファイル名で表す: [INDEX]（"index.html"）＝目次／それ以外＝章。
 * [screens] は本棚から読書画面へ「実際に辿った経路」の写しで、末尾が現在地。
 * この経路は前進操作（[openChapter]/[openToc]/[sibling]/[returnTo]）が既出画面への巻き戻し・退避元探索に使う。
 *
 * 【Back の定義＝経路逆再生でなく「必ず一つ上の階層へ」】（2026-07-19 ユーザー裁定・不変条件①）:
 * [back] は末尾を1枚 pop するのではなく、章なら目次を開き（[openToc] と同一＝左上 ← ボタンと一致）・
 * 目次なら本棚へ抜ける（null）。辿った経路には依存しない＝話送り・参照覗き等の L2↔L2 横移動を Back で
 * 逆走させない（裁定の第2要件）。これは 07/15 の「実経路を逆再生し直行なら Back 1発で本棚」設計の
 * 意図的撤回である（直行入場でも Back は目次を経て本棚＝左上 ← と一致。可視の 1→2 タップ化は裁定が織り込んだ回帰）。
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
     * 章から目次を開く（下端「目次」ボタン・章の Up ←）。既存の目次があればそこへ巻き戻し、
     * 本文直行で目次が無ければ積む。"index.html" を横移動で扱うと直行本文の段を失うため必ず drill 扱いにする。
     */
    fun openToc(): ReadingBackStack = navigate(INDEX, lateral = false)

    /**
     * 前後章の話送り（横移動＝置き換えで深さ不変＝不変条件②: 何話読み進めても段が増えないため
     * Back（＝一階層 up）は常に目次へまっすぐ上がるだけで済む）。
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
     * システム Back＝「必ず一つ上の階層へ」（2026-07-19 裁定）。経路の逆再生ではない:
     *   ・章 → 目次を開く（[openToc] と同一＝左上 ← ボタンと完全一致。既存目次へ巻き戻し・直行入場で無ければ積む）
     *   ・目次 → これ以上の上位が無い＝null を返す（呼び出し側が onNavigateToBookshelf で本棚へ抜ける）
     * [openToc] へ委譲することで Back と章の ← が定義上つねに同じ遷移になり、分岐の二重管理を無くす。
     */
    fun back(): ReadingBackStack? =
        if (current == INDEX) null else openToc()

    companion object {
        /** 目次を表すファイル名（章ファイルと区別する唯一のセンチネル）。 */
        const val INDEX: String = "index.html"

        /**
         * 入場スタック＝辿った経路の起点1枚。startFile が章なら [本文直行]＝Back で目次を経てから本棚（2段）、
         * "index.html" なら [目次]＝そこから開いた章が push されて Back で目次→本棚。
         * （startFile は getLastRead()＝続きが在れば章・無ければ "index.html"＝MainActivity/BookshelfScreen）。
         */
        fun initial(startFile: String): ReadingBackStack = ReadingBackStack(listOf(startFile))
    }
}
