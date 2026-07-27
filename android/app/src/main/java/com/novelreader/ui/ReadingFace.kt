package com.novelreader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.model.Ncode
import com.novelreader.viewmodel.NcodeSearchUiState

// ============================================================
// 読書層の引数束（2026-07-27 純構造リファクタ・本棚 ShelfFace.kt と同流儀）
//
// 読書層は route（[ChapterScreen]）→ 描画層（[ChapterScreenContent]）へ「同じ値の列がそのまま素通しする」
// 帯が5本（文字組・なろう紐付け・章ナビ・クローム state holder・継続導線）あり、両者の署名でその都度
// 全展開されていた。束にすることで署名を役割の粒度で読めるようにし、素通しの取り違えを型で防ぐ。
// 依存方向は従来どおり ui/ → skins/ → theme/ を保つ（この束は ui/ に置き skins/ を import しない
// ＝唯一の例外がテーマ束の再利用で、これは下記の理由で意図的）。
//
// 【束に既定値を付けない理由】旧シグネチャの既定値（chapterNumber=null・barsVisualReady=true・
// verticalMode=false・onVerticalModeChange={} 等）は「既存テスト・呼び出しの互換のため」に付いていたもので、
// 新しい呼び出し元が配線を忘れても無音で成立してしまう欠陥クラスだった（結線漏れが実機で沈黙死する）。
// 束のフィールドを全指定必須にすることで、配線忘れをコンパイルエラーへ格上げする（テスト側は実値を書く）。
//
// 【テーマ4択の束を新設しない理由】MainActivity が本棚と読書へ同一の単一正本（appTheme/followingSystem＝
// 2026-07-17 裁定②）を渡しているため、束も1つで足りる。既存の [com.novelreader.ui.skins.ThemeControl] を
// 読書層でもそのまま使い、同じ意味の型を二重に定義しない（定義位置も動かさない＝本棚側の参照を壊さない）。
// ============================================================

/**
 * 読書の文字組設定の束（文字サイズ・行間・左右余白・縦書き）。
 * 状態の正本は ReadingScreen（app_prefs の読み書き）で、route も描画層もここを読むだけ。
 *
 * なぜ値と「変更」「永続化」が3つ組で並ぶか: スライダーはドラッグ中に毎値 onXxxChange が発火する。
 * 状態更新は本文プレビューをリアルタイム追従させるため毎値必要だが、prefs 書き込みまで毎値行うと
 * 無駄なディスク I/O が連続する。永続化（onXxxPersist）は確定時（onValueChangeFinished）に一度だけ呼ぶ。
 *
 * @property onFontSizeChange ドラッグ中の毎値（本文プレビュー追従のため状態のみ更新・永続化しない）。
 * @property onFontSizePersist 永続化はスライダー確定時のみ呼ぶ（ドラッグ中の毎値書き込みを避ける）。
 * @property onLineHeightPersist フォントサイズと同型＝確定時に一度だけ永続化する。
 * @property onBodyMarginPersist フォントサイズと同型＝確定時に一度だけ永続化する。
 * @property verticalMode 縦書きモード（全書籍共通・app_prefs "reading_vertical"）。描画層はこの1値で
 *   本文スロットだけを VerticalChapterContent へ分岐する（item 構成・位置保存は横書きと同型）。
 * @property onVerticalModeChange 縦書きトグルの状態更新＋永続化（ReadingScreen が app_prefs へ書く）。
 *   トグルは1タップ＝1確定でドラッグ中の毎値発火が無いため、上の3つ組と違い persist を分けない。
 *   描画層はこれを直接シートへ渡さず、切替「前」に段落位置を捕捉するラッパ経由で呼ぶ（P5）。
 */
@Immutable
internal data class ReadingTypography(
    val fontSize: Int,
    val onFontSizeChange: (Int) -> Unit,
    val onFontSizePersist: () -> Unit,
    val lineHeightEm: Float,
    val onLineHeightChange: (Float) -> Unit,
    val onLineHeightPersist: () -> Unit,
    val bodyMarginDp: Int,
    val onBodyMarginChange: (Int) -> Unit,
    val onBodyMarginPersist: () -> Unit,
    val verticalMode: Boolean,
    val onVerticalModeChange: (Boolean) -> Unit,
)

/**
 * なろう作品との紐付け（PDF↔Web継続読書の前提）の束。
 * 候補検索の state は VM が持つ単一正本のスナップショット／検索・再試行は VM へ依頼するコールバック
 * （旧: 紐付けシートが NovelApiRepository を直接受け produceState で回していた依存注入漏れを解消済み）。
 *
 * @property bookTitle 蔵書タイトル。紐付けシートの初期検索語であり、スキンM の没入ゴースト題字も読む。
 * @property ncode 紐付け済みなろう作品の Nコード（null = 未紐付け＝継続導線が「静かな探索導線」へ分岐する）。
 * @property onLinkNcode 紐付けの確定／解除（null 渡しで解除）。書き込みは hot StateFlow 経由で
 *   [ncode] へ自動還流し、確定直後から継続導線が紐付け済み表示に切り替わる。
 */
