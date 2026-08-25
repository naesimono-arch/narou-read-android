package com.novelreader.ui.skins.m

import com.novelreader.sourcescan.KotlinSourceScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M（星図）の ⋮ の露出条件を固定する（`c76e7bd` の回帰）。
 *
 * 守る不変条件: **⋮ の入れ物（ボタンと `DropdownMenu` のアンカー）と中身（開発節）を、同じ1つの値で駆動する。**
 * M の ⋮ に載る項目は `HighLoadSkyMenuSection`（高負荷スカイ試作トグル）ただ1つで、その露出条件は debug 限定。
 * 以前は条件が節の**内側にしか無く**、入れ物側は「中身が出るか」を知らないまま無条件に描かれていた——
 * 結果、release で ⋮ をタップすると**項目ゼロの Popup だけが開いた**。⋮ 側に別ゲートを足して症状を塞ぐと
 * 条件が2箇所へ割れて同じ食い違いを再生産するので、読み口は1つ（`highLoadSkyMenuVisible`）に保つ。
 *
 * ⚠️ なぜ振る舞いテストでなくソース走査か（`c76e7bd` 自身が KDoc に明記した制約）: 露出条件の実体は
 * `BuildConfig.DEBUG` で、**JVM の単体テストは debug の BuildConfig しか見ない**＝壊れる側（off＝release）を
 * 実行では踏めない。条件を注入可能にする改修は本番の公開シグネチャを広げる別便の話なので、
 * 「off 側で何が起きるか」を実行の代わりに**コードの形**で縛る。on 側（debug で ⋮ が出て節が載ること）は
 * 既存の `com.novelreader.ui.ShelfMenuAnchorTest` が実際にクリックして担保しており、本テストはその裏面を埋める。
 *
 * ⚠️ 各検査には**陽性確認テストを対で置いてある**（`…を戻すと落ちる`）。実ソースを in-memory で当時の壊れた形へ
 * 書き換え、検知器が確かに違反を返すことを毎回機械確認する——検知器が死んでいるのにテストは緑、という
 * 2026-07-12 の実例を繰り返さないため（本番コードを一時的に壊す手順は並列編集中のツリーでは危険なので採らない）。
 */
class HighLoadSkyMenuExposureContractTest {

    // ────────────────────────────────────────────────────────
    // 走査の土台
    // ────────────────────────────────────────────────────────

    /** 走査根が解けないときは黙って PASS させず必ず fail させる（検知器の死を緑で隠さない）。 */
    private fun root(): File = KotlinSourceScanner.findModuleSourceRoot()
        ?: throw AssertionError(
            "走査根（src/main/java/com/novelreader）が解けない＝検知器が死んでいる。" +
                "作業ディレクトリ=${System.getProperty("user.dir")}",
        )

    /** コメントを落とした本文で走査する（why を説明するコメント中の `BuildConfig.DEBUG` 等を退行と誤認しないため）。 */
    private fun stripped(relativePath: String): String =
        KotlinSourceScanner.stripComments(File(root(), relativePath).readText())

    private fun skinMSources(): List<KotlinSourceScanner.SourceFile> =
        KotlinSourceScanner.sourceFiles(root(), SKIN_M_DIR)

    // ────────────────────────────────────────────────────────
    // 1. 入れ物と中身を同じ1つの値で駆動する
    // ────────────────────────────────────────────────────────

