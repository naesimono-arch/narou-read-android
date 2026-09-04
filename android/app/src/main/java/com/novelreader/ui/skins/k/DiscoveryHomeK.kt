package com.novelreader.ui.skins.k

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.discovery.NovelListRow
import com.novelreader.ui.discovery.RankingSkeletonDescription
import com.novelreader.ui.discovery.RankingSkeletonRow
import com.novelreader.ui.discovery.RankingSkeletonRowCount
import com.novelreader.ui.theme.FontBody
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontListItemTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationKTabSwitch
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import java.time.LocalDate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

// ============================================================
// 明快K: さがす（発見ホーム）＝正本モック discovery-K.html の忠実翻訳（ADR 0022 §1 の構造分岐先）。
//
// 核（plan default-ui-clarity-K 確定事項5）: ①画面タイトル「さがす」を明示（タブと同語彙）
//   ②最上部に実検索フィールドを第一強調 ③きょうの気分→ジャンル→ランキング ④末尾に公式サイトへの逃げ道。
// D の淡色字間見出し（.28em）を廃し、セクション見出しは gothic bold ink で自己説明させる（モック .sec）。
//
// 色は D トークン土台（SkinK は SkinD へ委譲）: base→background・ink→onSurface・藍→primary・
//   line→outlineVariant・検索フィールド地→surfaceVariant（薄地）。メタ文字は AA の infoText（LocalShelfColors）。
//   モック --ink-soft #6A6E78（AA 引き上げ値）は Compose 側の正規 AA メタトークン infoText(#5C606D・より高
//   コントラスト)で受ける（K=D 字面/色の共有・ADR 0014-D）。字面はゴシック（既定）・気分見出し/順位数字/作品名=明朝。
//
// 期間タブ行は sticky（モック discovery-K-period-sticky-A.html＝2026-08-07 ユーザー裁定の候補A）。
//   ランキングを縦に読み進めても「いま何期間か／隣に何があるか」が行ごと見え続け、直接タップで切り替えられる。
//
// ボトムナビ（KBottomNav）はこの画面には含めない＝K 最上位3画面を束ねる上位シェルが搭載する（没入層と分離。
//   本画面は M/P/J 発見と同型の「発見コンテンツ全画面」を描く）。onBack は K では未使用（タブ画面＝戻る無し）。
// モーション: モックに keyframes/JS 無し＝静止で実装（M/P 発見と同じ扱い）。
// ============================================================