@Immutable
internal data class NcodeLink(
    val bookTitle: String,
    val ncode: Ncode?,
    val ncodeSearchState: NcodeSearchUiState,
    val onSearchNcode: (query: String) -> Unit,
    val onRetryNcodeSearch: () -> Unit,
    val onLinkNcode: (Ncode?) -> Unit,
)

/**
 * 章ナビゲーションの束（隣章・活性条件・章位置・遷移コールバック）。route が tocEntries から算出する。
 *
 * @property prevFile 前章のファイル名。最初の章では "index.html"（目次）へ縮退する。
 * @property nextFile 次章のファイル名。最後の章では "index.html"（目次）へ縮退する。
 * @property navEnabled 目次ロード完了（tocEntries 非空）＝前後章ボタンの活性条件。未ロード中は
 *   currentIndex=-1 で prev/next が目次へ縮退するため、押せば必ず隣章を保証すべく disabled にする（公理2）。
 * @property isLastChapter 最終章か。継続導線の出現と読了検出のゲート（未ロード中は false＝出さない）。
 * @property chapterNumber スキンM（星図）の章扉・上端結線進捗、スキンP のセーブバー/チップの材料
 *   （ADR 0022 §1 の部品分岐）。null＝目次未ロード等で不明。M/P 以外のスキンでは未使用。
 * @property totalChapters 同上（総章数）。null または 0 以下なら進捗表示を出さない。
 * @property onNavigateTo 章/目次へ「進む」。"index.html" を渡すと目次を開く。
 * @property onNavigateToBookshelf 本棚へ直行（章パース失敗時のエラー画面が使う）。
 */
@Immutable
internal data class ChapterNav(
    val prevFile: String,
    val nextFile: String,
    val navEnabled: Boolean,
    val isLastChapter: Boolean,
    val chapterNumber: Int?,
    val totalChapters: Int?,
    val onNavigateTo: (String) -> Unit,
    val onNavigateToBookshelf: () -> Unit,
)

/**
 * 没入クローム（上下バー・本文スクロール）の state holder の束。
 *
 * なぜ route で生成して描画層へ渡すか: これらの holder は route が持つ副作用（没入入場の初期退避・
 * スクロール位置の debounce 保存／ON_STOP フラッシュ・没入ヒント・システムバー同期）と共有される。
 * 描画層はこの holder を読むだけ（＝route/Content 分割の純移動）。
 *
 * なぜ @Immutable でなく @Stable か: [lazyListState]/[topAppBarState]/[scrollBehavior] は同一インスタンスの
 * まま中身が後から変わる state holder＝「生成後一切不変」を意味する @Immutable を付けると嘘になり、
 * Compose の skip 判定が古いスクロール位置を正当化しかねない。変化は Snapshot 経由で観測可能・
 * equals は安定なので @Stable が正直な宣言（ShelfSelection と同じ判断）。
 *
 * @property barsVisualReady 初期退避（没入入場の実測待ち）が完了するまで false＝上下バーを alpha=0 で
 *   隠す（route が算出）。M3 の不変式（layout 高 = 実高 + heightOffset）に縛られ state 側で先に畳めないため、
 *   退避完了までの見た目は描画側の alpha ゲートで隠す。
 * @property showChromeHint 没入クローム復帰ヒント（アプリ通算初回の消灯時に数秒だけ出す一過性ラベル）。
 */
@Stable
@OptIn(ExperimentalMaterial3Api::class)
internal data class ReadingChrome(
    val lazyListState: LazyListState,
    val topAppBarState: TopAppBarState,
    val scrollBehavior: TopAppBarScrollBehavior,
    val barsVisualReady: Boolean,
    val showChromeHint: Boolean,
)

/**
 * 継続導線（最終章のみ出る継続カード）の束。
 *
 * @property continuationInfo なろう照会の結果。null＝未紐付け・照会中・照会失敗（オフライン）＝
 *   カードを静かに出さない（読書の没入を通信エラーで壊さない）。
 * @property onReadContinuation 「続きを読む」。Custom Tabs 起動は副作用のため route が再入ガード付きで実行し、
 *   描画層は「押された」ことだけを伝える。
 * @property onOpenWorkPage 「作品ページ」。同上（再入ガードは onReadContinuation と共有＝跨ぎ連打も抑える）。
 */
@Immutable
internal data class ContinuationCta(
    val continuationInfo: ContinuationInfo?,
    val onReadContinuation: () -> Unit,
    val onOpenWorkPage: () -> Unit,
)
