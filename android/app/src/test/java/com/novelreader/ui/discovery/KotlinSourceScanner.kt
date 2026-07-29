package com.novelreader.ui.discovery

import java.io.File

// ============================================================
// ソースツリー走査エンジン（バグ型に依存しない土台・2026-07-29）。
//
// 用途: 「この形のコードは既知の罠を踏み得る」を機械列挙し、テストの登録簿と突合するメタテストの部品。
// 走査ロジック（根の発見・関数宣言の抽出）だけをここに置き、**何を危険な形とみなすか**は呼び出し側
// （バグ型ごとのメタテスト）が述語で与える＝別のバグ型へ横展開するときはここを触らずに済む。
//
// 置き場所について: いまの利用者が発見ホームのメタテスト1件だけなので同じパッケージに置いている。
// 2つ目のバグ型が採用した時点で中立なテスト用パッケージへ移すこと（それまでは移動しても利用者が
// 増えないので先回りしない）。
//
// 既知の限界（許容する理由つき）:
//  - 正規表現ベース＝Kotlin の完全なパーサではない。文字列リテラル中の `//` や括弧に引きずられる余地が
//    ある。ただし誤りが出る方向は「宣言を1つ多く/少なく拾う」で、多い側は登録簿へ足せば解消し、
//    少ない側（＝取りこぼし）は現ツリーで実測して0であることを確かめてある（下の抽出条件を参照）。
//  - シグネチャに現れない依存（VM から間接的に状態を受け取る等）は拾えない。述語側で補うこと。
// ============================================================

internal object KotlinSourceScanner {

    /** 1つの関数宣言。 */
    internal data class Declaration(
        /** 走査根からの相対パス（例: `ui/skins/k/DiscoveryHomeK.kt`）。 */
        val relativePath: String,
        val functionName: String,
        /** 丸括弧で囲まれた引数リスト本文（型の突合に使う）。 */
        val signature: String,
    ) {
        /** 登録簿との突合キー。 */
        val id: String get() = "$relativePath#$functionName"
    }

    /**
     * 走査根（`src/main/java/com/novelreader`）を探す。
     *
     * Gradle の Test タスクは作業ディレクトリをモジュール（`android/app`）にするが、IDE や将来の
     * 設定変更で変わりうるため cwd から親を遡って探す。**見つからないときは null を返し、
     * 呼び出し側は必ず fail させること**（パスが解けないまま黙って PASS するのが最悪＝
     * 検知器が死んでいるのにテストは緑、という 2026-07-12 の実例がある）。
     */
    fun findModuleSourceRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var hops = 0
        while (hops < MAX_PARENT_HOPS) {
            val current = dir ?: return null
            // モジュール直下から起動された場合（Gradle 既定＝Test.workingDir はモジュールディレクトリ）。
            File(current, MODULE_RELATIVE_SOURCE).takeIf { it.isDirectory }?.let { return it }
            // リポジトリ根・worktree 根から起動された場合の保険。
            File(current, "android/app/$MODULE_RELATIVE_SOURCE").takeIf { it.isDirectory }?.let { return it }
            dir = current.parentFile
            hops++
        }
        return null
    }

    /**
     * [root] の [subPath] 配下の .kt から関数宣言を抽出する（相対パスは [root] 基準で付ける＝
     * 走査範囲を絞っても登録簿の突合キーが変わらない）。
     *
     * コメントは先に落とす（KDoc 中の `fun xxx(` を宣言と誤認しないため）。抽出は
     * 「`fun` → 省略可能な型引数 → 省略可能なレシーバ → 関数名 → 丸括弧」で、引数リストは
     * 括弧の深さを数えて閉じ括弧まで取る（既定引数の中の括弧に切られないように）。
     */
    fun declarations(root: File, subPath: String = ""): List<Declaration> =
        File(root, subPath).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = stripComments(file.readText())
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                DECLARATION.findAll(text).mapNotNull { match ->
                    val openParen = match.range.last // 正規表現の末尾が '('
                    val closeParen = matchingCloseParen(text, openParen) ?: return@mapNotNull null
                    Declaration(
                        relativePath = relative,
                        functionName = match.groupValues[1],
                        signature = text.substring(openParen, closeParen + 1),
                    )
                }
            }
            .toList()

    /** [open] の位置の `(` に対応する `)` の位置。対応が取れなければ null。 */
    private fun matchingCloseParen(text: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /**
     * ブロックコメント→行コメントの順で落とす。
     * 行コメント除去は文字列リテラル中の `//`（URL 等）も食うが、消えるのはその行の残りだけで、
     * 関数宣言は行頭側にあるため宣言の取りこぼしにはならない（現ツリーで実測確認済み）。
     */
    private fun stripComments(source: String): String =
        source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")

    private const val MODULE_RELATIVE_SOURCE = "src/main/java/com/novelreader"
    private const val MAX_PARENT_HOPS = 8

    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("""//[^\n]*""")
    private val DECLARATION =
        Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.<>?]+\.)?([A-Za-z0-9_]+)\s*\(""")
}
