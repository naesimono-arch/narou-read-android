package com.novelreader.ui.discovery

import com.novelreader.sourcescan.KotlinSourceScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 取り込み画面の注入 JS が「寄せ位置を明示した scrollIntoView」であることを固定する（2026-09-04 実機所感の回帰）。
 *
 * 守る不変条件: **本番ソースに引数なしの `scrollIntoView()` を書かない。**
 * 引数なしの既定は `block:"start"`＝対象要素の上端をビューポート上端へ貼り付けるため、なろう目次の PDF 生成
 * フォーム(.c-under-nav)が画面の一番端に着地して「どこへ着いたのか分かりにくい」状態に戻る。どの値を選ぶかは
 * 実機の見えで決める設計判断（`AUTO_SCROLL_BLOCK` の KDoc が正本）なので、ここで縛るのは**値**ではなく
 * 「明示していること」だけ——`center` を `nearest` へ倒す運用がこのテストで妨げられてはならない。
 *
 * なぜソース走査か: 注入 JS は文字列のまま WebView へ渡るだけで、JVM 単体テストからは評価されない
 * （Robolectric の WebView は shadow で、スクロール結果の画素も要素座標も観測できない）。守りたいのは
 * 実装の形そのものなので `NavigationBarBandContractTest` と同じくソースの形で固定する。
 *
 * ⚠️ 陽性確認テストを対で置いてある（`引数なしへ戻すと落ちる`）。実ソースを in-memory で壊した形に置き換え、
 * 検知器が確かに違反を返すことを毎回機械確認する——検知器が死んでいるのにテストは緑、を繰り返さないため。
 */
class PdfImportAutoScrollContractTest {

    /** 走査根が解けないときは黙って PASS させず必ず fail させる（検知器の死を緑で隠さない）。 */
    private fun root(): File = KotlinSourceScanner.findModuleSourceRoot()
        ?: throw AssertionError(
            "走査根（src/main/java/com/novelreader）が解けない＝検知器が死んでいる。" +
                "作業ディレクトリ=${System.getProperty("user.dir")}",
        )

    /** [text] 中の `scrollIntoView(` のうち、引数が空のものを snippet で返す（空リスト＝違反なし）。 */
    private fun bareCalls(text: String): List<String> {
        val found = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = text.indexOf(CALL, from)
            if (at < 0) return found
            val open = at + CALL.length - 1 // CALL の末尾が '('
            val close = KotlinSourceScanner.matchingClose(text, open)
                ?: throw AssertionError("$CALL の括弧の対応が取れない＝検知器の前提が壊れている（位置=$at）")
            if (text.substring(open + 1, close).isBlank()) {
                found += KotlinSourceScanner.snippet(text.substring(at, close + 1))
            }
            from = close + 1
        }
    }

    @Test
    fun `本番ソースの scrollIntoView は寄せ位置を明示する`() {
        val files = KotlinSourceScanner.sourceFiles(root()) // コメント除去済み＝why を説明する言及で偽陽性にならない
        val withCall = files.filter { CALL in it.text }
        assertTrue(
            "本番ソースに $CALL が1件も無い＝注入 JS が消えたか走査が壊れている。自動スクロールを廃止したなら" +
                "このテストごと消すこと（黙って全通過させない）。",
            withCall.isNotEmpty(),
        )
        val violations = withCall.flatMap { file -> bareCalls(file.text).map { "${file.relativePath}: $it" } }
        assertEquals(
            "引数なしの $CALL は既定 block:\"start\"＝要素の上端が画面上端に貼り付き、着地点が画面の端になる。" +
                "寄せ位置を明示すること（取り込み画面は AUTO_SCROLL_BLOCK を参照する）:\n" +
                violations.joinToString("\n"),
            emptyList<String>(),
            violations,
        )
    }

    @Test
    fun `取り込み画面の注入 JS は主経路とフォールバックの両方で block を渡す`() {
        val text = KotlinSourceScanner.stripComments(File(root(), IMPORT_SCREEN).readText())
        // 2つの JS は「どちらが走ったか実機から判別できない」ため、片方だけ寄せ位置を持つ状態を許さない。
        assertEquals(
            "$IMPORT_SCREEN の $CALL は主経路・フォールバックの2箇所で、いずれも block を渡すこと",
            2,
            WITH_BLOCK.findAll(text).count(),
        )
    }

    @Test
    fun `引数なしへ戻すと落ちる`() {
        val broken = KotlinSourceScanner.stripComments(File(root(), IMPORT_SCREEN).readText())
            .replace(WITH_BLOCK, "scrollIntoView()")
        assertEquals(
            "壊した本文で違反を2件検出できない＝検知器が死んでいる",
            2,
            bareCalls(broken).size,
        )
    }

    private companion object {
        const val CALL = "scrollIntoView("
        const val IMPORT_SCREEN = "ui/discovery/PdfImportScreen.kt"

        /** 寄せ位置つきの呼び出し。値そのもの（center / nearest …）は縛らない＝実機の見えで差し替えられる。 */
        val WITH_BLOCK = Regex("""scrollIntoView\(\{\s*block\s*:[^)]*\)""")
    }
}
