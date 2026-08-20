package com.novelreader.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.domain.SearchDraft
import com.novelreader.domain.SearchFilters
import com.novelreader.narou.SearchHistory
import com.novelreader.ui.discovery.DiscoverySearchContent
import com.novelreader.ui.skins.k.captureSkinK
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 検索ホーム（nav ルート `discovery/search`＝[com.novelreader.ui.discovery.DiscoverySearchScreen]）の
 * スクリーンショット回帰。**監査 2026-08-06 G-8 で 0枚だったさがす配下の面**を埋める束（優先度2位）。
 * K 素地で包む理由・Content 層を撮る理由は [NovelDetailScreenScreenshotTest] の KDoc と同じ。
 *
 * ### 撮る case と選定理由
 *  - `drafted`（3テーマ×2スケール全数）＝入力途中の状態。**検索フィールド（高さ 52dp 固定）・検索範囲チップ・
 *    「条件を調整」・履歴・下端の選択キーワード追従バー**が一度に載る、この画面で最も器が詰まった状態。
 *    fontScale 2.0 で最初に壊れるのは高さを固定した検索フィールドで、短い語では字面が器に収まってしまい
 *    退行が写らないため、実際に打たれる長さの複合語（3トークン）を入れて撮る。
 *    絞り込み条件を持たせる（`filters`）のは「条件を調整」チップが件数バッジ付きへ変わる分岐を含めるため。
 *  - `history`（ライトのみ×2スケール）＝ピン留め・最近の検索が長語で埋まった状態。履歴チップは
 *    ユーザーが打った任意長の語がそのまま並ぶ唯一の場所で、器の幅を決めるのが**実データの長さ**しかない。
 *    書籍化タイトル級の長語（20字前後）を混ぜて折り返しの段数を固定する。
 *
 * 撮っていないもの（既知の穴）:
 *  - 検索範囲を全て外した警告（「検索範囲は1つ以上必要です」）・キーワードカテゴリ展開時の版面。
 *
 * 「条件を調整」シートは 2026-08-17 に本番を枠（VM 受け）／中身（コールバック受け）へ分けて
 * [SearchConditionSheetScreenshotTest] が撮るようになった（前便の申し送り事項の消化）。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DiscoverySearchScreenScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val draft = if (caseId == CASE_HISTORY) SearchDraft() else DRAFTED
        val history = if (caseId == CASE_HISTORY) LONG_HISTORY else SHORT_HISTORY
        composeTestRule.captureSkinK(theme, fontScale, goldenName("DiscoverySearchScreen", caseId, theme, fontScale)) { _ ->
            DiscoverySearchContent(
                draft = draft,
                history = history,
                onBack = {},
                onSetDraft = {},
                onExecuteSearch = {},
                onSearchHistoryWord = {},
                onPinWord = {},
                onUnpinWord = {},
                onRemoveRecentWord = {},
                onOpenConditionSheet = {},
            )
        }
    }

    companion object {
        private const val CASE_DRAFTED = "drafted"
        private const val CASE_HISTORY = "history"

        /** 3トークン＝下端の選択キーワード追従バーが出る最小構成より1つ多い（バー内の折り返しも見る）。 */
        private val DRAFTED = SearchDraft(
            word = "廃鉱山 付与術 ざまぁ",
            notWord = "ハーレム",
            // なぜ4項目とも明示するか: 既定は 2026-08-21 に4項目 ON へ変わった（placeholder の約束との食い違いを
            // 直した）。明示しないとこの版面も全点灯になり、「範囲チップが一部だけ点灯している版面」が golden から
            // 消えて未選択チップの意匠が撮られなくなる。既定（＝全点灯）は CASE_HISTORY 側が撮る。
            inTitle = true,
            inStory = true,
            inKeyword = false,
            inWriter = false,
            // 「条件を調整」の件数バッジを点灯させる（条件ゼロだとバッジ自体が出ず、その分岐が撮れない）。
            filters = SearchFilters(length = "100000-", time = "-600"),
        )

        private val SHORT_HISTORY = SearchHistory(
            pinned = listOf("付与術"),
            recent = listOf("廃鉱山", "鍛冶"),
        )

        /** 書籍化タイトル級の長語を混ぜる＝チップ1個が行幅を占め、折り返しが必ず起きる長さ。 */
        private val LONG_HISTORY = SearchHistory(
            pinned = listOf("追放された万能付与術師", "辺境スローライフ"),
            recent = listOf(
                "婚約破棄されたので辺境で薬草を育てます",
                "ハイファンタジー 完結済み",
                "時間逆行",
                "職人 ものづくり",
                "姉御肌ヒロイン",
            ),
        )

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_DRAFTED, t, s)) }
            }
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_HISTORY, ReadingTheme.LIGHT, s))
            }
        }
    }
}