@OptIn(ExperimentalFoundationApi::class) // stickyHeader（期間タブ行の固定）。Compose foundation 1.8 では未安定 API
@Composable
internal fun DiscoveryHomeK(
    order: NarouOrder,
    state: DiscoveryUiState,
    onBack: () -> Unit, // K はタブ画面ゆえ戻るを持たない＝受けるが未使用（when 分岐を M/P/J と同型に保つため署名は共有）
    onOpenDetail: (ncode: Ncode) -> Unit,
    onOpenGenre: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    onOpenSearch: () -> Unit,
    onPickMood: (MoodPreset) -> Unit,
    onSelectOrder: (NarouOrder) -> Unit,
    onRefresh: () -> Unit,
    // きょうの気分ページャの初期組（日替わり）。既定＝端末日付から決定的に導出＝実アプリの挙動は不変。
    //
    // なぜ引数へ持ち上げるか（2026-07-30・state hoisting）: 旧実装は MoodSectionK の内部で
    // LocalDate.now() を直に呼んでおり、**Composable の中にテスト不能な依存（実時計）が埋まっていた**。
    // LocalDate.now() は JDK クラス＝Robolectric の shadow 対象外（差し替わるのは instrumented クラス内の
    // System.currentTimeMillis であって java.time の内部時計ではない）ため、テストからは日付を固定できず
    // 「この画面の絵は3日周期で変わる」＝スクリーンショット回帰を張れない状態だった。
    // 時計への依存を呼び出し側の境界（既定値）へ追い出し、画面本体は「与えられた組を描く純粋な関数」にする。
    //
    // remember の位置が「気分 item の中」から「画面」へ上がる副次効果（意図した改善・退行ではない）:
    // 旧実装の remember は LazyColumn の item 内にあり、気分ブロックを画面外へスクロールして戻すと
    // item ごと破棄・再生成されて日付が再導出されていた＝旧コメントの謳う「セッション中は固定」は
    // 実体としては item の寿命ぶんしか効いていなかった。画面スコープへ上げたことで、その記述どおり
    // 「表示中の画面を日付跨ぎで勝手に差し替えない／次回コンポジションから新しい日の組」になる。
    initialMoodPattern: MoodPattern = remember { MoodPattern.forEpochDay(LocalDate.now().toEpochDay()) },
) {
    // ── ランキングの期間スワイプ（2026-07-29 ユーザー指示「横スワイプで週間月間の遷移を」）──
    // 期間ページャ（1期間=1ページ）と期間タブは order（VM homeOrder）を単一情報源に同期する:
    //   タップ → onSelectOrder → order 変化 → 下の LaunchedEffect(order) がページをアニメ送り
    //   スワイプ → settledPage 確定 → onSelectOrder → order 追従（タブの選択表示はドラッグ中も
    //   currentPage 由来で先行追従）。双方向を order 経由に束ねることで発火ループを構造的に断つ
    //  （setHomeOrder は同値 no-op・ページ一致時は animate しない）。
    val rankingPagerState = rememberPagerState(
        initialPage = order.ordinal,
        pageCount = { NarouOrder.entries.size },
    )
    /**
     * order（VM）が先に変わったのに、送りが走っていてページがまだ追従できていない間だけ true。
     *
     * なぜ旗が要るか: 追従を「待たせる」だけでは症状は直らない。待っている最中に送りが据わると
     * `settledPage` 側が着地ページを order へ書き戻し、待っていた追従先＝ユーザーが選んだ期間が消えるため
     * ＝**待たせる**と**書き戻しを止める**の2つで1つの修正。
     */
    val followPending = remember { mutableStateOf(false) }
    // LaunchedEffect のキーは pagerState のみ＝order/onSelectOrder は寿命中に差し替わるため
    // rememberUpdatedState で常に最新を参照する（stale capture 防止・compose-side-effects 定石）。
    val currentOrder by rememberUpdatedState(order)
    val currentOnSelectOrder by rememberUpdatedState(onSelectOrder)
    LaunchedEffect(rankingPagerState) {
        // settledPage＝スナップ完了ページだけを拾う（ドラッグ中の中間値で VM を叩かない）。
        // drop(1): snapshotFlow は購読時に現在値を即時発行する。プロセス復元等で保存済みページと
        // order が食い違うケースで、初回発行が VM を過去ページへ引き戻すのを防ぎ、order 側を正として
        // ページの方を直す（直下の LaunchedEffect(order) が担当）。
        snapshotFlow { rankingPagerState.settledPage }.drop(1).collect { page ->
            // 追従待ちの間は書き戻さない（[followPending] の KDoc）。ここを止めないと、待っている最中に
            // 据わった「送りの着地ページ」が order を上書きし、ユーザーが選んだ期間が消える。
            if (followPending.value) return@collect
            val target = NarouOrder.entries[page]
            if (target != currentOrder) currentOnSelectOrder(target)
        }
    }
    LaunchedEffect(order) {
        // タブタップ（onSelectOrder 経由）や外部要因の order 変更にページを追従アニメさせる。
        // 尺はタブ切替の既存スロット MotionDurationKTabSwitch を踏襲＝新値を発明しない（監督裁定）。
        //
        // ⚠️ 送りが走っていても「見送って終わり」にしてはいけない（2026-09-05 実機で確定した無反応の真因）。
        // 旧実装は `!isScrollInProgress` を条件に含めて早期に諦めており、再試行の口が無かった。
        // フリング/スナップの尾（据わった直後もこの旗は暫く true）にタブタップが入ると追従が丸ごと捨てられ、
        // order（VM）とページャが食い違ったまま固定される。選択下線も行の内容もページャ現在地由来なので
        // 画面は1pxも動かず、しかも setHomeOrder は同値 no-op なので**同じタブをもう一度叩いても復帰できない**
        //（＝実機報告「2回とも無反応」の形。実測ログ: ORDER=DAILY currentPage=5 scrolling=true → SKIP）。
        // ⇒ 諦めるのでなく据わるのを待ってから送る。判断そのものは [rankingFollowAction]（純関数）が持つ。
        try {
            while (true) {
                val action = rankingFollowAction(
                    currentPage = rankingPagerState.currentPage,
                    orderPage = order.ordinal,
                    scrollInProgress = rankingPagerState.isScrollInProgress,
                )
                if (action == RankingFollowAction.AlreadyThere) break
                if (action == RankingFollowAction.Follow) {
                    rankingPagerState.animateScrollToPage(
                        order.ordinal,
                        animationSpec = tween(MotionDurationKTabSwitch),
                    )
                    break
                }
                // AwaitSettle: 据わるまで待つ。待つ意思を旗で示す＝この間の書き戻しを止める（上の collect）。
                followPending.value = true
                snapshotFlow { rankingPagerState.isScrollInProgress }.first { !it }
            }
        } finally {
            // 指のドラッグ（MutatePriority.UserInput）に追従アニメが取り消された場合も、order がさらに
            // 変わってこの側効果ごと作り直された場合もここを通る＝旗を残さない。
            // 残すとスワイプの着地が二度と order へ書き戻らなくなる（症状が入れ替わるだけになる）。
            followPending.value = false
        }
    }
    // 期間別 stale-while-revalidate: 期間ごとの直近 Content を控え、再訪ページは再取得(Loading)中も
    // 行の骨格を出し続けてスクロールアンカーを保つ。旧実装の単一控え（期間跨ぎ流用）はページャ化で
    // 誤誘導になる（週間の行が月間ページに載る）ため期間別へ分割した。
    val rankingContents = remember { mutableStateMapOf<NarouOrder, DiscoveryUiState.Content>() }
    LaunchedEffect(state, order) {
        // 合成中の書き戻しを避け側効果で控える（旧 RankingStaleRows と同じ理由）。order 切替直後の
        // 1フレームは旧期間の Content が新 order 名義で届き得る（order と state が別 flow で到着するため）が、
        // 直後の Loading→新 Content で上書きされ、Error/Empty 時は描画分岐が status を優先するため実害は閉じる。
        (state as? DiscoveryUiState.Content)?.let { rankingContents[order] = it }
    }
    // ランキング領域の横ジェスチャ封止: 子（期間ページャ／期間タブの横スクロール）が消費し切れない
    // 横成分をこの層で全量消費し、外側タブ Pager（本棚⇄さがす⇄設定）へ渡さない。なぜ: Compose の
    // 入れ子スクロールは余りを親へ伝播させる既定で、端ページ（日間/新着）でさらに引くとアプリの
    // タブごと切り替わる誤操作になる（2026-07-29 監督裁定＝ランキング上の横ジェスチャは期間移動専用に
    // 閉じる。実機体感で異論が出たら差し戻す前提）。副作用: 余りを食うため端の stretch overscroll は
    // 出ない（端では静止）。縦(y)は素通し＝ホーム全体の縦スクロールを妨げない。
    val rankingEdgeSeal = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    // ── 横スワイプの土台（2026-08-06 平坦化）──
    // 指のドラッグ・フリング・スナップは従来どおり PagerState に載せる（操作感を自前実装で作り直さない）。
    // ページャ本体は行を持たなくなるため、この FlingBehavior を各行スロットの scrollable へ配って
    // 「どの行を触っても同じ1つのページ送りが進む」形にする。
    val rankingFling = PagerDefaults.flingBehavior(state = rankingPagerState)
    // 送りの最中は行のタップ導線を殺す（2026-08-26・ページ送りアニメのコストの真因対処）。
    //
    // 旧実装は同じ目的を「覗き行には onOpenDetail / onRefresh を配線しない」という**役割による分岐**で
    // 果たしていた。しかしこの分岐こそが横スワイプの重さの主因だった——据わりと覗きは送りが半ページを
    // 越えた瞬間に**役割だけが入れ替わる**（前後どちらも同じ2期間が居る）ので、行へ渡す値が役割で変わると
    // 入れ替わりの1フレームで据わり・覗きの**両方が全数再合成**される。実測（エミュ・可視6行）で
    // その1フレームだけ StaticLayout 構築が 96 個・UI スレッド 50〜54ms に跳ねていた。
    //
    // ＝判定を「合成の時点でどちらの役割か」から「クリックの時点でページャが動いているか」へ移す。
    // 行へ渡す値が役割から独立するので、役割の入れ替わりが composition にとって無変化になる。
    // 意図は元の裁定（見えているだけの行から詳細へ飛ばさない）より**厳しくなる**方向＝送りが走っている間は
    // 据わり側の行も飛ばない（指を離した直後のスナップ中に誤タップで別作品を開く事故も併せて塞がる）。
    val currentOnOpenDetail by rememberUpdatedState(onOpenDetail)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val rankingOpenDetail = remember(rankingPagerState) {
        { ncode: Ncode -> if (!rankingPagerState.isScrollInProgress) currentOnOpenDetail(ncode) }
    }
    val rankingRefresh = remember(rankingPagerState) {
        { if (!rankingPagerState.isScrollInProgress) currentOnRefresh() }
    }
    // 行スロットに常駐させる期間＝**据わり（settledPage）とその両隣**（2026-09-02・ジャンク要因 A の真因対処）。
    //
    // 旧実装は覗きを「ドラッグ中だけ」合成していた（`isScrollInProgress` が false の間は null、動き出したら
    // `currentPageOffsetFraction` の符号で隣を1つ選ぶ）。この形は**指が動き出した最初のフレーム**で
    // null→非null に変わる＝**可視行ぶんの覗き行が丸ごと新規合成**される。実測（エミュ・可視6行）で
    // StaticLayout 42個・measure 25〜40ms の重いフレームが1フリックに必ず1枚あり、
    // 08-26／09-02 で B（半ページ地点）を潰した後は**これが唯一残る山**だった
    //（`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`）。
    //
    // ⇒ 窓を **`settledPage` を軸に ±1**（1フリックで到達し得る範囲＝PagerSnapDistance 既定）へ移す。
    // `settledPage` は送りの最中は動かない（据わったときだけ動く）ので、ドラッグ〜フリング〜スナップの
    // 全フレームで「どの枠にどの期間が入るか」が 1 ビットも動かない＝新規合成はドラッグの外
    //（据わった後の静止フレーム）へ移り、しかも1回の送りで新しく合成されるのは**新たに窓へ入る1面だけ**。
    // 覗きが要らない静止時も面を持ち続けるのは意図どおりで、見た目は変わらない（自分のページ番号の座席へ
    // translationX で置かれ、行スロットの `clipToBounds` が枠の外を切る）。
    //
    // `currentPage` も窓に必ず含める: 期間タブのタップは `animateScrollToPage` で複数ページを跨ぐことが
    // あり、その最中は `currentPage` だけが途中ページを指す（`settledPage` は終わるまで据え置き）。
    // 据わりの面が窓から漏れると行の高さが 0 に落ち、一覧全体が縮んで先頭へクランプする。
    //
    // 値でなく取得関数として下へ渡す理由（旧実装から継承）: この画面本体で読むと窓が動くたび画面全体が
    // 再コンポーズされるため、読む位置を LazyColumn の item スコープ（＝可視行だけ）まで下げる。
    val residentOrdersState = remember(rankingPagerState) {
        derivedStateOf {
            rankingResidentOrders(rankingPagerState.settledPage, rankingPagerState.currentPage)
        }
    }
    // 画面の「据わる位置」に居る期間＝ページャが指しているページ（2026-08-07）。
    // なぜ order（VM）でなくこちらを描画の基準にするか: この2つは常に一致しない。ページャは指を離した瞬間
    // ——正確には送り量が半ページを越えた瞬間——に次ページを指すのに対し、order が追いつくのは
    // settledPage→onSelectOrder→homeOrder(StateFlow) の往復が終わってから。据わり位置の中身を order で
    // 決めると、その遅れのあいだ「もう次の期間に居るのに前の期間の行が据わっている」状態を描いてしまい、
    // 追従した瞬間に文字だけが差し替わる＝2026-08-07 報告「スワイプの後、一瞬スワイプ前の文字列が出る」。
    // 位置（translationX）はページャ基準・中身は order 基準、という食い違いが症状の実体なので、
    // **中身の期間もページャ基準に揃える**（＝ページ1枚1枚が自分の期間を描いていた平坦化前の性質の回復）。
    val pagerOrder = NarouOrder.entries[rankingPagerState.currentPage]
    // 横向きだけ構造が変わる（ADR 0034＝T1 横一列化 × Rail）。判定は既存流儀の LocalConfiguration.orientation。
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val railSelect = LocalKTabSelect.current
    val railActive = isLandscape && railSelect != null
    // Rail は本文の左隣＝Row で受ける（縦向き・未結線では子が本文1つだけ＝従来の Column 単独と同値）。
    Row(modifier = Modifier.fillMaxSize()) {
        if (railActive) {
            KNavigationRail(
                current = KTab.DISCOVER,
                onSelect = railSelect!!,
                // T1: 題字「さがす」の移設先＝Rail ヘッダ（画面名を消さずに縦の固定分から外す）。
                // 本棚と違い冊数のような従属メタは無いので題だけ。FAB もこの面には無い。
                header = { KRailHeader(title = "さがす") },
            )
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .then(
                // 横向きは下端の帯（KBottomNav）が消える＝本文が nav インセットを引き受ける
                //（左端ぶんは Rail が持つので End+Bottom だけ＝二重加算しない）。
                if (railActive) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (railActive) {
            // ── T1 横一列化（ADR 0034 意匠裁定）──
            // 縦に積んでいた〈題字／検索バー／期間タブ〉を横1行へ。題字は Rail ヘッダへ抜け、
            // 残る2つが同じ行に並ぶ（固定トップ 120.5dp → 1行）。
            //
            // ⚠️ 幅が足りなくなったときの吸い先は **期間タブの既存 `horizontalScroll`**（裁定＝案E・新機構ゼロ）。
            // だから検索欄は**中身なりの幅**（要求幅）で置き、期間タブに weight(1f)＝残り全部を渡す:
            //  ・収まる条件（fontScale 1.0/1.15）では6本とも見えたまま
            //  ・溢れる条件（1.3 以上）では期間タブ側の可視域が縮み、行が横スクロールになって吸う
            //    ＝検索欄の文言が省略される・題字が消える等の「情報が減る」壊れ方をしない。
            // 逆に期間タブを固定幅・検索欄を weight にすると、溢れが検索プレースホルダの省略として出る
            //（＝正本の16文字が読めなくなる）ので採らない。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DiscoveryListHorizontalMargin,
                        end = DiscoveryListHorizontalMargin,
                        top = Spacing.S4,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchFieldK(onOpenSearch)
                Spacer(Modifier.width(Spacing.S16)) // モック導出の検索⇄タブ間（800−80−24×2−320−336＝16dp）
                OrderTabsK(
                    selected = pagerOrder,
                    onSelectOrder = onSelectOrder,
                    // 横ジェスチャ封止は sticky 版と同じ（タブ列を撫でてアプリのタブが変わる誤操作の防止）。
                    modifier = Modifier.weight(1f).nestedScroll(rankingEdgeSeal),
                )
            }
        } else {
            // 固定トップ（モック .top）: 画面タイトル＋実検索フィールド（常時可視・第一強調）。
            SearchHeaderK(onOpenSearch)
        }

        // 期間ページャの「本体」＝高さ0・中身空のアンカー（2026-08-06 平坦化）。
        // なぜ何も描かないページャを置くか: PagerState はスクロール量→ページ位置の換算に
        // layoutInfo.pageSize を必要とし、それを供給できるのは Pager の measure だけ。行を平坦化して
        // ページャから中身を抜いた後も、ページ送り・スナップ・currentPage/settledPage の意味を標準実装の
        // まま保つため、状態供給専用のページャとして残す。
        // なぜ LazyColumn の**外**に置くか: item として置くと縦スクロールで画面外へ出た瞬間に measure されなく
        // なり、PagerState が凍って横スワイプが死ぬ（ランキングを見ながら上へスクロールしただけで期間送りが
        // 効かなくなる）。高さ0ゆえレイアウト・意匠への影響は無い。
        // 縦リストと同じ左右マージンを渡す＝ページ寸法を行の実幅に揃える（[DiscoveryListHorizontalMargin]）。
        RankingPagerAnchorK(rankingPagerState, DiscoveryListHorizontalMargin)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            // .scroll padding:6px 20px 20px → 横 S24（D 発見の横マージンと同じ）・下 S24。
            contentPadding = PaddingValues(
                start = DiscoveryListHorizontalMargin,
                end = DiscoveryListHorizontalMargin,
                bottom = Spacing.S24,
            ),
        ) {
            item { MoodSectionK(onPickMood, initialMoodPattern) }
            item { GenreSectionK(onOpenGenre, onPickBiggenre) }
            item { SectionHeadingK("ランキング") }
            // 期間タブ行だけを scrollport 上端へ貼り付ける（モック A の `.rtabs{position:sticky;top:0}`）。
            // 見出し「ランキング」は貼り付けない＝モック A で sticky なのは .rtabs のみ。
            //
            // 順位行は平坦化で同階層の item 群になっているので（[rankingSectionK]）、ここを stickyHeader へ
            // 替えるだけで「タブが残り、その下を順位行が流れる」関係が成立する＝行側は無改変。
            // z-index はモック A が明示している（気分カードの藍ルール等 positioned 要素対策）が、Compose の
            // stickyHeader は貼り付き中のヘッダを他 item より前面へ置くのが既定＝翻訳先で足すものは無い。
            // 横向き（T1）では期間タブは固定トップの横1行へ移っている＝ここには置かない
            //（置くと同じ行が2つ出る。T1 は「この行自体が pinned」＝sticky の約束〈現在地が常に見える〉は
            //  固定トップに居ることで満たされる）。
            if (!railActive) {
            stickyHeader {
                OrderTabsK(
                    // 選択表示は order でなくページャ現在地に従える＝スワイプのドラッグ中から追従する
                    //（settle 前の中間状態でもタブが今向かっている期間を指す）。
                    selected = pagerOrder,
                    onSelectOrder = onSelectOrder,
                    modifier = Modifier
                        // 貼り付き中に下を潜る順位行が透けないための地色（モック A の background:var(--base)）。
                        // padding より**先**に置く＝下の 8dp ぶんも地で覆う（CSS の padding が背景の内側に
                        // 入るのと同じ関係。順を逆にすると 8dp が透けて行が覗く）。
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = Spacing.S8) // モック A の .rtabs padding-top:8px（貼り付き時の呼吸）
                        .nestedScroll(rankingEdgeSeal),
                )
            }
            }
            // ランキングの行は**外側 LazyColumn の item へ平坦化**する（2026-08-06）。
            // 旧実装は1ページ＝取得件数ぶんの素の Column を単一 item として抱いており、LazyColumn の
            // 間引き粒度が item である以上、画面外の行まで全数が合成・記録されていた（＝jank の真因）。
            rankingSectionK(
                pagerState = rankingPagerState,
                flingBehavior = rankingFling,
                edgeSeal = rankingEdgeSeal,
                pageOrder = pagerOrder,
                selectedOrder = order,
                residentOrders = { residentOrdersState.value },
                state = state,
                contents = rankingContents,
                // 送り中を殺した版を渡す＝行へ渡す値が「据わりか覗きか」に依存しなくなる（上の rankingOpenDetail）。
                onOpenDetail = rankingOpenDetail,
                onRefresh = rankingRefresh,
            )
            item { OfficialLinkK() }
        }
    } // Column（本文）
    } // Row（Rail ＋ 本文）
}

