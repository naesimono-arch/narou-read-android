package com.novelreader.ui

import com.novelreader.sourcescan.KotlinSourceScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ナビゲーションバー帯（画面最下端のジェスチャーバー／3ボタンが占める inset 帯）の扱いを固定する（`d3cfd99` の回帰）。
 *
 * 守る不変条件は2つ。どちらも**同じ帯を誰がどう塗る／避けるか**の話で、欠けると実機でだけ見える壊れ方をする:
 *
 *  1. **読書クロームの下部バーは nav 帯を不透明で塗る。** `BottomAppBar` の `Surface` は nav バー inset 帯まで
 *     `containerColor` で塗るので、ここに alpha を掛けるとボタン行の下の無地部分だけが半透明になり、
 *     **本文がその帯から透けて見える**（不透明な上部バーと非対称になる）。2026-07-29 実機の真因は
 *     WebView 期 `html_exporter.py .nav-footer` から持ち越した `.copy(alpha = 0.95f)` だった。
 *     ⚠️ `navBackground` トークン自体への alpha 掛けを一律で禁じてはいない——ヒント系の**非操作ピル**は
 *     意図して半透明に敷く（`NativeReadingScreen.kt` の 0.92f 群）。禁じるのは「帯を塗る面」＝バー本体だけ。
 *
 *  2. **スキン実装の目次は root で nav バー inset を処理する。** これが無いとリスト末尾／下端固定フッタが
 *     物理下端まで届き、**最終行がジェスチャーバーと重なる**（K のリスト・P の Deck で 2026-07-29 実機確認）。
 *     地色は inset の外側（`background` / `drawBehind`）で塗るので、帯まで塗られたまま内容だけが持ち上がる。
 *
 * なぜソース走査か: どちらも**視覚結果そのもの**（帯の画素の色・最終行と帯の重なり）で、semantics には出ない。
 * `containerColor` は Compose の内部状態で読み出す口が無く、nav inset は Robolectric の既定端末で 0 のため
 * 「padding が効いているか」を振る舞いで踏めない（0 を足しても 0）。守りたいのは実装の形そのものなので、
 * `ReadingWindowContractTest` の所有権テストと同じくソースの形で固定する。
 *
 * ⚠️ 各検査には**陽性確認テストを対で置いてある**（`…を戻すと落ちる`）。実ソースを in-memory で当時の壊れた形へ
 * 書き換え、検知器が確かに違反を返すことを毎回機械確認する——検知器が死んでいるのにテストは緑、という
 * 2026-07-12 の実例を繰り返さないため（本番コードを一時的に壊す手順は並列編集中のツリーでは危険なので採らない）。
 */
class NavigationBarBandContractTest {

    // ────────────────────────────────────────────────────────
    // 走査の土台
    // ────────────────────────────────────────────────────────

    /** 走査根が解けないときは黙って PASS させず必ず fail させる（検知器の死を緑で隠さない）。 */
    private fun root(): File = KotlinSourceScanner.findModuleSourceRoot()
        ?: throw AssertionError(
            "走査根（src/main/java/com/novelreader）が解けない＝検知器が死んでいる。" +
                "作業ディレクトリ=${System.getProperty("user.dir")}",
        )

    /**
     * コメントを落とした本文で走査する。why を説明するコメント中の言及（このリポジトリでは
     * 「旧 `.copy(alpha=0.95f)` は…」のように**残すべき記述**として書かれている）を退行と誤認しないため。
     */
    private fun stripped(relativePath: String): String =
        KotlinSourceScanner.stripComments(File(root(), relativePath).readText())

    // ────────────────────────────────────────────────────────
    // 1. 読書クロームの下部バーは nav 帯を不透明で塗る
    // ────────────────────────────────────────────────────────

    /** 違反なら理由・無ければ null。テキストを引数に取るのは陽性確認（壊した本文を食わせる）と同じ経路を通すため。 */
    private fun readingBottomBarViolation(text: String): String? {
        val at = text.indexOf(BOTTOM_APP_BAR)
        if (at < 0) return "$BOTTOM_APP_BAR が本文に無い＝検知器の前提（読書クロームの下部バー）が壊れている"
        val openParen = at + BOTTOM_APP_BAR.length - 1
        val closeParen = KotlinSourceScanner.matchingClose(text, openParen)
            ?: return "$BOTTOM_APP_BAR の引数リストで括弧の対応が取れない＝検知器の前提が壊れている"
        val args = text.substring(openParen, closeParen + 1)
        val value = CONTAINER_COLOR.find(args)?.groupValues?.get(1)?.trim()
            ?: return "BottomAppBar に containerColor 指定が無い＝M3 既定色になりスキンの nav 地色が失われる"
        if (!value.contains("navBackground")) {
            return "BottomAppBar の containerColor がスキントークン navBackground でない（実際: $value）"
        }
        if (value.contains("alpha")) {
            return "BottomAppBar の containerColor に alpha を掛けている（実際: $value）＝" +
                "Surface が nav バー inset 帯までこの色で塗るため、ボタン行の下の無地部分から本文が透ける" +
                "（2026-07-29 実機・上下バー非対称の真因）"
        }
        return null
    }

