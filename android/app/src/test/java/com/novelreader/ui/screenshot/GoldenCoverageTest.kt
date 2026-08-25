package com.novelreader.ui.screenshot

import com.novelreader.sourcescan.KotlinSourceScanner
import com.novelreader.ui.theme.Skin
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * golden の**網羅と孤児**を機械強制するメタテスト（L2・監査 2026-08-06 第1部 案3 / G-7 / G-8）。
 *
 * なぜこの層が要るか: Roborazzi の verify は「撮った1枚 → 同名 golden」の**片方向**しか見ない。
 * だから (a) 撮っていない画面は最初から検査対象に入らず、文書上の免除も無いまま 0枚で放置され
 * （読書ルート・装いの間・さがす配下）、(b) 撮影テストを消しても PNG は git に残り verify は緑のまま
 * になる（孤児＝台帳の `removed-hook-leaves-dead-consumer` と同型で、13日間 dead だった実例がある）。
 * どちらも「守られているつもり」が静かに嘘になる型で、絵の比較では原理的に検出できない。
 *
 * そこで**両方向**を突合する:
 *   ルート（[com.novelreader.MainActivity] の `composable(...)`）→ 期待 golden 接頭辞 → 実 PNG → 撮影テスト → ルート
 * どの向きの欠けも、[GoldenCoverageRegistry] へ**理由付きで**登録しない限り赤にする。
 * 理由なしの除外を禁じるのは [com.novelreader.ui.discovery.DiscoveryHomeInvariantCoverageTest] と同じ流儀
 * （`acknowledgedOutOfScope` が Map で、値＝理由を必ず持つ形）。
 *
 * この層の限界: 「撮っていること」しか強制できず、**撮った絵が正しいか**は見ない（初回記録の誤りを
 * 固定する構造的限界そのものは残る）。そちらは純 Python の走査 `tools/check_golden_*.py` 3本が別軸で
 * 見る（fontScale 2.0 の罫線消失・下端クリップ・ラベルの段割れ）。両者は補い合う関係で、片方だけでは
 * 監査が挙げた 20枚の破綻は塞がらない。
 */
class GoldenCoverageTest {

    @Test
    fun `nav ルートはすべて golden を持つか理由付きで除外されている`() {
        val routes = declaredRoutes()
        assertTrue(
            "MainActivity から nav ルートを1件も抽出できなかった（抽出条件が壊れると検知器は黙って全通過する）。",
            routes.isNotEmpty(),
        )

        val known = GoldenCoverageRegistry.routeGoldens.keys + GoldenCoverageRegistry.acknowledgedOutOfScope.keys
        val unregistered = routes - known
        if (unregistered.isNotEmpty()) {
            fail(
                "新しい nav ルート ${unregistered.sorted().joinToString(" / ")} が golden 網羅に登録されていない。" +
                    "(1) 撮るなら screenshot テストを足し GoldenCoverageRegistry.routeGoldens へ" +
                    "「ルート → golden 接頭辞」を登録する。" +
                    "(2) 撮らないなら acknowledgedOutOfScope へ**理由付きで**登録する（理由なしの除外は禁止）。" +
                    "撮影条件の追加優先順は .claude/plans/golden-and-docs-audit-2026-08-06.md 第1部を参照。",
            )
        }

        val stale = known - routes
        if (stale.isNotEmpty()) {
            fail(
                "登録簿の ${stale.sorted().joinToString(" / ")} が実体を失っている（ルートの改名・削除）。" +
                    "GoldenCoverageRegistry を実体へ追随させること。放置すると" +
                    "「除外理由が付いているから承知済み」が、実在しない画面についての記述になる。",
            )
        }
    }

