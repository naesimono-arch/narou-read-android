package com.novelreader.ui.skins

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.skins.j.BookshelfGridJ
import com.novelreader.ui.skins.j.BookshelfPortalJ
import com.novelreader.ui.skins.k.BookshelfK
import com.novelreader.ui.skins.m.BookshelfLogM
import com.novelreader.ui.skins.m.BookshelfSkyM
import com.novelreader.ui.skins.p.BookshelfCartridgeP
import com.novelreader.ui.skins.p.BookshelfListCartridgeP
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.ReimportPlan
import com.novelreader.domain.ScanProgress

// ============================================================
// 本棚スキン面の契約（2026-07-27 純構造リファクタ）
//
// 6つの引数束 ＋ 役割の sealed interface（Immersive/Listing）＋ スキン→面のファクトリ。
// なぜここ（skins/）か: どのスキンがどの面を持つか・面のビュー切替状態（ShelfViewToggle）は
// スキンの知識のため、受付（BookshelfContent）には束と2分岐だけを残し、スキン列挙はここへ寄せる。
// 依存方向は従来どおり ui/ → skins/ → theme/（theme/ は skins/ を import しない）を保つ。
//
// 【束に既定値を付けない理由】旧シグネチャの「既存呼び出し互換のため既定値（no-op/false）」は、
// 新しい呼び出し元が配線を忘れても無音で成立してしまう欠陥クラスだった（結線漏れが実機で沈黙死する）。
// 束のフィールドを全指定必須にすることで、配線忘れをコンパイルエラーへ格上げする。
// ============================================================

/**
 * 本棚が描く中身データの束（蔵書・Web由来・進捗・章数・続きあり）。
 * すべて読み取り専用のスナップショット＝インスタンス生成後に中身は変わらない（@Immutable の根拠）。
 */
@Immutable
internal data class ShelfData(
    val books: List<BookEntity>,
    val webNovels: List<WebNovelEntity>,
    /** ncode→最後に開いた話（機能②）。 */
    val webReadingProgress: Map<String, Int>,
    /** ncode→web 最終接触時刻（webRecencyKeyOf・2026-07-26 裁定）。 */
    val webLastReadAt: Map<String, Long>,
    val progressMap: Map<String, ProgressEntity>,
    /** bookId→章数（進捗行表示と状態フィルタ判定の単一真実源）。 */
    val chapterCountMap: Map<String, Int>,
    /** 続きありバッジ用のなろう詳細（key=ncode）。 */
    val newEpisodeNovelMap: Map<String, WorkSummary>,
    /** 続きありバッジ用の Web 蔵書の観測値（key=bookId→Worker が最後に見たサイト総話数）。
     *  なろう詳細と別系統なのは観測手段の違いだけで、判定は同一（ui/BookCard.newEpisodeCountFor）。 */
    val webNewEpisodeTotals: Map<String, Int>,
    /** 本文欠落本の bookId→復旧手段（案B バッジ・2026-07-29）。キー存在＝欠落。
     *  K と D/C 共通描画が「本文なし」バッジ・状態行に使う（M/P/J はモック未裁定のため未表出＝タップ時の
     *  復旧ダイアログは route 層の onOpenBook 差し替えで全スキン共通に効く）。 */
    val reimportPlans: Map<String, ReimportPlan>,
)

/**
 * 画面の額縁状態の束（読書状態フィルタ・取込中・読み込み中）。
 * フィルタの選択状態は骨格（BookshelfContent）所有の単一状態機械＝ここは値とコールバックの写し。
 */
@Immutable
internal data class ShelfChrome(
    val selectedStatus: ReadingStatus?,
    val statusCounts: Map<ReadingStatus, Int>,
    val onSelectStatus: (ReadingStatus?) -> Unit,
    val processingState: ProcessingState,
    val isLoading: Boolean,
    /** 本文欠落の一括検出バナー（案C・2026-07-29）を出すか。判定（新規検出の指紋）は VM が持つ。
     *  走査中は VM 側で false になる＝同じスロットに出る [folderScan] のバナーと排他。 */
    val sweepBannerVisible: Boolean,
    /** 案C バナー「あとで」＝指紋を保存して以後この集合では出さない（VM へ委譲）。 */
    val onSweepLater: () -> Unit,
    /** 案C バナー「まとめて再取込」＝内訳確認ダイアログを開く（ダイアログは route 層所有＝全スキン共通）。 */
    val onSweepConfirm: () -> Unit,
    /** PDF フォルダ走査の進捗（案X・2026-07-29）。null＝走査していない＝走査バナーを出さない。 */
    val folderScan: ScanProgress?,
    /** 走査バナーの「停止」＝今読んでいる1件の完了後に中断し、そこまでの一致は結果に残す（VM へ委譲）。 */
    val onScanStop: () -> Unit,
)

