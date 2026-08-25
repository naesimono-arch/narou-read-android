package com.novelreader.bench

import com.novelreader.PrefKeys
import com.novelreader.ui.intro.FakeIntroFlagStore
import com.novelreader.ui.intro.IntroController
import com.novelreader.ui.intro.IntroGroup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Macrobenchmark の**シード契約**（`LibrarySeedReceiver` が「測る面」を決定論に固定する責務）の退行を
 * 機械で捕まえるラチェット。
 *
 * ## なぜ要るか（同型が2回起きている）
 * ベンチの着地判定や描画原価は端末に残った prefs に左右されるのに、**シーダーがその軸を固定し忘れても
 * コンパイルは通り、テストも緑のまま**という欠陥クラスが2度成立した:
 *  - 2026-08-05 `gridMode` … `is_grid_view` だけを書いており、実際に読まれる `k_grid_view` を書いていなかった
 *    ＝「面を指定したつもりで1つも指定できていない」状態で本棚ベンチが走っていた。
 *  - 2026-08-19 `verticalMode` … 書字方向を固定しておらず、端末に残った縦書きのまま tab-swipe が走り
 *    全5走行の iter000 が「本文に着地しない」で落ちた（縦書き本文は text ノードを持たないため）。
 * どちらも真因は個別の軸ではなく**「シード契約とベンチの前提が突合されていないこと」**。2回起きたものは
 * 3回目も起きるので、突合を人の注意力から機械へ移す。
 *
 * ## 何を検査するか（3点）
 *  1. **分類の網羅**: [PrefKeys] の全キーが下の3表のどれかに必ず載る。新しいキーを足した人は
 *     「これはベンチの測る面を変えるか」を**表に書くまでビルドを通せない**（＝取りこぼしの入口を塞ぐ）。
 *  2. **固定の実在**: [MUST_PIN] のキーはシーダー本体が実際に参照している。verticalMode 固定を誰かが
 *     消したら落ちる（2026-08-19 と同型の再発検知）。
 *  3. **表の鮮度**: [KNOWN_UNPINNED_SURFACE] のキーがシーダーで固定され始めたら落として [MUST_PIN] へ
 *     移させる。表が実態から静かにずれるのを防ぐ（表そのものが腐ると 1. と 2. が無意味になる）。
 *
 * ## なぜ「ソースを読む」テストなのか（判定不能を避けるための割り切り）
 * `LibrarySeedReceiver` は `src/benchmark` にしか存在せず、**debug 変種の単体テストからは参照できない**
 * （型として import できない）。一方、必須ゲートは `testDebugUnitTest` ＝ここで落ちないと意味がない。
 * よって型ではなく**ソース文字列**を突合する。脆いが、守りたいのは「キー名がシーダーに現れているか」
 * という文字列レベルの事実そのものなので、この検査には十分。ファイルが見つからないときは
 * **緑で素通りさせず fail** する（サイレントスキップは、この手のテストが死ぬ典型経路）。
 */
class SeedContractPrefKeysTest {

    /**
     * シーダーが**必ず固定する**キー。ベンチの着地判定・描画原価が直接依存しており、端末の残り値で
     * 測ると結果が別物になるもの。ここに足したら `LibrarySeedReceiver` 側の実装も要る（2. が強制する）。
     */
    private val mustPin = setOf(
        // 本棚のグリッド⇄リスト。D/K で別キーなので両方（2026-08-05 の取りこぼしの当事者）。
        "IS_GRID_VIEW",
        "K_GRID_VIEW",
        // 読書画面の書字方向。縦書きは描画経路ごと変わり、text ノードも消える（2026-08-19 の当事者）。
        "READING_VERTICAL",
        // 教示「はじめに」の 3 系統（2026-08-25 新設）。未消費だと組B が**本文の上へスクリム＋カードを
        // 重ねる**＝ベンチが「本文を測ったつもりで教示を測る」。とくに読書系（chapter-flip / toc-push /
        // tab-swipe）は着地判定も描画原価も別物になるので、これは面の選択肢ではなく**必ず倒す軸**。
        // シーダーは extra 任せにせず無条件で true にする（LibrarySeedReceiver の当該コメント参照）。
        "INTRO_ABOUT_SHOWN",
        "INTRO_READING_SHOWN",
        "INTRO_SEARCH_SHOWN",
    )