    @Test
    fun `読書クロームの下部バーは nav 帯を不透明で塗る`() {
        assertNull(readingBottomBarViolation(stripped(READING_SCREEN)))
    }

    @Test
    fun `陽性確認 — 下部バーに alpha を戻すと落ちる`() {
        val original = stripped(READING_SCREEN)
        val broken = original.replace(
            "containerColor = colors.navBackground",
            "containerColor = colors.navBackground.copy(alpha = 0.95f)",
        )
        assertNotEquals("退行の再現に失敗＝検知器の前提（containerColor の書き方）が変わっている", original, broken)
        assertNotNull("alpha を掛けた形を検知器が見逃した＝この検査は死んでいる", readingBottomBarViolation(broken))
    }

    // ────────────────────────────────────────────────────────
    // 2. スキン実装の目次は root で nav バー inset を処理する
    // ────────────────────────────────────────────────────────

    /**
     * 目次を自前で組むスキン実装を、**ルーター（[SHARED_TOC_SCREEN] の `when (LocalSkin.current)`）が
     * 委譲先として呼ぶ composable から逆引き**して列挙する。
     *
     * なぜファイル名（`Toc*.kt`）で列挙しないか: 当初その形で書いたところ、画面 root ではない共有ヘルパ
     * （`ui/skins/TocEpLabelWidth.kt` / `TocHereChipLabel.kt`＝ラベル幅と文言を出す純関数）まで対象に入り、
     * inset を持たない当然の状態を違反として報告した（2026-08-25 実測）。**名前は「何を組むか」を保証しない。**
     *
     * ルーター起点なら (a) 画面 root だけが入る (b) スキンを1つ足せばルーターに必ず分岐が1つ増える＝新しい面も
     * 自動で検査対象に入る (c) padding を丸ごと消しても集合から抜けない。
     * ⚠️ (c) が肝で、「ファイルの中身から対象を決める」形（例: statusBarsPadding を持つファイルだけ見る）は
     * 採らない——両方消された退行がそのまま対象外へ落ちて緑になる＝守りたい欠落そのものを見逃す。
     */
    private fun skinTocRoots(): List<KotlinSourceScanner.SourceFile> {
        val router = stripped(SHARED_TOC_SCREEN)
        val names = LinkedHashSet<String>()
        var at = router.indexOf(SKIN_ROUTER)
        while (at >= 0) {
            val brace = router.indexOf('{', at + SKIN_ROUTER.length)
            if (brace < 0) throw AssertionError("$SHARED_TOC_SCREEN のスキンルーターに `{` が無い＝検知器の前提が壊れている")
            val close = KotlinSourceScanner.matchingClose(router, brace)
                ?: throw AssertionError("$SHARED_TOC_SCREEN のスキンルーターで波括弧の対応が取れない＝検知器の前提が壊れている")
            CALL.findAll(router.substring(brace, close + 1)).forEach { names += it.groupValues[1] }
            at = router.indexOf(SKIN_ROUTER, close)
        }
        val declaredIn = KotlinSourceScanner.declarations(root(), SKINS_DIR).associateBy { it.functionName }
        val byPath = KotlinSourceScanner.sourceFiles(root(), SKINS_DIR).associateBy { it.relativePath }
        // ルーターが呼ぶもののうち ui/skins に宣言が在るものだけ＝スキン側の目次 root。
        return names.mapNotNull { declaredIn[it] }.map { decl ->
            byPath[decl.relativePath]
                ?: throw AssertionError("${decl.relativePath} の本文が取れない＝検知器の前提が壊れている")
        }
    }

    private fun skinTocNavInsetViolation(file: KotlinSourceScanner.SourceFile): String? =
        if (NAV_INSET_FORMS.any { it.containsMatchIn(file.text) }) {
            null
        } else {
            "${file.relativePath}: 目次の root が nav バー inset を処理していない＝リスト末尾／下端固定フッタが" +
                "物理下端まで届き、最終行がジェスチャーバーと重なる（2026-07-29 実機・K のリストと P の Deck）。" +
                "許容する形は「root の修飾子連鎖で status と navigation を対で処理」か systemBars の一括処理。" +
                "別の正しい形を採ったなら NAV_INSET_FORMS へ理由付きで足すこと（偽陽性は足せば消せるが、" +
                "取りこぼしは誰も気づけない、という非対称さに合わせて狭く取ってある）"
        }