/**
 * 全スキン面が共有する画面操作の束（開く・追加・見つける・装い・取込停止）。
 * onOpenDiscovery/onOpenWardrobe は 2026-07-29 の K形正本追従で大半の面から撤去済み
 * （発見は恒常ナビ「さがす」タブ・装いは設定タブ「きせかえ」へ移管＝ADR 0021 追記）。
 * 残る使用はモックが温存するスキン署名のみ＝M 銘クラスタの装い（4条星）・J デッキ面クロームの
 * 見つける/装い（K形モック対象外）。束には残す＝実配線された本物のコールバックを渡す
 * （no-op 既定値は置かない＝配線忘れをコンパイルエラーへ格上げする流儀は不変）。
 */
@Immutable
internal data class ShelfActions(
    val onOpenBook: (BookEntity) -> Unit,
    val onFabClick: () -> Unit,
    val onOpenDiscovery: () -> Unit,
    val onOpenWardrobe: () -> Unit,
    val onCancelProcessing: () -> Unit,
)

/**
 * 複数選択削除の状態機械の束（一覧面専用＝Immersive には型として渡らない）。
 *
 * なぜ @Immutable でなく @Stable か: [selectedIds] の実体は骨格所有の SnapshotStateList＝
 * 同一インスタンスのまま中身が後から変わる。@Immutable（生成後一切不変）を付けると嘘になり、
 * Compose の skip 判定が古い選択内容を正当化しかねない。@Stable（変化は Snapshot 経由で
 * 観測可能・equals は安定）が正直な宣言。型を List<String> に絞るのは面側からの直接変更を
 * 型で禁じるため（変更は必ずコールバック経由＝状態機械の単一所有を保つ）。
 *
 * onSelectAll は M の観測野帳では意匠上未使用（全選択UIを持たない）だが実配線を渡す（no-op 禁止）。
 * onDeleteBooks をここに置く理由: 削除は選択モードの終端操作＝閲覧専用の Immersive から
 * 型ごと遮断する（「星図に選択モードを渡さない」のコンパイル時制約に削除も含める）。
 */
@Stable
internal data class ShelfSelection(
    val selectionMode: Boolean,
    val selectedIds: List<String>,
    val onToggleSelect: (String) -> Unit,
    val onEnterSelection: (String) -> Unit,
    val onExitSelection: () -> Unit,
    val onSelectAll: (List<String>) -> Unit,
    val onDeleteBooks: (List<BookEntity>, deleteSource: Boolean) -> Unit,
)

/** Web由来（未取込）カードの操作束（一覧面専用＝Immersive は蔵書のみを描く）。 */
@Immutable
internal data class ShelfWebActions(
    val onOpenWebNovel: (WebNovelEntity) -> Unit,
    /** 続きから読む＝記録した話(episode)へ WebView で直接着地（機能②）。 */
    val onResumeWebNovel: (novel: WebNovelEntity, episode: Int) -> Unit,
    val onImportWebNovel: (WebNovelEntity) -> Unit,
    val onRemoveWebNovel: (WebNovelEntity) -> Unit,
)

/**
 * テーマ4択の束（appTheme/システム追従の単一真実源＝読書設定シートと共有・2026-07-17 裁定②）。
 * P/J の⋮メニューが使う。M/K は意匠上テーマUIを持たない（M は星図固定・K は設定タブへ移管）。
 */
@Immutable
internal data class ThemeControl(
    val appTheme: ReadingTheme,
    val onThemeChange: (ReadingTheme) -> Unit,
    val followingSystem: Boolean,
    val onFollowSystem: () -> Unit,
)

/**
 * 本棚スキン面の役割（sealed）。受付（BookshelfContent）の分岐はこの2役だけになる。
 *
 * - [Immersive] 没入面（星図M・ラックP・デッキJ）＝閲覧と読書導線に徹する。
 *   [ShelfSelection]・[ShelfWebActions] を受け取るシグネチャ自体が無い＝「星図に選択モードを
 *   渡さない」を実行時分岐でなくコンパイル時制約にする（本 sealed 化の眼目）。
 * - [Listing] 一覧面（観測野帳M・一覧P・グリッドJ・明快K）＝選択削除・Webカード操作を持つ。
 */
internal sealed interface ShelfFace {
    /** 没入面。content は束を対応スキンの実面へ配線する（ビュー切替はファクトリが閉包で結線済み）。 */
    @Immutable
    class Immersive(
        val content: @Composable (
            data: ShelfData,
            chrome: ShelfChrome,
            actions: ShelfActions,
            theme: ThemeControl,
            snackbarHostState: SnackbarHostState,
        ) -> Unit,
    ) : ShelfFace

    /** 一覧面。没入面との差分＝選択状態機械と Web カード操作を追加で受ける。 */
    @Immutable
    class Listing(
        val content: @Composable (
            data: ShelfData,
            chrome: ShelfChrome,
            actions: ShelfActions,
            theme: ThemeControl,
            selection: ShelfSelection,
            webActions: ShelfWebActions,
            snackbarHostState: SnackbarHostState,
        ) -> Unit,
    ) : ShelfFace
}