/** 固定トップ: h1「さがす」＋実検索フィールド（タップで検索画面へ）。モック .top / .search。 */
@Composable
private fun SearchHeaderK(onOpenSearch: () -> Unit) {
    Column(
        // .top padding:2px 20px 14px → 上 S4 / 横 S24 / 下 S16。
        modifier = Modifier.padding(start = Spacing.S24, end = Spacing.S24, top = Spacing.S4, bottom = Spacing.S16),
    ) {
        Text(
            "さがす",
            // タブと同語彙の画面タイトル（モック h1 22px ゴシック bold）＝22sp の M3 titleLarge を bold 化。
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SearchFieldK(
            onOpenSearch,
            // .search margin-top 14px → S16／縦向きは1行まるごと使う
            modifier = Modifier.padding(top = Spacing.S16).fillMaxWidth(),
        )
    }
}

/**
 * 実検索フィールド（モック .search）。縦向きは固定トップの中で全幅・横向き（T1）は横1行の左半分に
 * **中身なりの幅**で置く＝どちらも同じ実装（置き方だけ [modifier] で変える）。
 *
 * 幅を [modifier] 任せにしているのが T1 の要点: `fillMaxWidth` を内側に固定してしまうと、横1行に並べたとき
 * 検索欄が行を食い尽くして期間タブが 0 幅になる（＝超過を E＝期間タブの横スクロールへ吸わせられない）。
 */
@Composable
private fun SearchFieldK(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .height(52.dp)             // .search 52px 固定（高さ＝構造値・スケール外）
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // 薄地の沈めた面（--field）
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = Spacing.S16), // .search padding 0 16px → 横 S16
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null, // 隣接プレースホルダ文が読み上げを担う
                tint = LocalShelfColors.current.infoText,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "作品名・作者名・キーワードで探す",
                fontSize = FontBody, // .search span 14px（＝検索入力欄の字面トークン）
                color = LocalShelfColors.current.infoText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.S12), // .search gap 10px → S12
            )
        }
}

/** セクション見出し（モック .sec）: 淡色字間装飾でなく gothic bold ink＝自己説明性優先。 */
@Composable
private fun SectionHeadingK(text: String, topSpace: androidx.compose.ui.unit.Dp = Spacing.S24) {
    Text(
        text,
        fontSize = FontSubTitle, // .sec 13px
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        // .sec margin 20px 0 12px（先頭の きょうの気分 のみ上 8px＝呼び出し側で topSpace を S8 に）。
        modifier = Modifier.padding(top = topSpace, bottom = Spacing.S12),
    )
}

/**
 * きょうの気分（モック .mood → 2026-07-24 ページャ化・正本 discovery-K.html）: 4件1組×[MoodPattern] 3組を
 * 横スワイプで行き来し、初期表示の組だけが日替わり（決定的＝MoodPattern.forEpochDay）。
 * 可視代替の義務（隠しスワイプ禁止）＝下のドットインジケータと日替わり注記が「他の組がある」ことを常時可視化する。
 * 2026-07-26 循環化: 端で止まらず右端→先頭・左端→末尾へ続く（仮想大カウント＋剰余写像。意匠・寸法は不変）。
 *
 * [todayPattern] は呼び出し側（[DiscoveryHomeK] の既定引数）が解決済みの初期組。
 * ここで LocalDate.now() を呼ばない理由＝実時計への依存を画面の境界へ追い出すため（[DiscoveryHomeK] の
 * initialMoodPattern のコメントが正本）。この関数は「与えられた組から描く」だけの決定的な描画に徹する。
 */
