package com.novelreader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 恒常タブ（本棚／さがす／設定）の**横スワイプ**と、そこから読書画面へ**遷移（push/pop）**したときの
 * frame timing（jank）計測。handover「遷移 jank・残④」＝計測ラウンドで見つけた重さを回帰として固定する枠。
 *
 * なぜ本棚スクロール（[BookshelfScrollBenchmark]）・章送り（[ChapterFlipBenchmark]）と別ベンチか:
 * この2つが測るのは「1つの面の中の連続スクロール」で、タブ切替の重さ——**ページ実体化**（隣ページの
 * 初回コンポーズ）と**遷移アニメ窓**（NavHost の push/pop 中に重い Lazy コンテナが同居する）——は
 * どちらの窓にも入らない。実測でも本棚グリッド 3.82% に対しページ実体化を伴う横スワイプは 11.89% と
 * 桁が違い（2026-07-30 ベースライン）、対処（`TabPagerHost` の隣ページ常駐と `deferNeighborPages`／
 * 本棚スケルトンの `deferHeavyContent`）もこの窓専用に入っている。**その対処が外れたことを検知する絵が
 * これまで一枚も無かった**＝ここがその枠。
 *
 * 2テストの役割分担:
 *  - [swipeTabs]              タブ横スワイプだけ（ページ実体化の窓）。
 *  - [swipeTabsWithTransition] 同じ12スワイプの末尾に読書画面への push/pop（本文→目次→本棚の実 pop 経路）を
 *    **1回**足す（遷移アニメ窓との同居。往復ごとに開閉しない理由は measureBlock 末尾のコメント参照）。
 *    両者の差分が「遷移窓が上乗せする分」＝`deferNeighborPages` / `deferHeavyContent` の効きに対応する。
 *
 * 予算 assert は 2026-08-06 に実機較正済み（[TabSwipeBudget] の既定定数と由来コメント参照。
 * [ScrollBudget] / [FlipBudget] が辿った「まず実測→由来付きで定数化」の順序と同じ）。
 *
 * COLD 性・シード配達・前面ガード・注入方式の各作法は [BookshelfScrollBenchmark] / [ChapterFlipBenchmark]
 * と同一の根拠（ColorOS の broadcast 沈黙不達／COLD の force-stop 仕様／launcher も scrollable を持つ／
 * shell `input swipe` の実証）に基づく。機序の詳細は両クラスの KDoc を参照。
 */