    /**
     * [plateName] の中で「⋮ ボタン」と「⋮ メニュー本体」がどちらも `if (menuHasContent) { … }` の内側に在るか。
     * 違反なら理由・無ければ null。テキストを引数に取るのは陽性確認と同じ経路を通すため。
     */
    private fun plateExposureViolation(text: String, plateName: String): String? {
        if (!SINGLE_SOURCE.containsMatchIn(text)) {
            return "$plateName: 露出条件が `val menuHasContent = highLoadSkyMenuVisible` の1本になっていない＝" +
                "入れ物と中身が別々の条件を読む形（release の空メニューを生んだ構造）へ戻っている"
        }
        val anchor = text.indexOf(MENU_HAS_CONTENT_DECL)
        if (anchor < 0) return "$plateName: $MENU_HAS_CONTENT_DECL が見つからない＝検知器の前提が壊れている"
        val member = KotlinSourceScanner.memberAt(KotlinSourceScanner.members(text), anchor)
            ?: return "$plateName: menuHasContent を含むメンバが取れない＝検知器の前提が壊れている"
        if (member.name != plateName) {
            return "$plateName: menuHasContent の所属が ${member.name} になっている＝検知器の前提が壊れている"
        }
        val span = member.start until member.endExclusive

        // `if (menuHasContent) { … }` の各ブロックが占める範囲（＝入れ物が入っていてよい場所）。
        val gates = GATE.findAll(text)
            .filter { it.range.first in span }
            .mapNotNull { m -> KotlinSourceScanner.matchingClose(text, m.range.last)?.let { m.range.last..it } }
            .toList()
        if (gates.isEmpty()) {
            return "$plateName: `if (menuHasContent) { … }` が1つも無い＝⋮ が中身の有無と無関係に組まれている" +
                "（release で項目ゼロの Popup だけが開く・2026-08-19 の真因）"
        }

        for ((needle, label) in CONTAINERS) {
            var seen = 0
            var i = text.indexOf(needle, span.first)
            while (i in span) {
                seen++
                if (gates.none { i in it }) {
                    return "$plateName: $label（本文位置 $i）が menuHasContent のゲートの外に在る＝" +
                        "中身が出ない条件でも入れ物だけが描かれる（release で空メニューが開く・`c76e7bd` の真因）"
                }
                i = text.indexOf(needle, i + 1)
            }
            if (seen == 0) return "$plateName: $label が見つからない＝検知器の前提が壊れている"
        }
        return null
    }