    /**
     * **面は変えうるが、まだシーダーが固定していない**キーと、固定していない理由。
     * ⚠️ これは「安全だ」という宣言ではなく**既知の穴の台帳**。ベンチが不安定なとき最初に疑う場所で、
     * 新しい面を測り始めてここが効くと分かったら [MUST_PIN] へ移すこと。
     */
    private val knownUnpinnedSurface = mapOf(
        // 装いは benchmark ビルドの機能ゲート（ADR 0027）で明快K へクランプされる＝端末値は効かない。
        "APP_SKIN" to "benchmark ビルドは K へクランプされるため端末値が効かない",
        // K 以外の装いの一覧⇄別表示トグル。上と同じ理由で benchmark ビルドでは読まれない。
        "M_SKY_VIEW" to "M 装いは benchmark ビルドで到達しない",
        "P_RACK_VIEW" to "P 装いは benchmark ビルドで到達しない",
        "J_DECK_VIEW" to "J 装いは benchmark ビルドで到達しない",
        "P_HINGE_DETENT" to "P 装いは benchmark ビルドで到達しない",
        // 読書テーマ（ライト/ダーク）と本文の組版パラメータ。既定のまま測っている＝端末で変更されると
        // 数値が動きうる**実在の穴**。現状どのベンチも明示していない。
        "READING_THEME" to "未固定の既知穴。端末でテーマを変えると読書面の描画原価が動く",
        "READING_FONT_SIZE" to "未固定の既知穴。字送り/行数が変わり本文の描画枚数が動く",
        "READING_LINE_HEIGHT" to "未固定の既知穴。同上",
        "READING_BODY_MARGIN" to "未固定の既知穴。同上",
        // 高負荷トグルは debug 限定（ADR 0023）＝benchmark ビルドには出ない。
        "SKY_HIGH_LOAD_M" to "debug 限定トグル（benchmark ビルドに無い）",
        "SHIORI_HIGH_LOAD_K" to "debug 限定トグル（benchmark ビルドに無い）",
        // 栞先端 tip の固定は debug 限定の観察器（ShioriDebugTip）＝benchmark ビルドでは解決が常に null。
        "SHIORI_DEBUG_TIP_INDEX" to "debug 限定の観察器（benchmark ビルドで値が解決されない）",
    )

    /**
     * 測る面を変えないキー（保存領域名・診断・履歴・一度きりのフラグ類）。
     * ⚠️ 「一度きりの案内フラグ」は**未表示だと初回に案内 UI が挟まって面が変わる**ものがあるが、
     * ベンチは毎回全消し→シードで DB を作り直す一方 prefs は残るため、実機では既に表示済みへ倒れている。
     * 新しい端末で走らせ始めたときだけ差が出うる点は承知のうえでここに置く。
     */
    private val neutral = setOf(
        "FILE_APP_PREFS", "FILE_NAROU_SEARCH_HISTORY",
        "SETTINGS_SCHEMA_VERSION",
        "NEW_EPISODE_NOTIFY_ENABLED",
        "IMMERSIVE_HINT_SHOWN",
        "BATTERY_DIALOG_DISMISSED",
        "NOTIF_PRIMING_SHOWN",
        "REIMPORT_SWEEP_SEEN_IDS",
        "PDF_LIBRARY_TREE_URI",
        "DIAG_SESSION_OPEN", "DIAG_LAST_SEEN_AT", "DIAG_LAST_SCREEN",
        "SEARCH_HISTORY_PINNED", "SEARCH_HISTORY_RECENT",
    )