@RunWith(AndroidJUnit4::class)
class TabSwipeBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** タブ横スワイプのみ（本棚→さがす→設定→さがす→本棚 を [ROUND_TRIPS] 往復）。 */
    @Test
    fun swipeTabs() = measureTabSwipe("swipeTabs", withTransition = false)

    /** タブ横スワイプ＋読書画面への push/pop（遷移アニメ窓と隣ページ実体化の同居を測る）。 */
    @Test
    fun swipeTabsWithTransition() = measureTabSwipe("swipeTabsWithTransition", withTransition = true)

    private fun measureTabSwipe(testName: String, withTransition: Boolean) {
        // 採用する JSON がこの走行で書き出されたものかを lastModified で検証するために使う
        // （残骸 JSON による偽判定防止＝[TabSwipeBudget] 参照）。
        val startedAtEpochMs = System.currentTimeMillis()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            // startupMode=COLD は使わない（setupBlock の後に force-stop される仕様で、着地済みの面が殺される）。
            // 反復間のコールド性は setupBlock 冒頭の killProcess() で自前確保する
            // ＝docs/knowledge/macrobenchmark-frametiming-scroll-pitfalls.md §1。
            startupMode = null,
            // compilationMode は既定（未指定）＝CompilationMode.DEFAULT。
            setupBlock = {
                // (1) 蔵書を投入する。2テストで同一シード（100冊＋実HTML 50章の1冊）にするのは、
                //     「遷移あり／なし」の差分がそのまま遷移窓の上乗せ分になるようにするため
                //     （データが違うと2本の数字が比較できなくなる）。
                seedLibrary()

                // (2) コールド起動して前面ガード。launcher 自身も scrollable を持つため scrollable 待ちでは
                //     未起動を検知できない＝By.pkg で対象アプリの前面化を必ず検証する。
                killProcess()
                pressHome()
                startActivityAndWait()
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }

                // (3) 本棚（page 0）への着地を確認してから測る。着地前に注入すると1反復目だけ
                //     ページ実体化の窓を外して測ることになり、反復間で意味の違う数字が混ざる。
                if (!device.wait(Until.hasObject(SHELF_MARKER), 10_000)) {
                    fail("本棚（page 0）に着地しなかった＝タブスワイプの起点が確定していない")
                }
            }
        ) {
            repeat(ROUND_TRIPS) {
                // 本棚 → さがす → 設定 → さがす → 本棚（往復1回＝4スワイプ）。
                swipeToNextTab(); awaitTab(from = SHELF_MARKER, to = DISCOVER_MARKER, label = "さがす")
                swipeToNextTab(); awaitTab(from = DISCOVER_MARKER, to = SETTINGS_MARKER, label = "設定")
                swipeToPrevTab(); awaitTab(from = SETTINGS_MARKER, to = DISCOVER_MARKER, label = "さがす")
                swipeToPrevTab(); awaitTab(from = DISCOVER_MARKER, to = SHELF_MARKER, label = "本棚")
            }
            // 開閉（push/pop）は iteration 末尾に**1回だけ**行う（2026-08-06 実機切り分けで旧設計
            // 「往復ごとに開閉」から変更）: 本を一度読むと本棚が並び替わり（読書状態が付くと未読99冊の
            // 下へ沈む＝実機 dump で復帰後の可視域から消えることを確認）、同一 iteration 内の2回目の
            // find が viewport 外で必ず空振りする。フィルタ操作やスクロールで探しに行くと、その操作の
            // フレームが計測に混ざり swipeTabs との差分設計が崩れる。開閉を末尾1回に絞れば、find は
            // 常に「未読状態の本棚」（シード直後の並び＝先頭行に居る）に対して行われ決定的に成立する。
            // 並びは setupBlock の reseed が毎 iteration リセットするため、iteration 間の汚染も無い。
            if (withTransition) openBookAndReturn()
        }

        if (TabSwipeBudget.isBudgetAssertEnabled()) {
            TabSwipeBudget.assertTabSwipeWithinBudget(testName, startedAtEpochMs)
        }
    }

    /**
     * 本棚から計測用の書を開き（NavHost push）、Back で本棚へ戻す（pop）。
     *
     * 対象を「章送り計測の書」にする理由: 実 HTML 50章＋progress=chap_1 でシードされる唯一の本で、
     * **目次でなく本文へ直接着地する**ことが確定契約（[ChapterFlipBenchmark] が実機で確立）。
     * ダミー本（bench_seed_N）は本文実体を持たず、開いても遷移の中身が実アプリと別物になる。
     */
    private fun MacrobenchmarkScope.openBookAndReturn() {
        // タブ settle アニメの完了を固定マージンで跨いでから click する（2026-08-06 実機切り分け）:
        // 直前の awaitTab は「移動元の gone＋移動先の出現」＝コミットまでしか保証せず、ページャの
        // settle アニメは まだ走っている。settle 中のタップはページャがアニメ停止に消費して子セルへ
        // 届かない（実機再現: スワイプ直後の即タップは本が開かず本棚に残る／600ms 待てば 開く）。
        // この窓は a11y から観測不能（settle 中も徴は出現済み・waitForIdle も Compose アニメを busy と
        // 見なさない＝ChapterFlipBenchmark の 400ms マージンと同根）ため固定 600ms で跨ぐ。
        // 計測への影響: 待機中は静止＝フレームが出ないため FrameTiming の分位には乗らない。
        Thread.sleep(600)
        // 題名ノードの出方はスキンで違う: 明快K のグリッドはキャプション行に題名を Text で出すが、
        // 和モダンD は題名を栞書影へ Canvas 描画するため text ノードを持たず contentDescription にだけ出る。
        // どちらでも掴めるよう text→desc の順で探す（スキン既定が変わっても静かに空振りしない）。
        val book = device.wait(Until.findObject(By.text(MEASURE_BOOK_TITLE)), 5_000)
            ?: device.wait(Until.findObject(By.desc(MEASURE_BOOK_TITLE)), 5_000)
        if (book == null) {
            fail("本棚に『$MEASURE_BOOK_TITLE』が現れなかった（シード契約違反 or 本棚未表示の疑い）")
        }
        book!!.click()
        if (!device.wait(Until.hasObject(By.textStartsWith("第1章")), 10_000)) {
            fail("chap_1 本文（第1章）に着地しなかった（目次着地＝progress リセット未反映の疑い）")
        }
        // pop は2段（2026-08-06 実機確定）: progress 付きで本を開くと NavHost のスタックは
        // 本棚→目次→本文 と積まれ、本文からの Back は**目次画面**に着地する（「本棚に戻る」ボタンと
        // 「目次」題字を実機 dump で確認）。旧実装の「Back 1回→本棚の徴を待つ」は必ず 10s 待って
        // fail する設計バグだった。目次経由の2段 pop はアプリの実遷移そのもの＝遷移窓の計測対象として
        // むしろ正しい。各段で着地の徴を確認し、崩れたら黙って計測を続けない。
        device.pressBack()
        if (!device.wait(Until.hasObject(TOC_MARKER), 10_000)) {
            fail("本文からの Back で目次に着地しなかった（ナビ設計の変更疑い＝pop 段数の再確認が要る）")
        }
        device.pressBack()
        if (!device.wait(Until.hasObject(SHELF_MARKER), 10_000)) {
            fail("目次からの Back で本棚へ戻らなかった（pop 先が本棚でない疑い）")
        }
    }

    /**
     * タブ切替の「コミット」を待つ。
     *
     * なぜ移動先の徴の出現だけでは足りないか（[ChapterFlipBenchmark] が実機で確立した判定則の横展開）:
     * :app の TabPagerHost は `beyondViewportPageCount = 1`＝**隣ページを常駐**させるため、
     * 移動先ページはスワイプ前から合成済みで、その徴はツリーに居うる＝出現は settle の証拠にならない。
     * 一方、離脱したページは画面外へ出た時点で a11y から外れるため「移動元の徴の gone」がコミットと 1:1 に
     * 対応する。gone を先に待ち、そのうえで移動先の徴を確認する（別タブへ飛んでいないことの検証）。
     */
    private fun MacrobenchmarkScope.awaitTab(from: BySelector, to: BySelector, label: String) {
        if (!device.wait(Until.gone(from), 5_000)) {
            fail("タブ切替がコミットしなかった（$label へのスワイプ後も移動元の徴が残存）。" +
                "内側の横スワイプ要素にジェスチャを奪われた可能性＝スワイプ位置 y の較正を疑うこと")
        }
        if (!device.wait(Until.hasObject(to), 5_000)) {
            fail("$label タブの徴が現れていない（別タブへ飛んだ疑い）")
        }
    }

    /** 次のタブへ（右→左の横スワイプ）。 */
    private fun MacrobenchmarkScope.swipeToNextTab() = swipeTabHorizontally(fromFraction = 0.8f, toFraction = 0.2f)

    /** 前のタブへ（左→右の横スワイプ）。 */
    private fun MacrobenchmarkScope.swipeToPrevTab() = swipeTabHorizontally(fromFraction = 0.2f, toFraction = 0.8f)

    /**
     * タブページャへ横スワイプを注入する（shell `input swipe`＝[ChapterFlipBenchmark] と同じ実証済み経路）。
     *
     * **y を画面上部の見出し帯（[SWIPE_Y_FRACTION]）に取る理由**——ここが本ベンチ固有の要点:
     * 「さがす」面は本文中に**内側の HorizontalPager を2つ**持つ（気分ブロックとランキング期間）。
     * 本文中央の高さで横スワイプすると内側のページャがジェスチャを消費し、外側のタブページャまで
     * 届かない＝「タブが切り替わらない」空振りになる。3タブとも上部は見出し（本棚＝TopAppBar・
     * さがす＝「さがす」題字・設定＝「設定」題字）で、横ドラッグを消費する要素が無い唯一の共通帯。
     * 上端に寄せすぎると通知シェードの引き下ろしと競合するため、ステータスバーより十分下へ置く。
     * この y は 2026-08-06 の初回実機実測で実証済み（0.15H＝PGEM10 で y=475。swipeTabs 5反復
     * ×12スワイプが全弾コミットし、内側ページャへの奪われは一度も出なかった）＝較正不要と確定。
     *
     * x は 0.8W↔0.2W（移動 0.6W）＝ページャの確定条件を大きく満たし、起点はどちらもエッジの
     * 戻る/進むジェスチャ帯から十分離れる（章送りベンチと同値）。尺 100ms も同値。
     */
    private fun MacrobenchmarkScope.swipeTabHorizontally(fromFraction: Float, toFraction: Float) {
        val w = device.displayWidth
        val y = (device.displayHeight * SWIPE_Y_FRACTION).toInt()
        device.executeShellCommand(
            "input swipe ${(w * fromFraction).toInt()} $y ${(w * toFraction).toInt()} $y 100"
        )
    }

    /**
     * LibrarySeedReceiver へ shell `am broadcast` を送り、100冊＋実HTML 50章の計測用の書を投入して完了を待つ。
     * 作法（force-stop で dead 化してから shell 経由）と根拠は ChapterFlipBenchmark.seedChapterBook と同一
     * （ColorOS の broadcast 沈黙不達＝docs/knowledge/coloros-broadcast-silent-drop.md）。
     *
     * `gridMode true` を渡す理由（2026-08-05・旧値 false から変更）: シーダーが K の `k_grid_view` も書く
     * ようになり gridMode が**実際に効くようになった**（それ以前は D の `is_grid_view` しか書かず、
     * ADR 0027 のゲートで明快K へクランプされる benchmark ビルドでは1つも効いていなかった）。
     * 本ベンチは面の徴と題名の掴み方を両対応にしてあるのでどちらでも成立するが、これまで実測してきたのは
     * K の既定＝**グリッド**なので、値の連続性を保つため明示的に true を渡す（false にすると測る面が
     * 変わり、既存の実測値と比較できなくなる）。
     */
    private fun seedLibrary() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        val out = device.executeShellCommand(
            "am broadcast --include-stopped-packages" +
                " -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_SEED" +
                " --ei count $SEED_COUNT --ei chapterCount $CHAPTER_COUNT --ez gridMode true"
        )
        val result = Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
        if (result != SEED_COUNT) {
            fail("シード結果 result=$result（期待 $SEED_COUNT）。am broadcast 出力: $out")
        }
    }

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.LibrarySeedReceiver"
        const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"
        const val SEED_COUNT = 100
        const val CHAPTER_COUNT = 50
        const val MEASURE_BOOK_TITLE = "章送り計測の書"

        /** 往復回数。1往復＝4スワイプ＝計12スワイプで、実機ベースラインの「12フリック」条件と同じ density。 */
        const val ROUND_TRIPS = 3

        /** 横スワイプを注入する高さ（画面高に対する比）。選定理由は [swipeTabHorizontally] の KDoc。 */
        const val SWIPE_Y_FRACTION = 0.15f

        // 各タブの「そこに居る」徴＝**その面にしか無い**文字列を使う。恒常ボトムナビのラベル
        // （本棚／さがす／設定）は3タブとも常時ツリーに居るため徴として使えない（常に真になる）。
        /** 本棚＝読書状態フィルタの先頭チップ（D/K 共通）。 */
        val SHELF_MARKER: BySelector = By.text("すべて")

        /** さがす＝検索窓のプレースホルダ（全スキンの発見面が同一文言を持つ・ネットワーク不要で出る）。 */
        val DISCOVER_MARKER: BySelector = By.text("作品名・作者名・キーワードで探す")

        /** 設定＝設定カードの行見出し（きせかえ行は公開ゲートで消えるため、常に在る行を選ぶ）。 */
        val SETTINGS_MARKER: BySelector = By.text("文字と組版")

        /**
         * 目次画面の徴＝「本棚に戻る」ボタンの contentDescription（2026-08-06 実機 dump で確認）。
         * 章タイトル text（第N章）は本文にも目次にも出て面の判別にならないため、目次にしか無いノードを使う。
         */
        val TOC_MARKER: BySelector = By.desc("本棚に戻る")
    }
}
