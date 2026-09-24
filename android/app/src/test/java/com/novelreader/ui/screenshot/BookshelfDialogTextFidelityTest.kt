package com.novelreader.ui.screenshot

import com.novelreader.sourcescan.KotlinSourceScanner
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [BookshelfDialogFixtures] の文言が**本番ソースに実在する**ことを機械照合する（L2 メタテスト）。
 *
 * ## なぜ要るか
 * 本棚ダイアログ群は `BookshelfScreen` のインラインラムダで書かれており、テストから呼べる
 * Composable にも定数にもなっていない。そのため golden の撮影テストは文言の**写し**を持たざるを得ない
 * （機序と代替案の却下理由は [captureDialogSkinned] の KDoc）。写しは放っておくと本番から静かに離れ、
 * 「golden で守っているつもり」が嘘になる——`GoldenCoverageTest` が潰した「撮っていない面が
 * 免除理由なしで放置される」型と同じ、**絵の比較では原理的に検出できない**種類の穴。
 * 宣言だけの約束（「文言を変えたら写しも直すこと」）は数週間で崩れる（ADR 0017 決定5）ので機械の番人を置く。
 *
 * ## 照合の正規化（なぜ素の contains ではないか）
 *  1. コメント除去: 「なぜ」コメントが同じ文言に言及していると素の contains が通ってしまう
 *     （2026-07-29 に ReadingWindowContractTest が実際に踏んだ偽陽性と同型）。
 *  2. 連結の畳み込み: 本番は長文を `"…" +\n  "…"` と折り返して書く。`"` `+` `"` の**継ぎ目だけ**を消して
 *     1つのリテラルへ戻す（リテラルの中身には触らない＝別々の文字列が偶然つながることはない）。
 *  3. 改行の再エスケープ: 写しは実改行を持つが、ソース上は `\n` の2文字。写し側を `\n` へ戻して突き合わせる。
 * `${…}` テンプレートで割れる文言は静的部だけを [BookshelfDialogFixtures.SOURCE_FRAGMENTS] に登録している
 * （件数・書名は撮影用の固定値であって本番の文言ではないため照合対象にしない）。
 *
 * ## この検査の限界
 * 「写しが本番に在る」ことしか見ない＝本番に**在るが撮っていない**文言（一括再取込・走査結果の内訳行）は
 * ここでは見つからない。そちらは撮影テスト側の KDoc と `GoldenCoverageRegistry` に理由付きで残してある。
 */
class BookshelfDialogTextFidelityTest {

    @Test
    fun `撮影テストの文言の写しはすべて本番ソースに実在する`() {
        val root = requireSourceRoot()
        val normalized = HashMap<String, String>()
        val missing = mutableListOf<String>()

        BookshelfDialogFixtures.SOURCE_FRAGMENTS.forEach { (fragment, relativePath) ->
            val text = normalized.getOrPut(relativePath) {
                val file = File(root, relativePath)
                assertTrue("照合先が存在しない: $file", file.isFile)
                normalizeSource(file.readText())
            }
            if (!text.contains(fragment.replace("\n", "\\n"))) missing += "$relativePath: 「$fragment」"
        }

        if (missing.isNotEmpty()) {
            fail(
                "BookshelfDialogFixtures の写しが本番ソースに見つからない:\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\n本番の文言を変えたなら (1) BookshelfDialogFixtures の定数、(2) 必要なら SOURCE_FRAGMENTS、" +
                    "(3) 該当 golden の撮り直し（recordRoborazziDebug）の3点をセットで更新すること。" +
                    "写しだけが古いまま残ると、golden は緑のまま**存在しない文言の版面**を守り続ける。",
            )
        }
    }

    @Test
    fun `照合器が壊れていないこと（正規化と存在しない文言の双方を実証）`() {
        // なぜ要るか: 上の検査はパスが解けない・正規化が空文字を返す等で黙って全通過し得る
        // （検知器が死んでいるのにテストは緑＝2026-07-12 の実例）。番人自身の生死をここで固定する。
        val root = requireSourceRoot()
        val shelf = normalizeSource(File(root, "ui/BookshelfScreen.kt").readText())
        assertTrue("BookshelfScreen.kt の正規化結果が空＝走査が死んでいる。", shelf.length > 10_000)
        assertTrue(
            "連結の畳み込みが効いていない（`\"…\" + \"…\"` を跨ぐ文言が繋がっていない）。",
            shelf.contains(BookshelfDialogFixtures.NOTIF_BODY),
        )
        assertTrue(
            "存在しないはずの文言が見つかった＝照合が常に真を返している疑い。",
            !shelf.contains("この文言は本番のどこにも存在しない番人用のカナリア"),
        )
        assertTrue(
            "SOURCE_FRAGMENTS が空＝登録簿が空でも上の検査は緑になる。",
            BookshelfDialogFixtures.SOURCE_FRAGMENTS.isNotEmpty(),
        )
    }

    /** コメントを落とし、`"…" + "…"` の継ぎ目だけを畳んで1リテラルへ戻す。 */
    private fun normalizeSource(source: String): String =
        KotlinSourceScanner.stripComments(source).replace(LITERAL_JOIN, "")

    private fun requireSourceRoot(): File =
        KotlinSourceScanner.findModuleSourceRoot() ?: run {
            fail("src/main/java/com/novelreader を解決できなかった（cwd=${System.getProperty("user.dir")}）。")
            error("unreachable")
        }

    private companion object {
        /** `" +`（改行・インデント可）` "` ＝隣り合う文字列リテラルの継ぎ目。 */
        val LITERAL_JOIN = Regex("\"\\s*\\+\\s*\"")
    }
}
