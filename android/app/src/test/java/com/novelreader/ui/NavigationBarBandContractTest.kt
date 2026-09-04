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
 *  1. **読書クロームの上下バーはシステム帯を不透明で塗る。** `TopAppBar`/`BottomAppBar` の `Surface` は
 *     `windowInsets` ぶんのシステム帯（下＝ナビ／上＝ステータス）まで `containerColor` で塗るので、ここに
 *     alpha を掛けるとボタン行の下・題字の上の**無地部分だけが半透明になり本文が透ける**。2026-07-29 実機の
 *     真因は WebView 期 `html_exporter.py .nav-footer` から持ち越した `.copy(alpha = 0.95f)` だった。
 *     2026-09-04 裁定（比較モック `reading-bars-translucency-candidates.html` 案B）で**面**は α.92 で透かす
 *     ことになったが、**帯は不透明のまま**が裁定自身の要件＝守る不変条件は変わっていない。変わったのは
 *     守り方で、`containerColor` を `Color.Transparent` にして [readingChromeBarSurface] が
 *     〈面＝半透明／帯＝不透明〉の2段で塗る。だから検査も「containerColor に alpha が無い」から
 *     「**塗りが2段の形になっている**」へ移した（帯の不透明さは同関数の [chromeBarBands] 側で別途固定する）。
 *     ⚠️ `navBackground` トークン自体への alpha 掛けを一律で禁じてはいない——ヒント系の**非操作ピル**は
 *     意図して半透明に敷く（`NativeReadingScreen.kt` の `ChromeSurfaceAlpha` 群）。禁じるのは帯を塗る面だけ。
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
    // 1. 読書クロームの上下バーはシステム帯を不透明で塗る（面だけを2段塗りで透かす）
    // ────────────────────────────────────────────────────────

    /**
     * バー1本ぶんの違反理由（無ければ null）。テキストを引数に取るのは陽性確認（壊した本文を食わせる）と
     * 同じ経路を通すため。[barCall] の引数リストだけを見る＝`modifier` も `containerColor` もその中に在る。
     */
    private fun chromeBarViolation(text: String, barCall: String, bandAtTop: Boolean): String? {
        val at = text.indexOf(barCall)
        if (at < 0) return "$barCall が本文に無い＝検知器の前提（読書クロームのバー）が壊れている"
        val openParen = at + barCall.length - 1
        val closeParen = KotlinSourceScanner.matchingClose(text, openParen)
            ?: return "$barCall の引数リストで括弧の対応が取れない＝検知器の前提が壊れている"
        val args = text.substring(openParen, closeParen + 1)

        // (a) Surface 自身は塗らない。ここに地色（まして alpha 付き）を置くと帯まで塗られる。
        val containers = CONTAINER_COLOR.findAll(args).map { it.groupValues[1].trim() }.toList()
        if (containers.isEmpty()) {
            return "$barCall に containerColor 指定が無い＝M3 既定色になり、地色の所在が実装から読めなくなる"
        }
        containers.forEach { value ->
            if (!value.startsWith(TRANSPARENT)) {
                return "$barCall の containerColor が $TRANSPARENT でない（実際: $value）＝Surface が" +
                    "システム帯まで塗るため、2段塗りと二重になるか帯から本文が透ける"
            }
        }

        // (b) 2段塗りが在る＝面と帯を別々に塗っている。
        val drawAt = args.indexOf(TWO_STAGE_PAINT)
        if (drawAt < 0) {
            return "$barCall の modifier に $TWO_STAGE_PAINT が無い＝バーの地を誰も塗っていない" +
                "（containerColor は透明なので、地が消えて本文がバー全面に透ける）"
        }
        val drawOpen = drawAt + TWO_STAGE_PAINT.length - 1
        val drawClose = KotlinSourceScanner.matchingClose(args, drawOpen)
            ?: return "$TWO_STAGE_PAINT の引数リストで括弧の対応が取れない＝検知器の前提が壊れている"
        val paint = args.substring(drawOpen, drawClose + 1)

        // (c) 帯の側（上バー＝ステータス／下バー＝ナビ）を取り違えると、透ける側と塗る側が入れ替わる。
        val expectedSide = "bandAtTop = $bandAtTop"
        if (!paint.contains(expectedSide)) {
            return "$barCall の $TWO_STAGE_PAINT に「$expectedSide」が無い＝面と帯が上下逆に塗られ、" +
                "システム帯が半透明になる（透ける側と塗る側の取り違え）"
        }

        // (d) 地色そのものに alpha を焼くと、帯（α を渡さない側）まで半透明になり (b)(c) が無意味になる。
        val color = PAINT_COLOR.find(paint)?.groupValues?.get(1)?.trim()
            ?: return "$TWO_STAGE_PAINT に color 指定が無い＝検知器の前提が壊れている"
        if (color.contains("alpha")) {
            return "$TWO_STAGE_PAINT の color に alpha を焼いている（実際: $color）＝帯も半透明になり、" +
                "ボタン行の下・題字の上の無地部分から本文が透ける（2026-07-29 実機・上下バー非対称の真因）"
        }
        return null
    }

    @Test
    fun `読書クロームの下部バーは nav 帯を不透明で塗る`() {
        assertNull(chromeBarViolation(stripped(READING_SCREEN), BOTTOM_APP_BAR, bandAtTop = false))
    }

    @Test
    fun `読書クロームの上部バーはステータス帯を不透明で塗る`() {
        assertNull(chromeBarViolation(stripped(READING_SCREEN), TOP_APP_BAR, bandAtTop = true))
    }

    @Test
    fun `陽性確認 — バーの地を Surface へ戻すと落ちる`() {
        val original = stripped(READING_SCREEN)
        val broken = original.replace(
            "containerColor = Color.Transparent",
            "containerColor = colors.navBackground.copy(alpha = 0.95f)",
        )
        assertNotEquals("退行の再現に失敗＝検知器の前提（containerColor の書き方）が変わっている", original, broken)
        assertNotNull(
            "Surface へ地色を戻した形を検知器が見逃した＝この検査は死んでいる",
            chromeBarViolation(broken, BOTTOM_APP_BAR, bandAtTop = false),
        )
    }

    @Test
    fun `陽性確認 — 2段塗りを外すと落ちる`() {
        val original = stripped(READING_SCREEN)
        val broken = original.replace(TWO_STAGE_PAINT, "padding(")
        assertNotEquals("退行の再現に失敗＝検知器の前提（2段塗りの呼び名）が変わっている", original, broken)
        listOf(BOTTOM_APP_BAR to false, TOP_APP_BAR to true).forEach { (bar, atTop) ->
            assertNotNull(
                "$bar: 2段塗りを外した形を検知器が見逃した＝この検査は死んでいる",
                chromeBarViolation(broken, bar, bandAtTop = atTop),
            )
        }
    }

    @Test
    fun `陽性確認 — 帯の上下を取り違えると落ちる`() {
        val original = stripped(READING_SCREEN)
        val broken = original.replace("bandAtTop = false", "bandAtTop = true")
        assertNotEquals("退行の再現に失敗＝検知器の前提（bandAtTop の書き方）が変わっている", original, broken)
        assertNotNull(
            "帯を上下逆に塗る形を検知器が見逃した＝この検査は死んでいる",
            chromeBarViolation(broken, BOTTOM_APP_BAR, bandAtTop = false),
        )
    }

    // ── 帯そのものが不透明であることは、描画から切り出した純関数の側で固定する ──

    @Test
    fun `2段塗りは面と帯を重ねずバー全高を使い切る`() {
        val bands = chromeBarBands(totalHeight = 200f, systemBandHeight = 48f)
        assertEquals(48f, bands.bandHeight, 0f)
        assertEquals(152f, bands.faceHeight, 0f)
        // 面＋帯＝全高＝隙間も重なりも無い（重なると帯の上に半透明の面が載って結局透ける）。
        assertEquals(200f, bands.faceHeight + bands.bandHeight, 0f)
    }

    @Test
    fun `帯がバー全高を超えても面は負にならない`() {
        // Robolectric や折り畳み端末で inset がバー高を超えうる。負サイズの矩形は黙って描画されないため、
        // 丸めが無いと「面だけ消える」形で壊れる（例外は出ない＝気づけない）。
        val bands = chromeBarBands(totalHeight = 30f, systemBandHeight = 48f)
        assertEquals(30f, bands.bandHeight, 0f)
        assertEquals(0f, bands.faceHeight, 0f)
    }

    @Test
    fun `帯が無い端末では全高が面になる`() {
        val bands = chromeBarBands(totalHeight = 160f, systemBandHeight = 0f)
        assertEquals(0f, bands.bandHeight, 0f)
        assertEquals(160f, bands.faceHeight, 0f)
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
        const val TOP_APP_BAR = "TopAppBar("

        /** バーの地を〈面＝半透明／システム帯＝不透明〉の2段で塗る Modifier（ReadingChromeBarSurface.kt）。 */
        const val TWO_STAGE_PAINT = "readingChromeBarSurface("

        /** Surface 自身は塗らない＝地は2段塗りが持つ、を表す値。 */
        const val TRANSPARENT = "Color.Transparent"

        const val SKINS_DIR = "ui/skins"

        /** 目次のスキン委譲ルーター（この分岐の中で呼ばれる composable が「スキン側の目次 root」の定義）。 */
        const val SKIN_ROUTER = "when (LocalSkin.current)"

        /** ルーター分岐の中の composable 呼び出し（大文字始まり＋開き括弧）。`Skin.SEIZU_M ->` 等は括弧が無く掛からない。 */
        val CALL = Regex("""\b([A-Z][A-Za-z0-9_]*)\s*\(""")

        /** 引数リスト内の `containerColor = …` の右辺（この呼出は1行で書かれている＝行末まで取れば式1つぶん）。
         *  上バーは `containerColor` と `scrolledContainerColor` の2つを持つので findAll で全数見る
         *  （`scrolledContainerColor` も末尾一致でこの正規表現に掛かる＝どちらか片方の取りこぼしが起きない）。 */
        val CONTAINER_COLOR = Regex("""[Cc]ontainerColor\s*=\s*([^\n,]+)""")

        /** 2段塗りの `color = …` の右辺（末尾のカンマは含めない）。 */
        val PAINT_COLOR = Regex("""\bcolor\s*=\s*([^\n,]+)""")

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