    @Test
    fun `M の⋮の入れ物と中身は同じ1つの値で駆動される`() {
        val violations = PLATES.mapNotNull { (path, plate) -> plateExposureViolation(stripped(path), plate) }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `陽性確認 — ⋮ボタンを無条件に戻すと落ちる`() {
        PLATES.forEach { (path, plate) ->
            val original = stripped(path)
            // 当時の壊れた形＝入れ物のゲートだけを外す（中身は節の内側の条件で消える＝空メニューが開く）。
            val broken = original.replaceFirst(Regex("""if\s*\(\s*menuHasContent\s*\)\s*\{"""), "if (true) {")
            assertNotEquals("$path: 退行の再現に失敗＝検知器の前提（ゲートの書き方）が変わっている", original, broken)
            assertNotNull(
                "$path: ⋮ を無条件に描く形を検知器が見逃した＝この検査は死んでいる",
                plateExposureViolation(broken, plate),
            )
        }
    }

    // ────────────────────────────────────────────────────────
    // 2. 節は自己ゲートを持たない（条件が2箇所へ割れない）
    // ────────────────────────────────────────────────────────

    private fun sectionSelfGateViolation(text: String): String? {
        val at = text.indexOf(SECTION_DECL)
        if (at < 0) return "$SECTION_DECL が見つからない＝検知器の前提が壊れている"
        val paramOpen = at + SECTION_DECL.length - 1
        val paramClose = KotlinSourceScanner.matchingClose(text, paramOpen)
            ?: return "節の引数リストで括弧の対応が取れない＝検知器の前提が壊れている"
        val bodyOpen = text.indexOf('{', paramClose)
        if (bodyOpen < 0) return "節の本体 `{` が見つからない＝検知器の前提が壊れている"
        val bodyClose = KotlinSourceScanner.matchingClose(text, bodyOpen)
            ?: return "節の本体で波括弧の対応が取れない＝検知器の前提が壊れている"
        val body = text.substring(bodyOpen, bodyClose + 1)
        SELF_GATES.forEach { (needle, why) ->
            if (needle.containsMatchIn(body)) {
                return "HighLoadSkyMenuSection が自分の内側で露出条件を持っている（$why）＝" +
                    "入れ物は中身が出るかを知りようがなく、⋮ だけが描かれて空メニューが開く（`c76e7bd` で撤去した形）"
            }
        }
        return null
    }

    @Test
    fun `開発節は自己ゲートを持たない`() {
        assertNull(sectionSelfGateViolation(stripped(SECTION_FILE)))
    }

    @Test
    fun `陽性確認 — 節に自己ゲートを戻すと落ちる`() {
        val original = stripped(SECTION_FILE)
        // 当時の形＝節の先頭で自分だけ即 return する（入れ物には伝わらない）。
        // 挿入位置は本体の `{` の直後。引数の既定値に `{}` が入るため正規表現では本体の `{` を切り出せない。
        val at = original.indexOf(SECTION_DECL)
        assertTrue("退行の再現に失敗＝$SECTION_DECL が見つからない", at >= 0)
        val paramClose = KotlinSourceScanner.matchingClose(original, at + SECTION_DECL.length - 1)
        assertNotNull("退行の再現に失敗＝節の引数リストの括弧対応が取れない", paramClose)
        val bodyOpen = original.indexOf('{', paramClose!!)
        assertTrue("退行の再現に失敗＝節の本体 `{` が見つからない", bodyOpen >= 0)
        val broken = original.substring(0, bodyOpen + 1) +
            "\n    if (!BuildConfig.DEBUG) return\n" +
            original.substring(bodyOpen + 1)
        assertNotEquals("退行の再現に失敗＝検知器の前提（節の宣言の形）が変わっている", original, broken)
        assertNotNull("節の自己ゲートを検知器が見逃した＝この検査は死んでいる", sectionSelfGateViolation(broken))
    }

    // ────────────────────────────────────────────────────────
    // 3. 露出条件の読み口は1つだけ
    // ────────────────────────────────────────────────────────

    /**
     * `BuildConfig.DEBUG` の直参照が M スキン配下で [SECTION_FILE] の1箇所だけであること。
     * 2箇所目が生えた瞬間、入れ物と中身が「同じ意味の別の条件」を読む形へ戻る（＝食い違いが再発しうる）。
     */
    private fun debugFlagReadersViolation(files: List<KotlinSourceScanner.SourceFile>): String? {
        val readers = files.flatMap { file ->
            DEBUG_FLAG.findAll(file.text).map { file.relativePath }
        }
        if (readers != listOf(SECTION_FILE)) {
            return "M スキン配下の BuildConfig.DEBUG 直参照が $readers＝露出条件の読み口が1つでない" +
                "（期待: $SECTION_FILE の highLoadSkyMenuVisible ただ1箇所）"
        }
        return null
    }

    @Test
    fun `露出条件の読み口は highLoadSkyMenuVisible の1箇所だけ`() {
        val files = skinMSources()
        // 走査が壊れて 0 件になると「違反なし」で緑になるので、件数そのものを検査する。
        assertTrue("M スキンのソース走査結果が ${files.size} 件＝検知器が対象を見失っている", files.size >= 5)
        assertNull(debugFlagReadersViolation(files))
        assertTrue(
            "highLoadSkyMenuVisible が BuildConfig.DEBUG を返す形になっていない＝露出条件の実体が変わっている",
            VISIBLE_GETTER.containsMatchIn(stripped(SECTION_FILE)),
        )
    }

    @Test
    fun `陽性確認 — 露出条件の読み口を増やすと落ちる`() {
        val files = skinMSources()
        val target = files.first { it.relativePath != SECTION_FILE }
        val broken = files.map { file ->
            if (file === target) file.copy(text = file.text + "\nprivate val stray = BuildConfig.DEBUG\n") else file
        }
        assertNotNull(
            "2箇所目の BuildConfig.DEBUG を検知器が見逃した＝この検査は死んでいる",
            debugFlagReadersViolation(broken),
        )
    }

    private companion object {
        const val SKIN_M_DIR = "ui/skins/m"
        const val SECTION_FILE = "$SKIN_M_DIR/HighLoadSkyMenuSection.kt"
        const val SECTION_DECL = "fun HighLoadSkyMenuSection("
        const val MENU_HAS_CONTENT_DECL = "val menuHasContent"

        /** ⋮ を持つ M の本棚2面と、その中で ⋮ を組む private コンポーザブル（＝走査を絞る単位）。 */
        val PLATES = listOf(
            "$SKIN_M_DIR/BookshelfLogM.kt" to "LogPlate",
            "$SKIN_M_DIR/BookshelfSkyM.kt" to "SkyPlate",
        )

        /** ゲートの内側に在らねばならない「入れ物」。⋮ ボタン（アイコン）とメニュー本体の2つ。 */
        val CONTAINERS = listOf(
            "Icons.Filled.MoreVert" to "⋮ ボタン",
            "DropdownMenu(" to "⋮ メニュー本体",
        )

        val SINGLE_SOURCE = Regex("""val\s+menuHasContent\s*=\s*highLoadSkyMenuVisible""")
        val GATE = Regex("""if\s*\(\s*menuHasContent\s*\)\s*\{""")
        val DEBUG_FLAG = Regex("""BuildConfig\.DEBUG""")
        val VISIBLE_GETTER = Regex("""highLoadSkyMenuVisible\s*:\s*Boolean\s*get\(\)\s*=\s*BuildConfig\.DEBUG""")

        /** 節の内側に在ってはならない露出条件の形（撤去した2つ＝ビルド種別とスキン装着）。 */
        val SELF_GATES = listOf(
            Regex("""BuildConfig""") to "BuildConfig によるビルド種別判定",
            Regex("""LocalSkin""") to "LocalSkin によるスキン装着判定",
        )
    }
}
