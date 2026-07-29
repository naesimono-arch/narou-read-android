package com.novelreader.sourcescan

import java.io.File

// ============================================================
// ソースツリー走査エンジン（バグ型に依存しない土台・2026-07-29）。
//
// 用途: 「この形のコードは既知の罠を踏み得る」を機械列挙し、テストの登録簿と突合するメタテスト（L2）の部品。
// 走査ロジック（根の発見・宣言の抽出・括弧の対応・メンバ境界）だけをここに置き、**何を危険な形とみなすか**は
// 呼び出し側（バグ型ごとのメタテスト）が述語で与える＝別のバグ型へ横展開するときはここを触らずに済む。
//
// 置き場所について: 当初は唯一の利用者（発見ホームのメタテスト）と同じパッケージに置き「2つ目のバグ型が
// 採用した時点で中立なテスト用パッケージへ移す」と決めていた。2026-07-30 に禁忌パターン走査
// （HazardousPatternScanTest）が2つ目の利用者になったため、予定どおり中立な `com.novelreader.sourcescan` へ移した。
//
// 既知の限界（許容する理由つき）:
//  - 正規表現ベース＝Kotlin の完全なパーサではない。文字列リテラル中の `//` や括弧に引きずられる余地が
//    ある。ただし誤りが出る方向は「宣言・出現を1つ多く/少なく拾う」で、多い側は登録簿へ足せば解消し、
//    少ない側（＝取りこぼし）は現ツリーで実測して0であることを確かめてある（各利用者のコメント参照）。
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

    /** コメントを落とした .kt 1ファイル（走査は全てこの本文に対して行う＝位置が一貫する）。 */
    internal data class SourceFile(val relativePath: String, val text: String)

    /**
     * クラス／オブジェクト直下（またはトップレベル）の宣言＝「メンバ」。
     * 関数本体の中の局所 `val` は含めない（含めると出現の帰属先が局所変数名になり、突合キーが無意味になる）。
     *
     * @param start 宣言の開始位置（インデント込み）。
     * @param endExclusive 次のメンバの開始位置（＝このメンバが占める範囲の終端）。
     * @param isSuspend 宣言行に `suspend` が付くか（キャンセル文脈の判定に使う）。
     */
    internal data class Member(
        val name: String,
        val start: Int,
        val endExclusive: Int,
        val isSuspend: Boolean,
    )

    /**
     * 危険な形の1出現。突合キーは `<相対パス>#<メンバ名>::<式の先頭 [SNIPPET_LENGTH] 文字>`。
     *
     * なぜ行番号を使わないか: 行番号は無関係な編集で総入れ替えになり、登録簿が毎回赤くなって信用を失う。
     * メンバ名＋式の先頭なら「同じコードが同じ場所に在る限り不変」で、しかも登録簿を読めば何を許したのかが分かる。
     * 限界: 同一メンバ内に**完全に同一の先頭を持つ出現**が複数あると1キーへ畳まれ、1件の理由で複数件を許す。
     * この畳み込みは偽陰性そのものなので、利用者側で「重複キーが無いこと」を検査して落とすこと。
     */
    internal data class Occurrence(
        val relativePath: String,
        val member: String,
        /** 出現の開始位置（式の先頭）。 */
        val start: Int,
        /** 出現の終端（呼び出し側が与えた式の範囲。連鎖まで含む）。 */
        val endExclusive: Int,
        val snippet: String,
    ) {
        val id: String get() = "$relativePath#$member::$snippet"
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

    /** [root] の [subPath] 配下の .kt を、コメント除去済み本文つきで返す（相対パスは [root] 基準）。 */
    fun sourceFiles(root: File, subPath: String = ""): List<SourceFile> =
        File(root, subPath).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map {
                SourceFile(
                    relativePath = it.relativeTo(root).path.replace(File.separatorChar, '/'),
                    text = stripComments(it.readText()),
                )
            }
            .toList()
            .sortedBy { it.relativePath }

    /**
     * [root] の [subPath] 配下の .kt から関数宣言を抽出する（相対パスは [root] 基準で付ける＝
     * 走査範囲を絞っても登録簿の突合キーが変わらない）。
     *
     * コメントは先に落とす（KDoc 中の `fun xxx(` を宣言と誤認しないため）。抽出は
     * 「`fun` → 省略可能な型引数 → 省略可能なレシーバ → 関数名 → 丸括弧」で、引数リストは
     * 括弧の深さを数えて閉じ括弧まで取る（既定引数の中の括弧に切られないように）。
     */
    fun declarations(root: File, subPath: String = ""): List<Declaration> =
        sourceFiles(root, subPath).flatMap { file ->
            DECLARATION.findAll(file.text).mapNotNull { match ->
                val openParen = match.range.last // 正規表現の末尾が '('
                val closeParen = matchingClose(file.text, openParen) ?: return@mapNotNull null
                Declaration(
                    relativePath = file.relativePath,
                    functionName = match.groupValues[1],
                    signature = file.text.substring(openParen, closeParen + 1),
                )
            }
        }

    /** [open] の位置の `(` / `{` / `[` に対応する閉じ括弧の位置。対応が取れなければ null。 */
    fun matchingClose(text: String, open: Int): Int? {
        val openChar = text[open]
        val closeChar = when (openChar) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            else -> return null
        }
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /**
     * [close] の閉じ括弧に続くメソッド連鎖（`.fold(...)` `.onFailure { ... }` `.build()` 等）を
     * 飲み込んだ末尾位置（exclusive）。「式1つぶんの範囲」を取るために使う。
     */
    fun expressionEnd(text: String, close: Int): Int {
        var i = close + 1
        while (true) {
            var j = i
            while (j < text.length && text[j].isWhitespace()) j++
            if (j >= text.length || text[j] != '.') return i
            var k = j + 1
            while (k < text.length && (text[k].isLetterOrDigit() || text[k] == '_')) k++
            if (k == j + 1) return i // `.` の後が識別子でない＝連鎖ではない
            var w = k
            while (w < text.length && text[w].isWhitespace()) w++
            i = if (w < text.length && (text[w] == '(' || text[w] == '{')) {
                (matchingClose(text, w) ?: return k) + 1
            } else {
                k
            }
        }
    }

    /**
     * クラス／オブジェクト直下（またはトップレベル）の宣言だけを、出現順に返す。
     *
     * 判定は波括弧の深さを追いながら「その `{` を開いたのが型宣言（class/object/interface）か本体か」を
     * 記録し、**全フレームが型宣言のときだけ**メンバとみなす。関数本体・ラムダの中の局所宣言は落ちる。
     * 型宣言の判定は `{` の手前を丸括弧の対ごと畳んでから最後の行を見る（コンストラクタ引数が複数行に
     * わたる／既定値にラムダ `{}` が入るケースで `class` を見失わないため＝実測で踏んだ）。
     */
    fun members(text: String): List<Member> {
        val declAt = HashMap<Int, MatchResult>()
        MEMBER_DECL.findAll(text).forEach { declAt[it.groups[1]!!.range.first] = it }
        val found = ArrayList<Triple<String, Int, Boolean>>()
        val containerStack = ArrayList<Boolean>()
        for (i in text.indices) {
            val c = text[i]
            if (c == '{') {
                containerStack.add(opensContainer(text, i))
            } else if (c == '}') {
                if (containerStack.isNotEmpty()) containerStack.removeAt(containerStack.size - 1)
            } else {
                val decl = declAt[i] ?: continue
                if (containerStack.any { !it }) continue
                val lineEnd = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                found += Triple(decl.groupValues[2], i, SUSPEND.containsMatchIn(text.substring(i, lineEnd)))
            }
        }
        return found.mapIndexed { index, entry ->
            Member(
                name = entry.first,
                start = entry.second,
                endExclusive = if (index + 1 < found.size) found[index + 1].second else text.length,
                isSuspend = entry.third,
            )
        }
    }

    /** [index] を含む（＝直前に始まる）メンバ。該当が無ければ null。 */
    fun memberAt(members: List<Member>, index: Int): Member? =
        members.lastOrNull { it.start <= index }

    /**
     * ブロックコメント→行コメントの順で落とす。
     * 行コメント除去は文字列リテラル中の `//`（URL 等）も食うが、消えるのはその行の残りだけで、
     * 関数宣言は行頭側にあるため宣言の取りこぼしにはならない（現ツリーで実測確認済み）。
     */
    // internal（旧 private）: 他のソース不変条件テストも「実コードに X が無いこと」を検査するため同じ除去が要る。
    // 生テキストへの contains は why を説明するコメント中の言及まで拾って偽陽性になる（2026-07-29 に
    // ReadingWindowContractTest が実際に踏んだ＝ChapterScreen の所有権コメントで fail した）。重複実装を避けて開く。
    fun stripComments(source: String): String =
        source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")

    /** 空白を1つに畳んで先頭 [SNIPPET_LENGTH] 文字を取る（突合キーの断片づくり）。 */
    fun snippet(text: String): String =
        text.split(WHITESPACE_RUN).filter { it.isNotEmpty() }.joinToString(" ").take(SNIPPET_LENGTH)

    /**
     * [brace] の `{` を開いたのが型宣言かどうか。
     *
     * 手前を「直前の構文境界（丸括弧の外に在る `{` `}` `;`）」まで遡って宣言ヘッダを丸ごと取り、丸括弧の対を
     * 畳んでから最後の行にキーワードが在るかを見る。固定長の窓で切ると**コンストラクタ引数が長いクラスで
     * `class` を見失い、そのクラスのメンバが1件も取れなくなる**（2026-07-30 に ScrapeHttpClient で実測）。
     * `Foo::class.java` を型宣言と誤認しないよう `::`／`.` に続く keyword は除く。
     */
    private fun opensContainer(text: String, brace: Int): Boolean {
        var i = brace - 1
        var parenDepth = 0
        val limit = maxOf(0, brace - CONTAINER_LOOKBACK)
        while (i >= limit) {
            when (val c = text[i]) {
                ')' -> parenDepth++
                '(' -> if (parenDepth > 0) parenDepth--
                else -> if (parenDepth == 0 && (c == '{' || c == '}' || c == ';')) break
            }
            i--
        }
        var head = text.substring(i + 1, brace)
        while (true) {
            val folded = PAREN_GROUP.replace(head, " ")
            if (folded == head) break
            head = folded
        }
        return CONTAINER_KEYWORD.containsMatchIn(head.substringAfterLast('\n'))
    }

    private const val MODULE_RELATIVE_SOURCE = "src/main/java/com/novelreader"
    private const val MAX_PARENT_HOPS = 8
    private const val CONTAINER_LOOKBACK = 2000

    /** 突合キーに使う式断片の長さ。短すぎると別物が同キーへ畳まれ、長すぎると些細な編集で登録簿が腐る。 */
    const val SNIPPET_LENGTH = 48

    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("""//[^\n]*""")
    private val WHITESPACE_RUN = Regex("""\s+""")
    private val PAREN_GROUP = Regex("""\([^()]*\)""")
    private val CONTAINER_KEYWORD = Regex("""(?<![:.])\b(?:class|object|interface)\b""")
    private val SUSPEND = Regex("""\bsuspend\b""")
    private val DECLARATION =
        Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.<>?]+\.)?([A-Za-z0-9_]+)\s*\(""")
    private const val MODIFIERS =
        "(?:(?:public|internal|private|protected|override|open|abstract|final|suspend|inline|" +
            "external|operator|infix|tailrec|const|lateinit|expect|actual)[ \\t]+)*"
    private val MEMBER_DECL =
        Regex("""(?:^|[\n;{}])([ \t]*$MODIFIERS(?:fun|val|var)[ \t]+(?:<[^>\n]*>[ \t]*)?(?:[A-Za-z0-9_.<>?]+\.)?([A-Za-z0-9_]+))""")
}