    @Test
    fun `スキン実装の目次は root で nav バー inset を処理する`() {
        val files = skinTocRoots()
        // 走査が壊れて 0 件になっても「全件 PASS」で緑になるので、件数そのものを検査する（K/J/M/P の4面が下限）。
        assertTrue("スキン目次 root の逆引き結果が ${files.size} 件＝検知器が対象を見失っている", files.size >= 4)
        val violations = files.mapNotNull { skinTocNavInsetViolation(it) }
        assertEquals("走査対象=${files.map { it.relativePath }}", emptyList<String>(), violations)
    }

    @Test
    fun `陽性確認 — 目次から navigationBarsPadding を外すと落ちる`() {
        skinTocRoots().forEach { file ->
            val broken = file.copy(text = file.text.replace(".navigationBarsPadding()", ""))
            assertNotEquals(
                "${file.relativePath}: 退行の再現に失敗＝この面は別の形で inset を処理している。" +
                    "陽性確認の壊し方をその形に合わせて更新すること",
                file.text,
                broken.text,
            )
            assertNotNull(
                "${file.relativePath}: nav inset を外した形を検知器が見逃した＝この検査は死んでいる",
                skinTocNavInsetViolation(broken),
            )
        }
    }

    /**
     * D/C の目次は自前 root を持たず `Scaffold` の inset 既定処理に乗る（上の走査の対象外なので別立てで固定する）。
     * 素の `Column` などへ置き換えると、K/P が 2026-07-29 に踏んだのと同じ「最終行が帯と重なる」欠落へ落ちる。
     */
    private fun sharedTocScaffoldViolation(text: String): String? =
        if (text.contains("Scaffold(")) {
            null
        } else {
            "$SHARED_TOC_SCREEN が Scaffold で組まれていない＝D/C の目次が nav バー inset の既定処理を失い、" +
                "最終行がジェスチャーバーと重なる（K/P が踏んだのと同じ欠落クラス）"
        }

    @Test
    fun `D_C の目次は Scaffold の inset 既定処理に乗る`() {
        assertNull(sharedTocScaffoldViolation(stripped(SHARED_TOC_SCREEN)))
    }

    @Test
    fun `陽性確認 — 共有目次の Scaffold を外すと落ちる`() {
        val original = stripped(SHARED_TOC_SCREEN)
        val broken = original.replace("Scaffold(", "Column(")
        assertNotEquals("退行の再現に失敗＝検知器の前提が変わっている", original, broken)
        assertNotNull("Scaffold を外した形を検知器が見逃した＝この検査は死んでいる", sharedTocScaffoldViolation(broken))
    }

    private companion object {
        const val READING_SCREEN = "ui/NativeReadingScreen.kt"
        const val SHARED_TOC_SCREEN = "ui/NativeTableOfContentsScreen.kt"
        const val BOTTOM_APP_BAR = "BottomAppBar("
        const val SKINS_DIR = "ui/skins"

        /** 目次のスキン委譲ルーター（この分岐の中で呼ばれる composable が「スキン側の目次 root」の定義）。 */
        const val SKIN_ROUTER = "when (LocalSkin.current)"

        /** ルーター分岐の中の composable 呼び出し（大文字始まり＋開き括弧）。`Skin.SEIZU_M ->` 等は括弧が無く掛からない。 */
        val CALL = Regex("""\b([A-Z][A-Za-z0-9_]*)\s*\(""")

        /** 引数リスト内の `containerColor = …` の右辺（この呼出は1行で書かれている＝行末まで取れば式1つぶん）。 */
        val CONTAINER_COLOR = Regex("""containerColor\s*=\s*([^\n]+)""")

        /**
         * root で nav バー inset を処理していると認める形。
         * 上2つ＝現行 K/J/M/P の「status と navigation を修飾子連鎖で対にする」流儀（順序は問わない）。
         * 下2つ＝systemBars（status + navigation の和）で一括処理する等価な形。
         */
        val NAV_INSET_FORMS = listOf(
            Regex("""\.statusBarsPadding\(\)\s*\.navigationBarsPadding\(\)"""),
            Regex("""\.navigationBarsPadding\(\)\s*\.statusBarsPadding\(\)"""),
            Regex("""\.systemBarsPadding\(\)"""),
            Regex("""\.windowInsetsPadding\(\s*WindowInsets\.systemBars"""),
        )
    }
}
