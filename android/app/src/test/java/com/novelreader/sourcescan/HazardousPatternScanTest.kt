package com.novelreader.sourcescan

import com.novelreader.sourcescan.HazardousPatternRegistry.NotificationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 禁忌パターンのソース走査テスト（L2・2026-07-30）。
 *
 * なぜこの層が要るか: `docs/known-bugs-registry.md` が「検知手段なし」と判定したバグ型は、再発しても
 * **何も落ちない**。個別の回帰テストは既存実装の退行しか止められず、新しく書かれたコードが同じ罠を踏むのは
 * 誰も見ていない。そこでソースを走査して「罠を踏み得る形」を機械列挙し、[HazardousPatternRegistry] と突合する。
 *
 * 塞いでいるバグ型（`known-bugs-registry` の ID）:
 *  - `runcatching-swallows-cancellation` … [キャンセル文脈の runCatching は必ず CancellationException を再送出する]
 *  - `test-dispatcher-escape-flaky` …… [本番コードは起動系ディスパッチャを直書きしない]
 *  - `no-network-timeout` …………………… [OkHttpClient には callTimeout を設定する]
 *  - `fgs-notification-id-collision` …… [終端通知は FGS 通知と別 ID で投稿する]
 *
 * 述語の取り方（DiscoveryHomeInvariantCoverageTest から踏襲）: **取りこぼし（偽陰性）を出さないことが最優先**で、
 * 掛かりすぎ（偽陽性）は登録簿へ理由付きで足せば解消できる——という非対称さに合わせて広めに取ってある。
 *
 * 現ツリーでの偽陰性0の確認（2026-07-30 実測。件数はコメント除去後の本文に対する数）:
 *  - 型1: `runCatching {` の全42件を分類し、キャンセル文脈（suspend メンバ内 or コルーチンビルダ内）の10件が
 *    走査結果と一致した（うち3件は再送出済み・7件が登録簿）。残り32件は非 suspend 関数の中＝
 *    suspension point が無く CancellationException が生じ得ない。
 *  - 型2: `Dispatchers.` の全36出現のうち、呼び出し元が待てない起動（launch / CoroutineScope / flowOn 系）の
 *    8件が走査結果と一致（うち4件はこの導入時に注入へ直し、4件が登録簿）。残りは `withContext`
 *    （呼び出し元が構造化並行性で待つ＝TestDispatcher から逃げない）と既定引数。
 *  - 型3: `OkHttpClient(` / `OkHttpClient.Builder(` の全3件、型4: `notify(` / `startForeground(` の全6件が
 *    それぞれ走査結果と一致した（取りこぼし0）。
 *
 * 陽性確認（検査が本当に落ちること）は導入時に型ごとの違反コードを一時挿入して4型とも実測済み
 * （runCatching へ suspend 呼び出しを足す／注入を素の Dispatchers.IO へ戻す／callTimeout を外す／
 * 終端通知を FGS の ID で投稿する——4型とも期待どおりのメッセージで落ち、元へ戻して緑を確認した）。
 */
class HazardousPatternScanTest {

    // ────────────────────────────────────────────────────────
    // 走査の土台
    // ────────────────────────────────────────────────────────

    /**
     * 本番ソース全ファイル。**根が解けない・極端に少ないときは必ず落とす**——検知器が死んでいるのに
     * テストは緑、という 2026-07-12 の実例を繰り返さないため。
     */
    private fun sources(): List<KotlinSourceScanner.SourceFile> {
        val root = KotlinSourceScanner.findModuleSourceRoot()
            ?: throw AssertionError(
                "ソースツリーの根（src/main/java/com/novelreader）を解決できなかった。" +
                    "作業ディレクトリ=${System.getProperty("user.dir")}。" +
                    "テストの作業ディレクトリ設定か KotlinSourceScanner.findModuleSourceRoot の探索条件を直すこと。",
            )
        val files = KotlinSourceScanner.sourceFiles(root)
        assertTrue(
            "本番ソースが ${files.size} 件しか取れていない（走査根=$root）。走査が壊れると全ての述語が" +
                "黙って全通過するため、ここで止める。意図的にファイルを大量削除したなら下限を見直すこと。",
            files.size >= MIN_SOURCE_FILES,
        )
        return files
    }