    @Test
    fun `golden PNG と撮影テストは全単射（孤児と未記録の双方向検出）`() {
        val goldenDir = goldenDir()
        val pngPrefixes = goldenDir.listFiles { f -> f.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.substringBefore('_') }
            .toSortedSet()
        assertTrue("golden PNG が1枚も無い（走査先=$goldenDir）。", pngPrefixes.isNotEmpty())

        val testPrefixes = capturedPrefixes()
        assertTrue(
            "screenshot テストから撮影対象の接頭辞を1件も抽出できなかった（抽出条件が壊れている）。",
            testPrefixes.isNotEmpty(),
        )

        val orphans = pngPrefixes - testPrefixes - GoldenCoverageRegistry.acknowledgedOrphanGoldens.keys
        if (orphans.isNotEmpty()) {
            fail(
                "孤児 golden: ${orphans.joinToString(" / ")} の PNG が残っているが、これを撮る" +
                    "screenshot テストが存在しない。テストの削除・クラス改名・caseId 改名の置き去りが疑われる。" +
                    "PNG を消すか、意図して残すなら GoldenCoverageRegistry.acknowledgedOrphanGoldens へ理由付きで登録する" +
                    "（verify は撮った側からしか照合しないため、この向きは誰も見ていない＝監査 G-7）。",
            )
        }

        val notRecorded = testPrefixes - pngPrefixes - GoldenCoverageRegistry.acknowledgedPendingRecord.keys
        if (notRecorded.isNotEmpty()) {
            fail(
                "未記録 golden: ${notRecorded.joinToString(" / ")} を撮る screenshot テストが在るのに PNG が無い。" +
                    "recordRoborazziDebug を打つか、記録待ちなら GoldenCoverageRegistry.acknowledgedPendingRecord へ" +
                    "理由付きで登録する。⚠️ 破綻を含む実装のまま record すると、その破綻が新しい正解として" +
                    "焼き付く（docs/knowledge/golden-record-bakes-in-regressions.md）。先に実装を直すこと。",
            )
        }

        val staleOrphanNotes = GoldenCoverageRegistry.acknowledgedOrphanGoldens.keys - pngPrefixes
        val stalePending = GoldenCoverageRegistry.acknowledgedPendingRecord.keys.intersect(pngPrefixes)
        if (staleOrphanNotes.isNotEmpty() || stalePending.isNotEmpty()) {
            fail(
                "登録簿が実体とずれている: 孤児として承知した ${staleOrphanNotes.joinToString(" / ")} は既に PNG が無く、" +
                    "記録待ちとした ${stalePending.joinToString(" / ")} は既に記録済み。登録を消して現実に合わせること。",
            )
        }
    }

    @Test
    fun `全ての golden 接頭辞は「どの面を守る束か」の説明を持つ`() {
        // なぜ孤児検査と別に要るか: 孤児検査は「撮るテストが在るか」しか見ないので、テストさえ在れば
        // **何のために撮っているか誰も言えない golden** が増え続けられる。監査が「golden 104枚の接頭辞は
        // 15種」と数え直して初めて 0枚の面が見えたように、束の一覧は人間が読める形で維持されないと
        // 投資判断（次に何を撮るか）の起点が消える。
        val goldenDir = goldenDir()
        val pngPrefixes = goldenDir.listFiles { f -> f.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.substringBefore('_') }
            .toSortedSet()

        val explained = GoldenCoverageRegistry.routeGoldens.values.flatten().toSet() +
            GoldenCoverageRegistry.componentGoldens.keys
        val unexplained = pngPrefixes - explained
        if (unexplained.isNotEmpty()) {
            fail(
                "${unexplained.joinToString(" / ")} の golden は登録簿に説明が無い。" +
                    "nav ルートに直結するなら GoldenCoverageRegistry.routeGoldens の該当ルートへ、" +
                    "部品・シート単位なら componentGoldens へ「何を守る束か」を書いて登録すること。",
            )
        }

        val staleExplanations = explained - pngPrefixes - GoldenCoverageRegistry.acknowledgedPendingRecord.keys
        if (staleExplanations.isNotEmpty()) {
            fail(
                "登録簿の ${staleExplanations.joinToString(" / ")} に対応する PNG が1枚も無い。" +
                    "撮影をやめたなら登録を消し、記録待ちなら acknowledgedPendingRecord へ移すこと" +
                    "——実体を失った説明が残ると「その面は守られている」が嘘になる。",
            )
        }
    }

