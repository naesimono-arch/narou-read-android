package com.novelreader.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.novelreader.ui.discovery.NovelDetailContent
import com.novelreader.ui.skins.k.captureRoot
import com.novelreader.ui.skins.k.setSkinKContent
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.NovelDetailUiState
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 作品詳細（nav ルート `discovery/detail/{ncode}`＝[com.novelreader.ui.discovery.NovelDetailScreen]）の
 * スクリーンショット回帰。**監査 2026-08-06 G-8 で 0枚だったさがす配下の面**を埋める束（優先度2位）。
 *
 * ### なぜ K 素地で包むか（captureThemed でなく setSkinKContent）
 * この画面はスキン分岐を持たない共有実装だが、出荷時に載る素地は既定スキン＝[com.novelreader.ui.theme.Skin.MEIKAI_K]
 * （ADR 0027 の初回公開スコープ）。[captureThemed] は `NovelReaderTheme` の既定 skin（WAMODERN_D）で包むため、
 * 画面が読む LocalShelfColors（末尾メタの infoText）が実際の出荷素地と別の束になる。現時点で K は D へ全委譲＝
 * 色値は一致するが、K がパレットを分けた瞬間に golden が静かに実画面から乖離する（K support の KDoc と同じ判断）。
 *
 * ### なぜ VM でなく [NovelDetailContent] を撮るか
 * ルート層は `viewModel.load(ncode)` で実 API を叩き、`fetchedAtMillis` に**実時刻**が入る＝絵が撮るたび変わる。
 * 描画層は state と callback だけの葉なので、状態を固定して撮れる（既存の分割方針そのままの利用＝
 * [ReadingSettingsSheetScreenshotTest] がシート中身を直接組むのと同型）。
 *
 * ### 決定性のための TimeZone 固定
 * 末尾の「HH:mm 時点の情報」は `SimpleDateFormat` が **JVM 既定 TimeZone** を読む。golden は WSL(Linux) 記録を
 * 正とするが、CI コンテナが UTC・開発機が JST という差だけで絵が変わり、誰も触っていないのに赤くなる。
 * `fetchedAtMillis` の固定だけでは足りないため、TimeZone も Asia/Tokyo へ固定して比較対象から外す。
 *
 * ### 撮る case と選定理由
 *  - `content`（3テーマ×2スケール全数）＝上端。書影ヒーロー＋作者/ジャンル行＋ステータス2×2表＋固定バー。
 *    fontScale 2.0 で最初に壊れるのはここで、**長作者名（15字）×最長ジャンルラベル「ヒューマンドラマ」**の
 *    組が作者行の `weight(1f, fill=false)` 防御を試し、**"約41時間（123.5万字）"** が `IntrinsicSize.Min` で
 *    組んだ2×2表の列幅主張を試す。短い既定値（題名 "t"・作者 "w"）で撮ると 1.0 と 2.0 の差がほぼ出ず、
 *    走査 `tools/check_golden_*.py`（1.0 と 2.0 の対で判定）が原理的に何も拾えない。
 *  - `story`（ライトのみ×2スケール）＝末尾キーワードまで送った版。24個のタグ FlowRow の**下側の段**が
 *    ここに写る。既存流儀どおり追加状態はライトのみ（色トークンは `content` 側が張る）で、狙いは
 *    折り返し段数の退行だけに絞る。
 *    ⚠️ 旧 KDoc は「約210字の本文ブロックが**ここにしか写らない**」と書いていたが**事実に反していた**
 *    （2026-08-21 に画素で確認）。送り先が末尾タグ「完結済み」＝それが画角の下端に来るので、
 *    あらすじ本文はとうに上へ流れ去っている。下の2 case はその穴を塞ぐために足したもの。
 *  - `synopsis`（ライトのみ×2スケール）＝キーワードの**先頭チップ**まで送った版。下端に「R15」が来る
 *    ように送るので、画角には〈あらすじ本文の下側 → キーワード見出し → 見出し下の空き → 1段目のチップ〉が
 *    そろって入る。狙いは2つで、(a) 約210字の本文ブロックの折り返し（`story` が写していなかったぶん）と
 *    (b) **節見出しの下の空き S12**。(b) は 4ee7829 がモック逆同期で S8→S12 へ直した値なのに、
 *    どの golden の画角にも入っておらず戻しても緑のままだった（2026-08-21 の回帰監査）。
 *  - `tail`（ライトのみ×2スケール）＝末尾の取得時刻メタまで送った版。下端が最終行になるので、画角には
 *    〈評価見出し → 見出し下の空き → 評価行 → 末尾メタ〉が入る。狙いは **last-updated の上アキ S16**で、
 *    こちらも 4ee7829 が S24→S16 へ直した値ながら画角外だった。評価節は従来どの golden にも
 *    写っておらず、この case で初めて絵が付く。
 *
 * 撮っていないもの（既知の穴）: Loading / NotFound / Error の3状態、取込済み（`isImported=true`）の
 * 固定バー分岐、本棚トグルの on 状態。いずれも版面が小さく、拡大破綻の主戦場ではないため後回し。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class NovelDetailScreenScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun pinTimeZone() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    }

    @After
    fun restoreTimeZone() {
        // 既定 TimeZone は JVM 全体の状態＝戻さないと同一 JVM で後続するテストへ漏れる。
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun capture() {
        composeTestRule.setSkinKContent(theme, fontScale) { _ ->
            NovelDetailContent(
                uiState = NovelDetailUiState.Content(
                    novel = DiscoveryScreenshotFixtures.detail(),
                    // 固定 epoch ＋ TimeZone 固定で末尾メタを「20:00 時点の情報」に一意化する。
                    fetchedAtMillis = FETCHED_AT_MILLIS,
                ),
                onSearchKeywords = {},
                onImportPdf = {},
                onUp = {},
                onRetry = {},
                onReadOnNarou = {},
            )
        }

        // 送り先は「その case で**画角の下端に来てほしい**要素」を名指しする。下へ送るときの
        // performScrollTo はその要素の下辺をスクロール域の下辺に合わせるので、下端を指定すれば
        // 「そこから上へ1画面ぶん」が写る＝写したい帯を後ろから指定できる。
        val scrollTarget = when (caseId) {
            CASE_STORY -> LAST_KEYWORD
            CASE_SYNOPSIS -> FIRST_KEYWORD
            CASE_TAIL -> null // 末尾メタは substring 一致で引く（文言に取得時刻が混ざるため）
            else -> null
        }
        if (scrollTarget != null) {
            composeTestRule.onNodeWithText(scrollTarget).performScrollTo()
            composeTestRule.waitForIdle()
            // スクロールが空振りすると golden が「上端と同じ絵」に化け、以後この case は
            // どんな退行も検出しなくなる（＝撮っているのに守らない golden）。
            composeTestRule.onNodeWithText(scrollTarget).assertIsDisplayed()
        } else if (caseId == CASE_TAIL) {
            composeTestRule.onNodeWithText(FETCHED_AT_SUFFIX, substring = true).performScrollTo()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(FETCHED_AT_SUFFIX, substring = true).assertIsDisplayed()
        }
        // 狙った帯が本当に画角へ入ったかを、下端の要素だけでなく**上側の要素**でも名指しする。
        // 下端だけの担保では「送りすぎて狙いの見出しが上へ抜けた」絵をそのまま golden にしてしまい、
        // 見出し下の空きを守るつもりの case が何も守らなくなる。
        when (caseId) {
            CASE_SYNOPSIS -> composeTestRule.onNodeWithText(KEYWORD_HEADING).assertIsDisplayed()
            CASE_TAIL -> {
                // 末尾メタの**直上**は評価表の最終行＝この2つが同時に写っていれば、両者の間の上アキ S16 が
                // 画角に入っていることになる（守りたい値そのもの）。どちらのスケールでも成り立つ。
                composeTestRule.onNodeWithText(LAST_EVAL_LABEL).assertIsDisplayed()
                // 評価**見出し**（＝見出し下 S12 の担保）は 1.0 でしか同じ画角に入らない。2.0 では
                // 評価4行が膨らんで見出しが上へ抜けるため、ここで縛ると実装が正しいまま赤になる。
                // S12 側は `synopsis`（キーワード見出し）が両スケールで担保するので取りこぼしは無い。
                if (fontScale == 1.0f) {
                    composeTestRule.onNodeWithText(EVAL_HEADING).assertIsDisplayed()
                }
            }
        }

        composeTestRule.captureRoot(goldenName("NovelDetailScreen", caseId, theme, fontScale))
    }

    companion object {
        private const val CASE_CONTENT = "content"
        private const val CASE_STORY = "story"
        private const val CASE_SYNOPSIS = "synopsis"
        private const val CASE_TAIL = "tail"
        // （削除 2026-08-21）NCODE ＝ 旧 BookCover の地色シード（bookId のハッシュ）。案2-c で書影が
        // 栞書影へ移り、色相も先端も**題名**から決まるようになったので ncode は描画に一切効かない
        // ＝ NovelDetailContent の引数ごと落ちた。決定論はフィクスチャの題名が担保する。

        private const val FETCHED_AT_MILLIS = 1_767_265_200_000L // 2026-01-01 20:00 JST

        /** [DiscoveryScreenshotFixtures.MANY_KEYWORDS] の最終トークン。 */
        private const val LAST_KEYWORD = "完結済み"

        /** [DiscoveryScreenshotFixtures.MANY_KEYWORDS] の先頭トークン＝1段目のチップ。 */
        private const val FIRST_KEYWORD = "R15"

        /** 末尾メタは "HH:mm 時点の情報"（先頭に最終更新が付く）＝時刻に依らない後半で引く。 */
        private const val FETCHED_AT_SUFFIX = "時点の情報"

        private const val KEYWORD_HEADING = "キーワード"
        private const val EVAL_HEADING = "評価"

        /** 評価表の最終行ラベル（末尾メタの直上に来る＝上アキ S16 を画角へ入れる目印）。 */
        private const val LAST_EVAL_LABEL = "週間ポイント"

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_CONTENT, t, s)) }
            }
            // 追加3 case はいずれもライトのみ×2スケール（色トークンは content 側が張る＝
            // ここで見たいのは画角と折り返しだけ）。
            listOf(CASE_STORY, CASE_SYNOPSIS, CASE_TAIL).forEach { case ->
                ScreenshotConfig.FONT_SCALES.forEach { s ->
                    add(arrayOf<Any>(case, ReadingTheme.LIGHT, s))
                }
            }
        }
    }
}