    /**
     * 走査結果と登録簿を両方向で突合する共通手続き。
     *
     * - 未登録（走査にあって登録簿に無い）＝新しく書かれた罠を踏み得る形。真因を直すか理由付きで登録する。
     * - 陳腐化（登録簿にあって走査に無い）＝実体を失った登録。放置すると「登録済みだから検討済み」が嘘になる。
     */
    private fun crossCheck(
        label: String,
        scanned: List<KotlinSourceScanner.Occurrence>,
        registry: Map<String, String>,
        howToFix: String,
    ) {
        val ids = scanned.map { it.id }
        val duplicated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(
            "[$label] 突合キーが重複している: ${duplicated.keys.joinToString(" / ")}。" +
                "1つの登録で複数件を許してしまう＝偽陰性なので、KotlinSourceScanner.SNIPPET_LENGTH を伸ばすか" +
                "メンバを分割してキーを一意にすること。",
            duplicated.isEmpty(),
        )

        val unregistered = ids.toSortedSet() - registry.keys
        if (unregistered.isNotEmpty()) {
            fail(
                "[$label] 未登録の危険な形が ${unregistered.size} 件見つかった。\n" +
                    unregistered.joinToString("\n") { "  \"$it\" to \"（理由を書く）\"," } +
                    "\n$howToFix",
            )
        }
        val stale = registry.keys.toSortedSet() - ids.toSet()
        if (stale.isNotEmpty()) {
            fail(
                "[$label] 登録簿の ${stale.joinToString(" / ")} が実体を失っている（修正済み・リネーム・削除）。" +
                    "HazardousPatternRegistry から消して実体へ追随させること。" +
                    "放置すると『登録済みだから検討済み』が嘘になる。",
            )
        }
    }

    // ────────────────────────────────────────────────────────
    // 型1: runcatching-swallows-cancellation
    // ────────────────────────────────────────────────────────

    /**
     * `runCatching` は `Throwable` を捕まえる＝`CancellationException` まで飲む。キャンセル文脈で飲むと
     * 構造化並行性が壊れ、キャンセルしたはずの処理が生き続ける／キャンセルがエラー通知に化ける。
     *
     * 述語（広めに取る）: 走査位置が「キャンセル文脈」に在る runCatching を全て危険とみなす。
     * キャンセル文脈＝(a) 所属メンバが `suspend` 宣言 / (b) メンバ先頭から出現までの間にコルーチンビルダが在る /
     * (c) 直前の `fun` 宣言が `suspend`。3つの OR＝どれか1つでも当たれば拾う（取りこぼしより掛かりすぎを選ぶ）。
     *
     * 合格条件: その runCatching の**式の範囲**（連鎖する `.fold`/`.onFailure` を含む。結果を `val` へ受けて
     * 別文で畳む書き方も、その変数の消費連鎖まで追う）に CancellationException の再送出が在ること。
     * 範囲を式に限定するのは、同じメンバの無関係な再送出を「守られている」と誤読しないため。
     */
    @Test
    fun `キャンセル文脈の runCatching は必ず CancellationException を再送出する`() {
        val candidates = mutableListOf<KotlinSourceScanner.Occurrence>()
        val unguarded = mutableListOf<KotlinSourceScanner.Occurrence>()
        for (file in sources()) {
            val text = file.text
            val members = KotlinSourceScanner.members(text)
            val funDecls = SUSPEND_FUN_SCAN.findAll(text).toList()
            for (match in RUN_CATCHING.findAll(text)) {
                val open = match.range.last
                val close = KotlinSourceScanner.matchingClose(text, open) ?: continue
                val member = KotlinSourceScanner.memberAt(members, match.range.first)
                val memberStart = member?.start ?: 0
                val nearestFun = funDecls.lastOrNull { it.range.first < match.range.first }
                val inCancellableContext = member?.isSuspend == true ||
                    COROUTINE_BUILDER.containsMatchIn(text.substring(memberStart, match.range.first)) ||
                    nearestFun?.groupValues?.get(1)?.contains("suspend") == true
                if (!inCancellableContext) continue

                val occurrence = KotlinSourceScanner.Occurrence(
                    relativePath = file.relativePath,
                    member = member?.name ?: "<file>",
                    start = match.range.first,
                    endExclusive = KotlinSourceScanner.expressionEnd(text, close),
                    snippet = KotlinSourceScanner.snippet(text.substring(open + 1, close)),
                )
                candidates += occurrence
                if (!rethrowsCancellation(text, occurrence, member)) unguarded += occurrence
            }
        }
        assertTrue(
            "キャンセル文脈の runCatching が1件も見つからない＝述語か走査が壊れている。",
            candidates.isNotEmpty(),
        )
        crossCheck(
            label = "runcatching-swallows-cancellation",
            scanned = unguarded,
            registry = HazardousPatternRegistry.cancellationSafeRunCatching,
            howToFix = "直し方: (1) 本文が suspend しうるなら .fold/.onFailure で " +
                "`if (e is CancellationException) throw e` を先頭に置く（PdfBookImporter.addBook が手本）。" +
                "(2) 本文に suspension point が無い（＝CancellationException が生じ得ない）なら " +
                "HazardousPatternRegistry.cancellationSafeRunCatching へ**その根拠を書いて**登録する。",
        )
    }