    @Test
    fun `撮影済み画面は代表 case でテーマ×スケールの全数を持つ`() {
        // なぜ「代表 case が1つ」で足りるとするか: 既存テストの張り方（BookshelfD/K の KDoc に明文）が
        // 「代表状態だけ 3テーマ×2スケール全数・追加状態はライトのみ」で、色トークンは全 case 共通のため
        // 同じ束を撮り直しても新しい退行は出ない。ここで強制したいのは「テーマ退行と拡大破綻を見る束が
        // その画面に1つは在ること」であって、全 case の直積ではない。
        val goldenDir = goldenDir()
        val combos = mutableMapOf<String, MutableMap<String, MutableSet<Pair<String, String>>>>()
        goldenDir.listFiles { f -> f.name.endsWith(".png") }.orEmpty().forEach { file ->
            var parts = file.name.removeSuffix(".png").split('_')
            val scale = parts.last().takeIf { it in SCALE_LABELS }
            if (scale != null) parts = parts.dropLast(1)
            val theme = parts.last().takeIf { it in THEME_LABELS }
            if (theme != null) parts = parts.dropLast(1)
            val screen = parts.first()
            val caseId = parts.drop(1).joinToString("_").ifEmpty { "-" }
            combos.getOrPut(screen) { mutableMapOf() }
                .getOrPut(caseId) { mutableSetOf() }
                .add((theme ?: "?") to (scale ?: "?"))
        }

        val required = THEME_LABELS.flatMap { t -> SCALE_LABELS.map { s -> t to s } }.toSet()
        val incomplete = combos
            .filterValues { cases -> cases.values.none { it.containsAll(required) } }
            .keys
            .toSortedSet()

        val unexplained = incomplete - GoldenCoverageRegistry.acknowledgedPartialMatrix.keys
        if (unexplained.isNotEmpty()) {
            fail(
                "${unexplained.joinToString(" / ")} は 3テーマ×2スケールを全数持つ代表 case を1つも持たない。" +
                    "代表 case をテーマ×スケール全数で撮るか、GoldenCoverageRegistry.acknowledgedPartialMatrix へ" +
                    "理由付きで登録する。fontScale 2.0 の破綻は撮っていない条件では原理的に見えない" +
                    "（ADR 0009 増補1 が screenshot の目的に明記している軸）。",
            )
        }

        val staleNotes = GoldenCoverageRegistry.acknowledgedPartialMatrix.keys - incomplete
        if (staleNotes.isNotEmpty()) {
            fail(
                "${staleNotes.joinToString(" / ")} は既にテーマ×スケール全数を満たしているのに、" +
                    "acknowledgedPartialMatrix に「欠けている理由」が残っている。登録を消すこと" +
                    "——満たした後も免除が残ると、次に欠けたとき誰も気づかない。",
            )
        }
    }

    @Test
    fun `全スキンが golden 網羅の裁定を持つ`() {
        // スキンは exhaustive な when で全面を分岐する＝新スキンは必ず全画面を持つ。裁定（撮る/撮らない）を
        // 持たないスキンが増えると、そのスキンだけ絵の回帰が一切効かないまま出荷され得る。
        val judged = GoldenCoverageRegistry.skinCoverage.keys
        val missing = Skin.entries.filterNot { it in judged }
        assertTrue(
            "新しいスキン ${missing.joinToString()} が golden 網羅の裁定を持たない。" +
                "GoldenCoverageRegistry.skinCoverage へ「撮る接頭辞」または「撮らない理由」を登録すること。",
            missing.isEmpty(),
        )
    }

    // ---- 走査（実体の側） ----------------------------------------------------