    /** [PrefKeys] が公開する String 定数の**名前**を全数取る（object の INSTANCE 等は除く）。 */
    private fun allPrefKeyNames(): Set<String> =
        PrefKeys::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.name }
            .toSet()

    @Test
    fun `PrefKeys の全キーがシード契約の分類表に載っている`() {
        val classified = mustPin + knownUnpinnedSurface.keys + neutral
        val unclassified = allPrefKeyNames() - classified
        if (unclassified.isNotEmpty()) {
            fail(
                "PrefKeys に分類表へ未登録のキーがある: ${unclassified.sorted()}\n" +
                    "これは「ベンチの測る面を変える軸を足したのに、シード契約が追いついていない」可能性の合図。\n" +
                    "SeedContractPrefKeysTest の mustPin / knownUnpinnedSurface / neutral のどれかへ" +
                    "**理由付きで**登録すること（面を変えるなら LibrarySeedReceiver 側の固定も要る）。"
            )
        }
        // 逆向き（表にあるのに PrefKeys から消えた）も落とす＝表が幽霊キーを抱えたまま腐るのを防ぐ。
        val stale = classified - allPrefKeyNames()
        assertTrue("分類表に PrefKeys へ実在しないキーが残っている: ${stale.sorted()}", stale.isEmpty())
    }

    @Test
    fun `シーダーは固定必須のキーを実際に参照している`() {
        val src = readSeederSource()
        val missing = mustPin.filterNot { src.contains("PrefKeys.$it") }
        assertTrue(
            "LibrarySeedReceiver が固定必須キーを参照していない: $missing\n" +
                "ベンチは端末に残った値で走ることになり、着地判定が空振りするか別の面を測る" +
                "（2026-08-05 gridMode / 2026-08-19 verticalMode と同型の退行）。",
            missing.isEmpty(),
        )
    }

    @Test
    fun `未固定表のキーがシーダーで固定され始めたら表を更新させる`() {
        val src = readSeederSource()
        val nowPinned = knownUnpinnedSurface.keys.filter { src.contains("PrefKeys.$it") }
        assertTrue(
            "knownUnpinnedSurface のキーが LibrarySeedReceiver で固定されている: $nowPinned\n" +
                "実態が先に進んだので mustPin へ移すこと（表が実態からずれると本テスト全体が無意味になる）。",
            nowPinned.isEmpty(),
        )
    }

    /** 教示「はじめに」の 3 系統（陽性確認で名指しする対象）。 */
    private val introKeys = setOf("INTRO_ABOUT_SHOWN", "INTRO_READING_SHOWN", "INTRO_SEARCH_SHOWN")

    @Test
    fun `固定行を落とすと検知器が落ちる（塞いだつもりで効いていない状態を作らない）`() {
        // 陽性確認。2. は「参照が在るか」しか見ないので、**在るのが当たり前**の状態では
        // 検知器が生きているのか空回りしているのか区別が付かない。実ソースから教示の固定行だけを
        // 取り除いた版を作って同じ判定に掛け、ちゃんと 3 本とも未固定として名指しされることを見る。
        val broken = readSeederSource()
            .lines()
            .filterNot { line -> introKeys.any { key -> line.contains("PrefKeys.$key") } }
            .joinToString("\n")
        val missing = mustPin.filterNot { broken.contains("PrefKeys.$it") }.toSet()
        assertEquals(
            "固定行を落とした版では 3 系統すべてが『未固定』として検出されねばならない" +
                "（ここが空集合なら 2. の検査は何も守っていない）",
            introKeys,
            missing,
        )
    }

    @Test
    fun `シードで倒した端末では教示が1枚も出ない（未固定だと本文の上に載る）`() {
        // 上の 2 つは**文字列**しか見ていない。キー名を正しく参照していても、そのキーを倒すことが
        // 本当に教示を黙らせるとは限らない（別のキーで出し分けていれば無意味）。ここだけは実物の
        // 出現判定（IntroController）に通して、固定の**効き**を確かめる。
        val unpinnedDevice = IntroController(FakeIntroFlagStore())
        unpinnedDevice.requestAuto(IntroGroup.READING)
        assertNotNull(
            "これが塞ぐべき穴そのもの: 未消費の端末では本文初回にカードが本文の上へ載る",
            unpinnedDevice.flow,
        )

        val seededDevice = IntroController(
            FakeIntroFlagStore().apply { preShow(*IntroGroup.entries.toTypedArray()) },
        )
        IntroGroup.entries.forEach { seededDevice.requestAuto(it) }
        assertNull("シードで倒した端末で教示が出た＝計測面が汚れる", seededDevice.flow)
    }

    /**
     * `src/benchmark` のシーダー本体を読む。単体テストの作業ディレクトリは実行環境で揺れるため、
     * カレントから上へ辿って探す。**見つからなければ fail**（緑で素通りさせない＝KDoc の割り切り参照）。
     */
    private fun readSeederSource(): String {
        val rel = "app/src/benchmark/java/com/novelreader/bench/LibrarySeedReceiver.kt"
        var dir: File? = File("").absoluteFile
        val tried = mutableListOf<String>()
        while (dir != null) {
            for (candidate in listOf(File(dir, rel), File(dir, "src/benchmark/java/com/novelreader/bench/LibrarySeedReceiver.kt"))) {
                tried += candidate.path
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        fail("LibrarySeedReceiver.kt を見つけられずシード契約を検査できなかった。探索: ${tried.take(6)}")
        error("unreachable")
    }
}