    /** 式の範囲（結果を受けた変数の消費連鎖を含む）に CancellationException の再送出が在るか。 */
    private fun rethrowsCancellation(
        text: String,
        occurrence: KotlinSourceScanner.Occurrence,
        member: KotlinSourceScanner.Member?,
    ): Boolean {
        val spans = StringBuilder(text.substring(occurrence.start, occurrence.endExclusive))
        // `val result = runCatching { ... }` のように一旦受けてから別文で畳む書き方（PdfImportViewModel）を追う。
        // 変数名は runCatching の直前から取り、消費側の連鎖だけを範囲に足す（メンバ全体を見ると無関係な
        // 再送出まで拾って「守られている」と誤読するため）。
        val head = text.substring(maxOf(0, occurrence.start - ASSIGNMENT_LOOKBACK), occurrence.start)
        val assigned = ASSIGNMENT.find(head)?.groupValues?.get(1)
        if (assigned != null && member != null) {
            val tail = text.substring(occurrence.endExclusive, maxOf(occurrence.endExclusive, member.endExclusive))
            Regex("""\b${Regex.escape(assigned)}\s*\.""").findAll(tail).forEach { use ->
                val consumerOpen = tail.indexOfFirst2(use.range.last)
                if (consumerOpen != null) {
                    val consumerClose = KotlinSourceScanner.matchingClose(tail, consumerOpen)
                    if (consumerClose != null) {
                        spans.append('\n').append(tail, use.range.first, KotlinSourceScanner.expressionEnd(tail, consumerClose))
                    }
                }
            }
        }
        return CANCELLATION_RETHROW.containsMatchIn(spans)
    }

    /** [from] 以降で最初に現れる `(` または `{` の位置（同一行内の呼び出し開始を探す）。 */
    private fun String.indexOfFirst2(from: Int): Int? {
        var i = from
        while (i < length && this[i] != '(' && this[i] != '{' && this[i] != '\n') i++
        return if (i < length && (this[i] == '(' || this[i] == '{')) i else null
    }

    // ────────────────────────────────────────────────────────
    // 型2: test-dispatcher-escape-flaky
    // ────────────────────────────────────────────────────────