@Composable
private fun MoodSectionK(onPickMood: (MoodPreset) -> Unit, todayPattern: MoodPattern) {
    val moodPagerState = rememberPagerState(
        // 循環スワイプ: Pager にネイティブ循環が無いため仮想大カウント＋剰余写像で実現。
        // 中央帯から開始（loopInitialPage）＝初日組を保ったまま左右どちらへも実用上無限にスワイプできる。
        initialPage = MoodPattern.loopInitialPage(todayPattern),
        pageCount = { MoodPattern.LOOP_PAGE_COUNT },
    )
    Column {
        SectionHeadingK("きょうの気分", topSpace = Spacing.S8) // .sec:first-child margin-top 8px
        // 高さの安定枠（2026-07-29 実機報告「表示が2行を超えると下の描画ががくんと動く」の真因対処）:
        // Pager は wrap-content＝表示中ページの高さへ都度スナップする一方、ページ高は文言の折返し行数
        //（端末幅・フォントスケール依存）で組ごとに違う→組の切替や日替わり初期組のたび下部が段差で動く。
        // 正本モック discovery-K.html は .mp-track（flex・align-items 既定 stretch）で「全ページ＝最高
        // ページと同高」を構造で規定している。その翻訳として全3組の格子を不可視・操作不可で重ね、
        // 枠高＝最大組高をその場の実測で予約する（幅・フォントスケールに追従＝固定 dp の発明をしない）。
        Box {
            MoodPattern.entries.forEach { pattern ->
                MoodPageGridK(
                    pattern = pattern,
                    onPickMood = null, // 計測専用ゴースト＝タップ配線なし（短いページの下で誤タップさせない）
                    modifier = Modifier
                        // Pager の contentPadding(end=S24) と同幅に合わせ、折返し行数の計算を実ページと一致させる。
                        .padding(end = Spacing.S24)
                        .alpha(0f)
                        // 不可視の計測専用ゆえ TalkBack へ幻のカード群を読ませない。
                        .clearAndSetSemantics {},
                )
            }
            HorizontalPager(
                state = moodPagerState,
                // 枠いっぱいに伸ばす＋上詰め＝モック .mp-track（flex の既定 align-items:stretch）の翻訳。
                // 2026-08-20 実機報告「スワイプしていくと枠内で上下にがくがくと動く」の真因対処:
                //  ・Pager の高さは wrap-content＝**そのとき viewport に居るページ**の最大高になる。覗き見せ
                //    （contentPadding end）があるので常に隣のページも measure され、ドラッグ中は3組が同時に居る
                //    瞬間もある→ Pager 自身の高さが送りのたびに変わる。
                //  ・そのうえ HorizontalPager の verticalAlignment 既定は CenterVertically＝各ページはその
                //    可変高の中で毎回センタリングし直される。左右の送りが縦の移動に化ける正体がこれ。
                // 枠（安定枠 Box）は既にゴーストで最大組高に固定済みなので、Pager をその枠へ matchParentSize で
                // 伸ばせば高さは送りと無関係な定数になり、Top 揃えでページ内容の縦位置も定数になる。
                modifier = Modifier.matchParentSize(),
                verticalAlignment = Alignment.Top,
                // 右端に次ページの頭を覗かせる＝「まだ横にある」のシグニファイア（モックの左右覗きの Compose 翻訳）。
                contentPadding = PaddingValues(end = Spacing.S24),
                pageSpacing = Spacing.S12,
            ) { page ->
                // 仮想ページ→実3組の剰余写像（循環）。
                MoodPageGridK(pattern = MoodPattern.forPage(page), onPickMood = onPickMood)
            }
        }
        // ドットインジケータ（モック .dots）＝現在組を可視化。寸法は構造値ゆえスケール外の raw dp。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.S4, bottom = Spacing.S4),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 仮想ページを論理組へ戻してから照合＝ドットは従来どおり実3組を指す（循環化でも見た目不変）。
            val logicalPage = MoodPattern.forPage(moodPagerState.currentPage).ordinal
            MoodPattern.entries.forEachIndexed { i, _ ->
                val active = i == logicalPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = Spacing.S4)
                        // 現在組は横長ピル＝方向のあるインジケータ（Material の pager 慣習）。
                        .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        // 日替わり注記（モックの1行）: 初期組が日で変わることの自己説明。
        Text(
            "日替わり・きょうは「${todayPattern.displayName}」から",
            fontSize = FontLabel,
            color = LocalShelfColors.current.infoText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 1組4プリセットの2列格子（親が LazyColumn ゆえ LazyGrid をネストせず chunked(2) の Row で組む）。
 * [onPickMood] null＝高さ計測専用ゴースト（MoodSectionK の安定枠）としてタップを配線しない。
 */
@Composable
private fun MoodPageGridK(
    pattern: MoodPattern,
    onPickMood: ((MoodPreset) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        pattern.presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S12),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12), // .mood gap 12px
            ) {
                rowPresets.forEach { preset ->
                    MoodCardK(
                        preset,
                        onClick = onPickMood?.let { pick -> { pick(preset) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodCardK(preset: MoodPreset, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            // onClick null＝計測専用ゴースト。clickable を積まない＝クリック・フォーカスの標的にしない。
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.S12), // .md padding 14px → 縦 S12
    ) {
        // 左の藍ルール（モック .md::before＝3px 藍の縦帯）。高さ・幅は構造値ゆえスケール外の raw dp。
        Box(
            modifier = Modifier
                .padding(top = Spacing.S4)
                .width(3.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(start = Spacing.S12, end = Spacing.S8)) {
            Text(
                preset.title,
                fontFamily = MinchoFamily,
                fontSize = FontListItemTitle, // .md b 14.5px（明朝）
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                preset.cardLabel,
                fontSize = FontLabel, // .md span 11px
                color = LocalShelfColors.current.infoText,
                modifier = Modifier.padding(top = Spacing.S4),
            )
        }
    }
}

/** ジャンルから（モック .chips）: 大ジャンルの横スクロールチップ＋末尾「すべて→」（ジャンル一覧入口）。 */
@Composable
private fun GenreSectionK(onOpenGenre: () -> Unit, onPickBiggenre: (code: Int, label: String) -> Unit) {
    Column {
        SectionHeadingK("ジャンルから")
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8), // .chips gap 8px
        ) {
            // key＝大ジャンルコード（安定・全件で一意）。key 無しだと Lazy の既定＝位置キーになり、
            // 将来この列の並びが変わったとき再利用が位置に貼り付いて別ジャンルへ状態が付いて回る。
            items(NarouGenres.BIGGENRES, key = { it.first }) { (code, label) ->
                GenreChipK(label, accent = false, onClick = { onPickBiggenre(code, label) })
            }
            // 「すべて→」＝ジャンル一覧入口（D の「すべて →」に相当・藍枠藍字）。
            item { GenreChipK("すべて→", accent = true, onClick = onOpenGenre) }
        }
    }
}

@Composable
private fun GenreChipK(label: String, accent: Boolean, onClick: () -> Unit) {
    // accent=true（すべて→）は藍枠・藍字、通常チップは line 枠・ink 字（モック .chip / .chip.all）。
    val borderColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        fontSize = FontButtonLabel, // .chip 12.5px
        color = textColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .chip padding 8px 16px
    )
}

/**
 * ランキングの期間タブ（モック .rtabs）: 選択タブは藍 bold＋2dp 下線、列全体の下端にヘアライン。
 * [selected] は呼び出し側がページャ現在地から導出する（期間スワイプ連動・2026-07-29）。
 * [modifier] で横ジェスチャ封止（rankingEdgeSeal）と、sticky 化に伴う地色・上余白を受ける＝タブ列上の
 * 横スワイプの余りも外側タブ Pager へ渡さない（期間タブを撫でたらアプリのタブが変わる誤操作の防止）。
 *
 * タブは6本（[NarouOrder] 全数・entries 順）＝画面幅に収まらないのでこの行自体が横スクロールを持つ
 *（`horizontalScroll`）。sticky 化してもその性質は変わらず、貼り付いたまま行内を横に繰って隣の期間を出せる。
 * 正本モック側は 3本のままだった（かつ冒頭コメントは「6本」と書いて自己矛盾していた）ため、
 * 2026-08-14 に実装を一次ソースとして discovery-K.html を6本＋横スクロールへ逆同期済み。
 * その横スクロール域から選択タブが出ないよう位置を追従させる（2026-08-14 裁定・下の側効果と
 * [orderTabFollowTarget]）＝A 案の「現在地が常に見える」を溢れ条件でも保つ。
 */
@Composable
private fun OrderTabsK(
    selected: NarouOrder,
    onSelectOrder: (NarouOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val gapPx = with(LocalDensity.current) { OrderTabGap.roundToPx() }
    // 追従の計算に要る実測2つ。可視域＝この行自身の幅／タブ幅＝並びの中での選択タブの位置を出すため
    // （Row の子は溝込みで先頭から詰まるので、幅の積算だけで左端が決まる）。
    val viewportWidthPx = remember { mutableStateOf(0) }
    val tabWidthsPx = remember { mutableStateListOf(*Array(NarouOrder.entries.size) { 0 }) }
    // 側効果のキーに使う寸法（合成で読む2値）。値が動くのはレイアウト寸法が変わったときだけなので、
    // スクロールやページ送りのたびに再合成が走ることはない。
    //  ・可視域幅: 画面リサイズ・分割画面・回転で変わる
    //  ・タブ幅の合計: フォントサイズ変更で変わる（この行は fillMaxWidth＝**可視域は変わらず中身が伸びる**。
    //    ここをキーに入れないと、activity 再生成を伴わない fontScale 変更で溢れが始まっても取りこぼす）
    val totalTabWidthPx = tabWidthsPx.sum()
    // 追従を一度でも評価したか（初回だけ瞬間移動にするための記録＝下の側効果のコメント）。
    val followedOnce = remember { mutableStateOf(false) }

    // ── 選択タブ追従（2026-08-14 ユーザー裁定「A 案のまま穴を塞ぐ」＝ADR 0033 決定3）──
    // 何を防ぐか: タブは6本あり、溢れ条件（fontScale 1.3 以上・幅 360dp）では行が画面幅に収まらないため、
    // 選択中の期間タブが横スクロール域の外＝画面外へ出てしまう。sticky 化（2026-08-07 裁定 A 案）が
    // 約束したのは「ランキングを読み進めても現在地が常に見える」ことなので、これが破れると裁定の狙いごと
    // 失われる。しかも期間は**行の横スワイプでも変わる**＝タブに触れずに現在地が動くため、
    // ユーザー側には見えていないことに気付く手掛かりが無い。
    //
    // 発火は「選択が変わったとき」と「寸法が変わったとき」だけ＝スクロール位置の変化では動かない。
    // これが手動横スクロールとの棲み分けそのもの: 指で選択タブを画面外へ送ったならそれはユーザーの意思で、
    // 次に期間が変わるまで引き戻さない（＝毎フレーム可視域へ引き戻す実装にはしない）。
    //
    // ⚠️ ここで `isScrollInProgress` による早期 return を置いてはいけない（2026-08-14 レビュー指摘で撤去）:
    // この旗は**誰が動かしているか**を区別せず、自分の追従アニメ（150ms）でも真になる。置くと期間を速く
    // 連続で送ったときに次の追従が丸ごと skip され、選択タブが画面外に残る＝塞いだはずの穴が同じ形で開く。
    // 指との競合は旗を見なくても構造的に解決している——ドラッグは MutatePriority.UserInput で、
    // こちらの追従（Default）を取り消して指が勝つ（`horizontalScroll` は interactionSource を受け取らず、
    // スクロールの由来を外から見分ける公開 API も無い＝旗で区別する術が無いという意味でも置く価値が無い）。
    LaunchedEffect(selected, viewportWidthPx.value, totalTabWidthPx) {
        // 初回コンポーズでは側効果がレイアウトより先に走る＝実測が入るまで待つ（0 のまま計算すると空振り）。
        snapshotFlow { viewportWidthPx.value > 0 && tabWidthsPx.all { it > 0 } }.first { it }
        val target = orderTabFollowTarget(
            tabWidths = tabWidthsPx,
            selectedIndex = selected.ordinal,
            gapPx = gapPx,
            scroll = scrollState.value,
            viewportWidth = viewportWidthPx.value,
            maxScroll = scrollState.maxValue,
        )
        val isFirstFollow = !followedOnce.value
        followedOnce.value = true
        if (target == null) return@LaunchedEffect // 収まっている＝動かさない（毎回のスクロールし直しで揺らさない）
        // 初回（画面に出た時点）は瞬間移動＝誰も触っていないのに行が流れるのは初見の混乱になる。
        // 2回目以降は期間切替に伴う移動なので、ページ送りと同じ尺で滑らせて対応関係を見せる。
        // アニメ中にユーザーがこの行を掴んだら、掴んだ側（UserInput）が優先されてこのアニメは取り消される
        // ＝指と綱引きにならない（取り消しは CancellationException でこの側効果のコルーチンに閉じる）。
        if (isFirstFollow) scrollState.scrollTo(target)
        else scrollState.animateScrollTo(target, animationSpec = tween(MotionDurationKTabSwitch))
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 可視域の実測。horizontalScroll より**前**に置く＝内側に置くと中身の全幅（無限幅で測った
                // 6本ぶん）を拾ってしまい、溢れているかどうかの判定が常に「収まっている」になる。
                .onSizeChanged { viewportWidthPx.value = it.width }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(OrderTabGap),
        ) {
            NarouOrder.entries.forEach { o ->
                val isSelected = o == selected
                Column(
                    modifier = Modifier.onSizeChanged { tabWidthsPx[o.ordinal] = it.width },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        o.uiLabel,
                        fontSize = FontSubTitle, // .rtab 13px
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        // 未選択タブも意味を運ぶ文字＝infoText（AA・ADR 0014-D）。選択は藍（primary）据え置き。
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else LocalShelfColors.current.infoText,
                        modifier = Modifier
                            // 現在期間を支援技術へも伝える（選択表示が色/太字の視覚だけに閉じないように。
                            // ページャ連動テストの観測点も兼ねる）。
                            .semantics { this.selected = isSelected }
                            .clickable { onSelectOrder(o) }
                            .padding(vertical = Spacing.S8), // .rtab padding 10px 0 → 縦 S8
                    )
                    // 選択下線（モック .rtab.on::after＝藍 2px）。未選択は透明で高さを揃える。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) // .rtabs border-bottom 1px
    }
}

/**
 * 期間ページャの追従判断（[DiscoveryHomeK] の `LaunchedEffect(order)` の中身）。
 *
 * **なぜ純関数へ切り出すか**: この判断の穴（送り進行中に来た order 変更を捨てる）は実機でしか現れず、
 * 画面ごとの試験では守れない——Compose のテスト入力注入（`performClick`/`performTouchInput`）は
 * 注入前に waitForIdle を通すため、**送りが据わる前にタップを届かせることが JVM では原理的にできない**
 *（2026-09-05 実測: `performClick` も order 直書きも、フリングが据わってからしか届かず競合窓を作れない。
 *  実機では `adb` のログ計測で `ORDER=… scrolling=true → SKIP` を直接観測して確定させた）。
 * ⇒ 画面試験に頼れない以上、**判断だけを機械で固定する**（[orderTabFollowTarget] と同じ流儀）。
 */
internal enum class RankingFollowAction {
    /** ページは既に order を指している＝何もしない。 */
    AlreadyThere,

    /** すぐ送る。 */
    Follow,

    /**
     * 送りが走っている＝据わるのを待ってから送り直す。
     * ⚠️ **「何もしない」ではない**。ここを取りこぼしにすると order とページャが食い違ったまま固定され、
     * 同じタブの再タップでも復帰できなくなる（setHomeOrder が同値 no-op のため）。
     */
    AwaitSettle,
}

/** [RankingFollowAction] の決定。分岐の順序に意味がある（既に着いているなら送りの有無は問わない）。 */
internal fun rankingFollowAction(
    currentPage: Int,
    orderPage: Int,
    scrollInProgress: Boolean,
): RankingFollowAction = when {
    currentPage == orderPage -> RankingFollowAction.AlreadyThere
    scrollInProgress -> RankingFollowAction.AwaitSettle
    else -> RankingFollowAction.Follow
}

/**
 * 期間タブ間の溝（モック .rtabs gap 18px → S16）。**並びの見た目と追従計算が同じ値を見る**ことに意味がある
 * （[orderTabFollowTarget] はタブの左端をこの溝込みで積算するので、片方だけ変えると追従先がずれる）。
 */
private val OrderTabGap = Spacing.S16

/**
 * 選択タブを可視域へ入れるための横スクロール位置（px）。**すでに収まっているときは null＝動かさない**。
 *
 * なぜ「外に出ているときだけ」か: 選択のたびに中央寄せ等で必ず位置を作り直すと、収まっているのに行が
 * 横に流れる（スワイプで期間を送るたびタブ行が揺れて読みづらい）。動かすのは A 案の約束（現在地が常に
 * 見える）が実際に破れている場合だけに絞る。
 *
 * [gapPx] を1つぶん余分に送るのは可視域の端ちょうどで止めないため——丸めで1px 欠けるのを避けつつ、端に
 * 溝を残して「その先にもタブが続く」ことを見せる（S16 は並びの既存値＝新しい値の発明はしていない）。
 * 行の端まで送り切る場合は [maxScroll] のクランプが余分を吸収する。タブ1本が可視域より広い極端な条件では
 * 左端で丸めて頭（期間名の1文字目）を優先する＝下の実装コメント。
 *
 * [tabWidths] は [NarouOrder] の entries 順・全数が実測済みであること（呼び出し側が実測を待ってから呼ぶ）。
 */
internal fun orderTabFollowTarget(
    tabWidths: List<Int>,
    selectedIndex: Int,
    gapPx: Int,
    scroll: Int,
    viewportWidth: Int,
    maxScroll: Int,
): Int? {
    // 並びは Arrangement.spacedBy(OrderTabGap) で先頭から詰めて置かれる＝左端は前のタブ幅と溝の積算で出る。
    val left = tabWidths.take(selectedIndex).sum() + gapPx * selectedIndex
    val right = left + tabWidths[selectedIndex]
    // 右の分岐で [left] を上限に丸めるのは、タブ1本が可視域より広い極端な条件（超拡大＋極狭幅）への備え。
    // 素の式は「右端＋溝を可視域へ入れる」ので、その条件では左端が可視域より左へ押し出され**頭が切れる**
    // ＝期間名の1文字目すら読めない。読めるところまでしか入らないなら、頭が読める側を採る。
    val target = when {
        left < scroll -> left - gapPx                                                   // 左へ隠れている
        right > scroll + viewportWidth -> (right + gapPx - viewportWidth).coerceAtMost(left) // 右へ溢れている
        else -> return null                                                             // 収まっている
    }
    return target.coerceIn(0, maxScroll.coerceAtLeast(0))
}

/**
 * ページ送り1つぶんの溝（モックの横マージンと同じ S24＝新値の発明なし）。
 * ドラッグ中に隣期間の行が地続きの1枚に見えないようにするためのもので、
 * **アンカーページャの pageSpacing と行スロットの移動量計算が同じ値を見る**ことに意味がある
 *（片方だけ変えると指の位置と覗きの位置がずれる）。
 */
private val RankingPageSpacing = Spacing.S24

/**
 * 発見ホームの縦リストの左右マージン（モック K `.scroll{padding:6px 20px 20px}` の 20px＝トークン S24。
 * D 発見の横マージンと同じで、新値の発明はしていない）。
 *
 * **縦リストの `contentPadding` とアンカーページャ（[RankingPagerAnchorK]）が同じ1値を見る**ことに意味がある
 *（2026-08-19・据わり失敗の残件①）。`PagerState` が持つ「1ページぶんの送り量」は
 * アンカーが measure された幅（`layoutInfo.pageSize`）から決まるので、アンカーだけがこのマージンを受けずに
 * 画面幅いっぱいで measure されると、〈1ページ＝画面幅〉と〈行の実幅＝画面幅−2×S24〉が食い違う。
 * 結果、1ページ送るのに指は行幅より広く動かす必要があり、覗きの溝も S24 でなく S24＋2×S24 に開く
 * ＝[RankingPageSpacing] の KDoc が避けたかった「指の位置と覗きの位置のずれ」がページ幅の側から再発する。
 */
private val DiscoveryListHorizontalMargin = Spacing.S24

/**
 * ページ送り1つぶんの移動量（px）。`currentPageOffsetFraction` はこの単位に対する比なので、
 * 覗きの位置もこの値から導く。
 */
private fun Density.rankingPageStepPx(pagerState: PagerState): Float =
    pagerState.layoutInfo.pageSize + RankingPageSpacing.toPx()

/**
 * [pageIndex] 番のページを置くべき横位置（px）。ページャの現在地（連続値）との差だけずらす。
 *
 * なぜ「自分が何ページ目か」から絶対位置で決めるか（2026-08-07）: 相対式（現在ページを 0 と見なして
 * `-fraction * step` で置く）だと、**そのレイヤが今どのページの中身を持っているか**を式が知らない。
 * `currentPage` は送りが半ページを越えた瞬間に切り替わる一方、中身の差し替えは次の合成なので、
 * その1フレームだけ「前の期間の中身が、次の期間の座席に座る」ことになる（＝報告された一瞬のちらつきの
 * 微小版）。自分のページ番号から引けば、中身の差し替えが1フレーム遅れても位置はその中身の座席のまま
 * ＝どのフレームを切り取っても嘘が無い。静止時 0・ドラッグ中の見えは従来式と完全に一致する
 *（現在ページなら `(cur - (cur + f)) * step = -f * step`）。
 */
private fun Density.rankingPageOffsetPx(pagerState: PagerState, pageIndex: Int): Float =
    (pageIndex - (pagerState.currentPage + pagerState.currentPageOffsetFraction)) *
        rankingPageStepPx(pagerState)

/**
 * 期間ページャの状態供給アンカー（2026-08-06 平坦化）。**画面には何も描かない**（高さ0の空ページ）。
 *
 * 役割は `PagerState` に `layoutInfo`（pageSize）を供給し続けること。行は外側 LazyColumn へ平坦化された
 * ので、このページャ自身は中身を持たない＝ページ高という概念が消え、
 * `docs/knowledge/pager-resident-pages-break-wrap-height.md` の「隣ページの高さに引きずられる」問題は
 * 原理的に起こらなくなる（高さは現在期間の行スロット群だけが決める）。
 *
 * [userScrollEnabled] を false にするのは、指のジェスチャを受けるのが各行スロットの `scrollable` だから
 *（このアンカーは画面上で触れない）。プログラム的な `animateScrollToPage` は従来どおり効く。
 *
 * [horizontalMargin]＝行が実際に置かれている左右マージン（[DiscoveryListHorizontalMargin]）。
 */
@Composable
private fun RankingPagerAnchorK(pagerState: PagerState, horizontalMargin: Dp, modifier: Modifier = Modifier) {
    HorizontalPager(
        state = pagerState,
        // 何も描かないアンカーに左右余白を与えるのは意匠でなく**採寸**のため（2026-08-19）:
        // ページ寸法はここで measure された幅で決まるので、行と同じ幅で measure させないと
        // 送り量と覗きの溝がその差ぶんずれる（[DiscoveryListHorizontalMargin] の KDoc）。
        // testTag は padding の**後**に置く＝この目印が報告する寸法が、ページャが実際に measure された幅
        // （＝layoutInfo.pageSize）と一致する。前に置くと余白ぶん外側の寸法を報告し、検証が余白の有無を
        // 見分けられなくなる（[RankingAnchorTestTag]）。
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalMargin).testTag(RankingAnchorTestTag),
        pageSpacing = RankingPageSpacing,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
    ) {
        // 高さ0＝この行に場所を取らせない（幅だけがページ寸法として意味を持つ）。
        Spacer(Modifier.fillMaxWidth())
    }
}

/**
 * ランキング領域1ページぶんの中身を「スロット列」として表したもの（平坦化の単位＝1スロット=1行）。
 *
 * なぜ描画そのものでなく**列の記述**を先に作るか: 外側 LazyColumn へ平坦化するには、描く前に
 * 「何スロットあるか」を確定させる必要がある（`items(count)` に渡す）。状態分岐と描画を分けることで、
 * 分岐の裁定（下の [rankingSlotsK]）を1箇所に閉じたまま行だけを遅延できる。
 */
private sealed interface RankingSlots {
    /** 実データの行（1行＝1スロット）。 */
    data class Rows(val novels: List<WorkSummary>) : RankingSlots

    /** 状態一文（空／失敗）＝1スロット。[retry] は失敗時の再試行導線を出すか。 */
    data class Status(val message: String, val retry: Boolean) : RankingSlots

    /** 控えの無いページ＝行数ぶんの骨で高さを保つ。 */
    object Skeleton : RankingSlots

    val count: Int
        get() = when (this) {
            is Rows -> novels.size
            is Status -> 1
            is Skeleton -> RankingSkeletonRowCount
        }
}

/**
 * 選択中の期間（＝ページャの据わり先と order が一致している期間）に何を並べるかの裁定。
 * 旧 RankingPageK の分岐をそのまま引き継ぐ:
 *
 *  1) 生きた [state] が正: Content=最新行・Empty/Error=正直に status（控えがあっても行で覆い隠さない
 *     ＝真に0件・失敗を隠さない旧裁定の継承）。
 *  2) 再取得(Loading)中は期間別の直近 Content を骨格として出し続ける（stale-while-revalidate）。
 *  3) 控えの無いページは行数ぶんの構造スケルトン。なぜ「読み込んでいます」1行ではいけないか:
 *     領域の高さが行数ぶん→1行へ崩壊し、外側 LazyColumn の総コンテンツ高ごと縮んで LazyListState が
 *     可視アンカーを失い先頭へクランプされる（2026-07-19 に修正済みの既知バグを、期間別控えへ分割した
 *     ことで「初訪ページ」という経路で踏み直した実例がある）。
 */
private fun rankingSlotsK(state: DiscoveryUiState, cached: DiscoveryUiState.Content?): RankingSlots = when {
    state is DiscoveryUiState.Content -> RankingSlots.Rows(state.novels)
    state is DiscoveryUiState.Empty -> RankingSlots.Status("作品が見つかりませんでした", retry = false)
    state is DiscoveryUiState.Error -> RankingSlots.Status(state.message, retry = true)
    cached != null -> RankingSlots.Rows(cached.novels)
    else -> RankingSlots.Skeleton
}

/**
 * **選択中でない期間**に何を並べるか。使い所は2つ＝据わりの両隣に常駐する覗きの面と、送りは確定したが
 * order の追従がまだ返ってきていない据わり位置（[rankingSectionK]）。どちらも生きた state を見ない
 *（state は常に選択中期間のもので、別の期間に当てると他期間の読込結果を誤って被せることになる）。
 * 控えがあればその行・無ければ骨＝平坦化前に非選択ページへ出していたものと同じ。
 */
private fun rankingNeighborSlotsK(cached: DiscoveryUiState.Content?): RankingSlots =
    cached?.let { RankingSlots.Rows(it.novels) } ?: RankingSlots.Skeleton

/**
 * ランキング領域を外側 LazyColumn へ平坦化して並べる（2026-08-06・遷移 jank 残③の真因対処）。
 *
 * **1行＝1 item** にすることで、LazyColumn が可視行だけを合成・記録するようになる。旧実装は
 * 1ページ＝取得件数ぶん（`DiscoveryQuery().limit`＝30行）の素の Column を**単一 item**として抱いており、
 * LazyColumn の間引き粒度が item である以上、画面外の行まで全数が display list に記録されていた
 * （実測 Janky 11.89〜12.23%・`draw/record` max 131〜167ms の一点集中／`measure/layout`・GPU は無罪）。
 *
 * 横スワイプは各スロットの `scrollable` が [pagerState] を直接動かして実現する（[RankingPagerAnchorK] の
 * KDoc も参照）。期間タブ（[OrderTabsK]）は同階層の `stickyHeader` として上に居る（2026-08-07 裁定）ので、
 * 縦スクロールでこの行群が上へ流れてもタブ行だけが上端に残る＝行側は sticky 化のために何も持たない。
 *
 * [pageOrder]＝**ページャが指している期間**（据わり位置に描く期間）・[selectedOrder]＝VM が選択中の期間。
 * この2つは食い違う（送りが半ページを越えた瞬間から order の追従が終わるまで）ので、生きた [state] を
 * 当ててよいのは一致しているときだけ。詳細は [DiscoveryHomeK] の pagerOrder のコメント。
 */
private fun LazyListScope.rankingSectionK(
    pagerState: PagerState,
    flingBehavior: FlingBehavior,
    edgeSeal: NestedScrollConnection,
    pageOrder: NarouOrder,
    selectedOrder: NarouOrder,
    residentOrders: () -> List<NarouOrder>,
    state: DiscoveryUiState,
    contents: Map<NarouOrder, DiscoveryUiState.Content>,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    val slots = if (pageOrder == selectedOrder) {
        rankingSlotsK(state, contents[pageOrder])
    } else {
        // order が追いつく前のページ＝その期間の控え（無ければ骨）で据わる。ここに [state] を当てると
        // 別期間の読込結果を被せることになる＝隣ページと同じ扱いにする（平坦化前の isCurrent 分岐の回復）。
        rankingNeighborSlotsK(contents[pageOrder])
    }
    items(
        count = slots.count,
        // key に**期間を含めない**（2026-08-14・「半ページずれて止まる」の真因対処）。
        //
        // なぜ: 送りが半ページを越えた瞬間に `PagerState.currentPage` が次ページへ切り替わり、それを読む
        // [pageOrder] も同時に変わる。期間名を key に含めていると、この瞬間に**ランキング行の item が全数
        // 別 key になる**＝LazyColumn が既存スロットを破棄して composition をやり直す。破棄されるものの中には
        // 「今まさに指を受けている [RankingSlotK] の `scrollable`」が含まれ、そのノードが抱えていたドラッグと
        // スナップの coroutine が道連れに消える。結果、ページャは `currentPageOffsetFraction` が非0のまま
        // 静止し（アンカーは userScrollEnabled=false・[DiscoveryHomeK] の LaunchedEffect(order) も
        // currentPage 一致で何もしない）、誰も据え直さない＝半分ずれた絵のまま固まる。
        //
        // ＝行スロットの同一性は「どの期間の行か」ではなく**座席（何行目か）**に置く。期間送りで差し替わるのは
        // 中身だけになり、指を受けているノードはページ跨ぎでも生き続けてスナップを完走できる
        //（位置は translationX が期間ごとの座席へ置くので、key で期間を分ける必要は元々ない）。
        // 旧 key の狙い「前の期間の行の状態が居座らないように」は、行が持つ remember が
        // `rememberOrderMetricLabel` の「今」の凍結だけ＝座席を跨いでも意味が変わらないため失うものが無い。
        key = { index -> "ranking_$index" },
        // 行・骨・status は中身の形が違う＝再利用プールを分ける（別種の item を使い回させない）。
        contentType = { slots::class.simpleName },
    ) { index ->
        // 常駐窓の読み取りは**この item スコープ**で行う（画面全体でなく可視行だけが再コンポーズされる）。
        RankingSlotK(
            pagerState = pagerState,
            flingBehavior = flingBehavior,
            edgeSeal = edgeSeal,
            seatedOrder = pageOrder,
            residentOrders = residentOrders(),
            index = index,
            // 期間 → その期間に並べるもの、の対応。**役割（据わり／覗き）で分岐させない**のが要点で、
            // 据わりの期間だけが生きた [state] を見てよい、という裁定はここに閉じている
            //（[rankingSlotsK] / [rankingNeighborSlotsK] の使い分けは従来どおり）。
            slotsOf = { o -> if (o == pageOrder) slots else rankingNeighborSlotsK(contents[o]) },
            onOpenDetail = onOpenDetail,
            onRefresh = onRefresh,
        )
    }
}

/**
 * ランキングの1スロット（＝平坦化された1行ぶんの枠）。
 *
 * 担うもの:
 *  - **横ジェスチャ**: `scrollable` が [pagerState] を直接動かす。Pager 本体を持たない構成なので、
 *    指の入力口は行そのものになる（＝旧実装でページャ全域が受けていた範囲と一致する）。
 *    `reverseDirection = true` は横 LTR の既定（左へ払う＝次ページへ進む）を標準実装と揃えるため。
 *  - **端の封止**: [edgeSeal] を `scrollable` の親側に置き、消費し切れない横成分を全量食う
 *    ＝端期間でさらに引いてもアプリのタブ（外側 Pager）が切り替わらない（2026-07-29 監督裁定の継承）。
 *  - **覗き**: 据わり以外の期間も同じ枠に重ねて描き、**親の高さ決定に参加しない**（枠の高さは据わりの期間の
 *    行だけが決める＝「ページ高を現在ページだけから決める」の実体。はみ出しは `clipToBounds` が切る。
 *    旧 Pager が wrap 高＝現在ページ準拠で隣をクリップして覗かせていた見え方を行単位で再現する）。
 *    ⚠️ この「参加しない」を**子の modifier（matchParentSize）で表さない**のが 2026-09-02 の是正で、
 *    理由は [rankingSlotMeasurePolicy] の KDoc（役割の入れ替わりが子の制約を反転させ測り直しを呼ぶ）。
 *    どの面も自分のページ番号の座席へ置く（＝[rankingPageOffsetPx]）。
 *
 * **なぜ枠を「据わり／覗き」でなく期間 ordinal の剰余で持つか（2026-08-26・送りアニメのコストの真因対処）**:
 * 送りが半ページを越えた瞬間、据わりと覗きは**役割だけが入れ替わる**——前後どちらも画面に居るのは同じ2期間
 *（例: 週間→月間の送りなら、跨ぐ前は〈据わり=週間・覗き=月間〉、跨いだ後は〈据わり=月間・覗き=週間〉）。
 * 旧実装は枠を役割の順（第1子=据わり・第2子=覗き）で並べていたので、この入れ替わりで**同じ枠に別期間の中身が
 * 流し込まれ、据わり・覗きの両方が全数再合成**されていた。実測（エミュ・可視6行）ではその1フレームだけ
 * StaticLayout 構築が 96 個・UI スレッド 50〜54ms に跳ね、これがページ送りアニメの最も重いフレームだった。
 *
 * ⇒ 枠は **ordinal を [RankingSlotCount] で割った余り**で固定する（[rankingSlotIdFor]）。常駐する期間は
 * 連続する高々3つ（[rankingResidentOrders]）＝余りは必ず全て異なるので、どの枠にどの期間が入るかが
 * 役割にも送りの向きにも依存しない。役割が入れ替わる瞬間も各枠は同じ期間を持ち続ける＝composition から見て
 * **何も起きない**（動くのは translationX だけ＝既に deferred read）。2026-08-26 に偶奇（2枠）で入れた形の
 * 一般化で、窓を ±1 へ広げる（＝A の対処）ために枠を1つ増やしただけ。
 * 行へ渡すタップ導線を役割から独立させたのも同じ理由（[DiscoveryHomeK] の rankingOpenDetail）。
 */
@Composable
private fun RankingSlotK(
    pagerState: PagerState,
    flingBehavior: FlingBehavior,
    edgeSeal: NestedScrollConnection,
    seatedOrder: NarouOrder,
    residentOrders: List<NarouOrder>,
    index: Int,
    slotsOf: (NarouOrder) -> RankingSlots,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            // 溝（pageSpacing）ぶん外へはみ出す覗きを、この行の枠で切る。
            // タップの当たり判定もこのクリップに従う＝枠の外へ送り出された期間の行は指を拾わない。
            .clipToBounds()
            // 期間ページの識別子（理由は rankingPageTestTag の KDoc）。中身の**祖先**に置くことで、
            // テストが「どのページの子孫か」で数えられる形を平坦化後も保つ。
            .testTag(rankingPageTestTag(seatedOrder))
            .nestedScroll(edgeSeal)
            .scrollable(
                state = pagerState,
                orientation = Orientation.Horizontal,
                flingBehavior = flingBehavior,
                reverseDirection = true,
            ),
        content = {
            // 枠の並び順は**ordinal の剰余で固定**（役割でも送りの向きでも並べ替わらない）。layoutId も同じ
            // 剰余＝親データが動かないので、据わりと覗きの入れ替わりが子の測り直しを呼ばない。
            // 窓に居ない剰余の枠は order=null＝何も置かない（[RankingPageLayerK] が即 return する）。
            RankingSlotIds.forEach { slotId ->
                val order = residentOrders.firstOrNull { rankingSlotIdFor(it) == slotId }
                RankingPageLayerK(
                    Modifier.layoutId(slotId), pagerState,
                    order, order == seatedOrder, index,
                    slotsOf, onOpenDetail, onRefresh,
                )
            }
        },
        measurePolicy = remember(seatedOrder) { rankingSlotMeasurePolicy(rankingSlotIdFor(seatedOrder)) },
    )
}

/**
 * 行スロットが持つ枠の数＝常駐窓の広さ（[rankingResidentOrders]＝据わり±1）。
 *
 * この値が「連続する期間の ordinal を割った余りで枠を固定できる」条件そのもの: 連続する 3 つの整数は
 * 3 で割った余りが必ず全て異なるので、窓の中の期間は枠を奪い合わない。逆に窓を ±2 へ広げるなら
 * この値も 5 にしないと衝突する（2 では 2026-08-26 の偶奇＝窓 ±0.5 相当が限界だった）。
 */
private const val RankingSlotCount = 3

/** 枠の識別子（[rankingSlotMeasurePolicy] が据わりの枠を見分けるためだけに使う）。値は ordinal の剰余。 */
private data class RankingSlotId(val residue: Int)

private val RankingSlotIds = List(RankingSlotCount) { RankingSlotId(it) }

/** [order] が入る枠。ordinal 由来＝**送りでも役割の入れ替わりでも動かない**のが唯一の要件。 */
private fun rankingSlotIdFor(order: NarouOrder): RankingSlotId =
    RankingSlotIds[order.ordinal % RankingSlotCount]

/**
 * 行スロットに常駐させる期間＝**据わり（[settledPage]）とその両隣**。理由は [DiscoveryHomeK] の
 * residentOrdersState のコメント（ジャンク要因 A＝覗きの新規合成を、指の動き出しから静止フレームへ移す）。
 *
 * [currentPage] を必ず含めるのは、期間タブのタップが `animateScrollToPage` で複数ページを跨ぎ得るため。
 * その最中は `currentPage` だけが途中ページを指し（`settledPage` は送りが終わるまで動かない）、窓から
 * 漏れると**据わりの面が消えて行の高さが 0 になる**＝一覧全体が縮んで先頭へクランプする。枠は剰余で
 * 1つずつしか無いので、衝突する常駐を追い出して据わりを必ず入れる（覗きは1フレーム欠けても
 * 画面外なので見た目に出ない＝譲るのは常に覗きの側）。
 */
private fun rankingResidentOrders(settledPage: Int, currentPage: Int): List<NarouOrder> {
    val entries = NarouOrder.entries
    val orders = ArrayList<NarouOrder>(RankingSlotCount)
    // ±1＝1フリックで到達し得る範囲（PagerSnapDistance 既定＝1回の送りで1ページ）。
    for (page in (settledPage - 1)..(settledPage + 1)) entries.getOrNull(page)?.let(orders::add)
    val seated = entries.getOrNull(currentPage)
    if (seated != null && seated !in orders) {
        orders.removeAll { rankingSlotIdFor(it) == rankingSlotIdFor(seated) }
        orders.add(seated)
    }
    return orders
}

/**
 * 1行ぶんの枠（据わり＋覗き）の測り方。**役割で子の制約を変えないこと**が唯一の要点。
 *
 * **なぜ Box + matchParentSize をやめたか（2026-09-02・送りアニメの残りスパイクの真因対処）**:
 * 旧実装は「高さを決めるのは据わりだけ」を覗き側の [androidx.compose.foundation.layout.BoxScope.matchParentSize]
 * で表していた。ところが matchParentSize は**子が受け取る制約そのもの**を変える（付いていれば
 * `Constraints.fixed(枠の実寸)`・付いていなければ高さ無限）。半ページを越えて据わりと覗きが入れ替わると、
 * この modifier も一緒に入れ替わる＝**両方の枠の高さ制約が反転**し、Compose は制約が変わった子を
 * 必ず測り直す。行の中身（テキスト）は同じままなので組版キャッシュは効き
 *（`Constructing StaticLayout` はほぼ 0）、それでも**可視行×2ページぶんの measure が丸ごと走る**。
 * 実測（Robolectric・可視3行）で「送り1回あたり枠9回・行9回の測り直し」＝1行につき3回、
 * うち2回は maxH が 151⇄無限に反転しただけのもの、と数え上げて確認した。
 *
 * ⇒ 子には**常に同じ制約**（Box が matchParentSize 無しの子へ渡すのと同じ緩め方）を渡し、
 * 「どちらの高さを採るか」は**親のこの測り方**が決める。据わりが入れ替わってもこの方針が
 * 選ぶ相手が変わるだけで、子の制約は 1 ビットも動かない＝測り直しが起きない。
 * 見た目は不変（枠の高さは従来どおり据わりの期間の行の高さ・はみ出しは親の `clipToBounds` が切る）。
 */
private fun rankingSlotMeasurePolicy(seatedSlotId: RankingSlotId) = MeasurePolicy { measurables, constraints ->
    // Box が matchParentSize でない子へ渡すのと同じ制約＝この1行が「役割に依存しない測り方」の実体。
    val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(childConstraints) }
    val seatedIndex = measurables.indexOfFirst { it.layoutId == seatedSlotId }
    // 枠の高さは据わりの期間の行だけが決める（覗きは高さに参加しない＝旧 matchParentSize の意図）。
    // 覗きしか居ない状況は構造上あり得ない（据わりの枠は常に置かれる）が、保険で最大高へ倒す。
    val height = if (seatedIndex >= 0) placeables[seatedIndex].height else placeables.maxOfOrNull { it.height } ?: 0
    val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
    layout(width, height) { placeables.forEach { it.place(0, 0) } }
}

/**
 * 1行ぶんの枠に載る「ある期間の1行」。[order] が null＝この枠（剰余）に入る期間が今の常駐窓に居ない
 *（端の期間に据わっているとき＝隣が片側しか無い場合）。
 *
 * [seated] は**描き方だけ**を決める（高さを決めるか・TalkBack に読ませるか）。中身に渡す値には一切効かない
 * ——効かせると役割の入れ替わりで全数再合成が起きる（[RankingSlotK] の KDoc）。modifier の差し替えは
 * ノードの付け替えで済み、行が抱えるテキストの計測結果は生き残る。
 */
@Composable
private fun RankingPageLayerK(
    modifier: Modifier,
    pagerState: PagerState,
    order: NarouOrder?,
    seated: Boolean,
    index: Int,
    slotsOf: (NarouOrder) -> RankingSlots,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    if (order == null) return
    val slots = slotsOf(order)
    // 覗き側の期間は行数が違い得る（例: 控えなしの骨 30 行 vs status 1 行）＝無い行は置かない。
    if (index >= slots.count) return
    Column(
        modifier = modifier
            // State 読みを layer 更新に閉じる（deferred read）＝ドラッグ中に composition/layout を起こさない。
            .graphicsLayer { translationX = rankingPageOffsetPx(pagerState, order.ordinal) }
            // 面そのものに寸法を読める節点を置く（理由は [rankingPageLayerTestTag] の KDoc）。
            // clearAndSetSemantics は**子孫**の意味だけを落とし、その節点自身の意味は残す＝下の分岐と同居できる。
            .testTag(rankingPageLayerTestTag(order))
            .then(
                when {
                    // 覗くだけの行を TalkBack に読ませない（K の気分ゴースト格子と同じ扱い）。
                    !seated -> Modifier.clearAndSetSemantics {}
                    // 骨は装飾＝文字を描かないが、旧 status 行が担っていた支援技術への通知は落とさない。
                    slots is RankingSlots.Skeleton && index == 0 ->
                        Modifier.semantics { contentDescription = RankingSkeletonDescription }
                    else -> Modifier
                },
            ),
    ) {
        RankingSlotBodyK(slots, index, order, onOpenDetail, onRefresh)
    }
}

/**
 * 1スロットぶんの中身（行／status／骨）。
 *
 * 導線は**据わり・覗きで同じものを受ける**（2026-08-26）。旧実装は覗きに null を渡して配線を落としていたが、
 * それは「渡す値が役割で変わる」ことを意味し、半ページ地点の役割入れ替わりで全数再合成を招いていた
 *（[RankingSlotK] の KDoc）。覗き行を踏ませない目的は、送りが走っている間だけ導線を殺す形へ移してある
 *（[DiscoveryHomeK] の rankingOpenDetail・そちらの方が範囲として厳しい）。
 */
@Composable
private fun RankingSlotBodyK(
    slots: RankingSlots,
    index: Int,
    pageOrder: NarouOrder,
    onOpenDetail: (ncode: Ncode) -> Unit,
    onRefresh: () -> Unit,
) {
    when (slots) {
        is RankingSlots.Rows -> {
            val novel = slots.novels[index]
            NovelListRow(
                rank = index + 1,
                novel = novel,
                order = pageOrder,
                // 境界: novel.ncode は Moshi 由来の String。詳細遷移の引数は型付き Ncode へ包む。
                onClick = { novel.ncode?.let { code -> onOpenDetail(Ncode(code)) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        is RankingSlots.Status -> {
            RankingStatus(slots.message)
            if (slots.retry) {
                Text(
                    "再試行",
                    fontSize = FontCaption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onRefresh)
                        .padding(vertical = Spacing.S8),
                )
            }
        }
        is RankingSlots.Skeleton -> RankingSkeletonRow(index)
    }
}

/**
 * 状態供給アンカー（[RankingPagerAnchorK]）の testTag。
 *
 * なぜ本番コードに目印が要るか: このアンカーは**何も描かない**（高さ0・文字も色も無い）ので、
 * セマンティクス木の中で他と見分ける手掛かりが一切無い。しかし守るべき不変条件——
 * 〈アンカーが measure された幅＝`layoutInfo.pageSize`〉が〈行の実幅〉と一致すること
 *（[DiscoveryListHorizontalMargin]）——は寸法そのものなので、寸法を読める節点が要る。
 * 見えるものを代理指標にする手（例: 覗きの溝を目視相当で測る）は、送り量のずれを
 * 溝の大きさへ変換した二次症状しか見ておらず、原因側（ページ幅）を名指しできない。
 */
internal const val RankingAnchorTestTag: String = "rankingPagerAnchor"

/**
 * 期間ページの testTag（平坦化後は「その期間の行スロット」に付く・2026-08-06 更新）。
 *
 * なぜ本番コードにテスト用の目印を置くか: ここで守るべき不変条件は「**どの期間に**何が載るか」
 * （期間別の控えを分けた狙い＝週間の行が月間ページに載る誤誘導を起こさないこと）であって、
 * 「セマンティクス木のどこかに在るか」ではない。木全体を数える検証は、合成される範囲が変われば
 * 意味が変わってしまう脆い代理指標で、実際 2026-07-31 に隣接ページ常駐を試した際、実装が正しいまま
 * 誤検知した（その常駐化自体は高さ規約と両立せず撤回＝[RankingPagerAnchorK] の KDoc）。
 * 期間を名指しできれば、検証は合成戦略に左右されず設計の意図そのものを見る——実際、行を LazyColumn へ
 * 平坦化した今回の変更でも、この目印を「ページの Column」から「行スロットの外枠」へ移すだけで
 * テスト側の契約（`hasAnyAncestor(hasTestTag(...))`）はそのまま生き延びている。
 */
internal fun rankingPageTestTag(order: NarouOrder): String = "rankingPage_${order.name}"

/**
 * その行スロットに載っている「[order] の面」そのものの testTag（2026-09-02・覗きの常駐化に伴う観測点の強化）。
 *
 * [rankingPageTestTag] が**枠**（据わりの期間で名乗る外側の1節点）に付くのに対し、こちらは枠の中に重なる
 * **面**1枚ずつに付く＝どの期間の面が今どこに置かれているかを1節点で読める。
 *
 * なぜ本番コードに増やすか: 覗きを常駐させた（[DiscoveryHomeK] の residentOrders）ことで、**据わっていない
 * 期間の面も合成されたまま画面外に居る**のが正常になった。その面は TalkBack に読ませないため
 * `clearAndSetSemantics` で子孫の意味を全部落としている＝**セマンティクス木にはその期間の行の文字が1つも
 * 現れない**。したがって「他期間の行が出ていないこと」を文字の有無で検査すると、覗きが誤って据わり位置へ
 * 出てしまう退行が起きても**常に緑のまま**になる（＝空振りの検査）。守るべき不変条件は
 * 「合成されていない」ではなく「**表示されていない**」なので、位置と寸法を読める節点を面ごとに置き、
 * 検査を合成戦略から独立させる（[RankingAnchorTestTag] と同じ理由づけ＝見えないものを見るための目印）。
 */
internal fun rankingPageLayerTestTag(order: NarouOrder): String = "rankingPageLayer_${order.name}"

/**
 * ランキング領域の状態一文（空／失敗理由＝意味テキストゆえ infoText）。
 * 読込中はここでなく骨（[RankingSkeletonRow]）が受ける＝「無い・失敗した」だけを1行に畳む
 *（畳んで良いのは真に0件・失敗のときだけ、という既存裁定）。
 */
@Composable
private fun RankingStatus(text: String) {
    Text(
        text,
        fontSize = FontCaption,
        color = LocalShelfColors.current.infoText,
        modifier = Modifier.padding(vertical = Spacing.S24),
    )
}

/**
 * 公式サイトへの逃げ道（handover ★A 要件・モック .official）: ヘアラインで区切った外部リンク行。
 * なろう公式（yomou.syosetu.com）を外部ブラウザで開く＝Blocked 送客と同じ素の ACTION_VIEW 流儀
 *（BookshelfScreen 参照）。ブラウザ不在の稀ケースは ActivityNotFoundException を握って無害化する
 *（案内リンクゆえ症状隠しではない＝逃げ道が塞がるより無反応の方が害が小さい）。
 */
@Composable
private fun OfficialLinkK() {
    val context = LocalContext.current
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = Spacing.S8), // .official margin-top 8px
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yomou.syosetu.com/")))
                    }
                }
                .padding(top = Spacing.S16, bottom = Spacing.S4), // .official padding 16px 2px 4px（横は .scroll マージン）
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "小説家になろう公式サイトで探す",
                fontSize = FontSubTitle, // .official 13px
                color = LocalShelfColors.current.infoText,
            )
            Icon(
                Icons.Filled.NorthEast, // .official ↗（外部リンク＝右上矢印）
                contentDescription = null,
                tint = LocalShelfColors.current.infoText,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