    /** [com.novelreader.MainActivity] が宣言する nav ルート。定数参照は定数名のまま返す。 */
    private fun declaredRoutes(): Set<String> {
        val root = requireSourceRoot()
        val text = KotlinSourceScanner.stripComments(File(root, "MainActivity.kt").readText())
        // `composable("x")` / `composable(route = "x")` / `composable(TAB_HOST_ROUTE)` の3形を拾う。
        // 定数参照を名前のまま扱うのは、値まで解決しようとすると定数の置き場所に依存して壊れるため
        // （登録簿の側で同じ名前を書けば突合は成立し、改名は stale 検査が捕まえる）。
        return Regex("""composable\(\s*(?:route\s*=\s*)?(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_]*))""")
            .findAll(text)
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** screenshot テストが撮る golden 接頭辞（ファイル名の組み立て2形式から抽出）。 */
    private fun capturedPrefixes(): Set<String> {
        val testRoot = requireTestSourceRoot()
        val prefixes = sortedSetOf<String>()
        testRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ScreenshotTest.kt") }
            .forEach { file ->
                val text = KotlinSourceScanner.stripComments(file.readText())
                // 形式1: goldenName("Screen", caseId, theme, scale)。
                // raw string にしないのは、末尾がクォート4連（`"` + `"""`）になり終端が読み取りづらいため。
                Regex("goldenName\\(\\s*\"([A-Za-z0-9]+)\"").findAll(text)
                    .forEach { prefixes.add(it.groupValues[1]) }
                // 形式2: "Screen_${...}" の文字列テンプレート直書き（goldenName を通さない既存テスト）。
                // raw string で書けない（`${` がテンプレート開始として解釈される）ため通常の文字列で組む。
                Regex("\"([A-Za-z0-9]+)_\\\$\\{").findAll(text)
                    .forEach { prefixes.add(it.groupValues[1]) }
            }
        return prefixes
    }

    private fun goldenDir(): File {
        val dir = File(ScreenshotConfig.SCREENSHOT_DIR)
        if (dir.isDirectory) return dir
        // 作業ディレクトリがモジュール直下でない場合の保険（IDE 実行など）。
        // SCREENSHOT_DIR はモジュール（app/）相対なので、走査根 src/main/java/com/novelreader から
        // 5 階層（com → java → main → src → app）上がってモジュールへ戻す。
        val fallback = File(moduleDir(), ScreenshotConfig.SCREENSHOT_DIR)
        if (fallback.isDirectory) return fallback
        fail("golden ディレクトリを解決できなかった（cwd=${System.getProperty("user.dir")} / 探索先=$fallback）。")
        error("unreachable")
    }

    /** モジュール（android/app）ディレクトリ。走査根からの相対で解く。 */
    private fun moduleDir(): File = requireSourceRoot()
        .parentFile.parentFile.parentFile.parentFile.parentFile

    private fun requireSourceRoot(): File =
        KotlinSourceScanner.findModuleSourceRoot() ?: run {
            // 解決できないまま PASS すると検知器が死んでいることに気づけない（2026-07-12 の実例）。
            fail("src/main/java/com/novelreader を解決できなかった（cwd=${System.getProperty("user.dir")}）。")
            error("unreachable")
        }

    private fun requireTestSourceRoot(): File {
        val main = requireSourceRoot()
        // src/main/java/com/novelreader → src/test/java/com/novelreader（同じ深さの兄弟）。
        val srcDir = main.parentFile.parentFile.parentFile.parentFile
        val test = File(srcDir, "test/java/com/novelreader")
        if (!test.isDirectory) {
            fail("src/test/java/com/novelreader を解決できなかった（探索先=$test）。")
        }
        return test
    }

    private companion object {
        val THEME_LABELS = ScreenshotConfig.THEMES.map { ScreenshotConfig.themeLabel(it) }.toSet()
        val SCALE_LABELS = ScreenshotConfig.FONT_SCALES.map { ScreenshotConfig.scaleLabel(it) }.toSet()
    }
}

/**
 * golden 網羅の登録簿（[GoldenCoverageTest] の突合対象）。
 *
 * 除外はすべて Map の「値＝理由」を持つ。理由のない除外を許すと、この登録簿は
 * 「なぜ守っていないのか誰も説明できない穴の一覧」に退化する。
 * 状態が変わったら（撮った・ルートを消した・実装を直した）**登録を消す**こと——
 * 満たした後も免除が残ると、次に欠けたときテストは緑のまま通る。
 */
internal object GoldenCoverageRegistry {

    /** nav ルート → そのルートで撮られている golden 接頭辞。 */
    val routeGoldens: Map<String, Set<String>> = mapOf(
        // タブ層は単一ルートに3面（本棚・さがす・設定）と恒常ボトムナビが同居する（ADR 0022 スロット契約）。
        "TAB_HOST_ROUTE" to setOf(
            "BookshelfK", "BookshelfD", "DiscoveryHomeK", "SettingsScreenK", "KBottomNav",
        ),
        // さがす配下（2026-08-07 に撮影条件を新設＝監査 G-8 優先度2位の消化）。いずれも出荷素地 K で包み、
        // ルート層でなく stateless な Content 層を撮る（VM の実 API 取得・実時刻が絵に混ざるのを断つため）。
        "discovery/search" to setOf("DiscoverySearchScreen"),
        "discovery/genre" to setOf("DiscoveryGenreScreen"),
        "discovery/result" to setOf("DiscoveryResultScreen"),
        "discovery/detail/{ncode}" to setOf("NovelDetailScreen"),
    )