    /**
     * 本番コードが `launch(Dispatchers.IO)` のように起動系へディスパッチャを直書きすると、その起動は
     * TestDispatcher の管理外で走る＝`advanceUntilIdle` が完了を待てず、単体テストがフレーキーになる。
     *
     * 述語: 「呼び出し元が構造化並行性で待てない」起動系だけを見る＝`launch` / `CoroutineScope` /
     * `flowOn` / `shareIn` / `stateIn` の丸括弧内に `Dispatchers.` が現れる形。
     * `withContext(Dispatchers.IO)` は呼び出し元が中断して待つため対象外（これを混ぜると本番の大半が
     * 掛かって登録簿が意味を失う）。
     */
    @Test
    fun `本番コードは起動系ディスパッチャを直書きしない`() {
        val scanned = mutableListOf<KotlinSourceScanner.Occurrence>()
        for (file in sources()) {
            val text = file.text
            val members = KotlinSourceScanner.members(text)
            for (match in LAUNCHING_CALL.findAll(text)) {
                val open = match.range.last
                val close = KotlinSourceScanner.matchingClose(text, open) ?: continue
                val args = text.substring(open, close + 1)
                if (!args.contains("Dispatchers.")) continue
                scanned += KotlinSourceScanner.Occurrence(
                    relativePath = file.relativePath,
                    member = KotlinSourceScanner.memberAt(members, match.range.first)?.name ?: "<file>",
                    start = match.range.first,
                    endExclusive = close + 1,
                    snippet = KotlinSourceScanner.snippet(match.groupValues[1] + args),
                )
            }
        }
        assertTrue(
            "起動系ディスパッチャ直書きが1件も見つからない＝述語か走査が壊れている" +
                "（登録簿に許容が ${HazardousPatternRegistry.allowedHardcodedDispatchers.size} 件ある以上、0 になるはずがない）。",
            scanned.isNotEmpty(),
        )
        crossCheck(
            label = "test-dispatcher-escape-flaky",
            scanned = scanned,
            registry = HazardousPatternRegistry.allowedHardcodedDispatchers,
            howToFix = "直し方: (1) ViewModel など TestDispatcher 下で検証される層はコンストラクタで " +
                "`ioDispatcher: CoroutineDispatcher = Dispatchers.IO` を受けて注入へ移す" +
                "（BookshelfViewModel.deleteBook が手本。既定値が Dispatchers.IO なので本番の挙動は不変）。" +
                "(2) その起動を advanceUntilIdle で待つ JVM 単体テストが存在し得ないなら " +
                "HazardousPatternRegistry.allowedHardcodedDispatchers へ**その根拠を書いて**登録する。",
        )
    }

    // ────────────────────────────────────────────────────────
    // 型3: no-network-timeout
    // ────────────────────────────────────────────────────────

    /**
     * OkHttp の既定では**全体タイムアウトが無制限**。低速だが切れない接続だとリクエストが永久に完了せず、
     * 呼び出し元のコルーチンを掴んだまま画面が固まる。
     *
     * 述語: `OkHttpClient(` / `OkHttpClient.Builder(` の生成式（連鎖する `.xxx()` を最後まで含む）に
     * `.callTimeout(` が現れないもの。
     */
    @Test
    fun `OkHttpClient には callTimeout を設定する`() {
        val scanned = mutableListOf<KotlinSourceScanner.Occurrence>()
        var total = 0
        for (file in sources()) {
            val text = file.text
            val members = KotlinSourceScanner.members(text)
            for (match in OKHTTP_BUILD.findAll(text)) {
                val open = match.range.last
                val close = KotlinSourceScanner.matchingClose(text, open) ?: continue
                total++
                val end = KotlinSourceScanner.expressionEnd(text, close)
                val expression = text.substring(match.range.first, end)
                if (expression.contains(".callTimeout(")) continue
                scanned += KotlinSourceScanner.Occurrence(
                    relativePath = file.relativePath,
                    member = KotlinSourceScanner.memberAt(members, match.range.first)?.name ?: "<file>",
                    start = match.range.first,
                    endExclusive = end,
                    snippet = KotlinSourceScanner.snippet(expression),
                )
            }
        }
        assertTrue(
            "OkHttpClient の生成が1件も見つからない＝述語か走査が壊れている（本番には必ず在る）。",
            total > 0,
        )
        crossCheck(
            label = "no-network-timeout",
            scanned = scanned,
            registry = HazardousPatternRegistry.allowedMissingCallTimeout,
            howToFix = "直し方: (1) `.callTimeout(n, TimeUnit.SECONDS)` を足す（NarouNetwork が手本）。" +
                "(2) 全体時間に上限を設けないことが仕様として正しい（大容量DL 等）なら " +
                "HazardousPatternRegistry.allowedMissingCallTimeout へ**その根拠を書いて**登録する。",
        )
    }

