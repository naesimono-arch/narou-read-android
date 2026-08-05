package com.novelreader.domain

import com.novelreader.data.BookEntity
import com.novelreader.pdf.HtmlExporter
import com.novelreader.pdf.ProcessedChapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本文欠落→再取込提案（2026-07-29 案B＋案C、同日 案X 増補）の純ロジック検証。
 * テスト契約: 検出（実体有無）／4分岐の分類／走査可否の分類（案X）／指紋の一度だけ表示／
 * 一括復旧の対象選別／ファイル名ヒントの妥当性判定（実機実測 URI 固定）。
 * 再取込が既存行を保持する契約は repository 層のテスト（BookRepositoryTest / AddWebBookTest）が固定する。
 * フォルダ走査そのものの検証は PdfFolderScanTest。
 */
class ReimportPlanTest {

    private fun book(
        id: String = "b1",
        sourceUri: String? = null,
        sourceUrl: String? = null,
        contentSha256: String? = null,
        ncode: String? = null,
        htmlDirPath: String = "/nonexistent/$id",
    ) = BookEntity(
        id, "本$id", htmlDirPath, "著",
        sourceUri = sourceUri, sourceUrl = sourceUrl, contentSha256 = contentSha256, ncode = ncode,
    )

    // ── 検出（実体有無）: hasContent は index.html の実在を代表点にする ─────────────────