/**
 * 装着中スキン（[LocalSkin]）→ 本棚面のファクトリ。null＝D/C（受付の共通描画が担う）。
 *
 * 各スキンのビュー切替状態（m_sky_view/p_rack_view/j_deck_view）はここで所有する
 * （[rememberShelfViewToggle]＝prefs 直参照・p_hinge_detent と同流儀）。トグルのコールバックは
 * 面の onToggleFace へ閉包で結線するため、受付にも束にもビュー切替の配線は存在しない。
 *
 * @param highLoadSkyM 高負荷スカイ試作トグル（ADR 0023・debug 限定）。M 構造だけが読む
 *   M 固有の素通しのため、共通の束に載せず引数でここまで運び M の面にだけ配る。
 * @param highLoadShioriK 栞アニメ高負荷トグル（ADR 0023 の明快K展開・2026-08-06 裁定・debug 限定）。
 *   K 構造だけが読む（highLoadSkyM と同じ理由で束に載せず K の面にだけ配る）。
 */
@Composable
internal fun rememberShelfFace(
    highLoadSkyM: Boolean,
    onHighLoadSkyChange: (Boolean) -> Unit,
    highLoadShioriK: Boolean,
): ShelfFace? = when (LocalSkin.current) {
    Skin.SEIZU_M -> {
        // 星図⇄一覧（旧 m_sky_view）。既定 true＝M 装着時は星図で開く（ADR 0022 §1）。
        val skyView = rememberShelfViewToggle(PrefKeys.M_SKY_VIEW, default = true)
        if (skyView.value) {
            ShelfFace.Immersive { data, chrome, actions, _, snackbarHostState ->
                // M の没入面はテーマUIを持たない＝theme は配線先が無い（束の契約は全面共通のまま保つ）。
                BookshelfSkyM(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = skyView::toggle,
                    highLoadSkyM = highLoadSkyM,
                    onHighLoadSkyChange = onHighLoadSkyChange,
                )
            }
        } else {
            ShelfFace.Listing { data, chrome, actions, _, selection, webActions, snackbarHostState ->
                BookshelfLogM(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    selection = selection,
                    webActions = webActions,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = skyView::toggle,
                    highLoadSkyM = highLoadSkyM,
                    onHighLoadSkyChange = onHighLoadSkyChange,
                )
            }
        }
    }
    Skin.CARTRIDGE_P -> {
        // ラック⇄一覧（旧 p_rack_view）。既定 true＝P 装着時はラックで開く。
        val rackView = rememberShelfViewToggle(PrefKeys.P_RACK_VIEW, default = true)
        if (rackView.value) {
            ShelfFace.Immersive { data, chrome, actions, theme, snackbarHostState ->
                BookshelfCartridgeP(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    theme = theme,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = rackView::toggle,
                )
            }
        } else {
            ShelfFace.Listing { data, chrome, actions, theme, selection, webActions, snackbarHostState ->
                BookshelfListCartridgeP(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    theme = theme,
                    selection = selection,
                    webActions = webActions,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = rackView::toggle,
                )
            }
        }
    }
    Skin.PORTAL_J -> {
        // デッキ⇄一覧（旧 j_deck_view）。既定 true＝J 装着時はデッキで開く。
        val deckView = rememberShelfViewToggle(PrefKeys.J_DECK_VIEW, default = true)
        if (deckView.value) {
            ShelfFace.Immersive { data, chrome, actions, theme, snackbarHostState ->
                BookshelfPortalJ(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    theme = theme,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = deckView::toggle,
                )
            }
        } else {
            ShelfFace.Listing { data, chrome, actions, theme, selection, webActions, snackbarHostState ->
                BookshelfGridJ(
                    data = data,
                    chrome = chrome,
                    actions = actions,
                    theme = theme,
                    selection = selection,
                    webActions = webActions,
                    snackbarHostState = snackbarHostState,
                    onToggleFace = deckView::toggle,
                )
            }
        }
    }
    Skin.MEIKAI_K -> {
        // 明快K は常に一覧面（グリッド⇄リストは K 自身が k_grid_view で所有＝面の切替ではない）。
        // theme は K の意匠上テーマUIが設定タブへ移管済みのため配線先が無い（M の没入面と同じ扱い）。
        ShelfFace.Listing { data, chrome, actions, _, selection, webActions, snackbarHostState ->
            BookshelfK(
                data = data,
                chrome = chrome,
                actions = actions,
                selection = selection,
                webActions = webActions,
                snackbarHostState = snackbarHostState,
                highLoadShioriK = highLoadShioriK,
            )
        }
    }
    // D/C は専用面を持たない＝受付（BookshelfContent）の共通描画（D 構造へトークン写像）が描く。
    Skin.WAMODERN_D, Skin.YAKO_C -> null
}