    // ────────────────────────────────────────────────────────
    // 型4: fgs-notification-id-collision
    // ────────────────────────────────────────────────────────

    /**
     * 終端通知（完了・失敗・重複）を FGS 通知と同一 ID へ投稿すると、サービス停止の道連れで
     * **出た瞬間に消える**。ID の使い分けは実行しないと分からないので、投稿口を全数登録制にして
     * 「新しい通知を足したら役割を宣言するまで赤で止める」形で固定する。
     *
     * 検査:
     *  (1) 走査した投稿口が全て登録簿に在る（＝新設は必ず役割の宣言を伴う）。
     *  (2) 宣言した ID 式が実コードと一致する（宣言だけ直して実装が置き去り、を落とす）。
     *  (3) `startForeground` が使う ID＝FGS 通知の ID。TERMINAL 役の投稿口はそれを使ってはならない。
     *  (4) 通知 ID 定数の値が互いに異なる（別名で同じ番号＝実質同一 ID の衝突も落とす）。
     */
    @Test
    fun `終端通知は FGS 通知と別 ID で投稿する`() {
        data class Site(val id: String, val call: String, val idToken: String)

        val sites = mutableListOf<Site>()
        val constants = mutableMapOf<String, MutableList<String>>()
        for (file in sources()) {
            val text = file.text
            val members = KotlinSourceScanner.members(text)
            for (match in NOTIFICATION_POST.findAll(text)) {
                val call = match.groupValues[2]
                val open = match.range.last
                val close = KotlinSourceScanner.matchingClose(text, open) ?: continue
                val args = splitTopLevelArgs(text.substring(open + 1, close))
                // ServiceCompat.startForeground(service, id, notification, type) は第2引数、
                // Service.startForeground(id, notification) は第1引数が ID。
                // notify は末尾が Notification なのでその1つ手前が ID（tag 付き3引数版も同じ規則で当たる）。
                val idToken = when {
                    call == "startForeground" && match.groupValues[1] == "ServiceCompat" -> args.getOrNull(1)
                    call == "startForeground" -> args.getOrNull(0)
                    else -> args.getOrNull(args.size - 2)
                } ?: continue
                val member = KotlinSourceScanner.memberAt(members, match.range.first)?.name ?: "<file>"
                sites += Site("${file.relativePath}#$member::$call", call, idToken.trim())
            }
            NOTIFICATION_ID_CONST.findAll(text).forEach {
                constants.getOrPut(it.groupValues[2]) { mutableListOf() } += "${file.relativePath}:${it.groupValues[1]}"
            }
        }

        assertTrue("通知の投稿口が1件も見つからない＝述語か走査が壊れている。", sites.isNotEmpty())

        val registry = HazardousPatternRegistry.notificationSites
        val unregistered = sites.map { it.id }.toSortedSet() - registry.keys
        if (unregistered.isNotEmpty()) {
            fail(
                "[fgs-notification-id-collision] 未登録の通知投稿口が ${unregistered.size} 件見つかった:\n" +
                    unregistered.joinToString("\n") { "  \"$it\" to NotificationSite(idToken = \"?\", role = ?, why = \"?\")," } +
                    "\n直し方: HazardousPatternRegistry.notificationSites へ役割を宣言する。" +
                    "サービス停止後も残すべき終端通知なら role=TERMINAL とし、FGS 通知とは別の ID 定数を使うこと" +
                    "（同一 ID だと stopForeground/stopSelf の道連れで出た瞬間に消える）。",
            )
        }
        val stale = registry.keys.toSortedSet() - sites.map { it.id }.toSet()
        assertTrue(
            "[fgs-notification-id-collision] 登録簿の ${stale.joinToString(" / ")} が実体を失っている。" +
                "HazardousPatternRegistry.notificationSites を実体へ追随させること。",
            stale.isEmpty(),
        )

        sites.forEach { site ->
            assertEquals(
                "[fgs-notification-id-collision] ${site.id} の ID 式が登録簿の宣言とずれている" +
                    "（宣言だけ直して実装が置き去り、またはその逆）。",
                registry.getValue(site.id).idToken,
                site.idToken,
            )
        }

        val fgsIds = sites.filter { it.call == "startForeground" }.map { it.idToken }.toSet()
        assertTrue(
            "startForeground の呼び出しが見つからない＝FGS の ID を特定できず、この検査が空振りする。",
            fgsIds.isNotEmpty(),
        )
        val collisions = sites.filter {
            registry.getValue(it.id).role == NotificationRole.TERMINAL && it.idToken in fgsIds
        }
        assertTrue(
            "[fgs-notification-id-collision] 終端通知 ${collisions.joinToString(" / ") { it.id }} が" +
                "FGS 通知と同一 ID（${fgsIds.joinToString()}）で投稿している。サービス停止の道連れで" +
                "出た瞬間に消えるため、専用の ID 定数を切ること。",
            collisions.isEmpty(),
        )

        val duplicatedValues = constants.filterValues { it.size > 1 }
        assertTrue(
            "[fgs-notification-id-collision] 同じ値を持つ通知 ID 定数がある: " +
                duplicatedValues.entries.joinToString(" / ") { "${it.key}=${it.value.joinToString("+")}" } +
                "。名前が違っても値が同じなら同一の通知として上書きし合う（FGS と終端が衝突すれば同じ症状が出る）。",
            duplicatedValues.isEmpty(),
        )
    }