    @Test
    fun `hasContent - index_html があれば本文あり`() {
        val filesDir = createTempDir(prefix = "reimportHas")
        try {
            val dir = File(filesDir, "novels/b1").apply { mkdirs() }
            File(dir, "index.html").writeText("<html></html>")
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - ディレクトリごと消えていれば欠落（Auto Backup が DB のみ復元した形）`() {
        val filesDir = createTempDir(prefix = "reimportMissing")
        try {
            assertFalse(book("b1", htmlDirPath = File(filesDir, "novels/b1").absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - ディレクトリはあるが index が無い torn 状態も欠落扱い`() {
        val filesDir = createTempDir(prefix = "reimportTorn")
        try {
            val dir = File(filesDir, "novels/b1").apply { mkdirs() }
            File(dir, "chap_1.html").writeText("x") // 書きかけ残骸のみ
            assertFalse(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── torn 検出（2026-08-06 裁定「組み込む」）: index はあるが章ファイルが欠けた本も欠落扱い ──
    // 判別法＝2026-07-30 実機実測で検証済みの「index.html の章リンクと実ファイルの突合」。
    // fixture は手書き HTML でなく HtmlExporter の実生成物を使う＝生成契約（<li><a href="chap_N.html">）と
    // 検出正規表現が将来 silent に乖離したらここが落ちる（HtmlExporterChapterCountInvariantTest と同じ狙い）。

    /** HtmlExporter で filesDir/novels/<id> へ n 章の実生成物一式を書き出す。 */
    private fun exportRealBook(filesDir: File, id: String, n: Int, titles: (Int) -> String = { "第${it}話" }): File {
        val dir = File(filesDir, "novels/$id")
        HtmlExporter.exportToMobileHtml(
            (1..n).map { ProcessedChapter(titles(it), "本文$it") }, dir, "テスト小説",
        )
        return dir
    }

    @Test
    fun `hasContent - torn（index はあるが章ファイルが欠けた本）は欠落扱い＝真陽性`() {
        val filesDir = createTempDir(prefix = "tornPositive")
        try {
            val dir = exportRealBook(filesDir, "b1", n = 3)
            File(dir, "chap_2.html").delete() // 中間章だけ欠く（数でなく実在の突合で検出できる形）
            assertFalse(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - 正常本（全章ファイル実在）は torn 扱いにならない＝偽陽性ゼロ`() {
        val filesDir = createTempDir(prefix = "tornNegative")
        try {
            val dir = exportRealBook(filesDir, "b1", n = 3)
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - 短編（1章のみ）は torn 扱いにならない（2026-07-30 実機の疑い1冊と同形）`() {
        val filesDir = createTempDir(prefix = "tornShort")
        try {
            // 07-30 実測: torn を疑った1冊は「短編＝index の章リンク1本・chap_1 実在」で正常と判定できた。
            val dir = exportRealBook(filesDir, "b1", n = 1)
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
            // 同じ短編でも唯一の章が消えれば欠落＝章数によらず突合が対称に働く。
            File(dir, "chap_1.html").delete()
            assertFalse(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - リンクに無い余剰章ファイルは torn の証拠にしない（件数比較でなく実在照合）`() {
        val filesDir = createTempDir(prefix = "tornStray")
        try {
            // 再取込前の版の残骸などで chap が余っても、リンクされた章が全て実在するなら本文あり。
            val dir = exportRealBook(filesDir, "b1", n = 2)
            File(dir, "chap_9.html").writeText("stray")
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - 章タイトルがリンク風文字列でも誤検知しない（htmlEscape が偽装を構造的に防ぐ）`() {
        val filesDir = createTempDir(prefix = "tornEscape")
        try {
            // タイトル中の < と " は htmlEscape で実体参照になるため、本文由来の文字列が
            // 目次リンク（<li><a href="chap_N.html">）に化けて「存在しない章」を偽登録することはない。
            val dir = exportRealBook(filesDir, "b1", n = 1) { """<li><a href="chap_99.html">罠""" }
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - torn 本も buildReimportPlans の欠落地図へ載る（バッジ・バナー・走査対象への入口）`() {
        val filesDir = createTempDir(prefix = "tornPlans")
        try {
            val tornDir = exportRealBook(filesDir, "t1", n = 2)
            File(tornDir, "chap_2.html").delete()
            val okDir = exportRealBook(filesDir, "ok1", n = 2)
            val books = listOf(
                book("t1", htmlDirPath = tornDir.absolutePath, contentSha256 = "sha-t"),
                book("ok1", htmlDirPath = okDir.absolutePath),
            )
            // 検出→分類の実配線（BookshelfViewModel）と同じ isContentMissing＝!hasContent で結線して確かめる。
            val plans = buildReimportPlans(
                books,
                isContentMissing = { !it.hasContent(filesDir) },
                hasPersistedRead = { false },
            )
            // torn 本だけが欠落地図へ載り（＝バッジ・バナーの対象）、指紋があるので案X 走査対象にもなる。
            assertEquals(setOf("t1"), plans.keys)
            assertEquals("sha-t", plans.getValue("t1").scanSha256)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - 保存パスが他端末の残骸でも bookId 再導出（resolvedHtmlDir）で実体を見つける`() {
        val filesDir = createTempDir(prefix = "reimportResolve")
        try {
            // htmlDirPath は存在しない旧端末パス。実体は filesDir/novels/<id> の規約位置にある。
            val canonical = File(filesDir, "novels/b1").apply { mkdirs() }
            File(canonical, "index.html").writeText("<html></html>")
            assertTrue(book("b1", htmlDirPath = "/data/old-device/novels/b1").hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── 4分岐の分類 ──────────────────────────────────────────────────────

    @Test
    fun `classifyReimport - sourceUri あり＋権限生存＝AutoPdf（分岐①）`() {
        val plan = classifyReimport(book(sourceUri = "content://docs/a"), hasPersistedRead = { true })
        assertEquals(ReimportPlan.AutoPdf("content://docs/a"), plan)
        assertTrue(plan.isAuto)
        // ①は取込元から直接戻せる＝走査対象に混ぜない（混ぜると二重取込になる）。
        assertNull(plan.scanSha256)
    }

    @Test
    fun `classifyReimport - sourceUri あり＋権限失効＝PickPdfPermissionLost（分岐②・指紋を運ぶ）`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fkuro.pdf"
        val plan = classifyReimport(book(sourceUri = uri, contentSha256 = "abc"), hasPersistedRead = { false })
        assertEquals(ReimportPlan.PickPdfPermissionLost("kuro.pdf", "abc"), plan)
        assertFalse(plan.isAuto)
        assertEquals("abc", plan.scanSha256)
    }

    @Test
    fun `classifyReimport - sourceUri NULL＝PickPdfNoRecord（分岐③・指紋があれば走査で救える）`() {
        val plan = classifyReimport(book(contentSha256 = "def"), hasPersistedRead = { true })
        assertEquals(ReimportPlan.PickPdfNoRecord("def"), plan)
        assertFalse(plan.isAuto)
        assertEquals("def", plan.scanSha256)
    }

    @Test
    fun `classifyReimport - 指紋 NULL の旧取込は走査対象にならない（黙って落とさず null で表す）`() {
        val lost = classifyReimport(
            book(sourceUri = "content://docs/a", contentSha256 = null), hasPersistedRead = { false },
        )
        val noRecord = classifyReimport(book(contentSha256 = null), hasPersistedRead = { true })
        assertNull(lost.scanSha256)
        assertNull(noRecord.scanSha256)
    }

    @Test
    fun `classifyReimport - sourceUrl あり＝AutoWeb（分岐④・権限判定より優先）`() {
        val plan = classifyReimport(
            book(sourceUrl = "https://example.com/works/1"),
            hasPersistedRead = { error("Web 本では権限照会自体を呼ばない") },
        )
        assertEquals(ReimportPlan.AutoWeb("https://example.com/works/1"), plan)
        assertTrue(plan.isAuto)
        assertNull(plan.scanSha256)
    }

    // ── ①' AutoCachePdf（なろう取込の cache PDF 直接再変換・2026-08-05）──────────────────
    // 対象＝sourceUri/sourceUrl 両 NULL・ncode あり（実機実測: なろう縦書きPDF取込の本はこの形）。
    // ③PickPdfNoRecord へ落とすと SAF から辿れない PDF を「探しますか？」と提案する嘘になるため、
    // cache 現存時はその前で拾う（削除警告が約束する「カードから再取込で戻せます」を実行可能に保つ）。

    @Test
    fun `classifyReimport - ncode あり＋cache 現存＝AutoCachePdf（分岐①'・③より優先）`() {
        val plan = classifyReimport(
            book(contentSha256 = "abc", ncode = "n1453lw"),
            hasPersistedRead = { error("sourceUri が無い本では権限照会を呼ばない") },
            cachedNarouPdfPath = { ncode -> "/cache/pdf_import/$ncode.pdf" },
        )
        assertEquals(ReimportPlan.AutoCachePdf("/cache/pdf_import/n1453lw.pdf", "n1453lw"), plan)
        assertTrue(plan.isAuto)
        // ①'は cache から直接戻せる＝フォルダ走査に混ぜない（混ぜると同じ本へ二重投入になる）。
        assertNull(plan.scanSha256)
    }

    @Test
    fun `classifyReimport - ncode ありでも cache 不在なら従来どおり PickPdfNoRecord（正直に落ちる）`() {
        // OS が逼迫時に cache を消した後の形。嘘の自動提案をせず、指紋があれば走査で救う③へ。
        val plan = classifyReimport(
            book(contentSha256 = "abc", ncode = "n1453lw"),
            hasPersistedRead = { true },
            cachedNarouPdfPath = { null },
        )
        assertEquals(ReimportPlan.PickPdfNoRecord("abc"), plan)
    }

    @Test
    fun `classifyReimport - ncode NULL の本では cache 照会自体を呼ばない`() {
        val plan = classifyReimport(
            book(contentSha256 = "abc"),
            hasPersistedRead = { true },
            cachedNarouPdfPath = { error("ncode が無い本で cache を探しに行かない") },
        )
        assertEquals(ReimportPlan.PickPdfNoRecord("abc"), plan)
    }

    @Test
    fun `classifyReimport - sourceUrl があれば cache より AutoWeb 優先（Web 再取得の方が確実）`() {
        val plan = classifyReimport(
            book(sourceUrl = "https://example.com/works/1", ncode = "n1453lw"),
            hasPersistedRead = { true },
            cachedNarouPdfPath = { "/cache/pdf_import/n1453lw.pdf" },
        )
        assertEquals(ReimportPlan.AutoWeb("https://example.com/works/1"), plan)
    }

    @Test
    fun `classifyReimport - sourceUri がある本は cache を見ない（①'は sourceUri NULL 限定のスコープ）`() {
        // 権限失効した SAF 取込本が偶然同じ ncode を紐付けていても、②の導線（元ファイルの選び直し／
        // フォルダ走査）を cache が横取りしない＝①'の対象は「取込元の記録を一切持たない本」だけ。
        val plan = classifyReimport(
            book(sourceUri = "content://docs/a", contentSha256 = "abc", ncode = "n1453lw"),
            hasPersistedRead = { false },
            cachedNarouPdfPath = { error("sourceUri を持つ本で cache を探しに行かない") },
        )
        assertTrue(plan is ReimportPlan.PickPdfPermissionLost)
    }

    @Test
    fun `buildReimportPlans - 欠落本だけが地図に載る`() {
        val missing = book("m1", sourceUri = "content://docs/a")
        val intact = book("ok1", sourceUri = "content://docs/b")
        val plans = buildReimportPlans(
            listOf(missing, intact),
            isContentMissing = { it.id == "m1" },
            hasPersistedRead = { true },
        )
        assertEquals(setOf("m1"), plans.keys)
        assertEquals(ReimportPlan.AutoPdf("content://docs/a"), plans["m1"])
    }

    // ── ファイル名ヒント（分岐②の表示と走査候補の優先順位付け）────────────────────────

    @Test
    fun `sourceFileNameHint - SAF 授権ID（primary区切り）からファイル名を復元する`() {
        assertEquals(
            "黒の魔王.pdf",
            sourceFileNameHint(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2F%E9%BB%92%E3%81%AE%E9%AD%94%E7%8E%8B.pdf",
            ),
        )
    }

    @Test
    fun `sourceFileNameHint - raw パス形式（Downloads プロバイダ）からも末尾ファイル名を切り出す`() {
        assertEquals(
            "foo.pdf",
            sourceFileNameHint(
                "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Ffoo.pdf",
            ),
        )
    }

    @Test
    fun `sourceFileNameHint - MediaStore Documents の内部IDはファイル名でない（実機実測・誤表示の回帰固定）`() {
        // 2026-07-29 実機の蔵書7冊が全てこの形式。旧実装は "1000027648" を切り出し、ダイアログに
        // 『取込元の PDF: 1000027648』と表示していた＝手がかりとして無価値かつファイル名だと誤認させる。
        assertNull(
            sourceFileNameHint(
                "content://com.android.providers.media.documents/document/document%3A1000027648",
            ),
        )
    }

    @Test
    fun `sourceFileNameHint - 拡張子を持たない不透明IDは一律に採用しない（UUID 形式も同断）`() {
        assertNull(sourceFileNameHint("content://com.example.provider/document/9f8c1b7e-4a21-4f00-9b3e-0d1f2a3b4c5d"))
        assertNull(sourceFileNameHint("content://docs/"))
        assertNull(sourceFileNameHint("content://docs/abc"))
    }

    // ── 指紋の一度だけ表示（案C・「新規に検出した際に一度だけ」）───────────────────────

    @Test
    fun `sweep指紋 - 初検出は表示・提示後の同一集合は再表示しない・新たな欠落で再表示する`() {
        // 初検出（seen 空）＝表示。
        assertTrue(shouldShowReimportSweep(setOf("a", "b"), emptySet()))
        // 「あとで」/実行で seen={a,b} を保存 → 同一集合では出さない。
        val seen = setOf("a", "b")
        assertFalse(shouldShowReimportSweep(setOf("a", "b"), seen))
        // 一部復旧で集合が縮んでも（b だけ残存）出さない＝縮小は新規検出ではない。
        assertFalse(shouldShowReimportSweep(setOf("b"), seen))
        // 新たな欠落 c が増えたら再表示。
        assertTrue(shouldShowReimportSweep(setOf("b", "c"), seen))
        // 欠落ゼロなら当然出さない。
        assertFalse(shouldShowReimportSweep(emptySet(), seen))
    }

    @Test
    fun `sweep指紋 - prune で復旧済み本を seen から外す＝再欠落は新規検出として拾う`() {
        val seen = setOf("a", "b")
        // a が復旧して欠落は {b} → seen も {b} へ刈り込む。
        val pruned = pruneReimportSeenIds(seen, missingIds = setOf("b"))
        assertEquals(setOf("b"), pruned)
        // その後 a が再び欠落＝seen に無い＝バナーが出る（黙殺しない）。
        assertTrue(shouldShowReimportSweep(setOf("a", "b"), pruned))
    }

    @Test
    fun `sweep指紋 - 1冊も動かせなかった実行では消費しない（実機で確認された欠陥の回帰固定）`() {
        // 初版の欠陥: runSweepReimport が実行の頭で無条件に指紋を保存していたため、自動対象0冊
        // （＝uninstall 後の実機構成そのもの）では「実行→何も起きない→バナーが二度と出ない」になった。
        assertFalse(shouldConsumeSweepBanner(autoSubmitted = 0, scanMatched = 0))
        // 走査で1冊でも当たれば消費してよい（ユーザーの操作が実を結んだ）。
        assertTrue(shouldConsumeSweepBanner(autoSubmitted = 0, scanMatched = 1))
        // 自動分だけ投入できた場合も同様（走査は0冊でも操作は前進している）。
        assertTrue(shouldConsumeSweepBanner(autoSubmitted = 2, scanMatched = 0))
    }

    // ── 一括復旧の対象選別（自動＋走査／人が選ぶしかない分の分離）──────────────────────

    @Test
    fun `reimportBreakdown - 復旧経路で3群に割れる（自動・走査・人が選ぶ）`() {
        val plans = listOf(
            ReimportPlan.AutoPdf("content://docs/1"),
            ReimportPlan.AutoPdf("content://docs/2"),
            ReimportPlan.AutoWeb("https://example.com/w/1"),
            ReimportPlan.AutoCachePdf("/cache/pdf_import/n1.pdf", "n1"), // ①'＝なろう取込の cache 実体
            ReimportPlan.PickPdfPermissionLost("a.pdf", "sha-a"),
            ReimportPlan.PickPdfNoRecord("sha-b"),
            ReimportPlan.PickPdfNoRecord(null), // v11 前＝走査で救えない唯一の系統
        )
        val b = reimportBreakdown(plans)
        assertEquals(2, b.autoPdf)
        assertEquals(1, b.autoWeb)
        assertEquals(1, b.autoCachePdf)
        assertEquals(1, b.pickPermissionLost)
        assertEquals(2, b.pickNoRecord)
        assertEquals(7, b.total)
        assertEquals(4, b.autoTotal)        // ①2冊＋①'1冊＋④1冊＝記録・cache 実体だけで戻せる
        assertEquals(3, b.manualTotal)      // ②1冊＋③2冊
        assertEquals(2, b.scannable)        // うち指紋あり＝フォルダ走査で戻せる
        assertEquals(1, b.unscannable)      // 指紋なし＝人が1冊ずつ選ぶしかない
        // 不変条件: ②③は必ず走査可否のどちらかに入る（黙って消える本が出ない）。
        assertEquals(b.manualTotal, b.scannable + b.unscannable)
        assertEquals(6, b.recoverableTotal) // ワンアクションで戻る見込み＝自動＋走査
        // 実行対象の選別そのもの（VM submitAutoReimports の filterIsInstance と同じ規則）。
        assertEquals(4, plans.count { it.isAuto })
    }

    @Test
    fun `reimportBreakdown - 実機構成（全冊が権限失効＋指紋あり）では自動0冊・走査で全冊`() {
        // 2026-07-29 実機実測: 蔵書は全冊 PDF 由来・contentSha256 あり・uninstall で永続権限は消える。
        // 旧設計（①④だけを一括対象）ではこの構成で autoTotal=0＝「まとめて再取込」が常に無効だった。
        val plans = List(4) { ReimportPlan.PickPdfPermissionLost(null, "sha-$it") }
        val b = reimportBreakdown(plans)
        assertEquals(0, b.autoTotal)
        assertEquals(4, b.scannable)
        assertEquals(0, b.unscannable)
        assertEquals(4, b.recoverableTotal)
    }

    // ── 状態行文言（案B・棚面に4種の語彙を発明しない）───────────────────────────

    @Test
    fun `reimportStatusLabel - PDF系は同文・Webだけ再取得の文言`() {
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.AutoPdf("u")))
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.AutoCachePdf("/c/n1.pdf", "n1")))
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.PickPdfPermissionLost(null, null)))
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.PickPdfNoRecord(null)))
        assertEquals("Web作品・再取得できます", reimportStatusLabel(ReimportPlan.AutoWeb("u")))
    }

    // ── 欠落本の削除＝復元の最後の機会を消す警告（2026-07-29 実害への対処）──────────────

    @Test
    fun `countMissingContentTargets - 欠落判定は復旧導線と同じ plans への所属だけで決まる`() {
        // plans は buildReimportPlans の結果＝「本文欠落と判定された本」だけが載る（棚バッジと同一の根拠）。
        val plans = mapOf<String, ReimportPlan>(
            "b1" to ReimportPlan.PickPdfPermissionLost(null, "sha1"),
            "b3" to ReimportPlan.AutoWeb("https://example.com/works/1"),
        )
        assertEquals(0, countMissingContentTargets(emptyList(), plans))
        assertEquals(0, countMissingContentTargets(listOf("b2"), plans))          // 健在の本のみ
        assertEquals(1, countMissingContentTargets(listOf("b1", "b2"), plans))    // 混在
        assertEquals(2, countMissingContentTargets(listOf("b1", "b3"), plans))    // 全部欠落
        // 分岐の種類では変わらない（①〜④のどれでも「行を消せば鍵が消える」ことは同じ）。
        assertEquals(1, countMissingContentTargets(listOf("b3"), plans))
    }

    @Test
    fun `missingContentDeleteWarning - 欠落0冊なら null＝通常の削除は文言も操作も変えない`() {
        // 不変条件: この null が「通常の本の削除体験を変えない」ことの単一の保証点
        //（UI 側は null で警告ブロックを描かず、確定ボタンも従来の「削除する」に戻る）。
        assertNull(missingContentDeleteWarning(missingCount = 0, bookCount = 3))
        assertNull(missingContentDeleteWarning(missingCount = 0, bookCount = 0))
        assertEquals("削除する", deleteConfirmLabel(false))
    }

    @Test
    fun `missingContentDeleteWarning - 失うものを具体名で言い代替手段を必ず添える`() {
        val w = missingContentDeleteWarning(missingCount = 1, bookCount = 1)!!
        // 実害（読書位置・追加日の喪失）を名指しする＝再取込ダイアログ群の「残ります」の対語。
        assertTrue("失うものが具体名で出ていない", w.detail.contains("読書位置・しおり・追加日"))
        // 警告だけでは同じ結末を防げない＝「まだ戻せる」手段を必ず示す（実害の本質は無知であって不注意でない）。
        assertTrue("代替手段（再取込）が示されていない", w.detail.contains("再取込"))
        // 強調部は棚バッジと同じ語「本文なし」で、失効するのは復元手段であることを名指しする。
        assertTrue(w.emphasis.contains("本文なし"))
        assertTrue(w.emphasis.contains("復元できなくなります"))
    }

    @Test
    fun `missingContentDeleteWarning - 対象の言い方は単数・全部欠落・混在で切り替わる`() {
        // 選択1件の削除（実装上の「単数削除」＝専用配線は無く選択1件で同じダイアログ）は「1冊」と数えない。
        assertTrue(missingContentDeleteWarning(1, 1)!!.detail.startsWith("この本は"))
        // 全部が欠落＝選択そのものを指す。
        assertTrue(missingContentDeleteWarning(3, 3)!!.detail.startsWith("選択した3冊は"))
        // 混在＝選択の一部であることを明示（健在の本まで戻せないと誤解させない）。
        assertTrue(missingContentDeleteWarning(1, 3)!!.detail.startsWith("選択のうち1冊は"))
    }

    @Test
    fun `deleteConfirmLabel - 欠落を含むときだけ確定ボタンが何を捨てるかを名乗る`() {
        // 本文コピーは読み飛ばされうる＝押す直前の最後の一語でも取り返しのつかなさが分かるようにする。
        assertEquals("復元せずに削除する", deleteConfirmLabel(true))
        assertEquals("削除する", deleteConfirmLabel(false))
    }
}