    /** 撮らない nav ルート → 理由（監査 2026-08-06 第1部の裁定に対応）。 */
    val acknowledgedOutOfScope: Map<String, String> = mapOf(
        "reading/{bookId}/{startFile}" to
            "監査 G-8: 読書画面**ルートとクローム**（NativeReadingScreen / ReadingChrome）が0枚。" +
                "本文組版・章見出し・設定シート・目次・エラー面・なろう紐付けシートは部品単位で撮れているが、没入 on/off を含む" +
                "ルートの絵は未撮影。撮影条件の追加優先度1位（3テーマ×2スケール×没入2値）。" +
                "既存は semantics アサーションと fling 算術のみで、KBottomNav 型の欠陥" +
                "（ノードは在り semantics も通るが画素として読めない）を原理的に検出できない。",
        "wardrobe" to
            "監査 G-8: 装いの間（WardrobeScreen）が0枚。ADR 0027 により release ではルートごと登録されない" +
                "（skinSwitchingEnabled=false）ため出荷面ではないが、debug では到達可能。" +
                "撮影条件の追加優先度5位（3枚可視カルーセル）。",
        // ⚠️ さがす配下の4ルート（search/genre/result/detail）は 2026-08-07 に撮影条件を新設して
        // routeGoldens へ移した。ここに残るのは取込面だけで、理由も「あとで撮る」から恒久除外へ変わっている。
        "discovery/detail/{ncode}/import" to
            "PdfImportScreen は画面の実体が AndroidView(WebView)＝取り込み専用の使い捨て WebView を1枚持つだけの面" +
                "（ADR 0011 案B・本番 KDoc が「stateless な Content へ切り出せない」理由も明記）。" +
                "Robolectric では WebView が外部 HTML を描画しない＝撮れる絵にアプリ側の版面がほとんど無く、" +
                "撮っても回帰検知にならないため web-reader と同じ恒久除外。当初は「実装完了後に足す（監査 F-1・" +
                "優先度2位）」と登録していたが、他の3ルートを撮る過程で WebView 主体と判明したため裁定を改めた。",
        "web-reader/{ncode}/{startEpisode}" to
            "WebView 主体の面で、Robolectric では外部 HTML が描画されない＝絵を撮っても" +
                "アプリ側の版面をほとんど含まない。ここだけは撮影しても回帰検知にならないため恒久除外。",
    )