    /** 丸括弧内をトップレベルのカンマで分割する（入れ子の呼び出し・ラムダで切られないように）。 */
    private fun splitTopLevelArgs(inner: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (c in inner) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ',' && depth == 0) {
                args += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
        }
        args += current.toString()
        return args
    }

    private companion object {
        /** 本番ソースの下限（現況 182 件）。大幅に下回るなら走査の壊れを疑う。 */
        const val MIN_SOURCE_FILES = 120
        const val ASSIGNMENT_LOOKBACK = 160

        val RUN_CATCHING = Regex("""\brunCatching\s*\{""")
        val COROUTINE_BUILDER = Regex(
            """\b(?:launch|async|withContext|runBlocking|LaunchedEffect|produceState|""" +
                """coroutineScope|supervisorScope|flow|rememberCoroutineScope)\s*[({]""",
        )
        val SUSPEND_FUN_SCAN = Regex("""(?:^|[\n;{}])[ \t]*((?:[A-Za-z]+[ \t]+)*fun)\b""")
        val ASSIGNMENT = Regex("""\b(?:val|var)\s+([A-Za-z0-9_]+)\s*(?::[^=\n]*)?=\s*$""")
        val CANCELLATION_RETHROW =
            Regex("""is\s+(?:kotlinx\.coroutines\.)?CancellationException\s*\)?\s*(?:->\s*)?throw""")

        val LAUNCHING_CALL = Regex("""\b(launch|CoroutineScope|flowOn|shareIn|stateIn)\s*\(""")
        val OKHTTP_BUILD = Regex("""\bOkHttpClient\s*(?:\.\s*Builder\s*)?\(""")
        val NOTIFICATION_POST = Regex("""(?:([A-Za-z0-9_]+)\s*\.\s*)?\b(startForeground|notify)\s*\(""")
        val NOTIFICATION_ID_CONST =
            Regex("""\bconst\s+val\s+([A-Za-z0-9_]*NOTIFICATION_ID)\s*=\s*(\d+)""")
    }
}