    /**
     * ルート直結でない golden（部品・シート単位で撮っているもの）→ 何を守る束か。
     * ルート網羅の検査対象ではないが、**孤児検査で「説明のある PNG」として扱う根拠**を残すために持つ。
     */
    val componentGoldens: Map<String, String> = mapOf(
        "TocK" to "K 目次（読書ルート配下）。現在地バーと話数ラベルの整列を守る。",
        "NativeTableOfContentsScreen" to "D/C 目次（読書ルート配下）。監査 G-11: workTitle 未指定で作品名サブが未撮影。",
        "ReadingSettingsSheetContent" to "読書の表示設定シート（ModalBottomSheet の中身）。",
        "NcodeLinkSheet" to
            "なろう紐付けシート（読書ルート配下・ModalBottomSheet の中身）。候補行の題名省略と" +
                "手動 N コード欄／紐付けボタンの並び、および通信失敗時の再試行ボタンが" +
                "fontScale 2.0 で欠けないこと（監査 2026-08-06 根因④の是正箇所）を守る。",
        "SearchConditionSheet" to
            "「条件を調整」シート（さがす検索ホーム配下・ModalBottomSheet の中身）。開いた直後に見える" +
                "ジャンル節のチップ折り返しと、下端の確定／リセットまでスクロールした版面（文字数⇄読了時間の" +
                "排他注記・カスタム範囲入力）を守る。2026-08-17 に本番を枠／中身へ分けて撮影可能にした。",
        "ReadingErrorScreen" to "読書のエラー面。",
        "ContinuationCard" to "本棚の続きから読むカード。",
        "DiscoveryStatusBox" to "さがすの状態ボックス（Loading/Empty/Error の版面）。",
        "ChapterHeader" to "横書き章見出しの話数ラベル整列（最長ラベル×最大フォントの worst case）。",
        "VerticalChapterContent" to "縦書き本文＋章見出し（Canvas 直描き）。",
        "VerticalParagraph" to "縦書きの行組版（約物・縦中横・ルビ・回転）。",
        "ShioriCover" to "書影の栞（題字の記号処理）。",
        "BookshelfDialogK" to
            "本棚ルート層のダイアログ群（BookshelfScreen の AlertDialog 群・K 素地）。" +
                "電池最適化案内（本文124字＝全文言の最長）・通知 priming・なろう形式でないPDF確認・" +
                "本文欠落からのフォルダ走査、の4つの器を守る。守る軸は fontScale 2.0 での本文の器溢れ" +
                "（M3 の text スロットは自動スクロールしない）とボタン列の折り返し。" +
                "⚠️ 一括再取込・走査結果の2ダイアログは**含まない**（理由は同接頭辞の撮影テスト KDoc）。",
        "ProcessingBannerK" to
            "処理中バナー（本棚最上段・K 素地）。題名1行・phase 2行・件数バッジ・停止ボタンが" +
                "1つの Row を奪い合う構造で、拡大時に phase 末尾のページ数が落ちる方向へ壊れる。" +
                "PDF（4段ステッパーあり）／WEB（ステッパーなし）／停止中の3分岐を守る。",
        "ShelfSnackbarK" to
            "本棚の長文 Snackbar（取込元PDF削除の失敗通知・63字＋「閉じる」アクション）。" +
                "1行の器に長文とアクションが同居する版面の折り合いを守る。",
        "IntroOverlayK" to
            "教示「はじめに」のカード列（MainActivity のルートへ重なる被せもの・K 素地）。列 5 枚を" +
                "「1枚＝1 case」で撮り、①**semantics に一切出ない線画**（clearAndSetSemantics で隠した" +
                "Canvas 直描き＝絵以外に検査手段が無い）②スクリム α .74 越しの素地/墨/藍/罫の対比" +
                "③図版112dp＋本文＋項目＋点＋ボタンを1枚に積んだ版面、を守る。" +
                "⚠️ この束は **nav ルートを持たない**＝ルート網羅の検査が原理的に届かず、束の粒度で見る" +
                "孤児検査も「PNG が1枚でも在れば通る」ため、**カードを増やしても誰も赤くならない**。" +
                "列の増減は IntroOverlayGoldenCaseCoverageTest（撮影表⇄IntroDeck の全数照合）が締める。",
        // 記録待ちの2件も先に説明を置く（record された瞬間に「説明の無い golden」で赤くならないように）。
        // PNG がまだ無いことは acknowledgedPendingRecord 側が承知しているため stale 判定にも掛からない。
        "TocSkyM" to "M 目次の現在地バー（監査 G-1 の同型4スキン）。2026-08-06 に実装修正と同便で記録。",
        "TocPortalJ" to "J 目次の現在地バー（監査 G-1 の同型4スキン）。2026-08-06 に実装修正と同便で記録。",
    )

    /** 撮影テストが無いまま残す PNG → 理由。現在なし（104↔104 の全単射を維持する）。 */
    val acknowledgedOrphanGoldens: Map<String, String> = emptyMap()

    /**
     * 撮影テストが在るが PNG 未記録 → 理由。
     *
     * 現在なし。2026-08-07 に新設した7束（BookshelfDialogK / ProcessingBannerK / ShelfSnackbarK /
     * DiscoverySearchScreen / DiscoveryGenreScreen / DiscoveryResultScreen / NovelDetailScreen）は
     * **実装の破綻を先に直してから**一括記録し、この登録を外した（電池最適化ダイアログの本文溢れは
     * NovelReaderAlertDialog の text スロットを縦スクロール化して是正済み）。
     *
     * ここへ足すのは「撮影ハーネスだけ先に置き、実装修正の前で record を待つ」場合に限る
     * ——破綻を含んだまま record すると、その破綻が新しい正解として焼き付き、直したときに golden が
     * 赤くなる＝是正が退行に見える倒錯が起きる（`docs/knowledge/golden-record-bakes-in-regressions.md`）。
     *
     * ⚠️ 2026-08-26 に新設した `IntroOverlayK`（教示カード列）は**ここへ登録していない**。実装は
     * 完了済み＝「実装修正を待つ」状況ではなく、未記録の赤は *recordRoborazziDebug を打て* という
     * 指示そのものだから。登録すると record 後に「記録待ちとしたのに記録済み」で再び赤くなり、
     * 消し込みの往復が1回増えるだけになる（record すれば何も残さず緑へ戻るのが正しい経路）。
     */
    val acknowledgedPendingRecord: Map<String, String> = emptyMap()

    /** 代表 case でテーマ×スケール全数を持たない接頭辞 → 理由。 */
    val acknowledgedPartialMatrix: Map<String, String> = mapOf(
        "ChapterHeader" to
            "横書き章見出しは「最長ラベル×最大フォント＝最も折り返しやすい worst case」を狙う設計で、" +
                "light の 1.0/2.0 だけを撮る。テーマ退行は同じトークンを使う ContinuationCard 等の束が張る。",
        "ShioriCover" to
            "書影の栞は題字の記号処理（！！・（）の字形）を見る束で、fontScale を持たない Canvas 直描き。" +
                "light/dark の2テーマのみ。sepia は栞色トークンを共有するため差が出ない。",
        "VerticalParagraph" to
            "縦書き行組版は Canvas 直描き（px 直指定）で、テーマは墨色のみに効く＝light/dark で足りる。",
        "VerticalChapterContent" to
            "⚠️ 監査 G-9: このテストの KDoc が主張する「fontScale 非依存」は**虚偽**で、話数ラベルは実 Compose Text" +
                "（sp→px 換算に fontScale が効く）。2.0 と sepia を落としている現状は穴であり、" +
                "免除ではなく**未消化の宿題**として登録している。2.0×ep4digits と sepia を足したらこの登録を消すこと。",
        "TocSkyM" to
            "M は ADR 0027 で初回出荷スコープ外＝テーマ退行を張る動機が無い。それでも撮るのは監査 G-1" +
                "（目次の現在地バーが fontScale 2.0 で章一覧を押し出す）が**4スキン同型**で、M/J だけ 0枚だと" +
                "同じ穴の再発が見えないため。よって light の 1.0/2.0 に限る＝拡大破綻の軸だけを張る束。" +
                "出荷スコープに入ったらテーマ全数へ広げてこの登録を消すこと。",
        "TocPortalJ" to
            "J も同上（ADR 0027 で出荷スコープ外・監査 G-1 の同型確認だけを目的に light の 1.0/2.0）。",
    )

    /** スキン → golden の裁定（撮る接頭辞、または撮らない理由）。 */
    val skinCoverage: Map<Skin, String> = mapOf(
        Skin.MEIKAI_K to
            "撮影あり: BookshelfK / DiscoveryHomeK / SettingsScreenK / KBottomNav / TocK（既定スキン＝出荷面）。" +
                "2026-08-07 に本棚の被せもの3束を追加（BookshelfDialogK / ProcessingBannerK / ShelfSnackbarK・記録待ち）。" +
                "同日、さがす配下4束（DiscoverySearchScreen / DiscoveryGenreScreen / DiscoveryResultScreen /" +
                "NovelDetailScreen・記録待ち）も K 素地で撮る＝これらはスキン分岐を持たない共有画面だが、" +
                "出荷時に載る素地は K のため K で包む（DiscoveryResultScreen だけは LocalSkin で M/P/J へ分岐し、" +
                "K/D/C は共通実装＝この束が3スキン分の版面を張る）。" +
                "2026-08-26 に教示カード列（IntroOverlayK）を追加＝スキン分岐は持たないが LocalShelfColors を" +
                "読むため K で包む（記録待ち＝PNG は record で入る）。",
        Skin.WAMODERN_D to "撮影あり: BookshelfD / NativeTableOfContentsScreen（D/C 共通描画）。",
        Skin.YAKO_C to "撮影あり（D と共通描画・色トークンのみ差）: NativeTableOfContentsScreen の dark 束が張る。",
        Skin.SEIZU_M to
            "ADR 0027 で release 出荷対象外。監査の裁定どおり優先度は最下位だが、目次 HereBar だけは" +
                "G-1 の同型4スキンに含まれるため例外的に撮影ハーネスを用意済み（TocSkyM・記録済み）。",
        Skin.CARTRIDGE_P to
            "ADR 0027 で release 出荷対象外＝golden 0枚。ADR 0027 の判断が変わるまで撮らない。",
        Skin.PORTAL_J to
            "ADR 0027 で release 出荷対象外。目次 HereBar のみ G-1 の同型4スキンとして撮影ハーネスを用意済み" +
                "（TocPortalJ・記録済み）。",
    )
}
